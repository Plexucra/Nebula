package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.GroundUnitTypeDef;

import java.util.List;

/**
 * Bodeneinheiten-Katalog – die Daten liegen in
 * {@code shared/catalog/ground-units.json} (siehe {@link CatalogJson}). Keine
 * attack/defense-Werte: militärischer Wert = Produktionsaufwand.
 *
 * <p>Kein {@code transportSlotUsage} mehr: seit Umsetzungskonzept/28_...md
 * reisen Soldaten über {@code ShipTypeDef.troopCapacity} des
 * Mannschaftstransporters und Drohnen als gewöhnliche Fracht im Frachter
 * (Masse und Volumen des Produkts). Ein gemeinsames Slot-Maß für beide gibt
 * es damit nicht mehr.</p>
 *
 * <p>Konterrichtung (analog zur Schiffs-Kontermatrix): Leichte Drohne schlägt
 * Schwere, Schwere schlägt Mittlere, Mittlere schlägt Leichte – abgebildet
 * über {@code countersClass} in der JSON-Datei.</p>
 */
public final class GroundUnitCatalog {
  private GroundUnitCatalog() {
  }

  public static final List<GroundUnitTypeDef> CATALOG =
      List.copyOf(CatalogJson.load("ground-units.json", new TypeReference<List<GroundUnitTypeDef>>() {
      }));

  public static GroundUnitTypeDef find(String productTypeId) {
    return CATALOG.stream().filter(u -> u.productTypeId.equals(productTypeId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter GroundUnitType: " + productTypeId));
  }
}
