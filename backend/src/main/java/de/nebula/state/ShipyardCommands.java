package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;
import de.nebula.model.ShipyardQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.List;

/**
 * 1:1-Portierung der Werft-Sektion (sequentielle Warteschlange, strukturell
 * identisch zu {@link ProductionCommands}) aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 7).
 */
public final class ShipyardCommands {
  private ShipyardCommands() {
  }

  public static List<ShipyardQueueEntry> shipyardQueueFor(GameState state, String colonyId) {
    return state.shipyardQueue.stream().filter(q -> q.colonyId.equals(colonyId)).toList();
  }

  public static void queueShip(GameState state, IdGenerator ids, String playerId, String colonyId,
                                String shipProductTypeId, double quantity, boolean autoProduceMissing, boolean requeueOnComplete) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    ProductType product = ProductCatalog.find(shipProductTypeId);
    if (product.category != ProductCategory.Ship) throw new CommandException("Kein Schiffstyp.");
    if (GameQueries.getBuildingLevel(state, colonyId, "b_shipyard") < 1) throw new CommandException("Ohne Werft können keine Schiffe gebaut werden.");
    if (GameConstants.COLONY_SHIP_PRODUCT_ID.equals(shipProductTypeId)) {
      reserveColonists(state, ids, playerId, colonyId, quantity);
    }

