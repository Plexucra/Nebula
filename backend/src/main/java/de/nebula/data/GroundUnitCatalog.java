package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.GroundUnitTypeDef;

import java.util.List;

/**
 * Bodeneinheiten-Katalog – die Daten liegen in
 * {@code shared/catalog/ground-units.json} (siehe {@link CatalogJson}).
 * Transport-Slot-Verbrauch nach Mechanik/05_..., §6 (vereinfacht). Keine
 * attack/defense-Werte: militärischer Wert = Produktionsaufwand.
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
