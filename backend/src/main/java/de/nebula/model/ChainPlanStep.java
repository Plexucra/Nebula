package de.nebula.model;

/**
 * Ein Schritt in der einmalig vorausberechneten Produktionskette eines
 * Auftrags (siehe {@link ChainPlan}) – vom Rohstoff bis zum angeforderten
 * Endprodukt selbst (letzter Eintrag). Rein informativ für die aufklappbare
 * Detailansicht; für die Ausführung zählt nur {@code ChainPlan.totalHours}.
 */
public class ChainPlanStep {
  public String productTypeId;
  /** Gesamtbedarf über alle georderten Einheiten des Wurzelprodukts hinweg. */
  public double quantityNeeded;
  /** Davon bereits im Kolonielager vorhanden (wird nicht erneut produziert). */
  public double quantityFromWarehouse;
  /** Davon tatsächlich zu produzieren. */
  public double quantityToProduce;
  /** Produktionszeit in Spielstunden für {@code quantityToProduce}, zu den Geschwindigkeitsfaktoren der Kolonie zum Berechnungszeitpunkt. */
  public double hours;
}
