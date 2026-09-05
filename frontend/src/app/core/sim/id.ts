let counter = 0;

/** Simpler, sortierbarer ID-Generator für die In-Memory-Simulation. */
export function nextId(prefix: string): string {
  counter += 1;
  return `${prefix}_${counter.toString(36)}`;
}

/**
 * Unerratbares Zufalls-Token für öffentlich teilbare Links (z. B.
 * `Battle.reportToken`) – bewusst NICHT `nextId` (fortlaufender, leicht
 * erratbarer Zähler). Nutzt `crypto.randomUUID()`, im Browser überall
 * verfügbar (Prototyp läuft nur dort).
 */
export function randomToken(): string {
  return crypto.randomUUID();
}
