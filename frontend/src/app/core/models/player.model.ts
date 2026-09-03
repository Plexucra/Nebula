import { Id } from './common.model';

export interface Player {
  id: Id;
  name: string;
  homeworldColonyId: Id;
  homeSystemId: Id;
  createdAt: number;
}

export interface System {
  id: Id;
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
