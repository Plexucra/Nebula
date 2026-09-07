package de.nebula.data;

import de.nebula.engine.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifiziert {@link ResourceClusterField}: die Grundlage der regionalen Rohstoffcluster
 * (Nutzervorgabe "jeder Bereich der Galaxie hat seine eigenen Stärken und Schwächen", siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §7.2). Package-private Klasse,
 * daher Test im selben Package.
 */
class ResourceClusterFieldTest {

  private static final List<String> RESOURCES = PlanetTypeProfiles.RESOURCE_ORDER;

  @Test
  void valuesAreAlwaysWithinZeroToHundred() {
    ResourceClusterField.Field field = ResourceClusterField.generate(RESOURCES, Rng.seeded(1));
    // Rasterprobe über die ganze Kartenfläche, inklusive Rändern und außerhalb [0,1] (Extrapolation).
    double[] coords = {-0.5, 0.0, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0, 1.5};
    for (double x : coords) {
      for (double y : coords) {
        for (String resourceTypeId : RESOURCES) {
          double v = field.valueAt(resourceTypeId, x, y);
          assertTrue(v >= 0 && v <= 100, resourceTypeId + " bei (" + x + "," + y + ") außerhalb [0,100]: " + v);
        }
      }
    }
  }

  @Test
  void valuesAtReturnsEveryRequestedResource() {
    ResourceClusterField.Field field = ResourceClusterField.generate(RESOURCES, Rng.seeded(7));
    Map<String, Double> values = field.valuesAt(0.42, 0.17);
    assertEquals(RESOURCES.size(), values.size());
    for (String resourceTypeId : RESOURCES) {
      assertTrue(values.containsKey(resourceTypeId));
      assertEquals(field.valueAt(resourceTypeId, 0.42, 0.17), values.get(resourceTypeId), 0.0001);
    }
  }

  @Test
  void generateIsDeterministicForTheSameRngSequence() {
    ResourceClusterField.Field a = ResourceClusterField.generate(RESOURCES, Rng.seeded(1337));
    ResourceClusterField.Field b = ResourceClusterField.generate(RESOURCES, Rng.seeded(1337));
    for (String resourceTypeId : RESOURCES) {
      assertEquals(a.valueAt(resourceTypeId, 0.3, 0.6), b.valueAt(resourceTypeId, 0.3, 0.6), 0.0001);
    }
  }

  /**
   * Kern der Nutzervorgabe: räumlich nahe Systeme sollen ähnlichere Rohstoffstärken haben als
   * weit entfernte. Gemittelt über alle 17 Rohstoffe muss die Differenz zwischen zwei NAHEN
   * Punkten deutlich kleiner sein als zwischen zwei WEIT entfernten Punkten.
   */
  @Test
  void nearbyPositionsHaveMoreSimilarClusterValuesThanFarApartOnes() {
    ResourceClusterField.Field field = ResourceClusterField.generate(RESOURCES, Rng.seeded(1337));

    double nearAvgDiff = averageAbsoluteDifference(field, 0.5, 0.5, 0.53, 0.52);
    double farAvgDiff = averageAbsoluteDifference(field, 0.5, 0.5, 0.95, 0.05);

    assertTrue(nearAvgDiff < farAvgDiff,
        "nahe Punkte sollten im Schnitt ähnlichere Clusterwerte haben (nah=" + nearAvgDiff + ", weit=" + farAvgDiff + ")");
  }

  private static double averageAbsoluteDifference(ResourceClusterField.Field field, double x1, double y1, double x2, double y2) {
    double sum = 0;
    for (String resourceTypeId : RESOURCES) {
      sum += Math.abs(field.valueAt(resourceTypeId, x1, y1) - field.valueAt(resourceTypeId, x2, y2));
    }
    return sum / RESOURCES.size();
  }
}
