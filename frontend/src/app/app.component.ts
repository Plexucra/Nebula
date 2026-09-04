import { Component, effect, inject, signal } from '@angular/core';
import { GAME_API } from './core/sim/game-api.token';
import { AppShellComponent } from './shell/app-shell.component';
import { NewGameComponent } from './shell/new-game.component';
import { LoginComponent } from './shell/login.component';
import { StartComponent } from './shell/start.component';

type AuthView = 'start' | 'login' | 'register';

@Component({
  selector: 'app-root',
  imports: [AppShellComponent, NewGameComponent, LoginComponent, StartComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly api = inject(GAME_API);
  protected readonly player = this.api.player;
  protected readonly authView = signal<AuthView>('start');

  constructor() {
    // Nach Logout/Reset (player() wird null) immer zur Startseite zurück,
    // statt in der zuletzt offenen Login-/Registrieren-Unteransicht zu landen.
    effect(() => {
      if (!this.player()) this.authView.set('start');
    });
  }

  protected showLogin(): void { this.authView.set('login'); }
  protected showRegister(): void { this.authView.set('register'); }
  protected showStart(): void { this.authView.set('start'); }
}
