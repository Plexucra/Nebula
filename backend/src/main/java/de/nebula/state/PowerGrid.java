package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.BuildingCategory;
import de.nebula.model.BuildingType;
import de.nebula.model.ColonyPowerState;

/**
 * Energie-/Versorgungszustand des Gebäudes "Infrastruktur" (Umsetzungskonzept/17_...md,
 * Teil A): es verbraucht laufend Stabilisiertes Elerium, überlinear zur Stufe.
 * Reicht der Bestand nicht, sinkt {@code coverageRatio}; unter
 * {@code Formulas.BLACKOUT_THRESHOLD} gilt die Kolonie als "im Blackout"
 * (Produktion 10 %, Kernwerte halbiert – siehe {@code ChainPlanner} und
 * {@code Economy.recalcCoreStats}). Die Wohnkapazität hängt NICHT mehr
 * daran, sie kommt allein aus dem Wohnkomplex ({@link #effectiveHousingCapacity}).
 */
public final class PowerGrid {
  private PowerGrid() {
  }

  /** Betriebszustand der Kolonie, bei Bedarf neu angelegt (voll versorgt). */
  static ColonyPowerState stateOf(GameState state, String colonyId) {
    for (ColonyPowerState p : state.powerStates) if (p.colonyId.equals(colonyId)) return p;
    ColonyPowerState ps = new ColonyPowerState();
    ps.colonyId = colonyId;
    ps.coverageRatio = 1;
    state.powerStates.add(ps);
    return ps;
  }

  /**
   * Elerium ist angekommen (Produktion, Kauf, Entladung): holt die am
   * Kolonietag ungedeckt gebliebene Menge sofort nach, damit der Blackout mit
   * dem Nachschub endet und nicht erst am nächsten Tag. Reaktion statt Takt,
   * aufgerufen aus {@link Warehouse#add}.
   */
  static void settleShortfall(GameState state, String colonyId) {
    ColonyPowerState ps = null;
    for (ColonyPowerState p : state.powerStates) if (p.colonyId.equals(colonyId)) ps = p;
    if (ps == null || ps.shortfall <= 0) return;
    double stock = Math.floor(EnergyStorageCommands.totalFuel(state, colonyId));
    double covered = EnergyStorageCommands.drawForUpkeep(state, colonyId, Math.min(ps.shortfall, stock));
    ps.shortfall -= covered;
    if (ps.shortfall <= 1e-9) {
      ps.shortfall = 0;
      ps.coverageRatio = 1;
    } else if (ps.dueToday > 0) {
      ps.coverageRatio = (ps.dueToday - ps.shortfall) / ps.dueToday;
    }
  }

  /** Ohne Infrastruktur (Stufe 0) gibt es nichts zu versorgen – {@code coverageRatio} ist dann per Definition 1. */
  public static boolean isBlackout(GameState state, String colonyId) {
    for (ColonyPowerState p : state.powerStates) {
      if (p.colonyId.equals(colonyId)) return p.coverageRatio < Formulas.BLACKOUT_THRESHOLD;
    }
    return false;
  }

  /** 0..1: wie viel des Elerium-Bedarfs der Infrastruktur zuletzt gedeckt war (1 = voll versorgt). */
  public static double coverageRatio(GameState state, String colonyId) {
    for (ColonyPowerState p : state.powerStates) {
      if (p.colonyId.equals(colonyId)) return p.coverageRatio;
    }
    return 1;
  }

  /** Aktueller Elerium-Bedarf der Infrastruktur pro Spielstunde, siehe {@code Formulas.infrastructureEleriumPerHour}. */
  public static double powerUpkeepPerHour(GameState state, String colonyId) {
    return Formulas.infrastructureEleriumPerHour(GameQueries.getBuildingLevel(state, colonyId, GameConstants.INFRASTRUCTURE_BUILDING_ID));
  }

  /** Wohnkapazität = Summe über alle {@code Housing}-Gebäude (Wohnkomplex) – unabhängig von der Energieversorgung. */
  public static double effectiveHousingCapacity(GameState state, String colonyId) {
    double sum = 0;
    for (Building b : state.buildings) {
      if (!b.colonyId.equals(colonyId)) continue;
      BuildingType type = BuildingCatalog.find(b.typeId);
      if (type.category != BuildingCategory.Housing || type.housingCapacityPerLevel == null) continue;
      sum += Formulas.housingCapacity(type.housingCapacityPerLevel, b.level);
    }
    return sum;
  }
}
