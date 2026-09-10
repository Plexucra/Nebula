package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.ProductCosts;
import de.nebula.engine.Clock;
import de.nebula.model.Colony;
import de.nebula.model.DepotEntry;
import de.nebula.model.Fleet;
import de.nebula.model.FleetStatus;
import de.nebula.model.MarketOrder;
import de.nebula.model.MarketOrderSide;
import de.nebula.model.NotificationType;
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
 * EIN Orderbuch-Handel für zwei Sorten Handelsort (Umsetzungskonzept/37):
 *
 * <ul>
 *   <li><b>Handelsgilde-Station</b> ({@code planetId == null},
 *       Umsetzungskonzept/22): neutraler Boden, jeder mit jedem, ein
 *       synthetischer Market-Maker ("Handelsgilde") hält je Ware eine kleine
 *       Kauf- und Verkaufs-Order.</li>
 *   <li><b>Planetarer Handelsposten</b> ({@code planetId} gesetzt): die
 *       Einrichtung des Planeten, genutzt von allen Kolonien darauf und von
 *       allen dort gelandeten Flotten. Handel zwischen zwei Kommandanten nur
 *       mit Handelsvertrag (Konzept 05 §14, geprüft im Matching); die
 *       Bevölkerung der Kolonien kauft ohne Vertrag aus der Verkaufsseite
 *       ({@code Economy}). Keine Handelsgilde-Orders.</li>
 * </ul>
 *
 * <p>Depot je Kommandant und Ort ({@link Depot}); wer eine Kolonie auf dem
 * Planeten hat, braucht am Posten keines – sein Lager ist sein Depot: seine
 * Verkaufs-Orders speisen sich aus dem Lager ({@code sourceColonyId}), seine
 * Käufe landen im Lager. Kauf- UND Verkaufs-Orders kreuzen sich sofort, auch
 * in Teilausführung, zum Preis der älteren Order.</p>
 */
public final class MarketCommands {
  private MarketCommands() {
  }

  /** Kleine, gleich große Lose je Market-Maker-Order (Nutzervorgabe: "kleine Kauf-/Verkauforders"). */
  private static final double MM_LOT = 5;
  /** Start-Kaufpreis des Market-Makers = Produktionskosten × diesen Faktor – Anreiz für Neulinge, für den Handel zu produzieren. */
  private static final double MM_BUY_MARKUP = 1.2;
  /** Start-Verkaufspreis des Market-Makers = Kaufpreis × diesen Faktor – sonst wäre der Verkaufspreis
   * niedriger als der Kaufpreis und jeder Kommandant könnte risikofrei zwischen beiden Seiten arbitrieren. */
  private static final double MM_SELL_MARKUP = 1.1;
  /** Verschiebung nach jeder Ausführung einer Market-Maker-Order: Verkauf +10 %, Kauf −10 %. */
  private static final double MM_STEP = 0.10;

  private static StarSystem findSystem(GameState state, String systemId) {
    for (StarSystem s : state.systems) if (s.id.equals(systemId)) return s;
    return null;
  }

  private static double round2(double amount) {
    return Math.round(amount * 100) / 100.0;
  }

  static boolean at(MarketOrder o, String systemId, String planetId) {
    return o.systemId.equals(systemId) && Objects.equals(o.planetId, planetId);
  }

  // --- Handelsorte ---------------------------------------------------------------

  /** Station ({@code planetId == null}, muss Handelsgilde-Station sein) oder Posten (Planet mit mindestens einer Kolonie). */
  private static void requireLocation(GameState state, String systemId, String planetId) {
    if (planetId == null) {
      StarSystem sys = findSystem(state, systemId);
      if (sys == null || !sys.isTradeHub) {
        throw new CommandException("Ein Orderbuch gibt es nur an Handelsgilde-Stationen und an Planetaren Handelsposten.");
      }
      return;
    }
    if (state.colonies.stream().noneMatch(c -> c.planetId.equals(planetId) && c.systemId.equals(systemId))) {
      throw new CommandException("Auf diesem Planeten gibt es keinen Handelsposten – dort siedelt niemand.");
    }
  }

  /** Eigene Kolonie des Kommandanten auf dem Planeten (höchstens eine je Planet), sonst {@code null}. */
  static Colony ownColonyOnPlanet(GameState state, String playerId, String planetId) {
    if (planetId == null || playerId == null) return null;
    for (Colony c : state.colonies) if (c.planetId.equals(planetId) && c.ownerId.equals(playerId)) return c;
    return null;
  }

