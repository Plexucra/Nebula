import { ChangeDetectionStrategy, Component, Signal, computed, inject } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colony, Id, PlanetStats, Population, Wallet } from '../../core/models';
import { SparklineTileComponent } from '../../shared/sparkline-tile.component';
import { CONSUMER_GOODS } from '../../core/shared-constants';

interface ColonyRow {
  colony: Colony;
  planetName: Signal<string>;
  population: Signal<Population | undefined>;
  stats: Signal<PlanetStats | undefined>;
  wallet: Signal<Wallet | undefined>;
  coverage: Signal<Record<Id, number>>;
  powerCoverage: Signal<number>;
  blackout: Signal<boolean>;
}

/** Beschriftung der Deckungsanzeige – reine Oberflächentexte; WELCHE Güter und in welcher Reihenfolge, sagt `CONSUMER_GOODS`. */
const COVERAGE_LABELS: Record<Id, { short: string; label: string }> = {
  p_grundnahrung: { short: 'N', label: 'Grundnahrung' },
  p_grundmedizin: { short: 'M', label: 'Grundmedizin' },
  p_unterhaltungselektronik: { short: 'E', label: 'Unterhaltungselektronik' },
};

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
  /** Ausgang des Krieges – null, solange mehrere Parteien Kolonien besitzen. */
  protected readonly victory = this.api.victory();
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

  protected readonly colonyRows: ColonyRow[] = this.api.colonies()().map(colony => ({
    colony,
    planetName: computed(() => this.api.planet(colony.planetId)()?.name ?? '—'),
    population: this.api.population(colony.id),
    stats: this.api.colonyStats(colony.id),
    wallet: this.api.populationWallet(colony.id),
    coverage: this.api.consumptionCoverage(colony.id),
    powerCoverage: this.api.powerCoverage(colony.id),
    blackout: this.api.isBlackout(colony.id),
  }));

  protected readonly coverageGoods: { id: Id; short: string; label: string }[] =
    CONSUMER_GOODS.map(id => ({ id, ...(COVERAGE_LABELS[id] ?? { short: '?', label: id }) }));
  /** Auflösung der Ein-Buchstaben-Kürzel – sie standen vorher ohne jede Legende in der Tabelle. */
  protected readonly coverageLegend = this.coverageGoods.map(g => `${g.short} = ${g.label}`).join(', ');

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

  /** Deckungswert in Prozent, gerundet – Helper statt `?? 0` im Template (TS kennt `Record`-Lücken zur Laufzeit nicht). */
  protected coveragePct(coverage: Record<Id, number>, goodId: Id): number {
    return Math.round((coverage[goodId] ?? 0) * 100);
  }
}
