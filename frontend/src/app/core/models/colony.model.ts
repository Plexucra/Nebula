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