  /**
   * Zugang zum Posten: eine eigene Kolonie auf dem Planeten oder eine dort
   * (bei irgendeiner Kolonie des Planeten) gelandete eigene Flotte. Der Posten
   * selbst ist neutral – auch bei einer fremden Kolonie darf man landen und
   * sein Depot nutzen (Konzept 05 §6). An der Station gibt es keine Hürde.
   */
  private static void requireAccess(GameState state, String playerId, String systemId, String planetId) {
    if (planetId == null) return;
    if (ownColonyOnPlanet(state, playerId, planetId) != null) return;
    for (Fleet f : state.fleets) {
      if (!f.ownerId.equals(playerId) || f.status != FleetStatus.Stationed || f.locationColonyId == null) continue;
      Colony c = ColonyCommands.colony(state, f.locationColonyId);
      if (c != null && c.planetId.equals(planetId)) return;
    }
    throw new CommandException("Am Handelsposten dieses Planeten handelt nur, wer dort eine Kolonie hat oder mit einer Flotte gelandet ist.");
  }

  /** Vertragsregel des Postens (Konzept 05 §14): eigene Gegenpartei, Handelsgilde oder Handelsvertrag. */
  private static boolean mayTrade(GameState state, String planetId, String ownerA, String ownerB) {
    if (planetId == null) return true;
    if (ownerA == null || ownerB == null || ownerA.equals(ownerB)) return true;
    return TreatyCommands.hasTradeAgreement(state, ownerA, ownerB);
  }

  // --- Lese-Abfragen ---------------------------------------------------------

  /** Offene Orders eines Handelsorts, beide Seiten. */
  public static List<MarketOrder> ordersAt(GameState state, String systemId, String planetId) {
    return state.marketOrders.stream().filter(o -> at(o, systemId, planetId) && o.remainingQuantity > 0).toList();
  }

  /** Offene Verkaufs-Orders aller Planetaren Handelsposten eines Systems – die Übersicht "was gibt es hier zu kaufen". */
  public static List<MarketOrder> postSellOrdersInSystem(GameState state, String systemId) {
    return state.marketOrders.stream()
        .filter(o -> o.systemId.equals(systemId) && o.planetId != null && o.side == MarketOrderSide.Sell && o.remainingQuantity > 0)
        .toList();
  }

  /** Verkaufs-Orders am Posten eines Planeten, günstigste zuerst – die Einkaufsliste der Bevölkerung. */
  public static List<MarketOrder> sellOrdersAtPost(GameState state, String systemId, String planetId, String productTypeId) {
    List<MarketOrder> orders = new ArrayList<>();
    for (MarketOrder o : state.marketOrders) {
      if (at(o, systemId, planetId) && o.side == MarketOrderSide.Sell && o.productTypeId.equals(productTypeId)
          && o.remainingQuantity > 0) orders.add(o);
    }
    orders.sort(Comparator.comparingDouble((MarketOrder o) -> o.limitPrice).thenComparingLong(o -> o.seq));
    return orders;
  }

  public static List<DepotEntry> depotOf(GameState state, String systemId, String planetId, String playerId) {
    return Depot.ownedBy(state, systemId, planetId, playerId);
  }

  // --- Order-Anlage ------------------------------------------------------------

