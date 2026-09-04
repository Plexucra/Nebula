import { ChangeDetectionStrategy, Component, Signal, computed, inject } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colony, Id, Npc, PlanetStats, Population, Wallet } from '../../core/models';
import { SparklineTileComponent } from '../../shared/sparkline-tile.component';

interface NpcRow {
  npc: Npc;
  colony: Signal<Colony | undefined>;
  population: Signal<Population | undefined>;
  stats: Signal<PlanetStats | undefined>;
  wallet: Signal<Wallet | undefined>;
  coverage: Signal<Record<Id, number>>;
  powerCoverage: Signal<number>;
}

interface ColonyRow {
  colony: Colony;
  planetName: Signal<string>;
  population: Signal<Population | undefined>;
  stats: Signal<PlanetStats | undefined>;
  wallet: Signal<Wallet | undefined>;
  coverage: Signal<Record<Id, number>>;
  powerCoverage: Signal<number>;
}

/** Reihenfolge/Kurzlabel für die Deckungsanzeige, siehe `CONSUMER_GOODS_ORDER` im Service (dort nicht exportiert). */
const COVERAGE_GOODS: { id: Id; short: string; label: string }[] = [
  { id: 'p_grundnahrung', short: 'N', label: 'Grundnahrung' },
  { id: 'p_grundmedizin', short: 'M', label: 'Grundmedizin' },
  { id: 'p_unterhaltungselektronik', short: 'E', label: 'Unterhaltungselektronik' },
];

@Component({
  selector: 'app-statistics',
  standalone: true,
  imports: [SparklineTileComponent, DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './statistics.component.html',
  styleUrl: './statistics.component.scss',
})
export class StatisticsComponent {
  protected readonly api = inject(GAME_API);

  protected readonly history = this.api.universeStats();
  protected readonly latest = computed(() => {
    const h = this.history();
    return h.length ? h[h.length - 1] : undefined;
  });
  protected readonly first = computed(() => {
    const h = this.history();
    return h.length ? h[0] : undefined;
  });

  protected readonly populationSeries = computed(() => this.history().map(s => s.totalPopulation));
  protected readonly creditsSeries = computed(() => this.history().map(s => s.totalCredits));
  protected readonly infraSeries = computed(() => this.history().map(s => s.avgInfrastructurePct));
  protected readonly securitySeries = computed(() => this.history().map(s => s.avgSecurityPct));
  protected readonly standardSeries = computed(() => this.history().map(s => s.avgStandardOfLivingPct));
  protected readonly loyaltySeries = computed(() => this.history().map(s => s.avgLoyaltyPct));
  protected readonly colonyCountSeries = computed(() => this.history().map(s => s.colonyCount));
  protected readonly strugglingSeries = computed(() => this.history().map(s => s.strugglingColonyCount));
  protected readonly sellOrderSeries = computed(() => this.history().map(s => s.openSellOrderCount));

  protected readonly npcRows: NpcRow[] = this.api.npcs()().map(npc => ({
    npc,
    colony: this.api.colony(npc.homeColonyId),
    population: this.api.population(npc.homeColonyId),
    stats: this.api.colonyStats(npc.homeColonyId),
    wallet: this.api.ownerWallet(npc.id),
    coverage: this.api.consumptionCoverage(npc.homeColonyId),
    powerCoverage: this.api.powerCoverage(npc.homeColonyId),
  }));

  protected readonly colonyRows: ColonyRow[] = this.api.colonies()().map(colony => ({
    colony,
    planetName: computed(() => this.api.planet(colony.planetId)()?.name ?? '—'),
    population: this.api.population(colony.id),
    stats: this.api.colonyStats(colony.id),
    wallet: this.api.populationWallet(colony.id),
    coverage: this.api.consumptionCoverage(colony.id),
    powerCoverage: this.api.powerCoverage(colony.id),
  }));

  protected readonly coverageGoods = COVERAGE_GOODS;

  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }

  protected isStruggling(stats: PlanetStats | undefined): boolean {
    return !!stats && (stats.standardOfLivingPct < 30 || stats.loyaltyPct < 20);
  }

  /** Erklärt den "gefährdet"-Status als Tooltip, statt ihn nur zu behaupten. */
  protected strugglingReason(stats: PlanetStats | undefined): string {
    if (!stats) return '';
    const reasons: string[] = [];
    if (stats.standardOfLivingPct < 30) reasons.push(`Lebensstandard ${stats.standardOfLivingPct.toFixed(0)}% < 30%`);
    if (stats.loyaltyPct < 20) reasons.push(`Loyalität ${stats.loyaltyPct.toFixed(0)}% < 20%`);
    return reasons.join(' · ');
  }

  protected isBlackout(powerCoverage: number): boolean {
    return powerCoverage < 0.999;
  }

  /** Deckungswert in Prozent, gerundet – Helper statt `?? 0` im Template (TS kennt `Record`-Lücken zur Laufzeit nicht). */
  protected coveragePct(coverage: Record<Id, number>, goodId: Id): number {
    return Math.round((coverage[goodId] ?? 0) * 100);
  }
}
