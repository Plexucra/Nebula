package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.ShipCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.engine.Graph;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

  public static List<Fleet> allFleets(GameState state) {
    return List.copyOf(state.fleets);
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
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist bereits unterwegs.");
    if (destinationSystemId.equals(fleet.systemId)) throw new CommandException("Die Flotte befindet sich bereits in diesem System.");
    if (state.systems.stream().noneMatch(s -> s.id.equals(destinationSystemId))) throw new CommandException("Unbekanntes Zielsystem.");
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
  }

  /** Fassungsvermögen des Treibstofftanks einer Flotte: {@code JUMP_FUEL_TANK_PER_SHIP} je Schiff. */
  public static double fuelTankCapacity(Fleet fleet) {
    double ships = fleet.ships.stream().mapToDouble(g -> g.quantity).sum();
    return ships * GameConstants.JUMP_FUEL_TANK_PER_SHIP;
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
   * Verbraucht Eleriumkapseln für einen kompletten (ggf. mehrsprungigen) Flug, siehe
   * {@code GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP}: Kosten = Schiffe der Flotte × Sprünge.
   * Seit Umsetzungskonzept/26_...md kommen sie AUSSCHLIESSLICH aus dem eigenen Tank
   * der Flotte – nicht mehr aus entfernten Kolonielagern. Wer fliegen will, muss
   * vorher betankt haben ({@link #refuelFleet}).
   */
  private static void consumeJumpFuel(GameState state, Fleet fleet, int hops) {
    double totalShips = fleet.ships.stream().mapToDouble(g -> g.quantity).sum();
    double needed = totalShips * hops * GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP;
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
   * Instant-Bewegung (keine Flugzeit) zwischen den drei Orten desselben
   * Systems, siehe {@link FleetSystemTarget}/{@link FleetLocationType}.
   */
  public static void moveFleetWithinSystem(GameState state, String playerId, String fleetId, FleetSystemTarget target) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist unterwegs.");
    if (BattleCommands.activeBattleForFleet(state, fleetId) != null) {
      throw new CommandException("Eine Flotte in einem laufenden Gefecht kann sich nicht bewegen – zuerst zurückziehen.");
    }
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
   * Ereignisbasiert: einziger Zeitvergleich je unterwegs befindlicher
   * Flotte. Ein mehrsprungiger Flug ({@code pendingHops}) wird
   * hop-für-hop abgearbeitet – nach jedem Sprung entscheidet dieser Tick
   * neu, ob es weiter zum nächsten Sprung geht oder die Flotte hier als
   * {@code Stationed} stehen bleibt.
   */
  public static void processFleetArrivals(GameState state, long t) {
    List<Fleet> due = state.fleets.stream()
        .filter(f -> f.status == FleetStatus.InTransit && f.arrivesAt != null && f.arrivesAt <= t)
        .toList();
    for (Fleet fleet : due) {
      String reachedSystemId = fleet.destinationSystemId;
      List<String> hops = fleet.pendingHops;
      if (!hops.isEmpty()) {
        String nextHop = hops.get(0);
        List<String> restHops = hops.subList(1, hops.size());
        long departedAt = t;
        long arrivesAt = departedAt + (long) Clock.hoursToMs(GameConstants.HOURS_PER_GATEWAY_HOP);
        fleet.systemId = reachedSystemId;
        fleet.destinationSystemId = nextHop;
        fleet.pendingHops = new ArrayList<>(restHops);
        fleet.departedAt = departedAt;
        fleet.arrivesAt = arrivesAt;
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
      }
      Set<String> known = state.knownSystemIdsByPlayer.computeIfAbsent(fleet.ownerId, k -> new LinkedHashSet<>());
      known.add(reachedSystemId);
    }
  }
}
