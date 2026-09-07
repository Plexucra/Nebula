package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.ProductCosts;
import de.nebula.engine.Clock;
import de.nebula.model.HubOrder;
import de.nebula.model.HubOrderSide;
import de.nebula.model.Player;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;
import de.nebula.model.StarSystem;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Börsenhandel an Handelsgilde-Stationen (Umsetzungskonzept/22_...md):
 * unbegrenztes Depot je Kommandant und Station ({@link HubDepot}) plus ein
 * echtes Orderbuch aus Kauf- UND Verkaufs-Orders ({@link HubOrder}), das bei
 * jeder neuen Order sofort – auch in Teilausführung – gegen bestehende
 * Gegen-Orders matcht. Ergänzt einen synthetischen Market-Maker
 * ("Handelsgilde"), der für jede handelbare Ware an jeder Station je eine
 * kleine Kauf- und Verkaufs-Order hält, damit überhaupt Handel entsteht.
 *
 * <p><b>Bewusst getrennt von {@link MarketCommands}</b>: jenes bedient den
 * planetaren Kolonie-Handel (Depot-Orders je Kolonie, Handelsvertrag-Pflicht
 * außerhalb einer Station), hier gibt es keine Kolonie, keinen
 * Handelsvertrag-Zwang und – neu – ein echtes zweiseitiges Orderbuch statt
 * reiner Verkaufs-Orders. Beide teilen sich lediglich {@link Ledger}/
 * {@link GameQueries}.</p>
 */
public final class HubMarketCommands {
  private HubMarketCommands() {
  }

  /** Kleine, gleich große Lose je Market-Maker-Order (Nutzervorgabe: "kleine Kauf-/Verkauforders"). */
  private static final double MM_LOT = 5;
  /** Start-Kaufpreis des Market-Makers = Produktionskosten × diesen Faktor – Anreiz für Neulinge, für den Handel zu produzieren. */
  private static final double MM_BUY_MARKUP = 1.2;
  /** Verschiebung nach jeder Ausführung einer Market-Maker-Order: Verkauf +10 %, Kauf −10 %. */
  private static final double MM_STEP = 0.10;

  private static StarSystem findSystem(GameState state, String systemId) {
    for (StarSystem s : state.systems) if (s.id.equals(systemId)) return s;
    return null;
  }

  private static StarSystem requireTradeHub(GameState state, String systemId) {
    StarSystem sys = findSystem(state, systemId);
    if (sys == null || !sys.isTradeHub) {
      throw new CommandException("Kauf-/Verkaufs-Orders gibt es nur an Handelsgilde-Stationen.");
    }
    return sys;
  }

  private static double round2(double amount) {
    return Math.round(amount * 100) / 100.0;
  }

  // --- Lese-Abfragen ---------------------------------------------------------

  public static List<HubOrder> ordersInSystem(GameState state, String systemId) {
    return state.hubOrders.stream().filter(o -> o.systemId.equals(systemId) && o.remainingQuantity > 0).toList();
  }

  public static List<de.nebula.model.HubDepotEntry> hubDepotOf(GameState state, String systemId, String playerId) {
    return HubDepot.ownedBy(state, systemId, playerId);
  }

  // --- Order-Anlage ------------------------------------------------------------

