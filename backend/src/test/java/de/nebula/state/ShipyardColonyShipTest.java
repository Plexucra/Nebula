package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.ShipyardQueueEntry;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baubedingungen des Kolonisationsschiffs (Umsetzungskonzept/24_...md):
 * Mindestloyalität, 2 × Startbevölkerung, Kolonistenprämie – und eine Bauzeit,
 * die von KEINEM Tempofaktor beeinflusst wird.
 */
class ShipyardColonyShipTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId, String colonyId) {
  }

  private static Bootstrapped newStateWithShipyard() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    String playerId = state.players.get(0).id;
    String colonyId = state.players.get(0).homeworldColonyId;
    Building shipyard = new Building();
    shipyard.id = ids.next("bld");
    shipyard.colonyId = colonyId;
    shipyard.typeId = "b_shipyard";
    shipyard.level = 1;
    state.buildings.add(shipyard);
    return new Bootstrapped(state, ids, playerId, colonyId);
  }

  private static void setLoyalty(Bootstrapped b, double pct) {
    for (PlanetStats s : b.state().planetStats) if (s.colonyId.equals(b.colonyId())) s.loyaltyPct = pct;
  }

  private static Population population(Bootstrapped b) {
    return b.state().populations.stream().filter(p -> p.colonyId.equals(b.colonyId())).findFirst().orElseThrow();
  }

  private static Wallet wallet(Bootstrapped b) {
    return GameQueries.findWallet(b.state(), WalletOwnerType.Player, b.playerId());
  }

  private static void queueColonyShip(Bootstrapped b) {
    ShipyardCommands.queueShip(b.state(), b.ids(), b.playerId(), b.colonyId(),
        GameConstants.COLONY_SHIP_PRODUCT_ID, 1, false, false);
  }

  @Test
  void tooLowLoyaltyIsRejected() {
    Bootstrapped b = newStateWithShipyard();
    setLoyalty(b, GameConstants.COLONY_SHIP_MIN_LOYALTY_PCT - 1);
    population(b).currentCount = 10 * GameConstants.START_POPULATION;

    CommandException ex = assertThrows(CommandException.class, () -> queueColonyShip(b));
    assertTrue(ex.getMessage().contains("Loyalität"), ex.getMessage());
    assertTrue(b.state().shipyardQueue.isEmpty());
  }

  @Test
  void exactlyTwoStartPopulationsAreRequired() {
    Bootstrapped b = newStateWithShipyard();
    setLoyalty(b, 100);
    wallet(b).balance = 10 * ShipyardCommands.colonistPremiumPerShip();

    // Eine Einwohnerin zu wenig: abgelehnt.
    population(b).currentCount = 2 * GameConstants.START_POPULATION - 1;
    CommandException ex = assertThrows(CommandException.class, () -> queueColonyShip(b));
    assertTrue(ex.getMessage().contains("Bevölkerung"), ex.getMessage());

    // Genau 2 × Startbevölkerung: geht, und es bleibt genau eine Startbevölkerung übrig.
    population(b).currentCount = 2 * GameConstants.START_POPULATION;
    queueColonyShip(b);
    assertEquals(GameConstants.START_POPULATION, population(b).currentCount, 0.001);
  }

  @Test
  void colonistPremiumIsPaidOnQueueAndRefundedOnCancel() {
    Bootstrapped b = newStateWithShipyard();
    setLoyalty(b, 100);
    population(b).currentCount = 4 * GameConstants.START_POPULATION;
    wallet(b).balance = 10 * ShipyardCommands.colonistPremiumPerShip();
    double balanceBefore = wallet(b).balance;
    double populationBefore = population(b).currentCount;

    queueColonyShip(b);
    assertEquals(balanceBefore - ShipyardCommands.colonistPremiumPerShip(), wallet(b).balance, 0.001);
    assertEquals(populationBefore - GameConstants.START_POPULATION, population(b).currentCount, 0.001);

    ShipyardQueueEntry entry = b.state().shipyardQueue.get(0);
    ShipyardCommands.cancelShipOrder(b.state(), b.ids(), b.playerId(), b.colonyId(), entry.id);
    assertEquals(balanceBefore, wallet(b).balance, 0.001, "Prämie muss beim Abbruch zurückfließen");
    assertEquals(populationBefore, population(b).currentCount, 0.001, "Kolonisten müssen beim Abbruch zurückkehren");
  }

  @Test
  void notEnoughCreditsIsRejectedAndLeavesPopulationUntouched() {
    Bootstrapped b = newStateWithShipyard();
    setLoyalty(b, 100);
    population(b).currentCount = 4 * GameConstants.START_POPULATION;
    wallet(b).balance = ShipyardCommands.colonistPremiumPerShip() - 1;
    double populationBefore = population(b).currentCount;

    CommandException ex = assertThrows(CommandException.class, () -> queueColonyShip(b));
    assertTrue(ex.getMessage().contains("Credits"), ex.getMessage());
    assertEquals(populationBefore, population(b).currentCount, 0.001,
        "Eine abgelehnte Order darf keine Kolonisten verbrauchen");
  }

  /**
   * Die ganze Kette: Rezeptbedarf im Lager, Auftrag einreihen, Bauwoche
   * verstreichen lassen – danach liegt genau ein Kolonisationsschiff im Lager
   * und die Werft-Warteschlange ist leer.
   */
  @Test
  void aStockedShipyardActuallyProducesTheColonyShip() {
    Bootstrapped b = newStateWithShipyard();
    setLoyalty(b, 100);
    population(b).currentCount = 4 * GameConstants.START_POPULATION;
    wallet(b).balance = 10 * ShipyardCommands.colonistPremiumPerShip();
    for (var input : ProductCatalog.find(GameConstants.COLONY_SHIP_PRODUCT_ID).recipe) {
      Warehouse.add(b.state(), b.colonyId(), input.inputProductTypeId, input.quantity);
    }

    queueColonyShip(b);
    ShipyardQueueEntry entry = b.state().shipyardQueue.get(0);
    assertEquals(GameConstants.COLONY_SHIP_BUILD_HOURS,
        Clock.msToHours(entry.endsAt - entry.startedAt), 0.001,
        "Auch mit allem im Lager bleibt es bei genau einer Spielwoche");

    ShipyardCommands.processShipyardCompletions(b.state(), b.ids(), entry.endsAt);
    assertTrue(b.state().shipyardQueue.isEmpty());
    assertEquals(1, Warehouse.qty(b.state(), b.colonyId(), GameConstants.COLONY_SHIP_PRODUCT_ID), 0.001);
  }

  /**
   * "Profitiert generell nie von irgendwelchen Boni bezüglich Bauzeit" – die
   * Dauer bleibt bei jeder Werftstufe und jeder Bevölkerungsgröße identisch,
   * während ein gewöhnliches Schiff bei höherer Werftstufe schneller wird.
   */
  @Test
  void buildTimeIsFixedRegardlessOfShipyardLevelAndPopulation() {
    Bootstrapped b = newStateWithShipyard();
    var colonyShip = ProductCatalog.find(GameConstants.COLONY_SHIP_PRODUCT_ID);
    var corvette = ProductCatalog.find("p_corvette");

    double colonyShipAtLevel1 = ChainPlanner.computeProductionHours(b.state(), b.colonyId(), colonyShip, "b_shipyard");
    double corvetteAtLevel1 = ChainPlanner.computeProductionHours(b.state(), b.colonyId(), corvette, "b_shipyard");

    for (Building building : b.state().buildings) {
      if (building.colonyId.equals(b.colonyId()) && building.typeId.equals("b_shipyard")) building.level = 10;
    }
    population(b).currentCount = 100 * GameConstants.START_POPULATION;
    double colonyShipAtLevel10 = ChainPlanner.computeProductionHours(b.state(), b.colonyId(), colonyShip, "b_shipyard");
    double corvetteAtLevel10 = ChainPlanner.computeProductionHours(b.state(), b.colonyId(), corvette, "b_shipyard");

    assertEquals(GameConstants.COLONY_SHIP_BUILD_HOURS, colonyShipAtLevel1, 0.001);
    assertEquals(GameConstants.COLONY_SHIP_BUILD_HOURS, colonyShipAtLevel10, 0.001);
    assertTrue(corvetteAtLevel10 < corvetteAtLevel1,
        "Gegenprobe: ein gewöhnliches Schiff MUSS von der höheren Werftstufe profitieren");
    assertEquals(168, GameConstants.COLONY_SHIP_BUILD_HOURS, 0.001, "eine Spielwoche");
  }
}
