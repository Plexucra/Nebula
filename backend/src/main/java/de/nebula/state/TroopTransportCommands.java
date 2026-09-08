package de.nebula.state;

import de.nebula.data.GroundUnitCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.data.ShipCatalog;
import de.nebula.engine.GameConstants;
import de.nebula.model.Fleet;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.FleetTroopCapacity;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.GroundUnitClass;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;

import java.util.ArrayList;

/**
 * Verladung von Bodentruppen (Umsetzungskonzept/28_...md). Die Aufteilung ist
 * bewusst hart:
 *
 * <ul>
 *   <li><b>Soldaten</b> reisen im Mannschaftstransporter, begrenzt durch
 *       {@code ShipTypeDef.troopCapacity}. Sie kommen NIE ins Warenlager und
 *       können deshalb auch nicht als Fracht an dieser Grenze vorbei
 *       verschifft werden (siehe {@link #requireNotASoldier}).</li>
 *   <li><b>Drohnen</b> sind Maschinen. Sie wandern zwischen Garnison und
 *       Warenlager ({@link #storeDrones}/{@link #deployDrones}) und reisen von
 *       dort als gewöhnliche Fracht im Frachter über
 *       {@link FleetCommands#loadCargo}. Mechanik/05_..., §6 behandelt sie
 *       schon beim Auflösen eines Verbands als Lagerbestand – das ist hier nur
 *       konsequent zu Ende gedacht.</li>
 * </ul>
 *
 * <p>Eine Landung braucht damit beide Schiffstypen: Transporter für die
 * Soldaten, Frachter für ihre Drohnen.</p>
 */
public final class TroopTransportCommands {
  private TroopTransportCommands() {
  }

  /** Plätze für Soldaten in dieser Flotte: Summe über alle Schiffe (nur der Mannschaftstransporter hat {@code troopCapacity > 0}). */
  public static double troopCapacity(Fleet fleet) {
    double capacity = 0;
    for (FleetShipGroup g : fleet.ships) {
      if (g.quantity <= 0) continue;
      capacity += ShipCatalog.find(g.shipProductTypeId).troopCapacity * g.quantity;
    }
    return capacity;
  }

  /** Der Verband AN BORD dieser Flotte, oder {@code null}, wenn keine Truppen eingeschifft sind. */
  public static GroundForceGroup embarkedForces(GameState state, String fleetId) {
    return state.groundForceGroups.stream()
        .filter(g -> fleetId.equals(g.fleetId)).findFirst().orElse(null);
  }

  public static double soldiersAboard(GameState state, String fleetId) {
    GroundForceGroup group = embarkedForces(state, fleetId);
    return group == null ? 0 : count(group, GameConstants.SOLDIER_PRODUCT_ID);
  }

  /**
   * Plätze, Belegung und die tatsächlich einschiffbare Menge in EINEM Aufruf –
   * damit das Frontend die Regel nicht nachbaut (Umsetzungskonzept/15_...md,
   * Auftrag 3). Liefert bei unbekannter Flotte ein leeres Ergebnis statt eines
   * Fehlers, weil es eine reine Anzeigeabfrage ist.
   */
  public static FleetTroopCapacity fleetTroopCapacity(GameState state, String fleetId) {
    FleetTroopCapacity result = new FleetTroopCapacity();
    Fleet fleet = state.fleets.stream().filter(f -> f.id.equals(fleetId)).findFirst().orElse(null);
    if (fleet == null) return result;
    result.capacitySoldiers = troopCapacity(fleet);
    result.soldiersAboard = soldiersAboard(state, fleetId);
    double free = result.capacitySoldiers - result.soldiersAboard;
    double inGarrison = 0;
    if (fleet.status == FleetStatus.Stationed && fleet.locationColonyId != null) {
      GroundForceGroup garrison = RecruitmentCommands.groundForces(state, fleet.locationColonyId);
      if (garrison != null) inGarrison = count(garrison, GameConstants.SOLDIER_PRODUCT_ID);
    }
    result.maxEmbarkableQuantity = Math.max(0, Math.floor(Math.min(free, inGarrison)));
    return result;
  }

