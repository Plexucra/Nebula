package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.BuildingType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

  /** Index nach Id – siehe {@code ProductCatalog.BY_ID}: {@link #find} läuft in jedem Tick für jedes Gebäude jeder Kolonie. */
  private static final Map<String, BuildingType> BY_ID = CATALOG.stream()
      .collect(Collectors.toUnmodifiableMap(bt -> bt.id, bt -> bt));

  public static BuildingType find(String id) {
    BuildingType type = BY_ID.get(id);
    if (type == null) throw new IllegalArgumentException("Unbekannter BuildingType: " + id);
    return type;
  }
}
