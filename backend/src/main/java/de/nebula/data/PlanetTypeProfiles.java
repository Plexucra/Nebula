package de.nebula.data;

import de.nebula.model.PlanetType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/planet-type-profiles.ts}.
 * Fördergüte-Bereiche [min, max] (0-100) je Planetentyp und Rohstoff, transkribiert aus
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §6.1-6.3. Reine Wertetabelle –
 * das tatsächliche Ziehen einer konkreten Fördergüte aus diesen Bereichen (Cluster-/Lokal-
 * Mischung) übernimmt {@code WorldSeed.concentrationProfileForType}, analog zur TS-Trennung
 * zwischen {@code rangesForPlanetType} (hier) und {@code concentrationProfileForType} (dort).
 */
public final class PlanetTypeProfiles {
  private PlanetTypeProfiles() {
  }

  /** Reihenfolge der Rohstoffe je Zeile in {@link #RAW_RANGES}, identisch zu {@code ResourceCatalog}. */
  public static final List<String> RESOURCE_ORDER = List.of(
      "res_ferrometall", "res_leichtmetall", "res_refraktaer", "res_leitmetall", "res_edelmetall", "res_seltenerden", "res_technometall", "res_silikat", "res_kohlenstoff", "res_salz", "res_radionuklid", "res_eis", "res_atmosphaere", "res_edelgas", "res_kohlenwasserstoff", "res_isotopentraeger", "res_elerium"
  );

  public record Range(double min, double max) {
  }

  /** Ein Eintrag von {@link #rangesForPlanetType}: Rohstoff-Id + Fördergüte-Bereich. */
  public record ResourceRange(String resourceTypeId, Range range) {
  }

  private static final Map<PlanetType, List<Range>> RAW_RANGES = new LinkedHashMap<>();

  static {
    RAW_RANGES.put(PlanetType.TemperierterBiosphaerenplanet, List.of(new Range(25, 55), new Range(35, 65), new Range(10, 30), new Range(20, 45), new Range(5, 20), new Range(10, 30), new Range(15, 35), new Range(45, 75), new Range(45, 75), new Range(60, 90), new Range(5, 20), new Range(80, 100), new Range(80, 100), new Range(20, 50), new Range(30, 60), new Range(20, 45), new Range(15, 30)));
    RAW_RANGES.put(PlanetType.Silikatplanet, List.of(new Range(35, 70), new Range(30, 65), new Range(20, 50), new Range(20, 50), new Range(5, 25), new Range(10, 40), new Range(15, 45), new Range(70, 100), new Range(15, 40), new Range(20, 50), new Range(10, 35), new Range(5, 35), new Range(10, 40), new Range(10, 35), new Range(5, 25), new Range(10, 35), new Range(5, 25)));
    RAW_RANGES.put(PlanetType.Wuestenplanet, List.of(new Range(25, 60), new Range(40, 75), new Range(15, 45), new Range(15, 40), new Range(5, 20), new Range(15, 45), new Range(10, 35), new Range(65, 95), new Range(20, 50), new Range(55, 90), new Range(10, 30), new Range(5, 30), new Range(20, 55), new Range(10, 40), new Range(15, 50), new Range(10, 35), new Range(5, 20)));
    RAW_RANGES.put(PlanetType.Ozeanplanet, List.of(new Range(10, 35), new Range(15, 40), new Range(5, 20), new Range(10, 30), new Range(5, 20), new Range(5, 20), new Range(10, 30), new Range(20, 50), new Range(35, 65), new Range(75, 100), new Range(5, 20), new Range(90, 100), new Range(70, 95), new Range(20, 50), new Range(25, 55), new Range(30, 60), new Range(8, 25)));
    RAW_RANGES.put(PlanetType.Eisplanet, List.of(new Range(10, 30), new Range(10, 30), new Range(5, 20), new Range(5, 20), new Range(2, 15), new Range(5, 25), new Range(5, 20), new Range(15, 45), new Range(25, 60), new Range(25, 60), new Range(5, 25), new Range(85, 100), new Range(15, 45), new Range(25, 60), new Range(35, 75), new Range(45, 80), new Range(10, 35)));
    RAW_RANGES.put(PlanetType.Vulkanplanet, List.of(new Range(55, 90), new Range(30, 60), new Range(50, 85), new Range(35, 70), new Range(10, 35), new Range(30, 65), new Range(25, 60), new Range(65, 95), new Range(10, 35), new Range(25, 60), new Range(30, 70), new Range(10, 40), new Range(40, 80), new Range(25, 60), new Range(5, 25), new Range(20, 55), new Range(20, 55)));
    RAW_RANGES.put(PlanetType.Metallplanet, List.of(new Range(75, 100), new Range(30, 65), new Range(50, 90), new Range(55, 95), new Range(20, 55), new Range(25, 60), new Range(25, 60), new Range(20, 55), new Range(10, 35), new Range(10, 35), new Range(25, 65), new Range(1, 20), new Range(1, 25), new Range(5, 30), new Range(1, 15), new Range(15, 45), new Range(15, 45)));
    RAW_RANGES.put(PlanetType.Kohlenstoffplanet, List.of(new Range(15, 40), new Range(10, 35), new Range(10, 30), new Range(10, 35), new Range(5, 25), new Range(10, 30), new Range(15, 50), new Range(25, 60), new Range(75, 100), new Range(20, 55), new Range(10, 35), new Range(10, 45), new Range(20, 60), new Range(10, 35), new Range(70, 100), new Range(20, 50), new Range(10, 40)));
    RAW_RANGES.put(PlanetType.Supererde, List.of(new Range(45, 85), new Range(30, 60), new Range(40, 75), new Range(35, 70), new Range(15, 40), new Range(25, 60), new Range(25, 60), new Range(55, 90), new Range(20, 50), new Range(25, 60), new Range(35, 75), new Range(20, 60), new Range(35, 75), new Range(20, 55), new Range(10, 40), new Range(25, 60), new Range(20, 60)));
    RAW_RANGES.put(PlanetType.Planetoid, List.of(new Range(20, 90), new Range(20, 85), new Range(10, 80), new Range(10, 80), new Range(5, 65), new Range(10, 70), new Range(10, 70), new Range(10, 90), new Range(10, 90), new Range(5, 70), new Range(5, 80), new Range(1, 70), new Range(1, 65), new Range(1, 70), new Range(1, 80), new Range(5, 85), new Range(2, 65)));
    RAW_RANGES.put(PlanetType.Gasriese, List.of(new Range(1, 8), new Range(1, 8), new Range(1, 6), new Range(1, 6), new Range(1, 5), new Range(1, 6), new Range(1, 8), new Range(1, 10), new Range(45, 85), new Range(1, 20), new Range(1, 8), new Range(30, 75), new Range(90, 100), new Range(75, 100), new Range(65, 100), new Range(70, 100), new Range(3, 25)));
    RAW_RANGES.put(PlanetType.Eisriese, List.of(new Range(1, 12), new Range(1, 12), new Range(1, 10), new Range(1, 10), new Range(1, 8), new Range(1, 12), new Range(2, 15), new Range(1, 15), new Range(50, 90), new Range(10, 35), new Range(1, 12), new Range(70, 100), new Range(90, 100), new Range(65, 95), new Range(75, 100), new Range(75, 100), new Range(8, 35)));
    RAW_RANGES.put(PlanetType.Schwefelplanet, List.of(new Range(25, 55), new Range(15, 40), new Range(15, 45), new Range(20, 50), new Range(5, 20), new Range(10, 40), new Range(15, 45), new Range(55, 85), new Range(10, 35), new Range(60, 95), new Range(15, 45), new Range(5, 35), new Range(45, 85), new Range(15, 50), new Range(10, 40), new Range(15, 45), new Range(10, 35)));
  }

