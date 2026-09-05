package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.engine.Formulas;
import de.nebula.model.Building;
import de.nebula.model.BuildingCategory;
import de.nebula.model.BuildingType;
import de.nebula.model.ColonyPowerState;

/**
 * 1:1-Portierung von {@code isBlackout}/{@code effectiveHousingCapacity} aus
 * {@code simulated-game-api.service.ts} – von mehreren Systemen gemeinsam
 * genutzt (Produktionstempo, Kernwerte-Neuberechnung, Bevölkerungswachstum).
 */
public final class PowerGrid {
  private PowerGrid() {
  }

  /** Kein Energienetz (Stufe 0) kann nicht "blackouten" – {@code coverageRatio} ist dann per Definition 1. */
  public static boolean isBlackout(GameState state, String colonyId) {
    for (ColonyPowerState p : state.powerStates) {
      if (p.colonyId.equals(colonyId)) return p.coverageRatio < Formulas.BLACKOUT_THRESHOLD;
    }
    return false;
  }

  /** 0..1: wie viel des Elerium-Bedarfs des Energienetzes zuletzt gedeckt war (1 = voll versorgt, kein Energienetz = 1). */
  public static double coverageRatio(GameState state, String colonyId) {
    for (ColonyPowerState p : state.powerStates) {
      if (p.colonyId.equals(colonyId)) return p.coverageRatio;
    }
    return 1;
  }

  /** Aktueller Elerium-Energiezelle-Bedarf des Energienetzes pro Spielstunde (0 ohne Energienetz). */
  public static double powerUpkeepPerHour(GameState state, String colonyId) {
    return GameQueries.getBuildingLevel(state, colonyId, "b_powergrid") * de.nebula.engine.GameConstants.ELERIUM_UPKEEP_PER_POWERGRID_LEVEL;
  }

  /**
   * Summe der Wohnkapazität aus allen Infrastructure-Gebäuden – der Anteil
   * des Energienetzes wird um {@code coverageRatio} gemindert (Blackout bei
   * Elerium-Mangel, siehe {@code EconomyTick.consumePowerUpkeep}). Der
   * Wohnkomplex-Anteil bleibt unabhängig davon voll wirksam.
   */
  public static double effectiveHousingCapacity(GameState state, String colonyId) {
    double coverage = coverageRatio(state, colonyId);
    double sum = 0;
    for (Building b : state.buildings) {
      if (!b.colonyId.equals(colonyId)) continue;
      BuildingType type = BuildingCatalog.find(b.typeId);
      if (type.category != BuildingCategory.Infrastructure) continue;
      double contribution = b.level * (type.populationCapacityPerLevel != null ? type.populationCapacityPerLevel : 0);
      sum += b.typeId.equals("b_powergrid") ? contribution * coverage : contribution;
    }
    return sum;
  }
}
