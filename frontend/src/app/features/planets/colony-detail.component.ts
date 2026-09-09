import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { BuildingType, ChainPlan, Id, MaterialRequirement, PlanetType, ProductionQueueEntry } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';
import { ProductPickerDialogComponent } from '../../core/ui/product-picker-dialog.component';
import { PopulationChartComponent } from '../../shared/population-chart.component';

type Tab = 'uebersicht' | 'bebauung' | 'verteidigung' | 'produktion' | 'bodentruppen' | 'bevoelkerung' | 'handel';

/** Platzhalter, solange eine Vorschau noch nicht (neu) berechnet wurde – siehe `refreshNewOrderPreview`/`toggleQueueEntry`. */
const EMPTY_CHAIN_PLAN: ChainPlan = { totalHours: 0, steps: [], feasible: true, totalWorkHours: 0, workersBoundPerHour: 0 };

@Component({
  selector: 'app-colony-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, FormsModule, ProductPickerDialogComponent, PopulationChartComponent],
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
  protected readonly warehouse = this.api.warehouse(this.colonyId);
  protected readonly supplyInventory = this.api.supplyInventory(this.colonyId);
  protected readonly specializations = this.api.specializations(this.colonyId);
  protected readonly productionQueue = this.api.productionQueue(this.colonyId);
  protected readonly groundForces = this.api.groundForces(this.colonyId);
  protected readonly recruitmentQueue = this.api.recruitmentQueue(this.colonyId);
  protected readonly sellOrdersAll = this.api.sellOrders(this.colony()?.systemId ?? '');
  protected readonly system = this.api.system(this.colony()?.systemId ?? '');
  protected readonly housingCapacity = this.api.housingCapacity(this.colonyId);
  protected readonly powerCoverage = this.api.powerCoverage(this.colonyId);
  protected readonly powerUpkeepPerHour = this.api.powerUpkeepPerHour(this.colonyId);
  /** Energiespeicher der Kolonie (Umsetzungskonzept/32_...md) – Anzeige und Konfiguration im Tab "Bebauung". */
  protected readonly energyStorage = this.api.energyStorage(this.colonyId);
  /** Eingabe für die Vorhaltemenge; null = noch nichts eingegeben. */
  protected energyReserveDraft: number | null = null;
  /**
   * Alle Tempo-/Kostenfaktoren dieser Kolonie – fertig BERECHNET vom Backend
   * (siehe `GameApi.colonySpeedBreakdown`). Früher rechnete diese Komponente
   * sie aus einer zweiten Formelkopie (`engine/formulas.ts`) nach; die Regeln
   * leben jetzt ausschließlich im Backend, die Transparenz-Anzeige bleibt
   * dabei vollständig erhalten (Umsetzungskonzept/15_...md, Auftrag 3).
   */
  protected readonly speedBreakdown = this.api.colonySpeedBreakdown(this.colonyId);
  protected readonly consumptionCoverage = this.api.consumptionCoverage(this.colonyId);
  protected readonly populationTrend = this.api.populationTrend(this.colonyId);

  /** Reaktiv: `player()` ist beim echten Backend erst nach der ersten Server-Antwort gesetzt (siehe TradeOverviewComponent). */
  protected readonly playerId = computed(() => this.api.player()?.id ?? '');
  protected readonly allPlayers = this.api.players();
  protected readonly tab = signal<Tab>(this.initialTab());
  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private static readonly OWNER_ONLY_TABS: Tab[] = ['bebauung', 'verteidigung', 'produktion', 'bodentruppen', 'bevoelkerung'];

  /** Nur die eigene Kolonie erlaubt Bau/Produktion/Truppen/Verwaltung – fremde Kolonien (siehe „System Handel") sind nur für Übersicht/Handel einsehbar. */
  protected isOwnColony(): boolean {
    return this.colony()?.ownerId === this.playerId();
  }

  /** Fällt für Besitzer-only-Tabs auf „Übersicht" zurück, sobald die Kolonie nicht (mehr) der eigenen gehört. */
  protected effectiveTab(): Tab {
    const t = this.tab();
    return (ColonyDetailComponent.OWNER_ONLY_TABS as readonly Tab[]).includes(t) && !this.isOwnColony() ? 'uebersicht' : t;
  }

  protected ownerDisplay(): string {
    const ownerId = this.colony()?.ownerId;
    if (!ownerId) return 'Unbekannt';
    return this.allPlayers().find(p => p.id === ownerId)?.name ?? 'Unbekannt';
  }

  /** Kataloge kommen asynchron nach dem Verbindungsaufbau – deshalb bei jedem Zugriff frisch lesen, nicht einmalig im Feld einfrieren. */
  protected get buildingTypes() { return this.api.buildingTypes(); }
  protected get productTypes() { return this.api.productTypes().filter(p => p.category !== 'Ship' && p.category !== 'GroundUnit'); }
  protected get groundUnitTypes() { return this.api.productTypes().filter(p => p.category === 'GroundUnit'); }

  /** Nur Orders, die diese Kolonie selbst eingestellt hat – "Planetarer Handel", siehe Handel-Tab. */
  protected readonly planetOrders = () => this.sellOrdersAll().filter(o => o.depotColonyId === this.colonyId);
  /** Übrige Depot-Orders anderer Kolonien im selben System – die dieser Kolonie selbst stehen schon unter "Planetarer Handel", eine Dopplung dort wäre verwirrend. Systemhandelsposten-Orders (`depotColonyId === null`) sind außerhalb einer Handelsgilde-Station nicht mehr möglich, siehe `canBuyFrom`. */
  protected readonly systemOrders = () => this.sellOrdersAll().filter(o => o.depotColonyId !== this.colonyId);

  /**
   * Planetarer Handel ist außerhalb einer neutralen Handelsgilde-Station
   * (`system().isTradeHub`) nur zwischen Kommandanten mit gültigem
   * Handelsvertrag möglich (Umsetzungskonzept/21_...md) – die eigene Order
   * ist davon unbenommen (dafür gibt es „Zurückziehen").
   */
  protected canBuyFrom(sellerId: Id): boolean {
    if (sellerId === this.playerId()) return true;
    if (this.system()?.isTradeHub) return true;
    return this.api.hasTradeAgreement(sellerId)();
  }

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

  private upgradePreview(typeId: Id) {
    return this.speedBreakdown()?.buildingUpgrades.find(u => u.typeId === typeId);
  }

  protected upgradeCost(bt: BuildingType): number {
    return this.upgradePreview(bt.id)?.upgradeCost ?? 0;
  }

  /** Baustoffe des nächsten Ausbauschritts (Bedarf + Lagerbestand), vom Backend – sichtbar VOR dem Klick. */
  protected upgradeMaterials(bt: BuildingType): MaterialRequirement[] {
    return this.upgradePreview(bt.id)?.materials ?? [];
  }

  protected upgradeBlockedReason(bt: BuildingType): string | null {
    return this.upgradePreview(bt.id)?.blockedReason ?? null;
  }

  protected upgradeAffordable(bt: BuildingType): boolean {
    return this.upgradePreview(bt.id)?.affordable ?? false;
  }

  /** Bebauungsplätze – DIE strategische Größe der Bebauung (Umsetzungskonzept/17_...md). */
  protected slots() {
    return this.speedBreakdown()?.buildSlots ?? null;
  }

  /** Versorgungsdeckung je Grundkonsumgut – erklärt am Plateau, WARUM das Wachstum stockt. */
  protected coverageEntries(): { productTypeId: Id; coverage: number }[] {
    return Object.entries(this.consumptionCoverage()).map(([productTypeId, coverage]) => ({ productTypeId, coverage }));
  }

  protected growthStateLabel(): string {
    switch (this.speedBreakdown()?.growthState) {
      case 'Shrinking': return 'Schrumpfung';
      case 'Holding': return 'Halten';
      case 'Growing': return 'Wachstum';
      case 'Overcrowded': return 'Überbevölkert';
      default: return '–';
    }
  }

  protected upgradeHours(bt: BuildingType): number {
    return this.upgradePreview(bt.id)?.upgradeHours ?? 0;
  }

  /** Produktionstempo einer Produktionsanlage (Industriekomplex/Werft/Ausbildungszentrum) als Prozentsatz – Stufe 1 = 100%, Stufe 4 = 400%. */
  protected productionSpeedPct(typeId: Id): number {
    return this.upgradePreview(typeId)?.productionSpeedPct ?? 0;
  }

  /** Produktionstempo nach dem nächsten Ausbauschritt in Prozent. */
  protected nextProductionSpeedPct(typeId: Id): number {
    return this.upgradePreview(typeId)?.nextProductionSpeedPct ?? 0;
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
    return this.speedBreakdown()?.satisfactionPct ?? 0;
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

  /** Tempo-Bonus durch Spezialisierung in % (vom Backend berechnet): 100% = doppelte Geschwindigkeit = nur noch die halbe Zeit. */
  protected specSpeedBonusPct(productTypeId: Id): number {
    return Math.round(this.speedBreakdown()?.specializationSpeedBonusPctByProduct[productTypeId] ?? 0);
  }

  /**
   * Vollständige Aufschlüsselung ALLER Faktoren, die in `computeProductionHours`
   * (Backend) in die Produktionsgeschwindigkeit dieser Kolonie eingehen –
   * bewusst hier client-seitig aus denselben `engine/formulas.ts`-Funktionen
   * nachgerechnet (keine zweite, potenziell abweichende Formel), damit dem
   * Spieler KEIN Faktor verborgen bleibt, der seine strategische Entscheidung
   * (z. B. "erst Bevölkerung wachsen lassen" oder "Industriekomplex ausbauen")
   * beeinflussen könnte. Bevölkerung/Gebäudestufe/Blackout gelten koloniweit
   * für JEDEN Produktionsschritt gleichermaßen; Spezialisierung und
   * Fördergüte sind PRODUKTSPEZIFISCH, siehe `specLevel`/`concentrationFactorFor`.
   */
  protected colonySpeedFactors(): { population: number; availableWorkers: number; industryLevel: number; buildingSpeedFactor: number; blackout: boolean } {
    const b = this.speedBreakdown();
    return {
      population: b?.population ?? 0,
      availableWorkers: b?.availableWorkers ?? 0,
      industryLevel: b?.industryLevel ?? 0,
      buildingSpeedFactor: b?.buildingSpeedFactor ?? 0,
      blackout: b?.blackout ?? false,
    };
  }

  /** Fördergüte-Ausbeutefaktor (nur für Rohstoffe/Tier 0 – bei allem anderen `null`), vom Backend berechnet. */
  protected concentrationFactorFor(productTypeId: Id): number | null {
    return this.speedBreakdown()?.concentrationFactorByProduct[productTypeId] ?? null;
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

  protected applyEnergyReserve(): void {
    const value = this.energyReserveDraft;
    if (value === null || !Number.isFinite(value) || value < 0) {
      this.error.set('Bitte eine Vorhaltemenge von 0 oder mehr eingeben.');
      return;
    }
    void this.run('energy-reserve', () => this.api.setEnergyReserve(this.colonyId, Math.floor(value)));
  }

  protected resetEnergyReserveToAutomatic(): void {
    this.energyReserveDraft = null;
    void this.run('energy-reserve', () => this.api.setEnergyReserve(this.colonyId, null));
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
  // --- Drohnen ein-/auslagern (Umsetzungskonzept/28_...md) ------------------
  // Drohnen sind Maschinen: im Lager sind sie Ware und damit Frachtgut, in der
  // Garnison sind sie Einheiten und zählen zur Sicherheit. Soldaten machen
  // diesen Weg NICHT mit – sie fahren ausschließlich im Mannschaftstransporter.
  protected readonly droneQty: Partial<Record<Id, number>> = {};

  /** Eingelagerte Drohnen dieser Kolonie – aus dem normalen Warenlager. */
  protected storedDrones(): { productTypeId: Id; quantity: number }[] {
    const droneIds = new Set(this.groundUnitTypes.filter(u => u.id !== 'p_soldier').map(u => u.id));
    return this.warehouse().filter(w => droneIds.has(w.productTypeId) && w.quantity > 0);
  }

  protected submitStoreDrones(unitProductTypeId: Id): void {
    const qty = this.droneQty[unitProductTypeId] ?? 0;
    if (qty <= 0) return;
    void this.run('drones', () => this.api.storeDrones(this.colonyId, unitProductTypeId, qty));
  }
  protected submitDeployDrones(unitProductTypeId: Id): void {
    const qty = this.droneQty[unitProductTypeId] ?? 0;
    if (qty <= 0) return;
    void this.run('drones', () => this.api.deployDrones(this.colonyId, unitProductTypeId, qty));
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
