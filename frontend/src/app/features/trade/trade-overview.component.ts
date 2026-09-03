import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Id } from '../../core/models';
import { nearestByHops } from '../../core/util/graph';

@Component({
  selector: 'app-trade-overview',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trade-overview.component.html',
  styleUrl: './trade-overview.component.scss',
})
export class TradeOverviewComponent {
  protected readonly api = inject(GAME_API);
  private readonly homeSystemId = this.api.player()?.homeSystemId ?? '';
  protected readonly gateway = this.api.gateway(this.homeSystemId);
  protected readonly gatewayActive = () => this.gateway()?.state === 'Active';
  protected readonly orders = this.api.sellOrders(this.homeSystemId);
  protected readonly colonies = this.api.colonies();
  protected readonly visibleSystems = this.api.visibleSystems();
  protected readonly routes = this.api.galaxyRoutes();

  protected readonly playerId = this.api.player()?.id ?? '';

  protected readonly nearestTradeHub = computed(() => {
    const hubIds = this.visibleSystems().filter(s => s.isTradeHub).map(s => s.id);
    const nearest = nearestByHops(this.routes(), this.homeSystemId, hubIds);
    if (!nearest) return null;
    const system = this.visibleSystems().find(s => s.id === nearest.id);
    return system ? { system, hops: nearest.hops } : null;
  });

  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }
  protected sellerColony(depotColonyId: Id | null): string {
    if (!depotColonyId) return '—';
    return this.api.colony(depotColonyId)()?.name ?? '—';
  }
}
