/**
 * Umschalter zwischen `SimulatedGameApiService` (lokale Browser-Simulation)
 * und `WebSocketGameApiService` (echtes Quarkus-Backend), siehe
 * Umsetzungskonzept/13_Client_Server_Migration_Quarkus_Backend.md,
 * Abschnitt "Frontend-Anpassung". Bewusst als Laufzeit-Schalter über
 * `localStorage` statt Angular-`environment.ts`/`fileReplacements`
 * umgesetzt: so lässt sich das Backend im selben Build ohne Neukompilieren
 * umschalten (z. B. für den End-to-End-Rauchtest gegen den laufenden
 * `quarkus:dev`-Server), was für diesen Migrationsschritt wichtiger ist als
 * eine Build-Zeit-Trennung. `SimulatedGameApiService` bleibt der Standard
 * (Fallback) im lokalen `ng serve` (Port 4200), solange nichts explizit
 * umgeschaltet wurde.
 */
const STORAGE_KEY = 'nebula_backend';
const STORAGE_KEY_URL = 'nebula_backend_url';
const ANGULAR_DEV_SERVER_PORT = '4200';

/**
 * Außerhalb des Angular-Dev-Servers (Port {@link ANGULAR_DEV_SERVER_PORT})
 * liefert Quarkus die Seite selbst aus (Umsetzungskonzept/14_...md, Teil 3
 * "LAN-Betrieb") – z. B. von einem Smartphone im selben WLAN aus aufgerufen.
 * Dort lässt sich `localStorage` vorher nicht manuell setzen, eine lokale
 * Browser-Simulation ohne gemeinsame Galaxie ergäbe dort ohnehin keinen Sinn.
 * Deshalb: WebSocket-Backend ist automatisch der Standard, sobald die Seite
 * NICHT vom Dev-Server kommt – bleibt aber über das bestehende
 * `localStorage`-Flag in beide Richtungen explizit umschaltbar.
 */
export function useWebSocketBackend(): boolean {
  try {
    const override = localStorage.getItem(STORAGE_KEY);
    if (override === 'websocket') return true;
    if (override === 'simulation') return false;
  } catch {
    // localStorage nicht verfügbar (z. B. SSR) – auf die Port-Heuristik ausweichen.
  }
  const port = typeof location !== 'undefined' ? location.port : ANGULAR_DEV_SERVER_PORT;
  return port !== ANGULAR_DEV_SERVER_PORT;
}

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
