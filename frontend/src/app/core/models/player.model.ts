import { Id } from './common.model';

/**
 * Bei der Registrierung gewählte Spielerart (Umsetzungskonzept/20_...md).
 * `Normal`: unsere Standard-Startbedingungen. `Npc`: von der NPC-Bot-Armee
 * genutzt, Heimatsystem wird beim eigenen Lager (`campId`) platziert.
 * `Test`: Aufhänger für Sonderausstattung bei gezielten Tests, aktuell
 * identisch zu `Normal`.
 */
export type PlayerRole = 'Normal' | 'Npc' | 'Test';

export interface Player {
  id: Id;
  name: string;
  homeworldColonyId: Id;
  homeSystemId: Id;
  createdAt: number;
  role: PlayerRole;
  /** Lager-Kennzeichen für `role === 'Npc'` (z. B. "NORD"/"SUED"), sonst null. */
  campId: string | null;
}

export interface System {
  id: Id;
  /** Laufende Nummer ab 1 – die kurze, eindeutige Adresse eines Systems; beim Bewegen direkt eingebbar. */
  number: number;
  name: string;
  /** Grobe Galaxie-Koordinaten für die Kartendarstellung. */
  x: number;
  y: number;
  planetIds: Id[];
  gatewayId: Id;
  /** true = eigenes Heimatsystem eines Spielers (narrativ, für Prototyp-Flair). */
  isHomeSystem: boolean;
  factionFlavor: string;
  /** true = sektorale Handelsstation der Handelsgilde, siehe Konzeption/05_..., §5. */
  isTradeHub: boolean;
}
