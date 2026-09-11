package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.MarketOrder;
import de.nebula.model.MarketOrderSide;
import de.nebula.model.TreatyOffer;
import de.nebula.model.TreatyType;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Planetare Handelsposten als Orderbuch wie an der Station
 * (Umsetzungskonzept/37): Zugang über Kolonie oder gelandete Flotte, Depot
 * für Fremde, Lager für die eigene Kolonie, Handelsvertrag im Matching, die
 * Bevölkerung kauft über ihre Gebote (Umsetzungskonzept/38) nur beim eigenen
 * Kommandanten oder bei Vertragspartnern.
 */
class PlanetaryPostTest {

  private record Arena(GameState state, IdGenerator ids, String aId, String bId, Colony home) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Anna", "Annaheim", ids), ids);
    GameStateSeeder.appendPlayer(state, WorldSeed.createAdditionalPlayerSeed(state.systems, "Bert", "Bertheim", ids), ids);
    Colony home = ColonyCommands.colony(state, state.players.get(0).homeworldColonyId);
    return new Arena(state, ids, state.players.get(0).id, state.players.get(1).id, home);
  }

  /** Berts Frachter, gelandet bei Annas Heimatwelt, mit Grundnahrung an Bord. */
  private static Fleet bertsFreighterAtAnnasHome(Arena a, double food) {
    Fleet fleet = new Fleet();
    fleet.id = a.ids().next("flt");
    fleet.ownerId = a.bId();
    fleet.name = "Berts Frachter";
    fleet.locationType = FleetLocationType.ColonyOrbit;
    fleet.locationColonyId = a.home().id;
    fleet.locationPlanetId = a.home().planetId;
    fleet.systemId = a.home().systemId;
    fleet.status = FleetStatus.Stationed;
    fleet.ships = List.of(new FleetShipGroup("p_freighter", 1));
    fleet.cargo = new ArrayList<>();
    fleet.fuelCapsules = 0;
    fleet.pendingHops = List.of();
    FleetCargo.add(fleet, GameConstants.FOOD_PRODUCT_ID, food);
    a.state().fleets.add(fleet);
    return fleet;
  }

  private static void tradeAgreement(Arena a) {
    TreatyCommands.offerTreaty(a.state(), a.ids(), a.aId(), a.bId(), TreatyType.Trade);
    TreatyOffer offer = TreatyCommands.incomingTreatyOffers(a.state(), a.bId()).get(0);
    TreatyCommands.respondToTreatyOffer(a.state(), a.ids(), a.bId(), offer.id, true);
    assertTrue(TreatyCommands.hasTradeAgreement(a.state(), a.aId(), a.bId()));
  }

  private static Wallet wallet(Arena a, String playerId) {
    return GameQueries.findWallet(a.state(), WalletOwnerType.Player, playerId);
  }

  private static List<MarketOrder> post(Arena a) {
    return MarketCommands.ordersAt(a.state(), a.home().systemId, a.home().planetId);
  }

  @Test
  void withoutColonyOrLandedFleetThereIsNoAccessToThePost() {
    Arena a = newArena();
    assertThrows(CommandException.class, () -> MarketCommands.createBuyOrder(
        a.state(), a.ids(), a.bId(), a.home().systemId, a.home().planetId, GameConstants.FOOD_PRODUCT_ID, 5, 10));
    bertsFreighterAtAnnasHome(a, 0);
    MarketCommands.createBuyOrder(a.state(), a.ids(), a.bId(), a.home().systemId, a.home().planetId, GameConstants.FOOD_PRODUCT_ID, 5, 10);
    assertTrue(post(a).stream().anyMatch(o -> a.bId().equals(o.ownerId) && o.side == MarketOrderSide.Buy), "gelandet darf Bert ordern");
  }

  @Test
  void thePopulationIgnoresForeignSellOrdersWithoutATradeAgreement() {
    Arena a = newArena();
    Fleet fleet = bertsFreighterAtAnnasHome(a, 100);
    double bertBefore = wallet(a, a.bId()).balance;
    double annaBefore = wallet(a, a.aId()).balance;

    // Annas Startorder liegt bei 20 Cr; Bert unterbietet – ohne Vertrag bleibt seine Order für die Bevölkerung unsichtbar.
    MarketCommands.createSellOrderFromFleet(a.state(), a.ids(), a.bId(), fleet.id, GameConstants.FOOD_PRODUCT_ID, 100, 10, false);
    assertEquals(0, FleetCargo.qty(fleet, GameConstants.FOOD_PRODUCT_ID), 1e-9, "Fracht ist in der Order gebunden");
    MarketOrder berts = post(a).stream().filter(o -> a.bId().equals(o.ownerId)).findFirst().orElseThrow();
    assertEquals(null, berts.sourceColonyId, "ohne Kolonie auf dem Planeten speist sich die Order aus dem Depot");

    ColonyCommands.population(a.state(), a.home().id).stock.clear();
    Economy.colonyDay(a.state(), a.ids(), a.home().id, Clock.now());
    assertEquals(bertBefore, wallet(a, a.bId()).balance, 1e-9, "ohne Handelsvertrag kauft die Bevölkerung nichts bei Bert");
    assertEquals(100, berts.remainingQuantity, 1e-9);
    assertTrue(wallet(a, a.aId()).balance > annaBefore, "die Bevölkerung kauft stattdessen die teurere Order ihres eigenen Kommandanten");
  }

  @Test
  void thePopulationBuysFromAForeignSellOrderWithATradeAgreement() {
    Arena a = newArena();
    Fleet fleet = bertsFreighterAtAnnasHome(a, 100);
    tradeAgreement(a);
    double bertBefore = wallet(a, a.bId()).balance;

    // Mit Vertrag ist Berts günstigere Order die erste Wahl der Bevölkerung.
    MarketCommands.createSellOrderFromFleet(a.state(), a.ids(), a.bId(), fleet.id, GameConstants.FOOD_PRODUCT_ID, 100, 10, false);
    MarketOrder berts = post(a).stream().filter(o -> a.bId().equals(o.ownerId)).findFirst().orElseThrow();

    ColonyCommands.population(a.state(), a.home().id).stock.clear();
    Economy.colonyDay(a.state(), a.ids(), a.home().id, Clock.now());
    assertTrue(wallet(a, a.bId()).balance > bertBefore, "die Bevölkerung hat bei Bert gekauft – mit Handelsvertrag");
    assertTrue(berts.remainingQuantity < 100);
  }

  @Test
  void buyOrdersOnlyMatchWithATradeAgreementAndDeliverToDepotOrWarehouse() {
    Arena a = newArena();
    Fleet fleet = bertsFreighterAtAnnasHome(a, 0);
    double annaBefore = wallet(a, a.aId()).balance;

    // Berts Gebot kreuzt Annas Startorder (20 Cr) preislich – ohne Vertrag bleibt es liegen.
    MarketCommands.createBuyOrder(a.state(), a.ids(), a.bId(), a.home().systemId, a.home().planetId, GameConstants.FOOD_PRODUCT_ID, 10, 80);
    MarketOrder bid = post(a).stream().filter(o -> a.bId().equals(o.ownerId)).findFirst().orElseThrow();
    assertEquals(10, bid.remainingQuantity, 1e-9, "ohne Handelsvertrag kein Matching am Posten");

    tradeAgreement(a);
    MarketCommands.matchOrders(a.state(), a.ids(), a.home().systemId, a.home().planetId, GameConstants.FOOD_PRODUCT_ID);
    assertFalse(post(a).stream().anyMatch(o -> a.bId().equals(o.ownerId)), "mit Vertrag ist das Gebot ausgeführt");
    assertEquals(10, Depot.qty(a.state(), a.home().systemId, a.home().planetId, a.bId(), GameConstants.FOOD_PRODUCT_ID), 1e-9,
        "Bert hat keine Kolonie hier – die Ware liegt in seinem Depot am Posten");
    assertEquals(annaBefore + 200, wallet(a, a.aId()).balance, 0.01, "zum Preis der ruhenden Order (20), nicht zum Limit (80)");

    // Anna kauft Berts Depotware zurück: mit Vertrag erlaubt, Lieferung ins Lager ihrer Kolonie.
    FleetCommands.loadCargoFromHubDepot(a.state(), a.bId(), fleet.id, GameConstants.FOOD_PRODUCT_ID, 10);
    MarketCommands.createSellOrderFromFleet(a.state(), a.ids(), a.bId(), fleet.id, GameConstants.FOOD_PRODUCT_ID, 10, 5, false);
    MarketOrder berts = post(a).stream().filter(o -> a.bId().equals(o.ownerId) && o.side == MarketOrderSide.Sell).findFirst().orElseThrow();
    double foodBefore = Warehouse.qty(a.state(), a.home().id, GameConstants.FOOD_PRODUCT_ID);
    MarketCommands.buyFromOrder(a.state(), a.ids(), a.aId(), berts.id, 4);
    assertEquals(foodBefore + 4, Warehouse.qty(a.state(), a.home().id, GameConstants.FOOD_PRODUCT_ID), 1e-9,
        "eigene Kolonie auf dem Planeten: Lieferung ins Lager, kein Depot");
  }

  @Test
  void cancellingReturnsGoodsToTheirSourceAndUnloadingAtAForeignColonyFillsTheDepot() {
    Arena a = newArena();
    Fleet fleet = bertsFreighterAtAnnasHome(a, 20);
    FleetCommands.unloadCargoToHubDepot(a.state(), a.bId(), fleet.id, GameConstants.FOOD_PRODUCT_ID, 20);
    assertEquals(20, Depot.qty(a.state(), a.home().systemId, a.home().planetId, a.bId(), GameConstants.FOOD_PRODUCT_ID), 1e-9);

    MarketCommands.createSellOrder(a.state(), a.ids(), a.bId(), a.home().systemId, a.home().planetId, GameConstants.FOOD_PRODUCT_ID, 20, 500, false);
    assertEquals(0, Depot.qty(a.state(), a.home().systemId, a.home().planetId, a.bId(), GameConstants.FOOD_PRODUCT_ID), 1e-9);
    MarketOrder berts = post(a).stream().filter(o -> a.bId().equals(o.ownerId)).findFirst().orElseThrow();
    MarketCommands.cancelOrder(a.state(), a.bId(), berts.id);
    assertEquals(20, Depot.qty(a.state(), a.home().systemId, a.home().planetId, a.bId(), GameConstants.FOOD_PRODUCT_ID), 1e-9, "zurück ins Depot");

    // Annas Lagerorder kehrt beim Zurückziehen ins Lager zurück.
    MarketOrder annas = post(a).stream().filter(o -> a.aId().equals(o.ownerId)).findFirst().orElseThrow();
    double foodBefore = Warehouse.qty(a.state(), a.home().id, GameConstants.FOOD_PRODUCT_ID);
    double remaining = annas.remainingQuantity;
    MarketCommands.cancelOrder(a.state(), a.aId(), annas.id);
    assertEquals(foodBefore + remaining, Warehouse.qty(a.state(), a.home().id, GameConstants.FOOD_PRODUCT_ID), 1e-9);
  }

  @Test
  void thereIsNoGuildMarketMakerAtAPost() {
    Arena a = newArena();
    assertTrue(post(a).stream().noneMatch(o -> o.ownerId == null), "Handelsgilde-Orders nur an Stationen");
    MarketCommands.createSellOrderFromColony(a.state(), a.ids(), a.aId(), a.home().id, "p_elerium_stabil", 1, 9, false);
    assertTrue(post(a).stream().noneMatch(o -> o.ownerId == null));
  }
}
