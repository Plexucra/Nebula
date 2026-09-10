import { EnergyStorage } from '../models/building.model';
import { Injectable, OnDestroy, Signal, inject, signal } from '@angular/core';
import { SIGNAL } from '@angular/core/primitives/signals';
import { GameApi } from './game-api';
import { webSocketBackendUrl } from './backend-config';
import { UiClockService } from '../ui/ui-clock.service';
import {
  Battle, Blockade, BlockadeAnchor, BuildSlots, Building, BuildingType, CarrierJumpPreview, ChainPlan, Colonization, Colony, ColonySpeedBreakdown, DiplomaticRelation, DiplomaticStatus, Fleet, FleetCargoCapacity, FleetSystemTarget, FleetTroopCapacity, GameNotification, Gateway,
  GatewayWeightEntry, GroundBattle, GroundForceGroup, GroundUnitTypeDef, HubDepotEntry, HubOrder, Id, Message, PeaceOffer, Planet, PlanetStats, Player, PlayerRole, Population,
  PopulationMoneySupplyState, PopulationTrend, ProductType, ProductionQueueEntry, RecruitmentQueueEntry, SellOrder, ShipTypeDef,
  ShipyardQueueEntry, Specialization, SupplyInventoryEntry, System, Transaction, Treaty, TreatyOffer, TreatyType, UniverseStatSnapshot, Wallet, GameVictory,
  WarehouseEntry,
} from '../models';

/** Schlüssel der gemerkten Anmeldung, siehe `rememberSession`. */
const SESSION_STORAGE_KEY = 'nebula_player_id';
/** Name des gemerkten Kommandanten – Gegenprobe zur ID, siehe `restoreSession`. */
const SESSION_NAME_STORAGE_KEY = 'nebula_player_name';

interface ServerMessage {
  type: string;
  requestId: string | null;
  payload: unknown;
  /** Spieluhr des Servers beim Absenden – stellt die `UiClockService`-Spieluhr, siehe dort. */
  gameNow?: number;
}

/** Takt des zentralen Poll-Zeitgebers – feiner als das Abfrageintervall, damit ein Intervall von 1 s auch etwa 1 s ist. */
const POLL_SCHEDULER_MS = 250;
/**
 * Ein Poll-Signal, das so lange nicht gelesen wurde, gilt als verwaist und
 * wird nicht mehr abgefragt – drei verpasste Takte, damit ein kurzer
 * Change-Detection-Aussetzer keine lebende Ansicht einfrieren lässt.
 */
const POLL_IDLE_MS = 3000;

interface PollEntry {
  type: string;
  params: unknown;
  intervalMs: number;
  /** Letztes Lesen des Signals – siehe Klassendoku ("gepollt wird nur, was gelesen wird"). */
  lastReadAt: number;
  lastFetchAt: number;
  inFlight: boolean;
  set: (v: unknown) => void;
  signal: Signal<unknown>;
}

/**
 * `GameApi`-Implementierung über das Quarkus-WebSocket-Backend (siehe
 * Umsetzungskonzept/13_Client_Server_Migration_Quarkus_Backend.md,
 * Abschnitt "Frontend-Anpassung"). Jede Befehlsmethode sendet ein
 * `{type, requestId, payload}` und löst ein Promise beim passenden
 * `Ack`/`Error` auf (`send`). Für Query-Methoden gibt es serverseitig NUR
 * für den Kanal `"players"` einen echten Push – alle anderen Signal-
 * Rückgaben werden hier stattdessen per Polling (`poll`, Standardintervall
 * 1s, angelehnt an den Server-Tick) aktuell gehalten.
 *
 * <p><b>Bewusste Vereinfachung</b> (siehe Migrationsplan, Abschnitt
 * "Sync-Strategie"): eine vollständige Push-basierte Synchronisation für
 * ALLE ~90 Kanäle wäre die "saubere" Lösung, ist aber ein separates, großes
 * Vorhaben (serverseitiges Interessen-Tracking je Verbindung). Polling ist
 * für die Größenordnung dieses Prototyps (wenige gleichzeitige Nutzer,
 * 1s-Tick ohnehin die kleinste sinnvolle Auflösung) ausreichend reaktiv.</p>
 *
 * <p><b>Gepollt wird nur, was gerade jemand LIEST.</b> Jedes Poll-Signal merkt
 * sich beim Lesen den Zeitpunkt; der eine zentrale Zeitgeber (`schedulePolls`)
 * fragt den Server nur für Signale, die binnen `POLL_IDLE_MS` gelesen wurden.
 * Vorher lief je Abfrage ein eigenes `setInterval` bis zum Schließen des Tabs –
 * nach dem Besuch einiger Kolonieseiten waren das hunderte Anfragen je Sekunde
 * gegen die globale Sperre des Servers, für Ansichten, die längst zerstört
 * waren. Eine Antwort, die noch aussteht, wird nicht überholt (kein Stau bei
 * langsamer Verbindung). Das ersetzt das früher offen gelassene Aufräumen je
 * `DestroyRef`: Abhängigkeiten aus `computed()` und Vorlagen brauchen keinen
 * Aufrufer-Kontext, sie melden sich schlicht durch ihr Lesen an.</p>
 */
