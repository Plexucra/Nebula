import { Id } from './common.model';

/**
 * Die zwei Blockade-Anker, die eine Flotte in einem System bilden kann
 * (Mechanik/06_..., stark vereinfacht – siehe
 * `SimulatedGameApiService.formBlockade`): `'Gateway'` = ausgehend vom
 * Systemhandelsposten ("Gateway blockieren"), `'PlanetOrbit'` = ausgehend
 * vom Orbit eines konkreten Planeten ("Planet blockieren"), auch bei
 * unbesiedelten Planeten möglich.
 */
export type BlockadeAnchorKind = 'Gateway' | 'PlanetOrbit';

/** Bewegungsziel-artiges Argument für `formBlockade` – siehe `BlockadeAnchorKind`. */
export type BlockadeAnchor =
  | { kind: 'Gateway' }
  | { kind: 'PlanetOrbit'; planetId: Id };

/**
 * Eine Blockade macht die sie bildende Flotte angreifbar – OHNE aktive
 * Blockade ist `engageBattle` gesperrt (Mechanik/06_..., "Flotten sind nur
 * an einer Blockadestelle angreifbar", hier vereinfacht auf genau zwei
 * Ankerarten je System ohne Mobilmachungsrampe/Expositionslimit/
 * Mehrparteien-Blockaden, siehe Klassendoku über `engageBattle`).
 * Höchstens EINE Blockade je Anker (je System für `'Gateway'`, je Planet
 * für `'PlanetOrbit'`) – erzwungen in `formBlockade`. Wird automatisch
 * aufgehoben, wenn die blockierende Flotte den Ort verlässt
 * (`moveFleetWithinSystem`) oder im Kampf vollständig vernichtet wird.
 */
export interface Blockade {
  id: Id;
  systemId: Id;
  anchorKind: BlockadeAnchorKind;
  /** Nur bei `anchorKind === 'PlanetOrbit'` gesetzt. */
  planetId: Id | null;
  fleetId: Id;
  ownerId: Id;
  startedAt: number;
}
