package de.nebula.model;

/** Aktiver Zustand der Bevölkerungsentwicklung, siehe {@code Formulas.populationGrowthDelta}. */
public enum PopulationGrowthState {
  Shrinking, Holding, Growing, Overcrowded
}
