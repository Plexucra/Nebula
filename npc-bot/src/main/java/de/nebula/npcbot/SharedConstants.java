package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zugang des Bots zu {@code shared/game-constants.json} – derselben Datei, die
 * Backend ({@code de.nebula.engine.SharedConstants}), Frontend
 * ({@code core/shared-constants.ts}) und e2e-Test lesen. Maven kopiert sie
 * über ein Resource-Mapping in den Klassenpfad (siehe {@code pom.xml}); das
 * ist eine Datei-, keine Modulabhängigkeit.
 *
 * <p>Regelzahlen, die der Bot zum Planen braucht (Kampftakt, Loyalitäts-
 * schwellen, Pro-Kopf-Bedarfe, Startbevölkerung …), kommen von hier statt als
 * Kopie in {@link Catalog}. Werte, die aus den Katalogen folgen (Kampfwert je
 * Schiff und Drohne, Tankgröße), fragt {@link World} beim Server ab.</p>
 */
final class SharedConstants {
  private SharedConstants() {
  }

  private static final JsonNode ROOT = load();

  /** Eine Zahl aus der Datei; ein fehlender Schlüssel ist ein Fehler, keine stille 0. */
  static double number(String key) {
    JsonNode node = ROOT.path(key);
    if (!node.isNumber()) throw new IllegalStateException("shared/game-constants.json: Zahl '" + key + "' fehlt");
    return node.asDouble();
  }

  /** Eine Tabelle Id → Zahl, in der Reihenfolge der Datei. */
  static Map<String, Double> numberMap(String key) {
    JsonNode node = ROOT.path(key);
    if (!node.isObject() || node.isEmpty()) throw new IllegalStateException("shared/game-constants.json: Tabelle '" + key + "' fehlt");
    Map<String, Double> out = new LinkedHashMap<>();
    node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asDouble()));
    return Collections.unmodifiableMap(out);
  }

  private static JsonNode load() {
    String resource = "shared/game-constants.json";
    try (InputStream in = SharedConstants.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Datei fehlt im Klassenpfad: " + resource);
      return new ObjectMapper().readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Datei konnte nicht geladen werden: " + resource, e);
    }
  }
}
