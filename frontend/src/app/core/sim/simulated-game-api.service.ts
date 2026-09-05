import { Injectable, Signal, computed, signal } from '@angular/core';
import {
  Battle, BattleOutcome, BattleStatus, BattleTickResult, Blockade, BlockadeAnchor, Building, BuildingType, ChainPlan, ChainPlanStep, Colony, ColonyPowerState,
  DiplomaticRelation, DiplomaticStatus, Fleet, FleetShipGroup, FleetSystemTarget, GameNotification, Gateway, GatewayWeightEntry, GroundForceGroup,
  GroundUnitTypeDef, Id, NotificationType, Npc, PeaceOffer, Planet, PlanetStats, Player, Population, PopulationMoneySupplyState,
  ProductType, ProductionQueueEntry, ProductionQueueStatus, RecruitmentQueueEntry, SellOrder, ShipTypeDef,
  ShipyardQueueEntry, Specialization, System, Transaction, TransactionReason,
  UniverseStatSnapshot, Wallet, WarehouseEntry,
} from '../models';
import { GameApi } from './game-api';
import { hoursToMs, now, REAL_MS_PER_GAME_HOUR } from './clock';
import { nextId, randomToken } from './id';
import { PRODUCT_CATALOG, findProductType } from './data/product-catalog';
import { BUILDING_CATALOG, findBuildingType } from './data/building-catalog';
import { SHIP_CATALOG, findShipDef } from './data/ship-catalog';
import { GROUND_UNIT_CATALOG } from './data/ground-unit-catalog';
import { createAdditionalPlayerSeed, createWorldSeed, AdditionalPlayerSeed, WorldSeed } from './data/world-seed';
import { bfsHops, bfsPath } from '../util/graph';
import * as F from './engine/formulas';

/**
 * EIN gemeinsam geteilter Galaxie-Zustand für alle registrierten
 * Kommandanten (siehe `Player`) – kein Konzept "pro Nutzer eine eigene
 * Galaxie" mehr: Registrieren fügt der bestehenden Galaxie einen neuen
 * Kommandanten samt neuem Heimatsystem hinzu (`createAdditionalPlayerSeed`),
 * NPCs/Systeme/Märkte bleiben dabei unverändert bestehen.
 */
const STORAGE_KEY = 'nebula_sim_v1';
/** Zeiger auf den in diesem Browser-Tab aktuell angemeldeten Kommandanten – Session-Info, kein Teil des Weltzustands. */
const ACTIVE_PLAYER_STORAGE_KEY = 'nebula_active_player';
/** Namen des einzigen, beim allerersten Start automatisch registrierten Kommandanten (siehe `bootstrapFreshWorld`). */
const DEFAULT_COMMANDER_NAME = 'Kommandant Vega';
const DEFAULT_HOMEWORLD_NAME = 'Neu-Terra';
const TICK_MS = 1000;
const TICK_GAME_HOURS = TICK_MS / REAL_MS_PER_GAME_HOUR;
const SPECIALIZATION_DECAY_GRACE_MS = 16000;
const DEFENSE_ACTIVATION_HOURS = 12;
/**
 * Konsumgüter für die Bevölkerungsversorgung – seit dem erweiterten
 * Produktionsbaum (Nebula_Planetentypen_..., §10.7) mehrstufige
 * Endprodukte statt einstufiger Güter. Siehe `npcMaybeQueueFoodChain` für
 * die NPC-KI-Seite dieser tieferen Kette.
 */
const CONSUMER_GOODS_ORDER = ['p_grundnahrung', 'p_grundmedizin', 'p_unterhaltungselektronik'];
/** Ziel-Endprodukt und Mindestbestand der NPC-Nahrungskette, siehe `npcMaybeQueueFoodChain`. */
const FOOD_TARGET_PRODUCT_ID = 'p_grundnahrung';
const FOOD_TARGET_STOCK = 30;
const DRONE_PRODUCT_IDS = ['p_drone_light', 'p_drone_medium', 'p_drone_heavy'];
/**
 * Soldaten-zu-Drohnen-Besetzungsverhältnis (wie viele Drohnen ein
 * einzelner Soldat per Fernsteuerung gleichzeitig kommandieren kann),
 * siehe Mechanik/05_..., §4 ("genaue Zahl noch offen") – Platzhalter, an
 * dieser einen Stelle austauschbar, sobald die Konzeption einen
 * konkreten Wert festlegt. Wenige Soldaten kommandieren viele Drohnen,
 * nicht umgekehrt (ein Soldat sitzt nicht in der Drohne, sondern
 * steuert sie aus der Ferne).
 */
const DRONES_PER_SOLDIER = 5;
/**
 * Bedarf je Einwohner UND Tick (nicht pro Spielstunde – ConsumptionTickJob
 * läuft hier jeden Tick). Ursprünglich mit 1/0,35/0,25 viel zu hoch
 * angesetzt: Bei ~250 Einwohnern ergab das einen Bedarf von >200
 * Einheiten pro Tick, während eine Kolonie realistischerweise nur
 * ~0,2-0,3 Einheiten pro Sekunde nachliefert – die Deckung blieb
 * dauerhaft nahe 0%, selbst nach einer ersten Kalibrierung auf 0,02
 * (Bedarf noch immer ~20× schneller als Produktion). Durch NPC-
 * Stresstest gefunden und in zwei Schritten so kalibriert, dass Bedarf
 * und realistisch erreichbarer Produktionsdurchsatz sich die Waage
 * halten (siehe Mechanik/09_..., "genaue Kurve offen"). Nach dem Wechsel
 * auf den siebenstufigen Produktionsbaum (Nebula_Planetentypen_..., §10)
 * nochmals um etwa den Faktor 6 gesenkt: Die neuen Zielgüter liegen 4-5
 * Fertigungsstufen hinter den Rohstoffen statt einer, der erreichbare
 * Durchsatz sinkt entsprechend. Weiterhin ein Platzhalter, siehe §15 dort
 * ("Verbrauch ... je Bevölkerungseinheit" als offene Zahlenfrage).
 */
const CONSUMER_NEED_PER_CAPITA: Record<string, number> = { p_grundnahrung: 0.0004, p_grundmedizin: 0.00015, p_unterhaltungselektronik: 0.0001 };
const NPC_AI_INTERVAL_MS = 5000;
const STATS_SNAPSHOT_INTERVAL_MS = 10000;
const STATS_HISTORY_LIMIT = 400;
/**
 * Der 1s-Tick verändert Wirtschaftswerte (Löhne, Konsum, Bevölkerung,
 * Statistik) kontinuierlich – ein voller State-Serialize bei JEDEM Tick wäre
 * für eine künftige Client/Server-Trennung genau das Muster, das den Server
 * über einen Websocket mit "ständig großen Tick-Daten" überlasten würde
 * (siehe Konzeption/Umsetzungskonzept/10_..., Abschnitt Ereignisbasierung).
 * Diskrete Nutzeraktionen (Gebäude in Auftrag geben, Produktion starten, ...)
 * persistieren weiterhin sofort über ihre eigenen `persist()`-Aufrufe direkt
 * an der jeweiligen Kommando-Methode; nur der tickgetriebene Persist wird
 * auf dieses Intervall gedrosselt (siehe `schedulePersistFromTick`).
 */
const TICK_PERSIST_INTERVAL_MS = 4000;
/**
 * Ausgleichsfonds gegen Geldhortung (Konzeption/Spieldesign/06_..., §8;
 * Mechanik/10_..., §7): täglich zahlen große Spieler-/Kommandanten-Wallets
 * und jede Kolonie eine feste Abgabe in einen galaxieweiten Topf, der im
 * selben Lauf wieder vollständig pro Kopf an alle Bevölkerungs-Wallets
 * ausgeschüttet wird – kein Money Sink, reine Umverteilung.
 */
const GAME_DAY_MS = hoursToMs(24);
/** Reisezeit pro Gateway-Sprung – Gateways sind von Anfang an uneingeschränkt offen, siehe world-seed.ts. */
const HOURS_PER_GATEWAY_HOP = 4;
const WEALTH_TAX_THRESHOLD = 1000;
const WEALTH_TAX_RATE = 0.001;
const COLONY_TAX_RATE = 0.01;
/** Platzhalter für einen noch nicht berechneten `ChainPlan` (Auftrag wartet in der Warteschlange), siehe `planChain`. */
const EMPTY_CHAIN_PLAN: ChainPlan = { totalHours: 0, steps: [], feasible: true };
/** Benachrichtigungscode "Auftragswarteschlange mangels Vorprodukten angehalten", siehe Konzeption/Umsetzungskonzept/10_...md, §5. */
const NOTIFICATION_CODE_QUEUE_STOPPED = 503;
const NOTIFICATION_CODE_PEACE_OFFERED = 101;
const NOTIFICATION_CODE_PEACE_ACCEPTED = 102;
const NOTIFICATION_CODE_WAR_DECLARED = 401;
const NOTIFICATION_CODE_BATTLE_STARTED = 402;
const NOTIFICATION_CODE_BATTLE_ENDED = 403;
/**
 * Mindestdauer eines Kriegs (Spielstunden), bevor die Kriegspartei, die ihn
 * erklärt hat, selbst ein Friedensangebot unterbreiten darf (Mechanik/06_...,
 * "Mindestdauer"-Regel – vereinfacht auf eine einzelne globale Schwelle statt
 * separater Erklärungs-/Beitritts-Fristen). Verhindert Kriegserklärungen als
 * folgenlose reine Drohgeste, die man Sekunden später wieder zurücknimmt.
 */
const WAR_MIN_DURATION_HOURS = 24;
/**
 * PowerUpkeepJob (Umsetzungskonzept/01_..., §3): laufender Verbrauch des
 * Energienetzes an Stabilisiertem Elerium, pro Stufe und Spielstunde.
 * Bewusst klein gewählt – Elerium-115 ist selten (siehe product-catalog.ts).
 * Betriebsstoff ist bewusst Stabilisiertes Elerium (Ebene 1, direkt aus
 * Eleriumspuren) und NICHT die tiefer in der Kette liegende
 * Eleriumenergiezelle (Ebene 4) – eine Kolonie muss ihr Energienetz allein
 * am Laufen halten können, ohne die komplette Elerium-Kette hochziehen zu
 * müssen. Reicht der Bestand nicht, sinkt `coverageRatio` und mindert
 * anteilig die Wohnkapazität, die das Energienetz beisteuert.
 */
const ELERIUM_UPKEEP_PER_POWERGRID_LEVEL = 0.005;
const POWERGRID_FUEL_PRODUCT_ID = 'p_elerium_stabil';
/** Unterhalb dieser Deckungsquote gilt eine Kolonie als "im Blackout" (siehe `isBlackout`). */
const BLACKOUT_THRESHOLD = 0.999;
/**
 * Blackout-Folgen (Konzeption/Spieldesign/06_..., "Energieversorgung"):
 * Produktion läuft nur noch auf 10% Geschwindigkeit, Sicherheit und
 * Lebensstandard werden halbiert, Bevölkerungswachstum (nicht aber
 * Schrumpfung) setzt vollständig aus – siehe `computeProductionHours`,
 * `recalcCoreStats`, `runConsumption`, `growPopulationAndMoneySupply`.
 */
const BLACKOUT_PRODUCTION_FACTOR = 0.1;
const BLACKOUT_STAT_FACTOR = 0.5;

interface Snapshot {
  version: 2;
  players: Player[];
  systems: System[];
  /** Pro Kommandant separat geführt (Fog of War) – siehe `_knownSystemIds`. */
  knownSystemIdsByPlayer: Record<Id, Id[]>;
  planets: Planet[];
  colonies: Colony[];
  planetStats: PlanetStats[];
  powerStates: ColonyPowerState[];
  populations: Population[];
  moneySupplyStates: PopulationMoneySupplyState[];
  wallets: Wallet[];
  transactions: Transaction[];
  buildings: Building[];
  specializations: Specialization[];
  productionQueue: ProductionQueueEntry[];
  warehouse: WarehouseEntry[];
  gateways: Gateway[];
  fleets: Fleet[];
  shipyardQueue: ShipyardQueueEntry[];
  groundForceGroups: GroundForceGroup[];
  recruitmentQueue: RecruitmentQueueEntry[];
  sellOrders: SellOrder[];
  consumptionBudget: Record<string, number>;
  npcs: Npc[];
  universeStats: UniverseStatSnapshot[];
  notifications: GameNotification[];
  diplomaticRelations: DiplomaticRelation[];
  peaceOffers: PeaceOffer[];
  battles: Battle[];
  blockades: Blockade[];
}

/**
 * In-Memory-Simulation, die den `GameApi`-Vertrag erfüllt. Hält den
 * kompletten Weltzustand als Angular-Signals und treibt ihn über einen
 * einzigen Tick-Loop an (siehe Klassendoku am Ende der Datei für die
 * Zuordnung zu den in Umsetzungskonzept/00_... dokumentierten
 * Hintergrundjobs).
 *
 * Absichtlich NICHT von Feature-Komponenten direkt injiziert – siehe
 * `GAME_API`-Token.
 */
@Injectable({ providedIn: 'root' })
export class SimulatedGameApiService implements GameApi {
  /** Alle registrierten Kommandanten der gemeinsamen Galaxie (siehe Klassendoku oben). */
  private readonly _players = signal<Player[]>([]);
  /** Wer in DIESEM Browser-Tab gerade eingeloggt ist – Session-Zustand, kein Teil des Weltzustands (siehe `ACTIVE_PLAYER_STORAGE_KEY`). */
  private readonly _activePlayerId = signal<Id | null>(null);
  private readonly _systems = signal<System[]>([]);
  /** Bekannte Systeme PRO Kommandant (Fog of War) – ein neu registrierter Kommandant kennt nur sein eigenes Heimatsystem, auch wenn andere längst die ganze Galaxie kartiert haben. */
  private readonly _knownSystemIds = signal<Map<Id, Set<Id>>>(new Map());
  private knownSystemIdsFor(playerId: Id | undefined): Set<Id> { return this._knownSystemIds().get(playerId ?? '') ?? new Set(); }
  private readonly _planets = signal<Planet[]>([]);
  private readonly _colonies = signal<Colony[]>([]);
  private readonly _planetStats = signal<PlanetStats[]>([]);
  private readonly _powerStates = signal<ColonyPowerState[]>([]);
  private readonly _populations = signal<Population[]>([]);
  private readonly _moneySupplyStates = signal<PopulationMoneySupplyState[]>([]);
  private readonly _wallets = signal<Wallet[]>([]);
  private readonly _transactions = signal<Transaction[]>([]);
  private readonly _buildings = signal<Building[]>([]);
  private readonly _specializations = signal<Specialization[]>([]);
  private readonly _productionQueue = signal<ProductionQueueEntry[]>([]);
  private readonly _warehouse = signal<WarehouseEntry[]>([]);
  /**
   * `colonyId:productTypeId` → Menge, im Gleichschritt mit `_warehouse`
   * gepflegt (siehe `rebuildWarehouseIndex`/`addToWarehouse`). Vermeidet die
   * frühere Alternative – bei jeder Mengenabfrage `_warehouse().find(...)`,
   * also ein linearer Scan über ALLE Lagerbestände der gesamten Galaxie pro
   * Aufruf – die bei tiefen, vielköpfigen Produktionsketten (siehe
   * Dauerproduktions-Baum: schnell 100+ Aufträge, mehrfach pro Tick je ein
   * Rezept-Eingang geprüft) zum Performance-Engpass werden kann.
   */
  private readonly warehouseIndex = new Map<Id, number>();
  private warehouseKey(colonyId: Id, productTypeId: Id): Id { return `${colonyId}:${productTypeId}`; }
  private warehouseQty(colonyId: Id, productTypeId: Id): number { return this.warehouseIndex.get(this.warehouseKey(colonyId, productTypeId)) ?? 0; }
  private rebuildWarehouseIndex(list: WarehouseEntry[]): void {
    this.warehouseIndex.clear();
    for (const w of list) this.warehouseIndex.set(this.warehouseKey(w.colonyId, w.productTypeId), w.quantity);
  }
  private readonly _gateways = signal<Gateway[]>([]);
  private readonly _fleets = signal<Fleet[]>([]);
  private readonly _shipyardQueue = signal<ShipyardQueueEntry[]>([]);
  private readonly _groundForceGroups = signal<GroundForceGroup[]>([]);
  private readonly _recruitmentQueue = signal<RecruitmentQueueEntry[]>([]);
  private readonly _sellOrders = signal<SellOrder[]>([]);
  private readonly _npcs = signal<Npc[]>([]);
  private readonly _universeStats = signal<UniverseStatSnapshot[]>([]);
  /** Benachrichtigungssystem, siehe Konzeption/Umsetzungskonzept/10_...md, §5. Neueste zuerst gepflegt (siehe `notify`). */
  private readonly _notifications = signal<GameNotification[]>([]);
  /** Genau ein Eintrag je ungeordnetem Kommandanten-Paar, siehe `relationKey`/`DiplomaticRelation`. Fehlender Eintrag = impliziter Frieden. */
  private readonly _diplomaticRelations = signal<DiplomaticRelation[]>([]);
  private readonly _peaceOffers = signal<PeaceOffer[]>([]);
  /** Raumgefechte, siehe `Battle` – bewusst vereinfacht auf strikte 1v1-Flottengefechte, siehe `engageBattle`. */
  private readonly _battles = signal<Battle[]>([]);
  /** Blockaden, siehe `Blockade` – macht die bildende Flotte angreifbar (`engageBattle`), stark vereinfacht ggü. Mechanik/06_...md. */
  private readonly _blockades = signal<Blockade[]>([]);
  /**
   * Zuletzt in `runConsumption` ermittelte Deckung (0..1,5, 1 = Bedarf exakt
   * gedeckt) je Grundkonsumgut und Kolonie – rein abgeleiteter Diagnosewert
   * für die Statistik-Seite ("woran hakt es beim Lebensstandard genau"),
   * nicht Teil des Spielstands (kein Snapshot-Feld, wird jeden Tick neu
   * berechnet statt persistiert).
   */
  private readonly _consumptionCoverage = signal<Record<Id, Record<Id, number>>>({});

  /** rein internes Buchführungs-Detail (Spezialisierungs-Verfall), kein Modellfeld. */
  private readonly lastProducedAt = new Map<string, number>();
  /** geglättetes Konsumbudget je Kolonie (Umsetzungskonzept/07_..., §3, N). */
  private readonly consumptionBudget = new Map<Id, number>();
  /**
   * Ungekürzter (nicht Blackout-halbierter) Lebensstandard je Kolonie,
   * getrennt von `PlanetStats.standardOfLivingPct` geführt: Letzteres ist
   * der öffentlich sichtbare, ggf. per Blackout halbierte Wert – würde die
   * Glättung in `runConsumption` direkt auf ihm aufbauen, würde ein
   * anhaltender Blackout den Wert exponentiell gegen 0 drücken statt ihn
   * stabil zu halbieren.
   */
  private readonly rawStandardOfLiving = new Map<Id, number>();
  private lastNpcAiAt = 0;
  private lastStatsSnapshotAt = 0;
  private lastWealthRedistributionAt = 0;
  private lastPersistAt = 0;

  /** Der aktuell EINGELOGGTE Kommandant (null = abgemeldet, siehe `_activePlayerId`) – gleiche Signalform wie zuvor, jetzt aus der gemeinsamen Spielerliste aufgelöst. */
  readonly player = computed(() => this._players().find(p => p.id === this._activePlayerId()) ?? null);
  readonly wallet = computed(() => this.findWallet('Player', this.player()?.id ?? ''));

