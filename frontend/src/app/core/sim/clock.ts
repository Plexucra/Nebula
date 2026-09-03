/**
 * Zeitkompression für den Prototyp.
 *
 * Die Konzeption rechnet in Spielstunden (z. B. 12h Verteidigungs-Anlaufzeit,
 * 24h Kriegs-Sperrfrist, 8h Kampftick). Damit sich die Simulation im Browser
 * tatsächlich beobachten lässt, wird 1 Spielstunde auf wenige Sekunden
 * Realzeit gestaucht. Dieser Faktor ist bewusst an einer einzigen Stelle
 * zentralisiert und hat mit der eigentlichen Simulationslogik nichts zu tun –
 * ein echtes Backend würde stattdessen echte Stunden verwenden.
 */
export const REAL_MS_PER_GAME_HOUR = 2500;

export function hoursToMs(hours: number): number {
  return hours * REAL_MS_PER_GAME_HOUR;
}

export function msToHours(ms: number): number {
  return ms / REAL_MS_PER_GAME_HOUR;
}

export function now(): number {
  return Date.now();
}
