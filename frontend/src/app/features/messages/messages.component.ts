import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../../core/sim/game-api.token';
import { Id, Player } from '../../core/models';
import { MESSAGE_RETENTION_GAME_HOURS, gameHoursToGameDays, gameHoursToRealMinutes } from '../../core/shared-constants';
import { UiClockService } from '../../core/ui/ui-clock.service';

type Tab = 'inbox' | 'sent';

/**
 * Ingame-Nachrichtensystem (Umsetzungskonzept/14_...md) – ausschließlich
 * Spieler-zu-Spieler, kein Gruppenchat. Posteingang/Gesendet als Tabs,
 * Verfassen-Formular mit Empfänger-Auswahl aus `players()`.
 */
@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.scss',
})
export class MessagesComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);

  protected readonly players = this.api.players();
  protected readonly inbox = this.api.inbox();
  protected readonly sent = this.api.sentMessages();

  protected readonly tab = signal<Tab>('inbox');
  protected readonly composeOpen = signal(false);
  protected readonly composeTo = signal<Id | null>(null);
  protected readonly composeSubject = signal('');
  protected readonly composeBody = signal('');

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected otherPlayers(): Player[] {
    const myId = this.api.player()?.id;
    return this.players().filter(p => p.id !== myId);
  }

  protected playerName(id: Id): string {
    return this.players().find(p => p.id === id)?.name ?? '—';
  }

  protected formatAge(sentAt: number): string {
    const seconds = Math.max(0, Math.floor((this.clock.now() - sentAt) / 1000));
    if (seconds < 60) return `vor ${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `vor ${minutes}m`;
    const hours = Math.floor(minutes / 60);
    return `vor ${hours}h`;
  }

  protected openCompose(toPlayerId?: Id): void {
    this.composeTo.set(toPlayerId ?? this.otherPlayers()[0]?.id ?? null);
    this.composeSubject.set('');
    this.composeBody.set('');
    this.composeOpen.set(true);
  }

  protected closeCompose(): void {
    this.composeOpen.set(false);
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

  protected async send(): Promise<void> {
    const to = this.composeTo();
    if (!to) return;
    await this.run('send', () => this.api.sendMessage(to, this.composeSubject(), this.composeBody()));
    if (!this.error()) this.closeCompose();
  }

  /**
   * Hinweistext zur Aufbewahrungsfrist – Zahlen kommen aus derselben
   * `shared/game-constants.json`, die auch das Backend liest, statt hier
   * erneut hartkodiert zu werden.
   */
  protected readonly keepHint =
    `Ohne "Beibehalten" wird diese Nachricht nach ${gameHoursToGameDays(MESSAGE_RETENTION_GAME_HOURS)} Spieltagen `
    + `(ca. ${Math.round(gameHoursToRealMinutes(MESSAGE_RETENTION_GAME_HOURS))} Minuten Echtzeit) automatisch gelöscht.`;

  /**
   * "Beibehalten": ohne diesen Schalter räumt der Server Nachrichten nach
   * 7 Spieltagen automatisch weg (siehe `RetentionCleanup` im Backend).
   * Absender UND Empfänger dürfen ihn setzen – beide sehen dieselbe Nachricht.
   */
  protected async toggleKeep(id: Id, keep: boolean): Promise<void> {
    await this.run(`keep:${id}`, () => this.api.setMessageKeep(id, keep));
  }

  protected async openAndMarkRead(id: Id): Promise<void> {
    await this.api.markMessageRead(id);
  }
}
