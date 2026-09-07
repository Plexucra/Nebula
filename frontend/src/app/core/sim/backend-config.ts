/**
 * Ableitung der Backend-WebSocket-URL. Der frühere Umschalter zwischen
 * lokaler Browser-Simulation und echtem Backend ist entfallen
 * (Umsetzungskonzept/15_...md, Auftrag 3): es gibt nur noch das Backend,
 * `SimulatedGameApiService` wurde gelöscht.
 *
 * Die URL bleibt bewusst RELATIV zur aufgerufenen Adresse (`location.hostname`)
 * statt fest auf `localhost` – nur so funktioniert der LAN-Betrieb, bei dem
 * die Seite vom Backend selbst unter dessen LAN-IP ausgeliefert wird
 * (Umsetzungskonzept/14_...md, Teil 3). Ein `localStorage`-Override bleibt für
 * Sonderfälle erhalten (z. B. `ng serve` auf 4200 gegen einen Server auf einem
 * anderen Rechner).
 */
const STORAGE_KEY_URL = 'nebula_backend_url';

export function webSocketBackendUrl(): string {
  try {
    const override = localStorage.getItem(STORAGE_KEY_URL);
    if (override) return override;
  } catch {
    // localStorage nicht verfügbar (z. B. SSR) – Standard-URL verwenden.
  }
  const isHttps = typeof location !== 'undefined' && location.protocol === 'https:';
  const host = typeof location !== 'undefined' ? location.hostname : 'localhost';
  return `${isHttps ? 'wss' : 'ws'}://${host}:8080/game`;
}
