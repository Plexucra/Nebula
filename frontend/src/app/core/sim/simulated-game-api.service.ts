import { Injectable, Signal, computed, signal } from '@angular/core';
import {
  AutoProductionOrder, Building, BuildingType, Colony, ColonyPowerState, Fleet, Gateway, GatewayWeightEntry, GroundForceGroup,
  GroundUnitTypeDef, Id, Npc, Planet, PlanetStats, Player, Population, PopulationMoneySupplyState,
  ProductType, ProductionQueueEntry, RecruitmentQueueEntry, SellOrder, ShipTypeDef,
  ShipyardQueueEntry, Specialization, System, Transaction, TransactionReason,
  UniverseStatSnapshot, Wallet, WarehouseEntry,
} from '../models';
import { GameApi } from './game-api';
import { hoursToMs, now, REAL_MS_PER_GAME_HOUR } from './clock';
import { nextId } from './id';
import { PRODUCT_CATALOG, findProductType } from './data/product-catalog';
import { BUILDING_CATALOG, findBuildingType } from './data/building-catalog';
import { SHIP_CATALOG } from './data/ship-catalog';
import { GROUND_UNIT_CATALOG } from './data/ground-unit-catalog';
import { createWorldSeed, WorldSeed } from './data/world-seed';
import * as F from './engine/formulas';

const STORAGE_KEY = 'nebula_sim_v1';
const TICK_MS = 1000;
const TICK_GAME_HOURS = TICK_MS / REAL_MS_PER_GAME_HOUR;
const SPECIALIZATION_DECAY_GRACE_MS = 16000;
const DEFENSE_ACTIVATION_HOURS = 12;
/**
 * Konsumgüter für die Bevölkerungsversorgung – seit dem erweiterten
 * Produktionsbaum (Nebula_Planetentypen_..., §10.7) mehrstufige
 * Endprodukte statt einstufiger Güter. Siehe `tryQueueChain` für die
 * NPC-KI-Seite dieser tieferen Kette.
 */
const CONSUMER_GOODS_ORDER = ['p_grundnahrung', 'p_grundmedizin', 'p_unterhaltungselektronik'];
/** Ziel-Endprodukt und Mindestbestand der NPC-Nahrungskette, siehe `tryQueueChain`. */
const FOOD_TARGET_PRODUCT_ID = 'p_grundnahrung';
const FOOD_TARGET_STOCK = 30;
const CHAIN_BATCH_MIN = 15;
const MAX_CHAIN_DEPTH = 8;
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
 * Ausgleichsfonds gegen Geldhortung (Konzeption/Spieldesign/06_..., §8;
 * Mechanik/10_..., §7): täglich zahlen große Spieler-/Kommandanten-Wallets
 * und jede Kolonie eine feste Abgabe in einen galaxieweiten Topf, der im
 * selben Lauf wieder vollständig pro Kopf an alle Bevölkerungs-Wallets
 * ausgeschüttet wird – kein Money Sink, reine Umverteilung.
 */
const GAME_DAY_MS = hoursToMs(24);
const WEALTH_TAX_THRESHOLD = 1000;
const WEALTH_TAX_RATE = 0.001;
const COLONY_TAX_RATE = 0.01;
/**
 * Obergrenze für die von der Dauerproduktion automatisch nachgezogene
 * Verkaufsorder (siehe `syncAutoProductionSellOrders`): ohne Deckel würde
 * bei aktivem `localPrice` jede produzierte Einheit sofort in den Verkauf
 * wandern und das lokale Lager nie befüllt.
 */
const AUTO_SELL_ORDER_CAP = 50;
/**
 * PowerUpkeepJob (Umsetzungskonzept/01_..., §3): laufender
 * Eleriumenergiezelle-Verbrauch des Energienetzes, pro Stufe und
 * Spielstunde. Bewusst klein gewählt – Elerium-115 ist selten (siehe
 * product-catalog.ts) und die Eleriumenergiezelle liegt seit dem
 * erweiterten Produktionsbaum vier Fertigungsstufen hinter der
 * Eleriumspur (Nebula_Planetentypen_..., §9.2) statt einer – reicht der
 * Bestand nicht, sinkt `coverageRatio` und mindert anteilig die
 * Wohnkapazität, die das Energienetz beisteuert.
 */
const ELERIUM_UPKEEP_PER_POWERGRID_LEVEL = 0.005;
const POWERGRID_FUEL_PRODUCT_ID = 'p_elerium_energiezelle';
/** Unterhalb dieser Deckungsquote gilt eine Kolonie als "im Blackout" (siehe `isBlackout`). */
const BLACKOUT_THRESHOLD = 0.999;
/**
 * Blackout-Folgen (Konzeption/Spieldesign/06_..., "Energieversorgung"):
 * Produktion läuft nur noch auf 10% Geschwindigkeit, Sicherheit und
 * Lebensstandard werden halbiert, Bevölkerungswachstum (nicht aber
 * Schrumpfung) setzt vollständig aus – siehe `computeProductionMs`,
 * `recalcCoreStats`, `runConsumption`, `growPopulationAndMoneySupply`.
 */
const BLACKOUT_PRODUCTION_FACTOR = 0.1;
const BLACKOUT_STAT_FACTOR = 0.5;

