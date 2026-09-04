import { Id } from './common.model';
import { ChainPlan, ProductionQueueStatus } from './production.model';

export type ShipClass = 'Corvette' | 'Destroyer' | 'Cruiser' | 'Freighter' | 'Carrier' | 'TroopTransport';

export interface ShipTypeDef {
  /** entspricht einem ProductType.id mit category=Ship */
  productTypeId: Id;
  class: ShipClass;
  /** Frachtkapazität in kg – Fracht darf weder diese noch `cargoVolumeM3` überschreiten (jeweils Summe über alle Schiffe der Flotte). */
  cargoMassKg: number;
  /** Frachtkapazität in m³ – siehe `cargoMassKg`. */
  cargoVolumeM3: number;
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

/**
 * `'ColonyOrbit'` = im Orbit einer konkreten Kolonie gelandet (`locationColonyId`
 * gesetzt) – dort ist sowohl der Planetare Handelsposten dieser Kolonie als
 * auch das Be-/Entladen (nur bei eigener Kolonie) nutzbar. `'System'` = im
 * System angekommen, aber bei keiner Kolonie gelandet – entspricht dem
 * Systemhandelsposten (`SellOrder.locationType: 'Station'`); spielmechanisch
 * unabhängig davon, ob `locationPlanetId` gesetzt ist (reine Anzeige).
 */
export type FleetLocationType = 'ColonyOrbit' | 'System';
export type FleetStatus = 'Stationed' | 'InTransit';

export interface FleetShipGroup {
  shipProductTypeId: Id;
  quantity: number;
}

export interface FleetCargoEntry {
  productTypeId: Id;
  quantity: number;
}

export interface Fleet {
  id: Id;
  ownerId: Id;
  name: string;
  locationType: FleetLocationType;
  locationColonyId: Id | null;
  /**
   * Nur informativ, wenn `locationType === 'System'` und die Flotte gerade
   * erst von einer Kolonie abgelegt hat ("Ablegen"): der Planet dieser
   * Kolonie, damit die UI "Im Orbit von X" statt pauschal "Systemhandelsposten"
   * anzeigen kann. Wird beim Ablegen gesetzt und bei Landung/Abflug/Ankunft
   * wieder auf `null` zurückgesetzt – die Simulation kennt keine Bewegung
   * zwischen Planeten innerhalb eines Systems, daher rein kosmetisch ohne
   * Einfluss auf Handel/Landeoptionen.
   */
  locationPlanetId: Id | null;
  /** Aktuelles bzw. (während `InTransit`) Ausgangssystem des GERADE LAUFENDEN Sprungs – siehe `destinationSystemId`. */
  systemId: Id;
  status: FleetStatus;
  ships: FleetShipGroup[];
  /** Geladene Fracht – siehe `loadCargo`/`unloadCargo`/`createSellOrderFromFleet`. Begrenzt durch die Summe aus `ShipTypeDef.cargoMassKg`/`cargoVolumeM3` aller Schiffe der Flotte. */
  cargo: FleetCargoEntry[];
  /** Ziel des GERADE LAUFENDEN, einzelnen Gateway-Sprungs – nur während `status === 'InTransit'` gesetzt. Bei einer mehrsprungigen Reise NICHT das Endziel, siehe `pendingHops`. */
  destinationSystemId: Id | null;
  departedAt: number | null;
  arrivesAt: number | null;
  /**
   * Bei einer mehrsprungigen Reise die noch folgenden Zielsysteme NACH
   * `destinationSystemId`, in Flugreihenfolge (letzter Eintrag = eigentliches
   * Endziel). Jeder Sprung ist ein eigenes, ereignisbasiertes Ankunfts-Tick
   * (siehe `processFleetArrivals`) – nach Ankunft am aktuellen
   * `destinationSystemId` wird automatisch der nächste Eintrag als neuer
   * Sprung gestartet, sofern die Flotte nicht per `cancelFleetMove`
   * abgebrochen wurde (dann bleibt sie am gerade erreichten System stehen,
   * statt weiterzufliegen). Das macht einen Flug JEDERZEIT unterwegs
   * abbrechbar, statt nur als ein einziger, nicht unterbrechbarer
   * Direktsprung zum Endziel – z. B. falls ein Gateway auf der Route später
   * gesperrt werden sollte. Leer, wenn der aktuelle Sprung der letzte ist.
   */
  pendingHops: Id[];
}

/** Sequentieller Werft-Auftrag, siehe `ProductionQueueEntry` und Konzeption/Umsetzungskonzept/10_...md. */
export interface ShipyardQueueEntry {
  id: Id;
  colonyId: Id;
  shipProductTypeId: Id;
  quantity: number;
  autoProduceMissing: boolean;
  requeueOnComplete: boolean;
  status: ProductionQueueStatus;
  stoppedReasonCode: number | null;
  plan: ChainPlan;
  startedAt: number | null;
  endsAt: number | null;
}
