import { ChangeDetectionStrategy, Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../core/sim/game-api.token';
import { PlayerRole } from '../core/models';

@Component({
  selector: 'app-new-game',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-game.component.html',
  styleUrl: './new-game.component.scss',
})
export class NewGameComponent {
  private readonly api = inject(GAME_API);

  @Output() readonly back = new EventEmitter<void>();

  protected commanderName = '';
  protected homeworldName = '';
  protected role: PlayerRole = 'Normal';
  protected campId = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async begin(): Promise<void> {
    this.error.set(null);
    this.busy.set(true);
    try {
      await this.api.registerPlayer(
        this.commanderName, this.homeworldName, this.role,
        this.role === 'Npc' ? this.campId : undefined);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Unbekannter Fehler.');
    } finally {
      this.busy.set(false);
    }
  }
}