@Injectable()
export class WebSocketGameApiService implements GameApi, OnDestroy {
  private ws: WebSocket;
  private readonly uiClock = inject(UiClockService);
  private requestCounter = 0;
  private readonly pending = new Map<string, { resolve: (value: unknown) => void; reject: (reason: unknown) => void }>();
  private readonly intervals: ReturnType<typeof setInterval>[] = [];

  private _productTypes: ProductType[] = [];
  private _buildingTypes: BuildingType[] = [];
  private _shipTypes: ShipTypeDef[] = [];
  private _groundUnitTypes: GroundUnitTypeDef[] = [];

  private readonly _players = signal<Player[]>([]);

  readonly player = signal<Player | null>(null);
  readonly wallet = signal<Wallet | undefined>(undefined);

  constructor() {
    this.ws = new WebSocket(webSocketBackendUrl());
    this.ws.addEventListener('message', (ev) => this.handleMessage(ev.data));
    this.ws.addEventListener('open', () => {
      // Statische Kataloge einmalig laden – synchrone GameApi-Methoden
      // (`productTypes()` usw.) liefern bis dahin `[]`.
      this.send<ProductType[]>('productTypes', {}).then(v => (this._productTypes = v));
      this.send<BuildingType[]>('buildingTypes', {}).then(v => (this._buildingTypes = v));
      this.send<ShipTypeDef[]>('shipTypes', {}).then(v => (this._shipTypes = v));
      this.send<GroundUnitTypeDef[]>('groundUnitTypes', {}).then(v => (this._groundUnitTypes = v));
    });
    this.ws.addEventListener('open', () => void this.restoreSession());
    this.intervals.push(setInterval(() => this.pollWallet(), 1000));
    this.intervals.push(setInterval(() => this.schedulePolls(), POLL_SCHEDULER_MS));
  }

  ngOnDestroy(): void {
    for (const id of this.intervals) clearInterval(id);
    this.ws.close();
  }

  private handleMessage(raw: string): void {
    const msg = JSON.parse(raw) as ServerMessage;
    // Jede Nachricht trägt die Spieluhr des Servers – daran hängt jede Restzeitanzeige.
    if (typeof msg.gameNow === 'number') this.uiClock.syncFromServer(msg.gameNow);
    if (msg.requestId !== null) {
      const entry = this.pending.get(msg.requestId);
      if (!entry) return;
      this.pending.delete(msg.requestId);
      if (msg.type === 'Error') entry.reject(new Error((msg.payload as { message: string })?.message ?? 'Unbekannter Fehler'));
      else entry.resolve(msg.payload);
      return;
    }
    if (msg.type === 'players') {
      this._players.set(msg.payload as Player[]);
      return;
    }
    // Generischer Sofort-Push-Empfang (siehe GameSocket.pushMessages): Kanalname
    // entspricht 1:1 einem über poll() mit leerer Payload ('{}') abgefragten
    // Query-Typ – trifft das Signal, wird es sofort statt erst beim nächsten
    // Poll-Intervall aktualisiert. Kein Treffer (Query noch nie aufgerufen,
    // oder Kanal mit Parametern) → stiller No-op, das nächste Poll holt es nach.
    const setter = this.pollSetters.get(msg.type + ':{}');
    if (setter) setter(msg.payload);
  }

