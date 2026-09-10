import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { BattleTickResult, FleetShipGroup, Id } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

/**
 * Öffentlicher Kampfbericht über den unerratbaren `Battle.reportToken`
 * (`/kampfbericht/:token`, siehe `GameApi.battleByReportToken`) – ab
 * Kampfbeginn abrufbar, nicht erst nach Kampfende, und UNABHÄNGIG davon,
 * welcher Kommandant gerade in diesem Browser angemeldet ist (teilbarer
 * Link). Einschränkung dieses Prototyps: die gesamte App liegt hinter dem
 * Login-Bildschirm (`app.component.html`), ein Link ist also nur nutzbar,
 * wenn man ohnehin als IRGENDEIN Kommandant angemeldet ist – „teilbar"
 * bezieht sich hier auf den Bericht selbst (unabhängig vom Betrachter),
 * nicht auf einen wirklich anonymen externen Abruf.
 */
@Component({
  selector: 'app-battle-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './battle-report.component.html',
  styleUrl: './battle-report.component.scss',
})
export class BattleReportComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly countdown = formatCountdown;
  private readonly route = inject(ActivatedRoute);

  protected readonly token: string = this.route.snapshot.paramMap.get('token') ?? '';
  protected readonly battle = this.api.battleByReportToken(this.token);

  protected systemName(id: Id): string {
    const s = this.api.system(id)();
    return s ? `${s.number} · ${s.name}` : '—';
  }

  protected playerName(id: Id): string {
    return this.api.players()().find(p => p.id === id)?.name ?? '—';
  }

  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }

  /** Bestand der Flotte VOR dem ersten Tick (Bericht ist schon ab Kampfbeginn abrufbar, bevor überhaupt ein Tick aufgelöst wurde). */
  protected currentShips(fleetId: Id): FleetShipGroup[] {
    return this.api.fleet(fleetId)()?.ships ?? [];
  }

  protected shipsLabel(ships: FleetShipGroup[]): string {
    const parts = ships.filter(s => s.quantity > 0).map(s => `${this.productName(s.shipProductTypeId)} × ${s.quantity}`);
    return parts.length > 0 ? parts.join(', ') : '— keine Schiffe mehr —';
  }

  protected lossesLabel(losses: Record<Id, number>): string {
    const parts = Object.entries(losses).filter(([, qty]) => qty > 0).map(([id, qty]) => `${this.productName(id)} × ${qty}`);
    return parts.length > 0 ? parts.join(', ') : 'keine';
  }

  /** Summe der Verluste über ALLE Ticks – für die Abschlussübersicht am Ende des Berichts. */
  protected totalLosses(ticks: BattleTickResult[], side: 'attacker' | 'defender'): Record<Id, number> {
    const totals: Record<Id, number> = {};
    for (const t of ticks) {
      const losses = side === 'attacker' ? t.attackerLosses : t.defenderLosses;
      for (const [id, qty] of Object.entries(losses)) totals[id] = (totals[id] ?? 0) + qty;
    }
    return totals;
  }
}
