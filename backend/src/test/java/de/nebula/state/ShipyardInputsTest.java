package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.ProductType;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.RecipeInput;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Werft montiert nur, was im Lager liegt. Vorher gab es „Vorprodukte automatisch
 * mitproduzieren": der Werftauftrag fertigte die ganze Vorkette am Industriekomplex
 * vorbei – in der Produktionswarteschlange stand nichts, nichts war blockiert. Jetzt
 * lehnt {@code queueShip} ohne Vorprodukte ab, und
 * {@code queueMissingShipInputs} reiht die fehlenden Vorprodukte als EINEN
 * Bündelauftrag in die Produktion ein.
 */
class ShipyardInputsTest {

  private static final String CORVETTE = "p_corvette";

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId, String colonyId) {
  }

  private static Bootstrapped newStateWithShipyardAndIndustry() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    String playerId = state.players.get(0).id;
    String colonyId = state.players.get(0).homeworldColonyId;
    addBuilding(state, ids, colonyId, "b_shipyard", 1);
    addBuilding(state, ids, colonyId, "b_industry", 3);
    // Startaufträge räumen, damit der Bündelauftrag der einzige Eintrag ist.
    state.productionQueue.clear();
    GameQueries.findWallet(state, WalletOwnerType.Player, playerId).balance = 1_000_000;
    return new Bootstrapped(state, ids, playerId, colonyId);
  }

  private static void addBuilding(GameState state, IdGenerator ids, String colonyId, String typeId, int level) {
    Building b = new Building();
    b.id = ids.next("bld");
    b.colonyId = colonyId;
    b.typeId = typeId;
    b.level = level;
    state.buildings.add(b);
  }

  private static void stockInputs(Bootstrapped b, String shipId, double ships) {
    for (RecipeInput input : ProductCatalog.find(shipId).recipe) {
      Warehouse.add(b.state(), b.colonyId(), input.inputProductTypeId, input.quantity * ships);
    }
  }

  private static Map<String, Double> demandOf(ProductionQueueEntry entry) {
    if (entry.bundledProducts != null) return entry.bundledProducts;
    Map<String, Double> single = new LinkedHashMap<>();
    single.put(entry.productTypeId, entry.quantity);
    return single;
  }

  @Test
  void shipWithoutInputsInStockIsRejectedAndNamesEveryMissingInput() {
    Bootstrapped b = newStateWithShipyardAndIndustry();
    ProductType corvette = ProductCatalog.find(CORVETTE);
    assertFalse(corvette.recipe.isEmpty(), "Vorbedingung: die Korvette hat Vorprodukte");

    CommandException ex = assertThrows(CommandException.class, () ->
        ShipyardCommands.queueShip(b.state(), b.ids(), b.playerId(), b.colonyId(), CORVETTE, 1, false));
    assertTrue(ex.getMessage().startsWith("Fehlende Vorprodukte"), ex.getMessage());
    for (RecipeInput input : corvette.recipe) {
      assertTrue(ex.getMessage().contains(input.inputProductTypeId + " (" + (long) Math.ceil(input.quantity) + " benötigt, 0 vorhanden)"),
          "Jedes fehlende Vorprodukt muss mit Bedarf und Bestand genannt werden: " + ex.getMessage());
    }
    assertTrue(b.state().shipyardQueue.isEmpty(), "Ein abgelehnter Auftrag darf nicht in der Werft landen");
    assertTrue(b.state().productionQueue.isEmpty(), "Die Ablehnung reiht von sich aus nichts in die Produktion ein");
  }

  /**
   * Die Vorproduktprüfung steht VOR dem Abmustern der Kolonisten: Prämie und Auszug
   * haben keinen Rückweg und dürfen bei einer Ablehnung nicht gebucht sein.
   */
  @Test
  void rejectedColonyShipLeavesColonistsAndPremiumUntouched() {
    Bootstrapped b = newStateWithShipyardAndIndustry();
    for (PlanetStats s : b.state().planetStats) if (s.colonyId.equals(b.colonyId())) s.loyaltyPct = 100;
    Population population = b.state().populations.stream().filter(p -> p.colonyId.equals(b.colonyId())).findFirst().orElseThrow();
    population.currentCount = 4 * GameConstants.START_POPULATION;
    Wallet wallet = GameQueries.findWallet(b.state(), WalletOwnerType.Player, b.playerId());
    double populationBefore = population.currentCount;
    double balanceBefore = wallet.balance;

    CommandException ex = assertThrows(CommandException.class, () ->
        ShipyardCommands.queueShip(b.state(), b.ids(), b.playerId(), b.colonyId(), GameConstants.COLONY_SHIP_PRODUCT_ID, 1, false));
    assertTrue(ex.getMessage().startsWith("Fehlende Vorprodukte"), ex.getMessage());
    assertEquals(populationBefore, population.currentCount, 0.001, "Kolonisten dürfen nicht abgemustert sein");
    assertEquals(balanceBefore, wallet.balance, 0.001, "Die Prämie darf nicht gezahlt sein");
  }

  @Test
  void missingInputsAreQueuedAsOneProductionBundleAndTheShipCanBeBuiltAfterwards() {
    Bootstrapped b = newStateWithShipyardAndIndustry();
    ProductType corvette = ProductCatalog.find(CORVETTE);
    double ships = 2;
    // Ein Vorprodukt liegt zur Hälfte bereit – eingereiht werden darf nur der Rest.
    RecipeInput partly = corvette.recipe.get(0);
    double stocked = Math.floor(partly.quantity * ships / 2);
    Warehouse.add(b.state(), b.colonyId(), partly.inputProductTypeId, stocked);

    Map<String, Double> missing = ShipyardCommands.missingInputs(b.state(), b.colonyId(), CORVETTE, ships);
    assertEquals(corvette.recipe.size(), missing.size());
    assertEquals(Math.ceil(partly.quantity * ships - stocked), missing.get(partly.inputProductTypeId), 0.001);

    Map<String, Double> queued = ShipyardCommands.queueMissingShipInputs(b.state(), b.ids(), b.playerId(), b.colonyId(), CORVETTE, ships);

    assertEquals(1, b.state().productionQueue.size(), "Alle Vorprodukte müssen EIN Auftrag sein");
    ProductionQueueEntry entry = b.state().productionQueue.get(0);
    assertTrue(entry.autoProduceMissing, "Das Bündel holt seine eigenen Vorstufen selbst");
    assertFalse(entry.requeueOnComplete);
    Map<String, Double> demand = demandOf(entry);
    assertEquals(missing.keySet(), demand.keySet());
    assertEquals(queued, demand);
    for (Map.Entry<String, Double> e : missing.entrySet()) {
      assertTrue(demand.get(e.getKey()) >= e.getValue() - 1e-9,
          "Mindestens der Fehlbetrag muss bestellt sein (Mindestlos darf anheben): " + e.getKey());
    }
    assertEquals(ProductionQueueStatus.running, entry.status, "Mit Industriekomplex und Guthaben läuft der Auftrag sofort");
    assertTrue(b.state().shipyardQueue.isEmpty(), "Das Einreihen der Vorprodukte legt keinen Werftauftrag an");

    // Alles vorhanden: ohne fehlende Vorprodukte gibt es nichts einzureihen …
    stockInputs(b, CORVETTE, ships);
    CommandException nothingMissing = assertThrows(CommandException.class, () ->
        ShipyardCommands.queueMissingShipInputs(b.state(), b.ids(), b.playerId(), b.colonyId(), CORVETTE, ships));
    assertTrue(nothingMissing.getMessage().contains("alle Vorprodukte vorhanden"), nothingMissing.getMessage());

    // … und der Bau wird angenommen und läuft.
    ShipyardCommands.queueShip(b.state(), b.ids(), b.playerId(), b.colonyId(), CORVETTE, ships, false);
    assertEquals(1, b.state().shipyardQueue.size());
    assertEquals(ProductionQueueStatus.running, b.state().shipyardQueue.get(0).status);
    for (var step : b.state().shipyardQueue.get(0).plan.steps) {
      if (!step.isRoot) assertEquals(0, step.quantityToProduce, 0.001, "Die Werft fertigt keine Vorstufen mehr: " + step.productTypeId);
    }
  }
}
