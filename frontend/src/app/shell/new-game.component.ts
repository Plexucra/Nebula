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

  /**
   * Ob die Eingaben vollständig sind. Angular setzt auf dem Formular
   * `novalidate`, deshalb greift das `required`-Attribut allein NICHT: Ein
   * leeres Formular ließ sich abschicken und legte still einen Kommandanten
   * "Unbekannter Kommandant" mit Heimatwelt "Heimatwelt" an. Weil der Name
   * zugleich die Kennung in der Anmelde- und Empfängerliste ist, entstanden so
   * nicht unterscheidbare Kommandanten. Der Server weist beides inzwischen
   * ebenfalls ab – hier wird es nur früher und freundlicher sichtbar.
   */
  protected get valid(): boolean {
    if (!this.commanderName.trim() || !this.homeworldName.trim()) return false;
    return this.role !== 'Npc' || !!this.campId.trim();
  }

  protected async begin(): Promise<void> {
    if (!this.valid) {
      this.error.set('Bitte einen Namen für den Kommandanten und für die Heimatkolonie angeben.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    try {
      await this.api.registerPlayer(
        this.commanderName.trim(), this.homeworldName.trim(), this.role,
        this.role === 'Npc' ? this.campId.trim() : undefined);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Unbekannter Fehler.');
    } finally {
      this.busy.set(false);
    }
  }
}
