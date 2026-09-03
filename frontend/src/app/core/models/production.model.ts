import { Id } from './common.model';

export interface Specialization {
  colonyId: Id;
  productTypeId: Id;
  /** Stufe der Spezialisierung, 0 = keine. */
  currentLevel: number;
  /** Kumulierte "Erfahrung" in produzierten Einheiten, treibt Stufenaufstieg. */
  experience: number;
  thresholdForNextLevel: number;
}

export interface ProductionQueueEntry {
  id: Id;
  colonyId: Id;
  productTypeId: Id;
  quantity: number;
  producedSoFar: number;
  startedAt: number;
  /** Fertigstellung der nächsten Einheit. */
  nextUnitCompletesAt: number;
}

export interface WarehouseEntry {
  colonyId: Id;
  productTypeId: Id;
  quantity: number;
}

/**
 * Dauerauftrag: produziert automatisch nach, solange der Lagerbestand unter
 * `maxStock` liegt und Ausgangsstoffe vorhanden sind. Fehlen sie, pausiert
 * die Produktion – bei Nachschub wird ohne erneute Bestätigung fortgesetzt.
 * Läuft unabhängig von und parallel zu `ProductionQueueEntry`-Aufträgen.
 */
export interface AutoProductionOrder {
  id: Id;
  colonyId: Id;
  productTypeId: Id;
  maxStock: number;
  nextUnitCompletesAt: number;
  /**
   * 0 = keine automatische Verkaufsorder. Bei einem Wert > 0 wird eine einzelne
   * Verkaufsorder für dieses Produkt am Systemmarkt gepflegt: Sobald neue
   * Einheiten produziert werden, wird ihre Menge nachgezogen; ändert sich der
   * Preis hier, wird die Order neu bepreist. Verweist auf `linkedSellOrderId`.
   */
  localPrice: number;
  linkedSellOrderId: Id | null;
}
