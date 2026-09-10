package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.ShipCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.engine.Graph;
import de.nebula.model.Blockade;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetCargoEntry;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.FleetCargoCapacity;
import de.nebula.model.FleetSystemTarget;
import de.nebula.model.Planet;
import de.nebula.model.ProductType;
import de.nebula.model.ShipTypeDef;
import de.nebula.model.StarSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1:1-Portierung der "Flotten"-Sektion aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 7).
 */
public final class FleetCommands {
  private FleetCommands() {
  }

  public static List<Fleet> fleetsOf(GameState state, String playerId) {
    return state.fleets.stream().filter(f -> f.ownerId.equals(playerId)).toList();
  }

  /** Alle Flotten der Galaxie – für Bots und Werkzeuge; die Oberfläche nutzt die gezielten Abfragen darunter. */
  public static List<Fleet> allFleets(GameState state) {
    return List.copyOf(state.fleets);
  }

  /** Alle Flotten (aller Kommandanten) in EINEM System – statt der ganzen Galaxie je Sekunde je Seite. */
  public static List<Fleet> fleetsInSystem(GameState state, String systemId) {
    return state.fleets.stream().filter(f -> f.systemId.equals(systemId)).toList();
  }

  /** Eine Flotte beliebigen Eigentümers (Name, Schiffe) – für Kampfberichte und Angriffsbestätigungen; {@code null}, wenn es sie nicht mehr gibt. */
  public static Fleet fleetById(GameState state, String fleetId) {
    return find(state, fleetId);
  }

  /** Schiffe je System für die Galaxiekarte: eigene überall, fremde nur in BESUCHTEN Systemen (Fog of War). */
  public record FleetPresence(int myShips, int enemyShips) {
  }

  public static Map<String, FleetPresence> fleetPresence(GameState state, String playerId) {
    Set<String> known = state.knownSystemIdsByPlayer.getOrDefault(playerId, Set.of());
    Map<String, int[]> counts = new HashMap<>();
    for (Fleet f : state.fleets) {
      int ships = 0;
      for (FleetShipGroup g : f.ships) ships += (int) g.quantity;
      if (ships == 0) continue;
      boolean mine = f.ownerId.equals(playerId);
      if (!mine && !known.contains(f.systemId)) continue;
      int[] c = counts.computeIfAbsent(f.systemId, k -> new int[2]);
      c[mine ? 0 : 1] += ships;
    }
    Map<String, FleetPresence> out = new HashMap<>();
    for (Map.Entry<String, int[]> e : counts.entrySet()) out.put(e.getKey(), new FleetPresence(e.getValue()[0], e.getValue()[1]));
    return out;
  }

  public static Fleet requireOwnFleet(GameState state, String playerId, String fleetId) {
    Fleet fleet = find(state, fleetId);
    if (fleet == null) throw new CommandException("Unbekannte Flotte.");
    var player = GameQueries.requirePlayer(state, playerId);
    if (!fleet.ownerId.equals(player.id)) throw new CommandException("Diese Flotte gehört einem anderen Kommandanten.");
    return fleet;
  }

  private static Fleet find(GameState state, String fleetId) {
    for (Fleet f : state.fleets) if (f.id.equals(fleetId)) return f;
    return null;
  }

  private static StarSystem findSystem(GameState state, String systemId) {
    for (StarSystem s : state.systems) if (s.id.equals(systemId)) return s;
    return null;
  }

  record Capacity(double massKg, double volumeM3) {
  }

  /** Frachtkapazität EINER Schiffsliste – auch für Schiffe, die noch in keiner Flotte stehen ({@code FleetCompositionCommands}). */
  static Capacity cargoCapacityOf(List<FleetShipGroup> ships) {
    double mass = 0, volume = 0;
    for (FleetShipGroup g : ships) {
      ShipTypeDef def = ShipCatalog.find(g.shipProductTypeId);
      mass += def.cargoMassKg * g.quantity;
      volume += def.cargoVolumeM3 * g.quantity;
    }
    return new Capacity(mass, volume);
  }

  /** Masse und Volumen EINER Frachtliste – Gegenstück zu {@link #cargoCapacityOf}. */
  static Capacity cargoUsedOf(List<FleetCargoEntry> cargo) {
    double mass = 0, volume = 0;
    for (FleetCargoEntry c : cargo) {
      ProductType product = ProductCatalog.find(c.productTypeId);
      mass += product.massKg * c.quantity;
      volume += product.volumeM3 * c.quantity;
    }
    return new Capacity(mass, volume);
  }

  private static Capacity fleetCargoCapacity(Fleet fleet) {
    return cargoCapacityOf(fleet.ships);
  }

  private static Capacity fleetCargoUsed(Fleet fleet) {
    return cargoUsedOf(fleet.cargo);
  }

  public static void transferShipsToFleet(GameState state, IdGenerator ids, String playerId, String colonyId,
                                           String shipProductTypeId, double quantity, String targetFleetId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    double stock = Warehouse.qty(state, colonyId, shipProductTypeId);
    if (stock < quantity) throw new CommandException("Nicht genug Schiffe im Lager.");
    Colony colony = ColonyCommands.colony(state, colonyId);

    Fleet fleet;
    if (targetFleetId != null) {
      fleet = find(state, targetFleetId);
      if (fleet == null || !fleet.ownerId.equals(colony.ownerId)) throw new CommandException("Unbekannte eigene Flotte.");
      if (fleet.status != FleetStatus.Stationed || !colonyId.equals(fleet.locationColonyId)) {
        throw new CommandException("Die Flotte muss bei dieser Kolonie stationiert sein.");
      }
    } else {
      long countOwned = state.fleets.stream().filter(f -> f.ownerId.equals(colony.ownerId)).count();
      fleet = new Fleet();
      fleet.id = ids.next("flt");
      fleet.ownerId = colony.ownerId;
      fleet.name = "Flotte " + colony.name + " " + (countOwned + 1);
      fleet.locationType = FleetLocationType.ColonyOrbit;
      fleet.locationColonyId = colonyId;
      fleet.locationPlanetId = null;
      fleet.systemId = colony.systemId;
      fleet.status = FleetStatus.Stationed;
      fleet.ships = new ArrayList<>();
      fleet.cargo = new ArrayList<>();
      fleet.destinationSystemId = null;
      fleet.pendingHops = List.of();
      fleet.departedAt = null;
      fleet.arrivesAt = null;
      fleet.fuelCapsules = 0;
      state.fleets.add(fleet);
    }

    Warehouse.add(state, colonyId, shipProductTypeId, -quantity);
    boolean found = false;
    for (FleetShipGroup g : fleet.ships) {
      if (g.shipProductTypeId.equals(shipProductTypeId)) {
        g.quantity += quantity;
        found = true;
        break;
      }
    }
    if (!found) {
      List<FleetShipGroup> ships = new ArrayList<>(fleet.ships);
      ships.add(new FleetShipGroup(shipProductTypeId, quantity));
      fleet.ships = ships;
    }
  }

