package de.nebula.model;

import java.util.List;
import java.util.Map;

/**
 * Raumgefecht zwischen genau zwei Flotten zweier miteinander im Krieg
 * stehender Kommandanten (bewusste Vereinfachung ggü. der vollen
 * Blockade-/Mobilmachungs-/Expositions-Mechanik aus Mechanik/06_...md).
 * Kein Bodenkampf: {@link GroundForceGroup} nimmt an einem Battle NICHT teil –
 * dafür gibt es {@link GroundBattle}.
 */
public class Battle {
  public String id;
  /**
   * Unerratbares Token für den öffentlich abrufbaren, teilbaren Kampfbericht
   * ({@code /kampfbericht/:token}) – bewusst NICHT {@code id} (fortlaufender,
   * erratbarer Zähler), sondern ein eigener Zufallswert (UUID). Ab dem
   * ERSTEN Kampf-Tick abrufbar, nicht erst am Ende.
   */
  public String reportToken;
  public String systemId;
  public String attackerId;
  public String defenderId;
  public String attackerFleetId;
  public String defenderFleetId;
  public BattleStatus status;
  public long startedAt;
  /** Zeitpunkt des nächsten Kampf-Ticks (8 Spielstunden nach dem letzten). */
  public long nextTickAt;
  public int ticksResolved;
  /**
   * Aufgelaufener, nicht-tödlicher Restschaden je Schiffs-ProductType
   * (Mechanik/04_..., §5 "Restschaden": neu = alt + Schaden; Verluste =
   * floor(neu / Haltbarkeit); Rest = neu - Verluste × Haltbarkeit) – nur
   * während {@code status == Active} gepflegt, beim Kampfende verworfen.
   */
  public Map<String, Double> attackerResidualDamage;
  public Map<String, Double> defenderResidualDamage;
  /** Verlustverlauf, neuester Eintrag zuletzt – rein zur Anzeige im Kampfprotokoll. */
  public List<BattleTickResult> ticks;
  public Long endedAt;
  public BattleOutcome outcome;
}
