import { EnergyStorage } from '../models/building.model';
import { Signal } from '@angular/core';
import {
  Battle, Blockade, BlockadeAnchor, BuildSlots, Building, BuildingType, ChainPlan, Colonization, CarrierJumpPreview, Colony, ColonySpeedBreakdown, DiplomaticRelation, DiplomaticStatus, Fleet, FleetCargoCapacity, FleetSystemTarget, FleetTroopCapacity, GameNotification, Gateway,
  GatewayWeightEntry, GroundBattle, GroundForceGroup, GroundUnitTypeDef, HubDepotEntry, HubOrder, Id, Message, PeaceOffer, Planet, PlanetStats, Player, PlayerRole, Population,
  PopulationMoneySupplyState, PopulationTrend, ProductType, ProductionQueueEntry, RecruitmentQueueEntry, SellOrder, ShipTypeDef,
  ShipyardQueueEntry, Specialization, SupplyInventoryEntry, System, Transaction, Treaty, TreatyOffer, TreatyType, UniverseStatSnapshot, Wallet, GameVictory,
  WarehouseEntry,
} from '../models';

/**
 * Vertrag des Backends aus Sicht des Clients.
 *
 * Diese Schnittstelle ist die Kapselgrenze zwischen UI und Backend: Jede
 * Komponente hängt ausschließlich von `GameApi` (über den Injection-Token
 * `GAME_API`) ab, nie von der konkreten Implementierung. Erfüllt wird der
 * Vertrag ausschließlich von `WebSocketGameApiService` gegen das
 * Quarkus-Backend – die früher parallel existierende Browser-Simulation
 * wurde gelöscht, weil zwei Regelimplementierungen zwangsläufig
 * auseinanderlaufen (Umsetzungskonzept/15_...md, Auftrag 3). Alle
 * Spielregeln leben damit ausschließlich im Backend.
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
   * Der ERSTE `registerPlayer`-Aufruf überhaupt erzeugt serverseitig die
   * komplette Galaxie (siehe `GameSocket.handleRegisterPlayer`).
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
  registerPlayer(commanderName: string, homeworldName: string, role: PlayerRole, campId?: string): Promise<void>;
  /** Kompletter Fabrik-Reset der GESAMTEN gemeinsamen Galaxie (alle Kommandanten!) – danach leere Galaxie, der nächste `registerPlayer`-Aufruf erzeugt sie neu. */
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
  /**
   * Fertig berechnete Aufschlüsselung ALLER Produktionstempo-Faktoren dieser
   * Kolonie (Bevölkerung/Workforce, Gebäudestufe, Spezialisierung, Fördergüte,
   * Blackout, Ausbaukosten-Vorschau) für die Transparenz-Panels – kommt
   * vollständig aus dem Backend, damit die Formeln nur dort existieren.
   */
  colonySpeedBreakdown(colonyId: Id): Signal<ColonySpeedBreakdown | null>;
  planet(id: Id): Signal<Planet | undefined>;
  planetsInSystem(systemId: Id): Signal<Planet[]>;
  /**
   * Löst die Landung aus: verbraucht ein Kolonisationsschiff aus einer eigenen
   * Flotte im Orbit dieses Planeten und startet die Gründung. Die Kolonie
   * entsteht erst einen Spieltag später (Umsetzungskonzept/24_...md), deshalb
   * liefert der Aufruf den laufenden Vorgang statt einer fertigen Kolonie.
   */
  colonizePlanet(planetId: Id): Promise<Colonization>;
  /** Eigene laufende Koloniegründungen – Fortschrittsanzeige. */
  colonizations(): Signal<Colonization[]>;
  /**
   * Versorgungsinventar der Kolonie: Lagerbestand samt Verbrauch je Spielstunde
   * und Reichweite (Umsetzungskonzept/25_...md). Bestände sind ganze Stückzahlen.
   */
  supplyInventory(colonyId: Id): Signal<SupplyInventoryEntry[]>;

  // --- Bebauung -------------------------------------------------------------
  buildings(colonyId: Id): Signal<Building[]>;
  queueBuilding(colonyId: Id, buildingTypeId: Id): Promise<void>;
  /**
   * Reiht die für den nächsten Ausbau FEHLENDEN Baustoffe als EINEN
   * Bündelauftrag ein. Nötig, weil ein Ausbau mehrere Baustoffe gleichzeitig
   * braucht, von denen einer Vorprodukt eines anderen sein kann – einzeln
   * eingereiht nehmen sie sich gegenseitig den Lagerbestand weg. Liefert die
   * eingereihten Mengen je Produkt zurück.
   */
  queueMissingBuildingMaterials(colonyId: Id, buildingTypeId: Id): Promise<Record<Id, number>>;
  cancelBuildingOrder(colonyId: Id, buildingId: Id): Promise<void>;
  demolishBuilding(colonyId: Id, buildingId: Id): Promise<void>;
  activateDefense(colonyId: Id, buildingId: Id): Promise<void>;
  deactivateDefense(colonyId: Id, buildingId: Id): Promise<void>;
  /** Bebauungsplätze der Kolonie (Umsetzungskonzept/17_...md) – DIE strategische Größe der Bebauung, vom Backend berechnet. */
  buildSlots(colonyId: Id): Signal<BuildSlots | null>;
  /** Wohnkapazität aus Infrastructure-Gebäuden – Energienetz-Anteil bereits um `powerCoverage` gemindert. */
  housingCapacity(colonyId: Id): Signal<number>;
  /** 0..1: wie viel des Elerium-Bedarfs des Energienetzes zuletzt gedeckt war (1 = voll versorgt, kein Energienetz = 1). */
  powerCoverage(colonyId: Id): Signal<number>;
  /**
   * true = das Energienetz dieser Kolonie ist unterversorgt ("Blackout").
   * Die Schwelle ist eine Spielregel (`Formulas.BLACKOUT_THRESHOLD`) und wird
   * deshalb vom Backend entschieden, statt im Client nachgebildet zu werden.
   */
  isBlackout(colonyId: Id): Signal<boolean>;
  /** Aktueller Elerium-Energiezelle-Bedarf des Energienetzes pro Spielstunde (0 ohne Energienetz) – unabhängig davon, ob er gerade gedeckt ist (siehe `powerCoverage`). */
  powerUpkeepPerHour(colonyId: Id): Signal<number>;
  /** Energiespeicher der Kolonie (Umsetzungskonzept/32_...md). */
  energyStorage(colonyId: Id): Signal<EnergyStorage | null>;
  /** Vorhaltemenge setzen; `null` = automatisch (folgt der Infrastrukturstufe). Überschuss geht ins Lager. */
  setEnergyReserve(colonyId: Id, reserveTarget: number | null): Promise<void>;

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
  /**
   * Verschiebt einen wartenden Auftrag um eine Position (`-1` = nach vorn,
   * `+1` = nach hinten). Pro Kolonie läuft nur EIN Auftrag – die Reihenfolge
   * entscheidet also, was zuerst fertig wird, und war bislang gar nicht
   * änderbar.
   */
  moveProductionEntry(colonyId: Id, entryId: Id, direction: -1 | 1): Promise<void>;

  // --- Bevölkerung / Geld -----------------------------------------------------
  population(colonyId: Id): Signal<Population | undefined>;
  /**
   * Bevölkerungsverlauf der letzten ~20 Minuten samt Einordnung der
   * Wachstumsphase und dessen, was das Wachstum gerade begrenzt – vom Backend
   * berechnet (siehe Umsetzungskonzept/18_...md).
   */
  populationTrend(colonyId: Id): Signal<PopulationTrend | null>;
  moneySupplyState(planetId: Id): Signal<PopulationMoneySupplyState | undefined>;
  populationWallet(colonyId: Id): Signal<Wallet | undefined>;
  transactions(): Signal<Transaction[]>;
  /**
   * Saldo des Kommandanten-Kontos je SPIELSTUNDE (Einnahmen minus Löhne,
   * Gebäude- und Flottenunterhalt). Die Zahl, ohne die ein schleichender
   * Bankrott unsichtbar bleibt – steht in der Kopfzeile neben dem Guthaben.
   */
  treasuryFlowPerHour(): Signal<number>;
  transfer(toPlayerName: string, amount: number): Promise<void>;

  // --- Flotten ------------------------------------------------------------
  /** ALLE eigenen Flotten, unabhängig vom Standort (auch unterwegs oder in einem fremden System). */
  fleets(): Signal<Fleet[]>;
  /** ALLE Flotten der Galaxie, jeden Besitzers (auch anderer Kommandanten und NPCs) – für die Marker auf der Galaxiekarte. Sichtbarkeit fremder Flotten dort clientseitig über `hasVisitedSystem` einschränken. */
  /**
   * Alle Flotten (aller Kommandanten) in EINEM System. Die frühere Abfrage
   * aller Flotten der Galaxie je Sekunde und Seite ist entfallen – bei
   * tausend Systemen wäre das je Betrachter ein Vielfaches der nötigen Daten.
   */
  fleetsInSystem(systemId: Id): Signal<Fleet[]>;
  /** Eine Flotte beliebigen Eigentümers (für Kampfberichte und Angriffsbestätigungen). */
  fleet(id: Id): Signal<Fleet | undefined>;
  /** Schiffe je System für die Galaxiekarte – eigene überall, fremde nur in besuchten Systemen; vom Server gezählt. */
  fleetPresence(): Signal<Record<Id, { myShips: number; enemyShips: number }>>;
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
  /**
   * Betankt eine bei einer eigenen Kolonie gelandete Flotte aus deren Lager
   * (Umsetzungskonzept/26_...md). Bewusst ein eigener Befehl, damit eindeutig
   * ist, welche Kapseln an Bord und damit für den Verbrauch freigegeben sind.
   * Ein Gegenstück zum Ausladen gibt es absichtlich nicht.
   */
  refuelFleet(fleetId: Id, quantity: number): Promise<void>;
  /** Abtanken: gibt GANZE Kapseln zurück ins Kolonielager bzw. Stationsdepot – die angebrochene bleibt an Bord. */
  drainFleetFuel(fleetId: Id, quantity: number): Promise<void>;
  /** Treibstoff zwischen zwei eigenen Flotten im selben System – der Rettungsweg für gestrandete Flotten. */
  transferFuelBetweenFleets(fromFleetId: Id, toFleetId: Id, quantity: number): Promise<void>;
  loadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void>;
  /** Entlädt Fracht zurück ins Lager der (eigenen) Kolonie, bei der die Flotte gerade gelandet ist. */
  unloadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void>;
  /** Lädt Ware aus dem unbegrenzten Stations-Depot des Kommandanten in die Fracht einer dort stationierten Flotte – Gegenstück zu `loadCargo`, nur an einer Handelsgilde-Station statt einer Kolonie. */
  loadCargoFromHubDepot(fleetId: Id, productTypeId: Id, quantity: number): Promise<void>;
  /** Entlädt Fracht der Flotte in das Stations-Depot des Kommandanten – Gegenstück zu `unloadCargo`. */
  unloadCargoToHubDepot(fleetId: Id, productTypeId: Id, quantity: number): Promise<void>;

  // --- Bodentruppen verladen (Umsetzungskonzept/28_...md) --------------------
  // Soldaten fahren im Mannschaftstransporter, Drohnen als gewöhnliche Fracht
  // im Frachter. `loadCargo` weist `p_soldier` deshalb ausdrücklich ab.
  /** Verlädt Soldaten aus der Garnison der Kolonie, bei der die Flotte liegt, an Bord – begrenzt durch `ShipTypeDef.troopCapacity`. */
  embarkSoldiers(fleetId: Id, quantity: number): Promise<void>;
  /** Schifft Soldaten in die Garnison der Kolonie aus, bei der die Flotte liegt – Gegenstück zu `embarkSoldiers`. */
  disembarkSoldiers(fleetId: Id, quantity: number): Promise<void>;
  /** Plätze, Belegung und einschiffbare Menge – vom Backend berechnet, wie `fleetCargoCapacity`. */
  fleetTroopCapacity(fleetId: Id): Signal<FleetTroopCapacity | undefined>;
  /** Verlegt Drohnen aus der Garnison ins Warenlager – erst von dort lassen sie sich als Fracht verladen. */
  storeDrones(colonyId: Id, unitProductTypeId: Id, quantity: number): Promise<void>;
  /** Stellt eingelagerte Drohnen wieder in die Garnison – Gegenstück zu `storeDrones`. */
  deployDrones(colonyId: Id, unitProductTypeId: Id, quantity: number): Promise<void>;
  /**
   * Landung (Umsetzungskonzept/04_...md): Soldaten an Bord und Drohnenfracht einer eigenen
   * Flotte im Orbit dieses Planeten kommen auf die Oberfläche – als neuer oder erweiterter
   * `GroundForceGroup` mit gesetztem `planetId`. Feuert vorher die Landungsabwehr jeder
   * feindlichen, kriegführenden Kolonie mit aktiver Verteidigung auf diesem Planeten
   * (Mechanik/05_...md §8); was das kostet, fehlt entsprechend in der Rückgabe.
   */
  land(fleetId: Id, targetPlanetId: Id): Promise<GroundForceGroup>;
  /**
   * Schickt eine stationierte, eigene Flotte über das (uneingeschränkt
   * offene, siehe `Gateway`) Netz los – Reisezeit richtet sich nach der
   * Anzahl Gateway-Sprünge zum Ziel, die intern hop-für-hop abgearbeitet
   * werden (siehe `Fleet.pendingHops`), nicht als ein einziger
   * ununterbrechbarer Sprung. Nach jedem einzelnen Sprung (ereignisbasiert,
   * siehe Konzeption/Umsetzungskonzept/10_...md) gilt das jeweils erreichte
   * System für diesen Kommandanten fortan als besucht (`hasVisitedSystem`).
   */
  moveFleet(fleetId: Id, destinationSystemId: Id, viaCarrier?: boolean): Promise<void>;
  /**
   * Bricht eine unterwegs befindliche Flotte ab: der gerade laufende
   * Gateway-Sprung wird noch zu Ende geflogen, alle weiteren geplanten
   * Sprünge entfallen – die Flotte bleibt am Ende dieses Sprungs stehen.
   * Jederzeit möglich, solange die Flotte unterwegs ist (auch mitten in
   * einer mehrsprungigen Reise) – z. B. falls ein Gateway auf der Route
   * gesperrt wird.
   */
  cancelFleetMove(fleetId: Id): Promise<void>;
  /** Benennt eine Flotte um – automatisch vergebene Namen wie "Flotte Alpha Prime 5" sind ab wenigen Flotten nicht mehr unterscheidbar. */
  renameFleet(fleetId: Id, name: string): Promise<void>;
  /** Reine Vorschau (keine Bewegung) für "Bewegen" auf der Galaxiekarte: Sprunganzahl + geschätzte Reisezeit (ms) zu einem Zielsystem – `null`, wenn kein Gateway-Pfad bekannt ist. Dieselbe Berechnung wie `moveFleet`, damit Vorschau und tatsächliche Ankunft nie auseinanderlaufen. */
  routePreview(fleetId: Id, destinationSystemId: Id): Signal<{ hops: number; ms: number } | null>;
  /**
   * Routenvorschau zu ALLEN erreichbaren Systemen in einer Abfrage (Schlüssel =
   * System-Id) – für Ziellisten. Je Ziel einzeln `routePreview` zu rufen hieße
   * bei 200 Systemen 200 Abfragen je Sekunde.
   */
  routePreviews(fleetId: Id): Signal<Record<Id, { hops: number; ms: number }>>;
  /**
   * Vorschau des Trägersprungs OHNE Gateway (Umsetzungskonzept/06_...md):
   * Slot-Bilanz, Dauer und Treibstoffbedarf. `possible: false` heißt, dass die
   * Träger die übrigen Schiffe nicht fassen – `reason` nennt den Grund und ist
   * derselbe Text, mit dem `moveFleet(..., viaCarrier)` den Sprung abbricht.
   */
  carrierJumpPreview(fleetId: Id, destinationSystemId: Id): Signal<CarrierJumpPreview | null>;
  /**
   * Frachtkapazität/Auslastung einer Flotte und die maximal ladbare Stückzahl
   * des angegebenen Produkts – vom Backend berechnet (dieselbe Regel, die
   * `loadCargo` durchsetzt), statt sie im Client nachzubilden.
   */
  fleetCargoCapacity(fleetId: Id, productTypeId: Id | null): Signal<FleetCargoCapacity | null>;
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
  /**
   * Legt `sourceFleetId` in `targetFleetId` zusammen (Umsetzungskonzept/33_...md):
   * Schiffe, Fracht, Treibstoff und eingeschiffte Truppen wandern hinüber, die
   * Quellflotte verschwindet. Beide müssen am selben Ort stationiert sein.
   */
  mergeFleets(targetFleetId: Id, sourceFleetId: Id): Promise<void>;
  /**
   * Spaltet eine neue Flotte ab: `ships`/`cargo` als Produkt-Id → Stückzahl,
   * dazu die mitfahrenden Soldaten und der Name der neuen Flotte. Der Server
   * prüft, dass BEIDE Seiten ihre Fracht und ihre Soldaten tragen können.
   */
  splitFleet(fleetId: Id, ships: Record<Id, number>, cargo: Record<Id, number>, soldiers: number, name: string): Promise<void>;

  // --- Bodentruppen -------------------------------------------------------
  groundForces(colonyId: Id): Signal<GroundForceGroup | undefined>;
  /** Eigene, auf diesem Planeten gelandete Verbände (`planetId` gesetzt) – siehe `land`. */
  groundForcesAtPlanet(planetId: Id): Signal<GroundForceGroup[]>;
  /** ALLE eigenen gelandeten Verbände, planetenübergreifend – für die Bodentruppen-Übersicht. */
  landedGroundForces(): Signal<GroundForceGroup[]>;
  /**
   * Verlegt einen gelandeten Verband in eine EIGENE Kolonie auf demselben Planeten – genau
   * ein Kampftick Dauer, unabhängig von der Distanz (Mechanik/05_...md §7). Der Angriff auf
   * eine fremde Kolonie läuft stattdessen über `engageGroundBattle`.
   */
  moveGroundForces(groupId: Id, targetColonyId: Id): Promise<void>;
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
  /**
   * true, sobald ein Kommandant die Rohstoffkonzentration der Planeten dieses Systems kennt –
   * getrennt von `hasVisitedSystem` ("schon mal dort gewesen"): erst das explizite Erforschen
   * (`exploreSystem`) oder eine eigene Kolonisierung dort deckt sie auf. Bis dahin liefert
   * `planet`/`planetsInSystem` für dieses System eine leere `resourceConcentration`.
   */
  hasExploredSystem(systemId: Id): Signal<boolean>;
  /**
   * Erforscht das System, in dem `fleetId` gerade `Stationed` ist – deckt die
   * Rohstoffkonzentration aller dortigen Planeten für den eigenen Kommandanten auf
   * (`hasExploredSystem`). Funktioniert mit jeder eigenen Flotte, unabhängig vom Schiffstyp.
   */
  exploreSystem(fleetId: Id): Promise<void>;

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
  /** Ändert den Preis einer eigenen, offenen Verkaufsorder – vorher ging das nur über Zurückziehen und Neuanlegen. */
  updateSellOrderPrice(orderId: Id, pricePerUnit: number): Promise<void>;
  buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void>;

  // --- Handelsgilde-Station: Depot & Orderbuch (Umsetzungskonzept/22_...md) ---
  /** Das unbegrenzte Depot des angemeldeten Kommandanten an EINER Handelsgilde-Station. */
  hubDepot(systemId: Id): Signal<HubDepotEntry[]>;
  /** Das gesamte Orderbuch (Kauf UND Verkauf, alle Kommandanten sowie die Handelsgilde selbst) an einer Station. */
  hubOrders(systemId: Id): Signal<HubOrder[]>;
  /**
   * Verkauf ab dem eigenen Stationsdepot – bucht die Ware sofort aus dem
   * Depot aus. Kreuzt die Order sofort bestehende Kauf-Orders (auch die der
   * Handelsgilde), wird SOFORT ausgeführt, auch in Teilausführung, zum Preis
   * der jeweils älteren (ruhenden) Gegenseite.
   */
  createHubSellOrder(systemId: Id, productTypeId: Id, quantity: number, pricePerUnit: number): Promise<void>;
  /**
   * Kauf-Order – bucht Menge × Preis SOFORT als Escrow aus dem Wallet aus
   * (Rückerstattung nur durch Zurückziehen der Order). Kreuzt sie sofort
   * bestehende Verkaufs-Orders, wird SOFORT ausgeführt, auch in
   * Teilausführung; gekaufte Ware fließt ins eigene Stationsdepot.
   */
  createHubBuyOrder(systemId: Id, productTypeId: Id, quantity: number, pricePerUnit: number): Promise<void>;
  /** Zieht eine eigene Kauf- oder Verkaufs-Order zurück und erstattet den nicht ausgeführten Rest (Credits bzw. Ware) zurück. Orders der Handelsgilde lassen sich nicht zurückziehen. */
  cancelHubOrder(orderId: Id): Promise<void>;

  // --- Diplomatie (Mechanik/06_..., vereinfacht, siehe DiplomacyCommands im Backend) ---
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

  // --- Friedens-/Handelsverträge (Umsetzungskonzept/21_...md) ---------------
  /** Alle Friedens-/Handelsverträge des angemeldeten Kommandanten, jeder Typ separat. */
  treaties(): Signal<Treaty[]>;
  /** An den angemeldeten Kommandanten gerichtete, noch unbeantwortete Vertragsangebote. */
  incomingTreatyOffers(): Signal<TreatyOffer[]>;
  /** Vom angemeldeten Kommandanten selbst gestellte, noch offene Vertragsangebote. */
  outgoingTreatyOffers(): Signal<TreatyOffer[]>;
  /** Gültiger (inkl. gekündigt, aber noch nicht abgelaufener) Friedensvertrag mit diesem Kommandanten – blockiert `declareWar`. */
  hasPeaceTreaty(otherPlayerId: Id): Signal<boolean>;
  /** Gültiger Handelsvertrag mit diesem Kommandanten – Voraussetzung für planetaren Handel (Kauf/Verkauf) außerhalb einer Handelsgilde-Station. */
  hasTradeAgreement(otherPlayerId: Id): Signal<boolean>;
  /** Einseitiges Angebot, wirksam erst nach Annahme durch den Empfänger (`respondToTreatyOffer`). Nur außerhalb eines Kriegs mit dieser Partei möglich. */
  offerTreaty(otherPlayerId: Id, type: TreatyType): Promise<void>;
  /** Nur der Empfänger darf antworten; Ablehnen löscht das Angebot ersatzlos. */
  respondToTreatyOffer(offerId: Id, accept: boolean): Promise<void>;
  /**
   * Kündigt einen bestehenden Vertrag – er bleibt bis zum Ende der
   * Kündigungsfrist (7 Spieltage Friedensvertrag, 2 Spieltage Handelsvertrag)
   * unverändert gültig.
   */
  terminateTreaty(otherPlayerId: Id, type: TreatyType): Promise<void>;

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

  // --- Bodengefechte (Mechanik/05_..., §2, §10-12) ---------------------------
  /** Alle laufenden Bodengefechte des angemeldeten Kommandanten (Angreifer oder Verteidiger). */
  activeGroundBattles(): Signal<GroundBattle[]>;
  groundBattle(id: Id): Signal<GroundBattle | undefined>;
  /** Beendete Bodengefechte, neueste zuerst. */
  groundBattleHistory(): Signal<GroundBattle[]>;
  /** Öffentlich abrufbarer Bodenkampfbericht über den unerratbaren `reportToken` – `/bodenkampfbericht/:token`. */
  groundBattleByReportToken(token: string): Signal<GroundBattle | undefined>;
  /**
   * Kolonien auf demselben Planeten, die dieser gelandete Verband angreifen darf
   * (fremd, im Krieg, Verband nicht schon im Gefecht) – die Regel steht im Backend,
   * der Client baut sie nicht nach.
   */
  attackableColoniesForGroup(groupId: Id): Signal<Colony[]>;
  /**
   * Läuft an dieser Kolonie gerade ein Bodengefecht? Dann ist dort weder Bau/Rückbau
   * (§1) noch ein neuer Handelsauftrag (§12) möglich, und ihre Verteidiger können
   * sie nicht verlassen (§11).
   */
  isColonyUnderGroundAttack(colonyId: Id): Signal<boolean>;
  /**
   * Greift mit einem gelandeten Verband eine fremde Kolonie auf demselben Planeten an –
   * nur im Krieg und nur mit aktiven Drohnen (Soldaten allein haben keine Kampfwirkung).
   * Hat die Kolonie keine aktivierbaren Drohnen, ist sie damit sofort gefallen (§10).
   */
  engageGroundBattle(groupId: Id, targetColonyId: Id): Promise<GroundBattle>;
  /**
   * Bricht den eigenen Bodenangriff ab; die Verteidigung schlägt dabei noch einmal
   * einseitig zu, danach steht der Verband wieder auf der Planetenoberfläche (§11).
   * Dem Eigentümer der angegriffenen Kolonie steht dieser Weg NICHT offen.
   */
  retreatFromGroundBattle(battleId: Id): Promise<void>;

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
  /** "Beibehalten" umschalten – schützt die Benachrichtigung vor der automatischen Löschung nach 2 Spieltagen. */
  setNotificationKeep(id: Id, keep: boolean): Promise<void>;

  // --- Nachrichten (ausschließlich Spieler-zu-Spieler, keine Gruppen-/Broadcast-Nachrichten) ---
  /** Empfangene Nachrichten des angemeldeten Kommandanten, neueste zuerst. */
  inbox(): Signal<Message[]>;
  /** Vom angemeldeten Kommandanten selbst gesendete Nachrichten, neueste zuerst. */
  sentMessages(): Signal<Message[]>;
  unreadMessageCount(): Signal<number>;
  sendMessage(toPlayerId: Id, subject: string, body: string): Promise<void>;
  /** Nur der Empfänger darf seine eigene Nachricht als gelesen markieren. */
  markMessageRead(id: Id): Promise<void>;
  /** "Beibehalten" umschalten – schützt die Nachricht vor der automatischen Löschung nach 7 Spieltagen. Absender UND Empfänger dürfen das setzen. */
  setMessageKeep(id: Id, keep: boolean): Promise<void>;

  // --- Universums-Statistik ------------------------------------------
  /** Zeitreihe aggregierter Stabilitätskennzahlen über die gesamte Galaxie. */
  universeStats(): Signal<UniverseStatSnapshot[]>;
  /**
   * Der entschiedene Krieg – `null`, solange mehr als eine Partei Kolonien
   * besitzt. Steht der Wert, hat eine Partei alle gegnerischen Kolonien
   * genommen (Siegbedingung, `VictoryCommands`).
   */
  victory(): Signal<GameVictory | null>;
}
