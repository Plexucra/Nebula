import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { BuildingType, ChainPlan, Id, PlanetType, ProductionQueueEntry } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';
import { ProductPickerDialogComponent } from '../../core/ui/product-picker-dialog.component';
import * as F from '../../core/sim/engine/formulas';

type Tab = 'uebersicht' | 'bebauung' | 'verteidigung' | 'produktion' | 'bodentruppen' | 'bevoelkerung' | 'handel';

/** Platzhalter, solange eine Vorschau noch nicht (neu) berechnet wurde – siehe `refreshNewOrderPreview`/`toggleQueueEntry`. */
const EMPTY_CHAIN_PLAN: ChainPlan = { totalHours: 0, steps: [], feasible: true };

@Component({
  selector: 'app-colony-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, FormsModule, ProductPickerDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './colony-detail.component.html',
  styleUrl: './colony-detail.component.scss',
})
export class ColonyDetailComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  private readonly route = inject(ActivatedRoute);

  protected readonly colonyId: Id = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly colony = this.api.colony(this.colonyId);
  protected readonly stats = this.api.colonyStats(this.colonyId);
  protected readonly population = this.api.population(this.colonyId);
  protected readonly popWallet = this.api.populationWallet(this.colonyId);
  protected readonly planet = this.api.planet(this.colony()?.planetId ?? '');
  protected readonly moneyState = this.api.moneySupplyState(this.colony()?.planetId ?? '');
  protected readonly buildings = this.api.buildings(this.colonyId);
  protected readonly overbuild = this.api.overbuildFactor(this.colony()?.planetId ?? '');
  protected readonly warehouse = this.api.warehouse(this.colonyId);
  protected readonly specializations = this.api.specializations(this.colonyId);
  protected readonly productionQueue = this.api.productionQueue(this.colonyId);
  protected readonly groundForces = this.api.groundForces(this.colonyId);
  protected readonly recruitmentQueue = this.api.recruitmentQueue(this.colonyId);
  protected readonly sellOrdersAll = this.api.sellOrders(this.colony()?.systemId ?? '');
  protected readonly housingCapacity = this.api.housingCapacity(this.colonyId);
  protected readonly powerCoverage = this.api.powerCoverage(this.colonyId);
  protected readonly powerUpkeepPerHour = this.api.powerUpkeepPerHour(this.colonyId);

  protected readonly playerId = this.api.player()?.id ?? '';
  protected readonly allPlayers = this.api.players();
  protected readonly npcsAll = this.api.npcs();
  protected readonly tab = signal<Tab>(this.initialTab());
  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private static readonly OWNER_ONLY_TABS: Tab[] = ['bebauung', 'verteidigung', 'produktion', 'bodentruppen', 'bevoelkerung'];

  /** Nur die eigene Kolonie erlaubt Bau/Produktion/Truppen/Verwaltung – fremde Kolonien (siehe „System Handel") sind nur für Übersicht/Handel einsehbar. */
  protected isOwnColony(): boolean {
    return this.colony()?.ownerId === this.playerId;
  }

  /** Fällt für Besitzer-only-Tabs auf „Übersicht" zurück, sobald die Kolonie nicht (mehr) der eigenen gehört. */
  protected effectiveTab(): Tab {
    const t = this.tab();
    return (ColonyDetailComponent.OWNER_ONLY_TABS as readonly Tab[]).includes(t) && !this.isOwnColony() ? 'uebersicht' : t;
  }

  protected ownerDisplay(): string {
    const ownerId = this.colony()?.ownerId;
    if (!ownerId) return 'Unbekannt';
    return this.allPlayers().find(p => p.id === ownerId)?.name
      ?? this.npcsAll().find(n => n.id === ownerId)?.name
      ?? 'Unbekannt';
  }

  protected readonly buildingTypes = this.api.buildingTypes();
  protected readonly productTypes = this.api.productTypes().filter(p => p.category !== 'Ship' && p.category !== 'GroundUnit');
  protected readonly groundUnitTypes = this.api.productTypes().filter(p => p.category === 'GroundUnit');

  /** Nur Orders, die diese Kolonie selbst eingestellt hat – "Planetarer Handel", siehe Handel-Tab. */
  protected readonly planetOrders = () => this.sellOrdersAll().filter(o => o.depotColonyId === this.colonyId);
  /** "System Handel": alle übrigen Orders im System (Systemhandelsposten + Depots anderer Kolonien) – die dieser Kolonie selbst stehen schon unter "Planetarer Handel", eine Dopplung dort wäre verwirrend. */
  protected readonly systemOrders = () => this.sellOrdersAll().filter(o => o.depotColonyId !== this.colonyId);

  protected newProductionProductId = this.productTypes[0]?.id ?? '';
  protected newProductionQty = 1;
  protected newProductionAutoMissing = true;
  protected newProductionRequeue = false;
  protected readonly productPickerOpen = signal(false);
  /** Reine Vorschau (keine Auftragsanlage) für das Neuer-Auftrag-Formular, siehe `refreshNewOrderPreview`. */
  protected readonly newOrderPreview = signal<ChainPlan | null>(null);
  protected readonly newOrderPreviewLoading = signal(false);

  protected newUnitProductId = this.groundUnitTypes[0]?.id ?? '';
  protected newUnitQty = 5;
  protected newUnitAutoMissing = true;
  protected newUnitRequeue = false;

  protected readonly expandedQueueEntry = signal<Id | null>(null);
  /** Vorschau-Pläne für noch nicht laufende (wartende/gestoppte) Warteschlangeneinträge – deren `entry.plan` ist bis zum Start ein Platzhalter, siehe `toggleQueueEntry`. */
  protected readonly queuePreview: Partial<Record<Id, ChainPlan>> = {};
  protected readonly queuePreviewLoading = signal<Id | null>(null);

  protected readonly buyDraftQty: Partial<Record<Id, number>> = {};

  protected countdown = formatCountdown;

  private initialTab(): Tab {
    const t = this.route.snapshot.queryParamMap.get('tab');
    const valid: Tab[] = ['uebersicht', 'bebauung', 'verteidigung', 'produktion', 'bodentruppen', 'bevoelkerung', 'handel'];
    return (valid as string[]).includes(t ?? '') ? (t as Tab) : 'uebersicht';
  }

  protected setTab(t: Tab): void { this.tab.set(t); }

  protected buildingFor(typeId: Id) {
    return this.buildings().find(b => b.typeId === typeId);
  }

  protected buildingLevel(typeId: Id): number {
    return this.buildingFor(typeId)?.level ?? 0;
  }

  protected upgradeCost(bt: BuildingType): number {
    return F.buildingUpgradeCost(bt.baseCostPerLevel, this.buildingLevel(bt.id));
  }

  protected upgradeHours(bt: BuildingType): number {
    return F.buildingUpgradeHours(bt.baseHoursPerLevel, this.buildingLevel(bt.id));
  }

  protected capacityUsagePct(): number {
    const capacity = this.housingCapacity();
    const count = this.population()?.currentCount ?? 0;
    return capacity > 0 ? (count / capacity) * 100 : 0;
  }

  /**
   * Zufriedenheit = derselbe Faktor, der auch das tatsächliche
   * Bevölkerungswachstum steuert (siehe `F.growthConditionFactor`) –
   * 100% entspricht Referenzgeschwindigkeit, darüber/darunter schneller/
   * langsamer.
   */
  protected satisfactionPct(): number {
    const s = this.stats();
    return s ? F.growthConditionFactor(s.standardOfLivingPct, s.securityPct) * 100 : 0;
  }

  protected readonly productName = (id: Id): string => {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  };

  protected planetTypeLabel(type: PlanetType): string {
    return planetTypeLabel(type);
  }

  protected specLevel(productTypeId: Id): number {
    return this.specializations().find(s => s.productTypeId === productTypeId)?.currentLevel ?? 0;
  }

  /** Tempo-Bonus durch Spezialisierung in %, siehe `F.specializationSpeedFactor`: 100% = doppelte Geschwindigkeit = nur noch die halbe Zeit. */
  protected specSpeedBonusPct(level: number): number {
    return Math.round((F.specializationSpeedFactor(level) - 1) * 100);
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

  protected upgradeBuilding(typeId: Id): void {
    void this.run(`build:${typeId}`, () => this.api.queueBuilding(this.colonyId, typeId));
  }
  protected cancelBuilding(buildingId: Id): void {
    void this.run(`cancel:${buildingId}`, () => this.api.cancelBuildingOrder(this.colonyId, buildingId));
  }
  protected demolish(buildingId: Id): void {
    void this.run(`demolish:${buildingId}`, () => this.api.demolishBuilding(this.colonyId, buildingId));
  }
  protected activateDefense(buildingId: Id): void {
    void this.run(`activate:${buildingId}`, () => this.api.activateDefense(this.colonyId, buildingId));
  }
  protected deactivateDefense(buildingId: Id): void {
    void this.run(`deactivate:${buildingId}`, () => this.api.deactivateDefense(this.colonyId, buildingId));
  }

  protected submitProduction(): void {
    void this.run('production', () => this.api.queueProduction(
      this.colonyId, this.newProductionProductId, this.newProductionQty,
      this.newProductionAutoMissing, this.newProductionRequeue));
  }
  protected resumeProduction(entryId: Id): void {
    void this.run(`resumeprod:${entryId}`, () => this.api.resumeProduction(this.colonyId, entryId));
  }
  protected cancelProduction(entryId: Id): void {
    void this.run(`cancelprod:${entryId}`, () => this.api.cancelProduction(this.colonyId, entryId));
  }

  protected readonly stockOf = (productTypeId: Id): number => {
    return this.warehouse().find(w => w.productTypeId === productTypeId)?.quantity ?? 0;
  };

  protected openProductPicker(): void {
    this.productPickerOpen.set(true);
  }
  protected onProductPicked(productTypeId: Id): void {
    this.productPickerOpen.set(false);
    this.newProductionProductId = productTypeId;
    this.refreshNewOrderPreview();
  }

  /** "Wird berechnet"-Prognose fürs Neuer-Auftrag-Formular – reine Vorschau, legt keinen Auftrag an (siehe `GameApi.previewProductionChain`). */
  protected refreshNewOrderPreview(): void {
    if (!this.newProductionProductId || this.newProductionQty <= 0) { this.newOrderPreview.set(null); return; }
    const productTypeId = this.newProductionProductId;
    const quantity = this.newProductionQty;
    this.newOrderPreviewLoading.set(true);
    this.api.previewProductionChain(this.colonyId, productTypeId, quantity).then(plan => {
      // Falls Produkt/Menge inzwischen weitergeklickt wurden, dieses veraltete Ergebnis verwerfen.
      if (productTypeId !== this.newProductionProductId || quantity !== this.newProductionQty) return;
      this.newOrderPreview.set(plan);
      this.newOrderPreviewLoading.set(false);
    });
  }

  protected toggleQueueEntry(entry: ProductionQueueEntry): void {
    if (this.expandedQueueEntry() === entry.id) {
      this.expandedQueueEntry.set(null);
      return;
    }
    this.expandedQueueEntry.set(entry.id);
    if (entry.status === 'running') return; // entry.plan ist bereits der verbindliche, beim Start berechnete Plan
    this.queuePreviewLoading.set(entry.id);
    this.api.previewProductionChain(entry.colonyId, entry.productTypeId, entry.quantity).then(plan => {
      this.queuePreview[entry.id] = plan;
      if (this.queuePreviewLoading() === entry.id) this.queuePreviewLoading.set(null);
    });
  }

  /** Für den aufgeklappten Eintrag anzuzeigender Plan: bei laufenden Aufträgen der verbindliche `entry.plan`, sonst die zuletzt berechnete Vorschau. */
  protected planFor(entry: ProductionQueueEntry): ChainPlan {
    return entry.status === 'running' ? entry.plan : (this.queuePreview[entry.id] ?? EMPTY_CHAIN_PLAN);
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

  protected readonly stockSaleDraftQty: Partial<Record<Id, number>> = {};
  protected readonly stockSaleDraftPrice: Partial<Record<Id, number>> = {};
  protected readonly stockSaleDraftAutoRelist: Partial<Record<Id, boolean>> = {};
  protected readonly stockSaleOpen = signal<Id | null>(null);

  protected toggleStockSale(productTypeId: Id): void {
    const opening = this.stockSaleOpen() !== productTypeId;
    this.stockSaleOpen.update(cur => cur === productTypeId ? null : productTypeId);
    // Menge bei JEDEM Öffnen frisch mit dem aktuellen Lagerbestand vorbelegen (nicht nur beim allerersten
    // Mal) – sonst zeigt ein erneutes Öffnen einen längst überholten Bestand von der letzten Anzeige an.
    if (opening) this.stockSaleDraftQty[productTypeId] = Math.floor(this.stockOf(productTypeId));
    if (this.stockSaleDraftPrice[productTypeId] === undefined) this.stockSaleDraftPrice[productTypeId] = 5;
    if (this.stockSaleDraftAutoRelist[productTypeId] === undefined) this.stockSaleDraftAutoRelist[productTypeId] = true;
  }

  protected submitStockSale(productTypeId: Id): void {
    const qty = this.stockSaleDraftQty[productTypeId] ?? 0;
    const price = this.stockSaleDraftPrice[productTypeId] ?? 0;
    const autoRelist = this.stockSaleDraftAutoRelist[productTypeId] ?? true;
    if (qty <= 0 || price <= 0) return;
    void this.run(`stocksale:${productTypeId}`, async () => {
      await this.api.createSellOrder(this.colonyId, productTypeId, qty, price, autoRelist);
      this.stockSaleOpen.set(null);
    });
  }

  protected submitRecruitment(): void {
    void this.run('recruit', () => this.api.queueRecruitment(
      this.colonyId, this.newUnitProductId, this.newUnitQty,
      this.newUnitAutoMissing, this.newUnitRequeue));
  }
  protected resumeRecruitment(entryId: Id): void {
    void this.run(`resumerecruit:${entryId}`, () => this.api.resumeRecruitment(this.colonyId, entryId));
  }
  protected cancelRecruitment(entryId: Id): void {
    void this.run(`cancelrecruit:${entryId}`, () => this.api.cancelRecruitment(this.colonyId, entryId));
  }

  protected cancelSellOrder(orderId: Id): void {
    void this.run(`cancelorder:${orderId}`, () => this.api.cancelSellOrder(orderId));
  }

  protected depotColonyName(depotColonyId: Id | null): string {
    if (!depotColonyId) return '—';
    if (depotColonyId === this.colonyId) return 'diese Kolonie';
    return this.api.colony(depotColonyId)()?.name ?? '—';
  }

  protected buyQtyFor(orderId: Id, max: number): number {
    return Math.min(this.buyDraftQty[orderId] ?? max, max);
  }
  protected buyFromOrder(orderId: Id, remaining: number): void {
    const qty = this.buyQtyFor(orderId, remaining);
    if (qty <= 0) return;
    // Lieferziel muss eine EIGENE Kolonie sein (siehe `GameApi.buyFromOrder`): auf der eigenen
    // Kolonieseite direkt hierher, auf einer fremden (System Handel/„Öffnen" von einer anderen
    // Kolonie aus) in die eigene Heimatkolonie – Ware ließe sich sonst nicht sinnvoll zustellen.
    const deliverToColonyId = this.isOwnColony() ? this.colonyId : (this.api.player()?.homeworldColonyId ?? this.colonyId);
    void this.run(`buy:${orderId}`, () => this.api.buyFromOrder(orderId, qty, deliverToColonyId));
  }
}
