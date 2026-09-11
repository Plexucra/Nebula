import { Id } from './common.model';

export interface Population {
  colonyId: Id;
  /** Gesamtbevölkerung – Arbeiter plus Akademiker (Umsetzungskonzept/38). */
  currentCount: number;
  /** Akademiker: keine Arbeitskraft, eigener Bedarf, nur in bezahlten Plätzen eines Forschungszentrums. */
  academics: number;
  /** Wachstumsrate in Einwohnern je Spielstunde, gesetzt am Kolonietag. */
  growthRatePerInterval: number;
  /** Vorrat der Bevölkerung je Grundkonsumgut (Umsetzungskonzept/36), getrennt vom Kolonielager. */
  stock: Record<Id, number>;
}

/** Höchststand-Regel gegen Bevölkerungs-Exploits, siehe Konzeption/06_..., §4. */
export interface PopulationMoneySupplyState {
  planetId: Id;
  historicalPeakPopulation: number;
  lastPopulation: number;
}

/**
 * Wozu ein Gut gerade nachgefragt wird (Umsetzungskonzept/38): Pflichtgut der
 * Arbeiter auf der aktuellen Wohnstufe, Wachstumsgut der nächsten Stufe
 * (mitgekauft, deckelt das Wachstum) oder Gut der Akademiker.
 */
export type PopulationSupplyGroup = 'Essential' | 'Growth' | 'Academic';

/** Versorgungslage je Gut: Vorrat, Tagesbedarf, Reichweite, Deckung, das Gebot der Bevölkerung und der günstigste Brief (Umsetzungskonzept/36 und 38). */
export interface PopulationSupplyGood {
  productTypeId: Id;
  name: string;
  group: PopulationSupplyGroup;
  stock: number;
  dailyNeed: number;
  daysLeft: number;
  /** Deckung des letzten Kolonietags, 0..1,5; null vor dem ersten Kolonietag. */
  coverage: number | null;
  orderAvailable: boolean;
  /** Günstigster kaufbarer Brief am eigenen Posten; null ohne Order. */
  askPrice: number | null;
  /** Limit des stehenden Gebots der Bevölkerung; null, wenn keines steht (Vorrat voll oder kein Budget). */
  bidPrice: number | null;
  bidQuantity: number;
}

export interface PopulationSupply {
  goods: PopulationSupplyGood[];
  /** Spielzeit des nächsten Kolonietags (Gebote erneuern, Verbrauch, Wachstum), 0 = keiner geplant. */
  nextPurchaseAt: number;
  targetDays: number;
  // --- Arbeiter und Akademiker (Umsetzungskonzept/38) ---
  workers: number;
  academics: number;
  /** Wohnstufe der Güterstaffel und ihre Einwohnergrenze. */
  consumerStage: number;
  consumerStageCap: number;
  /** Wachstumsgut der nächsten Stufe; null auf der letzten Stufe. */
  growthGoodId: Id | null;
  growthGoodCovered: boolean;
  /** Bezahlte Plätze des Forschungszentrums (0 ohne Zentrum). */
  researchCapacity: number;
  /** Deckel der Akademiker: Kapazität der höchsten Zentrumsstufe, deren Güter alle gedeckt sind. */
  academicCap: number;
  researchLevel: number;
  academicStandardOfLivingPct: number;
  // --- Kaufkraft ---
  /** Geglättetes Einkommen des Bevölkerungs-Wallets je Spieltag. */
  dailyIncome: number;
  /** Tagesbudget der Gebote am letzten Kolonietag. */
  dailyBudget: number;
}
