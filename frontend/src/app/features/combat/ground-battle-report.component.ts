import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { GroundBattleTickResult, GroundForceUnitStack, Id } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

/**
 * Öffentlicher Bodenkampfbericht über den unerratbaren
 * `GroundBattle.reportToken` (`/bodenkampfbericht/:token`) – dasselbe Prinzip
 * und dieselben Einschränkungen wie beim Raumgefecht, siehe
 * `BattleReportComponent`.
 *
 * Anders als dort zeigt der Bericht ZWEI zusätzliche Größen, weil am Boden
 * eine Zivilbevölkerung mit im Kampfgebiet steht (Mechanik/05_..., §2): die
 * Zivilverluste je Tick und die daraus folgende Zivilverlustquote, an der die
 * Sachschäden einer Eroberung hängen.
 */
@Component({
  selector: 'app-ground-battle-report',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ground-battle-report.component.html',
  styleUrl: './battle-report.component.scss',
})
export class GroundBattleReportComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly countdown = formatCountdown;
  private readonly route = inject(ActivatedRoute);

  protected readonly token: string = this.route.snapshot.paramMap.get('token') ?? '';
  protected readonly battle = this.api.groundBattleByReportToken(this.token);

  protected planetName(id: Id): string {
    return this.api.planet(id)()?.name ?? '—';
  }

  protected colonyName(id: Id): string {
    return this.api.colony(id)()?.name ?? '— nicht mehr vorhanden —';
  }

  protected playerName(id: Id): string {
    return this.api.players()().find(p => p.id === id)?.name ?? '—';
  }

  protected productName(id: Id): string {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  }

  /**
   * Nur der AKTIVE Bestand nimmt am Gefecht teil (§3) – die Reserve wird
   * getrennt ausgewiesen, damit ablesbar bleibt, wie viel Material mangels
   * Soldaten gar nicht erst zum Einsatz kam.
   */
  protected activeLabel(units: GroundForceUnitStack[]): string {
    const parts = units.filter(u => u.activeCount > 0).map(u => `${this.productName(u.unitProductTypeId)} × ${u.activeCount}`);
    return parts.length > 0 ? parts.join(', ') : '— nichts Kampffähiges —';
  }

  protected reserveLabel(units: GroundForceUnitStack[]): string {
    const parts = units.filter(u => u.reserveCount > 0).map(u => `${this.productName(u.unitProductTypeId)} × ${u.reserveCount}`);
    return parts.length > 0 ? parts.join(', ') : 'keine';
  }

  protected lossesLabel(losses: Record<Id, number>): string {
    const parts = Object.entries(losses).filter(([, qty]) => qty > 0).map(([id, qty]) => `${this.productName(id)} × ${qty}`);
    return parts.length > 0 ? parts.join(', ') : 'keine';
  }

  protected totalLosses(ticks: GroundBattleTickResult[], side: 'attacker' | 'defender'): Record<Id, number> {
    const totals: Record<Id, number> = {};
    for (const t of ticks) {
      const losses = side === 'attacker' ? t.attackerLosses : t.defenderLosses;
      for (const [id, qty] of Object.entries(losses)) totals[id] = (totals[id] ?? 0) + qty;
    }
    return totals;
  }

  protected totalCiviliansLost(ticks: GroundBattleTickResult[]): number {
    return ticks.reduce((sum, t) => sum + t.civiliansLost, 0);
  }
}
