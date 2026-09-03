import { InjectionToken } from '@angular/core';
import { GameApi } from './game-api';

/**
 * DI-Token für den Backend-Vertrag. Feature-Komponenten injizieren
 * ausschließlich `GAME_API`, nie die konkrete Simulationsklasse – das ist
 * die eigentliche Kapselung, die einen späteren Austausch gegen ein echtes
 * HTTP-Backend ermöglicht, ohne Feature-Code anzufassen.
 */
export const GAME_API = new InjectionToken<GameApi>('GAME_API');
