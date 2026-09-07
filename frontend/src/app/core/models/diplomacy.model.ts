import { Id } from './common.model';

export type DiplomaticStatus = 'Peace' | 'War';

/**
 * Genau eine Beziehung je ungeordnetem Kommandanten-Paar (siehe
 * `DiplomacyCommands.relationKey` im Backend, das `playerAId`/`playerBId` beim
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

/**
 * Art eines `Treaty`/`TreatyOffer` (siehe Umsetzungskonzept/21_...md).
 * `Peace` blockiert einseitige Kriegserklärungen zwischen den Parteien,
 * `Trade` erlaubt planetaren Handel zwischen ihnen – unabhängig voneinander
 * abschließbar.
 */
export type TreatyType = 'Peace' | 'Trade';

/**
 * Förmlicher, beidseitig angenommener Friedens- oder Handelsvertrag –
 * zusätzlich zum einfachen Kriegs-/Friedenszustand aus `DiplomaticRelation`.
 * Höchstens EIN aktiver Vertrag je `TreatyType` und ungeordnetem
 * Kommandanten-Paar. Eine Kündigung löscht den Vertrag NICHT sofort,
 * sondern setzt `terminationEffectiveAt` – bis dahin bleibt er voll gültig.
 */
export interface Treaty {
  id: Id;
  playerAId: Id;
  playerBId: Id;
  type: TreatyType;
  since: number;
  /** `null` = ungekündigt. Gesetzt = Kündigungsfrist läuft, Vertrag bis dahin unverändert gültig. */
  terminationEffectiveAt: number | null;
}

/**
 * Einseitiges Angebot für einen neuen Friedens- oder Handelsvertrag – wird
 * erst mit `respondToTreatyOffer(accept: true)` wirksam (siehe `Treaty`).
 */
export interface TreatyOffer {
  id: Id;
  fromPlayerId: Id;
  toPlayerId: Id;
  type: TreatyType;
  createdAt: number;
}
