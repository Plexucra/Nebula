package de.nebula.model;

import java.util.List;

public class ChainPlan {
  /** Summe aller {@code steps[].hours} – die einzige für die Ausführung relevante Zeitgröße. */
  public double totalHours;
  public List<ChainPlanStep> steps;
  /** false = ohne "automatisch mitproduzieren" nicht ausführbar. */
  public boolean feasible;

  public ChainPlan() {
  }

  public ChainPlan(double totalHours, List<ChainPlanStep> steps, boolean feasible) {
    this.totalHours = totalHours;
    this.steps = steps;
    this.feasible = feasible;
  }
}
