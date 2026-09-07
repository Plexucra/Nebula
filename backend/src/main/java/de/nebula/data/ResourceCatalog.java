package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.ResourceType;

import java.util.List;

/**
 * Ebene-1-Rohstoffkatalog – die Daten liegen in
 * {@code shared/catalog/resources.json} (siehe {@link CatalogJson}). Inhalt
 * nach Konzeption/Umsetzungskonzept/Nebula_Planetentypen_Rohstoffprofile_
 * Produktionsbaum.md, §4: bewusste wirtschaftliche Sammelgruppen statt
 * einzelner realer Elemente (§4.1, z. B. Aluminium+Magnesium als
 * Leichtmetallerz).
 */
public final class ResourceCatalog {
  private ResourceCatalog() {
  }

  public static final List<ResourceType> CATALOG =
      List.copyOf(CatalogJson.load("resources.json", new TypeReference<List<ResourceType>>() {
      }));
}
