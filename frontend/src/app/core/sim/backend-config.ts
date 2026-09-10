/**
 * Ableitung der Backend-WebSocket-URL. Der frühere Umschalter zwischen
 * lokaler Browser-Simulation und echtem Backend ist entfallen
 * (Umsetzungskonzept/15_...md, Auftrag 3): es gibt nur noch das Backend,
 * `SimulatedGameApiService` wurde gelöscht.
 *
 * Die URL bleibt bewusst RELATIV zur aufgerufenen Adresse (`location.hostname`
 * UND `location.port`) statt fest auf `localhost:8080` – nur so funktioniert
 * der LAN-Betrieb, bei dem die Seite vom Backend selbst unter dessen LAN-IP
 * ausgeliefert wird (Umsetzungskonzept/14_...md, Teil 3), und nur so verbindet
 * sich eine Testinstanz auf 8081 auch mit 8081 statt mit dem Live-Spiel auf
 * 8080 (vorher stand der Port hart im Code, und jede von 8081 ausgelieferte
 * Seite sprach still mit 8080). Einzige Ausnahme ist der Angular-Dev-Server
 * (`ng serve`, Port 4200): er liefert nur die Seite, das Backend läuft daneben
 * auf 8080. Ein `localStorage`-Override bleibt für Sonderfälle erhalten
 * (z. B. `ng serve` gegen einen Server auf einem anderen Rechner).
 */
const STORAGE_KEY_URL = 'nebula_backend_url';
const NG_SERVE_PORT = '4200';
const DEFAULT_BACKEND_PORT = '8080';

export function webSocketBackendUrl(): string {
  try {
    const override = localStorage.getItem(STORAGE_KEY_URL);
    if (override) return override;
  } catch {
    // localStorage nicht verfügbar (z. B. SSR) – Standard-URL verwenden.
  }
  if (typeof location === 'undefined') return `ws://localhost:${DEFAULT_BACKEND_PORT}/game`;
  const isHttps = location.protocol === 'https:';
  const servedByBackend = location.port !== '' && location.port !== NG_SERVE_PORT;
  const port = servedByBackend ? location.port : DEFAULT_BACKEND_PORT;
  return `${isHttps ? 'wss' : 'ws'}://${location.hostname}:${port}/game`;
}
