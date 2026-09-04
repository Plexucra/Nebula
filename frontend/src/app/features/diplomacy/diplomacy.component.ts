import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Id, Player } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

@Component({
  selector: 'app-diplomacy',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './diplomacy.component.html',
  styleUrl: './diplomacy.component.scss',
})
export class DiplomacyComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);

  protected readonly countdown = formatCountdown;

  protected readonly players = this.api.players();
  protected readonly activeWars = this.api.activeWars();
  protected readonly incomingPeaceOffers = this.api.incomingPeaceOffers();
  protected readonly outgoingPeaceOffers = this.api.outgoingPeaceOffers();
  protected readonly activeBattles = this.api.activeBattles();
  protected readonly battleHistory = this.api.battleHistory();

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected otherPlayers(): Player[] {
    const myId = this.api.player()?.id;
    return this.players().filter(p => p.id !== myId);
  }

  protected statusWith(otherPlayerId: Id): 'Peace' | 'War' {
    return this.api.diplomaticStatus(otherPlayerId)();
  }

  protected hasOutgoingOfferTo(otherPlayerId: Id): boolean {
    return this.outgoingPeaceOffers().some(o => o.toPlayerId === otherPlayerId);
  }

  protected warRelation(otherPlayerId: Id) {
    const myId = this.api.player()?.id;
    return this.activeWars().find(r => (r.playerAId === myId && r.playerBId === otherPlayerId) || (r.playerBId === myId && r.playerAId === otherPlayerId));
  }

  protected playerName(id: Id): string {
    return this.players().find(p => p.id === id)?.name ?? '—';
  }

  private async run(key: string, action: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    this.busy.set(key);
    try {
      await action();
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Aktion fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  protected async declareWar(otherPlayerId: Id): Promise<void> {
    await this.run('war:' + otherPlayerId, () => this.api.declareWar(otherPlayerId));
  }

  protected async offerPeace(otherPlayerId: Id): Promise<void> {
    await this.run('peace:' + otherPlayerId, () => this.api.offerPeace(otherPlayerId));
  }

  protected async respond(offerId: Id, accept: boolean): Promise<void> {
    await this.run('respond:' + offerId, () => this.api.respondToPeaceOffer(offerId, accept));
  }

  protected async retreat(battleId: Id): Promise<void> {
    await this.run('retreat:' + battleId, () => this.api.retreatFromBattle(battleId));
  }
}
