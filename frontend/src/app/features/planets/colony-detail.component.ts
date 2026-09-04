import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { BuildingType, Id, PlanetType } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';
import * as F from '../../core/sim/engine/formulas';

type Tab = 'uebersicht' | 'bebauung' | 'verteidigung' | 'produktion' | 'bodentruppen' | 'bevoelkerung' | 'handel';

@Component({
  selector: 'app-colony-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, FormsModule],
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

  protected readonly playerId = this.api.player()?.id ?? '';
  protected readonly tab = signal<Tab>(this.initialTab());
  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly buildingTypes = this.api.buildingTypes();
  protected readonly productTypes = this.api.productTypes().filter(p => p.category !== 'Ship' && p.category !== 'GroundUnit');
  protected readonly groundUnitTypes = this.api.productTypes().filter(p => p.category === 'GroundUnit');

  protected newProductionProductId = this.productTypes[0]?.id ?? '';
  protected newProductionQty = 10;
  protected newProductionAutoMissing = true;
  protected newProductionRequeue = false;
  protected newUnitProductId = this.groundUnitTypes[0]?.id ?? '';
  protected newUnitQty = 5;
  protected newUnitAutoMissing = true;
  protected newUnitRequeue = false;
  protected newOrderProductId = '';
  protected newOrderQty = 10;
  protected newOrderPrice = 5;
  protected readonly expandedQueueEntry = signal<Id | null>(null);

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

  protected toggleQueueEntry(entryId: Id): void {
    this.expandedQueueEntry.update(cur => cur === entryId ? null : entryId);
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
  protected readonly stockSaleOpen = signal<Id | null>(null);

  protected toggleStockSale(productTypeId: Id): void {
    this.stockSaleOpen.update(cur => cur === productTypeId ? null : productTypeId);
    if (this.stockSaleDraftQty[productTypeId] === undefined) this.stockSaleDraftQty[productTypeId] = Math.floor(this.stockOf(productTypeId));
    if (this.stockSaleDraftPrice[productTypeId] === undefined) this.stockSaleDraftPrice[productTypeId] = 5;
  }

  protected submitStockSale(productTypeId: Id): void {
    const qty = this.stockSaleDraftQty[productTypeId] ?? 0;
    const price = this.stockSaleDraftPrice[productTypeId] ?? 0;
    if (qty <= 0 || price <= 0) return;
    void this.run(`stocksale:${productTypeId}`, async () => {
      await this.api.createSellOrder(this.colonyId, productTypeId, qty, price, true);
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

  protected submitSellOrder(): void {
    if (!this.newOrderProductId) return;
    void this.run('sellorder', () => this.api.createSellOrder(this.colonyId, this.newOrderProductId, this.newOrderQty, this.newOrderPrice));
  }
  protected cancelSellOrder(orderId: Id): void {
    void this.run(`cancelorder:${orderId}`, () => this.api.cancelSellOrder(orderId));
  }
}
