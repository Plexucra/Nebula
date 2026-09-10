import { Id } from './common.model';

export interface Population {
  colonyId: Id;
  currentCount: number;
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

/** Versorgungslage einer Kolonie: Vorrat, Tagesbedarf, Reichweite je Gut und der nächste Tageseinkauf (Umsetzungskonzept/36). */
export interface PopulationSupplyGood {
  productTypeId: Id;
  name: string;
  stock: number;
  dailyNeed: number;
  daysLeft: number;
  /** Deckung des letzten Kolonietags, 0..1,5; null vor dem ersten Kolonietag. */
  coverage: number | null;
  orderAvailable: boolean;
}

export interface PopulationSupply {
  goods: PopulationSupplyGood[];
  /** Spielzeit des nächsten Tageseinkaufs, 0 = keiner geplant. */
  nextPurchaseAt: number;
  targetDays: number;
  emergencyBelowDays: number;
}
