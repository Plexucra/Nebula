import { Injectable, signal } from '@angular/core';

/**
 * Tickender "Wanduhr"-Signal für die UI (Countdown-Anzeigen). Getrennt von
 * der Simulationsuhr in `core/sim/clock.ts` – dient nur dazu, Templates
 * regelmäßig neu auszuwerten, ohne dass sich zugrunde liegende Daten ändern.
 */
@Injectable({ providedIn: 'root' })
export class UiClockService {
  private readonly _now = signal(Date.now());
  readonly now = this._now.asReadonly();

  constructor() {
    setInterval(() => this._now.set(Date.now()), 500);
  }
}

export function formatCountdown(msRemaining: number): string {
  if (msRemaining <= 0) return 'bereit';
  const totalSeconds = Math.ceil(msRemaining / 1000);
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return m > 0 ? `${m}m ${s.toString().padStart(2, '0')}s` : `${s}s`;
}
