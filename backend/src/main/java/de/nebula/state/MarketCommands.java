package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.SellOrder;
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
 */
public final class MarketCommands {
  private MarketCommands() {
  }

  public static List<SellOrder> sellOrdersInSystem(GameState state, String systemId) {
    return state.sellOrders.stream().filter(o -> o.systemId.equals(systemId) && o.remainingQuantity > 0).toList();
  }

  public static void createSellOrder(GameState state, IdGenerator ids, String playerId, String colonyId,
                                      String productTypeId, double quantity, double pricePerUnit, boolean autoRelist) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
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
  }

  /**
   * Versucht jeden Tick, "schlafende" Auto-Relist-Orders wiederzubefüllen,
   * sobald ihre Kolonie wieder Lagerbestand hat. NUR kolonie-basierte Orders
   * werden schlafend gehalten (siehe {@link #settleSellOrderPurchase}).
   */
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
      if (fleet == null || fleet.status != de.nebula.model.FleetStatus.Stationed) return 0;
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
