import { Signal } from '@angular/core';
import {
  AutoProductionOrder, Building, BuildingType, Colony, Fleet, Gateway, GatewayWeightEntry, GroundForceGroup,
  GroundUnitTypeDef, Id, Npc, Planet, PlanetStats, Player, Population, PopulationMoneySupplyState,
  ProductType, ProductionQueueEntry, RecruitmentQueueEntry, SellOrder, ShipTypeDef,
  ShipyardQueueEntry, Specialization, System, Transaction, UniverseStatSnapshot, Wallet,
  WarehouseEntry,
} from '../models';

/**
 * Vertrag des Backends aus Sicht des Clients.
 *
 * Diese Schnittstelle ist die Kapselgrenze zwischen UI und Simulation: Jede
 * Komponente hängt ausschließlich von `GameApi` (über den Injection-Token
 * `GAME_API`) ab, nie von der konkreten Implementierung. Aktuell erfüllt
 * `SimulatedGameApiService` diesen Vertrag rein im Browser-Speicher; ein
 * späteres echtes Backend müsste nur eine `HttpGameApiService`-Klasse
 * bereitstellen, die dieselbe Schnittstelle über REST/WebSocket erfüllt
 * (vgl. Umsetzungskonzept/00_..., §5 – Order/Job-Rückgabeobjekte,
 * Push-Topics). Kein anderer Teil der App müsste sich ändern.
 *
 * Query-Methoden liefern reaktive `Signal`s (analog zu einem serverseitig
 * gepushten/gepollten Zustand). Befehle (mutierende Aktionen) sind
 * `Promise`-basiert, wie ein echter Netzwerkaufruf.
 */
export interface GameApi {
  readonly player: Signal<Player | null>;
  readonly wallet: Signal<Wallet | undefined>;

  /** Legt Kommandant + Heimatkolonie neu an (nur wenn noch kein Player existiert). */
  startNewGame(commanderName: string, homeworldName: string): Promise<void>;
  /** Setzt die gesamte lokale Simulation zurück (löscht den Autosave). */
  resetGame(): Promise<void>;

  // --- Katalog (statisch, synchron) --------------------------------------
  productTypes(): ProductType[];
  buildingTypes(): BuildingType[];
  shipTypes(): ShipTypeDef[];
  groundUnitTypes(): GroundUnitTypeDef[];

  // --- Planeten / Kolonien ------------------------------------------------
  colonies(): Signal<Colony[]>;
  colony(id: Id): Signal<Colony | undefined>;
  colonyStats(id: Id): Signal<PlanetStats | undefined>;
  planet(id: Id): Signal<Planet | undefined>;
  planetsInSystem(systemId: Id): Signal<Planet[]>;
  colonizePlanet(planetId: Id): Promise<Colony>;

  // --- Bebauung -------------------------------------------------------------
  buildings(colonyId: Id): Signal<Building[]>;
  queueBuilding(colonyId: Id, buildingTypeId: Id): Promise<void>;
  cancelBuildingOrder(colonyId: Id, buildingId: Id): Promise<void>;
  demolishBuilding(colonyId: Id, buildingId: Id): Promise<void>;
  activateDefense(colonyId: Id, buildingId: Id): Promise<void>;
  deactivateDefense(colonyId: Id, buildingId: Id): Promise<void>;
  overbuildFactor(planetId: Id): Signal<number>;
  /** Wohnkapazität aus Infrastructure-Gebäuden – Energienetz-Anteil bereits um `powerCoverage` gemindert. */
  housingCapacity(colonyId: Id): Signal<number>;
  /** 0..1: wie viel des Elerium-Bedarfs des Energienetzes zuletzt gedeckt war (1 = voll versorgt, kein Energienetz = 1). */
  powerCoverage(colonyId: Id): Signal<number>;

