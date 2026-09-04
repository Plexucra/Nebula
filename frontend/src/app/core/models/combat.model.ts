import { Id } from './common.model';

export type BattleStatus = 'Active' | 'Ended';
export type BattleOutcome = 'AttackerVictory' | 'DefenderVictory' | 'Retreat';

/** Verluste eines einzelnen Kampf-Ticks, je Schiffs-ProductType. */
export interface BattleTickResult {
  tick: number;
  atTime: number;
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
