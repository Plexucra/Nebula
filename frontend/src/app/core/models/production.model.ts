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

export interface WarehouseEntry {
  colonyId: Id;
  productTypeId: Id;
  quantity: number;
}

/**
 * Ein Schritt in der einmalig vorausberechneten Produktionskette eines
 * Auftrags (siehe `ChainPlan`) – vom Rohstoff bis zum angeforderten
 * Endprodukt selbst (letzter Eintrag). Rein informativ für die aufklappbare
 * Detailansicht; für die Ausführung zählt nur `ChainPlan.totalHours` bzw.
 * die aggregierten Mengen je Schritt (siehe `SimulatedGameApiService.
 * planChain`, Konzeption/Umsetzungskonzept/10_...).
 */
export interface ChainPlanStep {
  productTypeId: Id;
  /** Gesamtbedarf über alle georderten Einheiten des Wurzelprodukts hinweg. */
  quantityNeeded: number;
  /** Davon bereits im Kolonielager vorhanden (wird nicht erneut produziert). */
  quantityFromWarehouse: number;
  /** Davon tatsächlich zu produzieren. */
  quantityToProduce: number;
  /** Produktionszeit in Spielstunden für `quantityToProduce`, zu den Geschwindigkeitsfaktoren der Kolonie zum Berechnungszeitpunkt. */
  hours: number;
}

export interface ChainPlan {
  /** Summe aller `steps[].hours` – die einzige für die Ausführung relevante Zeitgröße. */
  totalHours: number;
  steps: ChainPlanStep[];
  /**
   * false = ohne "automatisch mitproduzieren" nicht ausführbar, weil
   * mindestens ein Rohstoff-Schritt einen Mangel aufweist, den die Kolonie
   * nicht aus eigenem Lagerbestand decken kann.
   */
  feasible: boolean;
}

export type ProductionQueueStatus = 'queued' | 'running' | 'stopped' | 'done';

/**
 * Sequentieller Warteschlangeneintrag: pro Kolonie und Warteschlange
 * (Produktion/Werft/Rekrutierung, siehe `ShipyardQueueEntry`/
 * `RecruitmentQueueEntry`) ist höchstens ein Eintrag `running`, der Rest
 * wartet (`queued`). Siehe Konzeption/Umsetzungskonzept/10_
 * Sequentielle_Produktionsauftraege_und_Ereignissystem.md.
 */
export interface ProductionQueueEntry {
  id: Id;
  colonyId: Id;
  productTypeId: Id;
  quantity: number;
  /** Checkbox "Nicht vorhandene Vorprodukte automatisch mitproduzieren". */
  autoProduceMissing: boolean;
  /** Checkbox "Nach Erfolg erneut einreihen" – Auftrag wird bei Fertigstellung ans Ende der Warteschlange neu angehängt statt entfernt. */
  requeueOnComplete: boolean;
  status: ProductionQueueStatus;
  /** Benachrichtigungscode, der zum Stopp geführt hat (z. B. 503), nur bei `status: 'stopped'`. */
  stoppedReasonCode: number | null;
  plan: ChainPlan;
  startedAt: number | null;
  /** Ereignis-Zeitpunkt: einziger Trigger für den Fortschritt, keine Pro-Einheit-Schleife mehr. */
  endsAt: number | null;
}
