package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colony;
import de.nebula.model.Population;
import de.nebula.model.WarehouseEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sichert die Kernzusage aus Umsetzungskonzept/25_...md ab: im Lager, in der
 * Bevölkerung, im Vorrat und in den Orders bewegen sich ausschließlich GANZE
 * Stücke, und die Bruchteile leben isoliert in den Übertragskonten – ohne
 * dass dabei die langfristigen Raten verloren gehen.
 */
class IntegerQuantitiesTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String colonyId) {
  }

  private static Bootstrapped newWorld() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    return new Bootstrapped(state, ids, state.players.get(0).homeworldColonyId);
  }

  /** Ein Kolonietag der Heimatwelt (Umsetzungskonzept/36). */
  private static void day(Bootstrapped b) {
    Economy.colonyDay(b.state(), b.ids(), b.colonyId(), Clock.now());
  }

  private static Population population(Bootstrapped b) {
    return b.state().populations.stream().filter(p -> p.colonyId.equals(b.colonyId())).findFirst().orElseThrow();
  }

  @Test
  void warehousePopulationAndStockStayIntegralOverManyDays() {
    Bootstrapped b = newWorld();
    for (int i = 0; i < 400; i++) day(b);

    for (WarehouseEntry w : b.state().warehouse) {
      assertEquals(Math.rint(w.quantity), w.quantity, 1e-9,
          "Lagerbestand muss ganzzahlig bleiben: " + w.productTypeId + " = " + w.quantity);
    }
    double count = population(b).currentCount;
    assertEquals(Math.rint(count), count, 1e-9, "Einwohner sind ganze Menschen, war " + count);
    population(b).stock.forEach((good, qty) -> assertEquals(Math.rint(qty), qty, 1e-9,
        "Der Vorrat der Bevölkerung muss ganzzahlig bleiben: " + good + " = " + qty));
    b.state().sellOrders.forEach(o -> assertEquals(Math.rint(o.remainingQuantity), o.remainingQuantity, 1e-9,
        "Order-Restmenge muss ganzzahlig bleiben: " + o.productTypeId + " = " + o.remainingQuantity));
  }

  /** Die Bruchteile sind vollständig auf die Töpfe isoliert – und dort immer kleiner als ein Stück. */
  @Test
  void fractionsLiveOnlyInThePotsAndStayBelowOneUnit() {
    Bootstrapped b = newWorld();
    for (int i = 0; i < 400; i++) day(b);

    assertTrue(b.state().fractionPots.size() > 0, "Es muss überhaupt Übertragskonten geben");
    b.state().fractionPots.forEach((key, value) -> assertTrue(Math.abs(value) < 1.0,
        "Ein Topf darf nie ein ganzes Stück halten (das wäre sofort fällig): " + key + " = " + value));
  }

  /**
   * Der wichtigste Nachweis: die Stückelung verschiebt den Verbrauch nur, sie
   * verändert ihn nicht. Über viele Tage muss der tatsächlich abgebuchte
   * Elerium-Verbrauch der Rate entsprechen – auf weniger als eine Zelle genau.
   */
  @Test
  void powerUpkeepMatchesTheRateOverManyDays() {
    Bootstrapped b = newWorld();
    Warehouse.add(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, 1000);
    // Seit dem Energiespeicher (Umsetzungskonzept/32) liegt ein Teil davon im Speicher und wird
    // zuerst von dort verbraucht – gemessen wird deshalb Speicher plus Lager.
    double before = EnergyStorageCommands.totalFuel(b.state(), b.colonyId());

    int days = 200;
    for (int i = 0; i < days; i++) Economy.consumePower(b.state(), b.colonyId());

    double verbraucht = before - EnergyStorageCommands.totalFuel(b.state(), b.colonyId());
    int level = GameQueries.getBuildingLevel(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_BUILDING_ID);
    double erwartet = days * de.nebula.engine.Formulas.infrastructureEleriumPerHour(level) * GameConstants.GAME_DAY_HOURS;

    assertEquals(erwartet, verbraucht, 1.0, "Langfristiger Verbrauch muss der Rate entsprechen");
    assertEquals(Math.rint(verbraucht), verbraucht, 1e-9, "…und ausschließlich aus ganzen Zellen bestehen");
  }

  /**
   * Gegenprobe zum Grund, warum es die Töpfe überhaupt gibt: der Kreislauf muss
   * trotz Ganzzahligkeit anlaufen – die Bevölkerung kauft schon bei der
   * Gründung ein, isst am ersten Kolonietag aus dem Vorrat, und der
   * Lebensstandard folgt der Versorgung.
   */
  @Test
  void populationBuysAtFoundingAndEatsFromStock() {
    Bootstrapped b = newWorld();
    Population population = population(b);
    double target = Math.ceil(Economy.dailyNeed(population.currentCount, GameConstants.FOOD_PRODUCT_ID)
        * GameConstants.POPULATION_STOCK_TARGET_DAYS);
    assertEquals(target, population.stock.get(GameConstants.FOOD_PRODUCT_ID), 1e-9,
        "Der Gründungseinkauf füllt den Nahrungsvorrat auf das Ziel");
    long konsumBuchungen = b.state().transactions.stream()
        .filter(t -> t.reason == de.nebula.model.TransactionReason.Consumption).count();
    assertTrue(konsumBuchungen > 0, "Die Bevölkerung muss trotz Ganzzahligkeit tatsächlich einkaufen");

    day(b);
    // Der Tag füllt erst auf und isst dann: danach fehlt genau ein Tagesbedarf.
    double food = population(b).stock.get(GameConstants.FOOD_PRODUCT_ID);
    double dailyNeed = Economy.dailyNeed(population(b).currentCount, GameConstants.FOOD_PRODUCT_ID);
    assertTrue(food >= target - Math.ceil(dailyNeed) && food < target, "Nach dem Tag fehlt ein Tagesbedarf, war " + food);
    assertTrue(b.state().rawStandardOfLiving.getOrDefault(b.colonyId(), 0.0) > 0,
        "Der Lebensstandard muss über 0 liegen");
    double foodCoverage = ColonyCommands.foodCoverage(b.state(), b.colonyId());
    assertTrue(foodCoverage >= 1.0, "Gedeckter Tag mit Vorrat: Deckung mindestens 1, war " + foodCoverage);
  }

  /**
   * Ohne Order am eigenen Handelsposten lebt die Kolonie vom Vorrat. Der ist
   * auf das Vorratsziel bemessen, trägt aber kürzer, weil eine wachsende
   * Kolonie jeden Tag mehr isst – genau der Fall, für den der Puffer da ist.
   */
  @Test
  void withoutOrdersTheStockCarriesTheColonyForDays() {
    Bootstrapped b = newWorld();
    b.state().sellOrders.clear();
    double eatenTotal = 0;
    int fedDays = 0;
    for (int i = 0; i < GameConstants.POPULATION_STOCK_TARGET_DAYS + 3; i++) {
      double before = population(b).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0);
      day(b);
      eatenTotal += before - population(b).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0);
      if (ColonyCommands.foodCoverage(b.state(), b.colonyId()) >= 1.0) fedDays++;
    }
    double target = Math.ceil(Economy.dailyNeed(GameConstants.START_POPULATION, GameConstants.FOOD_PRODUCT_ID)
        * GameConstants.POPULATION_STOCK_TARGET_DAYS);
    assertEquals(target, eatenTotal, 1e-9, "Der ganze Vorrat wird gegessen, nichts geht verloren");
    assertTrue(fedDays >= 4 && fedDays < GameConstants.POPULATION_STOCK_TARGET_DAYS + 1,
        "Der Vorrat trägt mehrere Tage, bei wachsender Kolonie weniger als das Ziel: " + fedDays);
    assertTrue(ColonyCommands.foodCoverage(b.state(), b.colonyId()) < 1.0, "Danach ist die Deckung weg");
  }

  /** Notkauf: fällt der Vorrat unter einen Tag und eine Order erscheint, kauft die Bevölkerung sofort. */
  @Test
  void aNewOrderTriggersAnEmergencyPurchaseWhenTheStockIsLow() {
    Bootstrapped b = newWorld();
    Colony home = ColonyCommands.colony(b.state(), b.colonyId());
    b.state().sellOrders.clear();
    population(b).stock.clear();
    Warehouse.add(b.state(), b.colonyId(), GameConstants.FOOD_PRODUCT_ID, 500);
    MarketCommands.createSellOrder(b.state(), b.ids(), home.ownerId, b.colonyId(), GameConstants.FOOD_PRODUCT_ID, 200, 60, true);
    double food = population(b).stock.getOrDefault(GameConstants.FOOD_PRODUCT_ID, 0.0);
    assertTrue(food > 0, "Die Order am eigenen Posten löst den Notkauf aus, Vorrat war " + food);
  }
}
