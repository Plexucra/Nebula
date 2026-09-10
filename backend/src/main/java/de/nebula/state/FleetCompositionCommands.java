package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.GameConstants;
import de.nebula.model.Fleet;
import de.nebula.model.FleetCargoEntry;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.ProductType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flottenzusammenstellung (Umsetzungskonzept/33_...md): zwei Flotten am selben
 * Ort zu einer zusammenlegen, oder aus einer Flotte eine zweite abspalten.
 * Bis hierher war die Zusammensetzung einer Flotte praktisch unveränderlich –
 * Schiffe kamen nur über {@code transferShipsToFleet} aus dem Kolonielager
 * hinzu, und eine einmal gebildete Flotte ließ sich nie wieder teilen. Eine
 * Landungsoperation musste deshalb zwangsläufig als drei getrennte Flotten
 * reisen (Mannschaftstransporter, Drohnenfrachter, Eskorte), siehe Konzept 31
 * §J, Punkt 11.
 *
 * <p><b>Die eine Regel, die beides zusammenhält</b>: eine Flotte darf nie mehr
 * tragen, als ihre Schiffe fassen. Beim Zusammenlegen ist das geschenkt (beide
 * Kapazitäten addieren sich mit ihrer Ladung), beim Aufteilen ist es die
 * eigentliche Prüfung – und zwar auf BEIDEN Seiten: die abgespaltene Flotte
 * muss ihre Fracht und ihre Soldaten fassen, und die zurückbleibende ebenso.
 * Wer alle Frachter mitnimmt, kann die Ware nicht zurücklassen; wer alle
 * Mannschaftstransporter mitnimmt, muss auch die Soldaten mitnehmen
 * (Umsetzungskonzept/28_...md: Soldaten reisen ausschließlich im
 * Mannschaftstransporter, Drohnen als gewöhnliche Fracht).</p>
 */
public final class FleetCompositionCommands {
  private FleetCompositionCommands() {
  }

  // --- Zusammenlegen ---------------------------------------------------------

  /**
   * Legt {@code sourceFleetId} in {@code targetFleetId} zusammen: Schiffe,
   * Fracht, Treibstoff und eingeschiffte Truppen wandern hinüber, danach
   * existiert die Quellflotte nicht mehr. Beide müssen am selben Ort stehen –
   * "am selben Ort" heißt dasselbe System UND dieselbe Position darin (freier
   * Raum, Planetenorbit oder Kolonieorbit), denn ein Ortswechsel innerhalb des
   * Systems ist ein eigener Befehl ({@code moveFleetWithinSystem}).
   */
  public static void mergeFleets(GameState state, String playerId, String targetFleetId, String sourceFleetId) {
    if (Objects.equals(targetFleetId, sourceFleetId)) throw new CommandException("Eine Flotte kann nicht mit sich selbst zusammengelegt werden.");
    Fleet target = FleetCommands.requireOwnFleet(state, playerId, targetFleetId);
    Fleet source = FleetCommands.requireOwnFleet(state, playerId, sourceFleetId);
    requireIdleAndFree(state, target, "Die Zielflotte");
    requireIdleAndFree(state, source, "Die aufzulösende Flotte");
    if (!sameLocation(target, source)) {
      throw new CommandException("Beide Flotten müssen am selben Ort stehen – dasselbe System und dieselbe Position darin.");
    }

    List<FleetShipGroup> ships = new ArrayList<>(target.ships);
    for (FleetShipGroup g : source.ships) {
      if (g.quantity <= 0) continue;
      FleetShipGroup existing = null;
      for (FleetShipGroup t : ships) if (t.shipProductTypeId.equals(g.shipProductTypeId)) existing = t;
      if (existing != null) existing.quantity += g.quantity;
      else ships.add(new FleetShipGroup(g.shipProductTypeId, g.quantity));
    }
    target.ships = ships;
    for (FleetCargoEntry c : source.cargo) {
      if (c.quantity > 0) FleetCargo.add(target, c.productTypeId, c.quantity);
    }
    // Der Tank hängt an den Schiffen: mit ihnen wächst auch das Fassungsvermögen,
    // der Inhalt beider Tanks passt deshalb immer zusammen.
    target.fuelCapsules = round2(target.fuelCapsules + source.fuelCapsules);

    GroundForceGroup aboardSource = TroopTransportCommands.embarkedForces(state, source.id);
    if (aboardSource != null) {
      GroundForceGroup aboardTarget = TroopTransportCommands.embarkedForces(state, target.id);
      if (aboardTarget == null) {
        // Kein eigener Verband am Ziel: den mitgebrachten einfach umhängen.
        aboardSource.fleetId = target.id;
      } else {
        for (var stack : aboardSource.units) {
          double total = stack.activeCount + stack.reserveCount;
          if (total > 0) TroopTransportCommands.add(aboardTarget, stack.unitProductTypeId, total);
        }
        state.groundForceGroups.remove(aboardSource);
      }
    }

    state.blockades.removeIf(b -> b.fleetId.equals(source.id));
    state.fleets.remove(source);
  }