  /**
   * Verkaufs-Order an Station oder Posten. Am Posten speist sie sich aus dem
   * Lager der eigenen Kolonie auf dem Planeten, sonst aus dem Depot am Ort.
   */
  public static void createSellOrder(GameState state, IdGenerator ids, String playerId, String systemId, String planetId,
                                      String productTypeId, double quantity, double pricePerUnit, boolean autoRelist) {
    quantity = Math.floor(quantity);
    if (quantity < 1 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    requireLocation(state, systemId, planetId);
    requireAccess(state, playerId, systemId, planetId);
    Player player = GameQueries.requirePlayer(state, playerId);
    Colony source = ownColonyOnPlanet(state, playerId, planetId);
    if (source != null) {
      // Mechanik/05_...md §12: keine NEUEN Handelsaktionen an einer Kolonie im
      // Bodengefecht. Bestehende Orders laufen weiter, die Bevölkerung kauft weiter.
      if (GroundBattleCommands.isUnderGroundAttack(state, source.id)) {
        throw new CommandException("Während eines laufenden Bodengefechts nimmt diese Kolonie keine neuen Handelsaufträge an.");
      }
      if (Warehouse.qty(state, source.id, productTypeId) < quantity) throw new CommandException("Nicht genug Lagerbestand für diese Order.");
      Warehouse.add(state, source.id, productTypeId, -quantity);
    } else {
      if (Depot.qty(state, systemId, planetId, playerId, productTypeId) < quantity) {
        throw new CommandException(planetId == null ? "Nicht genug Bestand im Stationsdepot." : "Nicht genug Bestand im Depot am Handelsposten.");
      }
      Depot.add(state, systemId, planetId, playerId, productTypeId, -quantity);
    }

    MarketOrder order = newOrder(state, ids, systemId, planetId, productTypeId, MarketOrderSide.Sell, player, pricePerUnit, quantity);
    order.autoRelist = autoRelist;
    order.sourceColonyId = source != null ? source.id : null;
    state.marketOrders.add(order);

    if (planetId == null) ensureMarketMaker(state, ids, systemId, productTypeId);
    matchOrders(state, ids, systemId, planetId, productTypeId);
    if (planetId != null) emergencyPurchases(state, ids, planetId, productTypeId);
  }

  /** "Anbieten" im Lager einer eigenen Kolonie – die Order landet am Posten ihres Planeten. */
  public static void createSellOrderFromColony(GameState state, IdGenerator ids, String playerId, String colonyId,
                                                String productTypeId, double quantity, double pricePerUnit, boolean autoRelist) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    Colony colony = ColonyCommands.colony(state, colonyId);
    createSellOrder(state, ids, playerId, colony.systemId, colony.planetId, productTypeId, quantity, pricePerUnit, autoRelist);
  }

