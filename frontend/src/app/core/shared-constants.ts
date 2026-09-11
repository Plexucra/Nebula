import sharedConstants from '@shared/game-constants.json';

/**
 * Die wenigen Ausgangswerte, die das Frontend UNABHÄNGIG vom Server kennen
 * muss – importiert aus `/shared/game-constants.json`, derselben Datei, die
 * auch Backend und Bot zur Laufzeit lesen (siehe `SharedConstants.java`).
 * Damit gibt es für diese Werte genau EINE Quelle und keine zweite,
 * abweichende Kopie im TypeScript (Umsetzungskonzept/15_...md, Auftrag 3).
 *
 * Alles andere – Formeln, Kataloge, Regeln – lebt ausschließlich im Backend
 * und erreicht das Frontend über die WebSocket-Befehle. Hier steht nur, was
 * die Oberfläche tatsächlich für Hinweistexte und Schwellen verwendet
 * (unbenutzte Exporte am 11.9.2026 entfernt).
 */

/**
 * REALZEIT-AUSNAHME (siehe `_realTimeException` in `shared/game-constants.json`
 * und `GameConstants.NOTIFICATION_RETENTION_REAL_MS`): Aufbewahrungsfristen
 * zählen als EINZIGE Zeitangaben des Spiels in ECHTEN TAGEN, nicht in
 * Spielstunden – sie hängen deshalb NICHT am Tempo-Regler. Eine Frist, nach der
 * ein Mensch etwas gelesen haben soll, wird nicht kürzer, weil die Spieluhr
 * schneller läuft.
 */
export const NOTIFICATION_RETENTION_REAL_DAYS: number = sharedConstants.notificationRetentionRealDays;
/** REALZEIT-AUSNAHME, siehe `NOTIFICATION_RETENTION_REAL_DAYS`. */
export const MESSAGE_RETENTION_REAL_DAYS: number = sharedConstants.messageRetentionRealDays;
/** Kündigungsfristen für Friedens-/Handelsverträge (Umsetzungskonzept/21_...md) – für Hinweistexte. */
export const PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS: number = sharedConstants.peaceTreatyTerminationNoticeGameHours;
export const TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS: number = sharedConstants.tradeAgreementTerminationNoticeGameHours;
/** Kolonisation (Umsetzungskonzept/24_...md) – für Hinweistexte und Bau-Vorschauen. */
export const START_POPULATION: number = sharedConstants.startPopulation;
export const COLONIZATION_GAME_HOURS: number = sharedConstants.colonizationGameHours;
/** Standard-Reichweite des Energiespeichers in Spielstunden (Umsetzungskonzept/32_...md) – Schwelle der Ausbauwarnung. */
export const ENERGY_RESERVE_DEFAULT_GAME_HOURS: number = sharedConstants.energyReserveDefaultGameHours;
/** Trägersprung ohne Gateway (Umsetzungskonzept/06_...md) – für Hinweistexte. */
export const CARRIER_TRANSIT_TIME_FACTOR: number = sharedConstants.carrierTransitTimeFactor;
export const CARRIER_TRANSIT_FUEL_FACTOR: number = sharedConstants.carrierTransitFuelFactor;
/**
 * Die Grundkonsumgüter der Bevölkerung in Einkaufsreihenfolge – die Schlüssel
 * der Bedarfstabelle `consumerNeedPerCapitaPerGameHour`, aus der auch
 * `GameConstants.CONSUMER_GOODS_ORDER` im Backend entsteht.
 */
export const CONSUMER_GOODS: readonly string[] = Object.keys(sharedConstants.consumerNeedPerCapitaPerGameHour);

/** Spielstunden → Spieltage, für Hinweistexte („wird nach N Spieltagen gelöscht"). */
export function gameHoursToGameDays(gameHours: number): number {
  return gameHours / 24;
}
