package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.SellOrder;
import de.nebula.model.StarSystem;
import de.nebula.model.TradeLocationType;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.List;

/**
 * 1:1-Portierung der "Handel"-Sektion aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 6/10 – vorgezogen, weil Bevölkerungs-
 * Konsum aktiv am Markt kauft, siehe Migrationsplan-Fortschrittsnotiz).
 *
 * <p>{@link #createSellOrderFromFleet} ist seit Portierung des Flottensystems
 * (Phase 7) enthalten – der Flotten-Zweig in {@link #settleSellOrderPurchase}/
 * {@link #reserveForRelist}/{@link #cancelSellOrder} war schon vorher 1:1
 * mitportiert, wird ab jetzt tatsächlich erreichbar.</p>
 *
 * <p><b>Eingeschränkter planetarer Handel</b> (Umsetzungskonzept/21_...md):
 * außerhalb einer neutralen Handelsgilde-Station ({@code StarSystem.isTradeHub})
 * ist Systemhandel (Orders am Systemhandelsposten ohne Kolonie-Landung) nicht
 * mehr möglich – nur noch der Planetare Handelsposten einer konkreten Kolonie.
 * Ein tatsächlicher Kauf/Verkauf dort erfordert zusätzlich einen gültigen
 * Handelsvertrag ({@link TreatyCommands#hasTradeAgreement}) zwischen den
 * beiden beteiligten Kommandanten. An einer Handelsgilde-Station gilt keine
 * dieser Einschränkungen – dort kann jeder mit jedem handeln.</p>
 */
public final class MarketCommands {
  private MarketCommands() {
  }

  private static StarSystem findSystem(GameState state, String systemId) {
    for (StarSystem s : state.systems) if (s.id.equals(systemId)) return s;
    return null;
  }

  /** Wirft, sofern {@code systemId} KEINE Handelsgilde-Station ist und zwischen den beiden Parteien kein Handelsvertrag besteht. */
  private static void requireTradePermission(GameState state, String systemId, String playerAId, String playerBId) {
    if (playerAId.equals(playerBId)) return;
    StarSystem sys = findSystem(state, systemId);
    if (sys != null && sys.isTradeHub) return;
    if (!TreatyCommands.hasTradeAgreement(state, playerAId, playerBId)) {
      throw new CommandException("Planetarer Handel ist nur mit Kommandanten möglich, mit denen ein Handelsvertrag besteht.");
    }
  }

  public static List<SellOrder> sellOrdersInSystem(GameState state, String systemId) {
    return state.sellOrders.stream().filter(o -> o.systemId.equals(systemId) && o.remainingQuantity > 0).toList();
  }