  /**
   * Verkauf aus der Fracht einer bei einer Kolonie gelandeten Flotte: die Ware
   * geht ins eigene Depot am Posten dieses Planeten (bzw. ins Lager der eigenen
   * Kolonie dort) und wird von da angeboten – derselbe Weg wie an der Station,
   * nur in einem Schritt.
   */
  public static void createSellOrderFromFleet(GameState state, IdGenerator ids, String playerId, String fleetId,
                                               String productTypeId, double quantity, double pricePerUnit, boolean autoRelist) {
    quantity = Math.floor(quantity);
    if (quantity < 1 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    Fleet fleet = FleetCommands.requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist unterwegs.");
    if (fleet.locationColonyId == null) {
      StarSystem sys = findSystem(state, fleet.systemId);
      if (sys != null && sys.isTradeHub) {
        throw new CommandException("An einer Handelsgilde-Station läuft der Handel über das Depot: zuerst Fracht ins Depot entladen, dann auf der Handel-Seite eine Order aufgeben.");
      }
      throw new CommandException("Verkaufen geht nur am Handelsposten eines Planeten – bitte bei einer Kolonie landen.");
    }
    Colony here = ColonyCommands.colony(state, fleet.locationColonyId);
    if (here == null) throw new CommandException("Unbekannte Kolonie.");
    if (FleetCargo.qty(fleet, productTypeId) < quantity) throw new CommandException("Nicht genug Fracht an Bord.");
    FleetCargo.add(fleet, productTypeId, -quantity);
    Colony own = ownColonyOnPlanet(state, playerId, here.planetId);
    if (own != null) Warehouse.add(state, own.id, productTypeId, quantity);
    else Depot.add(state, here.systemId, here.planetId, playerId, productTypeId, quantity);
    createSellOrder(state, ids, playerId, here.systemId, here.planetId, productTypeId, quantity, pricePerUnit, autoRelist);
  }

  public static void createBuyOrder(GameState state, IdGenerator ids, String playerId, String systemId, String planetId,
                                     String productTypeId, double quantity, double pricePerUnit) {
    quantity = Math.floor(quantity);
    if (quantity < 1 || pricePerUnit <= 0) throw new CommandException("Menge und Preis müssen größer als 0 sein.");
    requireLocation(state, systemId, planetId);
    requireAccess(state, playerId, systemId, planetId);
    Player player = GameQueries.requirePlayer(state, playerId);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    double cost = round2(quantity * pricePerUnit);
    if (wallet == null || wallet.balance < cost) throw new CommandException("Nicht genug Credits.");
    // Sofortiges Escrow – kein Ledger-Eintrag: eine Reservierung ist kein abgeschlossenes Geschäft
    // (analog dazu, dass eine Verkaufs-Order die Ware ohne Ledger-Gegenstück aus dem Lager bucht).
    // Erst eine tatsächliche AUSFÜHRUNG erzeugt eine Transaktion.
    wallet.balance -= cost;

    MarketOrder order = newOrder(state, ids, systemId, planetId, productTypeId, MarketOrderSide.Buy, player, pricePerUnit, quantity);
    order.escrowedCredits = cost;
    state.marketOrders.add(order);

    if (planetId == null) ensureMarketMaker(state, ids, systemId, productTypeId);
    matchOrders(state, ids, systemId, planetId, productTypeId);
  }

  private static MarketOrder newOrder(GameState state, IdGenerator ids, String systemId, String planetId, String productTypeId,
                                      MarketOrderSide side, Player owner, double price, double quantity) {
    MarketOrder order = new MarketOrder();
    order.id = ids.next("mo");
    order.systemId = systemId;
    order.planetId = planetId;
    order.productTypeId = productTypeId;
    order.side = side;
    order.ownerId = owner != null ? owner.id : null;
    order.ownerName = owner != null ? owner.name : "Handelsgilde";
    order.limitPrice = price;
    order.quantity = quantity;
    order.remainingQuantity = quantity;
    order.escrowedCredits = 0;
    order.createdAt = Clock.now();
    order.seq = state.marketOrderSeq.incrementAndGet();
    return order;
  }

  public static void cancelOrder(GameState state, String playerId, String orderId) {
    MarketOrder order = find(state, orderId);
    if (order == null) return;
    Player player = GameQueries.requirePlayer(state, playerId);
    if (order.ownerId == null || !order.ownerId.equals(player.id)) {
      throw new CommandException("Diese Order gehört einem anderen Kommandanten.");
    }
    refundAndRemove(state, order);
  }

  /**
   * Ändert den Preis einer eigenen Order. Ein zu hoch gesetzter Preis (die
   * Bevölkerung kann ihn sich nicht leisten) ist beim ersten Versuch der
   * Normalfall und muss sich direkt korrigieren lassen. Bei Kauf-Orders wird
   * das Escrow auf den neuen Preis angepasst.
   */
  public static void updateOrderPrice(GameState state, IdGenerator ids, String playerId, String orderId, double pricePerUnit) {
    if (pricePerUnit <= 0) throw new CommandException("Der Preis muss größer als 0 sein.");
    MarketOrder order = find(state, orderId);
    if (order == null) throw new CommandException("Unbekannte Order.");
    if (!playerId.equals(order.ownerId)) throw new CommandException("Diese Order gehört einem anderen Kommandanten.");
    if (order.side == MarketOrderSide.Buy) {
      Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
      double newEscrow = round2(order.remainingQuantity * pricePerUnit);
      double delta = newEscrow - order.escrowedCredits;
      if (wallet == null || (delta > 0 && wallet.balance < delta)) throw new CommandException("Nicht genug Credits.");
      wallet.balance -= delta;
      order.escrowedCredits = newEscrow;
    }
    order.limitPrice = pricePerUnit;
    matchOrders(state, ids, order.systemId, order.planetId, order.productTypeId);
    // Ein gesenkter Preis ist der häufigste Grund, warum eine knappe Bevölkerung jetzt kaufen kann.
    if (order.planetId != null && order.side == MarketOrderSide.Sell) emergencyPurchases(state, ids, order.planetId, order.productTypeId);
  }

  /**
   * Sofortkauf gegen EINE bestimmte Verkaufs-Order zu deren Preis – der
   * "Kaufen"-Knopf neben einer Order. Lieferung ins Lager der eigenen Kolonie
   * auf dem Planeten, sonst ins Depot am Ort.
   */
  public static void buyFromOrder(GameState state, IdGenerator ids, String playerId, String orderId, double quantity) {
    quantity = Math.floor(quantity);
    MarketOrder ask = find(state, orderId);
    if (ask == null || ask.side != MarketOrderSide.Sell || ask.remainingQuantity < quantity || quantity < 1) {
      throw new CommandException("Nicht genug Ware in dieser Order verfügbar.");
    }
    requireAccess(state, playerId, ask.systemId, ask.planetId);
    Player player = GameQueries.requirePlayer(state, playerId);
    if (player.id.equals(ask.ownerId)) throw new CommandException("Die eigene Order kauft man nicht – sie lässt sich zurückziehen.");
    if (!mayTrade(state, ask.planetId, player.id, ask.ownerId)) {
      throw new CommandException("Planetarer Handel ist nur mit Kommandanten möglich, mit denen ein Handelsvertrag besteht.");
    }
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    double cost = round2(quantity * ask.limitPrice);
    if (wallet == null || wallet.balance < cost) throw new CommandException("Nicht genug Credits.");
    settleAsk(state, ids, ask, quantity, cost, wallet.id, player.id);
  }

  private static MarketOrder find(GameState state, String orderId) {
    for (MarketOrder o : state.marketOrders) if (o.id.equals(orderId)) return o;
    return null;
  }

  /** Erstattet den nicht ausgeführten Rest (Ware → Quelle bzw. Credits → Wallet) und entfernt die Order. */
  private static void refundAndRemove(GameState state, MarketOrder order) {
    if (order.ownerId != null) {
      if (order.side == MarketOrderSide.Buy) {
        Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, order.ownerId);
        if (wallet != null && order.escrowedCredits > 0) wallet.balance += order.escrowedCredits;
      } else if (order.remainingQuantity > 0) {
        returnGoods(state, order, order.remainingQuantity);
      }
    }
    // ownerId == null: Handelsgilde – kein echtes Konto/Depot, keine Erstattung.
    state.marketOrders.remove(order);
  }

