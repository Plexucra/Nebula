package de.nebula.model;

import java.util.List;
import java.util.Map;

/**
 * Ein einzelner Bodenkampf-Tick für den Kampfbericht. Wie beim Raumgefecht
 * ({@link BattleTickResult}) sind {@code ...UnitsBefore} die zu Tickbeginn
 * kampffähigen und damit an diesem Tick TEILNEHMENDEN Einheiten je Seite –
 * Mechanik/04_..., §1. Anders als dort zählt hier ausschließlich der AKTIVE
 * Bestand: Reserve-Drohnen und Reserve-Soldaten nehmen nicht teil und können
 * in einem normalen Kampftick auch keinen Schaden nehmen (Mechanik/05_...,
 * §3).
 */
public class GroundBattleTickResult {
  public int tick;
  public long atTime;
  /** In welcher Phase dieser Tick gerechnet wurde – die Zahlen darunter bedeuten je nach Phase Verschiedenes. */
  public GroundBattlePhase phase;
  public List<GroundForceUnitStack> attackerUnitsBefore;
  public List<GroundForceUnitStack> defenderUnitsBefore;
  /** Verluste GENAU dieses Ticks je Einheiten-ProductType, inklusive der mit den Drohnen gefallenen Soldaten. */
  public Map<String, Integer> attackerLosses;
  public Map<String, Integer> defenderLosses;
  /**
   * Zivilbevölkerung der angegriffenen Kolonie, die in diesem Tick umgekommen
   * ist: im Kampftick als Kollateralschaden (Mechanik/05_..., §2), im
   * Belagerungstick die gefallenen Aufständischen.
   */
  public double civiliansLost;

  // --- nur in der Belagerungsphase belegt ---------------------------------
  /** Soldaten des Angreifers, die diesen Belagerungstick bestritten haben. */
  public int attackerSoldiers;
  /** Aufständische Zivilisten, die diesen Belagerungstick bestritten haben (10 % der Bevölkerung). */
  public int rebels;
  /** Loyalität der Kolonie vor und nach diesem Tick – die einzige Größe, die über den Ausgang entscheidet. */
  public double loyaltyPctBefore;
  public double loyaltyPctAfter;
}
