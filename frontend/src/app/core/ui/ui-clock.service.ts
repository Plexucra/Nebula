import { Injectable, signal } from '@angular/core';

/**
 * Die SPIELUHR der Oberfläche, als tickendes Signal (alle 500 ms) für
 * Countdowns, Fortschrittsbalken und Altersangaben.
 *
 * <p>Sie ist NICHT die Wanduhr des Browsers. Jeder Zeitstempel, den der Server
 * liefert (`endsAt`, `arrivesAt`, `createdAt`, `nextTickAt`, ...), steht in
 * der Spielzeit des Servers, und die kann gegen die Wanduhr verschoben sein
 * (`Clock` im Backend: nach dem Laden eines Spielstands läuft die Spieluhr
 * dort weiter, wo sie stand). Der Server schickt seine Spielzeit mit jeder
 * Nachricht (`ServerMessage.gameNow`); `syncFromServer` stellt daran den
 * Versatz ein, und `now()` liefert Wanduhr plus Versatz. Wer hier
 * `Date.now()` gegen einen Serverzeitstempel rechnet, zeigt nach einem
 * Neustart des Servers falsche Restzeiten an.</p>
 */
@Injectable({ providedIn: 'root' })
export class UiClockService {
  /** Spieluhr minus Wanduhr in Millisekunden; 0, bis die erste Servernachricht eintrifft. */
  private offsetMs = 0;
  private readonly _now = signal(Date.now());
  /** Aktuelle SPIELZEIT in Millisekunden – die einzige Zeitbasis für Vergleiche mit Serverzeitstempeln. */
  readonly now = this._now.asReadonly();
  private readonly _offset = signal(0);
  /** Versatz Spieluhr − Wanduhr, für Hinweise in der Oberfläche (0 = beide laufen gleich). */
  readonly offset = this._offset.asReadonly();

  constructor() {
    setInterval(() => this._now.set(Date.now() + this.offsetMs), 500);
  }

  /** Vom `WebSocketGameApiService` an jeder Servernachricht gerufen. */
  syncFromServer(gameNow: number): void {
    const offset = gameNow - Date.now();
    // Kleine Schwankungen durch die Übertragung nicht als Uhrenkorrektur werten –
    // sonst zittern Countdowns mit der Netzlatenz.
    if (Math.abs(offset - this.offsetMs) < 250) return;
    this.offsetMs = offset;
    this._offset.set(offset);
    this._now.set(Date.now() + offset);
  }
}

/**
 * Restzeit als Countdown. Bei abgelaufener Zeit stand hier "bereit" – neben dem
 * Etikett "LÄUFT" derselben Zeile las sich das wie ein widersprüchlicher
 * Status. Ein Countdown, der abgelaufen ist, ist schlicht bei null.
 */
export function formatCountdown(msRemaining: number): string {
  if (msRemaining <= 0) return 'gleich fertig';
  const totalSeconds = Math.ceil(msRemaining / 1000);
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return m > 0 ? `${m}m ${s.toString().padStart(2, '0')}s` : `${s}s`;
}
