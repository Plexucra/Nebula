import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { GAME_API } from '../core/sim/game-api.token';
import { Id, NotificationType } from '../core/models';
import { UiClockService } from '../core/ui/ui-clock.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  lockedHint?: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);

  protected readonly player = this.api.player;
  protected readonly wallet = this.api.wallet;

  private readonly homeSystemId = this.api.player()?.homeSystemId ?? '';
  protected readonly gateway = this.api.gateway(this.homeSystemId);
  protected readonly gatewayActive = () => this.gateway()?.state === 'Active';

  protected readonly notifications = this.api.notifications();
  protected readonly unreadNotificationCount = this.api.unreadNotificationCount();
  protected readonly notificationPanelOpen = signal(false);

  protected toggleNotificationPanel(): void {
    this.notificationPanelOpen.update(v => !v);
  }

  protected async markRead(id: Id): Promise<void> {
    await this.api.markNotificationRead(id);
  }

  protected async markAllRead(): Promise<void> {
    await this.api.markAllNotificationsRead();
  }

  protected notificationIcon(type: NotificationType): string {
    switch (type) {
      case 'Problem': return '⛔';
      case 'Warnung': return '⚠';
      default: return 'ℹ';
    }
  }

  protected formatAge(createdAt: number): string {
    const seconds = Math.max(0, Math.floor((this.clock.now() - createdAt) / 1000));
    if (seconds < 60) return `vor ${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `vor ${minutes}m`;
    const hours = Math.floor(minutes / 60);
    return `vor ${hours}h`;
  }

  protected readonly navItems: NavItem[] = [
    { path: '/planeten', label: 'Planeten', icon: '◉' },
    { path: '/produktion', label: 'Produktion', icon: '⛭' },
    { path: '/flotten', label: 'Flotten', icon: '✈' },
    { path: '/bodentruppen', label: 'Bodentruppen', icon: '⛊' },
    { path: '/diplomatie', label: 'Diplomatie / Krieg', icon: '⚔', lockedHint: 'Freigeschaltet nach Gateway-Aktivierung' },
    { path: '/galaxie', label: 'Galaxiekarte', icon: '✦' },
    { path: '/handel', label: 'Handel', icon: '⇄' },
    { path: '/konto', label: 'Konto', icon: '◈' },
    { path: '/statistiken', label: 'Statistiken', icon: '▤' },
  ];

  protected isLocked(item: NavItem): boolean {
    return !!item.lockedHint && !this.gatewayActive();
  }

  /** Löscht die GESAMTE gemeinsame Galaxie – auch die aller anderen Kommandanten, nicht nur den eigenen Fortschritt. */
  protected async resetGame(): Promise<void> {
    if (!confirm('Die komplette Galaxie wirklich zurücksetzen? Der Fortschritt ALLER Kommandanten geht unwiderruflich verloren.')) return;
    await this.api.resetGame();
  }

  /** Meldet nur ab – der Spielstand bleibt erhalten und ist beim nächsten Login desselben Kommandanten wieder da. */
  protected async logout(): Promise<void> {
    await this.api.logout();
  }
}
