import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colony, Fleet, Id } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

type FleetPanel = 'load' | 'unload' | 'sell' | 'move' | 'land' | 'transfer' | 'attack' | null;

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
  protected readonly shipTypes = this.api.productTypes().filter(p => p.category === 'Ship');
  protected readonly allSystems = this.api.visibleSystems();
  protected readonly countdown = formatCountdown;

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

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

  protected fleetCargoMassKg(fleet: Fleet): number {
    return fleet.cargo.reduce((sum, c) => sum + this.api.productTypes().find(p => p.id === c.productTypeId)!.massKg * c.quantity, 0);
  }
  protected fleetCargoVolumeM3(fleet: Fleet): number {
    return fleet.cargo.reduce((sum, c) => sum + this.api.productTypes().find(p => p.id === c.productTypeId)!.volumeM3 * c.quantity, 0);
  }
  protected fleetCapacityMassKg(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + (this.api.shipTypes().find(s => s.productTypeId === g.shipProductTypeId)?.cargoMassKg ?? 0) * g.quantity, 0);
  }
  protected fleetCapacityVolumeM3(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + (this.api.shipTypes().find(s => s.productTypeId === g.shipProductTypeId)?.cargoVolumeM3 ?? 0) * g.quantity, 0);
  }

  /** Maximal ladbare Menge eines Produkts: begrenzt durch Lagerbestand UND verbleibende Massen-/Volumenkapazität der Flotte. */
  protected maxLoadable(fleet: Fleet, productTypeId: Id): number {
    if (!fleet.locationColonyId) return 0;
    const stock = this.api.warehouse(fleet.locationColonyId)().find(w => w.productTypeId === productTypeId)?.quantity ?? 0;
    const product = this.api.productTypes().find(p => p.id === productTypeId);
    if (!product) return 0;
    const remainingMass = this.fleetCapacityMassKg(fleet) - this.fleetCargoMassKg(fleet);
    const remainingVolume = this.fleetCapacityVolumeM3(fleet) - this.fleetCargoVolumeM3(fleet);
    const byMass = product.massKg > 0 ? Math.floor(remainingMass / product.massKg) : Infinity;
    const byVolume = product.volumeM3 > 0 ? Math.floor(remainingVolume / product.volumeM3) : Infinity;
    return Math.max(0, Math.min(Math.floor(stock), byMass, byVolume));
  }

  protected readonly loadProductId: Partial<Record<Id, Id>> = {};
  protected readonly loadQty: Partial<Record<Id, number>> = {};

  protected loadableProducts(fleet: Fleet): { productTypeId: Id; stock: number }[] {
    if (!fleet.locationColonyId) return [];
    return this.api.warehouse(fleet.locationColonyId)()
      .filter(w => !this.shipTypes.some(s => s.id === w.productTypeId))
      .map(w => ({ productTypeId: w.productTypeId, stock: w.quantity }));
  }

  protected async submitLoad(fleet: Fleet): Promise<void> {
    const productTypeId = this.loadProductId[fleet.id];
    const qty = this.loadQty[fleet.id] ?? 0;
    if (!productTypeId || qty <= 0) return;
    await this.run('load:' + fleet.id, () => this.api.loadCargo(fleet.id, productTypeId, qty));
  }

  protected readonly unloadQty: Partial<Record<Id, number>> = {};

  protected async submitUnload(fleet: Fleet, productTypeId: Id): Promise<void> {
    const qty = this.unloadQty[fleet.id + ':' + productTypeId] ?? 0;
    if (qty <= 0) return;
    await this.run('unload:' + fleet.id, () => this.api.unloadCargo(fleet.id, productTypeId, qty));
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

  protected readonly moveDestination: Partial<Record<Id, Id>> = {};

  protected async submitMove(fleet: Fleet): Promise<void> {
    const destinationSystemId = this.moveDestination[fleet.id];
    if (!destinationSystemId) return;
    await this.run('move:' + fleet.id, async () => {
      await this.api.moveFleet(fleet.id, destinationSystemId);
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

  // --- Kampf --------------------------------------------------------------

  /** Gegnerische, im selben System stationierte Flotten, gegen die im Krieg ein Angriff möglich ist (siehe `GameApi.attackableFleetsInSystem`). */
  protected attackableFleets(fleet: Fleet): Fleet[] {
    return this.api.attackableFleetsInSystem(fleet.systemId)();
  }

  /** Das laufende Gefecht, in dem diese eigene Flotte gerade steht (Angreifer ODER Verteidiger) – `undefined`, wenn keins läuft. */
  protected battleForFleet(fleetId: Id) {
    return this.api.activeBattles()().find(b => b.attackerFleetId === fleetId || b.defenderFleetId === fleetId);
  }

  protected async submitAttack(fleet: Fleet, defenderFleetId: Id): Promise<void> {
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
