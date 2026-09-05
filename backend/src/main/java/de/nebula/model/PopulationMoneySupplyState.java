package de.nebula.model;

/** Höchststand-Regel gegen Bevölkerungs-Exploits, siehe Konzeption/06_..., §4. */
public class PopulationMoneySupplyState {
  public String planetId;
  public double historicalPeakPopulation;
  public double lastPopulation;
}
