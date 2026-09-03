import { Id } from './common.model';

/**
 * "Drone" statt "Walker": die drei autonomen Waffenträgerklassen sind
 * unbemannt und werden von Soldaten nur ferngesteuert/kommandiert, nicht
 * bemannt (siehe Mechanik/05_..., §3).
 */
export type GroundUnitClass = 'Soldier' | 'LightDrone' | 'MediumDrone' | 'HeavyDrone';

export interface GroundUnitTypeDef {
  productTypeId: Id;
  class: GroundUnitClass;
  transportSlotUsage: number;
  /**
   * Drohnenklasse, die gekontert wird (×2 Schaden), analog zu den
   * Schiffsklassen (Mechanik/05_..., §3). Kein eigener Angriffs-/
   * Verteidigungswert — militärischer Wert = Produktionsaufwand
   * (ProductType.baseWorkforceRequired × baseProductionHours), siehe
   * Mechanik/04_..., §2. `null` bei Soldaten, die laut Mechanik/05_...
   * §3 keine eigene unmittelbare Kampfwirkung besitzen.
   */
  countersClass: GroundUnitClass | null;
}

/**
 * Siehe Mechanik/05_..., §3-4: Soldaten besitzen keine eigene
 * Kampfwirkung, sondern kommandieren/aktivieren autonome Drohnen aus
 * der Ferne — sie stecken nicht in den Drohnen drin.
 * - beim Eintrag `p_soldier`: activeCount = Soldaten, die aktuell
 *   Drohnen kommandieren; reserveCount = Reserve-Soldaten ohne
 *   passende Drohne.
 * - bei einem Drohnen-Eintrag: activeCount = aktive (kommandierte,
 *   kampffähige) Drohnen; reserveCount = Reserve-Drohnen ohne
 *   ausreichend Soldaten zur Fernsteuerung.
 * Wird nach jeder Rekrutierung/Produktion durch die Besatzungslogik im
 * Simulations-Backend neu verteilt (proportional über alle drei
 * Drohnenklassen, siehe SimulatedGameApiService.recalcCrewing).
 */
export interface GroundForceUnitStack {
  unitProductTypeId: Id;
  activeCount: number;
  reserveCount: number;
}

export interface GroundForceGroup {
  id: Id;
  ownerId: Id;
  colonyId: Id;
  units: GroundForceUnitStack[];
}

export interface RecruitmentQueueEntry {
  id: Id;
  colonyId: Id;
  unitProductTypeId: Id;
  count: number;
  producedSoFar: number;
  startedAt: number;
  nextUnitCompletesAt: number;
}