  // --- Aufteilen -------------------------------------------------------------

  /**
   * Spaltet aus {@code fleetId} eine neue Flotte ab. {@code ships} und
   * {@code cargo} nennen, was mitgeht (Produkt-Id → Stückzahl, nur ganze
   * Stücke), {@code soldiers} wie viele der eingeschifften Soldaten wechseln.
   * Die Ursprungsflotte behält den Rest und muss mindestens ein Schiff
   * behalten – wer alles mitnehmen will, benennt die Flotte einfach um.
   *
   * @return die neue Flotte
   */
  public static Fleet splitFleet(GameState state, IdGenerator ids, String playerId, String fleetId,
                                  Map<String, Double> ships, Map<String, Double> cargo, double soldiers,
                                  String newFleetName) {
    Fleet source = FleetCommands.requireOwnFleet(state, playerId, fleetId);
    requireIdleAndFree(state, source, "Die Flotte");

    Map<String, Double> shipsToMove = wholeUnits(ships, "Schiffe");
    Map<String, Double> cargoToMove = wholeUnits(cargo, "Fracht");
    double soldiersToMove = Math.floor(Math.max(0, soldiers));
    if (shipsToMove.isEmpty()) throw new CommandException("Für eine neue Flotte muss mindestens ein Schiff abgespalten werden.");

    // 1. Bestände prüfen und die künftigen Schiffslisten beider Seiten bilden.
    List<FleetShipGroup> newShips = new ArrayList<>();
    List<FleetShipGroup> remainingShips = new ArrayList<>();
    Map<String, Double> pending = new LinkedHashMap<>(shipsToMove);
    for (FleetShipGroup g : source.ships) {
      double move = pending.getOrDefault(g.shipProductTypeId, 0.0);
      if (move > g.quantity) {
        throw new CommandException("Die Flotte hat nur " + (long) g.quantity + " × "
            + ProductCatalog.find(g.shipProductTypeId).name + ".");
      }
      pending.remove(g.shipProductTypeId);
      if (move > 0) newShips.add(new FleetShipGroup(g.shipProductTypeId, move));
      if (g.quantity - move > 0) remainingShips.add(new FleetShipGroup(g.shipProductTypeId, g.quantity - move));
    }
    if (!pending.isEmpty()) {
      throw new CommandException("Diese Flotte hat keine Schiffe des Typs " + ProductCatalog.find(pending.keySet().iterator().next()).name + ".");
    }
    if (remainingShips.isEmpty()) {
      throw new CommandException("Die Ursprungsflotte muss mindestens ein Schiff behalten – zum Umbenennen ist kein Aufteilen nötig.");
    }

    // 2. Fracht prüfen und aufteilen.
    List<FleetCargoEntry> newCargo = new ArrayList<>();
    List<FleetCargoEntry> remainingCargo = new ArrayList<>();
    Map<String, Double> pendingCargo = new LinkedHashMap<>(cargoToMove);
    for (FleetCargoEntry c : source.cargo) {
      double move = pendingCargo.getOrDefault(c.productTypeId, 0.0);
      if (move > c.quantity) {
        throw new CommandException("Die Flotte hat nur " + (long) c.quantity + " × "
            + ProductCatalog.find(c.productTypeId).name + " an Bord.");
      }
      pendingCargo.remove(c.productTypeId);
      if (move > 0) newCargo.add(cargoEntry(c.productTypeId, move));
      if (c.quantity - move > 0) remainingCargo.add(cargoEntry(c.productTypeId, c.quantity - move));
    }
    if (!pendingCargo.isEmpty()) {
      throw new CommandException("Diese Flotte hat kein " + ProductCatalog.find(pendingCargo.keySet().iterator().next()).name + " an Bord.");
    }

    // 3. Soldaten prüfen (Umsetzungskonzept/28_...md: nur im Mannschaftstransporter).
    GroundForceGroup aboard = TroopTransportCommands.embarkedForces(state, source.id);
    double soldiersAboard = aboard == null ? 0 : TroopTransportCommands.count(aboard, GameConstants.SOLDIER_PRODUCT_ID);
    if (soldiersToMove > soldiersAboard) {
      throw new CommandException("Es sind nur " + (long) soldiersAboard + " Soldaten an Bord.");
    }

    // 4. Die eigentliche Regel: beide Seiten müssen tragen können, was sie behalten.
    requireCargoFits(newShips, newCargo, "Die abgespaltene Flotte");
    requireCargoFits(remainingShips, remainingCargo, "Die Ursprungsflotte");
    requireTroopsFit(newShips, soldiersToMove, "Die abgespaltene Flotte");
    requireTroopsFit(remainingShips, soldiersAboard - soldiersToMove, "Die Ursprungsflotte");

    // 5. Treibstoff geht anteilig mit: der Tank hängt an den Schiffen
    // (JUMP_FUEL_TANK_PER_SHIP je Schiff), also folgt der Inhalt demselben
    // Verhältnis. Verschieben lässt er sich danach mit transferFuelBetweenFleets.
    double totalShips = countShips(source.ships);
    double movedShips = countShips(newShips);
    double fuelForNew = totalShips > 0 ? round2(source.fuelCapsules * (movedShips / totalShips)) : 0;

    Fleet fleet = new Fleet();
    fleet.id = ids.next("flt");
    fleet.ownerId = source.ownerId;
    fleet.name = fleetName(state, source, newFleetName);
    fleet.systemId = source.systemId;
    fleet.status = FleetStatus.Stationed;
    fleet.locationType = source.locationType;
    fleet.locationColonyId = source.locationColonyId;
    fleet.locationPlanetId = source.locationPlanetId;
    fleet.ships = newShips;
    fleet.cargo = newCargo;
    fleet.fuelCapsules = fuelForNew;
    fleet.destinationSystemId = null;
    fleet.pendingHops = List.of();
    fleet.departedAt = null;
    fleet.arrivesAt = null;
    state.fleets.add(fleet);

    source.ships = remainingShips;
    source.cargo = remainingCargo;
    source.fuelCapsules = round2(source.fuelCapsules - fuelForNew);

    if (soldiersToMove > 0) {
      TroopTransportCommands.remove(aboard, GameConstants.SOLDIER_PRODUCT_ID, soldiersToMove);
      GroundForceGroup moved = new GroundForceGroup();
      moved.id = ids.next("gfg");
      moved.ownerId = source.ownerId;
      moved.colonyId = null;
      moved.fleetId = fleet.id;
      moved.units = new ArrayList<>();
      state.groundForceGroups.add(moved);
      // An Bord kommandiert niemand Drohnen – Soldaten stehen dort vollständig in
      // der Reserve, genau wie beim Einschiffen (TroopTransportCommands.embarkSoldiers).
      TroopTransportCommands.add(moved, GameConstants.SOLDIER_PRODUCT_ID, soldiersToMove);
      if (TroopTransportCommands.isEmpty(aboard)) state.groundForceGroups.remove(aboard);
    }
    return fleet;
  }

