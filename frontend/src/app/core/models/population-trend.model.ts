import { Id } from './common.model';
import { PopulationGrowthState } from './colony-speed.model';

/** Ein Messpunkt des Bevölkerungsverlaufs einer Kolonie. */
export interface PopulationSample {
  at: number;
  population: number;
  standardOfLivingPct: number;
  housingCapacity: number;
  growthState: PopulationGrowthState;
}

export type PopulationPhase = 'TooFewSamples' | 'Accelerating' | 'Steady' | 'Slowing' | 'Plateau' | 'Shrinking';
export type PopulationLimitingFactor = 'Supply' | 'Housing' | null;

/**
 * Bevölkerungsverlauf samt fertiger Einordnung der Wachstumsphase – die
 * Einordnung kommt aus dem Backend, weil sie eine Aussage über die
 * Spielregeln ist (siehe Umsetzungskonzept/18_...md).
 */
export interface PopulationTrend {
  samples: PopulationSample[];
  phase: PopulationPhase;
  limitingFactor: PopulationLimitingFactor;
  /** Abgedecktes Zeitfenster in Spielstunden. */
  windowGameHours: number;
}

export type PopulationTrendColonyId = Id;
