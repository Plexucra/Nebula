package de.nebula.model;

import java.util.List;

public class ChainPlan {
  /** Summe aller {@code steps[].hours} – die einzige für die Ausführung relevante Zeitgröße. */
  public double totalHours;
  public List<ChainPlanStep> steps;
  /** false = ohne "automatisch mitproduzieren" nicht ausführbar. */
  public boolean feasible;
  /**
   * Summe aller Arbeitsstunden über sämtliche Kettenschritte
   * ({@code ProductType.workHoursPerUnit × step.quantityToProduce}) – die für den
   * kompletten Auftrag aufgewendete Arbeitsleistung, unabhängig davon, wie viele
   * Schritte die Kette hat (Umsetzungskonzept/20_...md).
   */
  public double totalWorkHours;
  /**
   * {@code totalWorkHours / totalHours} – wie viele Arbeitskräfte der Auftrag im
   * Schnitt über seine gesamte Laufzeit bindet. Ergänzt die je Schritt sichtbare
   * {@code ChainPlanStep.workersBoundPerHour} um eine Aussage auf Auftragsebene,
   * die bei mehrstufigen Ketten (z. B. Grundnahrung) sonst fehlt.
   */
  public double workersBoundPerHour;

  public ChainPlan() {
  }

  public ChainPlan(double totalHours, List<ChainPlanStep> steps, boolean feasible) {
    this(totalHours, steps, feasible, 0, 0);
  }

  public ChainPlan(double totalHours, List<ChainPlanStep> steps, boolean feasible, double totalWorkHours,
                    double workersBoundPerHour) {
    this.totalHours = totalHours;
    this.steps = steps;
    this.feasible = feasible;
    this.totalWorkHours = totalWorkHours;
    this.workersBoundPerHour = workersBoundPerHour;
  }
}
