package de.nebula.engine;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/clock.ts}. Zeitkompression
 * für den Prototyp: 1 Spielstunde = {@link #REAL_MS_PER_GAME_HOUR} Realzeit-Millisekunden,
 * zentralisiert an dieser einen Stelle. Ein späteres echtes Backend könnte stattdessen
 * echte Stunden verwenden – bis dahin gilt exakt dieselbe Kompression wie im Frontend,
 * sonst würden Zeitangaben zwischen TS-Referenz und Java-Port auseinanderlaufen.
 */
public final class Clock {
  private Clock() {
  }

  /**
   * Zeitkompression – der Wert steht in {@code shared/game-constants.json}
   * und wird von BEIDEN Anwendungen gelesen (siehe {@link SharedConstants}),
   * damit Backend und Frontend garantiert dieselbe Zeitbasis verwenden.
   */
  public static final double REAL_MS_PER_GAME_HOUR = SharedConstants.realMsPerGameHour();

  public static double hoursToMs(double hours) {
    return hours * REAL_MS_PER_GAME_HOUR;
  }

  public static double msToHours(double ms) {
    return ms / REAL_MS_PER_GAME_HOUR;
  }

  public static long now() {
    return System.currentTimeMillis();
  }
}
