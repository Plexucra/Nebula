import { Id } from './common.model';

export type ShipClass = 'Corvette' | 'Destroyer' | 'Cruiser' | 'Freighter' | 'Carrier' | 'TroopTransport';

export interface ShipTypeDef {
  /** entspricht einem ProductType.id mit category=Ship */
  productTypeId: Id;
  class: ShipClass;
  cargoCapacity: number;
  carrierSlotUsage: number;
  /**
   * Klasse, die von dieser Klasse gekontert wird (×2 Schaden), siehe
   * Mechanik/03_..., §2 und Mechanik/04_..., §4. Es gibt bewusst KEINE
   * eigenen Angriffs-/Hüllenwerte: Schaden und Haltbarkeit im Kampf
   * leiten sich ausschließlich aus dem Produktionsaufwand
   * (ProductType.baseWorkforceRequired × baseProductionHours) ab,
   * siehe Mechanik/04_..., §2-3.
   */
  countersClass: ShipClass | null;
}

export type FleetLocationType = 'ColonyOrbit' | 'System';
export type FleetStatus = 'Stationed' | 'Building';

export interface FleetShipGroup {
  shipProductTypeId: Id;
  quantity: number;
}

export interface Fleet {
  id: Id;
  ownerId: Id;
  name: string;
  locationType: FleetLocationType;
  locationColonyId: Id | null;
  systemId: Id;
  status: FleetStatus;
  ships: FleetShipGroup[];
}

export interface ShipyardQueueEntry {
  id: Id;
  colonyId: Id;
  shipProductTypeId: Id;
  quantity: number;
  producedSoFar: number;
  startedAt: number;
  nextUnitCompletesAt: number;
}
