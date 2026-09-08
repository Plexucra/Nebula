package de.nebula.engine;

/**
 * Zeitkompression für den Prototyp: 1 Spielstunde =
 * {@link #REAL_MS_PER_GAME_HOUR} Realzeit-Millisekunden, zentralisiert an
 * dieser einen Stelle. JEDE Spieldauer im Backend – Bauzeit, Reisezeit,
 * Kampftick, Aufbewahrungsfrist, Kündigungsfrist – wird in SPIELSTUNDEN
 * ausgedrückt und ausschließlich über {@link #hoursToMs(double)} in
 * Realzeit umgerechnet. Damit gibt es genau einen Regler für das Spieltempo
 * (siehe {@link #GAME_SPEED_MULTIPLIER}), und keine Regel kann sich ihm
 * entziehen.
 *
 * <p>Der Realzeit-Tick selbst ({@code GameTick}, 1 s) bleibt vom Regler
 * unberührt – ein Tick deckt bei höherem Tempo schlicht mehr Spielstunden ab
 * ({@link GameConstants#TICK_GAME_HOURS}). Alles, was je Tick berechnet wird,
 * muss deshalb eine RATE JE SPIELSTUNDE sein, multipliziert mit
 * {@code TICK_GAME_HOURS} – nie ein fester Betrag je Tick.</p>
 */
public final class Clock {
  private Clock() {
  }

  /**
   * Separat einstellbarer Spielzeit-Multiplikator aus
   * {@code shared/game-constants.json} – 1 = Ausgangstempo, 4 = viermal so
   * schnell. Der einzige Wert, an dem für schnellere Testläufe gedreht wird;
   * BEIDE Anwendungen lesen ihn aus derselben Datei (siehe
   * {@link SharedConstants} und {@code core/shared-constants.ts}).
   */
  public static final double GAME_SPEED_MULTIPLIER = SharedConstants.gameSpeedMultiplier();

  /**
   * Wirksame Zeitkompression = Ausgangswert / Tempo-Regler. Der Wert steht in
   * {@code shared/game-constants.json} und wird von BEIDEN Anwendungen
   * identisch berechnet, damit Backend und Frontend garantiert dieselbe
   * Zeitbasis verwenden.
   */
  public static final double REAL_MS_PER_GAME_HOUR = SharedConstants.baseRealMsPerGameHour() / GAME_SPEED_MULTIPLIER;

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
