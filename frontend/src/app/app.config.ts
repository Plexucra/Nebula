import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { GAME_API } from './core/sim/game-api.token';
import { SimulatedGameApiService } from './core/sim/simulated-game-api.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // Kapselgrenze: das gesamte Feature-Layer hängt nur von GAME_API ab.
    // Ein späteres echtes Backend ersetzt hier ausschließlich `useClass`.
    { provide: GAME_API, useClass: SimulatedGameApiService },
  ],
};
