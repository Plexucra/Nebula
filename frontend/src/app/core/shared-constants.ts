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
/**
 * Separat einstellbarer Spielzeit-Multiplikator (1 = Ausgangstempo, 4 =
 * viermal so schnell). Der einzige Regler für das Spieltempo; Backend und
 * Frontend lesen ihn aus derselben Datei und rechnen ihn identisch um
 * (siehe `Clock.java`).
 */
export const GAME_SPEED_MULTIPLIER: number = sharedConstants.gameSpeedMultiplier;
/** Wirksame Zeitkompression = Ausgangswert / Tempo-Regler, exakt wie `Clock.REAL_MS_PER_GAME_HOUR`. */
export const REAL_MS_PER_GAME_HOUR: number = sharedConstants.baseRealMsPerGameHour / GAME_SPEED_MULTIPLIER;
export const NOTIFICATION_RETENTION_GAME_HOURS: number = sharedConstants.notificationRetentionGameHours;
export const MESSAGE_RETENTION_GAME_HOURS: number = sharedConstants.messageRetentionGameHours;
/** Kündigungsfristen für Friedens-/Handelsverträge (Umsetzungskonzept/21_...md) – für Hinweistexte. */
export const PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS: number = sharedConstants.peaceTreatyTerminationNoticeGameHours;
export const TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS: number = sharedConstants.tradeAgreementTerminationNoticeGameHours;
/** Lebensstandard-Totband der Bevölkerungsentwicklung (Umsetzungskonzept/17_...md, Teil C) – für Hinweistexte. */
export const LIVING_STANDARD_SHRINK_BELOW_PCT: number = sharedConstants.livingStandardShrinkBelowPct;
export const LIVING_STANDARD_GROWTH_FROM_PCT: number = sharedConstants.livingStandardGrowthFromPct;
export const SLOTS_PER_INFRASTRUCTURE_LEVEL: number = sharedConstants.slotsPerInfrastructureLevel;
/** Kolonisation (Umsetzungskonzept/24_...md) – für Hinweistexte und Bau-Vorschauen. */
export const START_POPULATION: number = sharedConstants.startPopulation;
export const COLONY_SHIP_BUILD_GAME_HOURS: number = sharedConstants.colonyShipBuildGameHours;
export const COLONY_SHIP_MIN_LOYALTY_PCT: number = sharedConstants.colonyShipMinLoyaltyPct;
export const COLONIZATION_GAME_HOURS: number = sharedConstants.colonizationGameHours;
/** Fassungsvermögen des Treibstofftanks je Schiff (Umsetzungskonzept/26_...md). */
export const JUMP_FUEL_TANK_PER_SHIP: number = sharedConstants.jumpFuelTankPerShip;

/** Spielstunden → Spieltage, für Hinweistexte („wird nach N Spieltagen gelöscht"). */
export function gameHoursToGameDays(gameHours: number): number {
  return gameHours / 24;
}

/** Spielstunden → Realzeit-Minuten bei der aktuellen Zeitkompression, für Hinweistexte. */
export function gameHoursToRealMinutes(gameHours: number): number {
  return (gameHours * REAL_MS_PER_GAME_HOUR) / 60000;
}
