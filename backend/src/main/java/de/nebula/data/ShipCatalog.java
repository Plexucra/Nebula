package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.engine.GameConstants;
import de.nebula.model.ShipTypeDef;

import java.util.List;

/**
 * Schiffskatalog – die Daten liegen in {@code shared/catalog/ships.json}
 * (siehe {@link CatalogJson}). Kontersystem, siehe Mechanik/03_..., §2
 * (verbindlich): Korvette schlägt Kreuzer, Kreuzer schlägt Zerstörer,
 * Zerstörer schlägt Korvette – abgebildet über das Feld {@code countersClass}
 * in der JSON-Datei. Der Multiplikator (×2 im Vorteil, ×0,5 im Nachteil,
 * sonst ×1) wird erst im Kampfsystem angewendet (Mechanik/04_..., §4).
 * Keine attack/hull-Werte: militärischer Wert = Produktionsaufwand.
 */
public final class ShipCatalog {
  private ShipCatalog() {
  }

  public static final List<ShipTypeDef> CATALOG = List.copyOf(withJumpFuel(
      CatalogJson.load("ships.json", new TypeReference<List<ShipTypeDef>>() {
      })));

  /**
   * Rechnet Sprungverbrauch und Tankgröße JE SCHIFF aus seiner Masse aus
   * (Umsetzungskonzept/34_...md, Entscheidung F7). Bewusst hier beim Laden und
   * nicht als weitere Spalte in {@code ships.json}: die Masse steht im
   * Produktkatalog, und zwei gepflegte Zahlen für dieselbe Aussage laufen
   * auseinander, sobald ein Schiff neu vermessen wird.
   */
  private static List<ShipTypeDef> withJumpFuel(List<ShipTypeDef> defs) {
    double corvetteMass = ProductCatalog.find(GameConstants.CORVETTE_PRODUCT_ID).massKg;
    for (ShipTypeDef def : defs) {
      double massRatio = corvetteMass > 0 ? ProductCatalog.find(def.productTypeId).massKg / corvetteMass : 1;
      def.jumpFuelPerHop = massRatio * GameConstants.JUMP_FUEL_PER_CORVETTE_MASS_PER_HOP;
      def.fuelTankCapacity = def.jumpFuelPerHop * GameConstants.JUMP_FUEL_TANK_RANGE_HOPS;
    }
    return defs;
  }

  public static ShipTypeDef find(String productTypeId) {
    return CATALOG.stream().filter(s -> s.productTypeId.equals(productTypeId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter ShipType: " + productTypeId));
  }
}
