import sharedConstants from '@shared/game-constants.json';

/**
 * Die wenigen Ausgangswerte, die das Frontend UNABHÄNGIG vom Server kennen
 * muss – importiert aus `/shared/game-constants.json`, derselben Datei, die
 * auch das Backend zur Laufzeit liest (siehe `SharedConstants.java`).
 * Damit gibt es für diese Werte genau EINE Quelle und keine zweite,
 * abweichende Kopie im TypeScript (Umsetzungskonzept/15_...md, Auftrag 3).
 *
 * Alles andere – Formeln, Kataloge, Regeln – lebt ausschließlich im Backend
 * und erreicht das Frontend über die WebSocket-Befehle.
 */
export const REAL_MS_PER_GAME_HOUR: number = sharedConstants.realMsPerGameHour;
export const NOTIFICATION_RETENTION_GAME_HOURS: number = sharedConstants.notificationRetentionGameHours;
export const MESSAGE_RETENTION_GAME_HOURS: number = sharedConstants.messageRetentionGameHours;
/** Kündigungsfristen für Friedens-/Handelsverträge (Umsetzungskonzept/21_...md) – für Hinweistexte. */
export const PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS: number = sharedConstants.peaceTreatyTerminationNoticeGameHours;
export const TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS: number = sharedConstants.tradeAgreementTerminationNoticeGameHours;
/** Lebensstandard-Totband der Bevölkerungsentwicklung (Umsetzungskonzept/17_...md, Teil C) – für Hinweistexte. */
export const LIVING_STANDARD_SHRINK_BELOW_PCT: number = sharedConstants.livingStandardShrinkBelowPct;
export const LIVING_STANDARD_GROWTH_FROM_PCT: number = sharedConstants.livingStandardGrowthFromPct;
export const SLOTS_PER_INFRASTRUCTURE_LEVEL: number = sharedConstants.slotsPerInfrastructureLevel;

/** Spielstunden → Spieltage, für Hinweistexte („wird nach N Spieltagen gelöscht"). */
export function gameHoursToGameDays(gameHours: number): number {
  return gameHours / 24;
}

/** Spielstunden → Realzeit-Minuten bei der aktuellen Zeitkompression, für Hinweistexte. */
export function gameHoursToRealMinutes(gameHours: number): number {
  return (gameHours * REAL_MS_PER_GAME_HOUR) / 60000;
}
