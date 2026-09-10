package de.nebula.data;

import de.nebula.model.ProductType;
import de.nebula.model.RecipeInput;

import java.util.HashMap;
import java.util.Map;

/**
 * Rekursive Produktionskosten-Schätzung, ausschließlich als Preisanker für
 * den Market-Maker der Handelsgilde-Stationen (Umsetzungskonzept/22_...md).
 * Das Spiel selbst kennt sonst KEINEN Credit-Preis für Rohstoffe/Baustoffe/
 * Schiffsmodule – Produktion kostet nur Arbeitsstunden
 * ({@code ProductType.workHoursPerUnit}), keine Credits (siehe
 * {@code ProductionCommands}, {@code ChainPlanner}). Diese Klasse rechnet die
 * Arbeitsstunden trotzdem in einen Credit-Wert um, indem sie denselben
 * Lohnsatz ansetzt, den die Bevölkerung tatsächlich verdient
 * ({@code Economy.WAGE_PER_CAPITA_PER_HOUR}: 0,02 Credits je Kopf und
 * Spielstunde) – ein reines Rechenmodell, keine zusätzliche Geldbewegung im
 * Spiel.
 *
 * <p>{@code kosten(p) = workHoursPerUnit(p) × 0,02 + Σ Rezeptmenge × kosten(Eingang)},
 * rekursiv bis zu den Rohstoffen (leeres Rezept). Ergebnisse werden
 * einmalig gecacht, da das Rezept-Netz statisch ist und die Rekursion sonst
 * bei jedem Aufruf die ganze Kette neu durchliefe.</p>
 */
public final class ProductCosts {
  private ProductCosts() {
  }

  /** = {@code Economy.WAGE_PER_CAPITA_PER_HOUR}: 0,02 Credits je Kopf und Spielstunde, im Kolonietag mit 24 h verbucht. */
  private static final double WAGE_PER_POPULATION_PER_GAME_HOUR = 0.02;

  private static final Map<String, Double> CACHE = new HashMap<>();

  public static double of(String productTypeId) {
    Double cached = CACHE.get(productTypeId);
    if (cached != null) return cached;
    // Platzhalter während der Berechnung: verhindert eine Endlosrekursion, falls das Rezept-Netz
    // wider Erwarten einen Zyklus enthält (im Katalog nicht vorgesehen, aber nicht durch Typen ausgeschlossen).
    CACHE.put(productTypeId, 0.0);
    ProductType product = ProductCatalog.find(productTypeId);
    double cost = product.workHoursPerUnit * WAGE_PER_POPULATION_PER_GAME_HOUR;
    for (RecipeInput input : product.recipe) {
      cost += input.quantity * of(input.inputProductTypeId);
    }
    CACHE.put(productTypeId, cost);
    return cost;
  }
}
