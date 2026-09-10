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
 * <h2>Spieluhr und Realuhr</h2>
 * <p>{@link #now()} ist die SPIELUHR: sie läuft im Takt der Realuhr, kann aber
 * gegen sie um {@link #offsetMs()} verschoben sein. Heute ist der Versatz null
 * (oder per {@code -Dnebula.game-clock.start=<epoch-ms>} beim Start gesetzt);
 * sobald ein gespeicherter Spielstand geladen wird, läuft die Spieluhr dort
 * weiter, wo sie beim Speichern stand – die Zeit, in der der Server nicht lief,
 * hat im Spiel nicht stattgefunden. Sonst wären nach einem Neustart alle
 * Flotten angekommen und alle Bauten fertig, während der Konsum ausgesetzt
 * hätte.</p>
 *
 * <p>Daraus folgt eine Regel für den GANZEN Zustand: <b>jeder Zeitstempel im
 * Spielzustand ist Spielzeit</b> ({@code completesAt}, {@code arrivesAt},
 * {@code createdAt}, {@code nextTickAt}, ...), und jeder Vergleich damit muss
 * gegen {@link #now()} laufen, nie gegen {@code System.currentTimeMillis()}.
 * Der Server schickt seine Spielzeit in jeder Nachricht mit
 * ({@code ServerMessage.gameNow}); Oberfläche und Bots rechnen ihre Countdowns
 * damit, nicht mit ihrer eigenen Wanduhr.</p>
 *
 * <p>Die einzigen Realzeit-Größen sind die, die messen, wann ein MENSCH
 * zuletzt da war: {@code Player.lastSeenAt} und die Abkühlzeit der
 * Versorgungswarnung ({@code lastSupplyWarningAt}). Sie werden mit
 * {@link #realNow()} gesetzt UND verglichen und nie mit Spielzeit gemischt.</p>
 *
 * <p>Der Sekundentakt des Ereignisplaners ({@code GameTick}) bleibt vom Regler
 * unberührt – bei höherem Tempo vergeht je Realsekunde mehr Spielzeit und es
 * werden mehr Ereignisse fällig. Alles, was periodisch verbucht wird (der
 * Kolonietag, {@link GameConstants#GAME_DAY_HOURS}), ist eine RATE JE
 * SPIELSTUNDE mal Schrittweite – nie ein fester Betrag je Schritt.</p>
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

  /** Systemeigenschaft, mit der die Spieluhr beim Start auf einen Stand gesetzt wird (Epoch-Millisekunden). */
  public static final String START_PROPERTY = "nebula.game-clock.start";

  /** Realuhr minus Spieluhr, in Millisekunden. Null, solange kein Spielstand geladen und nichts gesetzt wurde. */
  private static volatile long offsetMs = initialOffset();

  private static long initialOffset() {
    String start = System.getProperty(START_PROPERTY);
    if (start == null || start.isBlank()) return 0;
    try {
      return System.currentTimeMillis() - Long.parseLong(start.trim());
    } catch (NumberFormatException e) {
      throw new IllegalStateException(START_PROPERTY + " muss Epoch-Millisekunden enthalten, war: " + start);
    }
  }

  public static double hoursToMs(double hours) {
    return hours * REAL_MS_PER_GAME_HOUR;
  }

  public static double msToHours(double ms) {
    return ms / REAL_MS_PER_GAME_HOUR;
  }

  /**
   * Zeit des Ereignisses, das dieser Thread gerade behandelt ({@code GameEvents.runDue}),
   * oder {@code null} außerhalb der Ereignisbehandlung. Prinzip der
   * ereignisgesteuerten Simulation: WÄHREND ein Ereignis behandelt wird, ist
   * seine Fälligkeit das "Jetzt" – Folgetermine (nächste Gefechtsrunde, der
   * Nachfolger eines Dauerauftrags, die Meldung dazu) rechnen von dort aus,
   * nicht von der Abarbeitungszeit. Im Betrieb liegen beide höchstens eine
   * Sekunde auseinander; in Tests, die vorspulen, entscheidet es darüber, ob
   * ein Nachfolger in der Zukunft liegt oder sofort wieder fällig ist.
   */
  private static final ThreadLocal<Long> EVENT_TIME = new ThreadLocal<>();

  /** Die SPIELUHR – für jeden Zeitstempel und jeden Zeitvergleich im Spielzustand. */
  public static long now() {
    Long eventTime = EVENT_TIME.get();
    return eventTime != null ? eventTime : wallGameNow();
  }

  /** Die Spieluhr nach der Wanduhr, auch mitten in einer Ereignisbehandlung – nur für den Planer selbst. */
  public static long wallGameNow() {
    return System.currentTimeMillis() - offsetMs;
  }

  /** Nur für {@code GameEvents}: Beginn bzw. Ende der Behandlung eines Ereignisses mit dieser Fälligkeit. */
  public static void enterEventTime(long at) {
    EVENT_TIME.set(at);
  }

  public static void leaveEventTime() {
    EVENT_TIME.remove();
  }

  public static Long currentEventTime() {
    return EVENT_TIME.get();
  }

  /** Die REALUHR – ausschließlich für Größen, die messen, wann ein Mensch da war (siehe Klassendoku). */
  public static long realNow() {
    return System.currentTimeMillis();
  }

  /** Realuhr minus Spieluhr in Millisekunden; positiv, wenn die Spieluhr nachgeht. */
  public static long offsetMs() {
    return offsetMs;
  }

  /**
   * Stellt die Spieluhr auf {@code gameNow} – der Einstieg für einen geladenen
   * Spielstand (und für Tests). Ab jetzt läuft sie von dort weiter.
   */
  public static void setGameNow(long gameNow) {
    offsetMs = System.currentTimeMillis() - gameNow;
  }
}
