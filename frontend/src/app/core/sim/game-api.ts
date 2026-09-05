import { Signal } from '@angular/core';
import {
  Battle, Blockade, BlockadeAnchor, Building, BuildingType, ChainPlan, Colony, DiplomaticRelation, DiplomaticStatus, Fleet, FleetSystemTarget, GameNotification, Gateway,
  GatewayWeightEntry, GroundForceGroup, GroundUnitTypeDef, Id, Npc, PeaceOffer, Planet, PlanetStats, Player, Population,
  PopulationMoneySupplyState, ProductType, ProductionQueueEntry, RecruitmentQueueEntry, SellOrder, ShipTypeDef,
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
  /** ALLE Kolonien in einem System, unabhängig vom Besitzer – z. B. um von der Galaxiekarte aus fremde Kolonien für den System Handel zu finden. */
  coloniesInSystem(systemId: Id): Signal<Colony[]>;
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
  /** Aktueller Elerium-Energiezelle-Bedarf des Energienetzes pro Spielstunde (0 ohne Energienetz) – unabhängig davon, ob er gerade gedeckt ist (siehe `powerCoverage`). */
  powerUpkeepPerHour(colonyId: Id): Signal<number>;

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
  /**
   * Reine Vorschau unter dem aktuellen Lagerbestand, OHNE einen Auftrag
   * anzulegen – für die "wird berechnet"-Kosten-/Zeit-Prognose im
   * Neuer-Auftrag-Formular und beim Aufklappen eines wartenden Eintrags.
   */
  previewProductionChain(colonyId: Id, productTypeId: Id, quantity: number): Promise<ChainPlan>;
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
  /** ALLE eigenen Flotten, unabhängig vom Standort (auch unterwegs oder in einem fremden System). */
  fleets(): Signal<Fleet[]>;
  /** ALLE Flotten der Galaxie, jeden Besitzers (auch anderer Kommandanten und NPCs) – für die Marker auf der Galaxiekarte. Sichtbarkeit fremder Flotten dort clientseitig über `hasVisitedSystem` einschränken. */
  allFleets(): Signal<Fleet[]>;
  shipyardQueue(colonyId: Id): Signal<ShipyardQueueEntry[]>;
  queueShip(colonyId: Id, shipProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void>;
  resumeShipOrder(colonyId: Id, entryId: Id): Promise<void>;
  cancelShipOrder(colonyId: Id, entryId: Id): Promise<void>;
  /**
   * Fertig gebaute Schiffe landen zunächst wie normale Waren im Lager der
   * bauenden Kolonie (siehe `warehouse`) – erst dieser Befehl überführt sie
   * in eine Flotte: entweder in `targetFleetId` (muss eine eigene,
   * stationierte Flotte AN DIESER KOLONIE sein) oder, wenn `null`, in eine
   * neu gegründete Flotte dort.
   */
  transferShipsToFleet(colonyId: Id, shipProductTypeId: Id, quantity: number, targetFleetId: Id | null): Promise<void>;
  /** Lädt Ware aus dem Lager der (eigenen) Kolonie, bei der die Flotte gerade gelandet ist, in ihre Fracht – begrenzt durch Lagerbestand UND verbleibende Massen-/Volumenkapazität der Flotte. */
  loadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void>;
  /** Entlädt Fracht zurück ins Lager der (eigenen) Kolonie, bei der die Flotte gerade gelandet ist. */
  unloadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void>;
  /**
   * Schickt eine stationierte, eigene Flotte über das (uneingeschränkt
   * offene, siehe `Gateway`) Netz los – Reisezeit richtet sich nach der
   * Anzahl Gateway-Sprünge zum Ziel, die intern hop-für-hop abgearbeitet
   * werden (siehe `Fleet.pendingHops`), nicht als ein einziger
   * ununterbrechbarer Sprung. Nach jedem einzelnen Sprung (ereignisbasiert,
   * siehe Konzeption/Umsetzungskonzept/10_...md) gilt das jeweils erreichte
   * System für diesen Kommandanten fortan als besucht (`hasVisitedSystem`).
   */
  moveFleet(fleetId: Id, destinationSystemId: Id): Promise<void>;
  /**
   * Bricht eine unterwegs befindliche Flotte ab: der gerade laufende
   * Gateway-Sprung wird noch zu Ende geflogen, alle weiteren geplanten
   * Sprünge entfallen – die Flotte bleibt am Ende dieses Sprungs stehen.
   * Jederzeit möglich, solange die Flotte unterwegs ist (auch mitten in
   * einer mehrsprungigen Reise) – z. B. falls ein Gateway auf der Route
   * gesperrt wird.
   */
  cancelFleetMove(fleetId: Id): Promise<void>;
  /** Reine Vorschau (keine Bewegung) für "Bewegen" auf der Galaxiekarte: Sprunganzahl + geschätzte Reisezeit (ms) zu einem Zielsystem – `null`, wenn kein Gateway-Pfad bekannt ist. Dieselbe Berechnung wie `moveFleet`, damit Vorschau und tatsächliche Ankunft nie auseinanderlaufen. */
  routePreview(fleetId: Id, destinationSystemId: Id): Signal<{ hops: number; ms: number } | null>;
  /**
   * Bewegt eine im System angekommene (nicht unterwegs befindliche) Flotte
   * INSTANT (keine Flugzeit) zwischen den drei Orten desselben Systems –
   * Systemhandelsposten, Orbit eines beliebigen Planeten (auch unbesiedelt)
   * oder angedockt an einer Kolonie (eigene wie fremde, für Handel am
   * dortigen Planetaren Handelsposten) – siehe `FleetSystemTarget`. Für die
   * Systemansicht (Planet-zu-Planet-Bewegung); Bewegung ZWISCHEN Systemen
   * läuft weiterhin über `moveFleet`.
   */
  moveFleetWithinSystem(fleetId: Id, target: FleetSystemTarget): Promise<void>;

  // --- Bodentruppen -------------------------------------------------------
  groundForces(colonyId: Id): Signal<GroundForceGroup | undefined>;
  recruitmentQueue(colonyId: Id): Signal<RecruitmentQueueEntry[]>;
  queueRecruitment(colonyId: Id, unitProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void>;
  resumeRecruitment(colonyId: Id, entryId: Id): Promise<void>;
  cancelRecruitment(colonyId: Id, entryId: Id): Promise<void>;

  // --- Gateway / Galaxie ----------------------------------------------------
  /** Gateways sind von Anfang an uneingeschränkt offen (keine Erforschung/Aktivierung nötig) – jeder Kommandant kann seine Flotten sofort frei durchs gesamte bekannte Netz bewegen. */
  gateway(systemId: Id): Signal<Gateway | undefined>;
  gatewayWeights(systemId: Id): Signal<GatewayWeightEntry[]>;
  /** ALLE Systeme der Galaxie – die Netzwerktopologie selbst ist öffentlich bekannt (offene Gateways), unabhängig davon, ob man dort schon war. */
  visibleSystems(): Signal<System[]>;
  system(id: Id): Signal<System | undefined>;
  /** Alle Gateway-Routen (dedupliziert) der gesamten bekannten Galaxie. */
  galaxyRoutes(): Signal<{ a: Id; b: Id }[]>;
  /**
   * true, sobald eine eigene Flotte dieses System schon einmal erreicht hat
   * (siehe `moveFleet`) – erst dann sind dessen Kolonien einsehbar
   * (`coloniesInSystem` auf der Galaxiekarte). Das Heimatsystem gilt von
   * Anfang an als besucht.
   */
  hasVisitedSystem(systemId: Id): Signal<boolean>;

  // --- Handel ---------------------------------------------------------------
  sellOrders(systemId: Id): Signal<SellOrder[]>;
  /**
   * Verkauf ab Kolonie-Lager (Planetarer Handelsposten der EIGENEN Kolonie).
   * `autoRelist: true` ("Anbieten" im Lagerbestand) legt beim vollständigen
   * Verkauf im selben Vorgang automatisch eine neue Order mit identischer
   * Menge/Preis an, siehe Dokument §6.
   */
  createSellOrder(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist?: boolean): Promise<void>;
  /**
   * Verkauf direkt aus der Fracht einer eigenen, gerade dort befindlichen
   * Flotte – gelandet bei einer Kolonie (auch fremder!) entsteht eine
   * `'Depot'`-Order an deren Planetarem Handelsposten, im System ohne
   * Landung eine `'Station'`-Order am Systemhandelsposten.
   */
  createSellOrderFromFleet(fleetId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist?: boolean): Promise<void>;
  cancelSellOrder(orderId: Id): Promise<void>;
  buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void>;

  // --- Diplomatie (Mechanik/06_..., vereinfacht, siehe SimulatedGameApiService) ---
  /** Status gegenüber einem beliebigen anderen Kommandanten – `'Peace'` ohne Beziehungseintrag (impliziter Grundzustand). */
  diplomaticStatus(otherPlayerId: Id): Signal<DiplomaticStatus>;
  /** Alle laufenden Kriege des angemeldeten Kommandanten. */
  activeWars(): Signal<DiplomaticRelation[]>;
  /** An den angemeldeten Kommandanten gerichtete, noch unbeantwortete Friedensangebote. */
  incomingPeaceOffers(): Signal<PeaceOffer[]>;
  /** Vom angemeldeten Kommandanten selbst gestellte, noch offene Friedensangebote. */
  outgoingPeaceOffers(): Signal<PeaceOffer[]>;
  /** Einseitig, tritt sofort in Kraft. */
  declareWar(otherPlayerId: Id): Promise<void>;
  /**
   * Einseitiges Angebot, wirksam erst nach Annahme durch den Empfänger
   * (`respondToPeaceOffer`). Gesperrt während eines laufenden Gefechts und
   * vor Ablauf einer Mindest-Kriegsdauer seit der Erklärung.
   */
  offerPeace(otherPlayerId: Id): Promise<void>;
  /** Nur der Empfänger darf antworten; Ablehnen löscht das Angebot ersatzlos, der Krieg läuft weiter. */
  respondToPeaceOffer(offerId: Id, accept: boolean): Promise<void>;

  // --- Raumgefechte (Mechanik/04_..., Kernformeln; vereinfacht ggü. 06_...) ---
  /** Alle laufenden Gefechte des angemeldeten Kommandanten (Angreifer oder Verteidiger). */
  activeBattles(): Signal<Battle[]>;
  battle(id: Id): Signal<Battle | undefined>;
  /** Beendete Gefechte, neueste zuerst – Kampfprotokoll. */
  battleHistory(): Signal<Battle[]>;
  /**
   * Öffentlich abrufbarer Kampfbericht über den unerratbaren `reportToken`
   * (siehe `Battle`) – UNABHÄNGIG vom angemeldeten Kommandanten, für den
   * teilbaren Link `/kampfbericht/:token`. Ab Kampfbeginn verfügbar, nicht
   * erst nach Kampfende.
   */
  battleByReportToken(token: string): Signal<Battle | undefined>;
  /** Eigene, im selben System stationierte, gegnerische Flotten MIT AKTIVER BLOCKADE (im Krieg, mit Schiffen) – Kandidaten für `engageBattle`. Ohne Blockade nicht angreifbar, siehe `Blockade`. */
  attackableFleetsInSystem(systemId: Id): Signal<Fleet[]>;
  /** Startet ein 1v1-Gefecht zwischen der eigenen Flotte und einer gegnerischen, blockierenden Flotte im selben System – nur im Krieg möglich. */
  engageBattle(attackerFleetId: Id, defenderFleetId: Id): Promise<void>;
  /** Zieht die eigene Flotte aus einem laufenden Gefecht zurück – die Gegenseite feuert dabei noch einen letzten Schlag. */
  retreatFromBattle(battleId: Id): Promise<void>;

  // --- Blockaden (Mechanik/06_..., stark vereinfacht, siehe `Blockade`) ------
  blockadesInSystem(systemId: Id): Signal<Blockade[]>;
  /** Errichtet mit der eigenen, an diesem Ort bereits stationierten Flotte eine Blockade – macht sie angreifbar. Höchstens eine Blockade je Anker und je Flotte. */
  formBlockade(fleetId: Id, anchor: BlockadeAnchor): Promise<void>;
  /** Hebt die eigene Blockade wieder auf – nicht möglich während eines laufenden Gefechts der blockierenden Flotte. */
  liftBlockade(blockadeId: Id): Promise<void>;

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
