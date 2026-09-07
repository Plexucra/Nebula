package de.nebula.data;

import de.nebula.engine.Rng;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regionale Rohstoffstärke über die gesamte Galaxiekarte, siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §7.1/§7.2 ("für
 * jedes Sternsystem und jeden Rohstoff ein regionaler Clusterwert").
 *
 * <p>Statt den Clusterwert je System unabhängig zu würfeln (dann gäbe es
 * keinerlei räumliche Kohärenz) oder eine Tabelle zu erzeugen und
 * nachträglich zu sortieren, spannt diese Klasse je Rohstoff ein kleines Feld
 * aus zufällig platzierten "Hotspots" mit zufälliger Stärke über die
 * Einheitsfläche der Galaxiekarte auf. Der Clusterwert eines Systems an
 * Position (x, y) ist der distanzgewichtete Mittelwert der Hotspot-Stärken
 * (Gauß-Kern). Ergebnis: benachbarte Systeme haben ähnliche Werte (glatter
 * Feldverlauf), während weit entfernte Regionen unabhängig streuen –
 * genau die gewünschte "Systeme mit ähnlichen Stärken liegen eher
 * nebeneinander"-Eigenschaft, ohne eine explizite Nachbarschafts-
 * Validierungsphase (§7.2) zu benötigen.</p>
 */
final class ResourceClusterField {
  private ResourceClusterField() {
  }

  private static final int HOTSPOTS_PER_RESOURCE = 5;
  /** Ausdehnung eines Hotspots relativ zur Einheitsfläche [0,1]x[0,1] – groß genug für Regionen von mehreren Dutzend Systemen. */
  private static final double SIGMA = 0.28;

  private record Hotspot(double x, double y, double strength) {
  }

  static final class Field {
    private final Map<String, List<Hotspot>> hotspotsByResource;

    private Field(Map<String, List<Hotspot>> hotspotsByResource) {
      this.hotspotsByResource = hotspotsByResource;
    }

    /** Clusterwert (0-100) eines Rohstoffs an der gegebenen Kartenposition. */
    double valueAt(String resourceTypeId, double x, double y) {
      List<Hotspot> spots = hotspotsByResource.get(resourceTypeId);
      if (spots == null || spots.isEmpty()) return 50.0;
      double weightSum = 0;
      double valueSum = 0;
      for (Hotspot h : spots) {
        double dx = x - h.x();
        double dy = y - h.y();
        double dist2 = dx * dx + dy * dy;
        double w = Math.exp(-dist2 / (2 * SIGMA * SIGMA));
        weightSum += w;
        valueSum += w * h.strength();
      }
      return weightSum > 0 ? valueSum / weightSum : 50.0;
    }

    /** Clusterwerte ALLER bekannten Rohstoffe an der gegebenen Kartenposition, siehe {@link #valueAt}. */
    Map<String, Double> valuesAt(double x, double y) {
      Map<String, Double> result = new LinkedHashMap<>();
      for (String resourceTypeId : hotspotsByResource.keySet()) {
        result.put(resourceTypeId, valueAt(resourceTypeId, x, y));
      }
      return result;
    }
  }

  static Field generate(List<String> resourceTypeIds, Rng rnd) {
    Map<String, List<Hotspot>> map = new LinkedHashMap<>();
    for (String resourceTypeId : resourceTypeIds) {
      List<Hotspot> spots = new ArrayList<>();
      for (int i = 0; i < HOTSPOTS_PER_RESOURCE; i++) {
        spots.add(new Hotspot(rnd.next(), rnd.next(), rnd.next() * 100));
      }
      map.put(resourceTypeId, spots);
    }
    return new Field(map);
  }
}
