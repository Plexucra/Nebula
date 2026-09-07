import { Id } from './common.model';

/**
 * `Infrastructure`: ausschließlich das Gebäude "Infrastruktur" (Bebauungsplätze,
 * Elerium-Verbrauch, planetweit begrenzt). `Housing`: Wohnkomplex – liefert
 * allein die Wohnkapazität. Siehe Umsetzungskonzept/17_...md.
 */
export type BuildingCategory = 'Infrastructure' | 'Housing' | 'ProductionFacility' | 'PlanetaryDefense';

/** Baustoffbedarf ab Zielstufe `fromLevel`: `ceil(baseQuantity × Stufe^1,3)` je Ausbau (Backend rechnet, siehe `MaterialRequirement`). */
export interface BuildingMaterial {
  productTypeId: Id;
  baseQuantity: number;
  fromLevel: number;
}

/** Statischer Bauplan/Katalogeintrag – Daten in `shared/catalog/buildings.json`, Regeln in Umsetzungskonzept/17_...md. */
export interface BuildingType {
  id: Id;
  name: string;
  category: BuildingCategory;
  description: string;
  baseCostPerLevel: number;
  baseHoursPerLevel: number;
  upkeepPerLevel: number;
  productionSlotsPerLevel: number | null;
  /** Für Housing: Wohnkapazität pro Level. */
  housingCapacityPerLevel: number | null;
  materials: BuildingMaterial[];
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
 * Bebauungsplätze einer Kolonie (Umsetzungskonzept/17_...md): jede
 * Infrastruktur-Stufe liefert einen Platz, jede Stufe jedes anderen Gebäudes
 * (inkl. laufender Ausbauten) belegt einen. Kommt fertig vom Backend (`buildSlots`).
 */
export interface BuildSlots {
  total: number;
  used: number;
  free: number;
  infrastructureLevel: number;
  planetInfrastructureTotal: number;
  planetInfrastructureMax: number;
}

/** Ein Baustoff eines Ausbauschritts mit Bedarf und Lagerbestand – Kosten sind VOR dem Klick sichtbar. */
export interface MaterialRequirement {
  productTypeId: Id;
  required: number;
  available: number;
}

/**
 * Versorgungszustand der Infrastruktur: sie verbraucht laufend Stabilisiertes
 * Elerium; reicht der Bestand nicht, sinkt `coverageRatio` (Blackout: Produktion
 * 10 %, Kernwerte halbiert) – geglättet, kein hartes Ein/Aus.
 */
export interface ColonyPowerState {
  colonyId: Id;
  coverageRatio: number;
}