  public static void createSellOrder(GameState state, IdGenerator ids, String playerId, String colonyId,
                                      String productTypeId, double quantity, double pricePerUnit, boolean autoRelist) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    // Mechanik/05_...md §12: keine NEUEN Handelsaktionen des Spielers an einer
    // Kolonie im Bodengefecht. Bestehende Orders laufen bewusst weiter (Ware
    // und Geld sind bereits gebunden), und die Bevölkerung darf weiter kaufen –
    // deshalb steht die Sperre hier und nicht in createSellOrderCore.
    if (GroundBattleCommands.isUnderGroundAttack(state, colonyId)) {
      throw new CommandException("Während eines laufenden Bodengefechts nimmt diese Kolonie keine neuen Handelsaufträge an.");
    }
    createSellOrderCore(state, ids, colonyId, productTypeId, quantity, pricePerUnit, autoRelist);
  }

  /** Ungeprüfter Kern von {@link #createSellOrder} – für eine künftige NPC-KI gedacht (siehe TS-Original). */
  public static void createSellOrderCore(GameState state, IdGenerator ids, String colonyId, String productTypeId,
                                          double quantity, double pricePerUnit, boolean autoRelist) {
    if (quantity <= 0 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) throw new CommandException("Unbekannte Kolonie.");
    double stock = Warehouse.qty(state, colonyId, productTypeId);
    if (stock < quantity) throw new CommandException("Nicht genug Lagerbestand für diese Order.");
    Warehouse.add(state, colonyId, productTypeId, -quantity);

    SellOrder order = new SellOrder();
    order.id = ids.next("so");
    order.systemId = colony.systemId;
    order.locationType = TradeLocationType.Depot;
    order.depotColonyId = colonyId;
    order.sellerId = colony.ownerId;
    order.sellerName = GameQueries.ownerDisplayName(state, colony.ownerId);
    order.productTypeId = productTypeId;
    order.quantity = quantity;
    order.remainingQuantity = quantity;
    order.pricePerUnit = pricePerUnit;
    order.createdAt = Clock.now();
    order.autoRelist = autoRelist;
    order.sourceFleetId = null;
    state.sellOrders.add(order);
  }

  /**
   * Verkauf direkt aus der Fracht einer eigenen, gerade dort befindlichen
   * Flotte: gelandet ({@code locationColonyId} gesetzt) entsteht eine
   * {@code Depot}-Order an DIESER Kolonie – auch wenn sie einem anderen
   * Kommandanten gehört, denn hier verkauft die Flotte selbst, nicht die
   * Kolonie. Ohne Landung entsteht eine {@code Station}-Order am
   * Systemhandelsposten.
   */
  public static void createSellOrderFromFleet(GameState state, IdGenerator ids, String playerId, String fleetId,
                                               String productTypeId, double quantity, double pricePerUnit, boolean autoRelist) {
    if (quantity <= 0 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    Fleet fleet = FleetCommands.requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != de.nebula.model.FleetStatus.Stationed) throw new CommandException("Die Flotte ist unterwegs.");
    double have = FleetCargo.qty(fleet, productTypeId);
    if (have < quantity) throw new CommandException("Nicht genug Fracht an Bord.");
    var player = GameQueries.requirePlayer(state, playerId);
    if (fleet.locationColonyId == null) {
      StarSystem sys = findSystem(state, fleet.systemId);
      if (sys != null && sys.isTradeHub) {
        // Seit Umsetzungskonzept/22_...md abgelöst durch das Depot-basierte Orderbuch der
        // Handelsgilde-Station (HubMarketCommands) – dieser Zweig war die einzige bisherige Nutzung
        // von "ohne Landung verkaufen" (siehe Klassendoku) und bleibt nur als sprechende Fehlermeldung stehen.
        throw new CommandException("An einer Handelsgilde-Station läuft der Handel jetzt über das Depot: zuerst Fracht ins Depot entladen, dann auf der Handel-Seite eine Order aufgeben.");
      }
      throw new CommandException("Handel am Systemhandelsposten ist nur noch an neutralen Handelsgilde-Stationen möglich – bitte bei einer Kolonie landen.");
    } else {
      Colony depotColony = ColonyCommands.colony(state, fleet.locationColonyId);
      if (depotColony != null) requireTradePermission(state, fleet.systemId, player.id, depotColony.ownerId);
    }
    FleetCargo.add(fleet, productTypeId, -quantity);

    SellOrder order = new SellOrder();
    order.id = ids.next("so");
    order.systemId = fleet.systemId;
    order.locationType = fleet.locationColonyId != null ? TradeLocationType.Depot : TradeLocationType.Station;
    order.depotColonyId = fleet.locationColonyId;
    order.sellerId = player.id;
    order.sellerName = player.name;
    order.productTypeId = productTypeId;
    order.quantity = quantity;
    order.remainingQuantity = quantity;
    order.pricePerUnit = pricePerUnit;
    order.createdAt = Clock.now();
    order.autoRelist = autoRelist;
    order.sourceFleetId = fleetId;
    state.sellOrders.add(order);
  }

  public static void cancelSellOrder(GameState state, String playerId, String orderId) {
    SellOrder order = find(state, orderId);
    if (order == null) return;
    var player = GameQueries.requirePlayer(state, playerId);
    if (!order.sellerId.equals(player.id)) throw new CommandException("Diese Order gehört einem anderen Kommandanten.");
    if (order.remainingQuantity > 0) {
      // Aus Flottenfracht entstandene Orders erstatten in die Fracht zurück (falls die Flotte noch
      // existiert – die Ware lag nie in einem Kolonielager); alle anderen ins Kolonielager, in dem sie
      // ursprünglich lagen.
      Fleet sourceFleet = order.sourceFleetId != null ? findFleet(state, order.sourceFleetId) : null;
      if (sourceFleet != null) {
        FleetCargo.add(sourceFleet, order.productTypeId, order.remainingQuantity);
      } else if (order.depotColonyId != null) {
        Warehouse.add(state, order.depotColonyId, order.productTypeId, order.remainingQuantity);
      } else {
        // Weder Flotte noch Kolonie als Erstattungsziel: bislang unerreichbar (jede Order hatte bisher
        // eines von beiden), soll aber NICHT still die Ware verschwinden lassen, falls sich das ändert.
        throw new IllegalStateException("Order " + order.id + " hat kein Erstattungsziel (weder Flotte noch Kolonie).");
      }
    }
    state.sellOrders.remove(order);
  }

  public static void buyFromOrder(GameState state, IdGenerator ids, String playerId, String orderId, double quantity, String deliverToColonyId) {
    // Lieferziel muss eine eigene Kolonie sein, sonst könnte man Ware in eine fremde Kolonie "spenden".
    GameQueries.requireOwnColony(state, playerId, deliverToColonyId);
    SellOrder order = find(state, orderId);
    if (order == null || order.remainingQuantity < quantity) throw new CommandException("Nicht genug Ware in dieser Order verfügbar.");
    var player = GameQueries.requirePlayer(state, playerId);
    requireTradePermission(state, order.systemId, player.id, order.sellerId);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    double cost = Math.round(quantity * order.pricePerUnit);
    if (wallet == null || wallet.balance < cost) throw new CommandException("Nicht genug Credits.");
    Wallet sellerWallet = GameQueries.findWallet(state, WalletOwnerType.Player, order.sellerId);
    settleSellOrderPurchase(state, ids, order, quantity);
    Warehouse.add(state, deliverToColonyId, order.productTypeId, quantity);
    if (sellerWallet != null) {
      Ledger.recordTx(state, ids, wallet.id, sellerWallet.id, cost, TransactionReason.Trade, "Kauf " + (long) quantity + "× am Markt");
    }
  }

  /**
   * Zieht {@code quantity} von einer Verkaufsorder ab. Erreicht
   * {@code remainingQuantity} dadurch 0 und ist {@code autoRelist} gesetzt,
   * wird SOFORT versucht, mit dem gerade jetzt vorhandenen Bestand neu
   * aufzulegen ({@link #reserveForRelist}). Schlägt das fehl: Kolonie-Orders
   * bleiben "schlafend" (remainingQuantity 0, siehe {@link #replenishDormantSellOrders}),
   * Flotten-Orders werden gelöscht (siehe Klassen-Javadoc). Gemeinsam
   * genutzt von {@link #buyFromOrder} und der künftigen Bevölkerungs-Konsum-Logik.
   */
  public static void settleSellOrderPurchase(GameState state, IdGenerator ids, SellOrder order, double quantity) {
    double remaining = order.remainingQuantity - quantity;
    if (remaining > 0) {
      order.remainingQuantity = remaining;
      return;
    }
    if (!order.autoRelist) {
      state.sellOrders.remove(order);
      notifySoldOut(state, ids, order, false);
      return;
    }
    double relistQty = reserveForRelist(state, order);
    if (relistQty > 0) {
      order.remainingQuantity = relistQty;
      order.createdAt = Clock.now();
      return;
    }
    if (order.depotColonyId != null && order.sourceFleetId == null) {
      order.remainingQuantity = 0;
    } else {
      state.sellOrders.remove(order);
    }
    // Eine wiederkehrende Order, die nichts mehr nachlegen kann, ist der Moment,
    // in dem die Einnahmen versiegen – ohne Meldung merkt das niemand.
    notifySoldOut(state, ids, order, true);
  }

  /**
   * Versucht jeden Tick, "schlafende" Auto-Relist-Orders wiederzubefüllen,
   * sobald ihre Kolonie wieder Lagerbestand hat. NUR kolonie-basierte Orders
   * werden schlafend gehalten (siehe {@link #settleSellOrderPurchase}).
   */
  /**
   * Ändert den Preis einer eigenen, offenen Verkaufsorder.
   *
   * <p>Vorher ging das nur über Zurückziehen und Neuanlegen – an einer anderen
   * Stelle der Oberfläche, mit dem Umweg über das Lager. Ein zu hoch gesetzter
   * Preis (die Bevölkerung kann ihn sich nicht leisten) ist aber der Normalfall
   * beim ersten Versuch und muss sich direkt korrigieren lassen. Die Ware
   * liegt bereits in der Order, es ändert sich nur die Zahl.</p>
   */
  public static void updateSellOrderPrice(GameState state, String playerId, String orderId, double pricePerUnit) {
    if (pricePerUnit <= 0) throw new CommandException("Der Preis muss größer als 0 sein.");
    SellOrder order = state.sellOrders.stream().filter(o -> o.id.equals(orderId)).findFirst()
        .orElseThrow(() -> new CommandException("Unbekannte Verkaufsorder."));
    if (!playerId.equals(order.sellerId)) throw new CommandException("Diese Order gehört einem anderen Kommandanten.");
    order.pricePerUnit = pricePerUnit;
  }

  /** Meldet dem Verkäufer, dass eine Order leer ist – bei wiederkehrenden Orders mit dem Hinweis auf den fehlenden Nachschub. */
  private static void notifySoldOut(GameState state, IdGenerator ids, SellOrder order, boolean wasRecurring) {
    if (order.sellerId == null) return;
    String productName = de.nebula.data.ProductCatalog.find(order.productTypeId).name;
    String text = wasRecurring
        ? "Die wiederkehrende Verkaufsorder für " + productName + " ist leer und im Lager liegt kein Nachschub – "
          + "die Einnahmen aus diesem Gut versiegen."
        : "Ihre Verkaufsorder für " + productName + " ist vollständig abverkauft.";
    Notifications.notify(state, ids, de.nebula.model.NotificationType.Info, Notifications.CODE_SELL_ORDER_SOLD_OUT,
        text, order.depotColonyId, "/handel");
  }

  public static void replenishDormantSellOrders(GameState state) {
    List<SellOrder> dormant = state.sellOrders.stream()
        .filter(o -> o.remainingQuantity == 0 && o.autoRelist && o.depotColonyId != null && o.sourceFleetId == null)
        .toList();
    for (SellOrder order : dormant) {
      double relistQty = reserveForRelist(state, order);
      if (relistQty <= 0) continue;
      order.remainingQuantity = relistQty;
      order.createdAt = Clock.now();
    }
  }

  /**
   * Reserviert für ein Auto-Relist bis zu {@code order.quantity} frisch aus
   * der Quelle, aus der die Order ursprünglich entstand (Flottenfracht bzw.
   * Kolonielager). Liefert die tatsächlich reservierte Menge; 0 = keine
   * Deckung mehr vorhanden.
   */
  private static double reserveForRelist(GameState state, SellOrder order) {
    if (order.sourceFleetId != null) {
      Fleet fleet = findFleet(state, order.sourceFleetId);
      // Zusätzlich zu "stationiert" auch noch AM SELBEN Ort wie beim Einstellen der Order prüfen – sonst
      // würde eine inzwischen weitergezogene Flotte an ihrem NEUEN Standort weiter Fracht für eine Order
      // an ihrem ALTEN Standort abgeben.
      if (fleet == null || fleet.status != de.nebula.model.FleetStatus.Stationed
          || !fleet.systemId.equals(order.systemId)
          || !java.util.Objects.equals(fleet.locationColonyId, order.depotColonyId)) return 0;
      double have = FleetCargo.qty(fleet, order.productTypeId);
      double qty = Math.min(order.quantity, Math.floor(have));
      if (qty <= 0) return 0;
      FleetCargo.add(fleet, order.productTypeId, -qty);
      return qty;
    }
    if (order.depotColonyId != null) {
      double have = Warehouse.qty(state, order.depotColonyId, order.productTypeId);
      double qty = Math.min(order.quantity, Math.floor(have));
      if (qty <= 0) return 0;
      Warehouse.add(state, order.depotColonyId, order.productTypeId, -qty);
      return qty;
    }
    return 0;
  }

  private static SellOrder find(GameState state, String orderId) {
    for (SellOrder o : state.sellOrders) if (o.id.equals(orderId)) return o;
    return null;
  }

  private static Fleet findFleet(GameState state, String fleetId) {
    for (Fleet f : state.fleets) if (f.id.equals(fleetId)) return f;
    return null;
  }
}
