import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NOTIFICATION_RETENTION_REAL_DAYS } from '../core/shared-constants';
import { GAME_API } from '../core/sim/game-api.token';
import { Battle, GroundBattle, Id, NotificationType } from '../core/models';
import { UiClockService } from '../core/ui/ui-clock.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
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

  /** Reaktiv: `player()` ist beim echten Backend erst nach der ersten Server-Antwort gesetzt (siehe TradeOverviewComponent). */
  private readonly homeSystemId = computed(() => this.api.player()?.homeSystemId ?? '');
  protected readonly gateway = computed(() => this.api.gateway(this.homeSystemId())());
  protected readonly gatewayActive = () => this.gateway()?.state === 'Active';

  /**
   * Laufende Gefechte – dauerhaft und rot in der Kopfzeile, weil ein Kampf die
   * einzige Lage ist, die ohne Zutun des Kommandanten Schiffe kostet und in
   * Sekunden entschieden ist. Vorher war er nur zu sehen, wenn man zufällig auf
   * der Flotten- oder Diplomatieseite stand.
   */
  protected readonly activeBattles = this.api.activeBattles();
  protected readonly activeGroundBattles = this.api.activeGroundBattles();
  protected readonly activeBattleCount = computed(
    () => this.activeBattles().length + this.activeGroundBattles().length);

  /** Ziel des Kampf-Anzeigers: bei genau einem Gefecht direkt der Bericht, sonst die Übersicht. */
  protected readonly battleIndicatorLink = computed(() => {
    const space = this.activeBattles();
    const ground = this.activeGroundBattles();
    if (space.length === 1 && ground.length === 0) return `/kampfbericht/${space[0].reportToken}`;
    if (ground.length === 1 && space.length === 0) return `/bodenkampfbericht/${ground[0].reportToken}`;
    return '/diplomatie';
  });

  protected readonly battleIndicatorTitle = computed(() => {
    const n = this.activeBattleCount();
    return n === 1 ? 'Ein Gefecht läuft gerade – Bericht öffnen' : `${n} Gefechte laufen gerade – Übersicht öffnen`;
  });

  /**
   * Saldo je Spielstunde neben dem Guthaben. Ohne diese Zahl war ein
   * schleichender Bankrott unsichtbar: Im Test fiel ein Konto von 139.581 auf
   * 0 Credits, ohne dass die Oberfläche das irgendwo angezeigt hätte.
   */
  protected readonly treasuryFlowPerHour = this.api.treasuryFlowPerHour();

  protected readonly notifications = this.api.notifications();
  protected readonly unreadNotificationCount = this.api.unreadNotificationCount();
  protected readonly notificationPanelOpen = signal(false);
  /**
   * Eingeklappte Hauptnavigation auf schmalen Displays (Umsetzungskonzept/16_...md).
   * Auf dem Desktop ist die Seitenleiste unverändert dauerhaft sichtbar – dieser
   * Schalter wirkt ausschließlich unterhalb des Mobil-Breakpoints.
   */
  protected readonly mobileNavOpen = signal(false);

  protected toggleMobileNav(): void {
    this.mobileNavOpen.update(v => !v);
  }

  protected closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }
  protected readonly unreadMessageCount = this.api.unreadMessageCount();

  protected toggleNotificationPanel(): void {
    this.notificationPanelOpen.update(v => !v);
  }

  /**
   * Schließt das Benachrichtigungsfeld bei einem Klick daneben oder mit Escape.
   * Vorher blieb es offen stehen und verdeckte die halbe Seite, bis man erneut
   * genau die Glocke traf.
   */
  protected closeNotificationPanel(): void {
    this.notificationPanelOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.notificationPanelOpen.set(false);
    this.mobileNavOpen.set(false);
  }

  protected async markRead(id: Id): Promise<void> {
    await this.api.markNotificationRead(id);
  }

  protected async markAllRead(): Promise<void> {
    await this.api.markAllNotificationsRead();
  }

  /** Hinweistext zur Aufbewahrungsfrist – Zahlen aus `shared/game-constants.json` (siehe Backend `SharedConstants`). */
  protected readonly keepHint =
    `Ohne "Beibehalten" wird diese Benachrichtigung nach ${NOTIFICATION_RETENTION_REAL_DAYS} echten Tagen automatisch gelöscht.`;

  /**
   * "Beibehalten": ohne diesen Schalter räumt der Server Benachrichtigungen
   * nach `NOTIFICATION_RETENTION_REAL_DAYS` echten Tagen weg (siehe
   * `RetentionCleanup` im Backend, REALZEIT-AUSNAHME).
   */
  protected async toggleNotificationKeep(id: Id, keep: boolean): Promise<void> {
    await this.api.setNotificationKeep(id, keep);
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
    { path: '/diplomatie', label: 'Diplomatie / Krieg', icon: '⚔' },
    { path: '/nachrichten', label: 'Nachrichten', icon: '✉' },
    { path: '/galaxie', label: 'Galaxiekarte', icon: '✦' },
    { path: '/handel', label: 'Handel', icon: '⇄' },
    { path: '/konto', label: 'Konto', icon: '◈' },
    { path: '/statistiken', label: 'Statistiken', icon: '▤' },
  ];

  /** Löscht die GESAMTE gemeinsame Galaxie – auch die aller anderen Kommandanten, nicht nur den eigenen Fortschritt. */
  protected async resetGame(): Promise<void> {
    // Ein einzelnes "OK" ist für eine Aktion, die stundenlang gewachsene Reiche
    // ALLER Mitspieler löscht, zu wenig – deshalb muss der Wortlaut getippt
    // werden. (Eine echte Berechtigungsprüfung kommt mit der Anmeldung.)
    const expected = 'GALAXIE LOESCHEN';
    const answer = prompt(
      'Das löscht die komplette Galaxie – auch die Kolonien, Flotten und Konten ALLER anderen Kommandanten. '
      + `Zum Bestätigen "${expected}" eingeben:`);
    if (answer?.trim().toUpperCase() !== expected) return;
    await this.api.resetGame();
  }

  /** Meldet nur ab – der Spielstand bleibt erhalten und ist beim nächsten Login desselben Kommandanten wieder da. */
  protected async logout(): Promise<void> {
    await this.api.logout();
  }
}
