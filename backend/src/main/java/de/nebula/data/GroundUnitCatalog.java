package de.nebula.data;

import de.nebula.model.GroundUnitClass;
import de.nebula.model.GroundUnitTypeDef;

import java.util.List;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/ground-unit-catalog.ts}.
 * Transport-Slot-Verbrauch nach Mechanik/05_..., §6 (vereinfacht). Keine attack/defense-Werte:
 * militärischer Wert = Produktionsaufwand, siehe {@code GroundUnitTypeDef}.
 *
 * <p>Konterrichtung (analog zur Schiffs-Kontermatrix): Leichte Drohne schlägt Schwere,
 * Schwere schlägt Mittlere, Mittlere schlägt Leichte.</p>
 */
public final class GroundUnitCatalog {
  private GroundUnitCatalog() {
  }

  private static GroundUnitTypeDef u(String productTypeId, GroundUnitClass unitClass,
                                     double transportSlotUsage, GroundUnitClass countersClass) {
    GroundUnitTypeDef t = new GroundUnitTypeDef();
    t.productTypeId = productTypeId;
    t.unitClass = unitClass;
    t.transportSlotUsage = transportSlotUsage;
    t.countersClass = countersClass;
    return t;
  }

  public static final List<GroundUnitTypeDef> CATALOG = List.of(
      u("p_soldier", GroundUnitClass.Soldier, 0.05, null),
      u("p_drone_light", GroundUnitClass.LightDrone, 1, GroundUnitClass.HeavyDrone),
      u("p_drone_medium", GroundUnitClass.MediumDrone, 1, GroundUnitClass.LightDrone),
      u("p_drone_heavy", GroundUnitClass.HeavyDrone, 20, GroundUnitClass.MediumDrone)
  );

  public static GroundUnitTypeDef find(String productTypeId) {
    return CATALOG.stream().filter(u -> u.productTypeId.equals(productTypeId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter GroundUnitType: " + productTypeId));
  }
}
