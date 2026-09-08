import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { GroundForceGroup, Id } from '../../core/models';

@Component({
  selector: 'app-ground-forces-overview',
  standalone: true,
  imports: [RouterLink, FormsModule, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ground-forces-overview.component.html',
  styleUrl: './ground-forces-overview.component.scss',
})
export class GroundForcesOverviewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly colonies = this.api.colonies();
  protected readonly landedGroups = this.api.landedGroundForces();

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

  // --- Gelandete Verbände + Verlegen (Umsetzungskonzept/04_...md) -----------
  // Angriff auf fremde Kolonien/Bodentruppen ist (noch) nicht Teil davon –
  // "Verlegen" geht ausschließlich in eine eigene Kolonie auf demselben Planeten.
  protected planetName(planetId: Id): string {
    return this.api.planet(planetId)()?.name ?? '—';
  }
  protected ownColoniesOnPlanet(planetId: Id) {
    return this.colonies().filter(c => c.planetId === planetId);
  }
  protected totalGroupSize(group: GroundForceGroup): number {
    return group.units.reduce((sum, u) => sum + u.activeCount + u.reserveCount, 0);
  }

  protected readonly moveTarget: Partial<Record<Id, Id>> = {};
  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected async submitMove(group: GroundForceGroup): Promise<void> {
    const targetColonyId = this.moveTarget[group.id];
    if (!targetColonyId) return;
    this.error.set(null);
    this.busy.set(group.id);
    try {
      await this.api.moveGroundForces(group.id, targetColonyId);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Verlegung fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }
}
