package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.model.HubOrder;
import de.nebula.model.HubOrderSide;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifiziert das Handelsgilde-Orderbuch (Umsetzungskonzept/22_...md): Depot,
 * sofortige Ausführung (auch teilweise) beim Kreuzen von Kauf-/Verkaufs-
 * Orders, Escrow-Erstattung beim Zurückziehen und die ±10%-Nachstell-Leiter
 * des Market-Makers.
 */
class HubMarketCommandsTest {

  private static GameState newBootstrappedState() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    return state;
  }

  private static String firstHubSystemId(GameState state) {
    return state.systems.stream().filter(s -> s.isTradeHub).map(s -> s.id).findFirst().orElseThrow();
  }

  @Test
  void seedsMarketMakerOrdersForEveryEligibleProductAtEveryHub() {
    GameState state = newBootstrappedState();
    assertTrue(state.systems.stream().filter(s -> s.isTradeHub).count() >= 1);
    String hub = firstHubSystemId(state);

    List<HubOrder> orders = HubMarketCommands.ordersInSystem(state, hub);
    assertTrue(orders.stream().anyMatch(o -> o.productTypeId.equals("p_ferrometall") && o.side == HubOrderSide.Sell && o.ownerId == null));
    assertTrue(orders.stream().anyMatch(o -> o.productTypeId.equals("p_ferrometall") && o.side == HubOrderSide.Buy && o.ownerId == null));
    assertTrue(orders.stream().noneMatch(o -> o.productTypeId.equals("p_freighter")), "Schiffe dürfen nicht vom Market-Maker bepreist werden");

    HubOrder sell = orders.stream().filter(o -> o.productTypeId.equals("p_ferrometall") && o.side == HubOrderSide.Sell).findFirst().orElseThrow();
    HubOrder buy = orders.stream().filter(o -> o.productTypeId.equals("p_ferrometall") && o.side == HubOrderSide.Buy).findFirst().orElseThrow();
    assertEquals(2.0, sell.limitPrice, 0.001, "Erz: workHoursPerUnit 100 × 0,02 Cr, kein Rezept");
    assertEquals(2.4, buy.limitPrice, 0.001, "Kauf-Startpreis = Kosten × 1,2");
  }

  @Test
  void depotLoadAndUnloadRoundTrips() {
    GameState state = newBootstrappedState();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;

    HubDepot.add(state, hub, playerId, "p_ferrometall", 100);
    assertEquals(100, HubDepot.qty(state, hub, playerId, "p_ferrometall"));
    HubDepot.add(state, hub, playerId, "p_ferrometall", -40);
    assertEquals(60, HubDepot.qty(state, hub, playerId, "p_ferrometall"));
  }

  @Test
  void buyOrderMatchesRestingMarketMakerSellAtMakerPriceNotAtLimit() {
    GameState state = newBootstrappedState();
    IdGenerator ids = new IdGenerator();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    double balanceBefore = wallet.balance;

    // MM-Verkaufsorder für Erz liegt bei 2,0 Cr; Kauf-Limit deutlich darüber -> sofortige Vollausführung
    // zum Preis der RUHENDEN (Maker-)Order, nicht zum eigenen Limit.
    HubMarketCommands.createBuyOrder(state, ids, playerId, hub, "p_ferrometall", 3, 5.0);

    boolean stillResting = HubMarketCommands.ordersInSystem(state, hub).stream().anyMatch(o -> playerId.equals(o.ownerId));
    assertTrue(!stillResting, "die Kauf-Order sollte vollständig ausgeführt und daher weg sein");
    assertEquals(3, HubDepot.qty(state, hub, playerId, "p_ferrometall"));
    assertEquals(balanceBefore - 6.0, wallet.balance, 0.001, "3 Einheiten zu 2,0 Cr (Maker-Preis), nicht zu 5,0 Cr (eigenes Limit)");
  }

  @Test
  void buyOrderLargerThanMarketMakerLotPartiallyFillsAndRestsWithCorrectEscrow() {
    GameState state = newBootstrappedState();
    IdGenerator ids = new IdGenerator();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    double balanceBefore = wallet.balance;

    // MM-Lot ist 5 Einheiten; die nachgestellte Order liegt bei 2,0 × 1,1 = 2,2 Cr und kreuzt das
    // Kauf-Limit von 2,0 nicht mehr -> genau EIN Teil-Fill von 5, Rest bleibt als Order stehen.
    HubMarketCommands.createBuyOrder(state, ids, playerId, hub, "p_ferrometall", 10, 2.0);

    HubOrder resting = HubMarketCommands.ordersInSystem(state, hub).stream()
        .filter(o -> playerId.equals(o.ownerId)).findFirst().orElseThrow();
    assertEquals(5, resting.remainingQuantity, 0.001);
    assertEquals(10.0, resting.escrowedCredits, 0.001, "10 verbleibende Einheiten × 2,0 Cr Limit");
    assertEquals(5, HubDepot.qty(state, hub, playerId, "p_ferrometall"));
    assertEquals(balanceBefore - 20.0, wallet.balance, 0.001, "Gesamtes Escrow (10 × 2,0) sofort abgebucht, davon 10 für den Fill verbraucht, 10 noch gebunden");

    boolean repostedFurtherOut = HubMarketCommands.ordersInSystem(state, hub).stream()
        .anyMatch(o -> o.ownerId == null && o.side == HubOrderSide.Sell && o.productTypeId.equals("p_ferrometall")
            && Math.abs(o.limitPrice - 2.2) < 0.001);
    assertTrue(repostedFurtherOut, "Handelsgilde muss nach der Ausführung ihre Verkaufsorder 10% teurer nachstellen");

    // --- Zurückziehen erstattet exakt den Rest -------------------------------
    HubMarketCommands.cancelOrder(state, playerId, resting.id);
    assertEquals(balanceBefore - 10.0, wallet.balance, 0.001, "nur die tatsächlich ausgeführten 10 Cr bleiben abgebucht");
    assertTrue(HubMarketCommands.ordersInSystem(state, hub).stream().noneMatch(o -> playerId.equals(o.ownerId)));
  }

  @Test
  void partialFillAgainstMarketMakerOrderStillTriggersImmediateReposting() {
    GameState state = newBootstrappedState();
    IdGenerator ids = new IdGenerator();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;

    // MM-Lot ist 5 Einheiten; hier werden nur 3 gekauft – trotzdem muss die Handelsgilde-Order
    // (nicht erst bei vollständiger Ausführung) sofort verschwinden und 10% teurer neu erscheinen,
    // siehe Nutzervorgabe: "sobald ein Produkt gekauft oder verkauft wird...".
    HubMarketCommands.createBuyOrder(state, ids, playerId, hub, "p_ferrometall", 3, 5.0);

    List<HubOrder> orders = HubMarketCommands.ordersInSystem(state, hub);
    boolean oldPriceStillResting = orders.stream()
        .anyMatch(o -> o.ownerId == null && o.side == HubOrderSide.Sell && o.productTypeId.equals("p_ferrometall")
            && Math.abs(o.limitPrice - 2.0) < 0.001);
    assertTrue(!oldPriceStillResting, "die alte 2,0-Cr-Order darf nach der Teilausführung nicht mehr im Buch stehen");

    HubOrder repost = orders.stream()
        .filter(o -> o.ownerId == null && o.side == HubOrderSide.Sell && o.productTypeId.equals("p_ferrometall"))
        .findFirst().orElseThrow();
    assertEquals(2.2, repost.limitPrice, 0.001);
    assertEquals(5, repost.remainingQuantity, 0.001, "die nachgestellte Order hat wieder das volle Los, nicht den Rest der alten");
  }

  @Test
  void sellOrderMatchesRestingMarketMakerBuyAndGuildStepsBuyPriceDown() {
    GameState state = newBootstrappedState();
    IdGenerator ids = new IdGenerator();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    double balanceBefore = wallet.balance;

    HubDepot.add(state, hub, playerId, "p_ferrometall", 5);
    // MM-Kauforder liegt bei 2,4 Cr; eigenes Verkaufslimit darunter -> Ausführung zum Maker-Preis 2,4.
    HubMarketCommands.createSellOrder(state, ids, playerId, hub, "p_ferrometall", 5, 1.0);

    assertEquals(0, HubDepot.qty(state, hub, playerId, "p_ferrometall"));
    assertEquals(balanceBefore + 12.0, wallet.balance, 0.001, "5 Einheiten × 2,4 Cr Maker-Preis, nicht 1,0 Cr eigenes Limit");

    boolean steppedDown = HubMarketCommands.ordersInSystem(state, hub).stream()
        .anyMatch(o -> o.ownerId == null && o.side == HubOrderSide.Buy && o.productTypeId.equals("p_ferrometall")
            && Math.abs(o.limitPrice - 2.4 * 0.9) < 0.001);
    assertTrue(steppedDown, "Handelsgilde muss ihre Kauforder nach Ausführung 10% billiger nachstellen");
  }
}
