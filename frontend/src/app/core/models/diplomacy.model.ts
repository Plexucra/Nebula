import { Id } from './common.model';

export type DiplomaticStatus = 'Peace' | 'War';

/**
 * Genau eine Beziehung je ungeordnetem Kommandanten-Paar (siehe
 * `SimulatedGameApiService.relationKey`, das `playerAId`/`playerBId` beim
 * Anlegen kanonisch sortiert) – ohne Eintrag gilt implizit `'Peace'`
 * (Standardzustand, siehe `diplomaticStatus`). `War` entsteht einseitig
 * (`declareWar`), `Peace` erst nach beidseitiger Zustimmung (siehe
 * `PeaceOffer`).
 */
export interface DiplomaticRelation {
  id: Id;
  playerAId: Id;
  playerBId: Id;
  status: DiplomaticStatus;
  /** Zeitpunkt des letzten Statuswechsels (Kriegserklärung bzw. Friedensschluss). */
  since: number;
}

/**
 * Einseitiges Friedensangebot innerhalb eines laufenden Kriegs – wird erst
 * mit `respondToPeaceOffer(accept: true)` wirksam (siehe
 * `DiplomaticRelation.status`). Ein abgelehntes oder zurückgezogenes Angebot
 * wird gelöscht, nicht als Status gespeichert.
 */
export interface PeaceOffer {
  id: Id;
  fromPlayerId: Id;
  toPlayerId: Id;
  createdAt: number;
}
