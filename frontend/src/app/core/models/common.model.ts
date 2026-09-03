/**
 * Grundlegende Alias-Typen. Bewusst simple string-IDs (keine Branded Types) –
 * das hier ist ein Simulations-Prototyp, keine Produktionsarchitektur.
 */
export type Id = string;

/** Ein für den Client laufender Auftrag mit Fertigstellungszeitpunkt –
 *  spiegelt das im Konzept dokumentierte Order/Job-Rückgabeobjekt
 *  (Umsetzungskonzept/00_..., §5) auch im simulierten Client. */
export interface TimedOrder {
  id: Id;
  startedAt: number;
  completesAt: number;
}
