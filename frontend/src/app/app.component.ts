import { Component, inject } from '@angular/core';
import { GAME_API } from './core/sim/game-api.token';
import { AppShellComponent } from './shell/app-shell.component';
import { NewGameComponent } from './shell/new-game.component';

@Component({
  selector: 'app-root',
  imports: [AppShellComponent, NewGameComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly api = inject(GAME_API);
  protected readonly player = this.api.player;
}
