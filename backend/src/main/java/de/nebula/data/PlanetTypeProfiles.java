package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import de.nebula.model.PlanetType;

import java.util.List;
import java.util.Map;

/**
 * Fördergüte-Bereiche [min, max] (0-100) je Planetentyp und Rohstoff,
 * transkribiert aus Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md,
 * §6.1-6.3. Die Wertetabelle selbst liegt in
 * {@code shared/catalog/planet-type-profiles.json} (siehe {@link CatalogJson}
 * und Umsetzungskonzept/15_...md, Auftrag 3) – vorher stand sie doppelt in
 * Java und TypeScript.
 *
 * <p>Reine Wertetabelle: das tatsächliche Ziehen einer konkreten Fördergüte
 * aus diesen Bereichen (Cluster-/Lokal-Mischung) übernimmt
 * {@code WorldSeed.concentrationProfileForType}.</p>
 */
public final class PlanetTypeProfiles {
  private PlanetTypeProfiles() {
  }

  public record Range(double min, double max) {
  }

  /** Ein Eintrag von {@link #rangesForPlanetType}: Rohstoff-Id + Fördergüte-Bereich. */
  public record ResourceRange(String resourceTypeId, Range range) {
  }

  /** Aufbau der JSON-Datei: Rohstoff-Reihenfolge plus die Bereiche je Planetentyp. */
  private record ProfileFile(List<String> resourceOrder, Map<String, List<ResourceRange>> rangesByPlanetType) {
  }

  private static final ProfileFile FILE =
      CatalogJson.load("planet-type-profiles.json", new TypeReference<ProfileFile>() {
      });

  /** Reihenfolge der Rohstoffe je Zeile, identisch zu {@code ResourceCatalog}. */
  public static final List<String> RESOURCE_ORDER = List.copyOf(FILE.resourceOrder());

  /** Alle Fördergüte-Bereiche für einen Planetentyp. */
  public static List<ResourceRange> rangesForPlanetType(PlanetType type) {
    List<ResourceRange> ranges = FILE.rangesByPlanetType().get(type.name());
    if (ranges == null) throw new IllegalArgumentException("Kein Fördergüte-Profil für Planetentyp: " + type);
    return ranges;
  }
}
