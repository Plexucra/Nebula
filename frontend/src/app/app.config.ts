import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { GAME_API } from './core/sim/game-api.token';
import { WebSocketGameApiService } from './core/sim/websocket-game-api.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // Kapselgrenze: das gesamte Feature-Layer hängt nur von GAME_API ab.
    // Seit Umsetzungskonzept/15_...md, Auftrag 3 gibt es dahinter nur noch
    // EINE Implementierung – das Backend ist die alleinige Wahrheit über
    // Spielregeln. Die frühere Browser-Simulation (`SimulatedGameApiService`)
    // wurde samt ihrem gesamten Regel-/Katalog-Code gelöscht, weil zwei
    // parallele Regelimplementierungen zwangsläufig auseinanderlaufen.
    { provide: GAME_API, useClass: WebSocketGameApiService },
  ],
};
