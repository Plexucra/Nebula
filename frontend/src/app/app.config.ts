import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { GAME_API } from './core/sim/game-api.token';
import { WebSocketGameApiService } from './core/sim/websocket-game-api.service';

// Die gesamte Oberfläche ist deutsch, die Zahlen waren es nicht: ohne
// registrierte Locale formatiert Angulars `DecimalPipe` nach en-US und schrieb
// "1,543 Einwohner" statt "1.543" – im selben Satz neben hartkodierten
// deutschen Zahlen ("Stufe 1 fasst 20.000 Einwohner"). Deshalb hier EINMAL
// zentral registriert, statt in jeder Pipe ein Locale-Argument mitzuschleppen.
registerLocaleData(localeDe);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    { provide: LOCALE_ID, useValue: 'de-DE' },
    // Kapselgrenze: das gesamte Feature-Layer hängt nur von GAME_API ab.
    // Seit Umsetzungskonzept/15_...md, Auftrag 3 gibt es dahinter nur noch
    // EINE Implementierung – das Backend ist die alleinige Wahrheit über
    // Spielregeln. Die frühere Browser-Simulation (`SimulatedGameApiService`)
    // wurde samt ihrem gesamten Regel-/Katalog-Code gelöscht, weil zwei
    // parallele Regelimplementierungen zwangsläufig auseinanderlaufen.
    { provide: GAME_API, useClass: WebSocketGameApiService },
  ],
};
