package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.model.Building;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jeder Kettenschritt läuft in der Anlage, die sein Produkt herstellt
 * (Umsetzungskonzept/31_...md, Befund 2): die Vorkette eines Werft- oder
 * Rekrutierungsauftrags mit dem Tempo des Industriekomplexes, nur die
 * Endmontage mit dem der Werft bzw. des Ausbildungszentrums. Vorher lief die
 * gesamte Kette mit dem Tempo der Wurzelanlage – ein Mannschaftstransporter
 * dauerte im Werftauftrag fünfmal so lange wie in der Vorschau.
 */
class ChainPlannerFacilityTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String colonyId) {
  }

  private static Bootstrapped newState(int industryLevel, int shipyardLevel, int academyLevel) {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    String colonyId = state.players.get(0).homeworldColonyId;
    for (Building b : state.buildings) if (b.colonyId.equals(colonyId) && b.typeId.equals("b_industry")) b.level = industryLevel;
    addBuilding(state, ids, colonyId, "b_shipyard", shipyardLevel);
    addBuilding(state, ids, colonyId, "b_academy", academyLevel);
    // Leeres Lager, damit jeder Schritt wirklich produziert werden muss.
    state.warehouse.removeIf(w -> w.colonyId.equals(colonyId));
    return new Bootstrapped(state, ids, colonyId);
  }

  private static void addBuilding(GameState state, IdGenerator ids, String colonyId, String typeId, int level) {
    Building b = new Building();
    b.id = ids.next("bld");
    b.colonyId = colonyId;
    b.typeId = typeId;
    b.level = level;
    state.buildings.add(b);
  }

  private static double hoursPerUnit(ChainPlanStep step) {
    return step.hours / step.quantityToProduce;
  }

  @Test
  void shipChainRunsPrecursorsAtIndustrySpeedAndAssemblyAtShipyardSpeed() {
    Bootstrapped b = newState(5, 1, 0);
    ChainPlan plan = ChainPlanner.planChain(b.state(), b.colonyId(), "p_trooptransport", 1, "b_shipyard");
    int precursors = 0;
    for (ChainPlanStep step : plan.steps) {
      if (step.quantityToProduce <= 0) continue;
      ProductType product = ProductCatalog.find(step.productTypeId);
      String expectedFacility = product.category == ProductCategory.Ship ? "b_shipyard" : "b_industry";
      double expected = ChainPlanner.computeProductionHours(b.state(), b.colonyId(), product, expectedFacility);
      assertEquals(expected, hoursPerUnit(step), expected * 1e-9, step.productTypeId + " läuft nicht in " + expectedFacility);
      if (product.category != ProductCategory.Ship) precursors++;
    }
    assertTrue(precursors > 50, "die Transporterkette hat Dutzende Vorprodukte, gefunden: " + precursors);

    // Gegenprobe: mit Werftstufe 5 statt 1 ändert sich NUR der Montageschritt.
    Bootstrapped fast = newState(5, 5, 0);
    ChainPlan fastPlan = ChainPlanner.planChain(fast.state(), fast.colonyId(), "p_trooptransport", 1, "b_shipyard");
    for (int i = 0; i < plan.steps.size(); i++) {
      ChainPlanStep slow = plan.steps.get(i);
      ChainPlanStep quick = fastPlan.steps.get(i);
      assertEquals(slow.productTypeId, quick.productTypeId);
      if (ProductCatalog.find(slow.productTypeId).category == ProductCategory.Ship) {
        assertEquals(slow.hours / 5, quick.hours, slow.hours * 1e-9, "Endmontage skaliert mit der Werftstufe");
      } else {
        assertEquals(slow.hours, quick.hours, slow.hours * 1e-9 + 1e-9, "Vorkette hängt nicht an der Werft: " + slow.productTypeId);
      }
    }
  }

  @Test
  void recruitmentChainRunsPrecursorsAtIndustrySpeed() {
    Bootstrapped b = newState(5, 0, 1);
    ChainPlan plan = ChainPlanner.planChain(b.state(), b.colonyId(), "p_drone_medium", 10, "b_academy");
    for (ChainPlanStep step : plan.steps) {
      if (step.quantityToProduce <= 0) continue;
      ProductType product = ProductCatalog.find(step.productTypeId);
      String expectedFacility = product.category == ProductCategory.GroundUnit ? "b_academy" : "b_industry";
      double expected = ChainPlanner.computeProductionHours(b.state(), b.colonyId(), product, expectedFacility);
      assertEquals(expected, hoursPerUnit(step), expected * 1e-9, step.productTypeId + " läuft nicht in " + expectedFacility);
    }
  }

  @Test
  void previewAndShipyardOrderAgree() {
    // Die Vorschau aus der Kolonieansicht (Anlage "b_industry") und der echte Werftauftrag
    // (Anlage "b_shipyard") müssen dieselbe Dauer nennen – das war der 5×-Unterschied aus Konzept 31 §B.
    Bootstrapped b = newState(5, 2, 0);
    ChainPlan preview = ProductionCommands.previewProductionChain(b.state(), b.colonyId(), "p_corvette", 1);
    ChainPlan order = ChainPlanner.planChain(b.state(), b.colonyId(), "p_corvette", 1, "b_shipyard");
    assertEquals(preview.totalHours, order.totalHours, preview.totalHours * 1e-9);
  }
}
