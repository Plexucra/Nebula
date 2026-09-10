import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CARRIER_TRANSIT_FUEL_FACTOR, CARRIER_TRANSIT_TIME_FACTOR } from '../../core/shared-constants';
import { GAME_API } from '../../core/sim/game-api.token';
import { CarrierJumpPreview, Colony, Fleet, Id, ShipTypeDef } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

type FleetPanel = 'load' | 'unload' | 'sell' | 'move' | 'land' | 'landTroops' | 'transfer' | 'attack' | 'merge' | 'split' | null;

@Component({
  selector: 'app-fleets-overview',
  standalone: true,
  imports: [RouterLink, FormsModule, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fleets-overview.component.html',
  styleUrl: './fleets-overview.component.scss',
})
export class FleetsOverviewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);

  protected readonly colonies = this.api.colonies();
  protected readonly fleets = this.api.fleets();
  protected readonly allFleets = this.api.allFleets();
  /** Katalog kommt asynchron – bei jedem Zugriff frisch lesen statt einmalig einzufrieren. */
  protected get shipTypes() { return this.api.productTypes().filter(p => p.category === 'Ship'); }
  protected readonly allSystems = this.api.visibleSystems();
  protected readonly countdown = formatCountdown;

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /**
   * Steckbrief eines Schiffstyps für die Bauauswahl. Vorher war das ein reines
   * Namens-Auswahlfeld: weder Preis noch Bauzeit, Frachtraum, Truppenplätze,
   * Trägerkapazität oder Konterverhältnis waren zu sehen – man kaufte blind.
   *
   * <p>Kampfwerte gibt es bewusst keine: Schaden und Haltbarkeit leiten sich
   * ausschließlich aus dem Produktionsaufwand ab (Mechanik/04_..., §2-3), also
   * ist der Arbeitsaufwand die aussagekräftige Zahl.</p>
   */
  protected shipFacts(productTypeId: Id): string {
    const def = this.shipDef(productTypeId);
    const product = this.api.productTypes().find(p => p.id === productTypeId);
    if (!def || !product) return '';
    const parts: string[] = [];
    parts.push(`${Math.round(product.workHoursPerUnit).toLocaleString('de-DE')} Ah`);
    if (def.cargoMassKg > 0) parts.push(`Fracht ${this.formatTons(def.cargoMassKg)}`);
    if (def.troopCapacity > 0) parts.push(`${def.troopCapacity} Soldaten`);
    if (def.carrierSlotCapacity > 0) parts.push(`Trägerdeck ${def.carrierSlotCapacity} Slots`);
    if (def.carrierSlotUsage > 0) {
      const slots = Math.round(def.carrierSlotUsage * 100) / 100;
      parts.push(`belegt ${slots.toLocaleString('de-DE')} ${slots === 1 ? 'Slot' : 'Slots'} im Träger`);
    }
    if (def.countersClass) parts.push(`kontert ${this.shipClassLabel(def.countersClass)}`);
    return parts.join(' · ');
  }

  protected shipClassLabel(shipClass: string): string {
    const byClass: Record<string, string> = {
      Corvette: 'Korvette', Destroyer: 'Zerstörer', Cruiser: 'Kreuzer', Freighter: 'Frachter',
      Carrier: 'Trägerschiff', TroopTransport: 'Mannschaftstransporter', ColonyShip: 'Kolonisationsschiff',
    };
    return byClass[shipClass] ?? shipClass;
  }

  /**
   * Massen in Tonnen bzw. Millionen Tonnen statt in Kilogramm. "113.416.791 kg"
   * ist keine ablesbare Zahl – die Schiffe dieses Spiels bewegen Kilotonnen.
   */
  protected formatTons(kg: number): string {
    const tons = kg / 1000;
    if (tons >= 1_000_000) return `${(tons / 1_000_000).toLocaleString('de-DE', { maximumFractionDigits: 1 })} Mio. t`;
    if (tons >= 1000) return `${(tons / 1000).toLocaleString('de-DE', { maximumFractionDigits: 1 })} kt`;
    return `${tons.toLocaleString('de-DE', { maximumFractionDigits: 0 })} t`;
  }

  /**
   * Reichweite des Tanks in Sprüngen – die Zahl, die den Kommandanten
   * interessiert. Der Verbrauch je Sprung hängt an der Schiffsmasse
   * (Umsetzungskonzept/34_...md, F7), ein voller Tank reicht deshalb bei jeder
   * Flotte für dieselbe Zahl Sprünge; nur der Füllstand entscheidet.
   */
  protected fuelRangeInJumps(fleet: Fleet): number {
    const perHop = this.jumpFuelPerHop(fleet);
    if (perHop <= 0) return 0;
    return Math.floor(fleet.fuelCapsules / perHop);
  }

  protected shipyardLevel(colonyId: Id): number {
    return this.api.buildings(colonyId)().find(b => b.typeId === 'b_shipyard')?.level ?? 0;
  }
  protected shipyardQueue(colonyId: Id) {
    return this.api.shipyardQueue(colonyId)();
  }
  /** Fertig gebaute, noch keiner Flotte zugeordnete Schiffe – liegen wie normale Ware im Lager, siehe `GameApi.transferShipsToFleet`. */
  protected looseShipsAt(colonyId: Id) {
    return this.api.warehouse(colonyId)().filter(w => this.shipTypes.some(s => s.id === w.productTypeId));
  }
  protected fleetsStationedAt(colonyId: Id): Fleet[] {
    return this.fleets().filter(f => f.status === 'Stationed' && f.locationColonyId === colonyId);
  }
  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }
  protected colonyName(colonyId: Id | null): string {
    if (!colonyId) return '—';
    return this.api.colony(colonyId)()?.name ?? '—';
  }
  protected colonyOwnedByMe(colonyId: Id | null): boolean {
    if (!colonyId) return false;
    return this.api.colony(colonyId)()?.ownerId === this.api.player()?.id;
  }
  protected systemName(systemId: Id): string {
    return this.api.system(systemId)()?.name ?? '—';
  }
  protected planetName(planetId: Id): string {
    return this.api.planet(planetId)()?.name ?? '—';
  }
  /** Verbleibende Route einer unterwegs befindlichen Flotte als Namensliste – erster Eintrag ist der gerade laufende Sprung. */
  protected fleetRouteNames(fleet: Fleet): string[] {
    const names: string[] = [];
    if (fleet.destinationSystemId) names.push(this.systemName(fleet.destinationSystemId));
    for (const id of fleet.pendingHops) names.push(this.systemName(id));
    return names;
  }
  /** Bricht einen laufenden Flug jederzeit ab – der aktuelle Sprung wird noch zu Ende geflogen, siehe `GameApi.cancelFleetMove`. */
  protected async cancelFlight(fleet: Fleet): Promise<void> {
    await this.run('cancelFlight:' + fleet.id, () => this.api.cancelFleetMove(fleet.id));
  }
  protected coloniesInFleetSystem(fleet: Fleet): Colony[] {
    return this.api.coloniesInSystem(fleet.systemId)();
  }
  /** Stationiert, nicht gelandet, an einer Handelsgilde-Station – dort gibt es statt eines Kolonielagers ein unbegrenztes Depot (Umsetzungskonzept/22_...md). */
  protected isAtTradeHub(fleet: Fleet): boolean {
    return fleet.locationColonyId === null && (this.api.system(fleet.systemId)()?.isTradeHub ?? false);
  }

  selection: Record<Id, { productId: Id; qty: number; autoProduceMissing: boolean; requeueOnComplete: boolean }> = {};

  protected selFor(colony: Colony): { productId: Id; qty: number; autoProduceMissing: boolean; requeueOnComplete: boolean } {
    if (!this.selection[colony.id]) {
      this.selection[colony.id] = { productId: this.shipTypes[0]?.id ?? '', qty: 1, autoProduceMissing: true, requeueOnComplete: false };
    }
    return this.selection[colony.id];
  }

  protected queueProgressPct(entry: { status: string; startedAt: number | null; endsAt: number | null }): number {
    if (entry.status !== 'running' || entry.startedAt === null || entry.endsAt === null) return 0;
    const total = entry.endsAt - entry.startedAt;
    if (total <= 0) return 100;
    return Math.min(100, Math.max(0, ((this.clock.now() - entry.startedAt) / total) * 100));
  }

  protected queueStatusLabel(entry: { status: string }): string {
    switch (entry.status) {
      case 'running': return 'läuft';
      case 'stopped': return 'gestoppt';
      case 'done': return 'fertig';
      default: return 'wartet';
    }
  }

  private async run(key: string, action: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    this.busy.set(key);
    try {
      await action();
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Aktion fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  protected async queueShip(colony: Colony): Promise<void> {
    const sel = this.selFor(colony);
    await this.run('queueship:' + colony.id, () => this.api.queueShip(colony.id, sel.productId, sel.qty, sel.autoProduceMissing, sel.requeueOnComplete));
  }

  protected async resumeOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.run('resume:' + entryId, () => this.api.resumeShipOrder(colonyId, entryId));
  }

  protected async cancelOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.run('cancel:' + entryId, () => this.api.cancelShipOrder(colonyId, entryId));
  }

  // --- "In Flotte überführen" (lose Schiffe im Lager) -----------------------

  protected readonly transferDraftQty: Partial<Record<Id, number>> = {};
  protected readonly transferTarget: Partial<Record<Id, Id | 'new'>> = {};

  protected openTransfer(shipProductTypeId: Id, maxQty: number): void {
    if (this.transferDraftQty[shipProductTypeId] === undefined) this.transferDraftQty[shipProductTypeId] = Math.floor(maxQty);
    if (this.transferTarget[shipProductTypeId] === undefined) this.transferTarget[shipProductTypeId] = 'new';
  }

  protected async submitTransfer(colonyId: Id, shipProductTypeId: Id): Promise<void> {
    const qty = this.transferDraftQty[shipProductTypeId] ?? 0;
    const target = this.transferTarget[shipProductTypeId] ?? 'new';
    if (qty <= 0) return;
    await this.run('transfer:' + shipProductTypeId, () =>
      this.api.transferShipsToFleet(colonyId, shipProductTypeId, qty, target === 'new' ? null : target));
  }

  // --- Flotten-Aktionen -------------------------------------------------------

  protected readonly openPanel = signal<{ fleetId: Id; panel: FleetPanel } | null>(null);

  protected togglePanel(fleetId: Id, panel: FleetPanel): void {
    const cur = this.openPanel();
    this.openPanel.set(cur?.fleetId === fleetId && cur.panel === panel ? null : { fleetId, panel });
  }
  protected isPanelOpen(fleetId: Id, panel: FleetPanel): boolean {
    const cur = this.openPanel();
    return cur?.fleetId === fleetId && cur.panel === panel;
  }

  /**
   * Frachtkapazität/Auslastung und maximal ladbare Stückzahl kommen fertig
   * vom Backend (`fleetCargoCapacity`) – die Kapazitätsgrenze ist eine
   * SPIELREGEL, die `loadCargo` durchsetzt, und darf hier nicht abweichend
   * nachgerechnet werden (Umsetzungskonzept/15_...md, Auftrag 3).
   */
  private capacity(fleet: Fleet, productTypeId: Id | null = null) {
    return this.api.fleetCargoCapacity(fleet.id, productTypeId)();
  }

  protected fleetCargoMassKg(fleet: Fleet): number {
    return this.capacity(fleet)?.usedMassKg ?? 0;
  }
  protected fleetCargoVolumeM3(fleet: Fleet): number {
    return this.capacity(fleet)?.usedVolumeM3 ?? 0;
  }
  protected fleetCapacityMassKg(fleet: Fleet): number {
    return this.capacity(fleet)?.capacityMassKg ?? 0;
  }
  protected fleetCapacityVolumeM3(fleet: Fleet): number {
    return this.capacity(fleet)?.capacityVolumeM3 ?? 0;
  }

  /** Maximal ladbare Menge eines Produkts: begrenzt durch Lagerbestand UND verbleibende Massen-/Volumenkapazität der Flotte (vom Backend berechnet). */
  protected maxLoadable(fleet: Fleet, productTypeId: Id): number {
    return this.capacity(fleet, productTypeId)?.maxLoadableQuantity ?? 0;
  }

  protected readonly loadProductId: Partial<Record<Id, Id>> = {};
  protected readonly loadQty: Partial<Record<Id, number>> = {};

  /** Ladbare Waren: aus dem Kolonielager beim Landen, aus dem Stations-Depot an einer Handelsgilde-Station. */
  protected loadableProducts(fleet: Fleet): { productTypeId: Id; stock: number }[] {
    if (fleet.locationColonyId) {
      return this.api.warehouse(fleet.locationColonyId)()
        .filter(w => !this.shipTypes.some(s => s.id === w.productTypeId))
        .map(w => ({ productTypeId: w.productTypeId, stock: w.quantity }));
    }
    if (this.isAtTradeHub(fleet)) {
      return this.api.hubDepot(fleet.systemId)().map(d => ({ productTypeId: d.productTypeId, stock: d.quantity }));
    }
    return [];
  }

  protected async submitLoad(fleet: Fleet): Promise<void> {
    const productTypeId = this.loadProductId[fleet.id];
    const qty = this.loadQty[fleet.id] ?? 0;
    if (!productTypeId || qty <= 0) return;
    const action = fleet.locationColonyId
      ? () => this.api.loadCargo(fleet.id, productTypeId, qty)
      : () => this.api.loadCargoFromHubDepot(fleet.id, productTypeId, qty);
    await this.run('load:' + fleet.id, action);
  }

  // --- Truppen (Umsetzungskonzept/28_...md) ---------------------------------
  // Plätze, Belegung und einschiffbare Menge kommen wie die Frachtkapazität
  // FERTIG vom Backend – `troopCapacity` ist eine Spielregel, die
  // `TroopTransportCommands.embarkSoldiers` durchsetzt.
  private troops(fleet: Fleet) {
    return this.api.fleetTroopCapacity(fleet.id)();
  }
  protected troopCapacity(fleet: Fleet): number {
    return this.troops(fleet)?.capacitySoldiers ?? 0;
  }
  protected soldiersAboard(fleet: Fleet): number {
    return this.troops(fleet)?.soldiersAboard ?? 0;
  }
  protected maxEmbarkable(fleet: Fleet): number {
    return this.troops(fleet)?.maxEmbarkableQuantity ?? 0;
  }

  protected readonly troopQty: Partial<Record<Id, number>> = {};

  protected async submitEmbark(fleet: Fleet): Promise<void> {
    const qty = this.troopQty[fleet.id] ?? 0;
    if (qty <= 0) return;
    await this.run('troops:' + fleet.id, () => this.api.embarkSoldiers(fleet.id, qty));
  }

  protected async submitDisembark(fleet: Fleet): Promise<void> {
    const qty = this.troopQty[fleet.id] ?? 0;
    if (qty <= 0) return;
    await this.run('troops:' + fleet.id, () => this.api.disembarkSoldiers(fleet.id, qty));
  }

  // --- Landung (Umsetzungskonzept/04_...md) ---------------------------------
  // Nicht zu verwechseln mit dem "Landen"-Panel oben (Andocken der FLOTTE an
  // einer Kolonie, moveFleetWithinSystem): hier geht es um die Bodentruppen an
  // Bord/in der Fracht, die den Planeten unter sich verlassen.
  private droneCargoAboard(fleet: Fleet): boolean {
    return fleet.cargo.some(c => this.api.productTypes().find(p => p.id === c.productTypeId)?.category === 'GroundUnit');
  }
  protected canLandTroops(fleet: Fleet): boolean {
    return fleet.locationType === 'PlanetOrbit' && (this.soldiersAboard(fleet) > 0 || this.droneCargoAboard(fleet));
  }
  protected async submitLandTroops(fleet: Fleet): Promise<void> {
    if (!fleet.locationPlanetId) return;
    await this.run('landTroops:' + fleet.id, async () => {
      await this.api.land(fleet.id, fleet.locationPlanetId!);
      this.openPanel.set(null);
    });
  }

  protected readonly refuelQty: Partial<Record<Id, number>> = {};

  /**
   * Fassungsvermögen des Tanks: `ShipTypeDef.fuelTankCapacity` je Schiff der
   * Flotte. Der Wert kommt fertig aus dem Katalog (der Server leitet ihn aus
   * der Schiffsmasse ab) – hier steht bewusst keine zweite Formel.
   */
  protected fuelTankCapacity(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + (this.shipDef(g.shipProductTypeId)?.fuelTankCapacity ?? 0) * g.quantity, 0);
  }

  /** Kapseln, die diese Flotte für EINEN Sprung verbraucht – Summe über die Schiffsmassen. */
  protected jumpFuelPerHop(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + (this.shipDef(g.shipProductTypeId)?.jumpFuelPerHop ?? 0) * g.quantity, 0);
  }

  protected async submitRefuel(fleet: Fleet): Promise<void> {
    const qty = this.refuelQty[fleet.id] ?? 0;
    if (qty <= 0) return;
    await this.run('refuel:' + fleet.id, () => this.api.refuelFleet(fleet.id, qty));
  }

  protected async submitDrain(fleet: Fleet): Promise<void> {
    const qty = this.refuelQty[fleet.id] ?? 0;
    if (qty <= 0) return;
    await this.run('drain:' + fleet.id, () => this.api.drainFleetFuel(fleet.id, qty));
  }

  /** Nur GANZE Kapseln verlassen den Tank – der Bruchteil ist die angebrochene Kapsel. */
  protected drainableFuel(fleet: Fleet): number {
    return Math.floor(fleet.fuelCapsules);
  }

  protected readonly fuelTargetFleetId: Partial<Record<Id, Id>> = {};
  protected readonly fuelTransferQty: Partial<Record<Id, number>> = {};

  /** Andere eigene, stationierte Flotten im selben System – mögliche Treibstoffempfänger. */
  protected otherFleetsInSystem(fleet: Fleet): Fleet[] {
    return this.fleets().filter(f => f.id !== fleet.id && f.systemId === fleet.systemId && f.status === 'Stationed');
  }

  protected async submitFuelTransfer(fleet: Fleet): Promise<void> {
    const target = this.fuelTargetFleetId[fleet.id];
    const qty = this.fuelTransferQty[fleet.id] ?? 0;
    if (!target || qty <= 0) return;
    await this.run('fueltransfer:' + fleet.id, () => this.api.transferFuelBetweenFleets(fleet.id, target, qty));
  }

  protected readonly unloadQty: Partial<Record<Id, number>> = {};

  protected async submitUnload(fleet: Fleet, productTypeId: Id): Promise<void> {
    const qty = this.unloadQty[fleet.id + ':' + productTypeId] ?? 0;
    if (qty <= 0) return;
    const action = fleet.locationColonyId
      ? () => this.api.unloadCargo(fleet.id, productTypeId, qty)
      : () => this.api.unloadCargoToHubDepot(fleet.id, productTypeId, qty);
    await this.run('unload:' + fleet.id, action);
  }

  protected readonly sellProductId: Partial<Record<Id, Id>> = {};
  protected readonly sellQty: Partial<Record<Id, number>> = {};
  protected readonly sellPrice: Partial<Record<Id, number>> = {};
  protected readonly sellAutoRelist: Partial<Record<Id, boolean>> = {};

  protected async submitSellFromFleet(fleet: Fleet): Promise<void> {
    const productTypeId = this.sellProductId[fleet.id];
    const qty = this.sellQty[fleet.id] ?? 0;
    const price = this.sellPrice[fleet.id] ?? 0;
    const autoRelist = this.sellAutoRelist[fleet.id] ?? true;
    if (!productTypeId || qty <= 0 || price <= 0) return;
    await this.run('sell:' + fleet.id, () => this.api.createSellOrderFromFleet(fleet.id, productTypeId, qty, price, autoRelist));
  }

  /** Umbenennen: der Entwurf je Flotte, `undefined` = Feld zu. */
  protected readonly renameDraft: Partial<Record<Id, string>> = {};

  protected startRename(fleet: Fleet): void {
    this.renameDraft[fleet.id] = fleet.name;
  }

  protected cancelRename(fleet: Fleet): void {
    this.renameDraft[fleet.id] = undefined;
  }

  protected async submitRename(fleet: Fleet): Promise<void> {
    const name = (this.renameDraft[fleet.id] ?? '').trim();
    if (!name || name === fleet.name) {
      this.renameDraft[fleet.id] = undefined;
      return;
    }
    await this.run('rename:' + fleet.id, async () => {
      await this.api.renameFleet(fleet.id, name);
      this.renameDraft[fleet.id] = undefined;
    });
  }

  protected readonly moveDestination: Partial<Record<Id, Id>> = {};
  /** Trägersprung statt Gateway-Kette – siehe `carrierPreview`. */
  protected readonly moveViaCarrier: Partial<Record<Id, boolean>> = {};

  protected readonly carrierTimeFactor = CARRIER_TRANSIT_TIME_FACTOR;
  protected readonly carrierFuelFactor = CARRIER_TRANSIT_FUEL_FACTOR;

  /**
   * Zielsysteme nach ENTFERNUNG sortiert, mit Sprunganzahl im Text. Vorher war
   * das eine unsortierte Liste aller 203 Systeme ohne jede Angabe – man wählte
   * blind aus Namen.
   */
  protected moveTargets(fleet: Fleet): { id: Id; label: string }[] {
    // EINE Abfrage für alle Ziele (`routePreviews`) statt einer je System:
    // vorher liefen hier 200 Routenabfragen je Sekunde, solange das Feld offen war.
    const routes = this.api.routePreviews(fleet.id)();
    return this.allSystems()
      .filter(s => s.id !== fleet.systemId)
      .map(s => {
        const route = routes[s.id];
        return {
          id: s.id,
          hops: route?.hops ?? Number.POSITIVE_INFINITY,
          label: route
            ? `${s.name} · ${route.hops} ${route.hops === 1 ? 'Sprung' : 'Sprünge'} · ${this.countdown(route.ms)}`
            : `${s.name} · keine Gateway-Route`,
        };
      })
      .sort((a, b) => a.hops - b.hops || a.label.localeCompare(b.label, 'de'))
      .map(({ id, label }) => ({ id, label }));
  }

  /** Gateway-Routenvorschau für das gewählte Ziel – am Ort der Entscheidung, nicht erst nach dem Start. */
  protected movePreview(fleet: Fleet): { hops: number; ms: number } | null {
    const destination = this.moveDestination[fleet.id];
    return destination ? (this.api.routePreviews(fleet.id)()[destination] ?? null) : null;
  }

  /** Vorschau des Trägersprungs – Slot-Bilanz, Dauer, Treibstoff. */
  protected carrierPreview(fleet: Fleet): CarrierJumpPreview | null {
    const destination = this.moveDestination[fleet.id];
    return destination ? this.api.carrierJumpPreview(fleet.id, destination)() : null;
  }

  /** Hat die Flotte überhaupt einen Träger an Bord? */
  protected hasCarrier(fleet: Fleet): boolean {
    return fleet.ships.some(g => (this.shipDef(g.shipProductTypeId)?.carrierSlotCapacity ?? 0) > 0);
  }

  protected shipDef(productTypeId: Id): ShipTypeDef | undefined {
    return this.api.shipTypes().find(s => s.productTypeId === productTypeId);
  }

  protected async submitMove(fleet: Fleet): Promise<void> {
    const destinationSystemId = this.moveDestination[fleet.id];
    if (!destinationSystemId) return;
    const viaCarrier = this.moveViaCarrier[fleet.id] === true;
    await this.run('move:' + fleet.id, async () => {
      await this.api.moveFleet(fleet.id, destinationSystemId, viaCarrier);
      this.openPanel.set(null);
    });
  }

  protected async submitLand(fleet: Fleet, colonyId: Id): Promise<void> {
    await this.run('land:' + fleet.id, async () => {
      await this.api.moveFleetWithinSystem(fleet.id, { kind: 'ColonyOrbit', colonyId });
      this.openPanel.set(null);
    });
  }

  protected async undock(fleet: Fleet): Promise<void> {
    await this.run('undock:' + fleet.id, () => this.api.moveFleetWithinSystem(fleet.id, { kind: 'System' }));
  }

  /** Erforschen deckt die Rohstoffkonzentration des aktuellen Systems auf – geht mit jedem Schiffstyp, siehe `GameApi.exploreSystem`. */
  protected canExplore(fleet: Fleet): boolean {
    return fleet.status === 'Stationed' && !this.api.hasExploredSystem(fleet.systemId)();
  }

  protected async exploreSystem(fleet: Fleet): Promise<void> {
    await this.run('explore:' + fleet.id, () => this.api.exploreSystem(fleet.id));
  }

  // --- Zusammenstellung: zusammenlegen und aufteilen (Umsetzungskonzept/33_...md) ---

  /**
   * Eigene, stationierte Flotten am GENAU selben Ort – dasselbe System und
   * dieselbe Position darin. Der Server prüft dieselbe Bedingung noch einmal
   * (`mergeFleets`); hier steht sie nur, damit die Auswahlliste keine Ziele
   * anbietet, die ohnehin abgelehnt würden.
   */
  protected mergeCandidates(fleet: Fleet): Fleet[] {
    return this.fleets().filter(f => f.id !== fleet.id
      && f.status === 'Stationed'
      && f.systemId === fleet.systemId
      && f.locationType === fleet.locationType
      && f.locationColonyId === fleet.locationColonyId
      && f.locationPlanetId === fleet.locationPlanetId);
  }

  protected readonly mergeSource: Partial<Record<Id, Id>> = {};

  protected async submitMerge(fleet: Fleet): Promise<void> {
    const sourceFleetId = this.mergeSource[fleet.id];
    if (!sourceFleetId) return;
    await this.run('merge:' + fleet.id, async () => {
      await this.api.mergeFleets(fleet.id, sourceFleetId);
      this.mergeSource[fleet.id] = undefined;
      this.openPanel.set(null);
    });
  }

  /** Eingaben des Aufteilen-Formulars, Schlüssel `fleetId|produktId` – eine flache Map reicht, das Formular ist je Flotte nur einmal offen. */
  protected readonly splitShipQty: Partial<Record<string, number>> = {};
  protected readonly splitCargoQty: Partial<Record<string, number>> = {};
  protected readonly splitSoldiers: Partial<Record<Id, number>> = {};
  protected readonly splitName: Partial<Record<Id, string>> = {};

  protected splitKey(fleetId: Id, productTypeId: Id): string {
    return fleetId + '|' + productTypeId;
  }

  /**
   * Sammelt die Eingaben und schickt sie als ein Kommando. Ob die Aufteilung
   * zulässig ist (Fracht und Soldaten müssen auf BEIDEN Seiten in die Schiffe
   * passen), entscheidet ausschließlich der Server – die Regel wird hier nicht
   * nachgebaut (Umsetzungskonzept/15_...md, Auftrag 3); seine Fehlermeldung
   * landet in `error()`.
   */
  protected async submitSplit(fleet: Fleet): Promise<void> {
    const ships: Record<Id, number> = {};
    for (const g of fleet.ships) {
      const qty = Math.floor(this.splitShipQty[this.splitKey(fleet.id, g.shipProductTypeId)] ?? 0);
      if (qty > 0) ships[g.shipProductTypeId] = qty;
    }
    const cargo: Record<Id, number> = {};
    for (const c of fleet.cargo) {
      const qty = Math.floor(this.splitCargoQty[this.splitKey(fleet.id, c.productTypeId)] ?? 0);
      if (qty > 0) cargo[c.productTypeId] = qty;
    }
    const soldiers = Math.floor(this.splitSoldiers[fleet.id] ?? 0);
    if (Object.keys(ships).length === 0) {
      this.error.set('Für eine neue Flotte muss mindestens ein Schiff abgespalten werden.');
      return;
    }
    await this.run('split:' + fleet.id, async () => {
      await this.api.splitFleet(fleet.id, ships, cargo, soldiers, this.splitName[fleet.id] ?? '');
      for (const g of fleet.ships) this.splitShipQty[this.splitKey(fleet.id, g.shipProductTypeId)] = undefined;
      for (const c of fleet.cargo) this.splitCargoQty[this.splitKey(fleet.id, c.productTypeId)] = undefined;
      this.splitSoldiers[fleet.id] = undefined;
      this.splitName[fleet.id] = undefined;
      this.openPanel.set(null);
    });
  }

  // --- Kampf --------------------------------------------------------------

  /** Gegnerische, im selben System stationierte Flotten, gegen die im Krieg ein Angriff möglich ist (siehe `GameApi.attackableFleetsInSystem`). */
  protected attackableFleets(fleet: Fleet): Fleet[] {
    return this.api.attackableFleetsInSystem(fleet.systemId)();
  }

  /**
   * Erklärt, warum kein Angriff möglich ist, wenn fremde Flotten im selben
   * System stehen. Die Regel ("nur blockierende Flotten sind angreifbar") stand
   * vorher ausschließlich als Kommentar im Quelltext – in der Oberfläche
   * verschwand der Angriffsknopf einfach kommentarlos, und eine Kampfflotte im
   * feindlichen Heimatorbit hatte keinerlei erkennbare Handlungsmöglichkeit.
   */
  protected attackBlockedHint(fleet: Fleet): string | null {
    if (fleet.status !== 'Stationed') return null;
    if (this.battleForFleet(fleet.id)) return null;
    if (this.attackableFleets(fleet).length > 0) return null;
    const ownerId = this.api.player()?.id;
    const foreign = this.allFleets().filter(f => f.systemId === fleet.systemId && f.ownerId !== ownerId);
    if (foreign.length === 0) return null;
    return 'Hier stehen fremde Flotten, angreifbar ist aber keine: Ein Gefecht lässt sich nur gegen eine Flotte '
      + 'eröffnen, die im Krieg eine Blockade hält. Solange die Gegenseite nicht blockiert, ist sie unangreifbar – '
      + 'eine eigene Blockade an derselben Stelle verhindert zudem, dass die Gegenseite dort blockieren kann.';
  }

  /** Das laufende Gefecht, in dem diese eigene Flotte gerade steht (Angreifer ODER Verteidiger) – `undefined`, wenn keins läuft. */
  protected battleForFleet(fleetId: Id) {
    return this.api.activeBattles()().find(b => b.attackerFleetId === fleetId || b.defenderFleetId === fleetId);
  }

  /** Schiffszahl einer (auch fremden) Flotte – der Angriffsknopf nannte vorher nur "3 Schiffstypen". */
  protected shipCountOf(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + g.quantity, 0);
  }

  protected async submitAttack(fleet: Fleet, defenderFleetId: Id): Promise<void> {
    const defender = this.allFleets().find(f => f.id === defenderFleetId);
    const own = this.shipCountOf(fleet);
    const theirs = defender ? this.shipCountOf(defender) : 0;
    // Ein Gefecht ist unumkehrbar und kostet in Sekunden Schiffe – vorher startete
    // es mit einem einzigen Klick ohne Rückfrage.
    if (!confirm(`Gefecht eröffnen: ${own} eigene Schiffe gegen ${theirs} Schiffe von "${defender?.name ?? 'unbekannt'}"?`
        + ' Der Kampf beginnt sofort und läuft, bis eine Seite vernichtet ist oder sich zurückzieht.')) return;
    await this.run('attack:' + fleet.id, async () => {
      await this.api.engageBattle(fleet.id, defenderFleetId);
      this.openPanel.set(null);
    });
  }

  protected async retreat(battleId: Id): Promise<void> {
    await this.run('retreat:' + battleId, () => this.api.retreatFromBattle(battleId));
  }

  protected nextBattleTickIn(nextTickAt: number): number {
    return nextTickAt - this.clock.now();
  }

  /** Name der GEGNERISCHEN (fremden) Flotte in einem Gefecht, aus Sicht von `ownFleetId` – über `allFleets()`, da die Gegnerflotte einem anderen Kommandanten gehört und nicht in `fleets()` (nur eigene Flotten) auftaucht. */
  protected opposingFleetName(battle: { attackerFleetId: Id; defenderFleetId: Id }, ownFleetId: Id): string {
    const opposingId = battle.attackerFleetId === ownFleetId ? battle.defenderFleetId : battle.attackerFleetId;
    return this.allFleets().find(f => f.id === opposingId)?.name ?? '—';
  }

  /** Kurzer Anzeigetext der eigenen Verluste im letzten Kampf-Tick, z. B. "Korvette × 1" – `null`, wenn im letzten Tick nichts verloren ging. */
  protected lastOwnLossesText(battle: { ticks: { attackerLosses: Record<Id, number>; defenderLosses: Record<Id, number> }[]; attackerFleetId: Id }, ownFleetId: Id): string | null {
    const lastTick = battle.ticks[battle.ticks.length - 1];
    if (!lastTick) return null;
    const losses = battle.attackerFleetId === ownFleetId ? lastTick.attackerLosses : lastTick.defenderLosses;
    const parts = Object.entries(losses).map(([productTypeId, count]) => `${this.productName(productTypeId)} × ${count}`);
    return parts.length > 0 ? parts.join(', ') : null;
  }
}
