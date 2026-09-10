import { Id } from './common.model';
import { ChainPlan, ProductionQueueStatus } from './production.model';

export type ShipClass = 'Corvette' | 'Destroyer' | 'Cruiser' | 'Freighter' | 'Carrier' | 'TroopTransport' | 'ColonyShip';

export interface ShipTypeDef {
  /** entspricht einem ProductType.id mit category=Ship */
  productTypeId: Id;
  class: ShipClass;
  /** Frachtkapazität in kg – Fracht darf weder diese noch `cargoVolumeM3` überschreiten (jeweils Summe über alle Schiffe der Flotte). */
  cargoMassKg: number;
  /** Frachtkapazität in m³ – siehe `cargoMassKg`. */
  cargoVolumeM3: number;
  /**
   * Slots, die dieses Schiff an Bord eines Trägers belegt (Umsetzungskonzept/
   * 06_...md). Ein Slot ist die Masse einer Korvette – das Slotsystem ist ein
   * Massensystem.
   */
  carrierSlotUsage: number;
  /** Slots, die dieses Schiff selbst AUFNIMMT – nur das Trägerschiff hat einen Wert > 0. */
  carrierSlotCapacity: number;
  /**
   * Soldaten, die dieses Schiff aufnimmt (Umsetzungskonzept/28_...md). Nur der
   * Mannschaftstransporter hat einen Wert > 0 und nimmt AUSSCHLIESSLICH
   * Soldaten auf. Drohnen sind Maschinen und reisen als gewöhnliche Fracht im
   * Frachter (`cargoMassKg`/`cargoVolumeM3`) – eine Landung braucht deshalb
   * beide Schiffstypen.
   */
  troopCapacity: number;
  /**
   * Klasse, die von dieser Klasse gekontert wird (×2 Schaden), siehe
   * Mechanik/03_..., §2 und Mechanik/04_..., §4. Es gibt bewusst KEINE
   * eigenen Angriffs-/Hüllenwerte: Schaden und Haltbarkeit im Kampf
   * leiten sich ausschließlich aus dem Produktionsaufwand
   * (ProductType.workHoursPerUnit × baseProductionHours) ab,
   * siehe Mechanik/04_..., §2-3.
   */
  countersClass: ShipClass | null;
  /**
   * Eleriumkapseln, die dieses Schiff für EINEN Gateway-Sprung verbraucht.
   * Kommt fertig aus dem Katalog: der Server leitet den Wert aus der
   * Schiffsmasse ab (Umsetzungskonzept/34_...md, F7) – im Client steht
   * deshalb keine zweite Formel.
   */
  jumpFuelPerHop: number;
  /** Fassungsvermögen des Treibstofftanks dieses Schiffs in Kapseln (= `jumpFuelPerHop` × Reichweite in Sprüngen). */
  fuelTankCapacity: number;
}

/**
 * Die drei Orte, an denen eine im System angekommene (`status: 'Stationed'`)
 * Flotte innerhalb DESSELBEN Systems stehen kann (Konzeption/Mechanik/06_...,
 * räumliche Hierarchie System → Orbit → Kolonie) – Wechsel zwischen ihnen ist
 * instant (keine Flugzeit, siehe `moveFleetWithinSystem`), nur Gateway-Sprünge
 * ZWISCHEN Systemen (siehe `Fleet.pendingHops`) kosten Zeit:
 * - `'ColonyOrbit'` = angedockt an einer konkreten Kolonie (`locationColonyId`
 *   gesetzt) – dort sind sowohl deren Planetarer Handelsposten als auch
 *   Be-/Entladen (nur bei eigener Kolonie) nutzbar.
 * - `'PlanetOrbit'` = im Orbit eines Planeten OHNE Andocken an eine dortige
 *   Kolonie – auch bei unbesiedelten Planeten möglich (`locationPlanetId`
 *   gesetzt, `locationColonyId` NICHT). Vorstufe für eine spätere
 *   Landungs-/Invasionsmechanik (noch nicht umgesetzt), aktuell ohne
 *   Handelszugriff.
 * - `'System'` = am Systemhandelsposten, keinem Planeten zugeordnet
 *   (`locationPlanetId` NICHT gesetzt).
 */
