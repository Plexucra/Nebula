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
 * "alle 10 Sekunden", ein "so viel je Schritt"), bricht hier.
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
  void spieltagUndKampftickHaengenAusschliesslichAmRegler() {
    assertEquals(Clock.hoursToMs(GameConstants.GAME_DAY_HOURS), GameConstants.GAME_DAY_MS, 1e-9);
    assertEquals(24 * 2500 / Clock.GAME_SPEED_MULTIPLIER, GameConstants.GAME_DAY_MS, 1e-6);
  }

  /**
   * Der Kolonietag (Umsetzungskonzept/36) verbucht Raten je Spielstunde über
   * einen Spieltag. Der Tagesbedarf muss deshalb genau 24 Stundenbedarfe sein –
   * unabhängig davon, wie viele Realsekunden ein Spieltag gerade dauert.
   */
  @Test
  void tagesbedarfIstVierundzwanzigStundenbedarfeBeiJedemTempo() {
    double perHour = GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get("p_grundnahrung");
    assertEquals(perHour * 24 * 1000, de.nebula.state.Economy.dailyNeed(1000, "p_grundnahrung"), 1e-12);
  }

  /**
   * Die Glättung des Lebensstandards ist in Spielstunden definiert: die je
   * SPIELSTUNDE verbleibende Restgewichtung hängt nur von der Zeitkonstante
   * ab, nicht von der Schrittweite, mit der sie aufgerufen wird.
   */
  @Test
  void glaettungReagiertInSpielstundenNichtInSchritten() {
    double tau = Formulas.LIVING_STANDARD_SMOOTHING_TAU_HOURS;
    for (double step : new double[]{0.4, 1, 24, 48}) {
      double alpha = Formulas.smoothingAlpha(tau, step);
      double retainedPerGameHour = Math.pow(1 - alpha, 1 / step);
      assertEquals(Math.exp(-1 / tau), retainedPerGameHour, 1e-9,
          "Restgewichtung je Spielstunde muss nur von tau abhängen, nicht von der Schrittweite");
    }
  }
}
