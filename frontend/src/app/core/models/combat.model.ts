import { Id } from './common.model';
import { FleetShipGroup } from './fleet.model';
import { GroundForceUnitStack } from './ground-forces.model';

export type BattleStatus = 'Active' | 'Ended';
export type BattleOutcome = 'AttackerVictory' | 'DefenderVictory' | 'Retreat';

/**
 * Ein einzelner Kampf-Tick für den Kampfbericht (`SystemViewComponent`
 * betrifft das nicht – siehe `BattleReportComponent`):
 * `attackerShipsBefore`/`defenderShipsBefore` sind die zu Tickbeginn noch
 * kampffähigen (und damit an diesem Tick TEILNEHMENDEN) Schiffe je Seite –
 * Mechanik/04_..., §1: "Alle zu Tickbeginn kampffähigen Einheiten
 * verursachen ihren Schaden auch dann noch, wenn sie im selben Tick
 * zerstört werden." `...Losses` sind die in GENAU diesem Tick daraus
 * resultierenden Verluste.
 */
export interface BattleTickResult {
  tick: number;
  atTime: number;
  attackerShipsBefore: FleetShipGroup[];
  defenderShipsBefore: FleetShipGroup[];
  attackerLosses: Record<Id, number>;
  defenderLosses: Record<Id, number>;
}

/**
 * Raumgefecht zwischen genau zwei Flotten zweier miteinander im Krieg
 * stehender Kommandanten (bewusste Vereinfachung ggü. der vollen
 * Blockade-/Mobilmachungs-/Expositions-Mechanik aus Mechanik/06_...md –
 * siehe Kommentar über `BattleCommands.engageBattle` im Backend). Kein
 * Bodenkampf: Bodentruppen (`GroundForceGroup`) nehmen an einem `Battle`
 * NICHT teil, siehe dortiger Kommentar.
 */
export interface Battle {
  id: Id;
  /**
   * Unerratbares Token für den öffentlich abrufbaren, teilbaren
   * Kampfbericht (`/kampfbericht/:token`, siehe `BattleReportComponent` und
   * `GameApi.battleByReportToken`) – bewusst NICHT `id` (fortlaufender,
   * erratbarer Zähler, siehe `nextId`), sondern ein eigener Zufallswert
   * (`randomToken()`). Ab dem ERSTEN Kampf-Tick abrufbar, siehe
   * `engageBattle` (Bericht existiert schon bei Kampfbeginn, nicht erst am
   * Ende).
   */
  reportToken: string;
  systemId: Id;
  attackerId: Id;
  defenderId: Id;
  attackerFleetId: Id;
  defenderFleetId: Id;
  status: BattleStatus;
  startedAt: number;
  /** Zeitpunkt des nächsten Kampf-Ticks (8 Spielstunden nach dem letzten, siehe `COMBAT_TICK_HOURS`). */
  nextTickAt: number;
  ticksResolved: number;
  /**
   * Aufgelaufener, nicht-tödlicher Restschaden je Schiffs-ProductType
   * (Mechanik/04_..., §5 "Restschaden": `neu = alt + Schaden;
   * Verluste = floor(neu / Haltbarkeit); Rest = neu - Verluste × Haltbarkeit`)
   * – nur während `status === 'Active'` gepflegt, beim Kampfende verworfen.
   */
  attackerResidualDamage: Record<Id, number>;
  defenderResidualDamage: Record<Id, number>;
  /** Verlustverlauf, neuester Eintrag zuletzt – rein zur Anzeige im Kampfprotokoll der UI. */
  ticks: BattleTickResult[];
  endedAt: number | null;
  outcome: BattleOutcome | null;
}

/**
 * Ein einzelner Bodenkampf-Tick für den Bodenkampfbericht. Anders als im Raum
 * zählt hier ausschließlich der AKTIVE Bestand: Reserve-Drohnen und
 * Reserve-Soldaten nehmen nicht am Gefecht teil und können in einem normalen
 * Kampftick auch keinen Schaden nehmen (Mechanik/05_..., §3).
 */
