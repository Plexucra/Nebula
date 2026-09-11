package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Die Zeitkompression des Servers, gelesen aus derselben Datei, die auch
 * Backend ({@code de.nebula.engine.Clock}/{@code SharedConstants}) und
 * Frontend ({@code core/shared-constants.ts}) verwenden:
 * {@code shared/game-constants.json} (Maven-Resource-Mapping in
 * {@code pom.xml}).
 *
 * <p>Ohne das hier hätte der Bot als einziger Beteiligter eigene, in
 * REALZEIT festgeschriebene Fristen – und würde bei erhöhtem Tempo-Regler
 * nicht mitziehen: Der Server spielt viermal so schnell, der Bot entscheidet
 * weiter im alten Takt und wartet weiter dieselbe Realzeit auf seine
 * Aufbauphase. Alle Bot-Fristen sind deshalb in SPIELSTUNDEN formuliert und
 * werden hier umgerechnet.</p>
 */
final class GameSpeed {
  private GameSpeed() {
  }

  private static final JsonNode ROOT = load();

  /** Wirksame Zeitkompression, identisch zu {@code Clock.REAL_MS_PER_GAME_HOUR}. */
  static final double REAL_MS_PER_GAME_HOUR =
      ROOT.path("baseRealMsPerGameHour").asDouble() / ROOT.path("gameSpeedMultiplier").asDouble();

  static long hoursToMs(double gameHours) {
    return (long) (gameHours * REAL_MS_PER_GAME_HOUR);
  }

  /** Lohn je Einwohner und Spielstunde – dieselbe Quelle wie {@code Economy.WAGE_PER_CAPITA_PER_HOUR} im Backend. */
  static final double WAGE_PER_CAPITA_PER_GAME_HOUR = ROOT.path("wagePerCapitaPerGameHour").asDouble();

  private static JsonNode load() {
    String resource = "shared/game-constants.json";
    try (InputStream in = GameSpeed.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Datei fehlt im Klassenpfad: " + resource);
      return new ObjectMapper().readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Datei konnte nicht geladen werden: " + resource, e);
    }
  }
}
