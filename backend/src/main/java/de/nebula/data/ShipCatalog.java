package de.nebula.data;

import de.nebula.model.ShipClass;
import de.nebula.model.ShipTypeDef;

import java.util.List;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/ship-catalog.ts}.
 * Kontersystem, siehe Mechanik/03_..., §2 (verbindlich): Korvette schlägt Kreuzer,
 * Kreuzer schlägt Zerstörer, Zerstörer schlägt Korvette. Der Multiplikator (×2 im Vorteil,
 * ×0,5 im Nachteil, sonst ×1) wird erst im Kampfsystem angewendet (Mechanik/04_..., §4) –
 * hier steht nur, wer wen kontert. Keine attack/hull-Werte: militärischer Wert =
 * Produktionsaufwand, siehe {@code ShipTypeDef}.
 */
public final class ShipCatalog {
  private ShipCatalog() {
  }

  private static ShipTypeDef s(String productTypeId, ShipClass shipClass, double cargoMassKg,
                               double cargoVolumeM3, double carrierSlotUsage, ShipClass countersClass) {
    ShipTypeDef t = new ShipTypeDef();
    t.productTypeId = productTypeId;
    t.shipClass = shipClass;
    t.cargoMassKg = cargoMassKg;
    t.cargoVolumeM3 = cargoVolumeM3;
    t.carrierSlotUsage = carrierSlotUsage;
    t.countersClass = countersClass;
    return t;
  }

  public static final List<ShipTypeDef> CATALOG = List.of(
      s("p_corvette", ShipClass.Corvette, 0, 0, 1, ShipClass.Cruiser),
      s("p_destroyer", ShipClass.Destroyer, 0, 0, 2, ShipClass.Corvette),
      s("p_cruiser", ShipClass.Cruiser, 0, 0, 4, ShipClass.Destroyer),
      s("p_freighter", ShipClass.Freighter, 2000000, 3000, 3, null),
      s("p_carrier", ShipClass.Carrier, 0, 0, 0, null),
      s("p_trooptransport", ShipClass.TroopTransport, 0, 0, 2, null)
  );

  public static ShipTypeDef find(String productTypeId) {
    return CATALOG.stream().filter(s -> s.productTypeId.equals(productTypeId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter ShipType: " + productTypeId));
  }
}