/**
 * Ein Bodengefecht läuft in ZWEI Phasen, und der Fall der Garnison ist NICHT
 * sein Ende (Nutzervorgabe, Konkretisierung zu Mechanik/05_..., §10):
 * `Combat` = reguläre Kampfticks (Drohne gegen Drohne, Soldaten nur Bediener),
 * `Siege` = Belagerung (nur noch Soldaten gegen aufständische Zivilisten, es
 * entscheidet allein die Loyalität). Der Wechsel geht in beide Richtungen.
 */
export type GroundBattlePhase = 'Combat' | 'Siege';

export interface GroundBattleTickResult {
  tick: number;
  atTime: number;
  /** In welcher Phase dieser Tick gerechnet wurde – die Zahlen darunter bedeuten je nach Phase Verschiedenes. */
  phase: GroundBattlePhase;
  attackerUnitsBefore: GroundForceUnitStack[];
  defenderUnitsBefore: GroundForceUnitStack[];
  /** Verluste GENAU dieses Ticks je Einheiten-ProductType, inklusive der mit den Drohnen gefallenen Soldaten (§4). */
  attackerLosses: Record<Id, number>;
  defenderLosses: Record<Id, number>;
  /**
   * Zivilbevölkerung der angegriffenen Kolonie, die in diesem Tick umgekommen
   * ist: im Kampftick als Kollateralschaden (§2), im Belagerungstick die
   * gefallenen Aufständischen.
   */
  civiliansLost: number;

  // --- nur in der Belagerungsphase belegt ---------------------------------
  /** Soldaten des Angreifers, die diesen Belagerungstick bestritten haben. */
  attackerSoldiers: number;
  /** Aufständische Zivilisten dieses Belagerungsticks (10 % der Bevölkerung). */
  rebels: number;
  /** Loyalität vor und nach diesem Tick – die einzige Größe, die über den Ausgang entscheidet. */
  loyaltyPctBefore: number;
  loyaltyPctAfter: number;
}

/**
 * Bodengefecht um GENAU EINE Kolonie: ein gelandeter Verband des Angreifers
 * gegen die Garnison dieser Kolonie (Mechanik/05_..., §2, §10-12). Bewusste
 * Vereinfachung wie im Raum: strikt ein Angreifer gegen einen Verteidiger,
 * keine unterstützenden Verteidiger, keine Mehrparteien-Gefechte — mehrere
 * Angreifer führen mehrere getrennte Gefechte gegen dieselbe Kolonie
 * (siehe `GroundBattleCommands` im Backend).
 */
export interface GroundBattle {
  id: Id;
  /** Unerratbares Token für den teilbaren Bericht (`/bodenkampfbericht/:token`) – wie `Battle.reportToken`. */
  reportToken: string;
  planetId: Id;
  /** Die angegriffene Kolonie – bleibt auch nach einer Eroberung gesetzt, damit der Bericht lesbar bleibt. */
  colonyId: Id;
  attackerId: Id;
  /** Eigentümer der Kolonie BEI KAMPFBEGINN – nach einer Eroberung nicht mehr ihr aktueller Eigentümer. */
  defenderId: Id;
  attackerGroupId: Id;
  status: BattleStatus;
  /** Kampfticks oder Belagerung – das Gefecht endet NICHT mit dem Fall der Garnison. */
  phase: GroundBattlePhase;
  startedAt: number;
  nextTickAt: number;
  ticksResolved: number;
  attackerResidualDamage: Record<Id, number>;
  defenderResidualDamage: Record<Id, number>;
  ticks: GroundBattleTickResult[];
  /** Bezugsgrößen der Zivilverlustquote, EINMAL bei Kampfbeginn festgehalten (§2). */
  populationAtStart: number;
  defenderStrengthAtStart: number;
  /** Kumulierte Zivilverlustquote (0..1) – Eingangsgröße der Konfliktschäden bei der Eroberung. */
  civilianLossRatio: number;
  endedAt: number | null;
  outcome: BattleOutcome | null;
}