interface Snapshot {
  version: 1;
  player: Player | null;
  systems: System[];
  knownSystemIds: Id[];
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
  autoProductionOrders: AutoProductionOrder[];
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
  private readonly _player = signal<Player | null>(null);
  private readonly _systems = signal<System[]>([]);
  private readonly _knownSystemIds = signal<Set<Id>>(new Set());
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
  private readonly _autoProductionOrders = signal<AutoProductionOrder[]>([]);
  private readonly _warehouse = signal<WarehouseEntry[]>([]);
  private readonly _gateways = signal<Gateway[]>([]);
  private readonly _fleets = signal<Fleet[]>([]);
  private readonly _shipyardQueue = signal<ShipyardQueueEntry[]>([]);
  private readonly _groundForceGroups = signal<GroundForceGroup[]>([]);
  private readonly _recruitmentQueue = signal<RecruitmentQueueEntry[]>([]);
  private readonly _sellOrders = signal<SellOrder[]>([]);
  private readonly _npcs = signal<Npc[]>([]);
  private readonly _universeStats = signal<UniverseStatSnapshot[]>([]);

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

  readonly player = this._player.asReadonly();
  readonly wallet = computed(() => this.findWallet('Player', this._player()?.id ?? ''));

  constructor() {
    const loaded = this.tryLoad();
    if (!loaded) {
      // kein Autosave vorhanden -> Splash-Screen (siehe app-shell) fragt nach Namen
    }
    setInterval(() => this.runTick(), TICK_MS);
  }

  // ==========================================================================
  // Bootstrap
  // ==========================================================================

  async startNewGame(commanderName: string, homeworldName: string): Promise<void> {
    await this.latency();
    if (this._player()) return;
    const seed: WorldSeed = createWorldSeed(commanderName.trim() || 'Unbekannter Kommandant', homeworldName.trim() || 'Heimatwelt');
    this.hydrate(seed);
    this.persist();
  }

  async resetGame(): Promise<void> {
    await this.latency();
    localStorage.removeItem(STORAGE_KEY);
    this._player.set(null);
    this._systems.set([]);
    this._knownSystemIds.set(new Set());
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
    this._autoProductionOrders.set([]);
    this._warehouse.set([]);
    this._gateways.set([]);
    this._fleets.set([]);
    this._shipyardQueue.set([]);
    this._groundForceGroups.set([]);
    this._recruitmentQueue.set([]);
    this._sellOrders.set([]);
    this._npcs.set([]);
    this._universeStats.set([]);
    this.lastProducedAt.clear();
    this.consumptionBudget.clear();
    this.rawStandardOfLiving.clear();
    this.lastNpcAiAt = 0;
    this.lastStatsSnapshotAt = 0;
    this.lastWealthRedistributionAt = 0;
  }

  private hydrate(seed: WorldSeed): void {
    this._player.set(seed.player);
    this._systems.set(seed.systems);
    this._knownSystemIds.set(new Set([seed.player.homeSystemId]));
    this._planets.set(seed.planets);
    this._colonies.set(seed.colonies);
    this._planetStats.set(seed.planetStats);
    this._populations.set(seed.populations);
    this._moneySupplyStates.set(seed.moneySupplyStates);
    this._wallets.set(seed.wallets);
    this._buildings.set(seed.buildings);
    this._warehouse.set(seed.warehouse);
    this._gateways.set(seed.gateways);
    this._npcs.set(seed.npcs);
    this._fleets.set(seed.fleets);
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
    return computed(() => this._colonies().filter(c => c.ownerId === this._player()?.id));
  }
  colony(id: Id): Signal<Colony | undefined> {
    return computed(() => this._colonies().find(c => c.id === id));
  }
  colonyStats(id: Id): Signal<PlanetStats | undefined> {
    return computed(() => this._planetStats().find(s => s.colonyId === id));
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

  async queueBuilding(colonyId: Id, buildingTypeId: Id): Promise<void> {
    await this.latency();
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
    this._buildings.update(list => list.map(b => b.id === buildingId && b.colonyId === colonyId
      ? { ...b, activationState: 'Inactive', activationCompletesAt: null }
      : b));
    this.persist();
  }

  // ==========================================================================
  // Produktion
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

  async queueProduction(colonyId: Id, productTypeId: Id, quantity: number): Promise<void> {
    await this.latency();
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    const product = findProductType(productTypeId);
    if (product.category === 'Ship' || product.category === 'GroundUnit') {
      throw new Error('Schiffe und Bodeneinheiten werden über Werft bzw. Ausbildungszentrum in Auftrag gegeben.');
    }
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) {
      throw new Error('Ohne Industriekomplex ist keine Fertigung möglich.');
    }
    this.consumeRecipeInputs(colonyId, product, quantity);

    const msPerUnit = this.computeProductionMs(colonyId, product, 'b_industry');
    const entry: ProductionQueueEntry = {
      id: nextId('pq'), colonyId, productTypeId, quantity, producedSoFar: 0,
      startedAt: now(), nextUnitCompletesAt: now() + msPerUnit,
    };
    this._productionQueue.update(list => [...list, entry]);
    this.persist();
  }

