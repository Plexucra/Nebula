import { Id } from './common.model';

export interface Colony {
  id: Id;
  planetId: Id;
  systemId: Id;
  ownerId: Id;
  name: string;
  foundedAt: number;
  isHomeworld: boolean;
}

/**
 * Eine laufende Koloniegründung (Umsetzungskonzept/24_...md): das
 * Kolonisationsschiff ist bereits verbraucht, die Kolonie entsteht erst bei
 * `endsAt`. Bis dahin existiert sie NICHT – sie taucht daher in keiner
 * Kolonieliste auf.
 */
export interface Colonization {
  id: Id;
  planetId: Id;
  systemId: Id;
  ownerId: Id;
  fleetId: Id;
  colonyName: string;
  startedAt: number;
  endsAt: number;
}

/** Die vier zentralen Planetenwerte, siehe Konzeption/07_..., §5. */
export interface PlanetStats {
  colonyId: Id;
  infrastructurePct: number;
  securityPct: number;
  standardOfLivingPct: number;
  /** Loyalität ist bei 100% gedeckelt. */
  loyaltyPct: number;
  lastRecalculatedAt: number;
}