    ShipyardQueueEntry entry = new ShipyardQueueEntry();
    entry.id = ids.next("sy");
    entry.colonyId = colonyId;
    entry.shipProductTypeId = shipProductTypeId;
    entry.quantity = quantity;
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = ChainPlan.EMPTY;
    entry.startedAt = null;
    entry.endsAt = null;
    state.shipyardQueue.add(entry);
    tryStartNextShipyardEntry(state, ids, colonyId);
  }

  public static void resumeShipOrder(GameState state, IdGenerator ids, String playerId, String colonyId, String entryId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    ShipyardQueueEntry entry = find(state, colonyId, entryId);
    if (entry == null || entry.status != ProductionQueueStatus.stopped) return;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    tryStartNextShipyardEntry(state, ids, colonyId);
  }

  public static void cancelShipOrder(GameState state, IdGenerator ids, String playerId, String colonyId, String entryId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    ShipyardQueueEntry entry = find(state, colonyId, entryId);
    if (entry == null) return;
    ProductionCommands.creditPartialChainProgress(state, colonyId, entry.status, entry.startedAt, entry.endsAt, entry.plan,
        (pid, qty) -> Warehouse.add(state, colonyId, pid, qty));
    if (GameConstants.COLONY_SHIP_PRODUCT_ID.equals(entry.shipProductTypeId)) {
      releaseColonists(state, ids, playerId, colonyId, entry.quantity);
    }
    state.shipyardQueue.remove(entry);
    GameEvents.cancel(state, GameEventType.SHIP_COMPLETED, entry.id);
    tryStartNextShipyardEntry(state, ids, colonyId);
  }

  /** Kolonisten je Kolonisationsschiff – zugleich die Bevölkerung, mit der die neue Kolonie startet. */
  public static double colonistsPerShip() {
    return GameConstants.START_POPULATION;
  }

  /** Prämie, die der Kommandant den Kolonisten je Schiff auszahlt. */
  public static double colonistPremiumPerShip() {
    return GameConstants.START_POPULATION * Formulas.CREDITS_PER_NEW_INHABITANT;
  }

  /**
   * Bindet Kolonisten und Prämie beim EINREIHEN eines Kolonisationsschiffs
   * (Umsetzungskonzept/24_...md): die Kolonisten sind ab diesem Moment
   * abgemustert und zählen nicht mehr zur Bevölkerung der Kolonie, die Prämie
   * ist ausgezahlt. Ein Abbruch macht beides rückgängig ({@link #releaseColonists}).
   *
   * <p>Bewusst schon beim Einreihen und nicht erst bei Fertigstellung: sonst
   * könnte ein fertiges Schiff ohne Besatzung dastehen, weil die Bevölkerung
   * während der Bauwoche gesunken ist.</p>
   */
  private static void reserveColonists(GameState state, IdGenerator ids, String playerId, String colonyId, double quantity) {
    PlanetStats stats = null;
    for (PlanetStats s : state.planetStats) if (s.colonyId.equals(colonyId)) stats = s;
    if (stats == null || stats.loyaltyPct < GameConstants.COLONY_SHIP_MIN_LOYALTY_PCT) {
      throw new CommandException("Kolonisationsschiffe verlangen mindestens "
          + (long) GameConstants.COLONY_SHIP_MIN_LOYALTY_PCT + " % Loyalität – bei "
          + (stats == null ? 0 : Math.round(stats.loyaltyPct)) + " % findet sich niemand, der auswandern will.");
    }
    Population population = null;
    for (Population p : state.populations) if (p.colonyId.equals(colonyId)) population = p;
    double colonists = quantity * colonistsPerShip();
    // Die Kolonie muss nach dem Auszug noch eine volle Startbevölkerung behalten –
    // für EIN Schiff also die geforderten 2 × Startbevölkerung.
    double required = colonists + GameConstants.START_POPULATION;
    if (population == null || population.currentCount < required) {
      throw new CommandException("Zu wenig Bevölkerung: " + (long) required + " Einwohner nötig ("
          + (long) colonists + " wandern aus, " + (long) GameConstants.START_POPULATION
          + " müssen bleiben), vorhanden sind " + (population == null ? 0 : (long) population.currentCount) + ".");
    }
    double premium = quantity * colonistPremiumPerShip();
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    if (wallet == null || wallet.balance < premium) {
      throw new CommandException("Nicht genug Credits für die Kolonistenprämie: " + (long) premium + " Cr nötig.");
    }
    population.currentCount -= colonists;
    // Die Prämie geht an die Bevölkerung der BAU-Kolonie, wo die Kolonisten bis zum
    // Auslaufen leben – bewusste Vereinfachung gegenüber einer Prämie, die als
    // Startkapital der neuen Kolonie mitreist (Geld bleibt so im Kreislauf).
    Ledger.recordTx(state, ids, wallet.id, GameQueries.popWalletIdForColony(state, colonyId), premium,
        TransactionReason.Subsidy, "Kolonistenprämie für " + (long) quantity + " Kolonisationsschiff(e)");
  }

  /** Gegenstück zu {@link #reserveColonists} beim Abbruch eines Werftauftrags. */
  private static void releaseColonists(GameState state, IdGenerator ids, String playerId, String colonyId, double quantity) {
    for (Population p : state.populations) {
      if (p.colonyId.equals(colonyId)) p.currentCount += quantity * colonistsPerShip();
    }
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    if (wallet != null) {
      Ledger.recordTx(state, ids, GameQueries.popWalletIdForColony(state, colonyId), wallet.id,
          quantity * colonistPremiumPerShip(), TransactionReason.Subsidy,
          "Kolonistenprämie zurück (Werftauftrag abgebrochen)");
    }
  }

  private static ShipyardQueueEntry find(GameState state, String colonyId, String entryId) {
    for (ShipyardQueueEntry e : state.shipyardQueue) {
      if (e.id.equals(entryId) && e.colonyId.equals(colonyId)) return e;
    }
    return null;
  }

  public static void tryStartNextShipyardEntry(GameState state, IdGenerator ids, String colonyId) {
    List<ShipyardQueueEntry> queue = shipyardQueueFor(state, colonyId);
    for (ShipyardQueueEntry e : queue) if (e.status == ProductionQueueStatus.running) return;
    for (ShipyardQueueEntry e : queue) {
      if (e.status == ProductionQueueStatus.queued) {
        startShipyardEntry(state, ids, e);
        return;
      }
    }
  }

  private static void startShipyardEntry(GameState state, IdGenerator ids, ShipyardQueueEntry entry) {
    ChainPlan plan = ChainPlanner.planChain(state, entry.colonyId, entry.shipProductTypeId, entry.quantity, "b_shipyard");
    if (!plan.feasible && !entry.autoProduceMissing) {
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_QUEUE_STOPPED;
      Notifications.notify(state, ids, de.nebula.model.NotificationType.Problem, Notifications.CODE_QUEUE_STOPPED,
          "Werft-Warteschlange angehalten: nicht genug Vorprodukte für \""
              + ProductCatalog.find(entry.shipProductTypeId).name + "\" vorhanden.", entry.colonyId, null);
      return;
    }
    for (ChainPlanStep step : plan.steps) {
      if (step.quantityFromWarehouse > 0) Warehouse.add(state, entry.colonyId, step.productTypeId, -step.quantityFromWarehouse);
    }
    long startedAt = Clock.now();
    long endsAt = startedAt + (long) Clock.hoursToMs(plan.totalHours);
    entry.plan = plan;
    entry.status = ProductionQueueStatus.running;
    entry.startedAt = startedAt;
    entry.endsAt = endsAt;
    GameEvents.schedule(state, GameEventType.SHIP_COMPLETED, entry.id, endsAt);
  }

  /** Ereignis {@code SHIP_COMPLETED} – veraltet, wenn der Auftrag nicht mehr läuft oder ein anderes Ende trägt. */
  static void completeIfDue(GameState state, IdGenerator ids, String entryId, long at) {
    for (ShipyardQueueEntry e : state.shipyardQueue) {
      if (e.id.equals(entryId)) {
        if (e.status == ProductionQueueStatus.running && e.endsAt != null && e.endsAt == at) completeShipyardEntry(state, ids, e);
        return;
      }
    }
  }

  private static void completeShipyardEntry(GameState state, IdGenerator ids, ShipyardQueueEntry entry) {
    // Fertige Schiffe landen zunächst wie normale Ware im Lager (siehe
    // FleetCommands.transferShipsToFleet) – KEINE automatische Zuordnung zu einer Flotte.
    Warehouse.add(state, entry.colonyId, entry.shipProductTypeId, entry.quantity);
    Specializations.registerProducedChain(state, entry.colonyId, entry.plan);
    state.shipyardQueue.remove(entry);
    notifyShipDone(state, ids, entry);
    if (entry.requeueOnComplete) {
      ShipyardQueueEntry fresh = new ShipyardQueueEntry();
      fresh.id = ids.next("sy");
      fresh.colonyId = entry.colonyId;
      fresh.shipProductTypeId = entry.shipProductTypeId;
      fresh.quantity = entry.quantity;
      fresh.autoProduceMissing = entry.autoProduceMissing;
      fresh.requeueOnComplete = true;
      fresh.status = ProductionQueueStatus.queued;
      fresh.stoppedReasonCode = null;
      fresh.plan = ChainPlan.EMPTY;
      fresh.startedAt = null;
      fresh.endsAt = null;
      state.shipyardQueue.add(fresh);
    }
    tryStartNextShipyardEntry(state, ids, entry.colonyId);
  }

  /**
   * Fertige Schiffe landen unzugeordnet im Lager – ohne Meldung merkte das
   * niemand, der nicht zufällig auf der Flottenseite stand.
   */
  private static void notifyShipDone(GameState state, IdGenerator ids, ShipyardQueueEntry entry) {
    var colony = ColonyCommands.colony(state, entry.colonyId);
    if (colony == null) return;
    String shipName = de.nebula.data.ProductCatalog.find(entry.shipProductTypeId).name;
    Notifications.notify(state, ids, de.nebula.model.NotificationType.Info, Notifications.CODE_SHIP_DONE,
        (long) entry.quantity + "× " + shipName + " in \"" + colony.name
            + "\" fertiggestellt – im Lager, noch keiner Flotte zugeordnet.",
        colony.id, Notifications.colonyLink(colony.id));
  }

}
