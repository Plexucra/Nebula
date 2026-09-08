package de.nebula.model;

import java.util.List;
import java.util.Map;

/**
 * Bodengefecht um GENAU EINE Kolonie: ein gelandeter Verband des Angreifers
 * ({@link GroundForceGroup} mit gesetztem {@code planetId}) gegen die
 * Garnison dieser Kolonie (Mechanik/05_Bodentruppen_und_Bodenkrieg.md §10-12).
 *
 * <p>BEWUSSTE VEREINFACHUNG, analog zum Raumgefecht ({@link Battle}): strikt
 * ein Angreifer gegen einen Verteidiger. Mechanik/05_..., §11 kennt zusätzlich
 * unterstützende Verteidiger und mehrere getrennt zurückziehbare Angreifer –
 * dafür bräuchte es Mehrparteien-Gefechte, die es auch im Raum nicht gibt.
 * Mehrere Angreifer führen deshalb mehrere getrennte Gefechte gegen dieselbe
 * Kolonie; jedes rechnet mit dem Bestand ab, der beim Beginn seines Ticks
 * tatsächlich noch da ist.</p>
 */
public class GroundBattle {
  public String id;
  /** Unerratbares Token für den teilbaren Kampfbericht – exakt wie bei {@link Battle#reportToken}. */
  public String reportToken;
  public String planetId;
  /** Die angegriffene Kolonie. Bleibt auch nach der Eroberung gesetzt (der Bericht soll lesbar bleiben). */
  public String colonyId;
  public String attackerId;
  /** Eigentümer der Kolonie BEI KAMPFBEGINN – nach einer Eroberung ist das nicht mehr ihr aktueller Eigentümer. */
  public String defenderId;
  /** Der gelandete Verband des Angreifers. Nach seiner Vernichtung existiert er nicht mehr. */
  public String attackerGroupId;
  public BattleStatus status;
  /**
   * Reguläre Kampfticks oder Belagerung – siehe {@link GroundBattlePhase}. Ein
   * Gefecht kann beliebig oft zwischen beiden wechseln und endet NICHT mit dem
   * Fall der Garnison.
   */
  public GroundBattlePhase phase;
  public long startedAt;
  /** Zeitpunkt des nächsten Kampf-Ticks ({@code Formulas.COMBAT_TICK_HOURS} Spielstunden nach dem letzten). */
  public long nextTickAt;
  public int ticksResolved;
  /** Restschaden je Einheiten-ProductType, siehe {@link Battle#attackerResidualDamage}. */
  public Map<String, Double> attackerResidualDamage;
  public Map<String, Double> defenderResidualDamage;
  public List<GroundBattleTickResult> ticks;

  /**
   * Zivilbevölkerung der Kolonie bei Kampfbeginn und militärischer Wert der
   * Verteidigung bei Kampfbeginn – die beiden Bezugsgrößen der
   * Zivilverlustquote (Mechanik/05_..., §2). Beide werden EINMAL festgehalten,
   * damit die Quote über alle Ticks hinweg dieselbe Grundlage behält.
   */
  public double populationAtStart;
  public double defenderStrengthAtStart;
  /** Kumulierte Zivilverlustquote (0..1) – Eingangsgröße der Konfliktschäden bei der Eroberung. */
  public double civilianLossRatio;

  public Long endedAt;
  public BattleOutcome outcome;
}
