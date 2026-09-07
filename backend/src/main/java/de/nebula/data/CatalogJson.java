package de.nebula.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Lädt die statischen Spieldaten-Kataloge aus den JSON-Dateien unter
 * {@code shared/catalog/} (Umsetzungskonzept/15_...md, Auftrag 3). Diese
 * Dateien sind die EINZIGE Quelle dieser Werte – vorher standen dieselben
 * 214 Produkte, 6 Gebäude, 6 Schiffe, 4 Bodeneinheiten, 17 Rohstoffe und 13
 * Planetentyp-Profile doppelt in Java UND TypeScript und konnten
 * auseinanderlaufen.
 *
 * <p>Die Dateien liegen im Repo unter {@code /shared/catalog}, gehören also
 * bewusst KEINER der beiden Anwendungen; der Maven-Build kopiert sie über
 * eine zusätzliche Resource-Definition (siehe {@code pom.xml}) unter
 * {@code shared/catalog} in den Klassenpfad, von wo sie hier geladen
 * werden. Das Frontend braucht sie überhaupt nicht mehr: es bezieht die
 * Kataloge zur Laufzeit über die WebSocket-Befehle {@code productTypes},
 * {@code buildingTypes}, {@code shipTypes} und {@code groundUnitTypes}.</p>
 */
final class CatalogJson {
  private CatalogJson() {
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static <T> T load(String fileName, TypeReference<T> type) {
    String resource = "shared/catalog/" + fileName;
    try (InputStream in = CatalogJson.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Katalogdatei fehlt im Klassenpfad: " + resource);
      return MAPPER.readValue(in, type);
    } catch (Exception e) {
      throw new IllegalStateException("Katalogdatei konnte nicht geladen werden: " + resource, e);
    }
  }
}