  constructor() {
    const loadedWorld = this.tryLoad();
    if (!loadedWorld) {
      // Allererster Start dieses Browsers: genau EIN Kommandant wird automatisch
      // registriert (siehe Klassendoku) – sichtbar in `players()`, aber noch
      // nicht eingeloggt. Die Startseite (siehe app.component) zeigt Login/Registrieren.
      this.bootstrapFreshWorld();
    }
    const activePlayerId = localStorage.getItem(ACTIVE_PLAYER_STORAGE_KEY);
    if (activePlayerId && this._players().some(p => p.id === activePlayerId)) {
      this._activePlayerId.set(activePlayerId);
    }
    setInterval(() => this.runTick(), TICK_MS);
    // Fängt den durch TICK_PERSIST_INTERVAL_MS gedrosselten Persist ab: ohne
    // diesen Flush könnten beim Schließen/Wechseln des Tabs bis zu
    // TICK_PERSIST_INTERVAL_MS an tickgetriebenem Fortschritt (Löhne, Konsum,
    // Bevölkerungswachstum) verloren gehen. 'visibilitychange' statt/zusätzlich
    // zu 'beforeunload', weil Mobile-Browser Tabs oft ohne beforeunload beenden.
    // Ungebunden an einen eingeloggten Nutzer – die Welt simuliert unabhängig
    // davon weiter (siehe `runTick`), auch andere Kommandanten/NPCs laufen.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden' && this._players().length > 0) this.persist();
    });
    window.addEventListener('beforeunload', () => {
      if (this._players().length > 0) this.persist();
    });
  }

  // ==========================================================================
  // Bootstrap / Konten
  // ==========================================================================

  players(): Signal<Player[]> {
    return this._players.asReadonly();
  }

  async registerPlayer(commanderName: string, homeworldName: string): Promise<void> {
    await this.latency();
    const name = commanderName.trim() || 'Unbekannter Kommandant';
    const homeworld = homeworldName.trim() || 'Heimatwelt';
    const seed = createAdditionalPlayerSeed(this._systems(), name, homeworld);
    this.appendPlayer(seed);
    this._activePlayerId.set(seed.player.id);
    localStorage.setItem(ACTIVE_PLAYER_STORAGE_KEY, seed.player.id);
    this.persist();
  }

  async login(playerId: Id): Promise<void> {
    await this.latency();
    if (!this._players().some(p => p.id === playerId)) throw new Error('Unbekannter Kommandant.');
    this.persist(); // ausstehenden tickgetriebenen Fortschritt vor dem Wechsel sichern
    this._activePlayerId.set(playerId);
    localStorage.setItem(ACTIVE_PLAYER_STORAGE_KEY, playerId);
  }

  async logout(): Promise<void> {
    await this.latency();
    this.persist();
    this._activePlayerId.set(null);
    localStorage.removeItem(ACTIVE_PLAYER_STORAGE_KEY);
  }

  /** Kompletter Fabrik-Reset: löscht die GESAMTE gemeinsame Galaxie (alle Kommandanten!) und registriert danach wieder genau einen Standard-Kommandanten, wie beim allerersten Start. */
  async resetGame(): Promise<void> {
    await this.latency();
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(ACTIVE_PLAYER_STORAGE_KEY);
    this._activePlayerId.set(null);
    this.bootstrapFreshWorld();
  }

  /** Setzt alle In-Memory-Signale auf eine leere Galaxie zurück – Baustein für `bootstrapFreshWorld`. */
  private clearInMemoryState(): void {
    this._players.set([]);
    this._systems.set([]);
    this._knownSystemIds.set(new Map());
    this._planets.set([]);
    this._colonies.set([]);
    this._planetStats.set([]);
    this._powerStates.set([]);
    this._populations.set([]);
    this._moneySupplyStates.set([]);
    this._wallets.set([]);
    this._transactions.set([]);
    this._buildings.set([]);
    this._specializations.set([]);
    this._productionQueue.set([]);
    this._warehouse.set([]);
    this.warehouseIndex.clear();
    this._gateways.set([]);
    this._fleets.set([]);
    this._shipyardQueue.set([]);
    this._groundForceGroups.set([]);
    this._recruitmentQueue.set([]);
    this._sellOrders.set([]);
    this._npcs.set([]);
    this._universeStats.set([]);
    this._consumptionCoverage.set({});
    this._notifications.set([]);
    this._diplomaticRelations.set([]);
    this._peaceOffers.set([]);
    this._battles.set([]);
    this._blockades.set([]);
    this.lastProducedAt.clear();
    this.consumptionBudget.clear();
    this.rawStandardOfLiving.clear();
    this.lastNpcAiAt = 0;
    this.lastStatsSnapshotAt = 0;
    this.lastWealthRedistributionAt = 0;
    this.lastPersistAt = 0;
  }

  /** Erzeugt eine komplett neue Galaxie (NPCs, 24 Systeme) mit genau einem Standard-Kommandanten – für den allerersten Start UND für `resetGame`. */
  private bootstrapFreshWorld(): void {
    this.clearInMemoryState();
    this.hydrate(createWorldSeed(DEFAULT_COMMANDER_NAME, DEFAULT_HOMEWORLD_NAME));
    this.persist();
  }

  private hydrate(seed: WorldSeed): void {
    this._players.set([seed.player]);
    this._systems.set(seed.systems);
    this._knownSystemIds.set(new Map([[seed.player.id, new Set([seed.player.homeSystemId])]]));
    this._planets.set(seed.planets);
    this._colonies.set(seed.colonies);
    this._planetStats.set(seed.planetStats);
    this._populations.set(seed.populations);
    this._moneySupplyStates.set(seed.moneySupplyStates);
    this._wallets.set(seed.wallets);
    this._buildings.set(seed.buildings);
    this._warehouse.set(seed.warehouse);
    this.rebuildWarehouseIndex(seed.warehouse);
    this._productionQueue.set(seed.productionQueue);
    this._gateways.set(seed.gateways);
    this._npcs.set(seed.npcs);
    this._fleets.set(seed.fleets);
    this._groundForceGroups.set(seed.groundForceGroups);
    // Start-Auftragsliste kommt direkt über `.set()` statt über `queueProduction`
    // herein, das sonst automatisch den nächsten wartenden Eintrag anstößt.
    for (const colonyId of new Set(seed.productionQueue.map(e => e.colonyId))) this.tryStartNextProductionEntry(colonyId);
  }

  /**
   * Fügt einen NEU registrierten Kommandanten samt neuem Heimatsystem in die
   * BESTEHENDE Galaxie ein (siehe `createAdditionalPlayerSeed`), statt eine
   * neue Galaxie zu erzeugen – NPCs, andere Kommandanten und der Systemmarkt
   * bleiben unverändert bestehen.
   */
  private appendPlayer(seed: AdditionalPlayerSeed): void {
    this._systems.update(list => [...list, seed.newSystem]);
    this._gateways.update(list => [...list, seed.newGateway]);
    this._gateways.update(list => list.map(g => g.systemId === seed.linkedSystemId
      ? { ...g, reachableSystemIds: [...g.reachableSystemIds, seed.newSystem.id] }
      : g));
    this._players.update(list => [...list, seed.player]);
    this._knownSystemIds.update(map => new Map(map).set(seed.player.id, new Set([seed.newSystem.id])));
    this._planets.update(list => [...list, ...seed.planets]);
    this._colonies.update(list => [...list, seed.colony]);
    this._planetStats.update(list => [...list, seed.planetStats]);
    this._populations.update(list => [...list, seed.population]);
    this._moneySupplyStates.update(list => [...list, seed.moneySupplyState]);
    this._wallets.update(list => [...list, ...seed.wallets]);
    this._buildings.update(list => [...list, ...seed.buildings]);
    this._warehouse.update(list => [...list, ...seed.warehouse]);
    this.rebuildWarehouseIndex(this._warehouse());
    this._productionQueue.update(list => [...list, ...seed.productionQueue]);
    this._fleets.update(list => [...list, ...seed.fleets]);
    this._groundForceGroups.update(list => [...list, seed.groundForceGroup]);
    this.tryStartNextProductionEntry(seed.colony.id);
  }

  // ==========================================================================
  // Katalog
  // ==========================================================================

  productTypes(): ProductType[] { return PRODUCT_CATALOG; }
  buildingTypes(): BuildingType[] { return BUILDING_CATALOG; }
  shipTypes(): ShipTypeDef[] { return SHIP_CATALOG; }
  groundUnitTypes(): GroundUnitTypeDef[] { return GROUND_UNIT_CATALOG; }

  // ==========================================================================
  // Planeten / Kolonien
  // ==========================================================================

  colonies(): Signal<Colony[]> {
    return computed(() => this._colonies().filter(c => c.ownerId === this.player()?.id));
  }
  coloniesInSystem(systemId: Id): Signal<Colony[]> {
    return computed(() => this._colonies().filter(c => c.systemId === systemId));
  }
  colony(id: Id): Signal<Colony | undefined> {
    return computed(() => this._colonies().find(c => c.id === id));
  }
  colonyStats(id: Id): Signal<PlanetStats | undefined> {
    return computed(() => this._planetStats().find(s => s.colonyId === id));
  }
  /** Deckung (0..1,5) je Grundkonsumgut, siehe `_consumptionCoverage`. Leeres Objekt vor dem ersten Tick. */
  consumptionCoverage(colonyId: Id): Signal<Record<Id, number>> {
    return computed(() => this._consumptionCoverage()[colonyId] ?? {});
  }
  planet(id: Id): Signal<Planet | undefined> {
    return computed(() => this._planets().find(p => p.id === id));
  }
  planetsInSystem(systemId: Id): Signal<Planet[]> {
    return computed(() => this._planets().filter(p => p.systemId === systemId));
  }

  async colonizePlanet(planetId: Id): Promise<Colony> {
    await this.latency();
    const player = this.requirePlayer();
    const planet = this._planets().find(p => p.id === planetId);
    if (!planet) throw new Error('Unbekannter Planet.');
    if (this._colonies().some(c => c.planetId === planetId && c.ownerId === player.id)) {
      throw new Error('Auf diesem Planeten besteht bereits eine eigene Kolonie.');
    }
    const cost = 800;
    const wallet = this.findWallet('Player', player.id);
    if (!wallet || wallet.balance < cost) throw new Error('Nicht genug Credits für eine Kolonialgründung.');

    const colony: Colony = {
      id: nextId('col'), planetId, systemId: planet.systemId, ownerId: player.id,
      name: `${planet.name}-Kolonie`, foundedAt: now(), isHomeworld: false,
    };
    this._colonies.update(list => [...list, colony]);
    this._planetStats.update(list => [...list, {
      colonyId: colony.id, infrastructurePct: 15, securityPct: 5, standardOfLivingPct: 30, loyaltyPct: 55, lastRecalculatedAt: now(),
    }]);
    this._populations.update(list => [...list, { colonyId: colony.id, currentCount: 25, growthRatePerInterval: 0 }]);
    this._moneySupplyStates.update(list => {
      if (list.some(m => m.planetId === planetId)) return list;
      return [...list, { planetId, historicalPeakPopulation: 25, lastPopulation: 25 }];
    });
    const popWallet: Wallet = { id: nextId('wal'), ownerType: 'Population', ownerId: colony.id, balance: 30 };
    this._wallets.update(list => [...list, popWallet]);

    this.recordTx(wallet.id, this.homeworldPopulationWalletId(), cost, 'Construction', `Kolonialgründung ${colony.name}`);
    this.persist();
    return colony;
  }

  // ==========================================================================
  // Bebauung
  // ==========================================================================

  buildings(colonyId: Id): Signal<Building[]> {
    return computed(() => this._buildings().filter(b => b.colonyId === colonyId));
  }

  overbuildFactor(planetId: Id): Signal<number> {
    return computed(() => {
      const planet = this._planets().find(p => p.id === planetId);
      if (!planet) return 1;
      const colonyIds = new Set(this._colonies().filter(c => c.planetId === planetId).map(c => c.id));
      const used = this._buildings()
        .filter(b => colonyIds.has(b.colonyId))
        .reduce((sum, b) => sum + F.buildPointsUsed(findBuildingType(b.typeId).buildPointsPerLevel, b.level), 0);
      return F.overbuildFactor(used, planet.buildCapacity);
    });
  }

  housingCapacity(colonyId: Id): Signal<number> {
    return computed(() => this.effectiveHousingCapacity(colonyId));
  }

  powerCoverage(colonyId: Id): Signal<number> {
    return computed(() => this._powerStates().find(p => p.colonyId === colonyId)?.coverageRatio ?? 1);
  }

  /** Aktueller Elerium-Energiezelle-Bedarf des Energienetzes pro Spielstunde, siehe `consumePowerUpkeep`. 0, solange kein Energienetz gebaut ist. */
  powerUpkeepPerHour(colonyId: Id): Signal<number> {
    return computed(() => this.getBuildingLevel(colonyId, 'b_powergrid') * ELERIUM_UPKEEP_PER_POWERGRID_LEVEL);
  }

  async queueBuilding(colonyId: Id, buildingTypeId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    this.queueBuildingCore(colonyId, buildingTypeId);
  }

  /** Ungeprüfter Kern von `queueBuilding` – auch von der NPC-KI direkt genutzt (siehe `requireOwnColony`-Doku). */
  private queueBuildingCore(colonyId: Id, buildingTypeId: Id): void {
    const colonyOwnerId = this.requireColonyOwner(colonyId);
    const type = findBuildingType(buildingTypeId);
    let building = this._buildings().find(b => b.colonyId === colonyId && b.typeId === buildingTypeId);
    const fromLevel = building?.level ?? 0;
    if (fromLevel >= type.maxLevel) throw new Error(`${type.name} hat bereits die Höchststufe erreicht.`);
    if (building?.pendingOrder) throw new Error(`${type.name} wird bereits ausgebaut.`);

    const cost = F.buildingUpgradeCost(type.baseCostPerLevel, fromLevel);
    const hours = F.buildingUpgradeHours(type.baseHoursPerLevel, fromLevel);
    const wallet = this.findWallet('Player', colonyOwnerId);
    if (!wallet || wallet.balance < cost) throw new Error('Nicht genug Credits für diesen Ausbau.');

    const pendingOrder = { targetLevel: fromLevel + 1, startedAt: now(), completesAt: now() + hoursToMs(hours) };
    if (building) {
      this._buildings.update(list => list.map(b => b.id === building!.id ? { ...b, pendingOrder } : b));
    } else {
      building = { id: nextId('bld'), colonyId, typeId: buildingTypeId, level: 0, pendingOrder, activationState: type.category === 'PlanetaryDefense' ? 'Inactive' : null, activationCompletesAt: null };
      this._buildings.update(list => [...list, building!]);
    }
    this.recordTx(wallet.id, this.popWalletIdForColony(colonyId), cost, 'Construction', `Ausbau ${type.name} → Stufe ${fromLevel + 1}`);
    this.persist();
  }

  async cancelBuildingOrder(colonyId: Id, buildingId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const building = this._buildings().find(b => b.id === buildingId && b.colonyId === colonyId);
    if (!building?.pendingOrder) throw new Error('Kein laufender Ausbauauftrag.');
    const type = findBuildingType(building.typeId);
    const refund = F.buildingUpgradeCost(type.baseCostPerLevel, building.level);
    this._buildings.update(list => list.map(b => b.id === buildingId ? { ...b, pendingOrder: null } : b));
    const player = this.requirePlayer();
    const wallet = this.findWallet('Player', player.id);
    if (wallet) this.recordTx(this.popWalletIdForColony(colonyId), wallet.id, refund, 'Construction', `Abbruch Ausbau ${type.name}`);
    this.persist();
  }

  async demolishBuilding(colonyId: Id, buildingId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const building = this._buildings().find(b => b.id === buildingId && b.colonyId === colonyId);
    if (!building || building.level <= 0) throw new Error('Kein rückbaubares Gebäude.');
    if (building.pendingOrder) throw new Error('Während eines laufenden Ausbaus kann nicht rückgebaut werden.');
    const type = findBuildingType(building.typeId);
    const refund = Math.round(F.buildingUpgradeCost(type.baseCostPerLevel, building.level - 1) * 0.5);
    this._buildings.update(list => list.map(b => b.id === buildingId ? { ...b, level: b.level - 1 } : b));
    const player = this.requirePlayer();
    const wallet = this.findWallet('Player', player.id);
    if (wallet) this.recordTx(this.popWalletIdForColony(colonyId), wallet.id, refund, 'Construction', `Rückbau ${type.name}`);
    this.persist();
  }

  async activateDefense(colonyId: Id, buildingId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const building = this._buildings().find(b => b.id === buildingId && b.colonyId === colonyId);
    if (!building || building.level < 1) throw new Error('Die Verteidigungsanlage muss zunächst gebaut werden.');
    if (building.activationState === 'Active' || building.activationState === 'Activating') throw new Error('Bereits aktiv bzw. in Aktivierung.');
    this._buildings.update(list => list.map(b => b.id === buildingId
      ? { ...b, activationState: 'Activating', activationCompletesAt: now() + hoursToMs(DEFENSE_ACTIVATION_HOURS) }
      : b));
    this.persist();
  }

  async deactivateDefense(colonyId: Id, buildingId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    this._buildings.update(list => list.map(b => b.id === buildingId && b.colonyId === colonyId
      ? { ...b, activationState: 'Inactive', activationCompletesAt: null }
      : b));
    this.persist();
  }

  // ==========================================================================
  // Produktion (sequentielle Warteschlange, siehe Konzeption/Umsetzungskonzept/
  // 10_Sequentielle_Produktionsauftraege_und_Ereignissystem.md)
  // ==========================================================================

  warehouse(colonyId: Id): Signal<WarehouseEntry[]> {
    return computed(() => this._warehouse().filter(w => w.colonyId === colonyId && w.quantity > 0));
  }
  specializations(colonyId: Id): Signal<Specialization[]> {
    return computed(() => this._specializations().filter(s => s.colonyId === colonyId));
  }
  productionQueue(colonyId: Id): Signal<ProductionQueueEntry[]> {
    return computed(() => this._productionQueue().filter(q => q.colonyId === colonyId));
  }

  async queueProduction(colonyId: Id, productTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    this.queueProductionCore(colonyId, productTypeId, quantity, autoProduceMissing, requeueOnComplete);
  }

  /** Ungeprüfter Kern von `queueProduction` – auch von der NPC-KI direkt genutzt (siehe `requireOwnColony`-Doku). */
  private queueProductionCore(colonyId: Id, productTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): void {
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    const product = findProductType(productTypeId);
    if (product.category === 'Ship' || product.category === 'GroundUnit') {
      throw new Error('Schiffe und Bodeneinheiten werden über Werft bzw. Ausbildungszentrum in Auftrag gegeben.');
    }
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) {
      throw new Error('Ohne Industriekomplex ist keine Fertigung möglich.');
    }
    const entry: ProductionQueueEntry = {
      id: nextId('pq'), colonyId, productTypeId, quantity, autoProduceMissing, requeueOnComplete,
      status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
    };
    this._productionQueue.update(list => [...list, entry]);
    this.tryStartNextProductionEntry(colonyId);
    this.persist();
  }

  /**
   * Reine Vorschau (keine Zustandsänderung): berechnet den `ChainPlan` für
   * `quantity` Einheiten unter dem AKTUELLEN Lagerbestand, ohne einen
   * Auftrag anzulegen – für die "wird berechnet"-Prognose im Formular für
   * neue Aufträge und beim Aufklappen eines noch wartenden (nicht
   * laufenden) Warteschlangeneintrags, dessen `plan`-Feld bis zum
   * tatsächlichen Start ein Platzhalter ist.
   */
  async previewProductionChain(colonyId: Id, productTypeId: Id, quantity: number): Promise<ChainPlan> {
    await this.latency();
    if (quantity <= 0) return EMPTY_CHAIN_PLAN;
    return this.planChain(colonyId, productTypeId, quantity, 'b_industry');
  }

  /** "Fortsetzen"-Button: prüft einen angehaltenen Auftrag erneut und startet ihn, falls jetzt ausführbar. */
  async resumeProduction(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const entry = this._productionQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry || entry.status !== 'stopped') return;
    this._productionQueue.update(list => list.map(e => e.id === entryId ? { ...e, status: 'queued', stoppedReasonCode: null } : e));
    this.tryStartNextProductionEntry(colonyId);
    this.persist();
  }

  async cancelProduction(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const entry = this._productionQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry) return;
    this.creditPartialChainProgress(colonyId, entry, qty => this.addToWarehouse(colonyId, entry.productTypeId, qty));
    this._productionQueue.update(list => list.filter(e => e.id !== entryId));
    this.tryStartNextProductionEntry(colonyId);
    this.persist();
  }

  // ==========================================================================
  // Bevölkerung / Geld
  // ==========================================================================

  population(colonyId: Id): Signal<Population | undefined> {
    return computed(() => this._populations().find(p => p.colonyId === colonyId));
  }
  moneySupplyState(planetId: Id): Signal<PopulationMoneySupplyState | undefined> {
    return computed(() => this._moneySupplyStates().find(m => m.planetId === planetId));
  }
  populationWallet(colonyId: Id): Signal<Wallet | undefined> {
    return computed(() => this.findWallet('Population', colonyId));
  }
  transactions(): Signal<Transaction[]> {
    return computed(() => {
      const walletIds = new Set(this._wallets().filter(w => w.ownerType === 'Player' && w.ownerId === this.player()?.id).map(w => w.id));
      return this._transactions().filter(t => (t.fromWalletId && walletIds.has(t.fromWalletId)) || (t.toWalletId && walletIds.has(t.toWalletId))).slice(-200).reverse();
    });
  }

  async transfer(toPlayerName: string, _amount: number): Promise<void> {
    await this.latency();
    throw new Error(`Noch kein anderer Kommandant "${toPlayerName}" erreichbar – Mehrspieler folgt in einer späteren Ausbaustufe.`);
  }

  // ==========================================================================
  // Flotten
  // ==========================================================================

  fleets(): Signal<Fleet[]> {
    return computed(() => this._fleets().filter(f => f.ownerId === this.player()?.id));
  }
  allFleets(): Signal<Fleet[]> {
    return computed(() => this._fleets());
  }
  shipyardQueue(colonyId: Id): Signal<ShipyardQueueEntry[]> {
    return computed(() => this._shipyardQueue().filter(q => q.colonyId === colonyId));
  }

  async queueShip(colonyId: Id, shipProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    const product = findProductType(shipProductTypeId);
    if (product.category !== 'Ship') throw new Error('Kein Schiffstyp.');
    if (this.getBuildingLevel(colonyId, 'b_shipyard') < 1) throw new Error('Ohne Werft können keine Schiffe gebaut werden.');
    const entry: ShipyardQueueEntry = {
      id: nextId('sy'), colonyId, shipProductTypeId, quantity, autoProduceMissing, requeueOnComplete,
      status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
    };
    this._shipyardQueue.update(list => [...list, entry]);
    this.tryStartNextShipyardEntry(colonyId);
    this.persist();
  }

  async resumeShipOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const entry = this._shipyardQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry || entry.status !== 'stopped') return;
    this._shipyardQueue.update(list => list.map(e => e.id === entryId ? { ...e, status: 'queued', stoppedReasonCode: null } : e));
    this.tryStartNextShipyardEntry(colonyId);
    this.persist();
  }

  async cancelShipOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const entry = this._shipyardQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry) return;
    this.creditPartialChainProgress(colonyId, entry, qty => this.addToWarehouse(colonyId, entry.shipProductTypeId, qty));
    this._shipyardQueue.update(list => list.filter(e => e.id !== entryId));
    this.tryStartNextShipyardEntry(colonyId);
    this.persist();
  }

  // ==========================================================================
  // Bodentruppen
  // ==========================================================================

  groundForces(colonyId: Id): Signal<GroundForceGroup | undefined> {
    return computed(() => this._groundForceGroups().find(g => g.colonyId === colonyId));
  }
  recruitmentQueue(colonyId: Id): Signal<RecruitmentQueueEntry[]> {
    return computed(() => this._recruitmentQueue().filter(q => q.colonyId === colonyId));
  }

  async queueRecruitment(colonyId: Id, unitProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    const product = findProductType(unitProductTypeId);
    if (product.category !== 'GroundUnit') throw new Error('Kein Bodentruppen-Typ.');
    if (this.getBuildingLevel(colonyId, 'b_academy') < 1) throw new Error('Ohne Ausbildungszentrum keine Rekrutierung möglich.');
    const stats = this._planetStats().find(s => s.colonyId === colonyId);
    if (!stats || stats.loyaltyPct <= 50) throw new Error('Rekrutierung erfordert eine Loyalität über 50%.');
    const entry: RecruitmentQueueEntry = {
      id: nextId('rq'), colonyId, unitProductTypeId, quantity, autoProduceMissing, requeueOnComplete,
      status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
    };
    this._recruitmentQueue.update(list => [...list, entry]);
    this.tryStartNextRecruitmentEntry(colonyId);
    this.persist();
  }

  async resumeRecruitment(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const entry = this._recruitmentQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry || entry.status !== 'stopped') return;
    this._recruitmentQueue.update(list => list.map(e => e.id === entryId ? { ...e, status: 'queued', stoppedReasonCode: null } : e));
    this.tryStartNextRecruitmentEntry(colonyId);
    this.persist();
  }

  async cancelRecruitment(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    const entry = this._recruitmentQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry) return;
    this.creditPartialChainProgress(colonyId, entry, qty => this.addUnitToGarrison(colonyId, entry.unitProductTypeId, qty));
    this._recruitmentQueue.update(list => list.filter(e => e.id !== entryId));
    this.tryStartNextRecruitmentEntry(colonyId);
    this.persist();
  }

  // ==========================================================================
  // Gateway / Galaxie
  // ==========================================================================

  gateway(systemId: Id): Signal<Gateway | undefined> {
    return computed(() => this._gateways().find(g => g.systemId === systemId));
  }

  gatewayWeights(systemId: Id): Signal<GatewayWeightEntry[]> {
    return computed(() => {
      const player = this.player();
      if (!player) return [];
      const weight = this._colonies()
        .filter(c => c.systemId === systemId && c.ownerId === player.id)
        .reduce((sum, c) => {
          const pop = this._populations().find(p => p.colonyId === c.id)?.currentCount ?? 0;
          const loyalty = this._planetStats().find(s => s.colonyId === c.id)?.loyaltyPct ?? 0;
          return sum + pop * (loyalty / 100);
        }, 0);
      if (weight <= 0) return [];
      return [{ playerId: player.id, playerName: player.name, weight: Math.round(weight) }];
    });
  }

  /** Netzwerktopologie ist öffentlich bekannt (Gateways von Anfang an offen) – ALLE Systeme, unabhängig vom Besuchsstatus. */
  visibleSystems(): Signal<System[]> {
    return computed(() => this._systems());
  }
  system(id: Id): Signal<System | undefined> {
    return computed(() => this._systems().find(s => s.id === id));
  }

  galaxyRoutes(): Signal<{ a: Id; b: Id }[]> {
    return computed(() => {
      const seen = new Set<string>();
      const routes: { a: Id; b: Id }[] = [];
      for (const gateway of this._gateways()) {
        for (const targetId of gateway.reachableSystemIds) {
          const key = [gateway.systemId, targetId].sort().join('|');
          if (seen.has(key)) continue;
          seen.add(key);
          routes.push({ a: gateway.systemId, b: targetId });
        }
      }
      return routes;
    });
  }

  /** Siehe `GameApi.hasVisitedSystem` – gesetzt durch `processFleetArrivals`. */
  hasVisitedSystem(systemId: Id): Signal<boolean> {
    return computed(() => this.knownSystemIdsFor(this.player()?.id).has(systemId));
  }

  // ==========================================================================
  // Handel
  // ==========================================================================

  sellOrders(systemId: Id): Signal<SellOrder[]> {
    return computed(() => this._sellOrders().filter(o => o.systemId === systemId && o.remainingQuantity > 0));
  }

  async createSellOrder(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist = false): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    this.createSellOrderCore(colonyId, productTypeId, quantity, pricePerUnit, autoRelist);
  }

  /** Ungeprüfter Kern von `createSellOrder` – auch von der NPC-KI direkt genutzt (siehe `requireOwnColony`-Doku). */
  private createSellOrderCore(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist = false): void {
    if (quantity <= 0 || pricePerUnit <= 0) throw new Error('Menge und Preis müssen größer als 0 sein.');
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) throw new Error('Unbekannte Kolonie.');
    const stock = this.warehouseQty(colonyId, productTypeId);
    if (stock < quantity) throw new Error('Nicht genug Lagerbestand für diese Order.');
    this.addToWarehouse(colonyId, productTypeId, -quantity);
    const order: SellOrder = {
      id: nextId('so'), systemId: colony.systemId, locationType: 'Depot', depotColonyId: colonyId,
      sellerId: colony.ownerId, sellerName: this.ownerDisplayName(colony.ownerId), productTypeId, quantity, remainingQuantity: quantity,
      pricePerUnit, createdAt: now(), autoRelist, sourceFleetId: null,
    };
    this._sellOrders.update(list => [...list, order]);
    this.persist();
  }

  /**
   * Verkauf direkt aus der Fracht einer eigenen, gerade dort befindlichen
   * Flotte (siehe `GameApi.createSellOrderFromFleet`): gelandet
   * (`locationColonyId` gesetzt) entsteht eine `'Depot'`-Order an DIESER
   * Kolonie – auch wenn sie einem anderen Kommandanten gehört, denn hier
   * verkauft die Flotte selbst, nicht die Kolonie. Ohne Landung entsteht
   * eine `'Station'`-Order am Systemhandelsposten.
   */
  async createSellOrderFromFleet(fleetId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist = false): Promise<void> {
    await this.latency();
    if (quantity <= 0 || pricePerUnit <= 0) throw new Error('Menge und Preis müssen größer als 0 sein.');
    const fleet = this.requireOwnFleet(fleetId);
    if (fleet.status !== 'Stationed') throw new Error('Die Flotte ist unterwegs.');
    const have = fleet.cargo.find(c => c.productTypeId === productTypeId)?.quantity ?? 0;
    if (have < quantity) throw new Error('Nicht genug Fracht an Bord.');
    const player = this.requirePlayer();
    this._fleets.update(list => list.map(f => f.id !== fleetId ? f : {
      ...f,
      cargo: f.cargo.map(c => c.productTypeId === productTypeId ? { ...c, quantity: c.quantity - quantity } : c).filter(c => c.quantity > 0),
    }));
    const order: SellOrder = {
      id: nextId('so'), systemId: fleet.systemId,
      locationType: fleet.locationColonyId ? 'Depot' : 'Station', depotColonyId: fleet.locationColonyId,
      sellerId: player.id, sellerName: player.name, productTypeId, quantity, remainingQuantity: quantity,
      pricePerUnit, createdAt: now(), autoRelist, sourceFleetId: fleetId,
    };
    this._sellOrders.update(list => [...list, order]);
    this.persist();
  }

  async cancelSellOrder(orderId: Id): Promise<void> {
    await this.latency();
    const order = this._sellOrders().find(o => o.id === orderId);
    if (!order) return;
    if (order.sellerId !== this.requirePlayer().id) throw new Error('Diese Order gehört einem anderen Kommandanten.');
    if (order.remainingQuantity > 0) {
      // Aus Flottenfracht entstandene Orders erstatten in die Fracht zurück (falls die Flotte noch existiert – die Ware lag nie in einem Kolonielager); alle anderen ins Kolonielager, in dem sie ursprünglich lagen.
      if (order.sourceFleetId && this._fleets().some(f => f.id === order.sourceFleetId)) {
        const fleetId = order.sourceFleetId;
        this._fleets.update(list => list.map(f => {
          if (f.id !== fleetId) return f;
          const cargo = [...f.cargo];
          const idx = cargo.findIndex(c => c.productTypeId === order.productTypeId);
          if (idx === -1) cargo.push({ productTypeId: order.productTypeId, quantity: order.remainingQuantity });
          else cargo[idx] = { ...cargo[idx], quantity: cargo[idx].quantity + order.remainingQuantity };
          return { ...f, cargo };
        }));
      } else if (order.depotColonyId) {
        this.addToWarehouse(order.depotColonyId, order.productTypeId, order.remainingQuantity);
      }
    }
    this._sellOrders.update(list => list.filter(o => o.id !== orderId));
    this.persist();
  }

  async buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void> {
    await this.latency();
    this.requireOwnColony(deliverToColonyId); // Lieferziel muss eine eigene Kolonie sein, sonst könnte man Ware in eine fremde Kolonie "spenden"
    const order = this._sellOrders().find(o => o.id === orderId);
    if (!order || order.remainingQuantity < quantity) throw new Error('Nicht genug Ware in dieser Order verfügbar.');
    const player = this.requirePlayer();
    const wallet = this.findWallet('Player', player.id);
    const cost = Math.round(quantity * order.pricePerUnit);
    if (!wallet || wallet.balance < cost) throw new Error('Nicht genug Credits.');
    const sellerWallet = this.findWallet('Player', order.sellerId);
    this.settleSellOrderPurchase(order, quantity);
    this.addToWarehouse(deliverToColonyId, order.productTypeId, quantity);
    if (sellerWallet) this.recordTx(wallet.id, sellerWallet.id, cost, 'Trade', `Kauf ${quantity}× am Markt`);
    this.persist();
  }

  /**
   * Zieht `quantity` von einer Verkaufsorder ab. Erreicht `remainingQuantity`
   * dadurch 0 und ist `autoRelist` gesetzt, wird SOFORT versucht, mit dem
   * gerade jetzt vorhandenen Bestand neu aufzulegen (`reserveForRelist`) –
   * z. B. wenn mehr in Lager/Flotte lag, als in dieser einen Order angeboten
   * wurde. Schlägt das fehl, kommt es auf die Quelle an:
   * - KOLONIE (`depotColonyId`, kein `sourceFleetId`): Order bleibt
   *   "schlafend" mit `remainingQuantity: 0` bestehen (unsichtbar für Käufer,
   *   siehe `sellOrders()`), statt gelöscht zu werden – `replenishDormantSellOrders`
   *   versucht sie danach jeden Tick erneut zu befüllen, sobald die laufende
   *   Produktion wieder Bestand nachliefert. Ohne das würde "Wiederkehrend
   *   anbieten" genau dann seinen Zweck verfehlen, wenn das Lager im
   *   Verkaufsmoment gerade leer ist (Produktion und Konsum konkurrieren um
   *   denselben Bestand).
   * - FLOTTE (`sourceFleetId`): Order wird gelöscht, kein Dormant-Warten. Eine
   *   Flotte hat – anders als ein Kolonielager – keine eigene laufende
   *   "Produktion", die ihre Fracht von selbst nachfüllt; sie bekommt neue
   *   Fracht ausschließlich durch eine explizite Spieleraktion (`loadCargo`).
   *   Ließe man auch Flotten-Orders schlafend warten, würde ein SPÄTERES,
   *   fachlich unabhängiges `loadCargo` auf dieselbe Flotte/Ware von der
   *   alten, längst vergessenen Order stillschweigend wieder eingesammelt,
   *   bevor der Spieler selbst eine neue Order damit anlegen kann – genau das
   *   wurde beim Drei-Spieler-Test beobachtet (siehe Git-Historie).
   *
   * Gemeinsam genutzt von `buyFromOrder` und `runConsumption`.
   */
  private settleSellOrderPurchase(order: SellOrder, quantity: number): void {
    const remaining = order.remainingQuantity - quantity;
    if (remaining > 0) {
      this._sellOrders.update(list => list.map(o => o.id === order.id ? { ...o, remainingQuantity: remaining } : o));
      return;
    }
    if (!order.autoRelist) {
      this._sellOrders.update(list => list.filter(o => o.id !== order.id));
      return;
    }
    // Sofortiger Relist-Versuch mit dem GERADE JETZT vorhandenen Bestand – für beide Quellen gleich (z. B.
    // wenn mehr in der Flotte/im Lager liegt, als in dieser einen Order angeboten wurde). Nur das Verhalten
    // BEI FEHLSCHLAG unterscheidet sich, siehe Kommentar bei `replenishDormantSellOrders`.
    const relistQty = this.reserveForRelist(order);
    if (relistQty > 0) {
      this._sellOrders.update(list => list.map(o => o.id === order.id ? { ...o, remainingQuantity: relistQty, createdAt: now() } : o));
      return;
    }
    if (order.depotColonyId && !order.sourceFleetId) {
      this._sellOrders.update(list => list.map(o => o.id === order.id ? { ...o, remainingQuantity: 0 } : o));
    } else {
      this._sellOrders.update(list => list.filter(o => o.id !== order.id));
    }
  }

  /**
   * Versucht jeden Tick, "schlafende" Auto-Relist-Orders (`remainingQuantity
   * === 0`, siehe `settleSellOrderPurchase`) wiederzubefüllen, sobald ihre
   * Kolonie wieder Lagerbestand hat – z. B. nach dem nächsten
   * Fertigstellungs-Tick einer Produktionswarteschlange. Läuft bewusst NACH
   * `runConsumption` im Tick, damit frisch verbrauchter/produzierter Bestand
   * desselben Ticks schon berücksichtigt ist. NUR kolonie-basierte Orders
   * (`depotColonyId`) werden schlafend gehalten, siehe `settleSellOrderPurchase`
   * zur Begründung, warum flottenbasierte Orders das bewusst nicht tun.
   */
  private replenishDormantSellOrders(): void {
    const dormant = this._sellOrders().filter(o => o.remainingQuantity === 0 && o.autoRelist && o.depotColonyId && !o.sourceFleetId);
    for (const order of dormant) {
      const relistQty = this.reserveForRelist(order);
      if (relistQty <= 0) continue;
      this._sellOrders.update(list => list.map(o => o.id === order.id ? { ...o, remainingQuantity: relistQty, createdAt: now() } : o));
    }
  }

  /**
   * Reserviert für ein Auto-Relist bis zu `order.quantity` frisch aus der
   * Quelle, aus der die Order ursprünglich entstand (Flottenfracht bzw.
   * Kolonielager – bei beiden wurde die verkaufte Menge schon bei Anlage der
   * Order dort entnommen, siehe `createSellOrderCore`/`createSellOrderFromFleet`).
   * Liefert die tatsächlich reservierte Menge; `0` bedeutet keine Deckung
   * mehr vorhanden, die Order wird dann nicht neu aufgelegt.
   */
  private reserveForRelist(order: SellOrder): number {
    if (order.sourceFleetId) {
      const fleet = this._fleets().find(f => f.id === order.sourceFleetId);
      if (!fleet || fleet.status !== 'Stationed') return 0;
      const have = fleet.cargo.find(c => c.productTypeId === order.productTypeId)?.quantity ?? 0;
      const qty = Math.min(order.quantity, Math.floor(have));
      if (qty <= 0) return 0;
      this._fleets.update(list => list.map(f => f.id !== fleet.id ? f : {
        ...f,
        cargo: f.cargo.map(c => c.productTypeId === order.productTypeId ? { ...c, quantity: c.quantity - qty } : c).filter(c => c.quantity > 0),
      }));
      return qty;
    }
    if (order.depotColonyId) {
      const have = this.warehouseQty(order.depotColonyId, order.productTypeId);
      const qty = Math.min(order.quantity, Math.floor(have));
      if (qty <= 0) return 0;
      this.addToWarehouse(order.depotColonyId, order.productTypeId, -qty);
      return qty;
    }
    return 0;
  }

  // ==========================================================================
  // NPCs / Universums-Statistik
  // ==========================================================================

  npcs(): Signal<Npc[]> {
    return computed(() => this._npcs());
  }
  npcColony(npcId: Id): Signal<Colony | undefined> {
    return computed(() => this._colonies().find(c => c.ownerId === npcId));
  }
  ownerWallet(ownerId: Id): Signal<Wallet | undefined> {
    return computed(() => this.findWallet('Player', ownerId));
  }
  universeStats(): Signal<UniverseStatSnapshot[]> {
    return computed(() => this._universeStats());
  }

  // ==========================================================================
  // Benachrichtigungen
  // ==========================================================================

  /**
   * `_notifications` ist EINE gemeinsame Liste über die ganze Galaxie (jeder
   * Eintrag hängt an einer `colonyId`) – gefiltert auf die Kolonien des
   * aktuell eingeloggten Kommandanten, damit ein Konto nie die
   * Benachrichtigungen eines anderen zu sehen bekommt.
   */
  private notificationsForActivePlayer(): GameNotification[] {
    const myColonyIds = new Set(this._colonies().filter(c => c.ownerId === this.player()?.id).map(c => c.id));
    return this._notifications().filter(n => n.colonyId === null || myColonyIds.has(n.colonyId));
  }

  notifications(): Signal<GameNotification[]> {
    return computed(() => this.notificationsForActivePlayer());
  }
  unreadNotificationCount(): Signal<number> {
    return computed(() => this.notificationsForActivePlayer().filter(n => !n.read).length);
  }

  async markNotificationRead(id: Id): Promise<void> {
    await this.latency();
    this._notifications.update(list => list.map(n => n.id === id ? { ...n, read: true } : n));
    this.persist();
  }

  async markAllNotificationsRead(): Promise<void> {
    await this.latency();
    const myIds = new Set(this.notificationsForActivePlayer().map(n => n.id));
    this._notifications.update(list => list.map(n => myIds.has(n.id) && !n.read ? { ...n, read: true } : n));
    this.persist();
  }

  /** Neueste zuerst, siehe `GameNotification`. */
  private notify(type: NotificationType, code: number, message: string, colonyId: Id | null = null, link: string | null = null): void {
    const entry: GameNotification = { id: nextId('ntf'), code, type, message, colonyId, createdAt: now(), read: false, link };
    this._notifications.update(list => [entry, ...list]);
  }

  // ==========================================================================
  // Tick-Loop / Hintergrundjobs
  // ==========================================================================

  private runTick(): void {
    // NICHT an einen eingeloggten Nutzer gebunden: die gemeinsame Galaxie
    // (alle Kommandanten + NPCs) simuliert immer weiter, unabhängig davon,
    // wer gerade in diesem Browser-Tab eingeloggt ist oder ob niemand es ist.
    if (this._players().length === 0) return;
    const t = now();
    this.processBuildingCompletions(t);
    this.consumePowerUpkeep();
    this.processDefenseActivations(t);
    this.processFleetArrivals(t);
    this.processBattles(t);
    this.processProductionQueue(t);
    this.processShipyardCompletions(t);
    this.processRecruitmentCompletions(t);
    this.decaySpecializations(t);
    this.payUpkeepAndWages();
    this.runConsumption();
    this.replenishDormantSellOrders();
    this.recalcCoreStats();
    this.growPopulationAndMoneySupply();
    this.runWealthRedistributionIfDue(t);
    this.runNpcAiIfDue(t);
    this.recordStatsSnapshotIfDue(t);
    this.schedulePersistFromTick(t);
  }

  /** Siehe `TICK_PERSIST_INTERVAL_MS`: drosselt nur den tickgetriebenen Persist, nicht die sofortigen Aufrufe an den Kommando-Methoden. */
  private schedulePersistFromTick(t: number): void {
    if (t - this.lastPersistAt < TICK_PERSIST_INTERVAL_MS) return;
    this.persist();
  }

  private processBuildingCompletions(t: number): void {
    this._buildings.update(list => list.map(b => {
      if (b.pendingOrder && b.pendingOrder.completesAt <= t) {
        return { ...b, level: b.pendingOrder.targetLevel, pendingOrder: null };
      }
      return b;
    }));
  }

  private processDefenseActivations(t: number): void {
    this._buildings.update(list => list.map(b => {
      if (b.activationState === 'Activating' && b.activationCompletesAt !== null && b.activationCompletesAt <= t) {
        return { ...b, activationState: 'Active', activationCompletesAt: null };
      }
      return b;
    }));
  }

  /**
   * Ereignisbasiert (wie die Produktionswarteschlangen): einziger
   * Zeitvergleich je unterwegs befindlicher Flotte. Bei Ankunft gilt das
   * erreichte System für den Flottenbesitzer fortan als besucht (siehe
   * `hasVisitedSystem`) – Sichtbarkeit von Kolonien in einem System hängt
   * NICHT mehr an einer Gateway-Aktivierung (die gibt es nicht mehr,
   * Gateways sind von Anfang an offen), sondern daran, ob dort schon einmal
   * eine eigene Flotte war.
   *
   * Ein mehrsprungiger Flug (`pendingHops`) wird hop-für-hop abgearbeitet:
   * nach jedem einzelnen Sprung entscheidet dieser Tick neu, ob es weiter
   * zum nächsten Sprung geht (automatisch, sofern noch Hops ausstehen) oder
   * die Flotte hier als `Stationed` stehen bleibt – das ist die Grundlage
   * dafür, dass ein Flug jederzeit per `cancelFleetMove` unterwegs
   * abgebrochen werden kann (die Flotte fliegt dann nur den bereits
   * laufenden Sprung zu Ende, statt sich mitten in einem Gateway-Übergang
   * aufzulösen) und künftig auch automatisch stoppen könnte, falls ein
   * Gateway auf der Route gesperrt wird.
   */
  private processFleetArrivals(t: number): void {
    const due = this._fleets().filter(f => f.status === 'InTransit' && f.arrivesAt !== null && f.arrivesAt <= t);
    for (const fleet of due) {
      const reachedSystemId = fleet.destinationSystemId!;
      const [nextHop, ...restHops] = fleet.pendingHops;
      this._fleets.update(list => list.map(f => {
        if (f.id !== fleet.id) return f;
        if (nextHop) {
          const departedAt = t;
          const arrivesAt = departedAt + hoursToMs(HOURS_PER_GATEWAY_HOP);
          return { ...f, systemId: reachedSystemId, destinationSystemId: nextHop, pendingHops: restHops, departedAt, arrivesAt };
        }
        return {
          ...f, status: 'Stationed', locationType: 'System', locationColonyId: null, locationPlanetId: null,
          systemId: reachedSystemId, destinationSystemId: null, pendingHops: [], departedAt: null, arrivesAt: null,
        };
      }));
      this._knownSystemIds.update(map => {
        const next = new Map(map);
        next.set(fleet.ownerId, new Set([...(next.get(fleet.ownerId) ?? []), reachedSystemId]));
        return next;
      });
    }
  }

  /**
   * Berechnet einmalig die komplette Produktionskette für `quantity` Einheiten
   * von `productTypeId` (siehe Konzeption/Umsetzungskonzept/
   * 10_Sequentielle_Produktionsauftraege_und_Ereignissystem.md, §2): Bedarf
   * wird in Reihenfolge fallender Produkt-Ebene durch den Rezeptbaum
   * propagiert (jede Ebene ist strikt niedriger als ihre Eltern, also reicht
   * eine einmalige Sortierung statt echter Graph-Traversierung), an jeder
   * Stelle wird zuerst der aktuelle Lagerbestand abgezogen (nur EINMAL
   * verrechnet, auch wenn ein Produkt – "Zwilling" – von mehreren Zweigen
   * gleichzeitig gebraucht wird, weil die Bedarfsmenge vor dem Lagerabgleich
   * vollständig aufsummiert ist) und nur der tatsächliche Fehlbetrag löst
   * weiteren Bedarf bei den eigenen Rezept-Eingängen aus. Das Wurzelprodukt
   * selbst wird NIE aus dem Lager gedeckt (`quantity` bedeutet immer "so
   * viele NEU bauen", nicht "so viele insgesamt vorhalten").
   * `feasible = false` heißt: ohne "automatisch mitproduzieren" nicht
   * ausführbar, weil mindestens ein Nicht-Wurzel-Schritt einen ungedeckten
   * Fehlbetrag hat.
   */
  private planChain(colonyId: Id, productTypeId: Id, quantity: number, facilityTypeId: 'b_industry' | 'b_shipyard' | 'b_academy'): ChainPlan {
    const reachable = new Set<Id>();
    const discover = (pid: Id): void => {
      if (reachable.has(pid)) return;
      reachable.add(pid);
      for (const input of findProductType(pid).recipe) discover(input.inputProductTypeId);
    };
    discover(productTypeId);
    const orderedByTierDesc = [...reachable].sort((a, b) => findProductType(b).tier - findProductType(a).tier);

    const totalDemand = new Map<Id, number>([[productTypeId, quantity]]);
    const rawSteps: ChainPlanStep[] = [];
    for (const pid of orderedByTierDesc) {
      const needed = totalDemand.get(pid) ?? 0;
      if (needed <= 0) continue;
      const product = findProductType(pid);
      const isRoot = pid === productTypeId;
      const fromWarehouse = isRoot ? 0 : Math.min(needed, this.warehouseQty(colonyId, pid));
      const toProduce = needed - fromWarehouse;
      if (toProduce > 0) {
        for (const input of product.recipe) {
          totalDemand.set(input.inputProductTypeId, (totalDemand.get(input.inputProductTypeId) ?? 0) + input.quantity * toProduce);
        }
      }
      const hours = toProduce > 0 ? toProduce * this.computeProductionHours(colonyId, product, facilityTypeId) : 0;
      rawSteps.push({ productTypeId: pid, quantityNeeded: needed, quantityFromWarehouse: fromWarehouse, quantityToProduce: toProduce, hours });
    }
    const steps = rawSteps.reverse(); // Rohstoffe zuerst, Wurzel zuletzt (für die aufklappbare Detailansicht)
    const totalHours = steps.reduce((sum, s) => sum + s.hours, 0);
    const feasible = steps.every((s, i) => i === steps.length - 1 || s.quantityToProduce === 0);
    return { totalHours, steps, feasible };
  }

  /**
   * Schreibt bei Abbruch eines laufenden Auftrags einen anteiligen Teilerfolg
   * gut: pro Kettenschritt wird `floor(quantityToProduce × verstrichener
   * Zeitanteil)` gutgeschrieben (Abrundung – ein zu 90% fertiges Einzelmodul
   * zählt als 0, nicht als 0,9), der bereits aus dem Lager entnommene, aber
   * nicht mehr benötigte Anteil wird zurückerstattet. `creditRoot` bestimmt,
   * wohin der Wurzelschritt-Anteil gebucht wird (Lager bei Produktion, Flotte
   * bei Schiffen, Garnison bei Rekrutierung) – alle anderen Schritte landen
   * immer im Lager. Kein Effekt bei `queued`/`stopped` (dort wurde noch
   * nichts aus dem Lager entnommen). Spezialisierungs-XP gibt es für jeden
   * Schritt anteilig zum tatsächlich gutgeschriebenen `credited` (siehe
   * `registerProducedChain` für den Normalfall bei voller Fertigstellung).
   */
  private creditPartialChainProgress(
    colonyId: Id,
    entry: { status: ProductionQueueStatus; startedAt: number | null; endsAt: number | null; plan: ChainPlan },
    creditRoot: (quantity: number) => void,
  ): void {
    if (entry.status !== 'running' || entry.startedAt === null || entry.endsAt === null) return;
    const elapsedFraction = F.clamp((now() - entry.startedAt) / Math.max(entry.endsAt - entry.startedAt, 1), 0, 1);
    entry.plan.steps.forEach((step, i) => {
      const isRoot = i === entry.plan.steps.length - 1;
      const refund = Math.floor(step.quantityFromWarehouse * (1 - elapsedFraction));
      if (refund > 0) this.addToWarehouse(colonyId, step.productTypeId, refund);
      const credited = Math.floor(step.quantityToProduce * elapsedFraction);
      if (credited > 0) {
        if (isRoot) creditRoot(credited); else this.addToWarehouse(colonyId, step.productTypeId, credited);
      }
      // XP unabhängig von der (abgerundeten) Stückzahl – siehe `registerProduced`: zeitbasiert, damit auch ein
      // abgebrochener Auftrag mit z. B. nur einer Einheit (credited rundet auf 0) die investierte Zeit nicht verliert.
      this.registerProduced(colonyId, step.productTypeId, step.hours * elapsedFraction);
    });
  }

  // --- Produktion: sequentielle Ausführung -----------------------------------

  private tryStartNextProductionEntry(colonyId: Id): void {
    const queue = this._productionQueue().filter(e => e.colonyId === colonyId);
    if (queue.some(e => e.status === 'running')) return;
    const next = queue.find(e => e.status === 'queued');
    if (next) this.startProductionEntry(next);
  }

  private startProductionEntry(entry: ProductionQueueEntry): void {
    const plan = this.planChain(entry.colonyId, entry.productTypeId, entry.quantity, 'b_industry');
    if (!plan.feasible && !entry.autoProduceMissing) {
      this._productionQueue.update(list => list.map(e => e.id === entry.id ? { ...e, plan, status: 'stopped', stoppedReasonCode: NOTIFICATION_CODE_QUEUE_STOPPED } : e));
      this.notify('Problem', NOTIFICATION_CODE_QUEUE_STOPPED, `Produktionswarteschlange angehalten: nicht genug Vorprodukte für "${findProductType(entry.productTypeId).name}" vorhanden.`, entry.colonyId);
      return;
    }
    for (const step of plan.steps) {
      if (step.quantityFromWarehouse > 0) this.addToWarehouse(entry.colonyId, step.productTypeId, -step.quantityFromWarehouse);
    }
    const startedAt = now();
    const endsAt = startedAt + hoursToMs(plan.totalHours);
    this._productionQueue.update(list => list.map(e => e.id === entry.id ? { ...e, plan, status: 'running', startedAt, endsAt } : e));
  }

  private completeProductionEntry(entry: ProductionQueueEntry): void {
    this.addToWarehouse(entry.colonyId, entry.productTypeId, entry.quantity);
    this.registerProducedChain(entry.colonyId, entry.plan);
    if (entry.requeueOnComplete) {
      const fresh: ProductionQueueEntry = {
        id: nextId('pq'), colonyId: entry.colonyId, productTypeId: entry.productTypeId, quantity: entry.quantity,
        autoProduceMissing: entry.autoProduceMissing, requeueOnComplete: true,
        status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
      };
      this._productionQueue.update(list => [...list.filter(e => e.id !== entry.id), fresh]);
    } else {
      this._productionQueue.update(list => list.filter(e => e.id !== entry.id));
    }
    this.tryStartNextProductionEntry(entry.colonyId);
  }

  /** Ereignisbasiert: einziger Zeitvergleich je laufendem Auftrag statt einer Pro-Einheit-Schleife. */
  private processProductionQueue(t: number): void {
    const due = this._productionQueue().filter(e => e.status === 'running' && e.endsAt !== null && e.endsAt <= t);
    for (const entry of due) this.completeProductionEntry(entry);
  }

  // --- Werft: sequentielle Ausführung -----------------------------------------

  private tryStartNextShipyardEntry(colonyId: Id): void {
    const queue = this._shipyardQueue().filter(e => e.colonyId === colonyId);
    if (queue.some(e => e.status === 'running')) return;
    const next = queue.find(e => e.status === 'queued');
    if (next) this.startShipyardEntry(next);
  }

  private startShipyardEntry(entry: ShipyardQueueEntry): void {
    const plan = this.planChain(entry.colonyId, entry.shipProductTypeId, entry.quantity, 'b_shipyard');
    if (!plan.feasible && !entry.autoProduceMissing) {
      this._shipyardQueue.update(list => list.map(e => e.id === entry.id ? { ...e, plan, status: 'stopped', stoppedReasonCode: NOTIFICATION_CODE_QUEUE_STOPPED } : e));
      this.notify('Problem', NOTIFICATION_CODE_QUEUE_STOPPED, `Werft-Warteschlange angehalten: nicht genug Vorprodukte für "${findProductType(entry.shipProductTypeId).name}" vorhanden.`, entry.colonyId);
      return;
    }
    for (const step of plan.steps) {
      if (step.quantityFromWarehouse > 0) this.addToWarehouse(entry.colonyId, step.productTypeId, -step.quantityFromWarehouse);
    }
    const startedAt = now();
    const endsAt = startedAt + hoursToMs(plan.totalHours);
    this._shipyardQueue.update(list => list.map(e => e.id === entry.id ? { ...e, plan, status: 'running', startedAt, endsAt } : e));
  }

  private completeShipyardEntry(entry: ShipyardQueueEntry): void {
    // Fertige Schiffe landen zunächst wie normale Ware im Lager (siehe
    // `transferShipsToFleet`) – KEINE automatische Zuordnung zu einer Flotte
    // mehr, siehe Klassendoku `GameApi.transferShipsToFleet`.
    this.addToWarehouse(entry.colonyId, entry.shipProductTypeId, entry.quantity);
    this.registerProducedChain(entry.colonyId, entry.plan);
    if (entry.requeueOnComplete) {
      const fresh: ShipyardQueueEntry = {
        id: nextId('sy'), colonyId: entry.colonyId, shipProductTypeId: entry.shipProductTypeId, quantity: entry.quantity,
        autoProduceMissing: entry.autoProduceMissing, requeueOnComplete: true,
        status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
      };
      this._shipyardQueue.update(list => [...list.filter(e => e.id !== entry.id), fresh]);
    } else {
      this._shipyardQueue.update(list => list.filter(e => e.id !== entry.id));
    }
    this.tryStartNextShipyardEntry(entry.colonyId);
  }

  private processShipyardCompletions(t: number): void {
    const due = this._shipyardQueue().filter(e => e.status === 'running' && e.endsAt !== null && e.endsAt <= t);
    for (const entry of due) this.completeShipyardEntry(entry);
  }

  // --- Ausbildungszentrum: sequentielle Ausführung ----------------------------

  private tryStartNextRecruitmentEntry(colonyId: Id): void {
    const queue = this._recruitmentQueue().filter(e => e.colonyId === colonyId);
    if (queue.some(e => e.status === 'running')) return;
    const next = queue.find(e => e.status === 'queued');
    if (next) this.startRecruitmentEntry(next);
  }

  private startRecruitmentEntry(entry: RecruitmentQueueEntry): void {
    const plan = this.planChain(entry.colonyId, entry.unitProductTypeId, entry.quantity, 'b_academy');
    if (!plan.feasible && !entry.autoProduceMissing) {
      this._recruitmentQueue.update(list => list.map(e => e.id === entry.id ? { ...e, plan, status: 'stopped', stoppedReasonCode: NOTIFICATION_CODE_QUEUE_STOPPED } : e));
      this.notify('Problem', NOTIFICATION_CODE_QUEUE_STOPPED, `Rekrutierungs-Warteschlange angehalten: nicht genug Vorprodukte für "${findProductType(entry.unitProductTypeId).name}" vorhanden.`, entry.colonyId);
      return;
    }
    for (const step of plan.steps) {
      if (step.quantityFromWarehouse > 0) this.addToWarehouse(entry.colonyId, step.productTypeId, -step.quantityFromWarehouse);
    }
    const startedAt = now();
    const endsAt = startedAt + hoursToMs(plan.totalHours);
    this._recruitmentQueue.update(list => list.map(e => e.id === entry.id ? { ...e, plan, status: 'running', startedAt, endsAt } : e));
  }

  private completeRecruitmentEntry(entry: RecruitmentQueueEntry): void {
    this.addUnitToGarrison(entry.colonyId, entry.unitProductTypeId, entry.quantity);
    this.registerProducedChain(entry.colonyId, entry.plan);
    if (entry.requeueOnComplete) {
      const fresh: RecruitmentQueueEntry = {
        id: nextId('rq'), colonyId: entry.colonyId, unitProductTypeId: entry.unitProductTypeId, quantity: entry.quantity,
        autoProduceMissing: entry.autoProduceMissing, requeueOnComplete: true,
        status: 'queued', stoppedReasonCode: null, plan: EMPTY_CHAIN_PLAN, startedAt: null, endsAt: null,
      };
      this._recruitmentQueue.update(list => [...list.filter(e => e.id !== entry.id), fresh]);
    } else {
      this._recruitmentQueue.update(list => list.filter(e => e.id !== entry.id));
    }
    this.tryStartNextRecruitmentEntry(entry.colonyId);
  }

  private processRecruitmentCompletions(t: number): void {
    const due = this._recruitmentQueue().filter(e => e.status === 'running' && e.endsAt !== null && e.endsAt <= t);
    for (const entry of due) this.completeRecruitmentEntry(entry);
  }

  private decaySpecializations(t: number): void {
    this._specializations.update(list => list.map(s => {
      if (s.currentLevel <= 0) return s;
      const key = `${s.colonyId}:${s.productTypeId}`;
      const last = this.lastProducedAt.get(key) ?? 0;
      if (t - last > SPECIALIZATION_DECAY_GRACE_MS) {
        this.lastProducedAt.set(key, t);
        return { ...s, currentLevel: s.currentLevel - 1 };
      }
      return s;
    }));
  }

  /**
   * Läuft für ALLE Kolonien (Spieler UND NPCs) – jede Kolonie bezahlt
   * Unterhalt und Löhne aus dem Wallet ihres jeweiligen Besitzers, nicht
   * pauschal aus dem Spieler-Wallet. Das macht die Engine "besitzerneutral":
   * Ob eine Colony dem Menschen oder einem NPC gehört, spielt für die
   * Kernwirtschaft keine Rolle (siehe GameApi-Kapselung).
   */
  private payUpkeepAndWages(): void {
    for (const colony of this._colonies()) {
      const ownerWallet = this.findWallet('Player', colony.ownerId);
      const popWallet = this.findWallet('Population', colony.id);
      if (!ownerWallet || !popWallet) continue;
      const buildings = this._buildings().filter(b => b.colonyId === colony.id && b.level > 0);
      const overbuild = this.overbuildFactor(colony.planetId)();
      const buildingUpkeep = buildings.reduce((sum, b) => sum + findBuildingType(b.typeId).upkeepPerLevel * b.level, 0) * overbuild * TICK_GAME_HOURS;
      const fleetUpkeep = this._fleets()
        .filter(f => f.locationColonyId === colony.id)
        .reduce((sum, f) => sum + f.ships.reduce((s, g) => s + g.quantity, 0) * 0.5, 0) * TICK_GAME_HOURS;
      const population = this._populations().find(p => p.colonyId === colony.id)?.currentCount ?? 0;
      const wage = population * 0.02 * TICK_GAME_HOURS;

      for (const [amount, reason, note] of [
        [buildingUpkeep, 'BuildingUpkeep', 'Gebäudeunterhalt'],
        [fleetUpkeep, 'FleetUpkeep', 'Flottenunterhalt'],
        [wage, 'Wage', 'Löhne'],
      ] as const) {
        const available = Math.max(this.findWallet('Player', colony.ownerId)?.balance ?? 0, 0);
        const affordable = Math.min(amount, available);
        if (affordable > 0.001) this.recordTx(ownerWallet.id, popWallet.id, affordable, reason, note);
      }
    }
  }

  private runConsumption(): void {
    for (const colony of this._colonies()) {
      const popWallet = this.findWallet('Population', colony.id);
      const population = this._populations().find(p => p.colonyId === colony.id);
      const stats = this._planetStats().find(s => s.colonyId === colony.id);
      if (!popWallet || !population || !stats) continue;

      const prevN = this.consumptionBudget.get(colony.id) ?? popWallet.balance * 0.1;
      const income = Math.max(popWallet.balance - prevN, 0);
      const n = 0.9 * prevN + 0.1 * income;
      const budget = Math.min(popWallet.balance, Math.max(n, popWallet.balance / 10));
      this.consumptionBudget.set(colony.id, n);

      let remaining = budget;
      let coverageSum = 0;
      let weightSum = 0;
      const coverageByGood: Record<Id, number> = {};
      for (const goodId of CONSUMER_GOODS_ORDER) {
        const need = population.currentCount * CONSUMER_NEED_PER_CAPITA[goodId];
        if (need <= 0) continue;
        const goodBudget = remaining * (1 / CONSUMER_GOODS_ORDER.length);
        const orders = this._sellOrders()
          .filter(o => o.systemId === colony.systemId && o.productTypeId === goodId && o.remainingQuantity > 0)
          .sort((a, b) => a.pricePerUnit - b.pricePerUnit);
        let spend = 0;
        let bought = 0;
        for (const order of orders) {
          if (spend >= goodBudget || bought >= need) break;
          const affordableQty = Math.floor((goodBudget - spend) / order.pricePerUnit);
          const qty = Math.min(affordableQty, order.remainingQuantity, Math.ceil(need - bought));
          if (qty <= 0) continue;
          const cost = qty * order.pricePerUnit;
          const sellerWallet = this.findWallet('Player', order.sellerId);
          this.settleSellOrderPurchase(order, qty);
          if (sellerWallet) this.recordTx(popWallet.id, sellerWallet.id, cost, 'Consumption', `Konsum ${goodId}`);
          spend += cost;
          bought += qty;
        }
        remaining -= spend;
        const coverage = F.clamp(bought / need, 0, 1.5);
        coverageByGood[goodId] = coverage;
        const weight = goodId === 'p_grundnahrung' ? 2 : 1;
        coverageSum += coverage * weight;
        weightSum += weight;
      }
      this._consumptionCoverage.update(map => ({ ...map, [colony.id]: coverageByGood }));
      const prevRaw = this.rawStandardOfLiving.get(colony.id) ?? stats.standardOfLivingPct;
      const newStandard = weightSum > 0 ? (coverageSum / weightSum) * 100 : prevRaw;
      const smoothedRaw = prevRaw * 0.7 + newStandard * 0.3;
      this.rawStandardOfLiving.set(colony.id, smoothedRaw);
      const effective = this.isBlackout(colony.id) ? smoothedRaw * BLACKOUT_STAT_FACTOR : smoothedRaw;
      this._planetStats.update(list => list.map(s => s.colonyId === colony.id ? { ...s, standardOfLivingPct: F.clamp(effective, 0, 200) } : s));
    }
  }

  private recalcCoreStats(): void {
    const t = now();
    this._planetStats.update(list => list.map(stats => {
      const colony = this._colonies().find(c => c.id === stats.colonyId);
      const population = this._populations().find(p => p.colonyId === stats.colonyId)?.currentCount ?? 0;
      if (!colony) return stats;
      const builtCapacity = this.effectiveHousingCapacity(colony.id);
      const garrison = this._groundForceGroups().find(g => g.colonyId === colony.id);
      // Nur aktive (kommandierte) Drohnen tragen zur Sicherheit bei –
      // Soldaten besitzen keine eigene Kampfwirkung, Reserven kämpfen nicht
      // (Mechanik/05_..., §3).
      const garrisonStrength = (garrison?.units ?? [])
        .filter(u => u.unitProductTypeId !== 'p_soldier')
        .reduce((sum, u) => {
          const product = findProductType(u.unitProductTypeId);
          return sum + u.activeCount * F.productionAspect(product.baseWorkforceRequired, product.baseProductionHours);
        }, 0);
      const infra = F.infrastructurePct(builtCapacity, population);
      const rawSecurity = F.securityPct(garrisonStrength, population, stats.loyaltyPct);
      const security = this.isBlackout(colony.id) ? rawSecurity * BLACKOUT_STAT_FACTOR : rawSecurity;
      const loyaltyDelta = F.loyaltyDelta({ isHomeworld: colony.isHomeworld, standardOfLivingPct: stats.standardOfLivingPct, securityPct: security }) * TICK_GAME_HOURS;
      const loyalty = F.clamp(stats.loyaltyPct + loyaltyDelta, 0, 100);
      return { ...stats, infrastructurePct: infra, securityPct: security, loyaltyPct: loyalty, lastRecalculatedAt: t };
    }));
  }

  private growPopulationAndMoneySupply(): void {
    for (const colony of this._colonies()) {
      const population = this._populations().find(p => p.colonyId === colony.id);
      const stats = this._planetStats().find(s => s.colonyId === colony.id);
      const planet = this._planets().find(p => p.id === colony.planetId);
      if (!population || !stats || !planet) continue;
      const capacity = this.effectiveHousingCapacity(colony.id);
      let delta = F.populationGrowthDelta({
        population: population.currentCount, capacity,
        standardOfLivingPct: stats.standardOfLivingPct, securityPct: stats.securityPct,
      }) * TICK_GAME_HOURS;
      // Blackout unterbindet nur Wachstum – Schrumpfung durch Überbevölkerung
      // (negatives Delta) läuft unabhängig davon normal weiter.
      if (delta > 0 && this.isBlackout(colony.id)) delta = 0;
      // Bewusst NICHT gerundet gespeichert: Bei kleinen Deltas pro Tick
      // (< 0,5) würde erneutes Runden vom bereits gerundeten Wert das
      // Wachstum/Schrumpfen dauerhaft "einfrieren", da derselbe Bruchteil
      // jedes Mal wieder zur selben Ganzzahl gerundet würde. Anzeige
      // rundet über die number-Pipe (siehe Templates).
      const newCount = Math.max(0, population.currentCount + delta);
      this._populations.update(list => list.map(p => p.colonyId === colony.id ? { ...p, currentCount: newCount, growthRatePerInterval: delta } : p));

      const moneyState = this._moneySupplyStates().find(m => m.planetId === colony.planetId);
      if (moneyState && newCount > moneyState.historicalPeakPopulation) {
        const growthDelta = newCount - moneyState.historicalPeakPopulation;
        const created = growthDelta * F.CREDITS_PER_NEW_INHABITANT;
        const popWallet = this.findWallet('Population', colony.id);
        if (popWallet) this.recordTx(null, popWallet.id, created, 'MoneyCreation', 'Bevölkerungswachstum über Höchststand');
        this._moneySupplyStates.update(list => list.map(m => m.planetId === colony.planetId
          ? { ...m, historicalPeakPopulation: newCount, lastPopulation: newCount }
          : m));
      } else if (moneyState) {
        this._moneySupplyStates.update(list => list.map(m => m.planetId === colony.planetId ? { ...m, lastPopulation: newCount } : m));
      }
    }
  }

  /** Kein Energienetz (Stufe 0) kann nicht "blackouten" – `coverageRatio` ist dann per Definition 1. */
  private isBlackout(colonyId: Id): boolean {
    return (this._powerStates().find(p => p.colonyId === colonyId)?.coverageRatio ?? 1) < BLACKOUT_THRESHOLD;
  }

  /**
   * Summe der Wohnkapazität aus allen Infrastructure-Gebäuden – der Anteil
   * des Energienetzes wird um `coverageRatio` gemindert (Blackout bei
   * Elerium-Mangel, siehe `consumePowerUpkeep`). Der Wohnkomplex-Anteil
   * bleibt unabhängig davon voll wirksam.
   */
  private effectiveHousingCapacity(colonyId: Id): number {
    const coverage = this._powerStates().find(p => p.colonyId === colonyId)?.coverageRatio ?? 1;
    return this._buildings()
      .filter(b => b.colonyId === colonyId)
      .reduce((sum, b) => {
        const type = findBuildingType(b.typeId);
        if (type.category !== 'Infrastructure') return sum;
        const contribution = b.level * (type.populationCapacityPerLevel ?? 0);
        return sum + (b.typeId === 'b_powergrid' ? contribution * coverage : contribution);
      }, 0);
  }

  /**
   * PowerUpkeepJob (Umsetzungskonzept/01_..., §3): zieht je Kolonie mit
   * aktivem Energienetz laufend Elerium-Zellen aus dem Lager. Reicht der
   * Bestand nicht, sinkt `coverageRatio` – geglättet (wie
   * `consumptionBudget`), damit ein einzelner leerer Tick nicht sofort
   * einen harten Kapazitätssprung auslöst.
   */
  private consumePowerUpkeep(): void {
    const states = this._powerStates();
    const next: ColonyPowerState[] = this._colonies().map(colony => {
      const level = this.getBuildingLevel(colony.id, 'b_powergrid');
      const prevRatio = states.find(p => p.colonyId === colony.id)?.coverageRatio ?? 1;
      if (level <= 0) return { colonyId: colony.id, coverageRatio: 1 };
      const need = level * ELERIUM_UPKEEP_PER_POWERGRID_LEVEL * TICK_GAME_HOURS;
      const stock = this.warehouseQty(colony.id, POWERGRID_FUEL_PRODUCT_ID);
      const covered = Math.min(need, stock);
      if (covered > 0) this.addToWarehouse(colony.id, POWERGRID_FUEL_PRODUCT_ID, -covered);
      const instantRatio = need > 0 ? covered / need : 1;
      return { colonyId: colony.id, coverageRatio: prevRatio * 0.8 + instantRatio * 0.2 };
    });
    this._powerStates.set(next);
  }

  /**
   * Ausgleichsfonds gegen Geldhortung, siehe Konzeption/Spieldesign/06_...,
   * §8 und Mechanik/10_..., §7: einmal pro Spieltag zahlen große
   * Spieler-/Kommandanten-Wallets (Mensch wie NPC) 0,1 % ihres Guthabens
   * und jede Kolonie 1 % ihres Bevölkerungs-Wallets in einen galaxieweiten
   * Topf ein. Der Topf wird im selben Lauf komplett pro Kopf an alle
   * Bevölkerungs-Wallets zurückverteilt – kein Money Sink, reine
   * Umverteilung, bewusst nicht an Spieler-Wallets (sonst würde sie
   * Kommandanten-Hortung indirekt wieder belohnen).
   */
  private runWealthRedistributionIfDue(t: number): void {
    if (t - this.lastWealthRedistributionAt < GAME_DAY_MS) return;
    this.lastWealthRedistributionAt = t;

    let pot = 0;
    for (const wallet of this._wallets()) {
      if (wallet.ownerType !== 'Player' || wallet.balance <= WEALTH_TAX_THRESHOLD) continue;
      const tax = wallet.balance * WEALTH_TAX_RATE;
      if (tax <= 0.001) continue;
      this.recordTx(wallet.id, null, tax, 'Tax', 'Vermögenssteuer (Ausgleichsfonds)');
      pot += tax;
    }
    for (const colony of this._colonies()) {
      const popWallet = this.findWallet('Population', colony.id);
      if (!popWallet || popWallet.balance <= 0) continue;
      const tax = popWallet.balance * COLONY_TAX_RATE;
      if (tax <= 0.001) continue;
      this.recordTx(popWallet.id, null, tax, 'Tax', 'Kolonialabgabe (Ausgleichsfonds)');
      pot += tax;
    }
    if (pot <= 0.001) return;

    const totalPopulation = this._populations().reduce((sum, p) => sum + p.currentCount, 0);
    if (totalPopulation <= 0) return;
    const perCapita = pot / totalPopulation;
    for (const population of this._populations()) {
      const popWallet = this.findWallet('Population', population.colonyId);
      if (!popWallet) continue;
      const share = population.currentCount * perCapita;
      if (share <= 0.001) continue;
      this.recordTx(null, popWallet.id, share, 'Subsidy', 'Ausgleichsfonds-Ausschüttung');
    }
  }

  // ==========================================================================
  // NPC-KI und Universums-Statistik
  // ==========================================================================

  private runNpcAiIfDue(t: number): void {
    if (t - this.lastNpcAiAt < NPC_AI_INTERVAL_MS) return;
    this.lastNpcAiAt = t;
    for (const npc of this._npcs()) this.runNpcAiTick(npc);
  }

  /**
   * Bewusst einfache, nicht-kriegerische KI (siehe Auftrag: NPCs
   * verhalten sich wie Spieler, bauen aber keine Angriffsflotten oder
   * Bodentruppen): baut nur Infrastruktur/Industrie aus, hält eine
   * Grundnahrungsmittelproduktion am Laufen, produziert ihr
   * Spezialprodukt (meist die Rohstoffkonzentration ihres Planeten) und
   * verkauft Überschüsse lokal. Rührt Werft, Ausbildungszentrum,
   * Verteidigung, Schiffe und Bodentruppen nie an.
   */
  private runNpcAiTick(npc: Npc): void {
    const colony = this._colonies().find(c => c.id === npc.homeColonyId);
    if (!colony) return;
    const wallet = this.findWallet('Player', npc.id);
    if (!wallet) return;

    this.npcMaybeUpgradeInfrastructure(colony.id, wallet.balance);
    this.npcMaybeUpgradeIndustry(colony.id, wallet.balance);
    this.npcMaybeQueueFoodChain(colony.id);
    this.npcMaybeQueueSpecialty(colony.id, npc.specialtyProductId);
    this.npcMaybeSellSurplus(colony, npc);
  }

  private npcMaybeUpgradeInfrastructure(colonyId: Id, balance: number): void {
    const stats = this._planetStats().find(s => s.colonyId === colonyId);
    if (!stats || stats.infrastructurePct >= 95) return;
    for (const typeId of ['b_habitat', 'b_powergrid']) {
      const building = this._buildings().find(b => b.colonyId === colonyId && b.typeId === typeId);
      if (building?.pendingOrder) continue;
      const type = findBuildingType(typeId);
      const fromLevel = building?.level ?? 0;
      if (fromLevel >= type.maxLevel) continue;
      const cost = F.buildingUpgradeCost(type.baseCostPerLevel, fromLevel);
      if (balance < cost * 1.4) continue;
      try { this.queueBuildingCore(colonyId, typeId); } catch { /* diesen KI-Durchlauf überspringen, nächster Tick versucht es erneut */ }
      return; // ein Ausbau pro KI-Durchlauf reicht
    }
  }

  private npcMaybeUpgradeIndustry(colonyId: Id, balance: number): void {
    const building = this._buildings().find(b => b.colonyId === colonyId && b.typeId === 'b_industry');
    if (building?.pendingOrder) return;
    const fromLevel = building?.level ?? 0;
    if (fromLevel >= 6) return; // NPCs wachsen bewusst begrenzt – Stresstest, kein Wettrüsten
    const type = findBuildingType('b_industry');
    const cost = F.buildingUpgradeCost(type.baseCostPerLevel, fromLevel);
    if (balance < cost * 2) return;
    try { this.queueBuildingCore(colonyId, 'b_industry'); } catch { /* diesen KI-Durchlauf überspringen, nächster Tick versucht es erneut */ }
  }

  /**
   * NPCs stellen genau wie Spieler EINEN Auftrag mit "automatisch
   * mitproduzieren" + "nach Erfolg erneut einreihen" – `planChain`
   * übernimmt die komplette, ggf. mehrstufige Kette in einer Berechnung
   * (siehe Konzeption/Umsetzungskonzept/10_...md, §8). Ersetzt die frühere,
   * eigene rekursive "eine Kettenschicht pro KI-Tick"-Logik, die jetzt
   * überflüssig ist.
   */
  private npcMaybeQueueFoodChain(colonyId: Id): void {
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) return;
    if (this._productionQueue().some(q => q.colonyId === colonyId && q.productTypeId === FOOD_TARGET_PRODUCT_ID)) return;
    try { this.queueProductionCore(colonyId, FOOD_TARGET_PRODUCT_ID, FOOD_TARGET_STOCK, true, true); } catch { /* nächster Tick versucht es erneut */ }
  }

  private npcMaybeQueueSpecialty(colonyId: Id, productId: Id): void {
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) return;
    if (this._productionQueue().some(q => q.colonyId === colonyId && q.productTypeId === productId)) return;
    const stock = this.warehouseQty(colonyId, productId);
    if (stock >= 60) return;
    try { this.queueProductionCore(colonyId, productId, 15, true, true); } catch { /* nächster Tick versucht es erneut */ }
  }

  /**
   * WICHTIG: Die Kolonialbevölkerung kann NIE direkt aus dem eigenen
   * Lager konsumieren, sondern ausschließlich über Marktorders kaufen
   * (siehe runConsumption/reachableSellOrders) – eine ungelistete
   * "Reserve" ist für die eigene Bevölkerung unerreichbar und verhungert
   * dadurch faktisch. Für Nahrung wird deshalb nur ein minimaler Puffer
   * zurückgehalten und der Bestand aktiv nachgelistet, sobald der Markt
   * leerläuft (statt wie zuvor: Order nur erneuern, wenn GAR keine mehr
   * offen ist – das ließ Bestände oberhalb der Reserve unverkauft
   * liegen, siehe Stresstest-Befund).
   */
  private npcMaybeSellSurplus(colony: Colony, npc: Npc): void {
    for (const productId of [npc.specialtyProductId, FOOD_TARGET_PRODUCT_ID]) {
      const isFood = productId === FOOD_TARGET_PRODUCT_ID;
      const reserve = isFood ? 2 : 10;
      const minListing = isFood ? 3 : 5;
      const stock = this.warehouseQty(colony.id, productId);
      const surplus = Math.floor(stock - reserve);
      if (surplus < minListing) continue;
      const listedRemaining = this._sellOrders()
        .filter(o => o.depotColonyId === colony.id && o.productTypeId === productId && o.remainingQuantity > 0)
        .reduce((sum, o) => sum + o.remainingQuantity, 0);
      if (listedRemaining >= minListing) continue; // Markt noch ausreichend versorgt
      const product = findProductType(productId);
      const basePrice = 3 + product.tier * 2.5;
      const price = Math.round(basePrice * (0.85 + this.npcPriceJitter(npc.id) * 0.3) * 100) / 100;
      try { this.createSellOrderCore(colony.id, productId, surplus, price, true); } catch { /* nächster Tick versucht es erneut */ }
    }
  }

  /** Deterministischer Pseudo-Zufallswert [0,1) je NPC – nur für Preis-Streuung, kein Spielzustand. */
  private npcPriceJitter(npcId: Id): number {
    let hash = 0;
    for (let i = 0; i < npcId.length; i++) hash = (hash * 31 + npcId.charCodeAt(i)) & 0xffffffff;
    return (Math.abs(hash) % 1000) / 1000;
  }

  /**
   * Zeichnet periodisch einen aggregierten Messpunkt über die gesamte
   * Galaxie auf (Spieler + alle NPCs) – Grundlage für die
   * "Statistiken"-Ansicht, mit der sich die Wirtschafts-/
   * Bevölkerungsstabilität über die Zeit beobachten lässt.
   */
  private recordStatsSnapshotIfDue(t: number): void {
    if (t - this.lastStatsSnapshotAt < STATS_SNAPSHOT_INTERVAL_MS) return;
    this.lastStatsSnapshotAt = t;
    const colonies = this._colonies();
    if (colonies.length === 0) return;
    const stats = this._planetStats();
    const populations = this._populations();
    const wallets = this._wallets();

    const avg = (sel: (s: PlanetStats) => number) => stats.length ? stats.reduce((sum, s) => sum + sel(s), 0) / stats.length : 0;

    const snapshot: UniverseStatSnapshot = {
      at: t,
      colonyCount: colonies.length,
      strugglingColonyCount: stats.filter(s => s.standardOfLivingPct < 30 || s.loyaltyPct < 20).length,
      totalPopulation: populations.reduce((sum, p) => sum + p.currentCount, 0),
      totalCredits: Math.round(wallets.reduce((sum, w) => sum + w.balance, 0)),
      avgInfrastructurePct: avg(s => s.infrastructurePct),
      avgSecurityPct: avg(s => s.securityPct),
      avgStandardOfLivingPct: avg(s => s.standardOfLivingPct),
      avgLoyaltyPct: avg(s => s.loyaltyPct),
      openSellOrderCount: this._sellOrders().filter(o => o.remainingQuantity > 0).length,
    };
    this._universeStats.update(list => {
      const next = [...list, snapshot];
      return next.length > STATS_HISTORY_LIMIT ? next.slice(next.length - STATS_HISTORY_LIMIT) : next;
    });
  }

  // ==========================================================================
  // Hilfsfunktionen
  // ==========================================================================

  private requirePlayer(): Player {
    const p = this.player();
    if (!p) throw new Error('Kein aktiver Kommandant.');
    return p;
  }

  /** Besitzer-ID einer Colony (Spieler oder NPC) – finanzielle Aktionen hängen am Besitzer, nicht am Menschen. */
  private requireColonyOwner(colonyId: Id): Id {
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) throw new Error('Unbekannte Kolonie.');
    return colony.ownerId;
  }

  /**
   * Autorisierungsgrenze für UI-ausgelöste, kolonie-gebundene Befehle: seit
   * mehrere echte Kommandanten dieselbe Galaxie teilen (siehe
   * `_players`/`registerPlayer`), reicht "Kolonie existiert" allein nicht
   * mehr – ohne diese Prüfung könnte jeder eingeloggte Kommandant über die
   * URL einer fremden Kolonie (z. B. `/planeten/<fremde-id>`) dort Gebäude
   * ausbauen, Produktion einreihen usw. NUR für vom aktiven Menschen direkt
   * ausgelöste Befehle gedacht – die NPC-KI ruft für ihre eigenen Kolonien
   * bewusst die ungeprüften `...Core`-Varianten auf (siehe z. B.
   * `queueBuildingCore`), da dort kein "eingeloggter Kommandant" existiert.
   */
  private requireOwnColony(colonyId: Id): Colony {
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) throw new Error('Unbekannte Kolonie.');
    const player = this.requirePlayer();
    if (colony.ownerId !== player.id) throw new Error('Diese Kolonie gehört einem anderen Kommandanten.');
    return colony;
  }

  /** Löst JEDEN Besitzer auf (nicht nur den aktuell eingeloggten Kommandanten) – z. B. für Verkäufernamen am gemeinsamen Systemmarkt. */
  private ownerDisplayName(ownerId: Id): string {
    return this._players().find(p => p.id === ownerId)?.name
      ?? this._npcs().find(n => n.id === ownerId)?.name
      ?? 'Unbekannt';
  }

  private findWallet(ownerType: 'Player' | 'Population', ownerId: Id): Wallet | undefined {
    return this._wallets().find(w => w.ownerType === ownerType && w.ownerId === ownerId);
  }

  private popWalletIdForColony(colonyId: Id): Id | null {
    return this.findWallet('Population', colonyId)?.id ?? null;
  }

  private homeworldPopulationWalletId(): Id | null {
    const player = this.player();
    if (!player) return null;
    return this.popWalletIdForColony(player.homeworldColonyId);
  }

  private recordTx(fromWalletId: Id | null, toWalletId: Id | null, amount: number, reason: TransactionReason, note?: string): void {
    if (amount <= 0) return;
    this._wallets.update(list => list.map(w => {
      if (fromWalletId && w.id === fromWalletId) return { ...w, balance: w.balance - amount };
      if (toWalletId && w.id === toWalletId) return { ...w, balance: w.balance + amount };
      return w;
    }));
    this._transactions.update(list => {
      const next = [...list, { id: nextId('tx'), fromWalletId, toWalletId, amount: Math.round(amount * 100) / 100, reason, at: now(), note }];
      return next.length > 800 ? next.slice(next.length - 800) : next;
    });
  }

  private getBuildingLevel(colonyId: Id, buildingTypeId: Id): number {
    return this._buildings().find(b => b.colonyId === colonyId && b.typeId === buildingTypeId)?.level ?? 0;
  }

  private addToWarehouse(colonyId: Id, productTypeId: Id, delta: number): void {
    const key = this.warehouseKey(colonyId, productTypeId);
    this._warehouse.update(list => {
      const idx = list.findIndex(w => w.colonyId === colonyId && w.productTypeId === productTypeId);
      if (idx === -1) {
        if (delta <= 0) return list;
        this.warehouseIndex.set(key, delta);
        return [...list, { colonyId, productTypeId, quantity: delta }];
      }
      const quantity = Math.max(0, list[idx].quantity + delta);
      this.warehouseIndex.set(key, quantity);
      const next = [...list];
      next[idx] = { ...next[idx], quantity };
      return next;
    });
  }

  /** Produktionszeit EINER Einheit in Spielstunden, zu den aktuellen Geschwindigkeitsfaktoren der Kolonie. Kern von `planChain`. */
  private computeProductionHours(colonyId: Id, product: ProductType, facilityTypeId: 'b_industry' | 'b_shipyard' | 'b_academy'): number {
    const population = this._populations().find(p => p.colonyId === colonyId)?.currentCount ?? 100;
    const level = this.getBuildingLevel(colonyId, facilityTypeId);
    // Soldaten sind laut Mechanik/05_..., §5 "nicht durch Produktspezialisierung
    // effizienter machbar" – im Unterschied zu allen anderen Produkten.
    const isSoldier = product.id === 'p_soldier';
    const spec = isSoldier ? 0 : this._specializations().find(s => s.colonyId === colonyId && s.productTypeId === product.id)?.currentLevel ?? 0;
    let concFactor = 1;
    if (product.tier === 0 && product.resourceProfile.length > 0) {
      const colony = this._colonies().find(c => c.id === colonyId);
      const planet = colony ? this._planets().find(p => p.id === colony.planetId) : undefined;
      const resId = product.resourceProfile[0].resourceTypeId;
      const conc = planet?.resourceConcentration.find(c => c.resourceTypeId === resId)?.concentration ?? 50;
      concFactor = F.resourceConcentrationFactor(conc);
    }
    const blackoutFactor = this.isBlackout(colonyId) ? BLACKOUT_PRODUCTION_FACTOR : 1;
    const speed = F.workforceFactor(population) * F.buildingLevelSpeedFactor(level) * F.specializationSpeedFactor(spec) * concFactor * blackoutFactor;
    return product.baseProductionHours / Math.max(speed, 0.05);
  }

  /**
   * `hours` = tatsächlich aufgewendete Produktionszeit (Spielstunden), NICHT
   * die produzierte Stückzahl – XP bemisst sich an investierter Zeit (siehe
   * `specializationThresholdHours`), damit ein aufwendiges Produkt (viele
   * Stunden/Einheit) nicht gegenüber einem schnellen Massenprodukt
   * benachteiligt wird.
   */
  private registerProduced(colonyId: Id, productTypeId: Id, hours: number): void {
    // Soldaten sind laut Mechanik/05_..., §5 nicht spezialisierbar.
    if (productTypeId === 'p_soldier' || hours <= 0) return;
    const key = `${colonyId}:${productTypeId}`;
    this.lastProducedAt.set(key, now());
    const existing = this._specializations().find(s => s.colonyId === colonyId && s.productTypeId === productTypeId);
    let level = existing?.currentLevel ?? 0;
    let experience = (existing?.experience ?? 0) + hours;
    let threshold = existing?.thresholdForNextLevel ?? F.specializationThresholdHours(0);
    while (experience >= threshold) {
      level += 1;
      experience -= threshold;
      threshold = F.specializationThresholdHours(level);
    }
    if (!existing) {
      this._specializations.update(list => [...list, { colonyId, productTypeId, currentLevel: level, experience, thresholdForNextLevel: threshold }]);
    } else {
      this._specializations.update(list => list.map(s => s.colonyId === colonyId && s.productTypeId === productTypeId
        ? { ...s, currentLevel: level, experience, thresholdForNextLevel: threshold }
        : s));
    }
  }

  /**
   * Vergibt Spezialisierungs-XP für JEDEN Schritt der Kette, der tatsächlich
   * produziert wurde (`step.hours`) – nicht nur fürs Wurzelprodukt. Sonst
   * blieben automatisch mitproduzierte Vorprodukte (mangels Lagerbestand
   * gebaut, siehe `planChain`/`autoProduceMissing`) für immer auf Stufe 0,
   * obwohl tatsächlich Produktionszeit für sie aufgewendet wurde.
   */
  private registerProducedChain(colonyId: Id, plan: ChainPlan): void {
    for (const step of plan.steps) this.registerProduced(colonyId, step.productTypeId, step.hours);
  }

  private requireOwnFleet(fleetId: Id): Fleet {
    const fleet = this._fleets().find(f => f.id === fleetId);
    if (!fleet) throw new Error('Unbekannte Flotte.');
    const player = this.requirePlayer();
    if (fleet.ownerId !== player.id) throw new Error('Diese Flotte gehört einem anderen Kommandanten.');
    return fleet;
  }

  private fleetCargoCapacity(fleet: Fleet): { massKg: number; volumeM3: number } {
    return fleet.ships.reduce((acc, g) => {
      const def = findShipDef(g.shipProductTypeId);
      return { massKg: acc.massKg + def.cargoMassKg * g.quantity, volumeM3: acc.volumeM3 + def.cargoVolumeM3 * g.quantity };
    }, { massKg: 0, volumeM3: 0 });
  }
  private fleetCargoUsed(fleet: Fleet): { massKg: number; volumeM3: number } {
    return fleet.cargo.reduce((acc, c) => {
      const product = findProductType(c.productTypeId);
      return { massKg: acc.massKg + product.massKg * c.quantity, volumeM3: acc.volumeM3 + product.volumeM3 * c.quantity };
    }, { massKg: 0, volumeM3: 0 });
  }

  async transferShipsToFleet(colonyId: Id, shipProductTypeId: Id, quantity: number, targetFleetId: Id | null): Promise<void> {
    await this.latency();
    this.requireOwnColony(colonyId);
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    const stock = this.warehouseQty(colonyId, shipProductTypeId);
    if (stock < quantity) throw new Error('Nicht genug Schiffe im Lager.');
    const colony = this._colonies().find(c => c.id === colonyId)!;
    let fleetId = targetFleetId;
    if (fleetId) {
      const target = this._fleets().find(f => f.id === fleetId);
      if (!target || target.ownerId !== colony.ownerId) throw new Error('Unbekannte eigene Flotte.');
      if (target.status !== 'Stationed' || target.locationColonyId !== colonyId) throw new Error('Die Flotte muss bei dieser Kolonie stationiert sein.');
    } else {
      const newFleet: Fleet = {
        id: nextId('flt'), ownerId: colony.ownerId, name: `Flotte ${colony.name} ${this._fleets().filter(f => f.ownerId === colony.ownerId).length + 1}`,
        locationType: 'ColonyOrbit', locationColonyId: colonyId, locationPlanetId: null, systemId: colony.systemId,
        status: 'Stationed', ships: [], cargo: [], destinationSystemId: null, pendingHops: [], departedAt: null, arrivesAt: null,
      };
      this._fleets.update(list => [...list, newFleet]);
      fleetId = newFleet.id;
    }
    this.addToWarehouse(colonyId, shipProductTypeId, -quantity);
    this._fleets.update(list => list.map(f => {
      if (f.id !== fleetId) return f;
      const ships = [...f.ships];
      const idx = ships.findIndex(g => g.shipProductTypeId === shipProductTypeId);
      if (idx === -1) ships.push({ shipProductTypeId, quantity });
      else ships[idx] = { ...ships[idx], quantity: ships[idx].quantity + quantity };
      return { ...f, ships };
    }));
    this.persist();
  }

  async loadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void> {
    await this.latency();
    const fleet = this.requireOwnFleet(fleetId);
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    if (fleet.status !== 'Stationed' || !fleet.locationColonyId) throw new Error('Die Flotte muss bei einer Kolonie gelandet sein.');
    this.requireOwnColony(fleet.locationColonyId); // nur aus dem Lager der EIGENEN Kolonie ladbar
    const stock = this.warehouseQty(fleet.locationColonyId, productTypeId);
    if (stock < quantity) throw new Error('Nicht genug Lagerbestand.');
    const product = findProductType(productTypeId);
    const capacity = this.fleetCargoCapacity(fleet);
    const used = this.fleetCargoUsed(fleet);
    if (used.massKg + product.massKg * quantity > capacity.massKg + 1e-6) throw new Error('Massekapazität der Flotte reicht nicht aus.');
    if (used.volumeM3 + product.volumeM3 * quantity > capacity.volumeM3 + 1e-6) throw new Error('Volumenkapazität der Flotte reicht nicht aus.');
    this.addToWarehouse(fleet.locationColonyId, productTypeId, -quantity);
    this._fleets.update(list => list.map(f => {
      if (f.id !== fleetId) return f;
      const cargo = [...f.cargo];
      const idx = cargo.findIndex(c => c.productTypeId === productTypeId);
      if (idx === -1) cargo.push({ productTypeId, quantity });
      else cargo[idx] = { ...cargo[idx], quantity: cargo[idx].quantity + quantity };
      return { ...f, cargo };
    }));
    this.persist();
  }

  async unloadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void> {
    await this.latency();
    const fleet = this.requireOwnFleet(fleetId);
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    if (fleet.status !== 'Stationed' || !fleet.locationColonyId) throw new Error('Die Flotte muss bei einer Kolonie gelandet sein.');
    this.requireOwnColony(fleet.locationColonyId);
    const have = fleet.cargo.find(c => c.productTypeId === productTypeId)?.quantity ?? 0;
    if (have < quantity) throw new Error('Nicht genug Fracht an Bord.');
    this._fleets.update(list => list.map(f => f.id !== fleetId ? f : {
      ...f,
      cargo: f.cargo.map(c => c.productTypeId === productTypeId ? { ...c, quantity: c.quantity - quantity } : c).filter(c => c.quantity > 0),
    }));
    this.addToWarehouse(fleet.locationColonyId, productTypeId, quantity);
    this.persist();
  }

  private gatewayRoutes(): { a: Id; b: Id }[] {
    return this._gateways().flatMap(g => g.reachableSystemIds.map(targetId => ({ a: g.systemId, b: targetId })));
  }

  /**
   * Löst die Reise in einzelne Gateway-Sprünge auf (`bfsPath`) statt sie als
   * einen einzigen, nicht unterbrechbaren Direktsprung zu behandeln: nur der
   * ERSTE Sprung wird sofort gestartet (`destinationSystemId`), der Rest
   * landet in `pendingHops` und wird von `processFleetArrivals` nach und
   * nach automatisch angeschlossen – siehe dortige Doku und `cancelFleetMove`.
   */
  async moveFleet(fleetId: Id, destinationSystemId: Id): Promise<void> {
    await this.latency();
    const fleet = this.requireOwnFleet(fleetId);
    if (fleet.status !== 'Stationed') throw new Error('Die Flotte ist bereits unterwegs.');
    if (destinationSystemId === fleet.systemId) throw new Error('Die Flotte befindet sich bereits in diesem System.');
    if (!this._systems().some(s => s.id === destinationSystemId)) throw new Error('Unbekanntes Zielsystem.');
    const path = bfsPath(this.gatewayRoutes(), fleet.systemId, destinationSystemId);
    if (!path || path.length === 0) throw new Error('Kein Gateway-Pfad zu diesem System bekannt.');
    const [firstHop, ...pendingHops] = path;
    const departedAt = now();
    const arrivesAt = departedAt + hoursToMs(HOURS_PER_GATEWAY_HOP);
    this._fleets.update(list => list.map(f => f.id === fleetId
      ? { ...f, status: 'InTransit', destinationSystemId: firstHop, pendingHops, departedAt, arrivesAt, locationType: 'System', locationColonyId: null, locationPlanetId: null }
      : f));
    this.persist();
  }

  /**
   * Bricht eine unterwegs befindliche Flotte ab: der bereits laufende
   * Gateway-Sprung wird noch zu Ende geflogen (er lässt sich nicht mitten im
   * Gateway-Übergang rückgängig machen), aber alle weiteren geplanten Sprünge
   * (`pendingHops`) entfallen – die Flotte bleibt am Ende des aktuellen
   * Sprungs stehen, statt zum ursprünglichen Endziel weiterzufliegen. Damit
   * lässt sich ein Flug jederzeit unterbrechen, z. B. weil ein Gateway auf
   * der restlichen Route gesperrt wurde.
   */
  async cancelFleetMove(fleetId: Id): Promise<void> {
    await this.latency();
    const fleet = this.requireOwnFleet(fleetId);
    if (fleet.status !== 'InTransit') throw new Error('Die Flotte ist nicht unterwegs.');
    if (fleet.pendingHops.length === 0) throw new Error('Der letzte Sprung läuft bereits – die Flotte kommt gleich an.');
    this._fleets.update(list => list.map(f => f.id === fleetId ? { ...f, pendingHops: [] } : f));
    this.persist();
  }

  routePreview(fleetId: Id, destinationSystemId: Id): Signal<{ hops: number; ms: number } | null> {
    return computed(() => {
      const fleet = this._fleets().find(f => f.id === fleetId);
      if (!fleet || destinationSystemId === fleet.systemId) return null;
      const hops = bfsHops(this.gatewayRoutes(), fleet.systemId).get(destinationSystemId);
      if (hops === undefined) return null;
      return { hops, ms: hoursToMs(hops * HOURS_PER_GATEWAY_HOP) };
    });
  }

  /**
   * Instant-Bewegung (keine Flugzeit) zwischen den drei Orten desselben
   * Systems, siehe `FleetSystemTarget`/`FleetLocationType` – Grundlage der
   * Systemansicht (`SystemViewComponent`).
   */
  async moveFleetWithinSystem(fleetId: Id, target: FleetSystemTarget): Promise<void> {
    await this.latency();
    const fleet = this.requireOwnFleet(fleetId);
    if (fleet.status !== 'Stationed') throw new Error('Die Flotte ist unterwegs.');
    if (this.activeBattleForFleet(fleetId)) throw new Error('Eine Flotte in einem laufenden Gefecht kann sich nicht bewegen – zuerst zurückziehen.');
    if (target.kind === 'System') {
      this._fleets.update(list => list.map(f => f.id === fleetId ? { ...f, locationType: 'System', locationColonyId: null, locationPlanetId: null } : f));
    } else if (target.kind === 'PlanetOrbit') {
      const planet = this._planets().find(p => p.id === target.planetId);
      if (!planet) throw new Error('Unbekannter Planet.');
      if (planet.systemId !== fleet.systemId) throw new Error('Der Planet liegt nicht in diesem System.');
      this._fleets.update(list => list.map(f => f.id === fleetId ? { ...f, locationType: 'PlanetOrbit', locationColonyId: null, locationPlanetId: target.planetId } : f));
    } else {
      const colony = this._colonies().find(c => c.id === target.colonyId);
      if (!colony) throw new Error('Unbekannte Kolonie.');
      if (colony.systemId !== fleet.systemId) throw new Error('Die Kolonie liegt nicht in diesem System.');
      this._fleets.update(list => list.map(f => f.id === fleetId ? { ...f, locationType: 'ColonyOrbit', locationColonyId: target.colonyId, locationPlanetId: colony.planetId } : f));
    }
    // Ein Ortswechsel hebt eine eigene Blockade an diesem Ort automatisch auf – man kann nicht blockieren, wo man nicht mehr ist.
    this._blockades.update(list => list.filter(b => b.fleetId !== fleetId));
    this.persist();
  }

  // ==========================================================================
  // Blockaden (Mechanik/06_..., stark vereinfacht – siehe `Blockade`-Doku und
  // Klassendoku über `engageBattle`: nur zwei Ankerarten, kein
  // Blockade-Anker-Objekt mit räumlicher Hierarchie, keine
  // Mobilmachungsrampe, kein Expositionslimit, keine Mehrparteien-Blockaden.
  // Eine Blockade macht die sie bildende Flotte angreifbar – siehe
  // `attackableFleetsInSystem`/`engageBattle`.
  // ==========================================================================

  blockadesInSystem(systemId: Id): Signal<Blockade[]> {
    return computed(() => this._blockades().filter(b => b.systemId === systemId));
  }

  /** Errichtet eine Blockade mit der eigenen, an diesem Ort bereits stationierten Flotte – macht sie angreifbar. Höchstens eine Blockade je Anker, höchstens eine Blockade je Flotte. */
  async formBlockade(fleetId: Id, anchor: BlockadeAnchor): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    const fleet = this.requireOwnFleet(fleetId);
    if (fleet.status !== 'Stationed') throw new Error('Die Flotte ist unterwegs.');
    if (!fleet.ships.some(s => s.quantity > 0)) throw new Error('Eine Flotte ohne Schiffe kann keine Blockade bilden.');
    if (this._blockades().some(b => b.fleetId === fleetId)) throw new Error('Diese Flotte blockiert bereits einen Ort.');
    if (anchor.kind === 'Gateway') {
      if (fleet.locationType !== 'System') throw new Error('Für eine Gateway-Blockade muss die Flotte am Systemhandelsposten stehen.');
      if (this._blockades().some(b => b.systemId === fleet.systemId && b.anchorKind === 'Gateway')) throw new Error('Dieses Gateway wird bereits blockiert.');
    } else {
      if (fleet.locationPlanetId !== anchor.planetId || (fleet.locationType !== 'PlanetOrbit' && fleet.locationType !== 'ColonyOrbit')) {
        throw new Error('Die Flotte muss im Orbit dieses Planeten stehen.');
      }
      if (this._blockades().some(b => b.anchorKind === 'PlanetOrbit' && b.planetId === anchor.planetId)) throw new Error('Dieser Planet wird bereits blockiert.');
    }
    const blockade: Blockade = {
      id: nextId('blk'), systemId: fleet.systemId, anchorKind: anchor.kind,
      planetId: anchor.kind === 'PlanetOrbit' ? anchor.planetId : null, fleetId, ownerId: me.id, startedAt: now(),
    };
    this._blockades.update(list => [...list, blockade]);
    this.persist();
  }

  /** Hebt die eigene Blockade wieder auf – nicht möglich während eines laufenden Gefechts der blockierenden Flotte (zuerst zurückziehen). */
  async liftBlockade(blockadeId: Id): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    const blockade = this._blockades().find(b => b.id === blockadeId);
    if (!blockade) throw new Error('Unbekannte Blockade.');
    if (blockade.ownerId !== me.id) throw new Error('Diese Blockade gehört einem anderen Kommandanten.');
    if (this.activeBattleForFleet(blockade.fleetId)) throw new Error('Während eines laufenden Gefechts kann die Blockade nicht aufgehoben werden.');
    this._blockades.update(list => list.filter(b => b.id !== blockadeId));
    this.persist();
  }

  // ==========================================================================
  // Diplomatie (Mechanik/06_..., vereinfacht auf Krieg/Frieden zwischen genau
  // zwei Kommandanten – siehe Klassendoku über `engageBattle` für die
  // bewusst NICHT umgesetzten Teile: Blockaden, Mobilmachung, Mehrparteien-
  // Kriege).
  // ==========================================================================

  /** Kanonische, sortierte Paar-Reihenfolge für `DiplomaticRelation` – EIN Eintrag je Paar, unabhängig davon, wer wen zuerst erwähnt. */
  private relationKey(a: Id, b: Id): [Id, Id] {
    return a < b ? [a, b] : [b, a];
  }

  private findRelation(a: Id, b: Id): DiplomaticRelation | undefined {
    const [x, y] = this.relationKey(a, b);
    return this._diplomaticRelations().find(r => r.playerAId === x && r.playerBId === y);
  }

  diplomaticStatus(otherPlayerId: Id): Signal<DiplomaticStatus> {
    return computed(() => {
      const me = this.player();
      if (!me || me.id === otherPlayerId) return 'Peace';
      return this.findRelation(me.id, otherPlayerId)?.status ?? 'Peace';
    });
  }

  activeWars(): Signal<DiplomaticRelation[]> {
    return computed(() => {
      const meId = this.player()?.id;
      return this._diplomaticRelations().filter(r => r.status === 'War' && (r.playerAId === meId || r.playerBId === meId));
    });
  }

  incomingPeaceOffers(): Signal<PeaceOffer[]> {
    return computed(() => this._peaceOffers().filter(o => o.toPlayerId === this.player()?.id));
  }

  outgoingPeaceOffers(): Signal<PeaceOffer[]> {
    return computed(() => this._peaceOffers().filter(o => o.fromPlayerId === this.player()?.id));
  }

  /** Einseitig – tritt sofort in Kraft, siehe `DiplomaticRelation`. Löscht ein eventuell zwischen beiden noch offenes Friedensangebot (hinfällig). */
  async declareWar(otherPlayerId: Id): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    if (otherPlayerId === me.id) throw new Error('Krieg gegen sich selbst ist nicht möglich.');
    const other = this._players().find(p => p.id === otherPlayerId);
    if (!other) throw new Error('Unbekannter Kommandant.');
    const existing = this.findRelation(me.id, otherPlayerId);
    if (existing?.status === 'War') throw new Error('Sie befinden sich bereits im Krieg mit diesem Kommandanten.');
    const t = now();
    const [a, b] = this.relationKey(me.id, otherPlayerId);
    if (existing) {
      this._diplomaticRelations.update(list => list.map(r => r.id === existing.id ? { ...r, status: 'War' as const, since: t } : r));
    } else {
      this._diplomaticRelations.update(list => [...list, { id: nextId('dip'), playerAId: a, playerBId: b, status: 'War', since: t }]);
    }
    this._peaceOffers.update(list => list.filter(o =>
      !((o.fromPlayerId === me.id && o.toPlayerId === otherPlayerId) || (o.fromPlayerId === otherPlayerId && o.toPlayerId === me.id))));
    this.notify('Warnung', NOTIFICATION_CODE_WAR_DECLARED, `${me.name} hat Ihnen den Krieg erklärt.`, other.homeworldColonyId);
    this.persist();
  }

  /**
   * Legt ein einseitiges Friedensangebot ab – wirksam erst nach
   * `respondToPeaceOffer(accept: true)` durch den Empfänger. Gesperrt
   * während eines laufenden Gefechts zwischen den beiden (siehe `Battle`)
   * und vor Ablauf von `WAR_MIN_DURATION_HOURS` seit Kriegsbeginn.
   */
  async offerPeace(otherPlayerId: Id): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    const other = this._players().find(p => p.id === otherPlayerId);
    if (!other) throw new Error('Unbekannter Kommandant.');
    const relation = this.findRelation(me.id, otherPlayerId);
    if (!relation || relation.status !== 'War') throw new Error('Sie befinden sich nicht im Krieg mit diesem Kommandanten.');
    if (now() - relation.since < hoursToMs(WAR_MIN_DURATION_HOURS)) {
      throw new Error(`Ein Friedensangebot ist erst ${WAR_MIN_DURATION_HOURS} Spielstunden nach Kriegsbeginn möglich.`);
    }
    const hasActiveBattle = this._battles().some(b => b.status === 'Active'
      && ((b.attackerId === me.id && b.defenderId === otherPlayerId) || (b.attackerId === otherPlayerId && b.defenderId === me.id)));
    if (hasActiveBattle) throw new Error('Während eines laufenden Gefechts ist kein Friedensangebot möglich.');
    if (this._peaceOffers().some(o => o.fromPlayerId === me.id && o.toPlayerId === otherPlayerId)) {
      throw new Error('Es liegt bereits ein Friedensangebot an diesen Kommandanten vor.');
    }
    this._peaceOffers.update(list => [...list, { id: nextId('pof'), fromPlayerId: me.id, toPlayerId: otherPlayerId, createdAt: now() }]);
    this.notify('Info', NOTIFICATION_CODE_PEACE_OFFERED, `${me.name} bietet Ihnen Frieden an.`, other.homeworldColonyId);
    this.persist();
  }

  /** Nur der Empfänger (`toPlayerId`) darf antworten. Ablehnen löscht das Angebot ersatzlos, der Krieg läuft weiter. */
  async respondToPeaceOffer(offerId: Id, accept: boolean): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    const offer = this._peaceOffers().find(o => o.id === offerId);
    if (!offer) throw new Error('Unbekanntes Friedensangebot.');
    if (offer.toPlayerId !== me.id) throw new Error('Dieses Friedensangebot richtet sich nicht an Sie.');
    this._peaceOffers.update(list => list.filter(o => o.id !== offerId));
    if (accept) {
      const [a, b] = this.relationKey(offer.fromPlayerId, offer.toPlayerId);
      const t = now();
      this._diplomaticRelations.update(list => list.map(r => (r.playerAId === a && r.playerBId === b) ? { ...r, status: 'Peace' as const, since: t } : r));
      const sender = this._players().find(p => p.id === offer.fromPlayerId);
      this.notify('Info', NOTIFICATION_CODE_PEACE_ACCEPTED, `${me.name} hat Ihr Friedensangebot angenommen.`, sender?.homeworldColonyId ?? null);
    }
    this.persist();
  }

  // ==========================================================================
  // Raumgefechte (Mechanik/04_..., §2-5 – Kernformeln vollständig übernommen).
  //
  // BEWUSSTE VEREINFACHUNG ggü. Mechanik/06_Blockaden_Gefechtsablauf_
  // Aufmarsch.md (dort ein deutlich größeres System): kein Blockade-Anker-
  // Objekt, keine räumliche Hierarchie (Gateway/System/Orbit/Kolonie), keine
  // Mobilmachungsrampe (25/50/100% über mehrere Ticks), kein 10×-
  // Expositionslimit je Tick, keine Mehrparteien-Gefechte, keine
  // Schaden-Redistribution bei Overkill (siehe `applyDamage`). Stattdessen:
  // ein `Battle` ist IMMER strikt 1 Flotte gegen 1 Flotte, ausgelöst durch
  // eine explizite "Angreifen"-Aktion zwischen zwei im selben System
  // stationierten, miteinander im Krieg stehenden Flotten – beide Seiten
  // vollständig exponiert. Bodentruppen (`GroundForceGroup`) nehmen NICHT
  // teil (kein Bodenkampf/keine Invasion in diesem Umsetzungsschritt) – sie
  // wurden zwar bereits an neue Kommandanten ausgegeben (siehe
  // `world-seed.ts`, `starterGroundForceGroup`), ihre Verwendung im Kampf
  // ist als nächster Ausbauschritt vorgesehen.
  // ==========================================================================

  private activeBattleForFleet(fleetId: Id): Battle | undefined {
    return this._battles().find(b => b.status === 'Active' && (b.attackerFleetId === fleetId || b.defenderFleetId === fleetId));
  }

  private shipMilitaryValue(shipProductTypeId: Id): number {
    const product = findProductType(shipProductTypeId);
    return F.productionAspect(product.baseWorkforceRequired, product.baseProductionHours);
  }

  /**
   * Gruppenschaden EINER Seite gegen die andere, verteilt proportional zum
   * Produktionsaufwand-Anteil jedes gegnerischen Schiffstyps (Mechanik/04_...,
   * §3), Kontermultiplikator erst danach je Typenpaar angewendet (§4).
   */
  private computeSideDamage(attackerShips: FleetShipGroup[], defenderShips: FleetShipGroup[]): Record<Id, number> {
    const defenderCostByType = new Map<Id, number>();
    let totalDefenderCost = 0;
    for (const d of defenderShips) {
      if (d.quantity <= 0) continue;
      const cost = this.shipMilitaryValue(d.shipProductTypeId) * d.quantity;
      defenderCostByType.set(d.shipProductTypeId, cost);
      totalDefenderCost += cost;
    }
    const damageByType = new Map<Id, number>();
    if (totalDefenderCost <= 0) return Object.fromEntries(damageByType);
    for (const atk of attackerShips) {
      if (atk.quantity <= 0) continue;
      const atkDef = findShipDef(atk.shipProductTypeId);
      const rawGroupDamage = atk.quantity * this.shipMilitaryValue(atk.shipProductTypeId) * F.COMBAT_DAMAGE_FACTOR;
      for (const [defTypeId, cost] of defenderCostByType) {
        const defDef = findShipDef(defTypeId);
        const share = cost / totalDefenderCost;
        const mult = F.counterMultiplier(atkDef.countersClass === defDef.class, defDef.countersClass === atkDef.class);
        damageByType.set(defTypeId, (damageByType.get(defTypeId) ?? 0) + rawGroupDamage * share * mult);
      }
    }
    return Object.fromEntries(damageByType);
  }

  /**
   * Restschaden-Formel (Mechanik/04_..., §5): `neu = alt + Schaden`,
   * `Verluste = floor(neu / Haltbarkeit)`, `Rest = neu - Verluste × Haltbarkeit`.
   * Verlustzahl auf den tatsächlichen Bestand gedeckelt – überschüssiger
   * Schaden verfällt dabei (keine Overkill-Redistribution auf andere Typen,
   * siehe Klassendoku über `engageBattle`).
   */
  private applyDamage(
    ships: FleetShipGroup[], damageByType: Record<Id, number>, residual: Record<Id, number>,
  ): { ships: FleetShipGroup[]; residual: Record<Id, number>; losses: Record<Id, number> } {
    const losses: Record<Id, number> = {};
    const nextResidual: Record<Id, number> = { ...residual };
    const nextShips = ships.map(s => {
      const dmg = damageByType[s.shipProductTypeId];
      if (!dmg || s.quantity <= 0) return s;
      const durability = this.shipMilitaryValue(s.shipProductTypeId) * F.COMBAT_DURABILITY_FACTOR;
      const total = (nextResidual[s.shipProductTypeId] ?? 0) + dmg;
      const lostCount = Math.min(Math.floor(total / durability), s.quantity);
      nextResidual[s.shipProductTypeId] = lostCount >= s.quantity ? 0 : total - lostCount * durability;
      if (lostCount > 0) losses[s.shipProductTypeId] = lostCount;
      return lostCount > 0 ? { ...s, quantity: s.quantity - lostCount } : s;
    });
    return { ships: nextShips, residual: nextResidual, losses };
  }

  /** Alle noch laufenden Gefechte, die eine Flotte des angemeldeten Kommandanten betreffen (Angreifer ODER Verteidiger). */
  activeBattles(): Signal<Battle[]> {
    return computed(() => {
      const meId = this.player()?.id;
      return this._battles().filter(b => b.status === 'Active' && (b.attackerId === meId || b.defenderId === meId));
    });
  }

  battle(id: Id): Signal<Battle | undefined> {
    return computed(() => this._battles().find(b => b.id === id));
  }

  /** Beendete Gefechte des angemeldeten Kommandanten, neueste zuerst – reines Kampfprotokoll für die UI. */
  battleHistory(): Signal<Battle[]> {
    return computed(() => {
      const meId = this.player()?.id;
      return this._battles()
        .filter(b => b.status === 'Ended' && (b.attackerId === meId || b.defenderId === meId))
        .sort((a, b) => (b.endedAt ?? 0) - (a.endedAt ?? 0));
    });
  }

  /** Absichtlich UNGEFILTERT nach angemeldetem Kommandant – der Kampfbericht ist über den unerratbaren Token teilbar, siehe `Battle.reportToken`. */
  battleByReportToken(token: string): Signal<Battle | undefined> {
    return computed(() => this._battles().find(b => b.reportToken === token));
  }

  /** Eigene, im System stationierte, gegnerische (im Krieg stehende) Flotten MIT AKTIVER BLOCKADE (siehe `Blockade`) – Kandidaten für "Angreifen". Ohne Blockade ist eine Flotte nicht angreifbar. */
  attackableFleetsInSystem(systemId: Id): Signal<Fleet[]> {
    return computed(() => {
      const me = this.player();
      if (!me) return [];
      const atWarWith = new Set(
        this._diplomaticRelations()
          .filter(r => r.status === 'War' && (r.playerAId === me.id || r.playerBId === me.id))
          .map(r => (r.playerAId === me.id ? r.playerBId : r.playerAId)),
      );
      const blockadingFleetIds = new Set(this._blockades().filter(b => b.systemId === systemId).map(b => b.fleetId));
      return this._fleets().filter(f =>
        f.systemId === systemId && f.status === 'Stationed' && f.ownerId !== me.id
        && atWarWith.has(f.ownerId) && f.ships.some(s => s.quantity > 0) && blockadingFleetIds.has(f.id));
    });
  }

  /** Startet ein Gefecht zwischen der eigenen `attackerFleetId` und einer gegnerischen, blockierenden Flotte im selben System. Der Kampfbericht (`reportToken`) ist ab hier sofort abrufbar. */
  async engageBattle(attackerFleetId: Id, defenderFleetId: Id): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    const attackerFleet = this.requireOwnFleet(attackerFleetId);
    const defenderFleet = this._fleets().find(f => f.id === defenderFleetId);
    if (!defenderFleet) throw new Error('Unbekannte Zielflotte.');
    if (defenderFleet.ownerId === me.id) throw new Error('Ein Angriff auf die eigene Flotte ist nicht möglich.');
    if (attackerFleet.status !== 'Stationed' || defenderFleet.status !== 'Stationed') throw new Error('Beide Flotten müssen stationiert sein, keine von ihnen darf unterwegs sein.');
    if (attackerFleet.systemId !== defenderFleet.systemId) throw new Error('Die Flotten befinden sich nicht im selben System.');
    if (this.findRelation(me.id, defenderFleet.ownerId)?.status !== 'War') {
      throw new Error('Ein Angriff ist nur im Krieg möglich – erklären Sie zuerst den Krieg (Diplomatie).');
    }
    if (!this._blockades().some(b => b.fleetId === defenderFleetId)) {
      throw new Error('Diese Flotte hat keine Blockade gebildet und ist daher nicht angreifbar.');
    }
    if (!attackerFleet.ships.some(s => s.quantity > 0) || !defenderFleet.ships.some(s => s.quantity > 0)) {
      throw new Error('Eine der beiden Flotten hat keine Kampfschiffe.');
    }
    if (this.activeBattleForFleet(attackerFleetId)) throw new Error('Ihre Flotte befindet sich bereits in einem laufenden Gefecht.');
    if (this.activeBattleForFleet(defenderFleetId)) throw new Error('Die Zielflotte befindet sich bereits in einem laufenden Gefecht.');
    const t = now();
    const battle: Battle = {
      id: nextId('btl'), reportToken: randomToken(), systemId: attackerFleet.systemId, attackerId: me.id, defenderId: defenderFleet.ownerId,
      attackerFleetId, defenderFleetId, status: 'Active', startedAt: t, nextTickAt: t + hoursToMs(F.COMBAT_TICK_HOURS),
      ticksResolved: 0, attackerResidualDamage: {}, defenderResidualDamage: {}, ticks: [], endedAt: null, outcome: null,
    };
    this._battles.update(list => [...list, battle]);
    const reportLink = '/kampfbericht/' + battle.reportToken;
    const defenderOwner = this._players().find(p => p.id === defenderFleet.ownerId);
    this.notify('Warnung', NOTIFICATION_CODE_BATTLE_STARTED, `${me.name} greift Ihre Flotte "${defenderFleet.name}" an!`, defenderOwner?.homeworldColonyId ?? null, reportLink);
    this.notify('Warnung', NOTIFICATION_CODE_BATTLE_STARTED, `Sie greifen die Flotte "${defenderFleet.name}" an!`, me.homeworldColonyId, reportLink);
    this.persist();
  }

  /**
   * Rückzug: die zurückziehende Seite feuert in diesem letzten Tick NICHT
   * mehr selbst, die Gegenseite aber noch einmal (Mechanik/06_...,
   * "Rückzug" – ein finaler einseitiger Schadens-Tick), danach endet das
   * Gefecht sofort (`outcome: 'Retreat'`) statt regulär auf Vernichtung
   * einer Seite zu warten.
   */
  async retreatFromBattle(battleId: Id): Promise<void> {
    await this.latency();
    const me = this.requirePlayer();
    const battle = this._battles().find(b => b.id === battleId);
    if (!battle) throw new Error('Unbekanntes Gefecht.');
    if (battle.status !== 'Active') throw new Error('Dieses Gefecht ist bereits beendet.');
    if (battle.attackerId !== me.id && battle.defenderId !== me.id) throw new Error('Dieses Gefecht betrifft Sie nicht.');
    this.resolveBattleTick(battle, battle.attackerId === me.id ? 'attacker' : 'defender');
  }

  private processBattles(t: number): void {
    const due = this._battles().filter(b => b.status === 'Active' && b.nextTickAt <= t);
    for (const battle of due) this.resolveBattleTick(battle, null);
  }

  private resolveBattleTick(battle: Battle, retreatingSide: 'attacker' | 'defender' | null): void {
    const attackerFleet = this._fleets().find(f => f.id === battle.attackerFleetId);
    const defenderFleet = this._fleets().find(f => f.id === battle.defenderFleetId);
    if (!attackerFleet || !defenderFleet) {
      this._battles.update(list => list.map(b => b.id === battle.id
        ? { ...b, status: 'Ended' as const, endedAt: now(), outcome: null, attackerResidualDamage: {}, defenderResidualDamage: {} }
        : b));
      this.persist();
      return;
    }

    const damageToDefender = retreatingSide === 'attacker' ? {} : this.computeSideDamage(attackerFleet.ships, defenderFleet.ships);
    const damageToAttacker = retreatingSide === 'defender' ? {} : this.computeSideDamage(defenderFleet.ships, attackerFleet.ships);
    const defApplied = this.applyDamage(defenderFleet.ships, damageToDefender, battle.defenderResidualDamage);
    const atkApplied = this.applyDamage(attackerFleet.ships, damageToAttacker, battle.attackerResidualDamage);

    this._fleets.update(list => list.map(f => {
      if (f.id === attackerFleet.id) return { ...f, ships: atkApplied.ships };
      if (f.id === defenderFleet.id) return { ...f, ships: defApplied.ships };
      return f;
    }));

    const t = now();
    const tickResult: BattleTickResult = {
      tick: battle.ticksResolved + 1, atTime: t,
      attackerShipsBefore: attackerFleet.ships, defenderShipsBefore: defenderFleet.ships,
      attackerLosses: atkApplied.losses, defenderLosses: defApplied.losses,
    };
    const defenderDestroyed = defApplied.ships.every(s => s.quantity <= 0);
    const attackerDestroyed = atkApplied.ships.every(s => s.quantity <= 0);

    let outcome: BattleOutcome | null = null;
    let status: BattleStatus = 'Active';
    if (retreatingSide) {
      outcome = 'Retreat';
      status = 'Ended';
    } else if (attackerDestroyed && defenderDestroyed) {
      // Beide gleichzeitig vernichtet: Verteidiger gilt als erfolgreich verteidigt (Gleichstand-Auflösung).
      outcome = 'DefenderVictory';
      status = 'Ended';
    } else if (defenderDestroyed) {
      outcome = 'AttackerVictory';
      status = 'Ended';
    } else if (attackerDestroyed) {
      outcome = 'DefenderVictory';
      status = 'Ended';
    }

    this._battles.update(list => list.map(b => {
      if (b.id !== battle.id) return b;
      return {
        ...b,
        status,
        ticksResolved: b.ticksResolved + 1,
        nextTickAt: t + hoursToMs(F.COMBAT_TICK_HOURS),
        attackerResidualDamage: status === 'Ended' ? {} : atkApplied.residual,
        defenderResidualDamage: status === 'Ended' ? {} : defApplied.residual,
        ticks: [...b.ticks, tickResult],
        endedAt: status === 'Ended' ? t : null,
        outcome,
      };
    }));

    if (status === 'Ended') {
      const attackerName = this._players().find(p => p.id === battle.attackerId)?.name ?? '?';
      const defenderName = this._players().find(p => p.id === battle.defenderId)?.name ?? '?';
      const summary = outcome === 'AttackerVictory'
        ? `${attackerName} hat die Flotte von ${defenderName} vernichtet.`
        : outcome === 'DefenderVictory'
          ? `${defenderName} hat den Angriff von ${attackerName} abgewehrt.`
          : `${retreatingSide === 'attacker' ? attackerName : defenderName} hat sich aus dem Gefecht zurückgezogen.`;
      const attackerHome = this._players().find(p => p.id === battle.attackerId)?.homeworldColonyId ?? null;
      const defenderHome = this._players().find(p => p.id === battle.defenderId)?.homeworldColonyId ?? null;
      const reportLink = '/kampfbericht/' + battle.reportToken;
      this.notify('Warnung', NOTIFICATION_CODE_BATTLE_ENDED, summary, attackerHome, reportLink);
      this.notify('Warnung', NOTIFICATION_CODE_BATTLE_ENDED, summary, defenderHome, reportLink);
    }
    this.pruneEmptyBlockades();
    this.persist();
  }

  /** Eine Blockade ohne verbliebene Schiffe (Flotte im Kampf vollständig vernichtet) macht keinen Sinn mehr – wird automatisch entfernt. */
  private pruneEmptyBlockades(): void {
    const emptyFleetIds = new Set(this._fleets().filter(f => !f.ships.some(s => s.quantity > 0)).map(f => f.id));
    this._blockades.update(list => list.filter(b => !emptyFleetIds.has(b.fleetId)));
  }

  private addUnitToGarrison(colonyId: Id, unitProductTypeId: Id, count: number): void {
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) return;
    let group = this._groundForceGroups().find(g => g.colonyId === colonyId);
    if (!group) {
      group = { id: nextId('gfg'), ownerId: colony.ownerId, colonyId, units: [] };
      this._groundForceGroups.update(list => [...list, group!]);
    }
    const groupId = group.id;
    // Neu fertiggestellte Einheiten landen zunächst als "unzugeordnet" in
    // reserveCount – recalcCrewing() unten verteilt Soldaten und Drohnen
    // anschließend gemeinsam neu (Mechanik/05_..., §3-4).
    this._groundForceGroups.update(list => list.map(g => {
      if (g.id !== groupId) return g;
      const units = [...g.units];
      const idx = units.findIndex(u => u.unitProductTypeId === unitProductTypeId);
      if (idx === -1) units.push({ unitProductTypeId, activeCount: 0, reserveCount: count });
      else units[idx] = { ...units[idx], reserveCount: units[idx].reserveCount + count };
      return { ...g, units };
    }));
    // Spezialisierungs-XP wird von den Aufrufern vergeben (`registerProducedChain`
    // bei voller Fertigstellung, `creditPartialChainProgress` bei Abbruch) –
    // beide kennen die tatsächlich produzierte Menge, dieser Helfer nicht.
    this.recalcCrewing(colonyId);
  }

  /**
   * Verteilt Soldaten proportional auf die drei Drohnenklassen und
   * bestimmt daraus, wie viele Drohnen je Klasse aktiv (kommandiert,
   * kampffähig) bzw. Reserve (unkommandiert) sind – Mechanik/05_..., §3-4.
   * Läuft nach jeder Rekrutierung/Produktion von Soldaten oder Drohnen
   * in der betroffenen Kolonie neu.
   */
  private recalcCrewing(colonyId: Id): void {
    const group = this._groundForceGroups().find(g => g.colonyId === colonyId);
    if (!group) return;
    const totalOf = (id: Id) => {
      const u = group.units.find(x => x.unitProductTypeId === id);
      return u ? u.activeCount + u.reserveCount : 0;
    };
    const totalSoldiers = totalOf('p_soldier');
    const droneTotals = DRONE_PRODUCT_IDS.map(totalOf);
    const totalDrones = droneTotals.reduce((a, b) => a + b, 0);
    const commandCapacity = totalSoldiers * DRONES_PER_SOLDIER;

    let activeDrones: number[];
    if (totalDrones === 0) {
      activeDrones = droneTotals.map(() => 0);
    } else if (commandCapacity >= totalDrones) {
      activeDrones = droneTotals;
    } else {
      // gleiches Besetzungsverhältnis für alle drei Klassen (Mechanik/05_...,
      // §4) → verfügbare Kommandokapazität proportional zum Bestand jeder
      // Klasse verteilen.
      activeDrones = droneTotals.map(total => Math.floor((commandCapacity * total) / totalDrones));
    }
    const totalActiveDrones = activeDrones.reduce((a, b) => a + b, 0);
    const soldiersActive = Math.min(totalSoldiers, Math.ceil(totalActiveDrones / DRONES_PER_SOLDIER));

    const nextUnits = group.units.map(u => {
      const droneIdx = DRONE_PRODUCT_IDS.indexOf(u.unitProductTypeId);
      if (droneIdx !== -1) {
        return { ...u, activeCount: activeDrones[droneIdx], reserveCount: droneTotals[droneIdx] - activeDrones[droneIdx] };
      }
      if (u.unitProductTypeId === 'p_soldier') {
        return { ...u, activeCount: soldiersActive, reserveCount: totalSoldiers - soldiersActive };
      }
      return u;
    });
    this._groundForceGroups.update(list => list.map(g => g.id === group.id ? { ...g, units: nextUnits } : g));
  }

  private latency(): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, 130));
  }

  // ==========================================================================
  // Persistenz (localStorage-Autosave, rein Prototyp-Komfort)
  // ==========================================================================

  private persist(): void {
    if (this._players().length === 0) return;
    this.lastPersistAt = now();
    const knownSystemIdsByPlayer: Record<Id, Id[]> = {};
    for (const [playerId, ids] of this._knownSystemIds()) knownSystemIdsByPlayer[playerId] = [...ids];
    const snapshot: Snapshot = {
      version: 2,
      players: this._players(),
      systems: this._systems(),
      knownSystemIdsByPlayer,
      planets: this._planets(),
      colonies: this._colonies(),
      planetStats: this._planetStats(),
      powerStates: this._powerStates(),
      populations: this._populations(),
      moneySupplyStates: this._moneySupplyStates(),
      wallets: this._wallets(),
      transactions: this._transactions(),
      buildings: this._buildings(),
      specializations: this._specializations(),
      productionQueue: this._productionQueue(),
      warehouse: this._warehouse(),
      gateways: this._gateways(),
      fleets: this._fleets(),
      shipyardQueue: this._shipyardQueue(),
      groundForceGroups: this._groundForceGroups(),
      recruitmentQueue: this._recruitmentQueue(),
      sellOrders: this._sellOrders(),
      consumptionBudget: Object.fromEntries(this.consumptionBudget),
      npcs: this._npcs(),
      universeStats: this._universeStats(),
      notifications: this._notifications(),
      diplomaticRelations: this._diplomaticRelations(),
      peaceOffers: this._peaceOffers(),
      battles: this._battles(),
      blockades: this._blockades(),
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
    } catch {
      // Speicher voll o.ä. – für den Prototyp unkritisch
    }
  }

  private tryLoad(): boolean {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return false;
      const snap = JSON.parse(raw) as Snapshot;
      if (!snap.players?.length) return false;
      this._players.set(snap.players);
      this._systems.set(snap.systems);
      this._knownSystemIds.set(new Map(
        Object.entries(snap.knownSystemIdsByPlayer ?? {}).map(([playerId, ids]) => [playerId, new Set(ids)]),
      ));
      this._planets.set(snap.planets);
      this._colonies.set(snap.colonies);
      this._planetStats.set(snap.planetStats);
      this._powerStates.set(snap.powerStates ?? []);
      this._populations.set(snap.populations);
      this._moneySupplyStates.set(snap.moneySupplyStates);
      this._wallets.set(snap.wallets);
      this._transactions.set(snap.transactions);
      this._buildings.set(snap.buildings);
      this._specializations.set(snap.specializations);
      this._productionQueue.set(snap.productionQueue ?? []);
      this._warehouse.set(snap.warehouse);
      this.rebuildWarehouseIndex(snap.warehouse);
      this._gateways.set(snap.gateways);
      this._fleets.set((snap.fleets ?? []).map(f => ({
        ...f,
        cargo: f.cargo ?? [],
        locationPlanetId: f.locationPlanetId ?? null,
        pendingHops: f.pendingHops ?? [],
        destinationSystemId: f.destinationSystemId ?? null,
        departedAt: f.departedAt ?? null,
        arrivesAt: f.arrivesAt ?? null,
        status: (f.status as string) === 'Building' ? 'Stationed' : f.status, // altes, nie genutztes 'Building' – siehe Fleet-Modell
      })));
      this._shipyardQueue.set(snap.shipyardQueue ?? []);
      this._groundForceGroups.set(snap.groundForceGroups);
      this._recruitmentQueue.set(snap.recruitmentQueue ?? []);
      this._sellOrders.set((snap.sellOrders ?? []).map(o => ({ ...o, autoRelist: o.autoRelist ?? false, sourceFleetId: o.sourceFleetId ?? null })));
      this._npcs.set(snap.npcs ?? []);
      this._universeStats.set(snap.universeStats ?? []);
      this._notifications.set((snap.notifications ?? []).map(n => ({ ...n, link: n.link ?? null })));
      this._diplomaticRelations.set(snap.diplomaticRelations ?? []);
      this._peaceOffers.set(snap.peaceOffers ?? []);
      this._battles.set((snap.battles ?? []).map(b => ({
        ...b,
        reportToken: b.reportToken ?? randomToken(),
        ticks: (b.ticks ?? []).map(t => ({ ...t, attackerShipsBefore: t.attackerShipsBefore ?? [], defenderShipsBefore: t.defenderShipsBefore ?? [] })),
      })));
      this._blockades.set(snap.blockades ?? []);
      for (const [k, v] of Object.entries(snap.consumptionBudget ?? {})) this.consumptionBudget.set(k, v);
      return true;
    } catch {
      return false;
    }
  }
}
