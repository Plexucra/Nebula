import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { GroundBattle, GroundForceGroup, Id } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

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
  protected readonly clock = inject(UiClockService);
  protected readonly countdown = formatCountdown;
  protected readonly colonies = this.api.colonies();
  protected readonly landedGroups = this.api.landedGroundForces();
  protected readonly battles = this.api.activeGroundBattles();

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

  // --- Gelandete Verbände: verlegen ODER angreifen --------------------------
  protected planetName(planetId: Id): string {
    return this.api.planet(planetId)()?.name ?? '—';
  }
  protected colonyName(colonyId: Id): string {
    return this.api.colony(colonyId)()?.name ?? '—';
  }
  protected playerName(id: Id): string {
    return this.api.players()().find(p => p.id === id)?.name ?? '—';
  }
  protected ownColoniesOnPlanet(planetId: Id) {
    return this.colonies().filter(c => c.planetId === planetId);
  }
  /** Fremde Kolonien auf demselben Planeten, gegen die dieser Verband vorgehen darf – Regel im Backend. */
  protected attackTargets(groupId: Id) {
    return this.api.attackableColoniesForGroup(groupId)();
  }
  protected totalGroupSize(group: GroundForceGroup): number {
    return group.units.reduce((sum, u) => sum + u.activeCount + u.reserveCount, 0);
  }
  /** Nur aktive (von Soldaten kommandierte) Drohnen kämpfen – ein Verband ohne sie kann nicht angreifen. */
  protected activeUnits(group: GroundForceGroup): number {
    return group.units.reduce((sum, u) => sum + u.activeCount, 0);
  }
  protected battleForGroup(groupId: Id): GroundBattle | undefined {
    return this.battles().find(b => b.attackerGroupId === groupId);
  }
  /**
   * Loyalität der belagerten Kolonie. Aus dem letzten Belagerungstick statt aus
   * `colonyStats`: die Kolonie gehört dem Gegner, ihre Kennzahlen sind für den
   * Angreifer nicht abrufbar – der eigene Kampfbericht dagegen schon.
   */
  protected siegeLoyalty(battle: GroundBattle): number {
    const last = battle.ticks[battle.ticks.length - 1];
    return last?.loyaltyPctAfter ?? 0;
  }
  /** Laufende Bodengefechte, in denen der angemeldete Kommandant der Verteidiger ist. */
  protected defendingBattles(): GroundBattle[] {
    const me = this.api.player()?.id;
    return this.battles().filter(b => b.defenderId === me);
  }

  protected readonly moveTarget: Partial<Record<Id, Id>> = {};
  protected readonly attackTarget: Partial<Record<Id, Id>> = {};
  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected async submitMove(group: GroundForceGroup): Promise<void> {
    const targetColonyId = this.moveTarget[group.id];
    if (!targetColonyId) return;
    await this.run(group.id, () => this.api.moveGroundForces(group.id, targetColonyId), 'Verlegung fehlgeschlagen.');
  }

  protected async submitAttack(group: GroundForceGroup): Promise<void> {
    const targetColonyId = this.attackTarget[group.id];
    if (!targetColonyId) return;
    await this.run(group.id, () => this.api.engageGroundBattle(group.id, targetColonyId), 'Angriff fehlgeschlagen.');
  }

  protected async submitRetreat(battle: GroundBattle): Promise<void> {
    await this.run(battle.attackerGroupId,
      () => this.api.retreatFromGroundBattle(battle.id), 'Rückzug fehlgeschlagen.');
  }

  private async run(key: Id, action: () => Promise<unknown>, fallbackMessage: string): Promise<void> {
    this.error.set(null);
    this.busy.set(key);
    try {
      await action();
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : fallbackMessage);
    } finally {
      this.busy.set(null);
    }
  }
}
