package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Population;
import de.nebula.model.WarehouseEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sichert die Kernzusage aus Umsetzungskonzept/25_...md ab: im Lager, in der
 * Bevölkerung und in den Orders bewegen sich ausschließlich GANZE Stücke, und
 * die Bruchteile leben isoliert in den Übertragskonten – ohne dass dabei die
 * langfristigen Raten verloren gehen.
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

  /** Ein Wirtschafts-Tick in derselben Reihenfolge wie {@code GameTick}. */
  private static void tick(Bootstrapped b) {
    EconomyTick.consumePowerUpkeep(b.state());
    EconomyTick.payUpkeepAndWages(b.state(), b.ids());
    EconomyTick.runConsumption(b.state(), b.ids());
    MarketCommands.replenishDormantSellOrders(b.state());
    EconomyTick.recalcCoreStats(b.state(), Clock.now());
    EconomyTick.growPopulationAndMoneySupply(b.state(), b.ids());
  }

  private static Population population(Bootstrapped b) {
    return b.state().populations.stream().filter(p -> p.colonyId.equals(b.colonyId())).findFirst().orElseThrow();
  }

  @Test
  void warehouseAndPopulationStayIntegralOverManyTicks() {
    Bootstrapped b = newWorld();
    for (int i = 0; i < 400; i++) tick(b);

    for (WarehouseEntry w : b.state().warehouse) {
      assertEquals(Math.rint(w.quantity), w.quantity, 1e-9,
          "Lagerbestand muss ganzzahlig bleiben: " + w.productTypeId + " = " + w.quantity);
    }
    double count = population(b).currentCount;
    assertEquals(Math.rint(count), count, 1e-9, "Einwohner sind ganze Menschen, war " + count);
    b.state().sellOrders.forEach(o -> assertEquals(Math.rint(o.remainingQuantity), o.remainingQuantity, 1e-9,
        "Order-Restmenge muss ganzzahlig bleiben: " + o.productTypeId + " = " + o.remainingQuantity));
  }

  /** Die Bruchteile sind vollständig auf die Töpfe isoliert – und dort immer kleiner als ein Stück. */
  @Test
  void fractionsLiveOnlyInThePotsAndStayBelowOneUnit() {
    Bootstrapped b = newWorld();
    for (int i = 0; i < 400; i++) tick(b);

    assertTrue(b.state().fractionPots.size() > 0, "Es muss überhaupt Übertragskonten geben");
    b.state().fractionPots.forEach((key, value) -> assertTrue(Math.abs(value) < 1.0,
        "Ein Topf darf nie ein ganzes Stück halten (das wäre sofort fällig): " + key + " = " + value));
  }

  /**
   * Der wichtigste Nachweis: die Stückelung verschiebt den Verbrauch nur, sie
   * verändert ihn nicht. Über viele Ticks muss der tatsächlich abgebuchte
   * Elerium-Verbrauch der Rate entsprechen – auf weniger als eine Zelle genau.
   */
  @Test
  void powerUpkeepMatchesTheRateOverManyTicks() {
    Bootstrapped b = newWorld();
    Warehouse.add(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, 1000);
    double before = Warehouse.qty(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID);

    int ticks = 2000;
    for (int i = 0; i < ticks; i++) EconomyTick.consumePowerUpkeep(b.state());

    double verbraucht = before - Warehouse.qty(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID);
    int level = GameQueries.getBuildingLevel(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_BUILDING_ID);
    double erwartet = ticks * de.nebula.engine.Formulas.infrastructureEleriumPerHour(level) * GameConstants.TICK_GAME_HOURS;

    assertEquals(erwartet, verbraucht, 1.0, "Langfristiger Verbrauch muss der Rate entsprechen");
    assertEquals(Math.rint(verbraucht), verbraucht, 1e-9, "…und ausschließlich aus ganzen Zellen bestehen");
  }

  /**
   * Gegenprobe zum Grund, warum es die Töpfe überhaupt gibt: der Kreislauf muss
   * trotz Ganzzahligkeit anlaufen. Ohne Topf würde der Bedarf von 0,16 Stück je
   * Tick beim Abschneiden dauerhaft zu null Käufen führen.
   */
  @Test
  void populationStillBuysAndLivingStandardRises() {
    Bootstrapped b = newWorld();
    double foodBefore = Warehouse.qty(b.state(), b.colonyId(), "p_grundnahrung");
    for (int i = 0; i < 200; i++) tick(b);

    long konsumBuchungen = b.state().transactions.stream()
        .filter(t -> t.reason == de.nebula.model.TransactionReason.Consumption).count();
    assertTrue(konsumBuchungen > 0, "Die Bevölkerung muss trotz Ganzzahligkeit tatsächlich einkaufen");
    assertTrue(b.state().rawStandardOfLiving.getOrDefault(b.colonyId(), 0.0) > 0,
        "Der Lebensstandard muss über 0 steigen");
    assertTrue(foodBefore >= 0);
  }
}