  private send<T>(type: string, payload: unknown): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const requestId = 'r' + (++this.requestCounter);
      this.pending.set(requestId, { resolve: resolve as (v: unknown) => void, reject });
      const body = JSON.stringify({ type, requestId, payload });
      if (this.ws.readyState === WebSocket.OPEN) this.ws.send(body);
      else this.ws.addEventListener('open', () => this.ws.send(body), { once: true });
    });
  }

  /** Memoisierte Polling-Signale, Schlüssel = `type` + serialisierte Payload-Parameter (siehe {@link #poll}). */
  private readonly pollCache = new Map<string, PollEntry>();
  /** Setter-Gegenstück zu {@link #pollCache}, für Sofort-Updates aus einem Server-Push (siehe {@link #handleMessage}). */
  private readonly pollSetters = new Map<string, (v: unknown) => void>();

  /**
   * Polling-Signal für eine Query ohne eigenen Push-Kanal (siehe Klassendoku).
   * MEMOISIERT nach `type`+Payload: mehrere `GameApi`-Komponenten rufen
   * dieselbe Query-Methode oft direkt im Template auf (z. B.
   * `{{ population(colony.id)()?.currentCount }}`), was bei JEDEM
   * Change-Detection-Durchlauf ein NEUES Signal erzeugen würde – bei
   * rein lokal berechneten `computed()`-Signalen unproblematisch wäre
   * (billig, kein eigener Zustand), bei einem Polling-Signal mit echtem
   * Netzwerk-Roundtrip aber fatal: das alte, gerade erst gestartete Signal
   * würde verworfen, bevor seine Antwort je gelesen werden kann – die
   * betroffene UI-Stelle bliebe dauerhaft auf ihrem Startwert stehen.
   *
   * <p>Das zurückgegebene Signal ist eine LESE-VERFOLGTE Hülle um das eigentliche
   * Signal: jeder Aufruf vermerkt `lastReadAt`, und nur so lange jemand liest,
   * fragt {@link #schedulePolls} den Server. Beim Wiederauftauchen eines
   * Aufrufers (Cache-Treffer nach Leerlauf) wird sofort nachgeholt, damit die
   * Ansicht nicht bis zum nächsten Takt mit dem alten Stand steht.</p>
   */
  private poll<T>(type: string, payload: () => unknown, initial: T, intervalMs = 1000): Signal<T> {
    const params = payload();
    const key = type + ':' + JSON.stringify(params);
    const cached = this.pollCache.get(key);
    if (cached) {
      cached.lastReadAt = Date.now();
      if (cached.lastFetchAt < cached.lastReadAt - intervalMs) this.fetchPoll(cached);
      return cached.signal as Signal<T>;
    }

    const value = signal<T>(initial);
    const entry: PollEntry = {
      type, params, intervalMs, lastReadAt: Date.now(), lastFetchAt: 0, inFlight: false,
      set: value.set.bind(value) as (v: unknown) => void, signal: undefined as unknown as Signal<unknown>,
    };
    const inner = value.asReadonly();
    const tracked = (() => { entry.lastReadAt = Date.now(); return inner(); }) as Signal<T>;
    // Der Marker macht die Hülle für Angular zu einem echten Signal (`isSignal`);
    // die Abhängigkeitsverfolgung läuft ohnehin über den inneren Aufruf.
    (tracked as unknown as Record<symbol, unknown>)[SIGNAL] = (inner as unknown as Record<symbol, unknown>)[SIGNAL];
    entry.signal = tracked;
    this.pollCache.set(key, entry);
    this.pollSetters.set(key, entry.set);
    this.fetchPoll(entry);
    return tracked;
  }

  /** Ein Zeitgeber für alle Poll-Signale: fragt nur die ab, die gerade gelesen werden und deren Intervall abgelaufen ist. */
  private schedulePolls(): void {
    const now = Date.now();
    for (const entry of this.pollCache.values()) {
      if (entry.inFlight || now - entry.lastReadAt > POLL_IDLE_MS || now - entry.lastFetchAt < entry.intervalMs) continue;
      this.fetchPoll(entry);
    }
  }

  private fetchPoll(entry: PollEntry): void {
    if (entry.inFlight) return;
    entry.inFlight = true;
    entry.lastFetchAt = Date.now();
    this.send<unknown>(entry.type, entry.params)
      .then(v => entry.set(v))
      .catch(() => { /* z. B. noch nicht eingeloggt – Signal behält letzten Wert */ })
      .finally(() => { entry.inFlight = false; });
  }

  private async pollWallet(): Promise<void> {
    if (!this.player()) {
      this.wallet.set(undefined);
      return;
    }
    try {
      this.wallet.set(await this.send<Wallet | undefined>('wallet', {}));
    } catch {
      // ignorieren, nächster Tick versucht es erneut
    }
  }

  // ==========================================================================
  // Konto / Anmeldung
  // ==========================================================================

  players(): Signal<Player[]> {
    return this._players.asReadonly();
  }

  async login(playerId: Id): Promise<void> {
    const player = await this.send<Player>('login', { playerId });
    this.player.set(player);
    this.rememberSession(player.id, player.name);
  }

  async logout(): Promise<void> {
    await this.send<void>('logout', {});
    this.player.set(null);
    this.forgetSession();
  }

  async registerPlayer(commanderName: string, homeworldName: string, role: PlayerRole, campId?: string): Promise<void> {
    const player = await this.send<Player>('registerPlayer', { commanderName, homeworldName, role, campId });
    this.player.set(player);
    this.rememberSession(player.id, player.name);
  }

  async resetGame(): Promise<void> {
    await this.send<void>('resetGame', {});
    this.player.set(null);
    this.forgetSession();
  }

  /**
   * Merkt den angemeldeten Kommandanten im `localStorage`, damit ein Neuladen
   * (F5), ein Deep-Link und ein zweiter Tab nicht auf dem Startbildschirm
   * landen. Vorher war jeder Link auf einen Kampfbericht wertlos, weil er
   * grundsätzlich zur Anmeldung führte.
   *
   * <p>Das ist bewusst KEINE Authentifizierung – es ersetzt nur die verlorene
   * Sitzung, mehr nicht. Wer sich als jemand anderes anmelden kann, konnte das
   * vorher genauso; Kennwortschutz ist ein eigenes Thema.</p>
   *
   * <p>Gemerkt wird ID UND Name. Die IDs sind fortlaufend vergeben
   * (`p_1`, `p_2`, ...) und beginnen nach jedem Serverneustart wieder von
   * vorn: Ohne den Namensabgleich landete ein Neuladen deshalb still in
   * einem FREMDEN Kommandanten – im Test übernahm die Oberfläche nach einem
   * Neustart der Instanz ungefragt `NPC-Nord-01`.</p>
   */
  private rememberSession(playerId: Id, commanderName: string): void {
    try {
      localStorage.setItem(SESSION_STORAGE_KEY, playerId);
      localStorage.setItem(SESSION_NAME_STORAGE_KEY, commanderName);
    } catch {
      // Privater Modus o. ä. – dann bleibt es beim bisherigen Verhalten.
    }
  }

  private forgetSession(): void {
    try {
      localStorage.removeItem(SESSION_STORAGE_KEY);
      localStorage.removeItem(SESSION_NAME_STORAGE_KEY);
    } catch {
      // siehe rememberSession
    }
  }

  /**
   * Meldet nach einem Neuladen automatisch wieder an – aber nur, wenn hinter
   * der gemerkten ID noch DERSELBE Kommandant steht (siehe `rememberSession`).
   * Passt der Name nicht, wird die Sitzung verworfen und der Startbildschirm
   * gezeigt, statt einen fremden Spielstand zu übernehmen.
   */
  private async restoreSession(): Promise<void> {
    let playerId: string | null = null;
    let commanderName: string | null = null;
    try {
      playerId = localStorage.getItem(SESSION_STORAGE_KEY);
      commanderName = localStorage.getItem(SESSION_NAME_STORAGE_KEY);
    } catch {
      return;
    }
    // Ohne gemerkten Namen ist die ID nicht überprüfbar (Eintrag einer älteren
    // Fassung) – dann lieber neu anmelden als vielleicht fremd.
    if (!playerId || !commanderName) {
      if (playerId) this.forgetSession();
      return;
    }
    try {
      const player = await this.send<Player>('login', { playerId });
      if (player.name !== commanderName) {
        await this.send<void>('logout', {}).catch(() => undefined);
        this.forgetSession();
        return;
      }
      this.player.set(player);
    } catch {
      // Kommandant gelöscht (z. B. Inaktivität oder Reset) – Eintrag verwerfen.
      this.forgetSession();
    }
  }

  // ==========================================================================
  // Katalog (statisch, synchron – siehe Konstruktor)
  // ==========================================================================

  productTypes(): ProductType[] { return this._productTypes; }
  buildingTypes(): BuildingType[] { return this._buildingTypes; }
  shipTypes(): ShipTypeDef[] { return this._shipTypes; }
  groundUnitTypes(): GroundUnitTypeDef[] { return this._groundUnitTypes; }

  // ==========================================================================
  // Planeten / Kolonien
  // ==========================================================================

  colonies(): Signal<Colony[]> {
    return this.poll('colonies', () => ({}), []);
  }
  coloniesInSystem(systemId: Id): Signal<Colony[]> {
    return this.poll('coloniesInSystem', () => ({ systemId }), []);
  }
  colony(id: Id): Signal<Colony | undefined> {
    return this.poll('colony', () => ({ id }), undefined);
  }
  colonyStats(id: Id): Signal<PlanetStats | undefined> {
    return this.poll('colonyStats', () => ({ id }), undefined);
  }
  consumptionCoverage(colonyId: Id): Signal<Record<Id, number>> {
    return this.poll('consumptionCoverage', () => ({ colonyId }), {});
  }

  colonySpeedBreakdown(colonyId: Id): Signal<ColonySpeedBreakdown | null> {
    return this.poll('colonySpeedBreakdown', () => ({ colonyId }), null);
  }
  planet(id: Id): Signal<Planet | undefined> {
    return this.poll('planet', () => ({ id }), undefined);
  }
  planetsInSystem(systemId: Id): Signal<Planet[]> {
    return this.poll('planetsInSystem', () => ({ systemId }), []);
  }
  colonizePlanet(planetId: Id): Promise<Colonization> {
    return this.send('colonizePlanet', { planetId });
  }
  colonizations(): Signal<Colonization[]> {
    return this.poll('colonizations', () => ({}), []);
  }
  supplyInventory(colonyId: Id): Signal<SupplyInventoryEntry[]> {
    return this.poll('supplyInventory', () => ({ colonyId }), []);
  }

  // ==========================================================================
  // Bebauung
  // ==========================================================================

  buildings(colonyId: Id): Signal<Building[]> {
    return this.poll('buildings', () => ({ colonyId }), []);
  }
  queueBuilding(colonyId: Id, buildingTypeId: Id): Promise<void> {
    return this.send('queueBuilding', { colonyId, buildingTypeId });
  }
  queueMissingBuildingMaterials(colonyId: Id, buildingTypeId: Id): Promise<Record<Id, number>> {
    return this.send('queueMissingBuildingMaterials', { colonyId, buildingTypeId });
  }
  cancelBuildingOrder(colonyId: Id, buildingId: Id): Promise<void> {
    return this.send('cancelBuildingOrder', { colonyId, buildingId });
  }
  demolishBuilding(colonyId: Id, buildingId: Id): Promise<void> {
    return this.send('demolishBuilding', { colonyId, buildingId });
  }
  activateDefense(colonyId: Id, buildingId: Id): Promise<void> {
    return this.send('activateDefense', { colonyId, buildingId });
  }
  deactivateDefense(colonyId: Id, buildingId: Id): Promise<void> {
    return this.send('deactivateDefense', { colonyId, buildingId });
  }
  buildSlots(colonyId: Id): Signal<BuildSlots | null> {
    return this.poll('buildSlots', () => ({ colonyId }), null);
  }
  housingCapacity(colonyId: Id): Signal<number> {
    return this.poll('housingCapacity', () => ({ colonyId }), 0);
  }
  powerCoverage(colonyId: Id): Signal<number> {
    return this.poll('powerCoverage', () => ({ colonyId }), 1);
  }

  isBlackout(colonyId: Id): Signal<boolean> {
    return this.poll('isBlackout', () => ({ colonyId }), false);
  }
  powerUpkeepPerHour(colonyId: Id): Signal<number> {
    return this.poll('powerUpkeepPerHour', () => ({ colonyId }), 0);
  }

  energyStorage(colonyId: Id): Signal<EnergyStorage | null> {
    return this.poll('energyStorage', () => ({ colonyId }), null);
  }

  setEnergyReserve(colonyId: Id, reserveTarget: number | null): Promise<void> {
    return this.send('setEnergyReserve', { colonyId, reserveTarget });
  }

  // ==========================================================================
  // Produktion (sequentielle Warteschlange)
  // ==========================================================================

  warehouse(colonyId: Id): Signal<WarehouseEntry[]> {
    return this.poll('warehouse', () => ({ colonyId }), []);
  }
  specializations(colonyId: Id): Signal<Specialization[]> {
    return this.poll('specializations', () => ({ colonyId }), []);
  }
  productionQueue(colonyId: Id): Signal<ProductionQueueEntry[]> {
    return this.poll('productionQueue', () => ({ colonyId }), []);
  }
  queueProduction(colonyId: Id, productTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void> {
    return this.send('queueProduction', { colonyId, productTypeId, quantity, autoProduceMissing, requeueOnComplete });
  }
  previewProductionChain(colonyId: Id, productTypeId: Id, quantity: number): Promise<ChainPlan> {
    return this.send('previewProductionChain', { colonyId, productTypeId, quantity });
  }
  resumeProduction(colonyId: Id, entryId: Id): Promise<void> {
    return this.send('resumeProduction', { colonyId, entryId });
  }
  moveProductionEntry(colonyId: Id, entryId: Id, direction: -1 | 1): Promise<void> {
    return this.send('moveProductionEntry', { colonyId, entryId, direction });
  }
  cancelProduction(colonyId: Id, entryId: Id): Promise<void> {
    return this.send('cancelProduction', { colonyId, entryId });
  }

  // ==========================================================================
  // Bevölkerung / Geld
  // ==========================================================================

  populationTrend(colonyId: Id): Signal<PopulationTrend | null> {
    return this.poll('populationTrend', () => ({ colonyId }), null);
  }

  population(colonyId: Id): Signal<Population | undefined> {
    return this.poll('population', () => ({ id: colonyId }), undefined);
  }
  moneySupplyState(planetId: Id): Signal<PopulationMoneySupplyState | undefined> {
    return this.poll('moneySupplyState', () => ({ planetId }), undefined);
  }
  populationWallet(colonyId: Id): Signal<Wallet | undefined> {
    return this.poll('populationWallet', () => ({ colonyId }), undefined);
  }
  treasuryFlowPerHour(): Signal<number> {
    return this.poll('treasuryFlowPerHour', () => ({}), 0);
  }
  transactions(): Signal<Transaction[]> {
    return this.poll('transactions', () => ({}), []);
  }
  transfer(toPlayerName: string, amount: number): Promise<void> {
    return this.send('transfer', { toPlayerName, amount });
  }

  // ==========================================================================
  // Flotten
  // ==========================================================================

  fleets(): Signal<Fleet[]> {
    return this.poll('fleets', () => ({}), []);
  }
  allFleets(): Signal<Fleet[]> {
    return this.poll('allFleets', () => ({}), []);
  }
  shipyardQueue(colonyId: Id): Signal<ShipyardQueueEntry[]> {
    return this.poll('shipyardQueue', () => ({ colonyId }), []);
  }
  queueShip(colonyId: Id, shipProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void> {
    return this.send('queueShip', { colonyId, shipProductTypeId, quantity, autoProduceMissing, requeueOnComplete });
  }
  resumeShipOrder(colonyId: Id, entryId: Id): Promise<void> {
    return this.send('resumeShipOrder', { colonyId, entryId });
  }
  cancelShipOrder(colonyId: Id, entryId: Id): Promise<void> {
    return this.send('cancelShipOrder', { colonyId, entryId });
  }
  transferShipsToFleet(colonyId: Id, shipProductTypeId: Id, quantity: number, targetFleetId: Id | null): Promise<void> {
    return this.send('transferShipsToFleet', { colonyId, shipProductTypeId, quantity, targetFleetId });
  }
  refuelFleet(fleetId: Id, quantity: number): Promise<void> {
    return this.send('refuelFleet', { fleetId, quantity });
  }
  drainFleetFuel(fleetId: Id, quantity: number): Promise<void> {
    return this.send('drainFleetFuel', { fleetId, quantity });
  }
  transferFuelBetweenFleets(fromFleetId: Id, toFleetId: Id, quantity: number): Promise<void> {
    return this.send('transferFuelBetweenFleets', { fromFleetId, toFleetId, quantity });
  }
  loadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void> {
    return this.send('loadCargo', { fleetId, productTypeId, quantity });
  }
  unloadCargo(fleetId: Id, productTypeId: Id, quantity: number): Promise<void> {
    return this.send('unloadCargo', { fleetId, productTypeId, quantity });
  }
  loadCargoFromHubDepot(fleetId: Id, productTypeId: Id, quantity: number): Promise<void> {
    return this.send('loadCargoFromHubDepot', { fleetId, productTypeId, quantity });
  }
  unloadCargoToHubDepot(fleetId: Id, productTypeId: Id, quantity: number): Promise<void> {
    return this.send('unloadCargoToHubDepot', { fleetId, productTypeId, quantity });
  }
  embarkSoldiers(fleetId: Id, quantity: number): Promise<void> {
    return this.send('embarkSoldiers', { fleetId, quantity });
  }
  disembarkSoldiers(fleetId: Id, quantity: number): Promise<void> {
    return this.send('disembarkSoldiers', { fleetId, quantity });
  }
  fleetTroopCapacity(fleetId: Id): Signal<FleetTroopCapacity | undefined> {
    return this.poll('fleetTroopCapacity', () => ({ fleetId }), undefined);
  }
  storeDrones(colonyId: Id, unitProductTypeId: Id, quantity: number): Promise<void> {
    return this.send('storeDrones', { colonyId, unitProductTypeId, quantity });
  }
  deployDrones(colonyId: Id, unitProductTypeId: Id, quantity: number): Promise<void> {
    return this.send('deployDrones', { colonyId, unitProductTypeId, quantity });
  }
  land(fleetId: Id, targetPlanetId: Id): Promise<GroundForceGroup> {
    return this.send('land', { fleetId, targetPlanetId });
  }
  moveFleet(fleetId: Id, destinationSystemId: Id, viaCarrier = false): Promise<void> {
    return this.send('moveFleet', { fleetId, destinationSystemId, viaCarrier });
  }
  renameFleet(fleetId: Id, name: string): Promise<void> {
    return this.send('renameFleet', { fleetId, name });
  }
  cancelFleetMove(fleetId: Id): Promise<void> {
    return this.send('cancelFleetMove', { fleetId });
  }
  routePreview(fleetId: Id, destinationSystemId: Id): Signal<{ hops: number; ms: number } | null> {
    return this.poll('routePreview', () => ({ fleetId, destinationSystemId }), null);
  }
  routePreviews(fleetId: Id): Signal<Record<Id, { hops: number; ms: number }>> {
    return this.poll('routePreviews', () => ({ fleetId }), {});
  }
  carrierJumpPreview(fleetId: Id, destinationSystemId: Id): Signal<CarrierJumpPreview | null> {
    return this.poll('carrierJumpPreview', () => ({ fleetId, destinationSystemId }), null);
  }

  fleetCargoCapacity(fleetId: Id, productTypeId: Id | null): Signal<FleetCargoCapacity | null> {
    return this.poll('fleetCargoCapacity', () => ({ fleetId, productTypeId }), null);
  }
  mergeFleets(targetFleetId: Id, sourceFleetId: Id): Promise<void> {
    return this.send('mergeFleets', { targetFleetId, sourceFleetId });
  }

  splitFleet(fleetId: Id, ships: Record<Id, number>, cargo: Record<Id, number>, soldiers: number, name: string): Promise<void> {
    return this.send('splitFleet', { fleetId, ships, cargo, soldiers, name });
  }

  moveFleetWithinSystem(fleetId: Id, target: FleetSystemTarget): Promise<void> {
    return this.send('moveFleetWithinSystem', { fleetId, target });
  }

  // ==========================================================================
  // Bodentruppen
  // ==========================================================================

  groundForces(colonyId: Id): Signal<GroundForceGroup | undefined> {
    return this.poll('groundForces', () => ({ colonyId }), undefined);
  }
  groundForcesAtPlanet(planetId: Id): Signal<GroundForceGroup[]> {
    return this.poll('groundForcesAtPlanet', () => ({ planetId }), []);
  }
  landedGroundForces(): Signal<GroundForceGroup[]> {
    return this.poll('landedGroundForces', () => ({}), []);
  }
  moveGroundForces(groupId: Id, targetColonyId: Id): Promise<void> {
    return this.send('moveGroundForces', { groupId, targetColonyId });
  }
  recruitmentQueue(colonyId: Id): Signal<RecruitmentQueueEntry[]> {
    return this.poll('recruitmentQueue', () => ({ colonyId }), []);
  }
  queueRecruitment(colonyId: Id, unitProductTypeId: Id, quantity: number, autoProduceMissing: boolean, requeueOnComplete: boolean): Promise<void> {
    return this.send('queueRecruitment', { colonyId, unitProductTypeId, quantity, autoProduceMissing, requeueOnComplete });
  }
  resumeRecruitment(colonyId: Id, entryId: Id): Promise<void> {
    return this.send('resumeRecruitment', { colonyId, entryId });
  }
  cancelRecruitment(colonyId: Id, entryId: Id): Promise<void> {
    return this.send('cancelRecruitment', { colonyId, entryId });
  }

  // ==========================================================================
  // Gateway / Galaxie
  // ==========================================================================

  gateway(systemId: Id): Signal<Gateway | undefined> {
    return this.poll('gateway', () => ({ systemId }), undefined);
  }
  gatewayWeights(systemId: Id): Signal<GatewayWeightEntry[]> {
    return this.poll('gatewayWeights', () => ({ systemId }), []);
  }
  visibleSystems(): Signal<System[]> {
    return this.poll('visibleSystems', () => ({}), []);
  }
  system(id: Id): Signal<System | undefined> {
    return this.poll('system', () => ({ id }), undefined);
  }
  galaxyRoutes(): Signal<{ a: Id; b: Id }[]> {
    return this.poll('galaxyRoutes', () => ({}), []);
  }
  hasVisitedSystem(systemId: Id): Signal<boolean> {
    return this.poll('hasVisitedSystem', () => ({ systemId }), false);
  }
  hasExploredSystem(systemId: Id): Signal<boolean> {
    return this.poll('hasExploredSystem', () => ({ systemId }), false);
  }
  exploreSystem(fleetId: Id): Promise<void> {
    return this.send('exploreSystem', { fleetId });
  }

  // ==========================================================================
  // Handel
  // ==========================================================================

  sellOrders(systemId: Id): Signal<SellOrder[]> {
    return this.poll('sellOrders', () => ({ systemId }), []);
  }
  createSellOrder(colonyId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist = false): Promise<void> {
    return this.send('createSellOrder', { colonyId, productTypeId, quantity, pricePerUnit, autoRelist });
  }
  createSellOrderFromFleet(fleetId: Id, productTypeId: Id, quantity: number, pricePerUnit: number, autoRelist = false): Promise<void> {
    return this.send('createSellOrderFromFleet', { fleetId, productTypeId, quantity, pricePerUnit, autoRelist });
  }
  updateSellOrderPrice(orderId: Id, pricePerUnit: number): Promise<void> {
    return this.send('updateSellOrderPrice', { orderId, pricePerUnit });
  }
  cancelSellOrder(orderId: Id): Promise<void> {
    return this.send('cancelSellOrder', { orderId });
  }
  buyFromOrder(orderId: Id, quantity: number, deliverToColonyId: Id): Promise<void> {
    return this.send('buyFromOrder', { orderId, quantity, deliverToColonyId });
  }

  hubDepot(systemId: Id): Signal<HubDepotEntry[]> {
    return this.poll('hubDepot', () => ({ systemId }), []);
  }
  hubOrders(systemId: Id): Signal<HubOrder[]> {
    return this.poll('hubOrders', () => ({ systemId }), []);
  }
  createHubSellOrder(systemId: Id, productTypeId: Id, quantity: number, pricePerUnit: number): Promise<void> {
    return this.send('createHubSellOrder', { systemId, productTypeId, quantity, pricePerUnit });
  }
  createHubBuyOrder(systemId: Id, productTypeId: Id, quantity: number, pricePerUnit: number): Promise<void> {
    return this.send('createHubBuyOrder', { systemId, productTypeId, quantity, pricePerUnit });
  }
  cancelHubOrder(orderId: Id): Promise<void> {
    return this.send('cancelHubOrder', { orderId });
  }

  // ==========================================================================
  // Diplomatie
  // ==========================================================================

  diplomaticStatus(otherPlayerId: Id): Signal<DiplomaticStatus> {
    return this.poll('diplomaticStatus', () => ({ otherPlayerId }), 'Peace');
  }
  activeWars(): Signal<DiplomaticRelation[]> {
    return this.poll('activeWars', () => ({}), []);
  }
  incomingPeaceOffers(): Signal<PeaceOffer[]> {
    return this.poll('incomingPeaceOffers', () => ({}), []);
  }
  outgoingPeaceOffers(): Signal<PeaceOffer[]> {
    return this.poll('outgoingPeaceOffers', () => ({}), []);
  }
  declareWar(otherPlayerId: Id): Promise<void> {
    return this.send('declareWar', { otherPlayerId });
  }
  offerPeace(otherPlayerId: Id): Promise<void> {
    return this.send('offerPeace', { otherPlayerId });
  }
  respondToPeaceOffer(offerId: Id, accept: boolean): Promise<void> {
    return this.send('respondToPeaceOffer', { offerId, accept });
  }

  treaties(): Signal<Treaty[]> {
    return this.poll('treaties', () => ({}), []);
  }
  incomingTreatyOffers(): Signal<TreatyOffer[]> {
    return this.poll('incomingTreatyOffers', () => ({}), []);
  }
  outgoingTreatyOffers(): Signal<TreatyOffer[]> {
    return this.poll('outgoingTreatyOffers', () => ({}), []);
  }
  hasPeaceTreaty(otherPlayerId: Id): Signal<boolean> {
    return this.poll('hasPeaceTreaty', () => ({ otherPlayerId }), false);
  }
  hasTradeAgreement(otherPlayerId: Id): Signal<boolean> {
    return this.poll('hasTradeAgreement', () => ({ otherPlayerId }), false);
  }
  offerTreaty(otherPlayerId: Id, type: TreatyType): Promise<void> {
    return this.send('offerTreaty', { otherPlayerId, type });
  }
  respondToTreatyOffer(offerId: Id, accept: boolean): Promise<void> {
    return this.send('respondToTreatyOffer', { offerId, accept });
  }
  terminateTreaty(otherPlayerId: Id, type: TreatyType): Promise<void> {
    return this.send('terminateTreaty', { otherPlayerId, type });
  }

  // ==========================================================================
  // Raumgefechte
  // ==========================================================================

  activeBattles(): Signal<Battle[]> {
    return this.poll('activeBattles', () => ({}), []);
  }
  battle(id: Id): Signal<Battle | undefined> {
    return this.poll('battle', () => ({ id }), undefined);
  }
  battleHistory(): Signal<Battle[]> {
    return this.poll('battleHistory', () => ({}), []);
  }
  battleByReportToken(token: string): Signal<Battle | undefined> {
    return this.poll('battleByReportToken', () => ({ token }), undefined);
  }
  attackableFleetsInSystem(systemId: Id): Signal<Fleet[]> {
    return this.poll('attackableFleetsInSystem', () => ({ systemId }), []);
  }
  engageBattle(attackerFleetId: Id, defenderFleetId: Id): Promise<void> {
    return this.send('engageBattle', { attackerFleetId, defenderFleetId });
  }
  retreatFromBattle(battleId: Id): Promise<void> {
    return this.send('retreatFromBattle', { battleId });
  }

  // ==========================================================================
  // Bodengefechte (Mechanik/05_..., §2, §10-12)
  // ==========================================================================

  activeGroundBattles(): Signal<GroundBattle[]> {
    return this.poll('activeGroundBattles', () => ({}), []);
  }
  groundBattle(id: Id): Signal<GroundBattle | undefined> {
    return this.poll('groundBattle', () => ({ id }), undefined);
  }
  groundBattleHistory(): Signal<GroundBattle[]> {
    return this.poll('groundBattleHistory', () => ({}), []);
  }
  groundBattleByReportToken(token: string): Signal<GroundBattle | undefined> {
    return this.poll('groundBattleByReportToken', () => ({ token }), undefined);
  }
  attackableColoniesForGroup(groupId: Id): Signal<Colony[]> {
    return this.poll('attackableColoniesForGroup', () => ({ groupId }), []);
  }
  isColonyUnderGroundAttack(colonyId: Id): Signal<boolean> {
    return this.poll('isColonyUnderGroundAttack', () => ({ colonyId }), false);
  }
  engageGroundBattle(groupId: Id, targetColonyId: Id): Promise<GroundBattle> {
    return this.send('engageGroundBattle', { groupId, targetColonyId });
  }
  retreatFromGroundBattle(battleId: Id): Promise<void> {
    return this.send('retreatFromGroundBattle', { battleId });
  }

  // ==========================================================================
  // Blockaden
  // ==========================================================================

  blockadesInSystem(systemId: Id): Signal<Blockade[]> {
    return this.poll('blockadesInSystem', () => ({ systemId }), []);
  }
  formBlockade(fleetId: Id, anchor: BlockadeAnchor): Promise<void> {
    return this.send('formBlockade', { fleetId, anchor });
  }
  liftBlockade(blockadeId: Id): Promise<void> {
    return this.send('liftBlockade', { blockadeId });
  }

  // ==========================================================================
  // Benachrichtigungen
  // ==========================================================================

  notifications(): Signal<GameNotification[]> {
    return this.poll('notifications', () => ({}), []);
  }
  unreadNotificationCount(): Signal<number> {
    return this.poll('unreadNotificationCount', () => ({}), 0);
  }
  setNotificationKeep(id: Id, keep: boolean): Promise<void> {
    return this.send('setNotificationKeep', { id, keep });
  }

  markNotificationRead(id: Id): Promise<void> {
    return this.send('markNotificationRead', { id });
  }
  markAllNotificationsRead(): Promise<void> {
    return this.send('markAllNotificationsRead', {});
  }

  // ==========================================================================
  // Nachrichten (ausschließlich Spieler-zu-Spieler)
  // ==========================================================================

  inbox(): Signal<Message[]> {
    return this.poll('inbox', () => ({}), []);
  }
  sentMessages(): Signal<Message[]> {
    return this.poll('sentMessages', () => ({}), []);
  }
  unreadMessageCount(): Signal<number> {
    return this.poll('unreadMessageCount', () => ({}), 0);
  }
  sendMessage(toPlayerId: Id, subject: string, body: string): Promise<void> {
    return this.send('sendMessage', { toPlayerId, subject, body });
  }
  setMessageKeep(id: Id, keep: boolean): Promise<void> {
    return this.send('setMessageKeep', { id, keep });
  }

  markMessageRead(id: Id): Promise<void> {
    return this.send('markMessageRead', { id });
  }

  // ==========================================================================
  // Universums-Statistik
  // ==========================================================================

  universeStats(): Signal<UniverseStatSnapshot[]> {
    return this.poll('universeStats', () => ({}), []);
  }

  victory(): Signal<GameVictory | null> {
    return this.poll('victory', () => ({}), null);
  }
}
