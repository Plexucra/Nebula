package de.nebula.state;

import de.nebula.data.ProductCosts;
import de.nebula.data.WorldSeed;
import de.nebula.model.MarketOrder;
import de.nebula.model.MarketOrderSide;
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
class MarketCommandsTest {

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

  private static double round2(double v) {
    return Math.round(v * 100) / 100.0;
  }

  // Startpreise der Handelsgilde folgen dem Lohnsatz aus shared/game-constants.json
  // (ProductCosts = Arbeitsstunden × Lohn): hier hergeleitet statt als Zahl eingetragen,
  // damit ein geänderter Lohn die Tests nicht mehr verstimmt (11.9.2026: 0,02 → 0,0067).
  /** Kauf-Startpreis der Gilde für Ferrometallerz = Kosten × 1,2. */
  private static final double MM_BUY = round2(ProductCosts.of("p_ferrometall") * 1.2);
  /** Verkaufs-Startpreis = Kaufpreis × 1,1. */
  private static final double MM_SELL = round2(ProductCosts.of("p_ferrometall") * 1.2 * 1.1);

  @Test
  void seedsMarketMakerOrdersForEveryEligibleProductAtEveryHub() {
    GameState state = newBootstrappedState();
    assertTrue(state.systems.stream().filter(s -> s.isTradeHub).count() >= 1);
    String hub = firstHubSystemId(state);

    List<MarketOrder> orders = MarketCommands.ordersAt(state, hub, null);
    assertTrue(orders.stream().anyMatch(o -> o.productTypeId.equals("p_ferrometall") && o.side == MarketOrderSide.Sell && o.ownerId == null));
    assertTrue(orders.stream().anyMatch(o -> o.productTypeId.equals("p_ferrometall") && o.side == MarketOrderSide.Buy && o.ownerId == null));
    assertTrue(orders.stream().noneMatch(o -> o.productTypeId.equals("p_freighter")), "Schiffe dürfen nicht vom Market-Maker bepreist werden");

    MarketOrder sell = orders.stream().filter(o -> o.productTypeId.equals("p_ferrometall") && o.side == MarketOrderSide.Sell).findFirst().orElseThrow();
    MarketOrder buy = orders.stream().filter(o -> o.productTypeId.equals("p_ferrometall") && o.side == MarketOrderSide.Buy).findFirst().orElseThrow();
    assertEquals(MM_BUY, buy.limitPrice, 0.001, "Kauf-Startpreis = Kosten (Erz: workHoursPerUnit 100 × Lohn) × 1,2");
    assertEquals(MM_SELL, sell.limitPrice, 0.001, "Verkaufs-Startpreis = Kaufpreis × 1,1 – muss über dem Kaufpreis liegen, sonst risikofreie Arbitrage");
    assertTrue(sell.limitPrice > buy.limitPrice);
  }

  @Test
  void depotLoadAndUnloadRoundTrips() {
    GameState state = newBootstrappedState();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;

    Depot.add(state, hub, null, playerId, "p_ferrometall", 100);
    assertEquals(100, Depot.qty(state, hub, null, playerId, "p_ferrometall"));
    Depot.add(state, hub, null, playerId, "p_ferrometall", -40);
    assertEquals(60, Depot.qty(state, hub, null, playerId, "p_ferrometall"));
  }

  @Test
  void buyOrderMatchesRestingMarketMakerSellAtMakerPriceNotAtLimit() {
    GameState state = newBootstrappedState();
    IdGenerator ids = new IdGenerator();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    double balanceBefore = wallet.balance;

    // MM-Verkaufsorder für Erz liegt bei MM_SELL; Kauf-Limit deutlich darüber -> sofortige Vollausführung
    // zum Preis der RUHENDEN (Maker-)Order, nicht zum eigenen Limit.
    MarketCommands.createBuyOrder(state, ids, playerId, hub, null, "p_ferrometall", 3, MM_SELL * 2);

    boolean stillResting = MarketCommands.ordersAt(state, hub, null).stream().anyMatch(o -> playerId.equals(o.ownerId));
    assertTrue(!stillResting, "die Kauf-Order sollte vollständig ausgeführt und daher weg sein");
    assertEquals(3, Depot.qty(state, hub, null, playerId, "p_ferrometall"));
    assertEquals(balanceBefore - round2(3 * MM_SELL), wallet.balance, 0.001, "3 Einheiten zum Maker-Preis, nicht zum eigenen Limit");
  }

