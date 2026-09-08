package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.Colony;
import de.nebula.model.Planet;
import de.nebula.model.PlanetResourceConcentration;
import de.nebula.model.Population;
import de.nebula.model.ProductType;
import de.nebula.model.RecipeInput;
import de.nebula.model.Specialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1:1-Portierung von {@code planChain}/{@code computeProductionHours} aus
 * {@code simulated-game-api.service.ts} (Umsetzungskonzept/10_
 * Sequentielle_Produktionsauftraege_und_Ereignissystem.md). Berechnet die
 * GESAMTE Produktionskette für {@code quantity} Einheiten eines Produkts in
 * EINEM Rutsch (aggregierter Bedarf, Lagerabgleich, Produktionszeit je
 * Schritt zu den AKTUELLEN Geschwindigkeitsfaktoren der Kolonie) statt vieler
 * einzelner Aufträge – Kernstück der sequentiellen Warteschlange.
 */
public final class ChainPlanner {
  private ChainPlanner() {
  }

  /**
   * Das Kolonisationsschiff braucht IMMER exakt {@link GameConstants#COLONY_SHIP_BUILD_HOURS}
   * (eine Spielwoche) – weder Werftstufe noch Spezialisierung, Fördergüte, Blackout
   * oder verfügbare Arbeitskraft verändern das (Umsetzungskonzept/24_...md). Deshalb
   * die Abkürzung vor jeder Bonusrechnung, in beiden Zeitfunktionen identisch.
   */
  private static boolean hasFixedBuildTime(ProductType product) {
    return GameConstants.COLONY_SHIP_PRODUCT_ID.equals(product.id);
  }

  /** Produktionszeit EINER Einheit in Spielstunden, zu den aktuellen Geschwindigkeitsfaktoren der Kolonie. */
  public static double computeProductionHours(GameState state, String colonyId, ProductType product, String facilityTypeId) {
    if (hasFixedBuildTime(product)) return GameConstants.COLONY_SHIP_BUILD_HOURS;
    double population = 100;
    for (Population p : state.populations) {
      if (p.colonyId.equals(colonyId)) {
        population = p.currentCount;
        break;
      }
    }
    int level = GameQueries.getBuildingLevel(state, colonyId, facilityTypeId);
    // Soldaten sind laut Mechanik/05_..., §5 "nicht durch Produktspezialisierung
    // effizienter machbar" – im Unterschied zu allen anderen Produkten.
    boolean isSoldier = product.id.equals("p_soldier");
    double spec = 0;
    if (!isSoldier) {
      for (Specialization s : state.specializations) {
        if (s.colonyId.equals(colonyId) && s.productTypeId.equals(product.id)) {
          spec = s.currentLevel;
          break;
        }
      }
    }
    double concFactor = 1;
    if (product.tier == 0 && !product.resourceProfile.isEmpty()) {
      Colony colony = null;
      for (Colony c : state.colonies) if (c.id.equals(colonyId)) colony = c;
      Planet planet = null;
      if (colony != null) for (Planet p : state.planets) if (p.id.equals(colony.planetId)) planet = p;
      String resId = product.resourceProfile.get(0).resourceTypeId;
      double conc = 50;
      if (planet != null) {
        for (PlanetResourceConcentration c : planet.resourceConcentration) {
          if (c.resourceTypeId.equals(resId)) {
            conc = c.concentration;
            break;
          }
        }
      }
      concFactor = Formulas.resourceConcentrationFactor(conc);
    }
    double blackoutFactor = PowerGrid.isBlackout(state, colonyId) ? Formulas.BLACKOUT_PRODUCTION_FACTOR : 1;
    // Alle Boni AUSSER Arbeitskraft – daraus ergibt sich, wie viele Arbeitskräfte
    // die Fertigung je Stunde binden würde (Umsetzungskonzept/19_...md).
    double speed = Formulas.buildingLevelSpeedFactor(level)
        * Formulas.specializationSpeedFactor((int) spec) * concFactor * blackoutFactor;
    double hoursWithBonuses = product.baseProductionHours / Math.max(speed, 0.05);
    return Formulas.productionHoursWithWorkforce(hoursWithBonuses, product.workHoursPerUnit, population);
  }