  /**
   * Verlädt {@code quantity} Soldaten aus der Garnison der Kolonie, bei der die
   * Flotte liegt, an Bord. Soldaten an Bord zählen nicht mehr zur Sicherheit
   * der Kolonie ({@code EconomyTick.recalcCoreStats}) – das Ausschiffen ist
   * über {@link #disembarkSoldiers} jederzeit möglich.
   */
  public static void embarkSoldiers(GameState state, IdGenerator ids, String playerId, String fleetId, double quantity) {
    quantity = Math.floor(quantity); // nur ganze Soldaten (Umsetzungskonzept/25_...md)
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    Fleet fleet = requireFleetAtOwnColony(state, playerId, fleetId);

    double capacity = troopCapacity(fleet);
    if (capacity <= 0) throw new CommandException("Diese Flotte hat keinen Mannschaftstransporter – nur er nimmt Soldaten auf.");
    double free = capacity - soldiersAboard(state, fleetId);
    if (quantity > free + 1e-6) {
      throw new CommandException("Nicht genug Platz: " + (long) free + " von " + (long) capacity + " Plätzen frei.");
    }

    GroundForceGroup garrison = RecruitmentCommands.groundForces(state, fleet.locationColonyId);
    double available = garrison == null ? 0 : count(garrison, GameConstants.SOLDIER_PRODUCT_ID);
    if (available < quantity) throw new CommandException("Die Kolonie hat nur " + (long) available + " Soldaten.");

    remove(garrison, GameConstants.SOLDIER_PRODUCT_ID, quantity);
    RecruitmentCommands.recalcCrewing(state, fleet.locationColonyId);

    GroundForceGroup aboard = embarkedForces(state, fleetId);
    if (aboard == null) {
      aboard = new GroundForceGroup();
      aboard.id = ids.next("gfg");
      aboard.ownerId = fleet.ownerId;
      aboard.colonyId = null;
      aboard.fleetId = fleetId;
      aboard.units = new ArrayList<>();
      state.groundForceGroups.add(aboard);
    }
    // An Bord gibt es keine Drohnen zu kommandieren, deshalb stehen Soldaten
    // hier vollständig in reserveCount – aktiv werden sie erst wieder in einer
    // Garnison (recalcCrewing) bzw. nach der Landung.
    add(aboard, GameConstants.SOLDIER_PRODUCT_ID, quantity);
  }

  /** Schifft {@code quantity} Soldaten in die Garnison der Kolonie aus, bei der die Flotte liegt. */
  public static void disembarkSoldiers(GameState state, IdGenerator ids, String playerId, String fleetId, double quantity) {
    quantity = Math.floor(quantity);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    Fleet fleet = requireFleetAtOwnColony(state, playerId, fleetId);

    GroundForceGroup aboard = embarkedForces(state, fleetId);
    double available = aboard == null ? 0 : count(aboard, GameConstants.SOLDIER_PRODUCT_ID);
    if (available < quantity) throw new CommandException("Es sind nur " + (long) available + " Soldaten an Bord.");

    remove(aboard, GameConstants.SOLDIER_PRODUCT_ID, quantity);
    if (isEmpty(aboard)) state.groundForceGroups.remove(aboard);

    RecruitmentCommands.addSoldiersToGarrison(state, ids, fleet.locationColonyId, (int) quantity);
  }

  /**
   * Verlegt Drohnen aus der Garnison ins Warenlager der Kolonie – erst von dort
   * lassen sie sich als Fracht verladen. Gegenstück: {@link #deployDrones}.
   */
  public static void storeDrones(GameState state, String playerId, String colonyId, String droneProductTypeId, double quantity) {
    quantity = Math.floor(quantity);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    GameQueries.requireOwnColony(state, playerId, colonyId);
    requireDrone(droneProductTypeId);

    GroundForceGroup garrison = RecruitmentCommands.groundForces(state, colonyId);
    double available = garrison == null ? 0 : count(garrison, droneProductTypeId);
    if (available < quantity) throw new CommandException("Die Garnison hat nur " + (long) available + " davon.");

    remove(garrison, droneProductTypeId, quantity);
    Warehouse.add(state, colonyId, droneProductTypeId, quantity);
    RecruitmentCommands.recalcCrewing(state, colonyId);
  }