  /**
   * Nimmt {@code quantity} Schiffe eines Typs endgültig aus der Flotte heraus –
   * aktuell das bei der Koloniegründung verbrauchte Kolonisationsschiff
   * ({@code ColonyCommands.colonizePlanet}). Leergelaufene Schiffsgruppen
   * verschwinden, und mit dem letzten Schiff verschwindet die Flotte selbst.
   *
   * <p>Eine schiffslose Flotte stehen zu lassen wäre kein harmloser Rest: sie
   * bliebe in jeder Flottenübersicht sichtbar, ihr Tank fasst rechnerisch
   * nichts mehr ({@link #fuelTankCapacity} = 0) und sie dürfte beliebig weit
   * springen, weil {@link #consumeJumpFuel} bei 0 Schiffen ohne Verbrauch
   * zurückkehrt. Restlicher Treibstoff im Tank ist damit verloren – er hängt an
   * den Schiffen, nicht an der Flotte.</p>
   */
  public static void consumeShips(GameState state, Fleet fleet, String shipProductTypeId, double quantity) {
    List<FleetShipGroup> remaining = new ArrayList<>();
    for (FleetShipGroup g : fleet.ships) {
      double left = g.shipProductTypeId.equals(shipProductTypeId) ? g.quantity - quantity : g.quantity;
      if (left > 0) remaining.add(new FleetShipGroup(g.shipProductTypeId, left));
    }
    fleet.ships = remaining;
    if (remaining.isEmpty()) removeFleet(state, fleet);
  }

  /**
   * Eine im Gefecht restlos vernichtete Flotte verschwindet – samt Blockade, Fracht
   * und eingeschifften Truppen (die Soldaten gehen mit ihrem Transporter unter).
   * Vorher blieb sie als Flotte mit null Schiffen stehen (sieben Stück im Live-Spiel,
   * Konzept 31 §A): sichtbar in jeder Übersicht, ohne Tank, aber beliebig sprungfähig,
   * weil {@link #consumeJumpFuel} bei null Schiffen nichts verbraucht.
   */
  public static void removeDestroyedFleets(GameState state) {
    for (Fleet f : new ArrayList<>(state.fleets)) {
      if (f.ships.stream().noneMatch(s -> s.quantity > 0)) removeFleet(state, f);
    }
  }

  private static void removeFleet(GameState state, Fleet fleet) {
    state.fleets.remove(fleet);
    GameEvents.cancel(state, GameEventType.FLEET_ARRIVED, fleet.id);
    // Ohne Flotte keine Blockade – dieselbe Regel wie beim Ortswechsel in moveFleetWithinSystem.
    state.blockades.removeIf(b -> b.fleetId.equals(fleet.id));
    state.groundForceGroups.removeIf(g -> fleet.id.equals(g.fleetId));
  }

  /**
   * Frachtkapazität und aktuelle Auslastung EINER Flotte, plus – falls ein
   * Produkt angegeben ist – die maximal ladbare Stückzahl unter Berücksichtigung
   * von Lagerbestand UND Massen-/Volumengrenze.
   *
   * <p>Existiert, damit die Oberfläche diese Regel nicht ein zweites Mal
   * nachbilden muss: die Kapazitätsgrenze ist eine SPIELREGEL und wird in
   * {@link #loadCargo} durchgesetzt – die Anzeige darf sie nicht abweichend
   * nachrechnen (Umsetzungskonzept/15_...md, Auftrag 3).</p>
   */
  public static FleetCargoCapacity fleetCargoCapacity(GameState state, String fleetId, String productTypeId) {
    Fleet fleet = find(state, fleetId);
    if (fleet == null) throw new CommandException("Unbekannte Flotte.");
    Capacity capacity = fleetCargoCapacity(fleet);
    Capacity used = fleetCargoUsed(fleet);

    FleetCargoCapacity result = new FleetCargoCapacity();
    result.capacityMassKg = capacity.massKg();
    result.capacityVolumeM3 = capacity.volumeM3();
    result.usedMassKg = used.massKg();
    result.usedVolumeM3 = used.volumeM3();
    result.maxLoadableQuantity = 0;

    if (productTypeId == null) return result;
    double stock;
    if (fleet.locationColonyId != null) {
      stock = Warehouse.qty(state, fleet.locationColonyId, productTypeId);
    } else {
      StarSystem sys = findSystem(state, fleet.systemId);
      if (sys == null || !sys.isTradeHub) return result; // außerhalb einer Kolonie oder Station gibt es nichts zu laden
      stock = HubDepot.qty(state, fleet.systemId, fleet.ownerId, productTypeId);
    }
    ProductType product = ProductCatalog.find(productTypeId);
    double remainingMass = capacity.massKg() - used.massKg();
    double remainingVolume = capacity.volumeM3() - used.volumeM3();
    double byMass = product.massKg > 0 ? Math.floor(remainingMass / product.massKg) : Double.MAX_VALUE;
    double byVolume = product.volumeM3 > 0 ? Math.floor(remainingVolume / product.volumeM3) : Double.MAX_VALUE;
    result.maxLoadableQuantity = Math.max(0, Math.min(Math.floor(stock), Math.min(byMass, byVolume)));
    return result;
  }

