import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { HubOrder, HubOrderSide, Id } from '../../core/models';
import { ProductPickerDialogComponent } from '../../core/ui/product-picker-dialog.component';
import { nearestByHops } from '../../core/util/graph';

@Component({
  selector: 'app-trade-overview',
  standalone: true,
  imports: [DecimalPipe, RouterLink, FormsModule, ProductPickerDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trade-overview.component.html',
  styleUrl: './trade-overview.component.scss',
})
export class TradeOverviewComponent {
  protected readonly api = inject(GAME_API);
  /**
   * REAKTIV, nicht einmalig beim Konstruieren auslesen: `player()` ist beim
   * echten Backend zunächst `null` (die Antwort kommt erst über die
   * WebSocket-Verbindung). Ein hier eingefrorenes `''` würde über
   * `GameApi.sellOrders('')` ein dauerhaft leeres Polling-Signal erzeugen –
   * die Handelsansicht bliebe für immer leer.
   */
  private readonly homeSystemId = computed(() => this.api.player()?.homeSystemId ?? '');
  protected readonly gateway = computed(() => this.api.gateway(this.homeSystemId())());
  protected readonly gatewayActive = () => this.gateway()?.state === 'Active';
  protected readonly orders = computed(() => this.api.sellOrders(this.homeSystemId())());
  protected readonly colonies = this.api.colonies();
  protected readonly visibleSystems = this.api.visibleSystems();
  protected readonly routes = this.api.galaxyRoutes();
  protected readonly productTypes = this.api.productTypes();

  protected readonly playerId = computed(() => this.api.player()?.id ?? '');

  protected readonly nearestTradeHub = computed(() => {
    const hubIds = this.visibleSystems().filter(s => s.isTradeHub).map(s => s.id);
    const nearest = nearestByHops(this.routes(), this.homeSystemId(), hubIds);
    if (!nearest) return null;
    const system = this.visibleSystems().find(s => s.id === nearest.id);
    return system ? { system, hops: nearest.hops } : null;
  });

  // --- Handelsgilde-Station: Depot & Orderbuch (Umsetzungskonzept/22_...md) ---
  // Jeder Kommandant hat ein unbegrenztes Depot je Station (beliefert per Flotte mit Frachtern,
  // siehe Flotten-Seite); Kauf- UND Verkaufs-Orders kreuzen sich sofort, auch in Teilausführung.
  // Die Handelsgilde selbst hält immer eine kleine Order je Seite (`ownerId === null`), damit
  // überhaupt Handel entsteht.
  protected readonly hubSystems = computed(() => this.visibleSystems().filter(s => s.isTradeHub));
  private readonly selectedHubIdOverride = signal<Id | null>(null);
  protected readonly selectedHubId = computed(() =>
    this.selectedHubIdOverride() ?? this.nearestTradeHub()?.system.id ?? this.hubSystems()[0]?.id ?? '');
  protected selectHub(systemId: Id): void {
    this.selectedHubIdOverride.set(systemId);
  }

  protected readonly depot = computed(() => this.api.hubDepot(this.selectedHubId())());
  private readonly allHubOrders = computed(() => this.api.hubOrders(this.selectedHubId())());

  protected readonly productPickerOpen = signal(false);
  private readonly selectedProductIdOverride = signal<Id | null>(null);
  protected readonly selectedProductId = computed(() =>
    this.selectedProductIdOverride() ?? this.depot()[0]?.productTypeId ?? this.productTypes[0]?.id ?? '');
  protected openProductPicker(): void {
    this.productPickerOpen.set(true);
  }
  protected onProductPicked(productTypeId: Id): void {
    this.productPickerOpen.set(false);
    this.selectedProductIdOverride.set(productTypeId);
  }

  protected readonly bids = computed(() =>
    this.allHubOrders().filter(o => o.productTypeId === this.selectedProductId() && o.side === 'Buy')
      .sort((a, b) => b.limitPrice - a.limitPrice));
  protected readonly asks = computed(() =>
    this.allHubOrders().filter(o => o.productTypeId === this.selectedProductId() && o.side === 'Sell')
      .sort((a, b) => a.limitPrice - b.limitPrice));

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

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

  // --- Order aufgeben (für die aktuell gewählte Ware) -----------------------
  protected newOrderSide: HubOrderSide = 'Buy';
  protected newOrderQty = 1;
  protected newOrderPrice = 0;

  protected submitNewOrder(): void {
    const systemId = this.selectedHubId();
    const productTypeId = this.selectedProductId();
    const qty = Math.floor(this.newOrderQty);
    const price = this.newOrderPrice;
    if (!systemId || !productTypeId || qty < 1 || price <= 0) return;
    const action = this.newOrderSide === 'Buy'
      ? () => this.api.createHubBuyOrder(systemId, productTypeId, qty, price)
      : () => this.api.createHubSellOrder(systemId, productTypeId, qty, price);
    void this.run('new-order', action);
  }

  // --- Verkaufen direkt aus dem Depot ----------------------------------------
  protected readonly depotSellQty: Partial<Record<Id, number>> = {};
  protected readonly depotSellPrice: Partial<Record<Id, number>> = {};

  protected sellFromDepot(productTypeId: Id, maxQty: number): void {
    const systemId = this.selectedHubId();
    const qty = Math.min(Math.floor(this.depotSellQty[productTypeId] ?? maxQty), maxQty);
    const price = this.depotSellPrice[productTypeId] ?? 0;
    if (!systemId || qty < 1 || price <= 0) return;
    void this.run(`sell-depot:${productTypeId}`, () => this.api.createHubSellOrder(systemId, productTypeId, qty, price));
  }

  protected cancelHubOrder(order: HubOrder): void {
    void this.run(order.id, () => this.api.cancelHubOrder(order.id));
  }

  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }
  protected sellerColony(depotColonyId: Id | null): string {
    if (!depotColonyId) return '—';
    return this.api.colony(depotColonyId)()?.name ?? '—';
  }
}