  async cancelProduction(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    const entry = this._productionQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry) return;
    const product = findProductType(entry.productTypeId);
    const remaining = entry.quantity - entry.producedSoFar;
    for (const input of product.recipe) {
      this.addToWarehouse(colonyId, input.inputProductTypeId, input.quantity * remaining);
    }
    this._productionQueue.update(list => list.filter(e => e.id !== entryId));
    this.persist();
  }

  autoProductionOrders(colonyId: Id): Signal<AutoProductionOrder[]> {
    return computed(() => this._autoProductionOrders().filter(o => o.colonyId === colonyId));
  }
  autoProductionOrdersForProduct(productTypeId: Id): Signal<AutoProductionOrder[]> {
    return computed(() => this._autoProductionOrders().filter(o => o.productTypeId === productTypeId));
  }

  async setAutoProductionTarget(colonyId: Id, productTypeId: Id, maxStock: number, localPrice = 0): Promise<void> {
    await this.latency();
    if (!Number.isFinite(maxStock) || maxStock <= 0) throw new Error('Maximalbestand muss größer als 0 sein.');
    if (!Number.isFinite(localPrice) || localPrice < 0) throw new Error('Lokaler Preis darf nicht negativ sein.');
    const product = findProductType(productTypeId);
    if (product.category === 'Ship' || product.category === 'GroundUnit') {
      throw new Error('Schiffe und Bodeneinheiten werden über Werft bzw. Ausbildungszentrum in Auftrag gegeben.');
    }
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) {
      throw new Error('Ohne Industriekomplex ist keine Fertigung möglich.');
    }
    const roundedMaxStock = Math.floor(maxStock);
    const roundedPrice = Math.round(localPrice * 100) / 100;
    const existing = this._autoProductionOrders().find(o => o.colonyId === colonyId && o.productTypeId === productTypeId);
    if (existing) {
      if (roundedPrice <= 0) this.withdrawLinkedSellOrder(existing);
      this._autoProductionOrders.update(list => list.map(o => o.id === existing.id
        ? { ...o, maxStock: roundedMaxStock, localPrice: roundedPrice, linkedSellOrderId: roundedPrice <= 0 ? null : o.linkedSellOrderId }
        : o));
    } else {
      const order: AutoProductionOrder = {
        id: nextId('apo'), colonyId, productTypeId, maxStock: roundedMaxStock, nextUnitCompletesAt: now(),
        localPrice: roundedPrice, linkedSellOrderId: null,
      };
      this._autoProductionOrders.update(list => [...list, order]);
    }
    this.persist();
  }

  async cancelAutoProductionTarget(colonyId: Id, productTypeId: Id): Promise<void> {
    await this.latency();
    const order = this._autoProductionOrders().find(o => o.colonyId === colonyId && o.productTypeId === productTypeId);
    if (order) this.withdrawLinkedSellOrder(order);
    this._autoProductionOrders.update(list => list.filter(o => !(o.colonyId === colonyId && o.productTypeId === productTypeId)));
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
      const walletIds = new Set(this._wallets().filter(w => w.ownerType === 'Player' && w.ownerId === this._player()?.id).map(w => w.id));
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
    return computed(() => this._fleets().filter(f => f.ownerId === this._player()?.id));
  }
  shipyardQueue(colonyId: Id): Signal<ShipyardQueueEntry[]> {
    return computed(() => this._shipyardQueue().filter(q => q.colonyId === colonyId));
  }

  async queueShip(colonyId: Id, shipProductTypeId: Id, quantity: number): Promise<void> {
    await this.latency();
    if (quantity <= 0) throw new Error('Menge muss größer als 0 sein.');
    const product = findProductType(shipProductTypeId);
    if (product.category !== 'Ship') throw new Error('Kein Schiffstyp.');
    if (this.getBuildingLevel(colonyId, 'b_shipyard') < 1) throw new Error('Ohne Werft können keine Schiffe gebaut werden.');
    this.consumeRecipeInputs(colonyId, product, quantity);

    const msPerUnit = this.computeProductionMs(colonyId, product, 'b_shipyard');
    const entry: ShipyardQueueEntry = {
      id: nextId('sy'), colonyId, shipProductTypeId, quantity, producedSoFar: 0,
      startedAt: now(), nextUnitCompletesAt: now() + msPerUnit,
    };
    this._shipyardQueue.update(list => [...list, entry]);
    this.persist();
  }

  async cancelShipOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    const entry = this._shipyardQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry) return;
    const product = findProductType(entry.shipProductTypeId);
    const remaining = entry.quantity - entry.producedSoFar;
    for (const input of product.recipe) this.addToWarehouse(colonyId, input.inputProductTypeId, input.quantity * remaining);
    this._shipyardQueue.update(list => list.filter(e => e.id !== entryId));
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

  async queueRecruitment(colonyId: Id, unitProductTypeId: Id, count: number): Promise<void> {
    await this.latency();
    if (count <= 0) throw new Error('Menge muss größer als 0 sein.');
    const product = findProductType(unitProductTypeId);
    if (product.category !== 'GroundUnit') throw new Error('Kein Bodentruppen-Typ.');
    if (this.getBuildingLevel(colonyId, 'b_academy') < 1) throw new Error('Ohne Ausbildungszentrum keine Rekrutierung möglich.');
    const stats = this._planetStats().find(s => s.colonyId === colonyId);
    if (!stats || stats.loyaltyPct <= 50) throw new Error('Rekrutierung erfordert eine Loyalität über 50%.');
    this.consumeRecipeInputs(colonyId, product, count);

    const msPerUnit = this.computeProductionMs(colonyId, product, 'b_academy');
    const entry: RecruitmentQueueEntry = {
      id: nextId('rq'), colonyId, unitProductTypeId, count, producedSoFar: 0,
      startedAt: now(), nextUnitCompletesAt: now() + msPerUnit,
    };
    this._recruitmentQueue.update(list => [...list, entry]);
    this.persist();
  }

  async cancelRecruitment(colonyId: Id, entryId: Id): Promise<void> {
    await this.latency();
    const entry = this._recruitmentQueue().find(e => e.id === entryId && e.colonyId === colonyId);
    if (!entry) return;
    const product = findProductType(entry.unitProductTypeId);
    const remaining = entry.count - entry.producedSoFar;
    for (const input of product.recipe) this.addToWarehouse(colonyId, input.inputProductTypeId, input.quantity * remaining);
    this._recruitmentQueue.update(list => list.filter(e => e.id !== entryId));
    this.persist();
  }

  // ==========================================================================
  // Gateway / Galaxie
  // ==========================================================================

  gateway(systemId: Id): Signal<Gateway | undefined> {
    return computed(() => this._gateways().find(g => g.systemId === systemId));
  }

  async activateGateway(systemId: Id): Promise<void> {
    await this.latency();
    const gateway = this._gateways().find(g => g.systemId === systemId);
    if (!gateway) throw new Error('Kein Gateway in diesem System.');
    if (gateway.state === 'Active' || gateway.state === 'Activating') throw new Error('Gateway bereits aktiv bzw. in Aktivierung.');
    if (gateway.state === 'Hidden') throw new Error('Das Gateway wurde noch nicht entdeckt.');
    this._gateways.update(list => list.map(g => g.systemId === systemId
      ? { ...g, state: 'Activating', activatingCompletesAt: now() + hoursToMs(6) }
      : g));
    this.persist();
  }

  gatewayWeights(systemId: Id): Signal<GatewayWeightEntry[]> {
    return computed(() => {
      const player = this._player();
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

  visibleSystems(): Signal<System[]> {
    return computed(() => {
      const known = this._knownSystemIds();
      return this._systems().filter(s => known.has(s.id));
    });
  }
  system(id: Id): Signal<System | undefined> {
    return computed(() => this._systems().find(s => s.id === id));
  }

  galaxyRoutes(): Signal<{ a: Id; b: Id }[]> {
    return computed(() => {
      const known = this._knownSystemIds();
      const seen = new Set<string>();
      const routes: { a: Id; b: Id }[] = [];
      for (const gateway of this._gateways()) {
        if (!known.has(gateway.systemId)) continue;
        for (const targetId of gateway.reachableSystemIds) {
          if (!known.has(targetId)) continue;
          const key = [gateway.systemId, targetId].sort().join('|');
          if (seen.has(key)) continue;
          seen.add(key);
          routes.push({ a: gateway.systemId, b: targetId });
        }
      }
      return routes;
    });
  }

  // ==========================================================================
  // Handel
  // ==========================================================================

  sellOrders(systemId: Id): Signal<SellOrder[]> {
    return computed(() => this._sellOrders().filter(o => o.systemId === systemId && o.remainingQuantity > 0));
  }

  async createSellOrder(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number): Promise<void> {
    await this.latency();
    if (quantity <= 0 || pricePerUnit <= 0) throw new Error('Menge und Preis müssen größer als 0 sein.');
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) throw new Error('Unbekannte Kolonie.');
    const stock = this._warehouse().find(w => w.colonyId === colonyId && w.productTypeId === productTypeId)?.quantity ?? 0;
    if (stock < quantity) throw new Error('Nicht genug Lagerbestand für diese Order.');
    this.addToWarehouse(colonyId, productTypeId, -quantity);
    const order: SellOrder = {
      id: nextId('so'), systemId: colony.systemId, locationType: 'Depot', depotColonyId: colonyId,
      sellerId: colony.ownerId, sellerName: this.ownerDisplayName(colony.ownerId), productTypeId, quantity, remainingQuantity: quantity,
      pricePerUnit, createdAt: now(),
    };
    this._sellOrders.update(list => [...list, order]);
    this.persist();
  }

  async cancelSellOrder(orderId: Id): Promise<void> {
    await this.latency();
    const order = this._sellOrders().find(o => o.id === orderId);
    if (!order) return;
    if (order.depotColonyId && order.remainingQuantity > 0) {
      this.addToWarehouse(order.depotColonyId, order.productTypeId, order.remainingQuantity);
    }
    this._sellOrders.update(list => list.filter(o => o.id !== orderId));
    this.persist();
  }

  async buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void> {
    await this.latency();
    const order = this._sellOrders().find(o => o.id === orderId);
    if (!order || order.remainingQuantity < quantity) throw new Error('Nicht genug Ware in dieser Order verfügbar.');
    const player = this.requirePlayer();
    const wallet = this.findWallet('Player', player.id);
    const cost = Math.round(quantity * order.pricePerUnit);
    if (!wallet || wallet.balance < cost) throw new Error('Nicht genug Credits.');
    const sellerWallet = this.findWallet('Player', order.sellerId);
    this._sellOrders.update(list => list
      .map(o => o.id === orderId ? { ...o, remainingQuantity: o.remainingQuantity - quantity } : o)
      .filter(o => o.remainingQuantity > 0));
    this.addToWarehouse(deliverToColonyId, order.productTypeId, quantity);
    if (sellerWallet) this.recordTx(wallet.id, sellerWallet.id, cost, 'Trade', `Kauf ${quantity}× am Markt`);
    this.persist();
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
  // Tick-Loop / Hintergrundjobs
  // ==========================================================================

  private runTick(): void {
    if (!this._player()) return;
    const t = now();
    this.processBuildingCompletions(t);
    this.consumePowerUpkeep();
    this.processDefenseActivations(t);
    this.processGatewayActivation(t);
    this.processProductionQueue(t);
    this.processAutoProduction(t);
    this.syncAutoProductionSellOrders();
    this.processShipyardCompletions(t);
    this.processRecruitmentCompletions(t);
    this.decaySpecializations(t);
    this.payUpkeepAndWages();
    this.runConsumption();
    this.recalcCoreStats();
    this.growPopulationAndMoneySupply();
    this.runWealthRedistributionIfDue(t);
    this.runNpcAiIfDue(t);
    this.recordStatsSnapshotIfDue(t);
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

  private processGatewayActivation(t: number): void {
    const due = this._gateways().find(g => g.state === 'Activating' && g.activatingCompletesAt !== null && g.activatingCompletesAt <= t);
    if (!due) return;
    this._gateways.update(list => list.map(g => g.systemId === due.systemId
      ? { ...g, state: 'Active', activatedAt: t, activatingCompletesAt: null }
      : g));
    // Aktivierung des eigenen Gateways macht die komplette bereits
    // etablierte galaktische Kartografie bekannt (alle Systeme + Routen),
    // nicht nur die unmittelbaren Nachbarn – ein direkt ansteuerbares Ziel
    // ist davon unabhängig weiterhin nur ein Nachbar im Gateway-Netz.
    this._knownSystemIds.update(known => new Set([...known, ...this._systems().map(s => s.id)]));
  }

  private processProductionQueue(t: number): void {
    let queue = this._productionQueue();
    let changed = false;
    const next: ProductionQueueEntry[] = [];
    for (let entry of queue) {
      let safety = 0;
      while (entry.nextUnitCompletesAt <= t && entry.producedSoFar < entry.quantity && safety < 500) {
        this.addToWarehouse(entry.colonyId, entry.productTypeId, 1);
        this.registerProduced(entry.colonyId, entry.productTypeId);
        const product = findProductType(entry.productTypeId);
        const msPerUnit = this.computeProductionMs(entry.colonyId, product, 'b_industry');
        entry = { ...entry, producedSoFar: entry.producedSoFar + 1, nextUnitCompletesAt: entry.nextUnitCompletesAt + msPerUnit };
        changed = true;
        safety++;
      }
      if (entry.producedSoFar < entry.quantity) next.push(entry);
    }
    if (changed) this._productionQueue.set(next);
  }

  /**
   * Dauerproduktion: füllt den Lagerbestand bis `maxStock` auf, solange
   * Ausgangsstoffe vorhanden sind. Fehlen sie oder ist das Ziel erreicht,
   * bleibt `nextUnitCompletesAt` in der Vergangenheit stehen (kein
   * Nachholen verpasster Zeit) – bei Nachschub bzw. Lagerabbau greift der
   * nächste Tick sofort wieder zu.
   */
  private processAutoProduction(t: number): void {
    const orders = this._autoProductionOrders();
    if (orders.length === 0) return;
    let changed = false;
    const next: AutoProductionOrder[] = [];
    for (let entry of orders) {
      const product = findProductType(entry.productTypeId);
      let safety = 0;
      while (entry.nextUnitCompletesAt <= t && safety < 500) {
        const stock = this._warehouse().find(w => w.colonyId === entry.colonyId && w.productTypeId === entry.productTypeId)?.quantity ?? 0;
        if (stock >= entry.maxStock || !this.canAffordRecipeInputs(entry.colonyId, product, 1)) break;
        this.consumeRecipeInputs(entry.colonyId, product, 1);
        this.addToWarehouse(entry.colonyId, entry.productTypeId, 1);
        this.registerProduced(entry.colonyId, entry.productTypeId);
        const msPerUnit = this.computeProductionMs(entry.colonyId, product, 'b_industry');
        entry = { ...entry, nextUnitCompletesAt: entry.nextUnitCompletesAt + msPerUnit };
        changed = true;
        safety++;
      }
      next.push(entry);
    }
    if (changed) this._autoProductionOrders.set(next);
  }

  /**
   * Pflegt für jeden Dauerauftrag mit `localPrice > 0` genau eine Verkaufsorder
   * am Systemmarkt: frisch produzierter Lagerbestand wird in ihre Menge
   * übernommen, ein geänderter `localPrice` wird nachgezogen. Wurde die Order
   * komplett verkauft (oder extern storniert), wird bei erneutem Warenzugang
   * automatisch eine neue eröffnet.
   */
  private syncAutoProductionSellOrders(): void {
    const orders = this._autoProductionOrders();
    if (orders.length === 0) return;
    let ordersChanged = false;
    const next: AutoProductionOrder[] = [];
    for (let entry of orders) {
      if (entry.localPrice <= 0) { next.push(entry); continue; }
      let linked = entry.linkedSellOrderId ? this._sellOrders().find(o => o.id === entry.linkedSellOrderId) : undefined;
      if (entry.linkedSellOrderId && !linked) { entry = { ...entry, linkedSellOrderId: null }; ordersChanged = true; }
      if (linked && linked.pricePerUnit !== entry.localPrice) {
        const price = entry.localPrice;
        this._sellOrders.update(list => list.map(o => o.id === linked!.id ? { ...o, pricePerUnit: price } : o));
      }
      const stock = this._warehouse().find(w => w.colonyId === entry.colonyId && w.productTypeId === entry.productTypeId)?.quantity ?? 0;
      if (stock > 0) {
        if (linked) {
          const moveQty = Math.max(0, Math.min(stock, AUTO_SELL_ORDER_CAP - linked.remainingQuantity));
          if (moveQty > 0) {
            this.addToWarehouse(entry.colonyId, entry.productTypeId, -moveQty);
            const linkedId = linked.id;
            this._sellOrders.update(list => list.map(o => o.id === linkedId
              ? { ...o, quantity: o.quantity + moveQty, remainingQuantity: o.remainingQuantity + moveQty }
              : o));
          }
        } else {
          const colony = this._colonies().find(c => c.id === entry.colonyId);
          if (colony) {
            const moveQty = Math.min(stock, AUTO_SELL_ORDER_CAP);
            this.addToWarehouse(entry.colonyId, entry.productTypeId, -moveQty);
            const newOrder: SellOrder = {
              id: nextId('so'), systemId: colony.systemId, locationType: 'Depot', depotColonyId: entry.colonyId,
              sellerId: colony.ownerId, sellerName: this.ownerDisplayName(colony.ownerId),
              productTypeId: entry.productTypeId, quantity: moveQty, remainingQuantity: moveQty,
              pricePerUnit: entry.localPrice, createdAt: now(),
            };
            this._sellOrders.update(list => [...list, newOrder]);
            entry = { ...entry, linkedSellOrderId: newOrder.id };
            ordersChanged = true;
          }
        }
      }
      next.push(entry);
    }
    if (ordersChanged) this._autoProductionOrders.set(next);
  }

  /** Zieht eine ggf. mit einem Dauerauftrag verknüpfte Verkaufsorder zurück und gibt unverkauften Bestand ins Lager zurück. */
  private withdrawLinkedSellOrder(order: AutoProductionOrder): void {
    if (!order.linkedSellOrderId) return;
    const linked = this._sellOrders().find(o => o.id === order.linkedSellOrderId);
    if (!linked) return;
    if (linked.remainingQuantity > 0) this.addToWarehouse(order.colonyId, order.productTypeId, linked.remainingQuantity);
    this._sellOrders.update(list => list.filter(o => o.id !== linked.id));
  }

  private processShipyardCompletions(t: number): void {
    let queue = this._shipyardQueue();
    let changed = false;
    const next: ShipyardQueueEntry[] = [];
    for (let entry of queue) {
      let safety = 0;
      while (entry.nextUnitCompletesAt <= t && entry.producedSoFar < entry.quantity && safety < 500) {
        this.addShipToFleet(entry.colonyId, entry.shipProductTypeId, 1);
        const product = findProductType(entry.shipProductTypeId);
        const msPerUnit = this.computeProductionMs(entry.colonyId, product, 'b_shipyard');
        entry = { ...entry, producedSoFar: entry.producedSoFar + 1, nextUnitCompletesAt: entry.nextUnitCompletesAt + msPerUnit };
        changed = true;
        safety++;
      }
      if (entry.producedSoFar < entry.quantity) next.push(entry);
    }
    if (changed) this._shipyardQueue.set(next);
  }

  private processRecruitmentCompletions(t: number): void {
    let queue = this._recruitmentQueue();
    let changed = false;
    const next: RecruitmentQueueEntry[] = [];
    for (let entry of queue) {
      let safety = 0;
      while (entry.nextUnitCompletesAt <= t && entry.producedSoFar < entry.count && safety < 500) {
        this.addUnitToGarrison(entry.colonyId, entry.unitProductTypeId, 1);
        const product = findProductType(entry.unitProductTypeId);
        const msPerUnit = this.computeProductionMs(entry.colonyId, product, 'b_academy');
        entry = { ...entry, producedSoFar: entry.producedSoFar + 1, nextUnitCompletesAt: entry.nextUnitCompletesAt + msPerUnit };
        changed = true;
        safety++;
      }
      if (entry.producedSoFar < entry.count) next.push(entry);
    }
    if (changed) this._recruitmentQueue.set(next);
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
          this._sellOrders.update(list => list.map(o => o.id === order.id ? { ...o, remainingQuantity: o.remainingQuantity - qty } : o));
          if (sellerWallet) this.recordTx(popWallet.id, sellerWallet.id, cost, 'Consumption', `Konsum ${goodId}`);
          spend += cost;
          bought += qty;
        }
        remaining -= spend;
        const coverage = F.clamp(bought / need, 0, 1.5);
        const weight = goodId === 'p_grundnahrung' ? 2 : 1;
        coverageSum += coverage * weight;
        weightSum += weight;
      }
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
      const stock = this._warehouse().find(w => w.colonyId === colony.id && w.productTypeId === POWERGRID_FUEL_PRODUCT_ID)?.quantity ?? 0;
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
      void this.queueBuilding(colonyId, typeId).catch(() => {});
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
    void this.queueBuilding(colonyId, 'b_industry').catch(() => {});
  }

  private npcMaybeQueueFoodChain(colonyId: Id): void {
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) return;
    this.tryQueueChain(colonyId, FOOD_TARGET_PRODUCT_ID, FOOD_TARGET_STOCK, MAX_CHAIN_DEPTH);
  }

  /**
   * Generischer "produziere das nächste fehlende Kettenglied"-Helfer für
   * die NPC-KI: Reicht der Lagerbestand von `productId` nicht, wird
   * rekursiv die erste Rezept-Zutat mit zu wenig Bestand gesucht und
   * STATTDESSEN in Auftrag gegeben (eine Ebene pro Aufruf) – kein
   * vollständiges implizites Durchfertigen aller Stufen auf einmal (siehe
   * Nebula_Planetentypen_..., §13, noch nicht umgesetzt), aber genug,
   * damit die NPC-KI eine mehrstufige Kette schrittweise selbst
   * hochzieht, ohne jede Ebene einzeln verdrahten zu müssen. Gibt `true`
   * zurück, wenn irgendwo in der Kette bereits produziert wird/wurde.
   */
  private tryQueueChain(colonyId: Id, productId: Id, targetStock: number, depth: number): boolean {
    if (depth <= 0) return false;
    if (this._productionQueue().some(q => q.colonyId === colonyId && q.productTypeId === productId)) return true;
    const stock = this._warehouse().find(w => w.colonyId === colonyId && w.productTypeId === productId)?.quantity ?? 0;
    if (stock >= targetStock) return false;
    const product = findProductType(productId);
    for (const input of product.recipe) {
      const inputStock = this._warehouse().find(w => w.colonyId === colonyId && w.productTypeId === input.inputProductTypeId)?.quantity ?? 0;
      const need = input.quantity * targetStock;
      if (inputStock < need) {
        return this.tryQueueChain(colonyId, input.inputProductTypeId, Math.max(need, CHAIN_BATCH_MIN), depth - 1);
      }
    }
    void this.queueProduction(colonyId, productId, targetStock).catch(() => {});
    return true;
  }

  private npcMaybeQueueSpecialty(colonyId: Id, productId: Id): void {
    if (this.getBuildingLevel(colonyId, 'b_industry') < 1) return;
    if (this._productionQueue().some(q => q.colonyId === colonyId && q.productTypeId === productId)) return;
    const stock = this._warehouse().find(w => w.colonyId === colonyId && w.productTypeId === productId)?.quantity ?? 0;
    if (stock >= 60) return;
    void this.queueProduction(colonyId, productId, 15).catch(() => {});
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
      const stock = this._warehouse().find(w => w.colonyId === colony.id && w.productTypeId === productId)?.quantity ?? 0;
      const surplus = Math.floor(stock - reserve);
      if (surplus < minListing) continue;
      const listedRemaining = this._sellOrders()
        .filter(o => o.depotColonyId === colony.id && o.productTypeId === productId && o.remainingQuantity > 0)
        .reduce((sum, o) => sum + o.remainingQuantity, 0);
      if (listedRemaining >= minListing) continue; // Markt noch ausreichend versorgt
      const product = findProductType(productId);
      const basePrice = 3 + product.tier * 2.5;
      const price = Math.round(basePrice * (0.85 + this.npcPriceJitter(npc.id) * 0.3) * 100) / 100;
      void this.createSellOrder(colony.id, productId, surplus, price).catch(() => {});
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
    const p = this._player();
    if (!p) throw new Error('Kein aktiver Kommandant.');
    return p;
  }

  /** Besitzer-ID einer Colony (Spieler oder NPC) – finanzielle Aktionen hängen am Besitzer, nicht am Menschen. */
  private requireColonyOwner(colonyId: Id): Id {
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) throw new Error('Unbekannte Kolonie.');
    return colony.ownerId;
  }

  private ownerDisplayName(ownerId: Id): string {
    if (this._player()?.id === ownerId) return this._player()!.name;
    return this._npcs().find(n => n.id === ownerId)?.name ?? 'Unbekannt';
  }

  private findWallet(ownerType: 'Player' | 'Population', ownerId: Id): Wallet | undefined {
    return this._wallets().find(w => w.ownerType === ownerType && w.ownerId === ownerId);
  }

  private popWalletIdForColony(colonyId: Id): Id | null {
    return this.findWallet('Population', colonyId)?.id ?? null;
  }

  private homeworldPopulationWalletId(): Id | null {
    const player = this._player();
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
    this._warehouse.update(list => {
      const idx = list.findIndex(w => w.colonyId === colonyId && w.productTypeId === productTypeId);
      if (idx === -1) {
        if (delta <= 0) return list;
        return [...list, { colonyId, productTypeId, quantity: delta }];
      }
      const next = [...list];
      next[idx] = { ...next[idx], quantity: Math.max(0, next[idx].quantity + delta) };
      return next;
    });
  }

  private canAffordRecipeInputs(colonyId: Id, product: ProductType, quantity: number): boolean {
    return product.recipe.every(input => {
      const have = this._warehouse().find(w => w.colonyId === colonyId && w.productTypeId === input.inputProductTypeId)?.quantity ?? 0;
      return have >= input.quantity * quantity;
    });
  }

  private consumeRecipeInputs(colonyId: Id, product: ProductType, quantity: number): void {
    for (const input of product.recipe) {
      const have = this._warehouse().find(w => w.colonyId === colonyId && w.productTypeId === input.inputProductTypeId)?.quantity ?? 0;
      const need = input.quantity * quantity;
      if (have < need) {
        throw new Error(`Nicht genug ${findProductType(input.inputProductTypeId).name} im Lager (benötigt ${need}, vorhanden ${have}).`);
      }
    }
    for (const input of product.recipe) {
      this.addToWarehouse(colonyId, input.inputProductTypeId, -(input.quantity * quantity));
    }
  }

  private computeProductionMs(colonyId: Id, product: ProductType, facilityTypeId: 'b_industry' | 'b_shipyard' | 'b_academy'): number {
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
    const hours = product.baseProductionHours / Math.max(speed, 0.05);
    return hoursToMs(hours);
  }

  private registerProduced(colonyId: Id, productTypeId: Id): void {
    // Soldaten sind laut Mechanik/05_..., §5 nicht spezialisierbar.
    if (productTypeId === 'p_soldier') return;
    const key = `${colonyId}:${productTypeId}`;
    this.lastProducedAt.set(key, now());
    const existing = this._specializations().find(s => s.colonyId === colonyId && s.productTypeId === productTypeId);
    if (!existing) {
      this._specializations.update(list => [...list, { colonyId, productTypeId, currentLevel: 0, experience: 1, thresholdForNextLevel: 5 }]);
      return;
    }
    let experience = existing.experience + 1;
    let level = existing.currentLevel;
    let threshold = existing.thresholdForNextLevel;
    if (experience >= threshold && level < 10) {
      level += 1;
      experience = 0;
      threshold = Math.round(5 * Math.pow(1.5, level));
    }
    this._specializations.update(list => list.map(s => s.colonyId === colonyId && s.productTypeId === productTypeId
      ? { ...s, currentLevel: level, experience, thresholdForNextLevel: threshold }
      : s));
  }

  private addShipToFleet(colonyId: Id, shipProductTypeId: Id, quantity: number): void {
    const colony = this._colonies().find(c => c.id === colonyId);
    if (!colony) return;
    let fleet = this._fleets().find(f => f.locationColonyId === colonyId && f.ownerId === colony.ownerId && f.status === 'Stationed');
    if (!fleet) {
      fleet = {
        id: nextId('flt'), ownerId: colony.ownerId, name: `Standflotte ${colony.name}`,
        locationType: 'ColonyOrbit', locationColonyId: colonyId, systemId: colony.systemId,
        status: 'Stationed', ships: [],
      };
      this._fleets.update(list => [...list, fleet!]);
    }
    const fleetId = fleet.id;
    this._fleets.update(list => list.map(f => {
      if (f.id !== fleetId) return f;
      const ships = [...f.ships];
      const idx = ships.findIndex(g => g.shipProductTypeId === shipProductTypeId);
      if (idx === -1) ships.push({ shipProductTypeId, quantity });
      else ships[idx] = { ...ships[idx], quantity: ships[idx].quantity + quantity };
      return { ...f, ships };
    }));
    this.registerProduced(colonyId, shipProductTypeId);
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
    this.registerProduced(colonyId, unitProductTypeId);
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
    if (!this._player()) return;
    const snapshot: Snapshot = {
      version: 1,
      player: this._player(),
      systems: this._systems(),
      knownSystemIds: [...this._knownSystemIds()],
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
      autoProductionOrders: this._autoProductionOrders(),
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
      if (!snap.player) return false;
      this._player.set(snap.player);
      this._systems.set(snap.systems);
      this._knownSystemIds.set(new Set(snap.knownSystemIds));
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
      this._productionQueue.set(snap.productionQueue);
      this._autoProductionOrders.set((snap.autoProductionOrders ?? []).map(o => ({
        ...o, localPrice: o.localPrice ?? 0, linkedSellOrderId: o.linkedSellOrderId ?? null,
      })));
      this._warehouse.set(snap.warehouse);
      this._gateways.set(snap.gateways);
      this._fleets.set(snap.fleets);
      this._shipyardQueue.set(snap.shipyardQueue);
      this._groundForceGroups.set(snap.groundForceGroups);
      this._recruitmentQueue.set(snap.recruitmentQueue);
      this._sellOrders.set(snap.sellOrders);
      this._npcs.set(snap.npcs ?? []);
      this._universeStats.set(snap.universeStats ?? []);
      for (const [k, v] of Object.entries(snap.consumptionBudget ?? {})) this.consumptionBudget.set(k, v);
      return true;
    } catch {
      return false;
    }
  }
}