  public static void loadCargo(GameState state, String playerId, String fleetId, String productTypeId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    quantity = Math.floor(quantity); // Fracht bewegt sich nur in ganzen Stücken (Umsetzungskonzept/25_...md)
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    TroopTransportCommands.requireNotASoldier(productTypeId);
    if (fleet.status != FleetStatus.Stationed || fleet.locationColonyId == null) {
      throw new CommandException("Die Flotte muss bei einer Kolonie gelandet sein.");
    }
    GameQueries.requireOwnColony(state, playerId, fleet.locationColonyId); // nur aus dem Lager der EIGENEN Kolonie ladbar
    double stock = Warehouse.qty(state, fleet.locationColonyId, productTypeId);
    if (stock < quantity) throw new CommandException("Nicht genug Lagerbestand.");
    ProductType product = ProductCatalog.find(productTypeId);
    Capacity capacity = fleetCargoCapacity(fleet);
    Capacity used = fleetCargoUsed(fleet);
    if (used.massKg() + product.massKg * quantity > capacity.massKg() + 1e-6) throw new CommandException("Massekapazität der Flotte reicht nicht aus.");
    if (used.volumeM3() + product.volumeM3 * quantity > capacity.volumeM3() + 1e-6) throw new CommandException("Volumenkapazität der Flotte reicht nicht aus.");
    Warehouse.add(state, fleet.locationColonyId, productTypeId, -quantity);
    FleetCargo.add(fleet, productTypeId, quantity);
  }

  public static void unloadCargo(GameState state, String playerId, String fleetId, String productTypeId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    quantity = Math.floor(quantity); // Fracht bewegt sich nur in ganzen Stücken (Umsetzungskonzept/25_...md)
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    TroopTransportCommands.requireNotASoldier(productTypeId);
    if (fleet.status != FleetStatus.Stationed || fleet.locationColonyId == null) {
      throw new CommandException("Die Flotte muss bei einer Kolonie gelandet sein.");
    }
    GameQueries.requireOwnColony(state, playerId, fleet.locationColonyId);
    double have = FleetCargo.qty(fleet, productTypeId);
    if (have < quantity) throw new CommandException("Nicht genug Fracht an Bord.");
    FleetCargo.add(fleet, productTypeId, -quantity);
    Warehouse.add(state, fleet.locationColonyId, productTypeId, quantity);
  }

  /**
   * Lädt Fracht aus dem unbegrenzten Stations-Depot des Kommandanten in die
   * Flotte ({@code HubDepot}, Umsetzungskonzept/22_...md) – das Gegenstück zu
   * {@link #loadCargo}, nur an einer Handelsgilde-Station statt einer
   * Kolonie. Dieselbe Massen-/Volumengrenze wie dort gilt unverändert:
   * NUR Flotten mit Frachtern (die einzigen Schiffe mit Cargo-Kapazität &gt; 0)
   * können überhaupt etwas aufnehmen – eine eigene "Frachter-Pflicht" braucht
   * es dafür nicht.
   */
  public static void loadCargoFromHubDepot(GameState state, String playerId, String fleetId, String productTypeId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    quantity = Math.floor(quantity); // Fracht bewegt sich nur in ganzen Stücken (Umsetzungskonzept/25_...md)
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    TroopTransportCommands.requireNotASoldier(productTypeId);
    if (fleet.status != FleetStatus.Stationed || fleet.locationColonyId != null) {
      throw new CommandException("Die Flotte muss an einer Handelsgilde-Station stationiert sein.");
    }
    StarSystem sys = findSystem(state, fleet.systemId);
    if (sys == null || !sys.isTradeHub) throw new CommandException("Kein Depot außerhalb einer Handelsgilde-Station.");
    double stock = HubDepot.qty(state, fleet.systemId, playerId, productTypeId);
    if (stock < quantity) throw new CommandException("Nicht genug Bestand im Depot.");
    ProductType product = ProductCatalog.find(productTypeId);
    Capacity capacity = fleetCargoCapacity(fleet);
    Capacity used = fleetCargoUsed(fleet);
    if (used.massKg() + product.massKg * quantity > capacity.massKg() + 1e-6) throw new CommandException("Massekapazität der Flotte reicht nicht aus.");
    if (used.volumeM3() + product.volumeM3 * quantity > capacity.volumeM3() + 1e-6) throw new CommandException("Volumenkapazität der Flotte reicht nicht aus.");
    HubDepot.add(state, fleet.systemId, playerId, productTypeId, -quantity);
    FleetCargo.add(fleet, productTypeId, quantity);
  }

  /** Entlädt Fracht der Flotte in das Stations-Depot des Kommandanten – Gegenstück zu {@link #unloadCargo}. */
  public static void unloadCargoToHubDepot(GameState state, String playerId, String fleetId, String productTypeId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    quantity = Math.floor(quantity); // Fracht bewegt sich nur in ganzen Stücken (Umsetzungskonzept/25_...md)
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    TroopTransportCommands.requireNotASoldier(productTypeId);
    if (fleet.status != FleetStatus.Stationed || fleet.locationColonyId != null) {
      throw new CommandException("Die Flotte muss an einer Handelsgilde-Station stationiert sein.");
    }
    StarSystem sys = findSystem(state, fleet.systemId);
    if (sys == null || !sys.isTradeHub) throw new CommandException("Kein Depot außerhalb einer Handelsgilde-Station.");
    double have = FleetCargo.qty(fleet, productTypeId);
    if (have < quantity) throw new CommandException("Nicht genug Fracht an Bord.");
    FleetCargo.add(fleet, productTypeId, -quantity);
    HubDepot.add(state, fleet.systemId, playerId, productTypeId, quantity);
  }

  /**
   * Löst die Reise in einzelne Gateway-Sprünge auf ({@code Graph.bfsPath})
   * statt sie als einen einzigen, nicht unterbrechbaren Direktsprung zu
   * behandeln: nur der ERSTE Sprung wird sofort gestartet
   * ({@code destinationSystemId}), der Rest landet in {@code pendingHops}
   * und wird von {@link #processFleetArrivals} nach und nach automatisch
   * angeschlossen.
   */
  public static void moveFleet(GameState state, String playerId, String fleetId, String destinationSystemId) {
    moveFleet(state, playerId, fleetId, destinationSystemId, false);
  }