  /** Dauer OHNE Arbeitskraft-Bremse – Bezugsgröße für die Transparenz-Anzeige. */
  public static double computeProductionHoursWithoutWorkforce(GameState state, String colonyId, ProductType product, String facilityTypeId) {
    if (hasFixedBuildTime(product)) return GameConstants.COLONY_SHIP_BUILD_HOURS;
    int level = GameQueries.getBuildingLevel(state, colonyId, facilityTypeId);
    boolean isSoldier = product.id.equals("p_soldier");
    double spec = 0;
    if (!isSoldier) {
      for (Specialization s : state.specializations) {
        if (s.colonyId.equals(colonyId) && s.productTypeId.equals(product.id)) {
          spec = s.currentLevel;
          break;
        }
      }
    }
    double concFactor = 1;
    if (product.tier == 0 && !product.resourceProfile.isEmpty()) {
      Colony colony = null;
      for (Colony c : state.colonies) if (c.id.equals(colonyId)) colony = c;
      Planet planet = null;
      if (colony != null) for (Planet p : state.planets) if (p.id.equals(colony.planetId)) planet = p;
      String resId = product.resourceProfile.get(0).resourceTypeId;
      double conc = 50;
      if (planet != null) {
        for (PlanetResourceConcentration c : planet.resourceConcentration) {
          if (c.resourceTypeId.equals(resId)) {
            conc = c.concentration;
            break;
          }
        }
      }
      concFactor = Formulas.resourceConcentrationFactor(conc);
    }
    double blackoutFactor = PowerGrid.isBlackout(state, colonyId) ? Formulas.BLACKOUT_PRODUCTION_FACTOR : 1;
    double speed = Formulas.buildingLevelSpeedFactor(level)
        * Formulas.specializationSpeedFactor((int) spec) * concFactor * blackoutFactor;
    return product.baseProductionHours / Math.max(speed, 0.05);
  }

  /**
   * Löst die komplette Produktionskette für {@code quantity} Einheiten von
   * {@code productTypeId} auf: aggregiert den Bedarf über alle Rezeptebenen,
   * zieht bei jedem NICHT-Wurzel-Schritt zunächst vom aktuellen Lagerbestand
   * ab und produziert nur den Rest, rechnet für jeden zu produzierenden
   * Schritt die Produktionszeit zu den aktuellen Kolonie-Geschwindigkeits-
   * faktoren aus. Das Wurzelprodukt selbst wird NIE aus dem Lager gedeckt
   * ({@code quantity} bedeutet immer "so viele NEU bauen"). {@code feasible
   * = false} heißt: ohne "automatisch mitproduzieren" nicht ausführbar, weil
   * mindestens ein Nicht-Wurzel-Schritt einen ungedeckten Fehlbetrag hat.
   */
  public static ChainPlan planChain(GameState state, String colonyId, String productTypeId, double quantity, String facilityTypeId) {
    return planChain(state, colonyId, Map.of(productTypeId, quantity), facilityTypeId);
  }