  // --- Hilfen ---------------------------------------------------------------

  /** Stationiert, nicht unterwegs, nicht im Gefecht – sonst ist die Zusammensetzung tabu. */
  private static void requireIdleAndFree(GameState state, Fleet fleet, String label) {
    if (fleet.status != FleetStatus.Stationed) throw new CommandException(label + " ist unterwegs.");
    if (BattleCommands.activeBattleForFleet(state, fleet.id) != null) {
      throw new CommandException(label + " befindet sich in einem laufenden Gefecht.");
    }
  }

  private static boolean sameLocation(Fleet a, Fleet b) {
    return a.systemId.equals(b.systemId)
        && a.locationType == b.locationType
        && Objects.equals(a.locationColonyId, b.locationColonyId)
        && Objects.equals(a.locationPlanetId, b.locationPlanetId);
  }

  private static void requireCargoFits(List<FleetShipGroup> ships, List<FleetCargoEntry> cargo, String label) {
    FleetCommands.Capacity capacity = FleetCommands.cargoCapacityOf(ships);
    FleetCommands.Capacity used = FleetCommands.cargoUsedOf(cargo);
    if (used.massKg() > capacity.massKg() + 1e-6) {
      throw new CommandException(label + " kann ihre Fracht nicht tragen: " + Math.round(used.massKg() / 1000)
          + " t bei " + Math.round(capacity.massKg() / 1000) + " t Kapazität.");
    }
    if (used.volumeM3() > capacity.volumeM3() + 1e-6) {
      throw new CommandException(label + " hat zu wenig Laderaum: " + Math.round(used.volumeM3())
          + " m³ bei " + Math.round(capacity.volumeM3()) + " m³ Kapazität.");
    }
  }

