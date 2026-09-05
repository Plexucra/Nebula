import { Id } from './common.model';
import { FleetShipGroup } from './fleet.model';

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
 * siehe Kommentar über `SimulatedGameApiService.engageBattle`). Kein
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
