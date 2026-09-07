package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.BuildingType;

import java.util.List;

/**
 * Gebäudekatalog – die Daten liegen in {@code shared/catalog/buildings.json}
 * (siehe {@link CatalogJson} und Umsetzungskonzept/15_...md, Auftrag 3:
 * EINE Quelle statt je einer Kopie in Java und TypeScript).
 */
public final class BuildingCatalog {
  private BuildingCatalog() {
  }

  public static final List<BuildingType> CATALOG =
      List.copyOf(CatalogJson.load("buildings.json", new TypeReference<List<BuildingType>>() {
      }));

  public static BuildingType find(String id) {
    return CATALOG.stream().filter(bt -> bt.id.equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannter BuildingType: " + id));
  }
}
