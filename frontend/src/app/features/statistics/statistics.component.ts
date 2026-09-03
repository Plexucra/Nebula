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
}

interface ColonyRow {
  colony: Colony;
  planetName: Signal<string>;
  population: Signal<Population | undefined>;
  stats: Signal<PlanetStats | undefined>;
  wallet: Signal<Wallet | undefined>;
}

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
  }));

  protected readonly colonyRows: ColonyRow[] = this.api.colonies()().map(colony => ({
    colony,
    planetName: computed(() => this.api.planet(colony.planetId)()?.name ?? '—'),
    population: this.api.population(colony.id),
    stats: this.api.colonyStats(colony.id),
    wallet: this.api.populationWallet(colony.id),
  }));

  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }

  protected isStruggling(stats: PlanetStats | undefined): boolean {
    return !!stats && (stats.standardOfLivingPct < 30 || stats.loyaltyPct < 20);
  }
}