  /**
   * Wie {@link #planChain(GameState, String, String, double, String)}, aber für MEHRERE
   * Wurzelprodukte in EINEM Auftrag (Umsetzungskonzept/28_...md-Folgefehler: eine
   * Bau-Bestellung kann mehrere Baustoffe direkt zugleich benötigen, von denen einer das
   * Vorprodukt eines anderen ist, z. B. {@code p_leitermetall} und {@code p_leiterbuendel}).
   * ALLE Schlüssel aus {@code demand} gelten als Wurzel: keiner von ihnen wird aus dem Lager
   * gedeckt, jeder wird in voller angeforderter Menge NEU gebaut – genau wie beim
   * Einzelprodukt-Fall. Braucht ein Wurzelprodukt ein anderes Wurzelprodukt als Zutat (wie
   * oben), wird dessen Bedarf einfach aufaddiert: der gemeinsame Lagerbestand wird für keines
   * der beiden angetastet, es kann also nicht mehr passieren, dass der zweite Schritt dem
   * ersten die gerade erst eingelagerte Menge wieder wegnimmt.
   */
  public static ChainPlan planChain(GameState state, String colonyId, Map<String, Double> demand, String facilityTypeId) {
    Set<String> rootIds = demand.keySet();
    Set<String> reachable = new LinkedHashSet<>();
    for (String rootId : rootIds) discover(rootId, reachable);
    List<String> orderedByTierDesc = new ArrayList<>(reachable);
    orderedByTierDesc.sort((a, b) -> ProductCatalog.find(b).tier - ProductCatalog.find(a).tier);

    Map<String, Double> totalDemand = new LinkedHashMap<>(demand);
    List<ChainPlanStep> rawSteps = new ArrayList<>();

    for (String pid : orderedByTierDesc) {
      double needed = totalDemand.getOrDefault(pid, 0.0);
      if (needed <= 0) continue;
      ProductType product = ProductCatalog.find(pid);
      boolean isRoot = rootIds.contains(pid);
      double fromWarehouse = isRoot ? 0 : Math.min(needed, Warehouse.qty(state, colonyId, pid));
      double toProduce = needed - fromWarehouse;
      if (toProduce > 0) {
        for (RecipeInput input : product.recipe) {
          totalDemand.merge(input.inputProductTypeId, input.quantity * toProduce, Double::sum);
        }
      }
      double hoursPerUnit = computeProductionHours(state, colonyId, product, facilityTypeId);
      double hours = toProduce > 0 ? toProduce * hoursPerUnit : 0;
      double unlimitedPerUnit = computeProductionHoursWithoutWorkforce(state, colonyId, product, facilityTypeId);
      ChainPlanStep step = new ChainPlanStep();
      step.productTypeId = pid;
      step.isRoot = isRoot;
      step.quantityNeeded = needed;
      step.quantityFromWarehouse = fromWarehouse;
      step.quantityToProduce = toProduce;
      step.hours = hours;
      step.workersBoundPerHour = Formulas.workersBoundPerHour(unlimitedPerUnit, product.workHoursPerUnit);
      step.workforceLimited = hoursPerUnit > unlimitedPerUnit * 1.001;
      rawSteps.add(step);
    }

    // Rohstoffe zuerst, Wurzel(n) zuletzt (für die aufklappbare Detailansicht) – bei mehreren
    // Wurzeln landet nur die tierhöchste garantiert ganz am Ende, die übrigen tragen dafür
    // step.isRoot.
    List<ChainPlanStep> steps = new ArrayList<>(rawSteps);
    java.util.Collections.reverse(steps);

    double totalHours = 0;
    double totalWorkHours = 0;
    for (ChainPlanStep s : steps) {
      totalHours += s.hours;
      totalWorkHours += ProductCatalog.find(s.productTypeId).workHoursPerUnit * s.quantityToProduce;
    }
    double workersBoundPerHour = totalHours > 0 ? totalWorkHours / totalHours : 0;

    boolean feasible = true;
    for (ChainPlanStep step : steps) {
      if (!step.isRoot && step.quantityToProduce != 0) {
        feasible = false;
        break;
      }
    }

    return new ChainPlan(totalHours, steps, feasible, totalWorkHours, workersBoundPerHour);
  }

  private static void discover(String productTypeId, Set<String> reachable) {
    if (reachable.contains(productTypeId)) return;
    reachable.add(productTypeId);
    for (RecipeInput input : ProductCatalog.find(productTypeId).recipe) {
      discover(input.inputProductTypeId, reachable);
    }
  }
}
