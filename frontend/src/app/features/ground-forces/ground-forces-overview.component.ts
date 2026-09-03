import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Id } from '../../core/models';

@Component({
  selector: 'app-ground-forces-overview',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ground-forces-overview.component.html',
  styleUrl: './ground-forces-overview.component.scss',
})
export class GroundForcesOverviewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly colonies = this.api.colonies();

  protected garrison(colonyId: Id) {
    return this.api.groundForces(colonyId)()?.units ?? [];
  }
  protected totalUnits(colonyId: Id): number {
    return this.garrison(colonyId).reduce((sum, u) => sum + u.activeCount, 0);
  }
  protected loyalty(colonyId: Id): number {
    return this.api.colonyStats(colonyId)()?.loyaltyPct ?? 0;
  }
  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }
}
