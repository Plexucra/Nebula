package de.nebula.data;

import de.nebula.engine.GameConstants;
import de.nebula.model.ProductType;
import de.nebula.model.RecipeInput;

import java.util.HashMap;
import java.util.Map;

/**
 * Rekursive Produktionskosten-Schätzung, ausschließlich als Preisanker für
 * den Market-Maker der Handelsgilde-Stationen (Umsetzungskonzept/22_...md).
 * Seit Umsetzungskonzept/38_...md kostet Produktion tatsächlich Löhne
 * ({@code Formulas.wageFor}: Katalog-Arbeitsstunden durch die Produktivität
 * {@code productionSpeedMultiplier} mal Lohnsatz). Diese Klasse rechnet
 * dagegen mit den UNGESTAUCHTEN Katalog-Arbeitsstunden – bewusst: sie
 * liefert nur RELATIVE Preise (welches Gut teurer ist als welches) für die
 * Handelsgilde-Orders und die Gewichtung der Bevölkerungsgebote, keine
 * Geldbewegung im Spiel.
 *
 * <p>{@code kosten(p) = workHoursPerUnit(p) × Lohn + Σ Rezeptmenge × kosten(Eingang)},
 * rekursiv bis zu den Rohstoffen (leeres Rezept). Ergebnisse werden
 * einmalig gecacht, da das Rezept-Netz statisch ist und die Rekursion sonst
 * bei jedem Aufruf die ganze Kette neu durchliefe.</p>
 */
public final class ProductCosts {
  private ProductCosts() {
  }

  /** Derselbe Lohnsatz wie in {@code Economy} – eine Quelle ({@code shared/game-constants.json}), keine zweite Ablage mehr. */
  private static final double WAGE_PER_POPULATION_PER_GAME_HOUR = GameConstants.WAGE_PER_WORK_HOUR;

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