  private static void requireTroopsFit(List<FleetShipGroup> ships, double soldiers, String label) {
    if (soldiers <= 0) return;
    double capacity = TroopTransportCommands.troopCapacity(ships);
    if (soldiers > capacity + 1e-6) {
      throw new CommandException(label + " hat nur Platz für " + (long) capacity + " Soldaten, es sollen aber "
          + (long) soldiers + " mitfahren – Mannschaftstransporter mitgeben oder Soldaten anders aufteilen.");
    }
  }

  /** Nur ganze Stücke, nur positive Mengen (Umsetzungskonzept/25_...md); Nullwerte fallen still weg. */
  private static Map<String, Double> wholeUnits(Map<String, Double> input, String what) {
    Map<String, Double> result = new LinkedHashMap<>();
    if (input == null) return result;
    for (Map.Entry<String, Double> e : input.entrySet()) {
      double quantity = Math.floor(e.getValue() == null ? 0 : e.getValue());
      if (quantity <= 0) continue;
      ProductType product = ProductCatalog.find(e.getKey()); // wirft bei unbekannter Id
      result.put(product.id, quantity);
    }
    if (result.isEmpty() && input.values().stream().anyMatch(v -> v != null && v > 0 && v < 1)) {
      throw new CommandException(what + " lassen sich nur in ganzen Stücken aufteilen.");
    }
    return result;
  }

  private static FleetCargoEntry cargoEntry(String productTypeId, double quantity) {
    FleetCargoEntry entry = new FleetCargoEntry();
    entry.productTypeId = productTypeId;
    entry.quantity = quantity;
    return entry;
  }

  private static double countShips(List<FleetShipGroup> ships) {
    double sum = 0;
    for (FleetShipGroup g : ships) sum += g.quantity;
    return sum;
  }

  private static String fleetName(GameState state, Fleet source, String requested) {
    String name = requested == null ? "" : requested.trim();
    if (!name.isEmpty()) return name;
    long owned = state.fleets.stream().filter(f -> f.ownerId.equals(source.ownerId)).count();
    return "Flottenverband " + (owned + 1);
  }

  private static double round2(double v) {
    return Math.round(v * 100) / 100.0;
  }
}
