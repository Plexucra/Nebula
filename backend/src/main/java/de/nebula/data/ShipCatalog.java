package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
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

  public static final List<ShipTypeDef> CATALOG =
      List.copyOf(CatalogJson.load("ships.json", new TypeReference<List<ShipTypeDef>>() {
      }));

  public static ShipTypeDef find(String productTypeId) {
    return CATALOG.stream().filter(s -> s.productTypeId.equals(productTypeId)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter ShipType: " + productTypeId));
  }
}