  @Test
  void buyOrderLargerThanMarketMakerLotPartiallyFillsAndRestsWithCorrectEscrow() {
    GameState state = newBootstrappedState();
    IdGenerator ids = new IdGenerator();
    String hub = firstHubSystemId(state);
    String playerId = state.players.get(0).id;
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    double balanceBefore = wallet.balance;

    // MM-Lot ist 5 Einheiten; die nachgestellte Order liegt bei MM_SELL × 1,1 und kreuzt das
    // Kauf-Limit MM_SELL nicht mehr -> genau EIN Teil-Fill von 5, Rest bleibt als Order stehen.
    MarketCommands.createBuyOrder(state, ids, playerId, hub, null, "p_ferrometall", 10, MM_SELL);

    MarketOrder resting = MarketCommands.ordersAt(state, hub, null).stream()
        .filter(o -> playerId.equals(o.ownerId)).findFirst().orElseThrow();
    assertEquals(5, resting.remainingQuantity, 0.001);
    assertEquals(round2(5 * MM_SELL), resting.escrowedCredits, 0.001, "5 verbleibende Einheiten × Limit");
    assertEquals(5, Depot.qty(state, hub, null, playerId, "p_ferrometall"));
    assertEquals(balanceBefore - round2(10 * MM_SELL), wallet.balance, 0.001, "Gesamtes Escrow (10 × Limit) sofort abgebucht, die Hälfte für den Fill verbraucht, die Hälfte noch gebunden");

    boolean repostedFurtherOut = MarketCommands.ordersAt(state, hub, null).stream()
        .anyMatch(o -> o.ownerId == null && o.side == MarketOrderSide.Sell && o.productTypeId.equals("p_ferrometall")
            && Math.abs(o.limitPrice - round2(MM_SELL * 1.1)) < 0.001);
    assertTrue(repostedFurtherOut, "Handelsgilde muss nach der Ausführung ihre Verkaufsorder 10% teurer nachstellen");

    // --- Zurückziehen erstattet exakt den Rest -------------------------------
    MarketCommands.cancelOrder(state, playerId, resting.id);
    assertEquals(balanceBefore - round2(5 * MM_SELL), wallet.balance, 0.001, "nur die tatsächlich ausgeführten 5 × Maker-Preis bleiben abgebucht");
    assertTrue(MarketCommands.ordersAt(state, hub, null).stream().noneMatch(o -> playerId.equals(o.ownerId)));
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
    MarketCommands.createBuyOrder(state, ids, playerId, hub, null, "p_ferrometall", 3, MM_SELL * 2);

    List<MarketOrder> orders = MarketCommands.ordersAt(state, hub, null);
    boolean oldPriceStillResting = orders.stream()
        .anyMatch(o -> o.ownerId == null && o.side == MarketOrderSide.Sell && o.productTypeId.equals("p_ferrometall")
            && Math.abs(o.limitPrice - MM_SELL) < 0.001);
    assertTrue(!oldPriceStillResting, "die alte Startpreis-Order darf nach der Teilausführung nicht mehr im Buch stehen");

    MarketOrder repost = orders.stream()
        .filter(o -> o.ownerId == null && o.side == MarketOrderSide.Sell && o.productTypeId.equals("p_ferrometall"))
        .findFirst().orElseThrow();
    assertEquals(round2(MM_SELL * 1.1), repost.limitPrice, 0.001);
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

    Depot.add(state, hub, null, playerId, "p_ferrometall", 5);
    // MM-Kauforder liegt bei MM_BUY; eigenes Verkaufslimit darunter -> Ausführung zum Maker-Preis.
    MarketCommands.createSellOrder(state, ids, playerId, hub, null, "p_ferrometall", 5, MM_BUY / 2, false);

    assertEquals(0, Depot.qty(state, hub, null, playerId, "p_ferrometall"));
    assertEquals(balanceBefore + round2(5 * MM_BUY), wallet.balance, 0.001, "5 Einheiten × Maker-Kaufpreis, nicht das eigene Limit");

    boolean steppedDown = MarketCommands.ordersAt(state, hub, null).stream()
        .anyMatch(o -> o.ownerId == null && o.side == MarketOrderSide.Buy && o.productTypeId.equals("p_ferrometall")
            && Math.abs(o.limitPrice - round2(MM_BUY * 0.9)) < 0.001);
    assertTrue(steppedDown, "Handelsgilde muss ihre Kauforder nach Ausführung 10% billiger nachstellen");
  }
}
