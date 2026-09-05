import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { useWebSocketBackend } from './core/sim/backend-config';
import { GAME_API } from './core/sim/game-api.token';
import { SimulatedGameApiService } from './core/sim/simulated-game-api.service';
import { WebSocketGameApiService } from './core/sim/websocket-game-api.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // Kapselgrenze: das gesamte Feature-Layer hängt nur von GAME_API ab.
    // `useWebSocketBackend()` schaltet zur Laufzeit (via localStorage, siehe
    // backend-config.ts) zwischen der lokalen Browser-Simulation und dem
    // echten Quarkus-Backend um, ohne dass Feature-Code sich ändern muss –
    // siehe Umsetzungskonzept/13_Client_Server_Migration_Quarkus_Backend.md.
    { provide: GAME_API, useClass: useWebSocketBackend() ? WebSocketGameApiService : SimulatedGameApiService },
  ],
};
