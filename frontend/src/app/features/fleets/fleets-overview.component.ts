import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colony, Id } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

@Component({
  selector: 'app-fleets-overview',
  standalone: true,
  imports: [RouterLink, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fleets-overview.component.html',
  styleUrl: './fleets-overview.component.scss',
})
export class FleetsOverviewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);

  protected readonly colonies = this.api.colonies();
  protected readonly fleets = this.api.fleets();
  protected readonly shipTypes = this.api.productTypes().filter(p => p.category === 'Ship');
  protected readonly countdown = formatCountdown;

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected shipyardLevel(colonyId: Id): number {
    return this.api.buildings(colonyId)().find(b => b.typeId === 'b_shipyard')?.level ?? 0;
  }
  protected shipyardQueue(colonyId: Id) {
    return this.api.shipyardQueue(colonyId)();
  }
  protected fleetsAt(colonyId: Id) {
    return this.fleets().filter(f => f.locationColonyId === colonyId);
  }
  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }

  selection: Record<Id, { productId: Id; qty: number; autoProduceMissing: boolean; requeueOnComplete: boolean }> = {};

  protected selFor(colony: Colony): { productId: Id; qty: number; autoProduceMissing: boolean; requeueOnComplete: boolean } {
    if (!this.selection[colony.id]) {
      this.selection[colony.id] = { productId: this.shipTypes[0]?.id ?? '', qty: 1, autoProduceMissing: true, requeueOnComplete: false };
    }
    return this.selection[colony.id];
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

  protected async queueShip(colony: Colony): Promise<void> {
    const sel = this.selFor(colony);
    this.error.set(null);
    this.busy.set(colony.id);
    try {
      await this.api.queueShip(colony.id, sel.productId, sel.qty, sel.autoProduceMissing, sel.requeueOnComplete);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Auftrag fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  protected async resumeOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.api.resumeShipOrder(colonyId, entryId);
  }

  protected async cancelOrder(colonyId: Id, entryId: Id): Promise<void> {
    await this.api.cancelShipOrder(colonyId, entryId);
  }
}
