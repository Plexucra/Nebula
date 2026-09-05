package de.nebula.model;

public class Specialization {
  public String colonyId;
  public String productTypeId;
  /** Stufe der Spezialisierung, 0 = keine. */
  public int currentLevel;
  /** Kumulierte "Erfahrung" in Spielstunden investierter Produktionszeit, treibt Stufenaufstieg. */
  public double experience;
  public double thresholdForNextLevel;
}