  public static void moveFleet(GameState state, String playerId, String fleetId, String destinationSystemId,
                               boolean viaCarrier) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist bereits unterwegs.");
    if (destinationSystemId.equals(fleet.systemId)) throw new CommandException("Die Flotte befindet sich bereits in diesem System.");
    if (state.systems.stream().noneMatch(s -> s.id.equals(destinationSystemId))) throw new CommandException("Unbekanntes Zielsystem.");
    if (viaCarrier) {
      startCarrierTransit(state, fleet, destinationSystemId);
      return;
    }
    List<String> path = Graph.bfsPath(GatewayCommands.gatewayRoutes(state), fleet.systemId, destinationSystemId);
    if (path == null || path.isEmpty()) throw new CommandException("Kein Gateway-Pfad zu diesem System bekannt.");
    consumeJumpFuel(state, fleet, path.size());
    // Wer das System verlässt, blockiert dort nichts mehr – dieselbe Regel wie beim
    // Ortswechsel innerhalb des Systems (moveFleetWithinSystem). Vorher blieb der
    // Blockade-Eintrag stehen: die Flotte war überall angreifbar (engageBattle prüft nur
    // die Existenz eines Eintrags) und konnte nach der Rückkehr keine neue Blockade
    // bilden ("wird bereits blockiert" – durch sich selbst), siehe Konzept 31 §H.
    state.blockades.removeIf(b -> b.fleetId.equals(fleetId));
    String firstHop = path.get(0);
    List<String> pendingHops = path.subList(1, path.size());
    long departedAt = Clock.now();
    long arrivesAt = departedAt + (long) Clock.hoursToMs(GameConstants.HOURS_PER_GATEWAY_HOP);