  public static void createSellOrder(GameState state, IdGenerator ids, String playerId, String systemId,
                                      String productTypeId, double quantity, double pricePerUnit) {
    quantity = Math.floor(quantity);
    if (quantity < 1 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    requireTradeHub(state, systemId);
    Player player = GameQueries.requirePlayer(state, playerId);
    double have = HubDepot.qty(state, systemId, playerId, productTypeId);
    if (have < quantity) throw new CommandException("Nicht genug Bestand im Stationsdepot.");
    HubDepot.add(state, systemId, playerId, productTypeId, -quantity);

    HubOrder order = new HubOrder();
    order.id = ids.next("ho");
    order.systemId = systemId;
    order.productTypeId = productTypeId;
    order.side = HubOrderSide.Sell;
    order.ownerId = player.id;
    order.ownerName = player.name;
    order.limitPrice = pricePerUnit;
    order.quantity = quantity;
    order.remainingQuantity = quantity;
    order.escrowedCredits = 0;
    order.createdAt = Clock.now();
    order.seq = state.hubOrderSeq.incrementAndGet();
    state.hubOrders.add(order);

    ensureMarketMaker(state, ids, systemId, productTypeId);
    matchHubOrders(state, ids, systemId, productTypeId);
  }

  public static void createBuyOrder(GameState state, IdGenerator ids, String playerId, String systemId,
                                     String productTypeId, double quantity, double pricePerUnit) {
    quantity = Math.floor(quantity);
    if (quantity < 1 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    requireTradeHub(state, systemId);
    Player player = GameQueries.requirePlayer(state, playerId);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    double cost = round2(quantity * pricePerUnit);
    if (wallet == null || wallet.balance < cost) throw new CommandException("Nicht genug Credits.");
    // Sofortiges Escrow – kein Ledger-Eintrag, siehe Klassendoku bei HubOrder.escrowedCredits: eine
    // Reservierung ist kein abgeschlossenes Geschäft (analog dazu, dass eine Verkaufs-Order die Ware ohne
    // Ledger-Gegenstück aus dem Lager bucht). Erst eine tatsächliche AUSFÜHRUNG erzeugt eine Transaktion.
    wallet.balance -= cost;

    HubOrder order = new HubOrder();
    order.id = ids.next("ho");
    order.systemId = systemId;
    order.productTypeId = productTypeId;
    order.side = HubOrderSide.Buy;
    order.ownerId = player.id;
    order.ownerName = player.name;
    order.limitPrice = pricePerUnit;
    order.quantity = quantity;
    order.remainingQuantity = quantity;
    order.escrowedCredits = cost;
    order.createdAt = Clock.now();
    order.seq = state.hubOrderSeq.incrementAndGet();
    state.hubOrders.add(order);

    ensureMarketMaker(state, ids, systemId, productTypeId);
    matchHubOrders(state, ids, systemId, productTypeId);
  }

  public static void cancelOrder(GameState state, String playerId, String orderId) {
    HubOrder order = find(state, orderId);
    if (order == null) return;
    Player player = GameQueries.requirePlayer(state, playerId);
    if (order.ownerId == null || !order.ownerId.equals(player.id)) {
      throw new CommandException("Diese Order gehört einem anderen Kommandanten.");
    }
    refundAndRemove(state, order);
  }

  private static HubOrder find(GameState state, String orderId) {
    for (HubOrder o : state.hubOrders) if (o.id.equals(orderId)) return o;
    return null;
  }

  /** Erstattet den nicht ausgeführten Rest (Ware -&gt; Depot bzw. Credits -&gt; Wallet) und entfernt die Order. */
  private static void refundAndRemove(GameState state, HubOrder order) {
    if (order.ownerId != null) {
      if (order.side == HubOrderSide.Buy) {
        Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, order.ownerId);
        if (wallet != null && order.escrowedCredits > 0) wallet.balance += order.escrowedCredits;
      } else if (order.remainingQuantity > 0) {
        HubDepot.add(state, order.systemId, order.ownerId, order.productTypeId, order.remainingQuantity);
      }
    }
    // ownerId == null: Handelsgilde – kein echtes Konto/Depot, keine Erstattung (siehe Klassendoku Market-Maker).
    state.hubOrders.remove(order);
  }

  // --- Matching ----------------------------------------------------------------

  private record MatchOutcome(boolean sellRemoved, String sellOwnerId, double sellLastPrice,
                               boolean buyRemoved, String buyOwnerId, double buyLastPrice) {
  }

  /**
   * Führt SO LANGE Einzel-Ausführungen aus, bis sich Bestes-Gebot und
   * Bester-Brief nicht mehr kreuzen. Vom Market-Maker konsumierte Seiten
   * werden ERST NACH der gesamten Schleife neu eingestellt (nicht sofort je
   * Ausführung) – sonst könnte eine sehr aggressive Order die frisch
   * nachgestellte Market-Maker-Order im selben Durchlauf gleich wieder
   * treffen und eine Kaskade auslösen, die das gesamte Escrow des Käufers zu
   * immer schlechteren Preisen aufzehrt, bevor sie endet.
   */
  public static void matchHubOrders(GameState state, IdGenerator ids, String systemId, String productTypeId) {
    boolean mmSellConsumed = false;
    double mmSellLastPrice = 0;
    boolean mmBuyConsumed = false;
    double mmBuyLastPrice = 0;

    int guard = 0;
    while (true) {
      if (++guard > 10_000) break; // Notbremse – sollte laut Terminierungsargument (siehe matchOnce) nie greifen.
      MatchOutcome r = matchOnce(state, ids, systemId, productTypeId);
      if (r == null) break;
      if (r.sellRemoved() && r.sellOwnerId() == null) {
        mmSellConsumed = true;
        mmSellLastPrice = r.sellLastPrice();
      }
      if (r.buyRemoved() && r.buyOwnerId() == null) {
        mmBuyConsumed = true;
        mmBuyLastPrice = r.buyLastPrice();
      }
    }
    if (mmSellConsumed) repostMarketMaker(state, ids, systemId, productTypeId, HubOrderSide.Sell, mmSellLastPrice);
    if (mmBuyConsumed) repostMarketMaker(state, ids, systemId, productTypeId, HubOrderSide.Buy, mmBuyLastPrice);
  }

  /** Sucht das bestpreisige, zeitälteste kreuzende Gebot/Brief-Paar (Selbsthandel – auch der Handelsgilde mit sich selbst – ausgeschlossen) und führt EINE Ausführung aus. {@code null}, wenn nichts (mehr) kreuzt. */
  private static MatchOutcome matchOnce(GameState state, IdGenerator ids, String systemId, String productTypeId) {
    List<HubOrder> bids = new ArrayList<>();
    List<HubOrder> asks = new ArrayList<>();
    for (HubOrder o : state.hubOrders) {
      if (!o.systemId.equals(systemId) || !o.productTypeId.equals(productTypeId) || o.remainingQuantity <= 0) continue;
      (o.side == HubOrderSide.Buy ? bids : asks).add(o);
    }
    bids.sort(Comparator.comparingDouble((HubOrder o) -> -o.limitPrice).thenComparingLong(o -> o.seq));
    asks.sort(Comparator.comparingDouble((HubOrder o) -> o.limitPrice).thenComparingLong(o -> o.seq));

    for (HubOrder bid : bids) {
      for (HubOrder ask : asks) {
        if (bid.limitPrice + 1e-9 < ask.limitPrice) break; // asks aufsteigend sortiert: ab hier kreuzt nichts mehr für DIESES Gebot
        if (Objects.equals(bid.ownerId, ask.ownerId)) continue; // eigene Gegenposition (auch Handelsgilde vs. Handelsgilde) überspringen
        return execute(state, ids, bid, ask);
      }
    }
    return null;
  }

  /** Ausführungspreis = Preis der RUHENDEREN (älteren) Order der beiden – klassische Maker-nimmt-seinen-Preis-Regel. */
  private static MatchOutcome execute(GameState state, IdGenerator ids, HubOrder bid, HubOrder ask) {
    HubOrder maker = bid.seq < ask.seq ? bid : ask;
    double execPrice = maker.limitPrice;
    double qty = Math.min(bid.remainingQuantity, ask.remainingQuantity);
    double cost = round2(qty * execPrice);
    if (bid.ownerId != null && cost > bid.escrowedCredits) {
      // Rundungsdrift durch vorherige Teilausführungen: auf das tatsächlich noch gebundene Escrow klemmen.
      qty = Math.floor(bid.escrowedCredits / execPrice);
      cost = round2(qty * execPrice);
    }
    if (qty < 1) {
      // Entartete Restmenge (Escrow durch Rundung knapp aufgebraucht) – Gebot gilt als erschöpft, verhindert Endlosschleife.
      String buyOwner = bid.ownerId;
      refundAndRemove(state, bid);
      return new MatchOutcome(false, null, 0, true, buyOwner, execPrice);
    }

    ask.remainingQuantity -= qty;
    if (bid.ownerId != null) HubDepot.add(state, bid.systemId, bid.ownerId, bid.productTypeId, qty);
    // ask.ownerId == null (Handelsgilde verkauft): Ware wird konjuriert, kein Depot-Abbuchen nötig.

    bid.escrowedCredits = Math.max(0, bid.escrowedCredits - cost);
    bid.remainingQuantity -= qty;

    // NUR den Verkäufer gutschreiben: der Käufer wurde bereits beim Einstellen der Order ins Escrow
    // belastet (siehe createBuyOrder), nicht jetzt bei der Ausführung – sonst würde derselbe Betrag
    // zweimal vom Wallet abgezogen (einmal ins Escrow, einmal hier über den Ledger).
    String toWalletId = ask.ownerId != null ? walletId(state, ask.ownerId) : null;
    Ledger.recordTx(state, ids, null, toWalletId, cost, TransactionReason.Trade,
        "Handelsgilde: " + (long) qty + "× " + bid.productTypeId + " zu " + execPrice + " Cr");

    boolean sellRemoved = false;
    double sellLastPrice = ask.limitPrice;
    boolean buyRemoved = false;
    double buyLastPrice = bid.limitPrice;
    String sellOwnerId = ask.ownerId;
    String buyOwnerId = bid.ownerId;
    // Eine Handelsgilde-Order wird bei JEDER Ausführung entfernt, auch bei einer Teilausführung
    // (Nutzervorgabe: "sobald ein Produkt gekauft oder verkauft wird, stellt er erneut eine Order
    // ein" – nicht erst, wenn das ganze Los weg ist). Ein etwaiger Rest verfällt (siehe Klassendoku
    // Market-Maker: kein echtes Depot/Wallet, also nichts, das dabei "verloren" ginge).
    if (ask.ownerId == null || ask.remainingQuantity < 1e-9) {
      state.hubOrders.remove(ask);
      sellRemoved = true;
    }
    if (bid.ownerId == null || bid.remainingQuantity < 1e-9 || bid.escrowedCredits < 0.005) {
      refundAndRemove(state, bid);
      buyRemoved = true;
    }
    return new MatchOutcome(sellRemoved, sellOwnerId, sellLastPrice, buyRemoved, buyOwnerId, buyLastPrice);
  }

  private static String walletId(GameState state, String playerId) {
    Wallet w = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    return w != null ? w.id : null;
  }

  // --- Market-Maker ("Handelsgilde") --------------------------------------------

  /**
   * Stellt sicher, dass beide Seiten (Kauf UND Verkauf) einer Ware an einer
   * Station eine ruhende Handelsgilde-Order haben. Wird defensiv bei jeder
   * Order-Anlage aufgerufen (billige Existenzprüfung); die eigentliche
   * Erstbefüllung für die GESAMTE Galaxie passiert einmalig und vollständig
   * in {@link #seedAllMarketMakers}, damit an einer frisch besuchten Station
   * sofort ein volles Orderbuch steht statt erst nach dem ersten Spieler-Klick.
   */
  public static void ensureMarketMaker(GameState state, IdGenerator ids, String systemId, String productTypeId) {
    if (!isMarketMakerEligible(productTypeId)) return;
    boolean hasSell = false, hasBuy = false;
    for (HubOrder o : state.hubOrders) {
      if (o.ownerId != null || !o.systemId.equals(systemId) || !o.productTypeId.equals(productTypeId)) continue;
      if (o.side == HubOrderSide.Sell) hasSell = true; else hasBuy = true;
    }
    double cost = ProductCosts.of(productTypeId);
    if (!hasSell) postMarketMaker(state, ids, systemId, productTypeId, HubOrderSide.Sell, cost);
    if (!hasBuy) postMarketMaker(state, ids, systemId, productTypeId, HubOrderSide.Buy, cost * MM_BUY_MARKUP);
  }

  private static boolean isMarketMakerEligible(String productTypeId) {
    ProductType product = ProductCatalog.find(productTypeId);
    // Schiffe/Bodeneinheiten entstehen nur in Werft/Ausbildungszentrum und werden nie Lagerware
    // (ProductionCommands.queueProductionCore) – gekaufte Stück wären tote Depot-Einträge.
    return product.category != ProductCategory.Ship && product.category != ProductCategory.GroundUnit;
  }

  private static void repostMarketMaker(GameState state, IdGenerator ids, String systemId, String productTypeId,
                                         HubOrderSide side, double lastPrice) {
    double newPrice = side == HubOrderSide.Sell ? lastPrice * (1 + MM_STEP) : lastPrice * (1 - MM_STEP);
    postMarketMaker(state, ids, systemId, productTypeId, side, newPrice);
  }

  private static void postMarketMaker(GameState state, IdGenerator ids, String systemId, String productTypeId,
                                       HubOrderSide side, double price) {
    HubOrder order = new HubOrder();
    order.id = ids.next("ho");
    order.systemId = systemId;
    order.productTypeId = productTypeId;
    order.side = side;
    order.ownerId = null;
    order.ownerName = "Handelsgilde";
    order.limitPrice = round2(Math.max(price, 0.01));
    order.quantity = MM_LOT;
    order.remainingQuantity = MM_LOT;
    order.escrowedCredits = 0; // ungenutzt für Handelsgilde-Orders, siehe execute(): der Escrow-Klemme-Zweig greift nur bei ownerId != null.
    order.createdAt = Clock.now();
    order.seq = state.hubOrderSeq.incrementAndGet();
    state.hubOrders.add(order);
  }

  /**
   * Einmalige Erstbefüllung der GESAMTEN Galaxie beim allerersten
   * {@code GameStateSeeder.bootstrap}: für jede Handelsgilde-Station und
   * jede handelbare Ware (alle außer Schiffe/Bodeneinheiten) je eine
   * Kauf- und Verkaufs-Order der Handelsgilde. Absichtlich NICHT lazy beim
   * ersten Betreten einer Station – sonst müsste jede Sekunden-Abfrage der
   * Orderbuch-Seite (Client-Polling) 177 Waren auf Vollständigkeit prüfen.
   */
  public static void seedAllMarketMakers(GameState state, IdGenerator ids) {
    for (StarSystem sys : state.systems) {
      if (!sys.isTradeHub) continue;
      for (ProductType product : ProductCatalog.CATALOG) {
        if (!isMarketMakerEligible(product.id)) continue;
        ensureMarketMaker(state, ids, sys.id, product.id);
      }
    }
  }
}
