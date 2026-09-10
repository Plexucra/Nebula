package de.nebula.state;

/**
 * Die Ereignisse des Spiels, die zu einem vorab bekannten Spielzeitpunkt
 * fällig werden (siehe {@link GameEvents}). Zwei Sorten:
 *
 * <ul>
 *   <li><b>Fälligkeiten je Objekt</b> – beim Anlegen des Vorgangs geplant
 *       (Bauauftrag, Flug, Gefechtsrunde, ...), das Ziel ist die Id des
 *       Objekts. Der Behandler prüft beim Feuern, ob das Objekt noch genau
 *       diese Fälligkeit hat; sonst ist das Ereignis veraltet und wird
 *       verworfen.</li>
 *   <li><b>Wiederkehrende Aufgaben</b> ohne Objektbezug – planen sich nach dem
 *       Feuern selbst neu. Der Wirtschaftsschritt ist eine davon: er bündelt
 *       die fünf Ratenprozesse (Energie, Unterhalt, Konsum, Kernwerte,
 *       Wachstum), die keine Fälligkeit haben, sondern eine Rate.</li>
 * </ul>
 */
public enum GameEventType {
  BUILDING_COMPLETED,
  DEFENSE_ACTIVATED,
  FLEET_ARRIVED,
  BATTLE_ROUND,
  GROUND_BATTLE_ROUND,
  PRODUCTION_COMPLETED,
  SHIP_COMPLETED,
  RECRUITMENT_COMPLETED,
  COLONIZATION_COMPLETED,
  GROUND_FORCE_MOVED,
  SPECIALIZATION_DECAY,
  TREATY_ENDED,
  /** Wiederkehrend: die Ratenprozesse der Wirtschaft, alle {@code GameConstants.TICK_MS}. */
  ECONOMY_STEP,
  /** Wiederkehrend: Ausgleichsfonds, einmal je Spieltag. */
  WEALTH_REDISTRIBUTION,
  /** Wiederkehrend: Universums-Statistik und Bevölkerungsverlauf. */
  STATS_SNAPSHOT,
  /** Wiederkehrend: Aufräumen abgelaufener Meldungen und inaktiver Kommandanten. */
  RETENTION_CLEANUP
}
