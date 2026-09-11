package de.nebula.npcbot;

/**
 * Die Zeitkompression des Servers, gelesen aus derselben Datei, die auch
 * Backend ({@code de.nebula.engine.Clock}/{@code SharedConstants}) und
 * Frontend ({@code core/shared-constants.ts}) verwenden:
 * {@code shared/game-constants.json} (siehe {@link SharedConstants}).
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

  /** Wirksame Zeitkompression, identisch zu {@code Clock.REAL_MS_PER_GAME_HOUR}. */
  static final double REAL_MS_PER_GAME_HOUR =
      SharedConstants.number("baseRealMsPerGameHour") / SharedConstants.number("gameSpeedMultiplier");

  static long hoursToMs(double gameHours) {
    return (long) (gameHours * REAL_MS_PER_GAME_HOUR);
  }

  /**
   * Lohn je Einwohner-Arbeitsstunde (Umsetzungskonzept/38) – dieselbe Quelle wie
   * {@code GameConstants.WAGE_PER_WORK_HOUR} im Backend. Eine voll beschäftigte
   * Kolonie zahlt Einwohner × diesen Satz je Spielstunde.
   */
  static final double WAGE_PER_WORK_HOUR = SharedConstants.number("wagePerWorkHour");
}
