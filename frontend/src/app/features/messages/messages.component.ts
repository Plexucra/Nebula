import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../../core/sim/game-api.token';
import { Id, Player } from '../../core/models';
import { MESSAGE_RETENTION_REAL_DAYS } from '../../core/shared-constants';
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

  /** Kurze Erfolgsmeldung nach dem Senden – vorher schloss sich das Formular kommentarlos. */
  protected readonly sentConfirmation = signal<string | null>(null);

  protected async send(): Promise<void> {
    const to = this.composeTo();
    if (!to) return;
    const recipient = this.playerName(to);
    await this.run('send', () => this.api.sendMessage(to, this.composeSubject(), this.composeBody()));
    if (!this.error()) {
      this.closeCompose();
      // Ohne Rückmeldung war nicht erkennbar, ob die Nachricht raus ist: die
      // Ansicht blieb auf dem (leeren) Posteingang stehen.
      this.sentConfirmation.set(`Nachricht an ${recipient} gesendet.`);
      this.tab.set('sent');
      setTimeout(() => this.sentConfirmation.set(null), 6000);
    }
  }

  /**
   * Antworten auf eine erhaltene Nachricht. Vorher gab es das nicht – man
   * musste "Neue Nachricht" öffnen und den Empfänger aus einer Liste
   * heraussuchen, in der zwei Kommandanten gleich heißen konnten.
   */
  protected reply(fromPlayerId: Id, subject: string): void {
    this.openCompose(fromPlayerId);
    this.composeSubject.set(subject.startsWith('Re: ') ? subject : `Re: ${subject}`);
  }

  /**
   * Hinweistext zur Aufbewahrungsfrist – Zahlen kommen aus derselben
   * `shared/game-constants.json`, die auch das Backend liest, statt hier
   * erneut hartkodiert zu werden.
   */
  protected readonly keepHint =
    `Ohne "Beibehalten" wird diese Nachricht nach ${MESSAGE_RETENTION_REAL_DAYS} echten Tagen automatisch gelöscht.`;

  /**
   * "Beibehalten": ohne diesen Schalter räumt der Server Nachrichten nach
   * `MESSAGE_RETENTION_REAL_DAYS` echten Tagen weg (siehe `RetentionCleanup`
   * im Backend, REALZEIT-AUSNAHME).
   * Absender UND Empfänger dürfen ihn setzen – beide sehen dieselbe Nachricht.
   */
  protected async toggleKeep(id: Id, keep: boolean): Promise<void> {
    await this.run(`keep:${id}`, () => this.api.setMessageKeep(id, keep));
  }

  protected async openAndMarkRead(id: Id): Promise<void> {
    await this.api.markMessageRead(id);
  }
}
