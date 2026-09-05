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
import de.nebula.model.FleetSystemTarget;
import de.nebula.model.Planet;
import de.nebula.model.ProductType;
import de.nebula.model.ShipTypeDef;

import java.util.ArrayList;
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

  private record Capacity(double massKg, double volumeM3) {
  }

  private static Capacity fleetCargoCapacity(Fleet fleet) {
    double mass = 0, volume = 0;
    for (FleetShipGroup g : fleet.ships) {
      ShipTypeDef def = ShipCatalog.find(g.shipProductTypeId);
      mass += def.cargoMassKg * g.quantity;
      volume += def.cargoVolumeM3 * g.quantity;
    }
    return new Capacity(mass, volume);
  }

  private static Capacity fleetCargoUsed(Fleet fleet) {
    double mass = 0, volume = 0;
    for (FleetCargoEntry c : fleet.cargo) {
      ProductType product = ProductCatalog.find(c.productTypeId);
      mass += product.massKg * c.quantity;
      volume += product.volumeM3 * c.quantity;
    }
    return new Capacity(mass, volume);
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

  public static void loadCargo(GameState state, String playerId, String fleetId, String productTypeId, double quantity) {
    Fleet fleet = requireOwnFleet(state, playerId, fleetId);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
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
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
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
