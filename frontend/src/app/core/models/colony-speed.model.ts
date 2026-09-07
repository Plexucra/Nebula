import { Id } from './common.model';
import { BuildSlots, MaterialRequirement } from './building.model';

export type PopulationGrowthState = 'Shrinking' | 'Holding' | 'Growing' | 'Overcrowded';

/** Vorschau für den nächsten Ausbauschritt EINES Gebäudetyps auf einer Kolonie. */
export interface BuildingUpgradePreview {
  typeId: Id;
  currentLevel: number;
  upgradeCost: number;
  upgradeHours: number;
  /** Produktionstempo der Anlage auf der AKTUELLEN Stufe in Prozent (Stufe 1 = 100%). */
  productionSpeedPct: number;
  /** Produktionstempo nach dem nächsten Ausbauschritt in Prozent. */
  nextProductionSpeedPct: number;
  /** Baustoffe des nächsten Ausbauschritts mit Lagerabgleich. */
  materials: MaterialRequirement[];
  /** true = belegt einen Bebauungsplatz (alle Gebäude außer Infrastruktur). */
  needsSlot: boolean;
  affordable: boolean;
  blockedReason: string | null;
}

/**
 * Fertig ausgerechnete Aufschlüsselung ALLER Faktoren, die in die
 * Produktionsgeschwindigkeit einer Kolonie eingehen – Datengrundlage der
 * Transparenz-Panels ("Produktionstempo dieser Kolonie").
 *
 * Diese Werte kommen VOLLSTÄNDIG aus dem Backend (`colonySpeedBreakdown`).
 * Früher rechnete das Frontend sie aus einer zweiten Kopie der Formeln
 * (`engine/formulas.ts`) nach; seit Umsetzungskonzept/15_...md, Auftrag 3
 * existiert die Regel nur noch an EINER Stelle, ohne dass die Anzeige
 * verloren geht – kein Faktor darf dem Spieler verborgen bleiben.
 */
export interface ColonySpeedBreakdown {
  population: number;
  /** Verfügbare Arbeitskräfte = Bevölkerung. Kein Tempo-Multiplikator, sondern eine Obergrenze je Fertigung. */
  availableWorkers: number;
  industryLevel: number;
  buildingSpeedFactor: number;
  blackout: boolean;
  /** Zufriedenheit = derselbe Faktor, der das Bevölkerungswachstum steuert, in Prozent. */
  satisfactionPct: number;
  /** Aktiver Zustand der Bevölkerungsentwicklung samt Schwellen (Umsetzungskonzept/17_...md, Teil C). */
  growthState: PopulationGrowthState;
  growthPerHour: number;
  shrinkBelowPct: number;
  growthFromPct: number;
  housingCapacity: number;
  buildSlots: BuildSlots;
  infrastructureEleriumPerHour: number;
  powerCoverage: number;
  buildingUpgrades: BuildingUpgradePreview[];
  /** productTypeId -> Tempobonus der Spezialisierung in Prozent. */
  specializationSpeedBonusPctByProduct: Record<Id, number>;
  /** productTypeId -> Fördergüte-Ausbeutefaktor (nur Rohstoffe/Tier 0). */
  concentrationFactorByProduct: Record<Id, number>;
}
