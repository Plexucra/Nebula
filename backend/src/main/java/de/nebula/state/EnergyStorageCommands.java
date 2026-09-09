package de.nebula.state;

import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.EnergyStorage;
import de.nebula.model.EnergyStorageView;

/**
 * Energiespeicher (Umsetzungskonzept/32_...md). Der Befund aus den Testläufen
 * der Bot-Armee (Konzept 31, Befund 9): Produktionsketten decken jeden
 * Zwischenschritt zuerst aus dem Lager – ein Mannschaftstransporter zog 188
 * Stabilisiertes Elerium, ein Zerstörer 455, und die Infrastruktur fiel in den
 * Blackout. Der Speicher trennt Betriebsstoff von Lagerware: was hier liegt,
 * ist für Ketten unsichtbar ({@link Warehouse#qty} liest nur das Lager).
 *
 * <ul>
 *   <li>Zufluss ({@link #intake}): jedes Stabilisierte Elerium, das die Kolonie
 *       erreicht, füllt zuerst den Speicher bis zur Vorhaltemenge.</li>
 *   <li>Abfluss ({@link #drawForUpkeep}): die Infrastruktur zieht zuerst aus dem
 *       Speicher, dann aus dem Lager.</li>
 *   <li>Vorhaltemenge ({@link #setReserve}): konfigurierbar; wird sie gesenkt,
 *       wandert der Überschuss ins Lager. Ohne Konfiguration folgt sie der
 *       Infrastrukturstufe ({@link #defaultTarget}).</li>
 * </ul>
 */
public final class EnergyStorageCommands {
  private EnergyStorageCommands() {
  }

  public static EnergyStorage storageOf(GameState state, String colonyId) {
    for (EnergyStorage s : state.energyStorages) if (s.colonyId.equals(colonyId)) return s;
    EnergyStorage s = new EnergyStorage();
    s.colonyId = colonyId;
    s.stored = 0;
    s.reserveTarget = null;
    state.energyStorages.add(s);
    return s;
  }

  public static double stored(GameState state, String colonyId) {
    for (EnergyStorage s : state.energyStorages) if (s.colonyId.equals(colonyId)) return s.stored;
    return 0;
  }

  /** Automatische Vorhaltemenge: Verbrauch der aktuellen Infrastrukturstufe über die Standardreichweite. */
  public static double defaultTarget(GameState state, String colonyId) {
    int level = GameQueries.getBuildingLevel(state, colonyId, GameConstants.INFRASTRUCTURE_BUILDING_ID);
    return Math.ceil(Formulas.infrastructureEleriumPerHour(level) * GameConstants.ENERGY_RESERVE_DEFAULT_GAME_HOURS);
  }

  public static double effectiveTarget(GameState state, String colonyId) {
    EnergyStorage s = storageOf(state, colonyId);
    return s.reserveTarget != null ? s.reserveTarget : defaultTarget(state, colonyId);
  }

  /** Nimmt bis zur Vorhaltemenge auf und liefert, was NICHT aufgenommen wurde (gehört ins Lager). */
  static double intake(GameState state, String colonyId, double quantity) {
    if (quantity <= 0) return quantity;
    EnergyStorage s = storageOf(state, colonyId);
    double room = Math.max(0, effectiveTarget(state, colonyId) - s.stored);
    double taken = Math.min(room, quantity);
    s.stored += taken;
    return quantity - taken;
  }

  /** Deckt den fälligen Infrastrukturverbrauch – zuerst aus dem Speicher, dann aus dem Lager – und liefert die gedeckte Menge. */
  static double drawForUpkeep(GameState state, String colonyId, double due) {
    if (due <= 0) return 0;
    EnergyStorage s = storageOf(state, colonyId);
    double fromStorage = Math.min(s.stored, due);
    s.stored -= fromStorage;
    double rest = due - fromStorage;
    double fromWarehouse = Math.min(rest, Math.floor(Warehouse.qty(state, colonyId, GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID)));
    if (fromWarehouse > 0) Warehouse.addRaw(state, colonyId, GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, -fromWarehouse);
    return fromStorage + fromWarehouse;
  }

  /** Speicher plus Lager – die Gesamtreichweite, mit der die Energiedeckung gemessen wird. */
  static double totalFuel(GameState state, String colonyId) {
    return stored(state, colonyId) + Warehouse.qty(state, colonyId, GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID);
  }

  /**
   * Vorhaltemenge setzen ({@code null} = automatisch). Liegt danach mehr im
   * Speicher als vorgehalten werden soll, geht der Überschuss ins Lager.
   */
  public static void setReserve(GameState state, String playerId, String colonyId, Double reserveTarget) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    if (reserveTarget != null && (reserveTarget < 0 || Double.isNaN(reserveTarget) || Double.isInfinite(reserveTarget))) {
      throw new CommandException("Die Vorhaltemenge muss 0 oder größer sein.");
    }
    EnergyStorage s = storageOf(state, colonyId);
    s.reserveTarget = reserveTarget == null ? null : Math.floor(reserveTarget);
    double target = effectiveTarget(state, colonyId);
    if (s.stored > target) {
      double surplus = s.stored - target;
      s.stored = target;
      Warehouse.addRaw(state, colonyId, GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, surplus);
    }
  }

  public static EnergyStorageView view(GameState state, String colonyId) {
    EnergyStorage s = storageOf(state, colonyId);
    EnergyStorageView v = new EnergyStorageView();
    v.colonyId = colonyId;
    v.stored = s.stored;
    v.automatic = s.reserveTarget == null;
    v.defaultTarget = defaultTarget(state, colonyId);
    v.reserveTarget = effectiveTarget(state, colonyId);
    int level = GameQueries.getBuildingLevel(state, colonyId, GameConstants.INFRASTRUCTURE_BUILDING_ID);
    v.upkeepPerHour = Formulas.infrastructureEleriumPerHour(level);
    v.storedCoverageGameHours = v.upkeepPerHour > 0 ? s.stored / v.upkeepPerHour : null;
    v.warehouseStock = Warehouse.qty(state, colonyId, GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID);
    return v;
  }

  /** Beim Verschwinden einer Kolonie (Eingliederung nach Eroberung). */
  static void remove(GameState state, String colonyId) {
    state.energyStorages.removeIf(s -> s.colonyId.equals(colonyId));
  }
}
