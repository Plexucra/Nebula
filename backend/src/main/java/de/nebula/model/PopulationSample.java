package de.nebula.model;

/**
 * Ein Messpunkt des Bevölkerungsverlaufs EINER Kolonie (Umsetzungskonzept/18_...md).
 * Bewusst schlank: nur was die Verlaufsgrafik und die Phasen-Einordnung brauchen.
 */
public class PopulationSample {
  public long at;
  public double population;
  public double standardOfLivingPct;
  public double housingCapacity;
  public PopulationGrowthState growthState;
}