export type FleetLocationType = 'ColonyOrbit' | 'PlanetOrbit' | 'System';
export type FleetStatus = 'Stationed' | 'InTransit';

/** Bewegungsziel für `moveFleetWithinSystem` – siehe `FleetLocationType`. */
export type FleetSystemTarget =
  | { kind: 'System' }
  | { kind: 'PlanetOrbit'; planetId: Id }
  | { kind: 'ColonyOrbit'; colonyId: Id };

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
  /** Nur bei `locationType === 'ColonyOrbit'` gesetzt. */
  locationColonyId: Id | null;
  /** Bei `'ColonyOrbit'` und `'PlanetOrbit'` der umkreiste Planet, bei `'System'` `null` – siehe `FleetLocationType`. */
  locationPlanetId: Id | null;
  /** Aktuelles bzw. (während `InTransit`) Ausgangssystem des GERADE LAUFENDEN Sprungs – siehe `destinationSystemId`. */
  systemId: Id;
  status: FleetStatus;
  ships: FleetShipGroup[];
  /** Geladene Fracht – siehe `loadCargo`/`unloadCargo`/`createSellOrderFromFleet`. Begrenzt durch die Summe aus `ShipTypeDef.cargoMassKg`/`cargoVolumeM3` aller Schiffe der Flotte. */
  cargo: FleetCargoEntry[];
  /**
   * Eleriumkapseln im separaten TANK (Umsetzungskonzept/26_...md). Keine Fracht:
   * belegt keine Lade­kapazität, kann aber auch nicht ausgeladen werden – sonst
   * wäre der Tank ein zweiter, weit größerer Frachtraum. Fassungsvermögen:
   * `ShipTypeDef.fuelTankCapacity` je Schiff der Flotte.
   */
  fuelCapsules: number;
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

/**
 * Vorschau eines Trägersprungs ohne Gateway (Umsetzungskonzept/06_...md).
 * Kommt fertig gerechnet vom Backend, damit Vorschau und tatsächlicher Sprung
 * nie auseinanderlaufen.
 */
export interface CarrierJumpPreview {
  /** false = die Träger fassen die übrigen Schiffe nicht; `reason` nennt den Grund. */
  possible: boolean;
  reason: string | null;
  /** Slots, die die mitfliegenden Schiffe belegen. */
  slotsNeeded: number;
  /** Slots, die die Trägerschiffe bereitstellen. */
  slotsAvailable: number;
  /** Strecke, gemessen in durchschnittlichen Gateway-Sprüngen. */
  referenceHops: number;
  /** Reisedauer in Realzeit-Millisekunden. */
  ms: number;
  fuelNeeded: number;
  fuelInTank: number;
}

/**
 * Frachtkapazität, Auslastung und maximal ladbare Stückzahl einer Flotte –
 * kommt fertig berechnet vom Backend (`fleetCargoCapacity`), damit die
 * Kapazitätsregel aus `FleetCommands.loadCargo` nicht ein zweites Mal im
 * Client nachgebildet wird (Umsetzungskonzept/15_...md, Auftrag 3).
 */
export interface FleetCargoCapacity {
  capacityMassKg: number;
  capacityVolumeM3: number;
  usedMassKg: number;
  usedVolumeM3: number;
  /** Maximal ladbare Stückzahl des angefragten Produkts. */
  maxLoadableQuantity: number;
}

/**
 * Truppenkapazität einer Flotte – Gegenstück zu `FleetCargoCapacity` für
 * Soldaten (Umsetzungskonzept/28_...md). Kommt ebenfalls fertig vom Backend
 * (`fleetTroopCapacity`), damit die Regel aus
 * `TroopTransportCommands.embarkSoldiers` nicht im Client nachgebaut wird.
 */
export interface FleetTroopCapacity {
  /** Plätze insgesamt – nur der Mannschaftstransporter steuert welche bei. */
  capacitySoldiers: number;
  soldiersAboard: number;
  /** Was jetzt zusteigen kann: freier Platz UND Soldaten in der Garnison vor Ort. */
  maxEmbarkableQuantity: number;
}