    fleet.status = FleetStatus.InTransit;
    fleet.destinationSystemId = firstHop;
    fleet.pendingHops = new ArrayList<>(pendingHops);
    fleet.departedAt = departedAt;
    fleet.arrivesAt = arrivesAt;
    fleet.locationType = FleetLocationType.System;
    fleet.locationColonyId = null;
    fleet.locationPlanetId = null;
    GameEvents.schedule(state, GameEventType.FLEET_ARRIVED, fleet.id, arrivesAt);
  }

  // ==========================================================================
  // Trägersprung ohne Gateway (Umsetzungskonzept/06_...md, "CarrierTransit")
  // ==========================================================================

  /**
   * Slots, die die Trägerschiffe einer Flotte bereitstellen. Ein Slot ist die
   * Masse einer Korvette (siehe {@code ShipTypeDef.carrierSlotCapacity}).
   */
  public static double carrierSlotCapacity(Fleet fleet) {
    double sum = 0;
    for (FleetShipGroup g : fleet.ships) {
      sum += ShipCatalog.find(g.shipProductTypeId).carrierSlotCapacity * g.quantity;
    }
    return sum;
  }

  /**
   * Slots, die die ÜBRIGEN Schiffe der Flotte belegen. Träger tragen sich nicht
   * selbst – sie fliegen den Sprung aus eigener Kraft und zählen deshalb nicht
   * gegen die eigene Kapazität ({@code carrierSlotUsage} ist beim Träger 0).
   */
  public static double carrierSlotLoad(Fleet fleet) {
    double sum = 0;
    for (FleetShipGroup g : fleet.ships) {
      sum += ShipCatalog.find(g.shipProductTypeId).carrierSlotUsage * g.quantity;
    }
    return sum;
  }

  /** Luftlinie zweier Systeme in Karteneinheiten (Galaxie-Koordinaten im Einheitsquadrat). */
  private static double systemDistance(StarSystem a, StarSystem b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    return Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * Mittlere Länge einer Gateway-Kante der TATSÄCHLICHEN Topologie – die
   * Bezugsgröße, in der ein Trägersprung in "Referenz-Sprünge" umgerechnet
   * wird. Bewusst gemessen statt geraten: Die Galaxie wird zufällig erzeugt und
   * wächst mit jedem neuen Kommandanten, eine feste Zahl wäre sofort veraltet.
   */
  public static double averageGatewayEdgeLength(GameState state) {
    double sum = 0;
    int count = 0;
    for (Graph.Route route : GatewayCommands.gatewayRoutes(state)) {
      // Jede ungerichtete Kante nur einmal zählen.
      if (route.a().compareTo(route.b()) >= 0) continue;
      StarSystem from = findSystem(state, route.a());
      StarSystem to = findSystem(state, route.b());
      if (from == null || to == null) continue;
      sum += systemDistance(from, to);
      count++;
    }
    return count > 0 ? sum / count : GameConstants.CARRIER_FALLBACK_HOP_DISTANCE;
  }

  /**
   * Vorschau eines Trägersprungs: Referenz-Sprünge, Dauer, Treibstoffbedarf und
   * die Slot-Bilanz. {@code possible} ist genau dann {@code false}, wenn die
   * Träger die übrigen Schiffe nicht fassen – dann steht in {@code reason},
   * woran es liegt.
   */
  public record CarrierJumpPreview(boolean possible, String reason, double slotsNeeded, double slotsAvailable,
                                   double referenceHops, double ms, double fuelNeeded, double fuelInTank) {
  }

  public static CarrierJumpPreview carrierJumpPreview(GameState state, String fleetId, String destinationSystemId) {
    Fleet fleet = find(state, fleetId);
    if (fleet == null) return null;
    StarSystem from = findSystem(state, fleet.systemId);
    StarSystem to = findSystem(state, destinationSystemId);
    if (from == null || to == null || from.id.equals(to.id)) return null;

    double capacity = carrierSlotCapacity(fleet);
    double load = carrierSlotLoad(fleet);
    double hops = referenceHops(state, from, to);
    double ms = Clock.hoursToMs(hops * GameConstants.HOURS_PER_GATEWAY_HOP * GameConstants.CARRIER_TRANSIT_TIME_FACTOR);
    double fuel = carrierJumpFuel(fleet, hops);

    if (capacity <= 0) {
      return new CarrierJumpPreview(false, "Keine Trägerschiffe in dieser Flotte.",
          load, capacity, hops, ms, fuel, fleet.fuelCapsules);
    }
    if (load > capacity + 1e-9) {
      return new CarrierJumpPreview(false, capacityShortfallMessage(fleet, load, capacity),
          load, capacity, hops, ms, fuel, fleet.fuelCapsules);
    }
    return new CarrierJumpPreview(true, null, load, capacity, hops, ms, fuel, fleet.fuelCapsules);
  }

  private static double referenceHops(GameState state, StarSystem from, StarSystem to) {
    double reference = averageGatewayEdgeLength(state);
    if (reference <= 0) reference = GameConstants.CARRIER_FALLBACK_HOP_DISTANCE;
    return Math.max(1, systemDistance(from, to) / reference);
  }

  private static double carrierJumpFuel(Fleet fleet, double referenceHops) {
    return jumpFuelPerHop(fleet) * referenceHops * GameConstants.CARRIER_TRANSIT_FUEL_FACTOR;
  }

  /**
   * Die Fehlermeldung, die den Sprung abbricht, wenn die Träger nicht reichen.
   * Sie nennt nicht nur "zu wenig Platz", sondern auch, wie viele Träger fehlen
   * bzw. wie viele Schiffe zurückbleiben müssten – sonst muss der Kommandant
   * die Slot-Rechnung selbst anstellen.
   */
  private static String capacityShortfallMessage(Fleet fleet, double load, double capacity) {
    double missing = load - capacity;
    double perCarrier = ShipCatalog.CATALOG.stream()
        .mapToDouble(s -> s.carrierSlotCapacity).max().orElse(0);
    String carrierHint = "";
    if (perCarrier > 0) {
      long needed = (long) Math.ceil(missing / perCarrier);
      carrierHint = " Es fehlt " + (needed == 1 ? "ein weiteres Trägerschiff" : needed + " weitere Trägerschiffe")
          + " – oder es müssen entsprechend weniger Schiffe mitfliegen.";
    }
    return "Die Trägerschiffe dieser Flotte fassen die übrigen Schiffe nicht: benötigt "
        + germanNumber(load) + " Slots, verfügbar " + germanNumber(capacity) + "." + carrierHint;
  }

  /**
   * Zahlen in Fehlermeldungen deutsch formatiert. {@link #round2} liefert einen
   * {@code double}, dessen Java-Standardausgabe einen PUNKT als Dezimaltrenner
   * hat ("600.0") – in einer sonst durchgehend deutschen Oberfläche liest sich
   * das falsch.
   */
  private static String germanNumber(double value) {
    return String.format(java.util.Locale.GERMANY, Math.abs(value - Math.rint(value)) < 1e-9 ? "%,.0f" : "%,.2f", value);
  }

  /**
   * Startet den Trägersprung. Anders als der Gateway-Flug ist das EIN einziger,
   * langer Sprung ohne Zwischenstationen: {@code pendingHops} bleibt leer, die
   * bestehende Ankunftsverarbeitung ({@link #processFleetArrivals}) setzt die
   * Flotte danach ganz normal auf {@code Stationed}.
   */
  private static void startCarrierTransit(GameState state, Fleet fleet, String destinationSystemId) {
    if (BattleCommands.activeBattleForFleet(state, fleet.id) != null) {
      throw new CommandException("Eine Flotte in einem laufenden Gefecht kann nicht springen – zuerst zurückziehen.");
    }
    CarrierJumpPreview preview = carrierJumpPreview(state, fleet.id, destinationSystemId);
    if (preview == null) throw new CommandException("Unbekanntes Zielsystem.");
    if (!preview.possible()) throw new CommandException(preview.reason());
    if (fleet.fuelCapsules + 1e-9 < preview.fuelNeeded()) {
      throw new CommandException("Nicht genug Treibstoff für den Trägersprung (benötigt "
          + germanNumber(preview.fuelNeeded()) + ", im Tank " + germanNumber(fleet.fuelCapsules)
          + ") – ein Trägersprung kostet das " + (long) GameConstants.CARRIER_TRANSIT_FUEL_FACTOR
          + "-fache eines Gateway-Sprungs.");
    }
    fleet.fuelCapsules = Math.max(0, fleet.fuelCapsules - preview.fuelNeeded());

    // Wer das System verlässt, blockiert dort nichts mehr – dieselbe Regel wie
    // beim Gateway-Flug und beim Ortswechsel innerhalb des Systems.
    state.blockades.removeIf(b -> b.fleetId.equals(fleet.id));

    long departedAt = Clock.now();
    fleet.status = FleetStatus.InTransit;
    fleet.destinationSystemId = destinationSystemId;
    fleet.pendingHops = new ArrayList<>();
    fleet.departedAt = departedAt;
    fleet.arrivesAt = departedAt + (long) preview.ms();
    fleet.locationType = FleetLocationType.System;
    fleet.locationColonyId = null;
    fleet.locationPlanetId = null;
    GameEvents.schedule(state, GameEventType.FLEET_ARRIVED, fleet.id, fleet.arrivesAt);
  }

  /**
   * Benennt eine Flotte um. Ohne das hießen alle neuen Flotten
   * "Flotte &lt;Kolonie&gt; 3", "... 4", "... 5" – ab etwa fünf Flotten war die
   * Übersicht nicht mehr lesbar.
   */
  public static void renameFleet(GameState state, String playerId, String fleetId, String name) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) throw new CommandException("Bitte einen Namen für die Flotte angeben.");
    if (trimmed.length() > 40) throw new CommandException("Der Flottenname darf höchstens 40 Zeichen lang sein.");
    fleet.name = trimmed;
  }

  /**
   * Fassungsvermögen des Treibstofftanks einer Flotte: die Summe der
   * Schiffstanks ({@code ShipTypeDef.fuelTankCapacity}, aus der Schiffsmasse
   * abgeleitet). Weil jeder Schiffstank auf dieselbe Zahl von Sprüngen
   * ausgelegt ist, hat jede Flotte dieselbe Reichweite –
   * {@code GameConstants.JUMP_FUEL_TANK_RANGE_HOPS}.
   */
  public static double fuelTankCapacity(Fleet fleet) {
    double sum = 0;
    for (FleetShipGroup g : fleet.ships) sum += ShipCatalog.find(g.shipProductTypeId).fuelTankCapacity * g.quantity;
    return sum;
  }

  /** Kapseln, die diese Flotte für EINEN Sprung verbraucht – Summe über die Schiffsmassen. */
  public static double jumpFuelPerHop(Fleet fleet) {
    double sum = 0;
    for (FleetShipGroup g : fleet.ships) sum += ShipCatalog.find(g.shipProductTypeId).jumpFuelPerHop * g.quantity;
    return sum;
  }

  /**
   * Kapseln, die sich aus einem Tank ENTNEHMEN lassen: nur ganze. Der Bruchteil
   * ist die bereits angebrochene Kapsel – sie ist teilweise verflogen und kann
   * nicht wieder ins Lager (Umsetzungskonzept/26_...md).
   */
  public static double drainableFuel(Fleet fleet) {
    return Math.floor(fleet.fuelCapsules);
  }

  /** Beschreibt, wo eine Flotte gerade Treibstoff aufnehmen/abgeben kann. */
  private record FuelPort(String colonyId, String hubSystemId) {
  }

  /**
   * Gegenstelle für Betanken/Abtanken am aktuellen Standort: das Lager der eigenen
   * Kolonie, bei der die Flotte gelandet ist, ODER das eigene Stationsdepot an
   * einer Handelsgilde-Station. Sonst gibt es hier nichts, wohin der Treibstoff
   * könnte.
   */
  private static FuelPort requireFuelPort(GameState state, String playerId, Fleet fleet) {
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist unterwegs.");
    if (fleet.locationColonyId != null) {
      GameQueries.requireOwnColony(state, playerId, fleet.locationColonyId);
      return new FuelPort(fleet.locationColonyId, null);
    }
    StarSystem sys = findSystem(state, fleet.systemId);
    if (sys != null && sys.isTradeHub) return new FuelPort(null, fleet.systemId);
    throw new CommandException("Treibstoff lässt sich nur bei einer eigenen Kolonie oder an einer "
        + "Handelsgilde-Station umschlagen – für gestrandete Flotten hilft eine andere eigene Flotte im selben System.");
  }

  private static double portStock(GameState state, String playerId, FuelPort port) {
    return port.colonyId() != null
        ? Warehouse.qty(state, port.colonyId(), GameConstants.JUMP_FUEL_PRODUCT_ID)
        : HubDepot.qty(state, port.hubSystemId(), playerId, GameConstants.JUMP_FUEL_PRODUCT_ID);
  }

  private static void portAdd(GameState state, String playerId, FuelPort port, double delta) {
    if (port.colonyId() != null) Warehouse.add(state, port.colonyId(), GameConstants.JUMP_FUEL_PRODUCT_ID, delta);
    else HubDepot.add(state, port.hubSystemId(), playerId, GameConstants.JUMP_FUEL_PRODUCT_ID, delta);
  }

  private static void addToTank(Fleet fleet, double quantity) {
    double free = fuelTankCapacity(fleet) - fleet.fuelCapsules;
    if (free + 1e-9 < quantity) {
      throw new CommandException("Der Tank fasst nur noch " + round2(Math.max(free, 0))
          + " Kapseln (" + round2(fuelTankCapacity(fleet)) + " je Flotte, davon "
          + round2(fleet.fuelCapsules) + " belegt).");
    }
    fleet.fuelCapsules += quantity;
  }

  /**
   * Betankt eine Flotte aus dem Lager der eigenen Kolonie bzw. dem eigenen
   * Stationsdepot, an dem sie gerade liegt (Umsetzungskonzept/26_...md).
   * Bewusst ein EIGENER Befehl und kein Nebeneffekt des Reisens: nur so ist
   * eindeutig, welche Kapseln tatsächlich an Bord und damit für den Verbrauch
   * freigegeben sind.
   */
  public static void refuelFleet(GameState state, String playerId, String fleetId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    quantity = Math.floor(quantity);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    FuelPort port = requireFuelPort(state, playerId, fleet);
    double stock = Math.floor(portStock(state, playerId, port));
    if (stock < quantity) {
      throw new CommandException("Nicht genug Eleriumkapseln am Standort (benötigt "
          + round2(quantity) + ", vorhanden " + round2(stock) + ").");
    }
    addToTank(fleet, quantity);
    portAdd(state, playerId, port, -quantity);
  }

  /**
   * Abtanken: gibt GANZE Kapseln aus dem Tank zurück ins Kolonielager bzw. ins
   * Stationsdepot. Der Tank darf damit ausdrücklich auch als Lager dienen
   * (Nutzervorgabe) – die angebrochene Kapsel bleibt aber an Bord, weil sie
   * bereits teilweise verflogen ist (siehe {@link #drainableFuel}).
   */
  public static void drainFleetFuel(GameState state, String playerId, String fleetId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    quantity = Math.floor(quantity);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    FuelPort port = requireFuelPort(state, playerId, fleet);
    double drainable = drainableFuel(fleet);
    if (drainable < quantity) {
      throw new CommandException("Nur " + round2(drainable) + " ganze Kapseln entnehmbar (im Tank "
          + round2(fleet.fuelCapsules) + " – eine angebrochene Kapsel bleibt an Bord).");
    }
    fleet.fuelCapsules -= quantity;
    portAdd(state, playerId, port, quantity);
  }

  /**
   * Treibstoff von einer eigenen Flotte zu einer anderen im SELBEN System – der
   * Rettungsweg für gestrandete Flotten, die keine Kolonie und keine Station
   * erreichen. Auch hier wandern nur ganze Kapseln, die angebrochene bleibt beim
   * Geber.
   */
  public static void transferFuelBetweenFleets(GameState state, String playerId, String fromFleetId,
                                                String toFleetId, double quantity) {
    Fleet from = requireOwnFleet(state, playerId, fromFleetId);
    Fleet to = requireOwnFleet(state, playerId, toFleetId);
    quantity = Math.floor(quantity);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    if (from.id.equals(to.id)) throw new CommandException("Quelle und Ziel sind dieselbe Flotte.");
    if (from.status != FleetStatus.Stationed || to.status != FleetStatus.Stationed) {
      throw new CommandException("Beide Flotten müssen stationiert sein.");
    }
    if (!from.systemId.equals(to.systemId)) {
      throw new CommandException("Beide Flotten müssen sich im selben System befinden.");
    }
    double drainable = drainableFuel(from);
    if (drainable < quantity) {
      throw new CommandException("Nur " + round2(drainable) + " ganze Kapseln entnehmbar (im Tank "
          + round2(from.fuelCapsules) + " – eine angebrochene Kapsel bleibt an Bord).");
    }
    addToTank(to, quantity);
    from.fuelCapsules -= quantity;
  }

  /**
   * Verbraucht Eleriumkapseln für einen kompletten (ggf. mehrsprungigen) Flug:
   * Kosten = Sprünge × Verbrauch der Flotte je Sprung, und der hängt an der MASSE
   * der Schiffe ({@link #jumpFuelPerHop}, Umsetzungskonzept/34_...md).
   * Seit Umsetzungskonzept/26_...md kommen sie AUSSCHLIESSLICH aus dem eigenen Tank
   * der Flotte – nicht mehr aus entfernten Kolonielagern. Wer fliegen will, muss
   * vorher betankt haben ({@link #refuelFleet}).
   */
  private static void consumeJumpFuel(GameState state, Fleet fleet, int hops) {
    double needed = jumpFuelPerHop(fleet) * hops;
    if (needed <= 0) return;
    // Verlorene Schiffe können den Tank über sein Fassungsvermögen heben – überzähliger
    // Treibstoff verfällt beim nächsten Flug.
    fleet.fuelCapsules = Math.min(fleet.fuelCapsules, fuelTankCapacity(fleet));
    if (fleet.fuelCapsules + 1e-9 < needed) {
      throw new CommandException("Nicht genug Treibstoff im Tank (benötigt "
          + round2(needed) + ", im Tank " + round2(fleet.fuelCapsules) + ") – die Flotte muss betankt werden.");
    }
    // Kein Übertragskonto mehr für Treibstoff: der Tank selbst führt den Bruchteil,
    // und dieser Bruchteil IST die angebrochene Kapsel (Umsetzungskonzept/26_...md).
    // Sie bleibt an Bord und lässt sich nicht mehr abtanken – nur ganze Kapseln
    // verlassen den Tank wieder ({@link #drainableFuel}).
    fleet.fuelCapsules = Math.max(0, fleet.fuelCapsules - needed);
  }

  private static double round2(double v) {
    return Math.round(v * 100) / 100.0;
  }

  /**
   * Bricht eine unterwegs befindliche Flotte ab: der bereits laufende
   * Gateway-Sprung wird noch zu Ende geflogen, aber alle weiteren geplanten
   * Sprünge entfallen.
   */
  public static void cancelFleetMove(GameState state, String playerId, String fleetId) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.InTransit) throw new CommandException("Die Flotte ist nicht unterwegs.");
    if (fleet.pendingHops.isEmpty()) throw new CommandException("Der letzte Sprung läuft bereits – die Flotte kommt gleich an.");
    fleet.pendingHops = List.of();
  }

  public record RoutePreview(int hops, double ms) {
  }

  public static RoutePreview routePreview(GameState state, String fleetId, String destinationSystemId) {
    Fleet fleet = find(state, fleetId);
    if (fleet == null || destinationSystemId.equals(fleet.systemId)) return null;
    var hopsMap = Graph.bfsHops(GatewayCommands.gatewayRoutes(state), fleet.systemId);
    Integer hops = hopsMap.get(destinationSystemId);
    if (hops == null) return null;
    return new RoutePreview(hops, Clock.hoursToMs(hops * GameConstants.HOURS_PER_GATEWAY_HOP));
  }

  /**
   * Routenvorschau zu ALLEN erreichbaren Systemen in EINER Abfrage (eine
   * Breitensuche). Die Zielauswahl der Flottenübersicht fragte vorher für
   * jedes der rund 200 Systeme einzeln {@link #routePreview} ab – 200 Suchen je
   * Sekunde und Flotte, solange das Bewegen-Feld offen war. Unerreichbare
   * Systeme und das eigene fehlen im Ergebnis.
   */
  public static Map<String, RoutePreview> routePreviewsFrom(GameState state, String fleetId) {
    Fleet fleet = find(state, fleetId);
    if (fleet == null) return Map.of();
    Map<String, RoutePreview> out = new HashMap<>();
    for (Map.Entry<String, Integer> e : Graph.bfsHops(GatewayCommands.gatewayRoutes(state), fleet.systemId).entrySet()) {
      int hops = e.getValue();
      if (hops == 0) continue;
      out.put(e.getKey(), new RoutePreview(hops, Clock.hoursToMs(hops * GameConstants.HOURS_PER_GATEWAY_HOP)));
    }
    return out;
  }

  /**
   * Instant-Bewegung (keine Flugzeit) zwischen den drei Orten desselben
   * Systems, siehe {@link FleetSystemTarget}/{@link FleetLocationType}.
   */
  public static void moveFleetWithinSystem(GameState state, IdGenerator ids, String playerId, String fleetId,
                                           FleetSystemTarget target) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist unterwegs.");
    if (BattleCommands.activeBattleForFleet(state, fleetId) != null) {
      throw new CommandException("Eine Flotte in einem laufenden Gefecht kann sich nicht bewegen – zuerst zurückziehen.");
    }
    // Blockierter Orbit (Umsetzungskonzept/34_...md, §J 7): VOR der Bewegung
    // ermittelt, ausgelöst NACH ihr – die Flotte fliegt ein und steht dann im
    // Gefecht, statt an der Grenze abgewiesen zu werden.
    Blockade blocking = BlockadeCommands.orbitBlockadeAgainst(state, playerId, targetPlanetId(state, target));
    if (blocking != null) BlockadeCommands.requireBreakthroughPossible(state, playerId, blocking);
    if (target instanceof FleetSystemTarget.System) {
      fleet.locationType = FleetLocationType.System;
      fleet.locationColonyId = null;
      fleet.locationPlanetId = null;
    } else if (target instanceof FleetSystemTarget.PlanetOrbit po) {
      Planet planet = ColonyCommands.planet(state, po.planetId());
      if (planet == null) throw new CommandException("Unbekannter Planet.");
      if (!planet.systemId.equals(fleet.systemId)) throw new CommandException("Der Planet liegt nicht in diesem System.");
      fleet.locationType = FleetLocationType.PlanetOrbit;
      fleet.locationColonyId = null;
      fleet.locationPlanetId = po.planetId();
    } else if (target instanceof FleetSystemTarget.ColonyOrbit co) {
      Colony colony = ColonyCommands.colony(state, co.colonyId());
      if (colony == null) throw new CommandException("Unbekannte Kolonie.");
      if (!colony.systemId.equals(fleet.systemId)) throw new CommandException("Die Kolonie liegt nicht in diesem System.");
      fleet.locationType = FleetLocationType.ColonyOrbit;
      fleet.locationColonyId = co.colonyId();
      fleet.locationPlanetId = colony.planetId;
    }
    // Ein Ortswechsel hebt eine eigene Blockade an diesem Ort automatisch auf – man kann nicht blockieren, wo man nicht mehr ist.
    state.blockades.removeIf(b -> b.fleetId.equals(fleetId));
    if (blocking != null) BlockadeCommands.breakThroughOrbitBlockade(state, ids, playerId, fleetId, blocking);
  }

  /** Der Planet, um den es beim Ortswechsel geht – {@code null} beim Systemhandelsposten, der nie blockiert ist. */
  private static String targetPlanetId(GameState state, FleetSystemTarget target) {
    if (target instanceof FleetSystemTarget.PlanetOrbit po) return po.planetId();
    if (target instanceof FleetSystemTarget.ColonyOrbit co) {
      Colony colony = ColonyCommands.colony(state, co.colonyId());
      return colony != null ? colony.planetId : null;
    }
    return null;
  }

  /**
   * Erforscht das System, in dem die Flotte gerade steht ({@code Stationed}, unabhängig vom
   * genauen Ort im System und vom Schiffstyp – jede eigene Flotte kann das) und deckt damit die
   * Rohstoffkonzentration aller dortigen Planeten für diesen Kommandanten auf (siehe
   * {@code GatewayCommands.hasExploredSystem}). Bloßes Durchreisen/Ankommen ({@code moveFleet})
   * reicht dafür bewusst NICHT – das ist der Unterschied zu {@code knownSystemIdsByPlayer}
   * ("besucht"), das weiterhin automatisch bei Ankunft gesetzt wird.
   */
  public static void exploreSystem(GameState state, String playerId, String fleetId) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte muss im System stationiert sein, um es zu erforschen.");
    if (GatewayCommands.hasExploredSystem(state, playerId, fleet.systemId)) {
      throw new CommandException("Dieses System ist bereits erforscht.");
    }
    state.exploredSystemIdsByPlayer.computeIfAbsent(playerId, k -> new LinkedHashSet<>()).add(fleet.systemId);
  }

  /**
   * Ereignis {@code FLEET_ARRIVED}: der laufende Sprung ist zu Ende. Ein
   * mehrsprungiger Flug ({@code pendingHops}) wird hop-für-hop abgearbeitet –
   * nach jedem Sprung entscheidet sich neu, ob es weiter zum nächsten geht
   * (der dann ab {@code at} geplant wird) oder die Flotte hier als
   * {@code Stationed} stehen bleibt. Veraltet, wenn die Flotte inzwischen
   * verschwunden ist oder eine andere Ankunft trägt.
   */
  static void arrive(GameState state, IdGenerator ids, String fleetId, long at) {
    Fleet fleet = find(state, fleetId);
    if (fleet == null || fleet.status != FleetStatus.InTransit || fleet.arrivesAt == null || fleet.arrivesAt != at) return;
    String reachedSystemId = fleet.destinationSystemId;
    List<String> hops = fleet.pendingHops;
    if (!hops.isEmpty()) {
      String nextHop = hops.get(0);
      List<String> restHops = hops.subList(1, hops.size());
      long arrivesAt = at + (long) Clock.hoursToMs(GameConstants.HOURS_PER_GATEWAY_HOP);
      fleet.systemId = reachedSystemId;
      fleet.destinationSystemId = nextHop;
      fleet.pendingHops = new ArrayList<>(restHops);
      fleet.departedAt = at;
      fleet.arrivesAt = arrivesAt;
      GameEvents.schedule(state, GameEventType.FLEET_ARRIVED, fleet.id, arrivesAt);
    } else {
      fleet.status = FleetStatus.Stationed;
      fleet.locationType = FleetLocationType.System;
      fleet.locationColonyId = null;
      fleet.locationPlanetId = null;
      fleet.systemId = reachedSystemId;
      fleet.destinationSystemId = null;
      fleet.pendingHops = List.of();
      fleet.departedAt = null;
      fleet.arrivesAt = null;
      StarSystem arrived = findSystem(state, reachedSystemId);
      // An den EIGENTÜMER der Flotte, nicht global: eine Meldung ohne Adresse
      // (colonyId und playerId beide null) ist für JEDEN Kommandanten sichtbar –
      // ein frisch registrierter Spieler fand so in seiner Glocke hunderte
      // Ankunftsmeldungen fremder NPC-Flotten.
      Notifications.notifyPlayer(state, ids, de.nebula.model.NotificationType.Info, Notifications.CODE_FLEET_ARRIVED,
          "\"" + fleet.name + "\" ist in " + (arrived != null ? arrived.name : reachedSystemId) + " angekommen.",
          fleet.ownerId, "/flotten");
    }
    Set<String> known = state.knownSystemIdsByPlayer.computeIfAbsent(fleet.ownerId, k -> new LinkedHashSet<>());
    known.add(reachedSystemId);
  }
}
