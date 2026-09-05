package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;
import de.nebula.model.ShipyardQueueEntry;
import de.nebula.model.ProductionQueueStatus;

import java.util.List;

/**
 * 1:1-Portierung der Werft-Sektion (sequentielle Warteschlange, strukturell
 * identisch zu {@link ProductionCommands}) aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 7).
 */
public final class ShipyardCommands {
  private ShipyardCommands() {
  }

  private static final ChainPlan EMPTY_CHAIN_PLAN = new ChainPlan(0, List.of(), true);

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

    ShipyardQueueEntry entry = new ShipyardQueueEntry();
    entry.id = ids.next("sy");
    entry.colonyId = colonyId;
    entry.shipProductTypeId = shipProductTypeId;
    entry.quantity = quantity;
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = EMPTY_CHAIN_PLAN;
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
    creditPartialChainProgress(state, colonyId, entry, qty -> Warehouse.add(state, colonyId, entry.shipProductTypeId, qty));
    state.shipyardQueue.remove(entry);
    tryStartNextShipyardEntry(state, ids, colonyId);
  }

  private static ShipyardQueueEntry find(GameState state, String colonyId, String entryId) {
    for (ShipyardQueueEntry e : state.shipyardQueue) {
      if (e.id.equals(entryId) && e.colonyId.equals(colonyId)) return e;
    }
    return null;
  }

  private static void creditPartialChainProgress(GameState state, String colonyId, ShipyardQueueEntry entry, java.util.function.DoubleConsumer creditRoot) {
    if (entry.status != ProductionQueueStatus.running || entry.startedAt == null || entry.endsAt == null) return;
    double elapsedFraction = de.nebula.engine.Formulas.clamp(
        (double) (Clock.now() - entry.startedAt) / Math.max(entry.endsAt - entry.startedAt, 1), 0, 1);
    List<ChainPlanStep> steps = entry.plan.steps;
    for (int i = 0; i < steps.size(); i++) {
      ChainPlanStep step = steps.get(i);
      boolean isRoot = i == steps.size() - 1;
      double refund = Math.floor(step.quantityFromWarehouse * (1 - elapsedFraction));
      if (refund > 0) Warehouse.add(state, colonyId, step.productTypeId, refund);
      double credited = Math.floor(step.quantityToProduce * elapsedFraction);
      if (credited > 0) {
        if (isRoot) creditRoot.accept(credited); else Warehouse.add(state, colonyId, step.productTypeId, credited);
      }
      Specializations.registerProduced(state, colonyId, step.productTypeId, step.hours * elapsedFraction);
    }
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
  }

  private static void completeShipyardEntry(GameState state, IdGenerator ids, ShipyardQueueEntry entry) {
    // Fertige Schiffe landen zunächst wie normale Ware im Lager (siehe
    // FleetCommands.transferShipsToFleet) – KEINE automatische Zuordnung zu einer Flotte.
    Warehouse.add(state, entry.colonyId, entry.shipProductTypeId, entry.quantity);
    Specializations.registerProducedChain(state, entry.colonyId, entry.plan);
    state.shipyardQueue.remove(entry);
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
      fresh.plan = EMPTY_CHAIN_PLAN;
      fresh.startedAt = null;
      fresh.endsAt = null;
      state.shipyardQueue.add(fresh);
    }
    tryStartNextShipyardEntry(state, ids, entry.colonyId);
  }

  public static void processShipyardCompletions(GameState state, IdGenerator ids, long t) {
    List<ShipyardQueueEntry> due = state.shipyardQueue.stream()
        .filter(e -> e.status == ProductionQueueStatus.running && e.endsAt != null && e.endsAt <= t)
        .toList();
    for (ShipyardQueueEntry entry : due) completeShipyardEntry(state, ids, entry);
  }
}
