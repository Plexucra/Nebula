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

/**
 * Eine Zeile des Versorgungsinventars (Umsetzungskonzept/25_...md): was liegt im
 * Lager, wie schnell wird es verbraucht, wie lange reicht es noch.
 * `quantity` ist immer eine ganze Stückzahl – Bruchteile leben ausschließlich in
 * den Übertragskonten und stehen hier als `pendingFraction`.
 */
export interface SupplyInventoryEntry {
  productTypeId: Id;
  name: string;
  category: string;
  quantity: number;
  consumptionPerGameHour: number;
  /** null, wenn die Kolonie dieses Produkt nicht laufend verbraucht. */
  coverageGameHours: number | null;
  pendingFraction: number;
  /** Nur Stabilisiertes Elerium: im Energiespeicher vorgehalten (zählt zur Reichweite, nicht zum Lager). */
  reserved: number;
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
