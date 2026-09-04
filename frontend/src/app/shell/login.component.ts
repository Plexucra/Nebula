import { ChangeDetectionStrategy, Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { GAME_API } from '../core/sim/game-api.token';
import { Id } from '../core/models';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly api = inject(GAME_API);

  @Output() readonly back = new EventEmitter<void>();

  protected readonly players = this.api.players();
  protected readonly busy = signal<Id | null>(null);
  protected readonly error = signal<string | null>(null);

  protected async selectPlayer(playerId: Id): Promise<void> {
    this.error.set(null);
    this.busy.set(playerId);
    try {
      await this.api.login(playerId);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Anmeldung fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }
}
