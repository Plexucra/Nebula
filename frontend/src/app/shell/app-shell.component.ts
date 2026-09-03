import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { GAME_API } from '../core/sim/game-api.token';

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

  protected readonly player = this.api.player;
  protected readonly wallet = this.api.wallet;

  private readonly homeSystemId = this.api.player()?.homeSystemId ?? '';
  protected readonly gateway = this.api.gateway(this.homeSystemId);
  protected readonly gatewayActive = () => this.gateway()?.state === 'Active';

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

  protected async resetGame(): Promise<void> {
    if (!confirm('Simulation wirklich zurücksetzen? Der gesamte Fortschritt geht verloren.')) return;
    await this.api.resetGame();
  }
}