  /** Ware zurück in die Quelle der Verkaufs-Order: Kolonielager oder Depot am Ort. */
  private static void returnGoods(GameState state, MarketOrder order, double quantity) {
    if (order.sourceColonyId != null && ColonyCommands.colony(state, order.sourceColonyId) != null) {
      Warehouse.add(state, order.sourceColonyId, order.productTypeId, quantity);
    } else {
      Depot.add(state, order.systemId, order.planetId, order.ownerId, order.productTypeId, quantity);
    }
  }

  /** Gekaufte Ware an den Käufer: Lager der eigenen Kolonie auf dem Planeten, sonst Depot am Ort. */
  private static void deliver(GameState state, String systemId, String planetId, String buyerId, String productTypeId, double quantity) {
    Colony own = ownColonyOnPlanet(state, buyerId, planetId);
    if (own != null) Warehouse.add(state, own.id, productTypeId, quantity);
    else Depot.add(state, systemId, planetId, buyerId, productTypeId, quantity);
  }

  // --- Ausführung einer Verkaufs-Order ------------------------------------------

  /**
   * Zieht {@code quantity} aus einer Verkaufs-Order, zahlt den Verkäufer und
   * liefert an den Käufer. Gemeinsamer Kern für Kauf-Orders ({@link #execute}),
   * den Sofortkauf ({@link #buyFromOrder}) und den Tageseinkauf der Bevölkerung
   * ({@code Economy}, {@code buyerId == null}: die Ware geht in ihren Vorrat,
   * {@code fromWalletId} ist das Bevölkerungs-Wallet).
   *
   * <p>Erreicht die Restmenge 0 und ist {@code autoRelist} gesetzt, wird SOFORT
   * aus der Quelle nachgelegt; reicht die nicht, bleibt die Order "schlafend"
   * (Restmenge 0) und wird beim nächsten Zugang geweckt. Ohne Dauerorder
   * verschwindet sie.</p>
   */
  public static void settleAsk(GameState state, IdGenerator ids, MarketOrder ask, double quantity, double cost,
                               String fromWalletId, String buyerId) {
    ask.remainingQuantity -= quantity;
    if (buyerId != null) deliver(state, ask.systemId, ask.planetId, buyerId, ask.productTypeId, quantity);
    String toWalletId = ask.ownerId != null ? walletId(state, ask.ownerId) : null;
    if (cost > 0 && (fromWalletId != null || toWalletId != null)) {
      TransactionReason reason = buyerId == null ? TransactionReason.Consumption : TransactionReason.Trade;
      String note = buyerId == null ? "Konsum " + ask.productTypeId
          : (ask.planetId == null ? "Handelsgilde: " : "Handelsposten: ") + (long) quantity + "× " + ask.productTypeId + " zu " + ask.limitPrice + " Cr";
      Ledger.recordTx(state, ids, fromWalletId, toWalletId, cost, reason, note);
    }
    if (ask.remainingQuantity > 1e-9) return;
    if (ask.ownerId == null) return; // Handelsgilde: entfernt und nachgestellt von matchOrders
    if (!ask.autoRelist) {
      state.marketOrders.remove(ask);
      notifySoldOut(state, ids, ask, false);
      return;
    }
    double relisted = reserveForRelist(state, ask);
    if (relisted > 0) {
      ask.remainingQuantity = relisted;
      ask.createdAt = Clock.now();
      return;
    }
    ask.remainingQuantity = 0;
    // Eine Dauerorder, die nichts mehr nachlegen kann, ist der Moment, in dem
    // die Einnahmen versiegen – ohne Meldung merkt das niemand.
    notifySoldOut(state, ids, ask, true);
  }

