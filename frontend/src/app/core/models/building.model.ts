import { Id } from './common.model';

export type BuildingCategory = 'Infrastructure' | 'ProductionFacility' | 'PlanetaryDefense';

/** Statischer Bauplan/Katalogeintrag – siehe Umsetzungskonzept/01_..., §1. */
export interface BuildingType {
  id: Id;
  name: string;
  category: BuildingCategory;
  description: string;
  maxLevel: number;
  /** Bebauungspunkte, die Level `n` belegt (Index 0 = Level 1). */
  buildPointsPerLevel: number;
  /** Basis-Credits-Kosten für Level `n` (linear mit level skaliert). */
  baseCostPerLevel: number;
  /** Basis-Bauzeit in Spielstunden für Level `n`. */
  baseHoursPerLevel: number;
  /** laufender Unterhalt pro Level, in Credits pro Intervall. */
  upkeepPerLevel: number;
  /** Für ProductionFacility: wie viele parallele Produktionsslots ein Level gibt. */
  productionSlotsPerLevel?: number;
  /** Für Infrastructure: wie viel Bevölkerungs-Referenzkapazität ein Level deckt. */
  populationCapacityPerLevel?: number;
}

export type DefenseActivationState = 'Inactive' | 'Activating' | 'Active';

/** Instanz eines Gebäudes auf einer Kolonie – Umsetzungskonzept/01_..., §1. */
export interface Building {
  id: Id;
  colonyId: Id;
  typeId: Id;
  level: number;
  /** Falls > 0: ein Ausbau-/Neubauauftrag läuft aktuell. */
  pendingOrder: { targetLevel: number; startedAt: number; completesAt: number } | null;
  activationState: DefenseActivationState | null;
  activationCompletesAt: number | null;
}

/**
 * Betriebszustand des Energienetzes (b_powergrid), siehe
 * Umsetzungskonzept/01_..., §3 "PowerUpkeepJob": Das Energienetz
 * verbraucht laufend Elerium-Zellen aus dem Kolonielager. Reicht der
 * Bestand nicht, sinkt `coverageRatio` unter 1 und mindert anteilig die
 * Wohnkapazitäts-Kapazität, die das Energienetz sonst beisteuert
 * (Blackout) – geglättet, kein hartes Ein/Aus.
 */
export interface ColonyPowerState {
  colonyId: Id;
  coverageRatio: number;
}
