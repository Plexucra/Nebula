package de.nebula.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Tempo-Regler ({@code shared/game-constants.json}, {@code gameSpeedMultiplier})
 * ist die EINE Stelle, an der das Spieltempo eingestellt wird. Dieser Test
 * hält die Eigenschaften fest, die dafür gelten müssen – und zwar so, dass sie
 * bei JEDEM eingestellten Multiplikator gelten, nicht nur beim gerade
 * konfigurierten. Wer künftig eine Größe wieder in Realzeit festschreibt (ein
 * "alle 10 Sekunden", ein "so viel je Tick"), bricht hier.
 */
class GameSpeedTest {

  @Test
  void wirksameZeitkompressionIstAusgangswertGeteiltDurchRegler() {
    assertEquals(SharedConstants.baseRealMsPerGameHour() / SharedConstants.gameSpeedMultiplier(),
        Clock.REAL_MS_PER_GAME_HOUR, 1e-9,
        "REAL_MS_PER_GAME_HOUR muss ausschließlich aus baseRealMsPerGameHour und gameSpeedMultiplier folgen");
    assertTrue(Clock.GAME_SPEED_MULTIPLIER > 0, "Ein Multiplikator ≤ 0 würde die Uhr anhalten oder rückwärts laufen lassen");
  }

  @Test
  void einTickBleibtEineRealsekundeUndDecktEntsprechendMehrSpielzeitAb() {
    // Der Realzeit-Takt ist bewusst NICHT am Regler: schneller heißt mehr
    // Spielstunden je Tick, nicht mehr Ticks je Sekunde (siehe GameTick).
    assertEquals(GameConstants.TICK_MS, GameConstants.TICK_GAME_HOURS * Clock.REAL_MS_PER_GAME_HOUR, 1e-9);
    assertEquals(1000.0, GameConstants.TICK_MS, 1e-9, "muss zum @Scheduled(every = \"1s\") in GameTick passen");
  }

  @Test
  void spieltagUndKampftickHaengenAusschliesslichAmRegler() {
    assertEquals(Clock.hoursToMs(24), GameConstants.GAME_DAY_MS, 1e-9);
    assertEquals(24 * 2500 / Clock.GAME_SPEED_MULTIPLIER, GameConstants.GAME_DAY_MS, 1e-6);
  }

  /**
   * Die entscheidende Eigenschaft des Reglers: eine je Tick verbuchte Rate
   * ergibt über eine SPIELSTUNDE immer denselben Betrag, unabhängig vom Tempo.
   * Geprüft am Bevölkerungsverbrauch – genau der Wert, der vorher als fester
   * Betrag JE TICK definiert war und damit als einziger nicht mitskaliert hätte.
   */
  @Test
  void verbrauchJeSpielstundeIstTempounabhaengig() {
    double perHour = GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get("p_grundnahrung");
    double perTick = perHour * GameConstants.TICK_GAME_HOURS;
    double ticksPerGameHour = 1 / GameConstants.TICK_GAME_HOURS;
    assertEquals(perHour, perTick * ticksPerGameHour, 1e-12);
  }

  /**
   * Auch die tickweise Glättung muss am Regler hängen: die pro SPIELSTUNDE
   * verbleibende Restgewichtung muss bei jedem Tempo dieselbe sein. Ohne die
   * Umrechnung über {@link Formulas#smoothingAlpha} wäre sie die einzige
   * Größe, deren Reaktionszeit in Realsekunden festgeschrieben bliebe.
   */
  @Test
  void glaettungReagiertInSpielstundenNichtInRealsekunden() {
    for (double tau : new double[]{Formulas.POWER_COVERAGE_SMOOTHING_TAU_HOURS,
        Formulas.CONSUMPTION_BUDGET_SMOOTHING_TAU_HOURS, Formulas.LIVING_STANDARD_SMOOTHING_TAU_HOURS}) {
      double alpha = Formulas.smoothingAlpha(tau);
      double retainedPerGameHour = Math.pow(1 - alpha, 1 / GameConstants.TICK_GAME_HOURS);
      assertEquals(Math.exp(-1 / tau), retainedPerGameHour, 1e-9,
          "Restgewichtung je Spielstunde muss nur von tau abhängen, nicht vom Tempo");
    }
  }

  /** Die Zeitkonstanten sind so gewählt, dass bei Tempo 1 exakt die früheren Alpha-Werte herauskommen. */
  @Test
  void zeitkonstantenReproduzierenDieHistorischenAlphaWerteBeiTempoEins() {
    double tickGameHoursAtSpeedOne = GameConstants.TICK_MS / SharedConstants.baseRealMsPerGameHour();
    assertEquals(0.2, 1 - Math.exp(-tickGameHoursAtSpeedOne / Formulas.POWER_COVERAGE_SMOOTHING_TAU_HOURS), 1e-4);
    assertEquals(0.1, 1 - Math.exp(-tickGameHoursAtSpeedOne / Formulas.CONSUMPTION_BUDGET_SMOOTHING_TAU_HOURS), 1e-4);
    assertEquals(0.3, 1 - Math.exp(-tickGameHoursAtSpeedOne / Formulas.LIVING_STANDARD_SMOOTHING_TAU_HOURS), 1e-4);
  }
}
