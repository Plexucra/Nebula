package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.ChainPlan;
import de.nebula.model.Colony;
import de.nebula.model.MarketOrder;
import de.nebula.model.MarketOrderSide;
import de.nebula.model.Population;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.Transaction;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Löhne je Arbeitsstunde und Kauforders der Bevölkerung
 * (Umsetzungskonzept/38, Teile B und C): Produktion kostet beim Start Löhne
 * ins Bevölkerungs-Wallet, ohne Guthaben startet nichts; die Bevölkerung
 * bietet aus ihrem Tagesbudget, Escrow und Erstattung laufen über ihr Wallet,
 * der Kommandant kann ihre Gebote nicht anfassen.
 */
class WagesAndBidsTest {

  private record World(GameState state, IdGenerator ids, Colony home, String playerId) {
  }

  private static World newWorld() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    Colony home = ColonyCommands.colony(state, state.players.get(0).homeworldColonyId);
    return new World(state, ids, home, state.players.get(0).id);
  }

  private static Wallet playerWallet(World w) {
    return GameQueries.findWallet(w.state(), WalletOwnerType.Player, w.playerId());
  }

  private static Wallet popWallet(World w) {
    return GameQueries.findWallet(w.state(), WalletOwnerType.Population, w.home().id);
  }

  private static Population population(World w) {
    return ColonyCommands.population(w.state(), w.home().id);
  }

  private static double sum(World w, TransactionReason reason, String fromWalletId) {
    return w.state().transactions.stream()
        .filter(t -> t.reason == reason && fromWalletId.equals(t.fromWalletId))
        .mapToDouble(t -> t.amount).sum();
  }

  // --- Löhne ------------------------------------------------------------------

  @Test
  void startingAnOrderBooksTheWagesOfThePlanIntoThePopulationWallet() {
    World w = newWorld();
    w.state().productionQueue.clear();
    double playerBefore = playerWallet(w).balance;
    double popBefore = popWallet(w).balance;
    double wagesBefore = sum(w, TransactionReason.Wage, playerWallet(w).id); // die Startaufträge haben beim Bootstrap schon Löhne gezahlt

    ProductionCommands.queueProduction(w.state(), w.ids(), w.playerId(), w.home().id, GameConstants.FOOD_PRODUCT_ID, 2000, true, false, false);
    ProductionQueueEntry entry = ProductionCommands.productionQueueFor(w.state(), w.home().id).get(0);
    assertEquals(ProductionQueueStatus.running, entry.status);
    ChainPlan plan = entry.plan;
    double expected = Formulas.wageFor(plan.totalWorkHours);
    assertEquals(expected, plan.wageCredits, 1e-9, "Der Plan trägt die Löhne des Auftrags");
    assertTrue(expected > 0);
    assertEquals(playerBefore - expected, playerWallet(w).balance, 0.01, "Löhne verlassen das Kommandanten-Wallet beim Start");
    assertEquals(popBefore + expected, popWallet(w).balance, 0.01, "…und landen im Bevölkerungs-Wallet");
    assertEquals(expected, sum(w, TransactionReason.Wage, playerWallet(w).id) - wagesBefore, 0.01, "gebucht als Lohn");
  }

  @Test
  void wagesAreEinwohnerHoursNotCatalogHours() {
    // Eine voll beschäftigte Kolonie zahlt Einwohner × Lohnsatz je Spielstunde: 90 Katalogstunden
    // sind eine Einwohner-Stunde (productionSpeedMultiplier), nicht 90.
    assertEquals(GameConstants.WAGE_PER_WORK_HOUR, Formulas.wageFor(GameConstants.PRODUCTION_SPEED_MULTIPLIER), 1e-12);
  }

  @Test
  void withoutCreditsTheOrderStopsWithCode509AndResumesOncePaid() {
    World w = newWorld();
    w.state().productionQueue.clear();
    playerWallet(w).balance = 0;
    double wagesBefore = sum(w, TransactionReason.Wage, playerWallet(w).id);

    ProductionCommands.queueProduction(w.state(), w.ids(), w.playerId(), w.home().id, GameConstants.FOOD_PRODUCT_ID, 2000, true, false, false);
    ProductionQueueEntry entry = ProductionCommands.productionQueueFor(w.state(), w.home().id).get(0);
    assertEquals(ProductionQueueStatus.stopped, entry.status, "ohne Guthaben startet der Auftrag nicht");
    assertEquals(Notifications.CODE_WAGES_UNPAID, entry.stoppedReasonCode);
    assertTrue(w.state().notifications.stream().anyMatch(n -> n.code == Notifications.CODE_WAGES_UNPAID), "Meldung an die Kolonie");
    assertEquals(wagesBefore, sum(w, TransactionReason.Wage, playerWallet(w).id), 1e-9, "nichts gebucht");

    playerWallet(w).balance = 5000;
    ProductionCommands.resumeProduction(w.state(), w.ids(), w.playerId(), w.home().id, entry.id);
    assertEquals(ProductionQueueStatus.running, entry.status, "mit Guthaben läuft er nach Fortsetzen");
    assertTrue(sum(w, TransactionReason.Wage, playerWallet(w).id) > wagesBefore);
  }

  @Test
  void theColonyDayPaysNoPerCapitaWageAnymore() {
    World w = newWorld();
    w.state().productionQueue.clear();
    double wagesBefore = sum(w, TransactionReason.Wage, playerWallet(w).id);
    Economy.colonyDay(w.state(), w.ids(), w.home().id, Clock.now());
    assertEquals(wagesBefore, sum(w, TransactionReason.Wage, playerWallet(w).id), 1e-9,
        "Ohne Aufträge und ohne Akademiker fließt kein Lohn – keine Grundsicherung");
    assertTrue(sum(w, TransactionReason.BuildingUpkeep, playerWallet(w).id) > 0, "Unterhalt läuft weiter");
  }

  // --- Gebote -----------------------------------------------------------------

  @Test
  void thePopulationBidsFromItsBudgetAndTheEscrowNeverExceedsTheWallet() {
    World w = newWorld();
    w.state().marketOrders.removeIf(o -> o.planetId != null);
    population(w).stock.clear();
    Wallet pop = popWallet(w);
    double before = pop.balance;

    Economy.refreshPopulationBids(w.state(), w.ids(), w.home());
    List<MarketOrder> bids = MarketCommands.populationBids(w.state(), w.home().id);
    assertTrue(bids.size() >= 2, "Gebote für Grundnahrung und das Wachstumsgut Grundmedizin, waren " + bids.size());
    double escrow = bids.stream().mapToDouble(o -> o.escrowedCredits).sum();
    double budget = w.state().populationDailyBudget.get(w.home().id);
    double income = w.state().populationDailyIncome.get(w.home().id);
    assertEquals(Math.min(before, income + before / GameConstants.POPULATION_STOCK_TARGET_DAYS), budget, 0.01,
        "Budget = Einkommen + Guthaben / Vorratstage, gedeckelt durch das Guthaben");
    assertTrue(escrow <= budget + 0.01, "Escrow " + escrow + " bleibt im Budget " + budget);
    assertEquals(before - escrow, pop.balance, 0.01, "das Escrow ist aus dem Bevölkerungs-Wallet gebucht");
    for (MarketOrder o : bids) {
      assertEquals(MarketOrderSide.Buy, o.side);
      assertEquals(w.playerId(), o.ownerId, "Eigentümer ist der Kommandant – Vertragsregel des Postens");
      assertEquals(w.home().id, o.populationColonyId);
      assertTrue(o.ownerName.startsWith("Bevölkerung von"));
    }

    // Erneuern zieht die alten Gebote zurück und erstattet das Escrow, bevor es neu stellt.
    Economy.refreshPopulationBids(w.state(), w.ids(), w.home());
    double escrowAfter = MarketCommands.populationBids(w.state(), w.home().id).stream().mapToDouble(o -> o.escrowedCredits).sum();
    assertEquals(before - escrowAfter, pop.balance, 0.01, "kein Geld verschwindet beim Erneuern");
  }

  @Test
  void essentialsGetTheBudgetFirstAtTheCurrentAsk() {
    World w = newWorld();
    Colony home = w.home();
    w.state().marketOrders.removeIf(o -> o.planetId != null);
    population(w).stock.clear();
    double consumptionBefore = w.state().transactions.stream().filter(t -> t.reason == TransactionReason.Consumption && t.note != null && t.note.contains(GameConstants.FOOD_PRODUCT_ID))
        .mapToDouble(t -> t.amount).sum();
    // Ein teurer Brief für Grundnahrung frisst das Budget der Grundsicherung; das Wachstumsgut bekommt nur den Rest.
    Warehouse.add(w.state(), home.id, GameConstants.FOOD_PRODUCT_ID, 500);
    MarketCommands.createSellOrderFromColony(w.state(), w.ids(), w.playerId(), home.id, GameConstants.FOOD_PRODUCT_ID, 500, 30, true);

    Economy.refreshPopulationBids(w.state(), w.ids(), home);
    MarketOrder food = null, medicine = null;
    for (MarketOrder o : MarketCommands.populationBids(w.state(), home.id)) {
      if (o.productTypeId.equals(GameConstants.FOOD_PRODUCT_ID)) food = o;
      if (o.productTypeId.equals("p_grundmedizin")) medicine = o;
    }
    // Das Nahrungsgebot lag über dem Brief (30) und ist bereits ausgeführt: der Vorrat ist voll.
    double target = Math.ceil(Economy.dailyNeed(population(w).currentCount, GameConstants.FOOD_PRODUCT_ID) * GameConstants.POPULATION_STOCK_TARGET_DAYS);
    assertEquals(target, population(w).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0), 1e-9, "Grundnahrung zuerst, zum Brief gekauft");
    assertTrue(food == null || food.remainingQuantity == 0, "das Nahrungsgebot ist bedient");
    assertTrue(medicine != null && medicine.limitPrice > 0, "für das Wachstumsgut steht ein Gebot aus dem Rest");
    double paid = w.state().transactions.stream().filter(t -> t.reason == TransactionReason.Consumption && t.note != null && t.note.contains(GameConstants.FOOD_PRODUCT_ID))
        .mapToDouble(t -> t.amount).sum() - consumptionBefore;
    assertEquals(target * 30, paid, 0.01, "ausgeführt zum Preis der ruhenden Verkaufsorder");
  }

  @Test
  void aColonyLivingFromHandToMouthToppsUpItsStockEveryDayAtTheAsk() {
    // Steady state des Kreislaufs: das Einkommen deckt genau den Tagesverbrauch, der Vorrat ist voll.
    // Die Grundsicherung muss dann JEDEN Tag die Tageslücke zum Brief nachkaufen – kein Wochen-Sägezahn.
    World w = newWorld();
    Colony home = w.home();
    w.state().productionQueue.clear();
    w.state().marketOrders.removeIf(o -> o.planetId != null);
    double ask = 0.5;
    Warehouse.add(w.state(), home.id, GameConstants.FOOD_PRODUCT_ID, 1_000_000);
    MarketCommands.createSellOrderFromColony(w.state(), w.ids(), w.playerId(), home.id, GameConstants.FOOD_PRODUCT_ID, 100_000, ask, true);
    double need = Economy.dailyNeed(population(w).currentCount, GameConstants.FOOD_PRODUCT_ID);
    double target = Math.ceil(need * GameConstants.POPULATION_STOCK_TARGET_DAYS);
    population(w).stock.clear();
    population(w).stock.put(GameConstants.FOOD_PRODUCT_ID, target);
    double dailyIncome = ask * need;
    popWallet(w).balance = dailyIncome; // ein Tag Einkommen, kein Polster
    w.state().populationDailyIncome.put(home.id, dailyIncome);
    w.state().populationInflowSinceLastDay.remove(home.id);

    for (int day = 1; day <= 10; day++) {
      Ledger.recordTx(w.state(), w.ids(), playerWallet(w).id, popWallet(w).id, dailyIncome, TransactionReason.Wage, "Tageslohn");
      Economy.refreshPopulationBids(w.state(), w.ids(), home);
      double stock = population(w).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0);
      assertTrue(stock >= target - 1, "Tag " + day + ": der Vorrat ist wieder aufgefüllt, war " + stock + " von " + target);
      Economy.consumeFromStock(w.state(), w.ids(), home);
      assertTrue(population(w).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0) >= (GameConstants.POPULATION_STOCK_TARGET_DAYS - 1) * need - 1,
          "Tag " + day + ": nach dem Verbrauch bleiben mindestens sechs Tage");
    }
  }

  @Test
  void theCommanderCannotCancelOrRepriceThePopulationBid() {
    World w = newWorld();
    MarketOrder bid = MarketCommands.populationBids(w.state(), w.home().id).get(0);
    assertThrows(CommandException.class, () -> MarketCommands.cancelOrder(w.state(), w.playerId(), bid.id));
    assertThrows(CommandException.class, () -> MarketCommands.updateOrderPrice(w.state(), w.ids(), w.playerId(), bid.id, 1));
  }

  @Test
  void sellingIntoTheBidDeliversIntoTheStockAndPaysTheCommander() {
    World w = newWorld();
    Colony home = w.home();
    w.state().marketOrders.removeIf(o -> o.planetId != null);
    population(w).stock.clear();
    Economy.refreshPopulationBids(w.state(), w.ids(), home);
    MarketOrder bid = MarketCommands.populationBids(w.state(), home.id).stream()
        .filter(o -> o.productTypeId.equals(GameConstants.FOOD_PRODUCT_ID)).findFirst().orElseThrow();
    double playerBefore = playerWallet(w).balance;
    double foodBefore = Warehouse.qty(w.state(), home.id, GameConstants.FOOD_PRODUCT_ID);
    Warehouse.add(w.state(), home.id, GameConstants.FOOD_PRODUCT_ID, 10);

    // "Verkaufen" am Gebot: eine Verkaufsorder zum Gebotspreis, die sofort kreuzt – der eigene Kommandant ist Lieferant.
    MarketCommands.createSellOrderFromColony(w.state(), w.ids(), w.playerId(), home.id, GameConstants.FOOD_PRODUCT_ID, 10, bid.limitPrice, false);
    assertEquals(10, population(w).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0), 1e-9, "Ware im Vorrat, nicht im Lager");
    assertEquals(foodBefore, Warehouse.qty(w.state(), home.id, GameConstants.FOOD_PRODUCT_ID), 1e-9);
    assertEquals(playerBefore + 10 * bid.limitPrice, playerWallet(w).balance, 0.01, "der Kommandant bekommt den Gebotspreis");
    assertEquals(bid.quantity - 10, bid.remainingQuantity, 1e-9, "das Gebot ist um die Menge kleiner");
  }

  @Test
  void aCompletedProductionMatchesTheStandingBid() {
    World w = newWorld();
    Colony home = w.home();
    // Startorder (20 Cr, Dauerorder) bleibt, der Vorrat wird geleert und die Order leer gekauft:
    population(w).stock.clear();
    for (MarketOrder o : List.copyOf(w.state().marketOrders)) {
      if (o.planetId != null && o.side == MarketOrderSide.Sell) {
        Warehouse.add(w.state(), home.id, o.productTypeId, -Warehouse.qty(w.state(), home.id, o.productTypeId));
        o.remainingQuantity = 0; // schlafend, ohne Nachschub im Lager
      }
    }
    Economy.refreshPopulationBids(w.state(), w.ids(), home);
    assertEquals(0, population(w).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0), 1e-9, "nichts kaufbar");

    // Eine Fertigstellung legt Ware ins Lager, weckt die Dauerorder und kreuzt das Gebot.
    ProductionQueueEntry entry = new ProductionQueueEntry();
    entry.id = w.ids().next("pq");
    entry.colonyId = home.id;
    entry.productTypeId = GameConstants.FOOD_PRODUCT_ID;
    entry.quantity = 100;
    entry.plan = ChainPlan.EMPTY;
    entry.status = ProductionQueueStatus.running;
    w.state().productionQueue.add(entry);
    ProductionCommands.completeProductionEntry(w.state(), w.ids(), entry);
    assertTrue(population(w).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0) > 0, "die Fertigstellung bedient das Gebot sofort");
  }

  @Test
  void thePopulationIncomeCountsEveryInflowAndFeedsTheBudget() {
    World w = newWorld();
    w.state().productionQueue.clear();
    Wallet pop = popWallet(w);
    double previous = w.state().populationDailyIncome.getOrDefault(w.home().id, 0.0);
    double counted = w.state().populationInflowSinceLastDay.getOrDefault(w.home().id, 0.0);
    Ledger.recordTx(w.state(), w.ids(), playerWallet(w).id, pop.id, 700, TransactionReason.Wage, "Testlohn");
    assertEquals(counted + 700, w.state().populationInflowSinceLastDay.get(w.home().id), 1e-9, "der Ledger zählt den Zufluss");
    Economy.refreshPopulationBids(w.state(), w.ids(), w.home());
    double income = w.state().populationDailyIncome.get(w.home().id);
    double alpha = Formulas.smoothingAlpha(GameConstants.POPULATION_INCOME_SMOOTHING_DAYS * GameConstants.GAME_DAY_HOURS, GameConstants.GAME_DAY_HOURS);
    assertEquals(previous * (1 - alpha) + (counted + 700) * alpha, income, 1e-9, "geglättet über die Einkommenstage");
    assertTrue(income > previous, "der Zufluss hebt das Einkommen");
    assertTrue(w.state().populationInflowSinceLastDay.getOrDefault(w.home().id, 0.0) == 0, "Zähler zurückgesetzt");
    Transaction last = w.state().transactions.get(w.state().transactions.size() - 1);
    assertTrue(last != null);
  }
}