  /** Stellt eingelagerte Drohnen wieder in die Garnison – Gegenstück zu {@link #storeDrones}. */
  public static void deployDrones(GameState state, IdGenerator ids, String playerId, String colonyId, String droneProductTypeId, double quantity) {
    quantity = Math.floor(quantity);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    GameQueries.requireOwnColony(state, playerId, colonyId);
    requireDrone(droneProductTypeId);

    double stock = Warehouse.qty(state, colonyId, droneProductTypeId);
    if (stock < quantity) throw new CommandException("Nicht genug Lagerbestand.");
    Warehouse.add(state, colonyId, droneProductTypeId, -quantity);
    RecruitmentCommands.addDronesToGarrison(state, ids, colonyId, droneProductTypeId, (int) quantity);
  }

  /**
   * Riegelt den Frachtweg für Soldaten ab. Ohne diese Prüfung ließen sich
   * Soldaten – sobald sie auf irgendeinem Weg ins Lager gelangen – als
   * gewöhnliche Ware an {@code troopCapacity} vorbei verschiffen, und die
   * ganze Aufteilung aus Umsetzungskonzept/28_...md wäre umgangen.
   */
  public static void requireNotASoldier(String productTypeId) {
    if (GameConstants.SOLDIER_PRODUCT_ID.equals(productTypeId)) {
      throw new CommandException("Soldaten reisen nicht als Fracht, sondern im Mannschaftstransporter.");
    }
  }

  private static Fleet requireFleetAtOwnColony(GameState state, String playerId, String fleetId) {
    Fleet fleet = FleetCommands.requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed || fleet.locationColonyId == null) {
      throw new CommandException("Die Flotte muss bei einer Kolonie gelandet sein.");
    }
    GameQueries.requireOwnColony(state, playerId, fleet.locationColonyId);
    return fleet;
  }

  private static void requireDrone(String productTypeId) {
    ProductType product = ProductCatalog.find(productTypeId);
    if (product.category != ProductCategory.GroundUnit
        || GroundUnitCatalog.find(productTypeId).unitClass == GroundUnitClass.Soldier) {
      throw new CommandException("Nur Drohnen lassen sich einlagern.");
    }
  }

  /** Package-private statt private: {@code LandingCommands} teilt sich diese Bestands-Buchhaltung. */
  static double count(GroundForceGroup group, String unitProductTypeId) {
    double total = 0;
    for (GroundForceUnitStack u : group.units) {
      if (u.unitProductTypeId.equals(unitProductTypeId)) total += u.activeCount + u.reserveCount;
    }
    return total;
  }

  /** Nimmt zuerst aus der Reserve, erst danach aus den aktiven Einheiten – aktive Verteidigung bleibt so lange wie möglich stehen. */
  static void remove(GroundForceGroup group, String unitProductTypeId, double quantity) {
    double rest = quantity;
    for (GroundForceUnitStack u : group.units) {
      if (!u.unitProductTypeId.equals(unitProductTypeId)) continue;
      int fromReserve = (int) Math.min(u.reserveCount, rest);
      u.reserveCount -= fromReserve;
      rest -= fromReserve;
      int fromActive = (int) Math.min(u.activeCount, rest);
      u.activeCount -= fromActive;
      rest -= fromActive;
    }
    group.units.removeIf(u -> u.activeCount <= 0 && u.reserveCount <= 0);
  }

  static void add(GroundForceGroup group, String unitProductTypeId, double quantity) {
    for (GroundForceUnitStack u : group.units) {
      if (u.unitProductTypeId.equals(unitProductTypeId)) {
        u.reserveCount += (int) quantity;
        return;
      }
    }
    GroundForceUnitStack stack = new GroundForceUnitStack();
    stack.unitProductTypeId = unitProductTypeId;
    stack.activeCount = 0;
    stack.reserveCount = (int) quantity;
    group.units.add(stack);
  }

  static boolean isEmpty(GroundForceGroup group) {
    return group.units.stream().allMatch(u -> u.activeCount <= 0 && u.reserveCount <= 0);
  }
}