  /** Reserviert für ein Auto-Relist bis zu {@code order.quantity} frisch aus der Quelle; 0 = keine Deckung mehr. */
  private static double reserveForRelist(GameState state, MarketOrder order) {
    if (order.sourceColonyId != null) {
      if (ColonyCommands.colony(state, order.sourceColonyId) == null) return 0;
      double have = Warehouse.qty(state, order.sourceColonyId, order.productTypeId);
      double qty = Math.min(order.quantity, Math.floor(have));
      if (qty <= 0) return 0;
      Warehouse.add(state, order.sourceColonyId, order.productTypeId, -qty);
      return qty;
    }
    double have = Depot.qty(state, order.systemId, order.planetId, order.ownerId, order.productTypeId);
    double qty = Math.min(order.quantity, Math.floor(have));
    if (qty <= 0) return 0;
    Depot.add(state, order.systemId, order.planetId, order.ownerId, order.productTypeId, -qty);
    return qty;
  }

  /** Meldet dem Verkäufer, dass eine Order leer ist – bei Dauerorders mit dem Hinweis auf den fehlenden Nachschub. */
  private static void notifySoldOut(GameState state, IdGenerator ids, MarketOrder order, boolean wasRecurring) {
    if (order.ownerId == null) return;
    String productName = ProductCatalog.find(order.productTypeId).name;
    String text = wasRecurring
        ? "Die wiederkehrende Verkaufsorder für " + productName + " ist leer und es liegt kein Nachschub bereit – "
          + "die Einnahmen aus diesem Gut versiegen."
        : "Ihre Verkaufsorder für " + productName + " ist vollständig abverkauft.";
    if (order.sourceColonyId != null) {
      Notifications.notify(state, ids, NotificationType.Info, Notifications.CODE_SELL_ORDER_SOLD_OUT, text, order.sourceColonyId, "/handel");
    } else {
      Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_SELL_ORDER_SOLD_OUT, text, order.ownerId, "/handel");
    }
  }

  /**
   * Weckt schlafende Dauerorders, die aus dem Lager einer Kolonie gespeist
   * sind, sobald dort wieder Bestand liegt. Reaktion statt Takt: {@link Warehouse#addRaw}
   * ruft die eingeschränkte Fassung bei jedem Lagerzugang. Die Fassung über
   * alles bleibt für Tests und Seeds.
   *
   * <p>Den Notkauf der Bevölkerung ({@link Economy#emergencyPurchase}) löst
   * das Nachfüllen hier NICHT aus – das Lager kennt keinen Id-Generator für die
   * Buchung. Die Produktion ruft ihn nach dem Einlagern selbst
   * ({@code ProductionCommands.completeProductionEntry}).</p>
   */
  public static void replenishDormantSellOrders(GameState state) {
    replenishDormantSellOrders(state, null, null);
  }

  /** Wie oben, nur für die Orders EINER Kolonie und EINES Produkts ({@code null} = alle). */
  public static void replenishDormantSellOrders(GameState state, String colonyId, String productTypeId) {
    for (MarketOrder order : state.marketOrders) {
      if (order.remainingQuantity != 0 || !order.autoRelist || order.sourceColonyId == null) continue;
      if (colonyId != null && !order.sourceColonyId.equals(colonyId)) continue;
      if (productTypeId != null && !order.productTypeId.equals(productTypeId)) continue;
      wake(state, order);
    }
  }

  /** Dasselbe für Dauerorders aus einem Depot, gerufen von {@link Depot#add} bei jedem Zugang. */
  static void replenishDormantDepotOrders(GameState state, String systemId, String planetId, String ownerId, String productTypeId) {
    for (MarketOrder order : state.marketOrders) {
      if (order.remainingQuantity != 0 || !order.autoRelist || order.sourceColonyId != null) continue;
      if (!at(order, systemId, planetId) || !ownerId.equals(order.ownerId) || !order.productTypeId.equals(productTypeId)) continue;
      wake(state, order);
    }
  }

  private static void wake(GameState state, MarketOrder order) {
    double relistQty = reserveForRelist(state, order);
    if (relistQty <= 0) return;
    order.remainingQuantity = relistQty;
    order.createdAt = Clock.now();
  }

  /** Notkauf aller Kolonien auf dem Planeten, deren Vorrat an diesem Gut knapp ist. */
  private static void emergencyPurchases(GameState state, IdGenerator ids, String planetId, String productTypeId) {
    for (Colony c : state.colonies) {
      if (c.planetId.equals(planetId)) Economy.emergencyPurchase(state, ids, c.id, productTypeId);
    }
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
  public static void matchOrders(GameState state, IdGenerator ids, String systemId, String planetId, String productTypeId) {
    boolean mmSellConsumed = false;
    double mmSellLastPrice = 0;
    boolean mmBuyConsumed = false;
    double mmBuyLastPrice = 0;

    int guard = 0;
    while (true) {
      if (++guard > 10_000) break; // Notbremse – sollte laut Terminierungsargument (siehe matchOnce) nie greifen.
      MatchOutcome r = matchOnce(state, ids, systemId, planetId, productTypeId);
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
    if (mmSellConsumed) repostMarketMaker(state, ids, systemId, productTypeId, MarketOrderSide.Sell, mmSellLastPrice);
    if (mmBuyConsumed) repostMarketMaker(state, ids, systemId, productTypeId, MarketOrderSide.Buy, mmBuyLastPrice);
  }

  /**
   * Sucht das bestpreisige, zeitälteste kreuzende Gebot/Brief-Paar und führt
   * EINE Ausführung aus. Selbsthandel ist ausgeschlossen, am Posten außerdem
   * jedes Paar ohne Handelsvertrag. {@code null}, wenn nichts (mehr) kreuzt.
   */
  private static MatchOutcome matchOnce(GameState state, IdGenerator ids, String systemId, String planetId, String productTypeId) {
    List<MarketOrder> bids = new ArrayList<>();
    List<MarketOrder> asks = new ArrayList<>();
    for (MarketOrder o : state.marketOrders) {
      if (!at(o, systemId, planetId) || !o.productTypeId.equals(productTypeId) || o.remainingQuantity <= 0) continue;
      (o.side == MarketOrderSide.Buy ? bids : asks).add(o);
    }
    bids.sort(Comparator.comparingDouble((MarketOrder o) -> -o.limitPrice).thenComparingLong(o -> o.seq));
    asks.sort(Comparator.comparingDouble((MarketOrder o) -> o.limitPrice).thenComparingLong(o -> o.seq));

    for (MarketOrder bid : bids) {
      for (MarketOrder ask : asks) {
        if (bid.limitPrice + 1e-9 < ask.limitPrice) break; // asks aufsteigend sortiert: ab hier kreuzt nichts mehr für DIESES Gebot
        if (Objects.equals(bid.ownerId, ask.ownerId)) continue; // eigene Gegenposition (auch Handelsgilde vs. Handelsgilde) überspringen
        if (!mayTrade(state, planetId, bid.ownerId, ask.ownerId)) continue; // Posten: nur mit Handelsvertrag
        return execute(state, ids, bid, ask);
      }
    }
    return null;
  }

  /** Ausführungspreis = Preis der RUHENDEREN (älteren) Order der beiden – klassische Maker-nimmt-seinen-Preis-Regel. */
  private static MatchOutcome execute(GameState state, IdGenerator ids, MarketOrder bid, MarketOrder ask) {
    MarketOrder maker = bid.seq < ask.seq ? bid : ask;
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

    boolean askWasGuild = ask.ownerId == null;
    double askLimit = ask.limitPrice;
    String askOwner = ask.ownerId;
    double askRemainingBefore = ask.remainingQuantity;

    // Der Käufer wurde bereits beim Einstellen ins Escrow belastet, deshalb kein Wallet
    // auf der Zahlerseite – sonst würde derselbe Betrag zweimal abgezogen.
    settleAsk(state, ids, ask, qty, cost, null, bid.ownerId);
    // ask.ownerId == null (Handelsgilde verkauft): Ware wird konjuriert, kein Depot-Abbuchen nötig.

    bid.escrowedCredits = Math.max(0, bid.escrowedCredits - cost);
    bid.remainingQuantity -= qty;

    boolean sellRemoved = false;
    // Eine Handelsgilde-Order wird bei JEDER Ausführung entfernt, auch bei einer Teilausführung
    // (Nutzervorgabe: "sobald ein Produkt gekauft oder verkauft wird, stellt er erneut eine Order
    // ein" – nicht erst, wenn das ganze Los weg ist). Ein etwaiger Rest verfällt.
    if (askWasGuild) {
      state.marketOrders.remove(ask);
      sellRemoved = true;
    } else if (askRemainingBefore - qty < 1e-9) {
      sellRemoved = true; // settleAsk hat entfernt, nachgelegt oder schlafend gestellt
    }
    boolean buyRemoved = false;
    if (bid.ownerId == null || bid.remainingQuantity < 1e-9 || bid.escrowedCredits < 0.005) {
      refundAndRemove(state, bid);
      buyRemoved = true;
    }
    return new MatchOutcome(sellRemoved, askOwner, askLimit, buyRemoved, bid.ownerId, bid.limitPrice);
  }

  private static String walletId(GameState state, String playerId) {
    Wallet w = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    return w != null ? w.id : null;
  }

  // --- Market-Maker ("Handelsgilde"), nur an Stationen --------------------------

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
    for (MarketOrder o : state.marketOrders) {
      if (o.ownerId != null || !at(o, systemId, null) || !o.productTypeId.equals(productTypeId)) continue;
      if (o.side == MarketOrderSide.Sell) hasSell = true; else hasBuy = true;
    }
    double cost = ProductCosts.of(productTypeId);
    double buyPrice = cost * MM_BUY_MARKUP;
    if (!hasSell) postMarketMaker(state, ids, systemId, productTypeId, MarketOrderSide.Sell, buyPrice * MM_SELL_MARKUP);
    if (!hasBuy) postMarketMaker(state, ids, systemId, productTypeId, MarketOrderSide.Buy, buyPrice);
  }

  private static boolean isMarketMakerEligible(String productTypeId) {
    ProductType product = ProductCatalog.find(productTypeId);
    // Schiffe/Bodeneinheiten entstehen nur in Werft/Ausbildungszentrum und werden nie Lagerware
    // (ProductionCommands.queueProductionCore) – gekaufte Stück wären tote Depot-Einträge.
    return product.category != ProductCategory.Ship && product.category != ProductCategory.GroundUnit;
  }

  private static void repostMarketMaker(GameState state, IdGenerator ids, String systemId, String productTypeId,
                                         MarketOrderSide side, double lastPrice) {
    double newPrice = side == MarketOrderSide.Sell ? lastPrice * (1 + MM_STEP) : lastPrice * (1 - MM_STEP);
    postMarketMaker(state, ids, systemId, productTypeId, side, newPrice);
  }

  private static void postMarketMaker(GameState state, IdGenerator ids, String systemId, String productTypeId,
                                       MarketOrderSide side, double price) {
    MarketOrder order = newOrder(state, ids, systemId, null, productTypeId, side, null, round2(Math.max(price, 0.01)), MM_LOT);
    state.marketOrders.add(order);
  }

  /**
   * Einmalige Erstbefüllung der GESAMTEN Galaxie beim allerersten
   * {@code GameStateSeeder.bootstrap}: für jede Handelsgilde-Station und
   * jede handelbare Ware (alle außer Schiffe/Bodeneinheiten) je eine
   * Kauf- und Verkaufs-Order der Handelsgilde. Absichtlich NICHT lazy beim
   * ersten Betreten einer Station – sonst müsste jede Abfrage der
   * Orderbuch-Seite 177 Waren auf Vollständigkeit prüfen.
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
