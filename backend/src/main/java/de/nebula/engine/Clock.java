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

  public static final double REAL_MS_PER_GAME_HOUR = 2500;

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
