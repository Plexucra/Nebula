import { Signal } from '@angular/core';
import {
  Building, BuildingType, Colony, Fleet, GameNotification, Gateway, GatewayWeightEntry, GroundForceGroup,
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
  /** Der aktuell EINGELOGGTE Kommandant in diesem Browser-Tab, `null` = abgemeldet (siehe Startseite). */
  readonly player: Signal<Player | null>;
  readonly wallet: Signal<Wallet | undefined>;

  // --- Konto / Anmeldung ---------------------------------------------------
  /**
   * Alle in der gemeinsamen Galaxie registrierten Kommandanten (für die
   * Login-Auswahl) – reaktiv, da `registerPlayer` sie zur Laufzeit erweitert.
   * Beim allerersten Start dieses Browsers wird automatisch genau einer
   * registriert (aber nicht eingeloggt), siehe `SimulatedGameApiService`.
   */
  players(): Signal<Player[]>;
  /** Meldet den gewählten Kommandanten an. Wechselt dabei weg von einem eventuell zuvor angemeldeten anderen. */
  login(playerId: Id): Promise<void>;
  /** Meldet ab, OHNE Daten zu löschen – ein erneutes `login` mit derselben Id setzt exakt dort fort. */
  logout(): Promise<void>;
  /**
   * Registriert einen neuen Kommandanten in der bestehenden, gemeinsamen
   * Galaxie und loggt ihn direkt ein. Bringt dabei ein komplett neues
   * Heimatsystem samt Heimatplanet in die Galaxie ein (siehe
   * `createAdditionalPlayerSeed`) – die Galaxie selbst (NPCs, andere
   * Kommandanten, Systemmarkt) bleibt unverändert bestehen.
   */
  registerPlayer(commanderName: string, homeworldName: string): Promise<void>;
  /** Kompletter Fabrik-Reset der GESAMTEN gemeinsamen Galaxie (alle Kommandanten!) – danach wieder Startseite mit genau einem neu registrierten Standard-Kommandanten. */
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
  /** Deckung (0..1,5, 1 = Bedarf exakt gedeckt) je Grundkonsumgut – Diagnosewert für die Statistik-Seite, kein Snapshot-Feld. */
  consumptionCoverage(colonyId: Id): Signal<Record<Id, number>>;
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

  // --- Produktion (sequentielle Warteschlange, siehe Konzeption/Umsetzungskonzept/
  //     10_Sequentielle_Produktionsauftraege_und_Ereignissystem.md) ------------
  warehouse(colonyId: Id): Signal<WarehouseEntry[]>;
  specializations(colonyId: Id): Signal<Specialization[]>;
  productionQueue(colonyId: Id): Signal<ProductionQueueEntry[]>;
  /**
   * Reiht einen sequentiellen Auftrag ein (pro Kolonie läuft immer nur
   * höchstens ein Auftrag gleichzeitig). `autoProduceMissing` löst die
   * komplette Produktkette einmalig vorausberechnet auf (§2 im Dokument
   * oben); ohne dieses Flag muss der DIREKTE Rezept-Bedarf bereits im Lager
   * liegen, sonst hält die Warteschlange an (`status: 'stopped'`, siehe
   * `resumeProduction`). `requeueOnComplete` reiht denselben Auftrag nach
   * Fertigstellung automatisch ans Ende der Warteschlange neu ein.
   */
  queueProduction(colonyId: Id, productTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void>;
  /** "Fortsetzen"-Button: prüft einen angehaltenen Auftrag erneut und startet ihn, falls jetzt ausführbar. */
  resumeProduction(colonyId: Id, entryId: Id): Promise<void>;
  /** Bei laufendem Auftrag anteilige Gutschrift nach verstrichener Zeit (abgerundet je Schritt), siehe Dokument §4. */
  cancelProduction(colonyId: Id, entryId: Id): Promise<void>;

  // --- Bevölkerung / Geld -----------------------------------------------------
  population(colonyId: Id): Signal<Population | undefined>;
  moneySupplyState(planetId: Id): Signal<PopulationMoneySupplyState | undefined>;
  populationWallet(colonyId: Id): Signal<Wallet | undefined>;
  transactions(): Signal<Transaction[]>;
  transfer(toPlayerName: string, amount: number): Promise<void>;

  // --- Flotten ------------------------------------------------------------
  fleets(): Signal<Fleet[]>;
  shipyardQueue(colonyId: Id): Signal<ShipyardQueueEntry[]>;
  queueShip(colonyId: Id, shipProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void>;
  resumeShipOrder(colonyId: Id, entryId: Id): Promise<void>;
  cancelShipOrder(colonyId: Id, entryId: Id): Promise<void>;

  // --- Bodentruppen -------------------------------------------------------
  groundForces(colonyId: Id): Signal<GroundForceGroup | undefined>;
  recruitmentQueue(colonyId: Id): Signal<RecruitmentQueueEntry[]>;
  queueRecruitment(colonyId: Id, unitProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void>;
  resumeRecruitment(colonyId: Id, entryId: Id): Promise<void>;
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
  /**
   * `autoRelist: true` ("Anbieten" im Lagerbestand) legt beim vollständigen
   * Verkauf im selben Vorgang automatisch eine neue Order mit identischer
   * Menge/Preis an, siehe Dokument §6.
   */
  createSellOrder(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist?: boolean): Promise<void>;
  cancelSellOrder(orderId: Id): Promise<void>;
  buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void>;

  // --- Benachrichtigungen (siehe Dokument §5) -------------------------------
  notifications(): Signal<GameNotification[]>;
  unreadNotificationCount(): Signal<number>;
  markNotificationRead(id: Id): Promise<void>;
  markAllNotificationsRead(): Promise<void>;

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