  private static final Map<PlanetType, String> LABELS = new LinkedHashMap<>();

  static {
    LABELS.put(PlanetType.TemperierterBiosphaerenplanet, "Temperierter Biosphärenplanet");
    LABELS.put(PlanetType.Silikatplanet, "Silikatplanet");
    LABELS.put(PlanetType.Wuestenplanet, "Wüstenplanet");
    LABELS.put(PlanetType.Ozeanplanet, "Ozeanplanet");
    LABELS.put(PlanetType.Eisplanet, "Eisplanet");
    LABELS.put(PlanetType.Vulkanplanet, "Vulkanplanet");
    LABELS.put(PlanetType.Metallplanet, "Metallplanet");
    LABELS.put(PlanetType.Kohlenstoffplanet, "Kohlenstoffplanet");
    LABELS.put(PlanetType.Supererde, "Supererde");
    LABELS.put(PlanetType.Planetoid, "Planetoid");
    LABELS.put(PlanetType.Gasriese, "Gasriese");
    LABELS.put(PlanetType.Eisriese, "Eisriese");
    LABELS.put(PlanetType.Schwefelplanet, "Schwefelplanet");
  }

  public static String label(PlanetType type) {
    return LABELS.get(type);
  }

  /** Alle Fördergüte-Bereiche für einen Planetentyp, siehe TS {@code rangesForPlanetType}. */
  public static List<ResourceRange> rangesForPlanetType(PlanetType type) {
    List<Range> ranges = RAW_RANGES.get(type);
    List<ResourceRange> result = new java.util.ArrayList<>();
    for (int i = 0; i < RESOURCE_ORDER.size(); i++) {
      result.add(new ResourceRange(RESOURCE_ORDER.get(i), ranges.get(i)));
    }
    return result;
  }
}
