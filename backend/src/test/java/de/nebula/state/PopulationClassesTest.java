package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationGrowthState;
import de.nebula.model.PopulationSupply;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Güterstaffel, Arbeiter und Akademiker (Umsetzungskonzept/38, Teil D): ohne
 * das Wachstumsgut steht die Kolonie an der Stufengrenze, Akademiker entstehen
 * nur mit Forschungszentrum und versorgten Gütern, gehen bei Mangel oder ohne
 * Gehalt zurück zu den Arbeitern, und nur Arbeiter zählen als Arbeitskraft.
 */
class PopulationClassesTest {

  private record World(GameState state, IdGenerator ids, Colony home, String playerId) {
  }

  private static World newWorld() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    Colony home = ColonyCommands.colony(state, state.players.get(0).homeworldColonyId);
    // Keine Startaufträge – die Tests versorgen die Bevölkerung gezielt über Orders.
    state.productionQueue.clear();
    return new World(state, ids, home, state.players.get(0).id);
  }

  private static Population population(World w) {
    return ColonyCommands.population(w.state(), w.home().id);
  }

  private static PlanetStats stats(World w) {
    return ColonyCommands.colonyStats(w.state(), w.home().id);
  }

  private static void setLevel(World w, String typeId, int level) {
    for (Building b : w.state().buildings) {
      if (b.colonyId.equals(w.home().id) && b.typeId.equals(typeId)) {
        b.level = level;
        return;
      }
    }
    Building b = new Building();
    b.id = w.ids().next("bld");
    b.colonyId = w.home().id;
    b.typeId = typeId;
    b.level = level;
    w.state().buildings.add(b);
  }

  /** Eine unerschöpfliche, billige Dauerorder des Kommandanten für ein Gut – Versorgung ohne Produktion. */
  private static void supply(World w, String productTypeId) {
    Warehouse.add(w.state(), w.home().id, productTypeId, 1_000_000);
    MarketCommands.createSellOrderFromColony(w.state(), w.ids(), w.playerId(), w.home().id, productTypeId, 100_000, 0.5, true);
  }

  private static void days(World w, int n) {
    for (int i = 0; i < n; i++) Economy.colonyDay(w.state(), w.ids(), w.home().id, Clock.now());
  }

  private static void richOwner(World w) {
    GameQueries.findWallet(w.state(), WalletOwnerType.Player, w.playerId()).balance = 10_000_000;
  }

  @Test
  void stageOneNeedsOnlyFoodAndMedicineIsTheGrowthGood() {
    World w = newWorld();
    Economy.Demand d = Economy.demand(w.state(), w.home(), population(w));
    assertEquals(1, d.stage);
    assertEquals(List.of(GameConstants.FOOD_PRODUCT_ID), d.essentials);
    assertEquals("p_grundmedizin", d.growthGood);
    assertEquals(PopulationSupply.Group.Growth, d.group.get("p_grundmedizin"));
    assertTrue(d.academicGoods.isEmpty(), "ohne Forschungszentrum keine Akademikergüter");
  }

  @Test
  void withoutTheGrowthGoodTheColonyStopsAtTheStageCap() {
    World w = newWorld();
    richOwner(w);
    setLevel(w, GameConstants.HOUSING_BUILDING_ID, 2); // Wohnraum 40 000 – die Staffel deckelt bei 20 000
    population(w).currentCount = 20_000;
    stats(w).standardOfLivingPct = 100;
    supply(w, GameConstants.FOOD_PRODUCT_ID);
    days(w, 3);
    assertEquals(PopulationGrowthState.GoodsLimited, Economy.growthState(w.state(), w.home()),
        "ohne Grundmedizin hält die Kolonie an der Grenze der Wohnstufe 1");
    assertTrue(population(w).currentCount <= 20_000 + 1e-9, "sie wächst nicht darüber hinaus: " + population(w).currentCount);

    supply(w, "p_grundmedizin");
    days(w, 3);
    assertEquals(PopulationGrowthState.Growing, Economy.growthState(w.state(), w.home()), "mit Grundmedizin fällt der Deckel");
    assertTrue(population(w).currentCount > 20_000, "…und die Kolonie wächst in Stufe 2: " + population(w).currentCount);
    assertEquals(2, Economy.demand(w.state(), w.home(), population(w)).stage);
  }

  @Test
  void theStageCapCountsAsReachedFromTheLastPercent() {
    // Die logistische Bremse erreicht den Deckel nie exakt – ab 99 % steht die Kolonie als GoodsLimited.
    assertEquals(PopulationGrowthState.GoodsLimited, Formulas.populationGrowthState(39_700, 80_000, 40_000, 100, 1.0));
    assertEquals(PopulationGrowthState.Growing, Formulas.populationGrowthState(39_000, 80_000, 40_000, 100, 1.0));
    assertTrue(Formulas.populationGrowthDelta(39_700, 80_000, 40_000, 100, 100, 1.0) >= 0, "an der Grenze kriecht sie noch, sie schrumpft nicht");
    assertEquals(0, Formulas.populationGrowthDelta(40_000, 80_000, 40_000, 100, 100, 1.0), 1e-9, "auf dem Deckel steht sie");
  }

  @Test
  void academicsAppearOnlyWithAResearchCentreAndTheirGoodsAndCountAsResearchLevel() {
    World w = newWorld();
    richOwner(w);
    supply(w, GameConstants.FOOD_PRODUCT_ID);
    supply(w, "p_grundmedizin");
    for (String good : List.of("p_hygienewaren", "p_grundkleidung", "p_erweiterte_medizin", "p_haushaltswaren")) supply(w, good);
    days(w, 3);
    assertEquals(0, population(w).academics, 1e-9, "ohne Forschungszentrum keine Akademiker");
    assertEquals(0, Economy.researchLevel(w.state(), w.playerId()));

    setLevel(w, GameConstants.RESEARCH_BUILDING_ID, 1);
    days(w, 4);
    Population p = population(w);
    assertTrue(p.academics > 0, "mit Zentrum und versorgten Gütern entstehen Akademiker: " + p.academics);
    assertEquals(Math.rint(p.academics), p.academics, 1e-9, "Akademiker sind ganze Menschen");
    assertTrue(p.workers() + p.academics == p.currentCount, "der Wechsel ändert die Gesamtzahl nicht");
    assertEquals((int) Math.round(p.academics), Economy.researchLevel(w.state(), w.playerId()), "Forschungsniveau = Akademiker aller Kolonien");
    assertTrue(stats(w).academicStandardOfLivingPct > 50, "ihr Lebensstandard ist gemessen: " + stats(w).academicStandardOfLivingPct);

    // Forschungsgehälter: 24 Stunden je Akademiker und Tag aus dem Kommandanten-Wallet.
    Wallet owner = GameQueries.findWallet(w.state(), WalletOwnerType.Player, w.playerId());
    double before = owner.balance;
    double academics = p.academics;
    Economy.payUpkeepAndWages(w.state(), w.ids(), w.home());
    assertTrue(before - owner.balance >= academics * 24 * GameConstants.WAGE_PER_WORK_HOUR - 0.01, "Gehälter gezahlt");
  }

  @Test
  void academicsReturnToTheWorkersWhenTheirGoodsRunOutOrWagesAreNotPaid() {
    World w = newWorld();
    richOwner(w);
    setLevel(w, GameConstants.RESEARCH_BUILDING_ID, 1);
    supply(w, GameConstants.FOOD_PRODUCT_ID);
    Population p = population(w);
    p.academics = 500;
    stats(w).academicStandardOfLivingPct = 100;
    double total = p.currentCount;

    // Ihre Güter fehlen völlig: der Lebensstandard der Akademiker fällt, sie gehen zurück.
    days(w, 12);
    assertTrue(p.academics < 500, "ohne höhere Güter schwinden die Akademiker: " + p.academics);
    assertTrue(p.currentCount >= total, "der Wechsel kostet keine Einwohner – die Kolonie wächst derweil sogar");
    assertEquals(p.currentCount, p.workers() + p.academics, 1e-9);

    // Ohne Guthaben werden die verbliebenen nicht bezahlt – und gehen sofort.
    p.academics = 100;
    GameQueries.findWallet(w.state(), WalletOwnerType.Player, w.playerId()).balance = 0;
    Economy.payUpkeepAndWages(w.state(), w.ids(), w.home());
    assertEquals(0, p.academics, 1e-9, "unbezahlte Akademiker gibt es nicht");
  }

  @Test
  void onlyWorkersCountAsWorkforce() {
    World w = newWorld();
    Population p = population(w);
    var withAll = ChainPlanner.planChain(w.state(), w.home().id, GameConstants.FOOD_PRODUCT_ID, 1000, "b_industry");
    p.academics = p.currentCount - 1; // ein einziger Arbeiter
    var withOne = ChainPlanner.planChain(w.state(), w.home().id, GameConstants.FOOD_PRODUCT_ID, 1000, "b_industry");
    assertTrue(withOne.totalHours > withAll.totalHours * 10, "die Arbeitskraft-Bremse rechnet mit den Arbeitern, nicht mit allen Einwohnern");
    assertEquals(1, ColonyCommands.colonySpeedBreakdown(w.state(), w.home().id).availableWorkers, 1e-9);
  }
}
