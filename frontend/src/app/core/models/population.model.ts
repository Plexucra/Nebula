import { Id } from './common.model';

export interface Population {
  colonyId: Id;
  currentCount: number;
  growthRatePerInterval: number;
}

/** Höchststand-Regel gegen Bevölkerungs-Exploits, siehe Konzeption/06_..., §4. */
export interface PopulationMoneySupplyState {
  planetId: Id;
  historicalPeakPopulation: number;
  lastPopulation: number;
}