  // --- Produktion -----------------------------------------------------------
  warehouse(colonyId: Id): Signal<WarehouseEntry[]>;
  specializations(colonyId: Id): Signal<Specialization[]>;
  productionQueue(colonyId: Id): Signal<ProductionQueueEntry[]>;
  queueProduction(colonyId: Id, productTypeId: Id, quantity: number): Promise<void>;
  cancelProduction(colonyId: Id, entryId: Id): Promise<void>;
  /** Dauerproduktion je Kolonie – siehe `setAutoProductionTarget`. */
  autoProductionOrders(colonyId: Id): Signal<AutoProductionOrder[]>;
  /** Dieselben Daten, gefiltert nach Produkt statt Kolonie – für die Kolonie-Auswahl beim Starten. */
  autoProductionOrdersForProduct(productTypeId: Id): Signal<AutoProductionOrder[]>;
  /**
   * Legt einen Dauerauftrag an oder ändert dessen Ziel-Lagerbestand, falls bereits vorhanden.
   * `localPrice` > 0 pflegt automatisch eine einzelne Verkaufsorder am Systemmarkt (0 = keine).
   */
  setAutoProductionTarget(colonyId: Id, productTypeId: Id, maxStock: number, localPrice?: number): Promise<void>;
  cancelAutoProductionTarget(colonyId: Id, productTypeId: Id): Promise<void>;

  // --- Bevölkerung / Geld -----------------------------------------------------
  population(colonyId: Id): Signal<Population | undefined>;
  moneySupplyState(planetId: Id): Signal<PopulationMoneySupplyState | undefined>;
  populationWallet(colonyId: Id): Signal<Wallet | undefined>;
  transactions(): Signal<Transaction[]>;
  transfer(toPlayerName: string, amount: number): Promise<void>;

  // --- Flotten ------------------------------------------------------------
  fleets(): Signal<Fleet[]>;
  shipyardQueue(colonyId: Id): Signal<ShipyardQueueEntry[]>;
  queueShip(colonyId: Id, shipProductTypeId: Id, quantity: number): Promise<void>;
  cancelShipOrder(colonyId: Id, entryId: Id): Promise<void>;

  // --- Bodentruppen -------------------------------------------------------
  groundForces(colonyId: Id): Signal<GroundForceGroup | undefined>;
  recruitmentQueue(colonyId: Id): Signal<RecruitmentQueueEntry[]>;
  queueRecruitment(colonyId: Id, unitProductTypeId: Id, count: number): Promise<void>;
  cancelRecruitment(colonyId: Id, entryId: Id): Promise<void>;

  // --- Gateway / Galaxie ----------------------------------------------------
  gateway(systemId: Id): Signal<Gateway | undefined>;
  activateGateway(systemId: Id): Promise<void>;
  gatewayWeights(systemId: Id): Signal<GatewayWeightEntry[]>;
  visibleSystems(): Signal<System[]>;
  system(id: Id): Signal<System | undefined>;
  /** Bekannte Gateway-Routen (dedupliziert) zwischen sichtbaren Systemen. */
  galaxyRoutes(): Signal<{ a: Id; b: Id }[]>;

  // --- Handel ---------------------------------------------------------------
  sellOrders(systemId: Id): Signal<SellOrder[]>;
  createSellOrder(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number): Promise<void>;
  cancelSellOrder(orderId: Id): Promise<void>;
  buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void>;

  // --- NPCs / Universums-Statistik ------------------------------------------
  /** Alle NPC-"Spieler" der Galaxie (nicht-kriegerisch, siehe Npc-Modell). */
  npcs(): Signal<Npc[]>;
  /** Kolonie eines NPCs (jeder NPC besitzt aktuell genau eine). */
  npcColony(npcId: Id): Signal<Colony | undefined>;
  /** Wallet eines beliebigen Besitzers (Spieler oder NPC) – für die NPC-Übersicht. */
  ownerWallet(ownerId: Id): Signal<Wallet | undefined>;
  /** Zeitreihe aggregierter Stabilitätskennzahlen über die gesamte Galaxie. */
  universeStats(): Signal<UniverseStatSnapshot[]>;
}
