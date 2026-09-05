package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.Specialization;
import de.nebula.model.WarehouseEntry;

import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * 1:1-Portierung der Produktions-Sektion (sequentielle Warteschlange) aus
 * {@code simulated-game-api.service.ts}, siehe Umsetzungskonzept/10_
 * Sequentielle_Produktionsauftraege_und_Ereignissystem.md und
 * Umsetzungskonzept/13_...md, Phase 5.
 */
public final class ProductionCommands {
  private ProductionCommands() {
  }

  private static final ChainPlan EMPTY_CHAIN_PLAN = new ChainPlan(0, List.of(), true);

  public static List<WarehouseEntry> warehouseFor(GameState state, String colonyId) {
    return state.warehouse.stream().filter(w -> w.colonyId.equals(colonyId) && w.quantity > 0).toList();
  }

  public static List<Specialization> specializationsFor(GameState state, String colonyId) {
    return state.specializations.stream().filter(s -> s.colonyId.equals(colonyId)).toList();
  }

  public static List<ProductionQueueEntry> productionQueueFor(GameState state, String colonyId) {
    return state.productionQueue.stream().filter(q -> q.colonyId.equals(colonyId)).toList();
  }

  public static void queueProduction(GameState state, IdGenerator ids, String playerId, String colonyId,
                                      String productTypeId, double quantity, boolean autoProduceMissing,
                                      boolean requeueOnComplete) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    queueProductionCore(state, ids, colonyId, productTypeId, quantity, autoProduceMissing, requeueOnComplete);
  }

  /** Ungeprüfter Kern von {@link #queueProduction} – für eine künftige NPC-KI gedacht (siehe TS-Original). */
  public static void queueProductionCore(GameState state, IdGenerator ids, String colonyId, String productTypeId,
                                          double quantity, boolean autoProduceMissing, boolean requeueOnComplete) {
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    ProductType product = ProductCatalog.find(productTypeId);
    if (product.category == ProductCategory.Ship || product.category == ProductCategory.GroundUnit) {
      throw new CommandException("Schiffe und Bodeneinheiten werden über Werft bzw. Ausbildungszentrum in Auftrag gegeben.");
    }
    if (GameQueries.getBuildingLevel(state, colonyId, "b_industry") < 1) {
      throw new CommandException("Ohne Industriekomplex ist keine Fertigung möglich.");
    }
    ProductionQueueEntry entry = new ProductionQueueEntry();
    entry.id = ids.next("pq");
    entry.colonyId = colonyId;
    entry.productTypeId = productTypeId;
    entry.quantity = quantity;
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = EMPTY_CHAIN_PLAN;
    entry.startedAt = null;
    entry.endsAt = null;
    state.productionQueue.add(entry);
    tryStartNextProductionEntry(state, ids, colonyId);
  }

  /**
   * Reine Vorschau (keine Zustandsänderung): berechnet den {@link ChainPlan}
   * für {@code quantity} Einheiten unter dem AKTUELLEN Lagerbestand, ohne
   * einen Auftrag anzulegen.
   */
  public static ChainPlan previewProductionChain(GameState state, String colonyId, String productTypeId, double quantity) {
    if (quantity <= 0) return EMPTY_CHAIN_PLAN;
    return ChainPlanner.planChain(state, colonyId, productTypeId, quantity, "b_industry");
  }

  /** "Fortsetzen"-Button: prüft einen angehaltenen Auftrag erneut und startet ihn, falls jetzt ausführbar. */
  public static void resumeProduction(GameState state, IdGenerator ids, String playerId, String colonyId, String entryId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    ProductionQueueEntry entry = find(state, colonyId, entryId);
    if (entry == null || entry.status != ProductionQueueStatus.stopped) return;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    tryStartNextProductionEntry(state, ids, colonyId);
  }

  public static void cancelProduction(GameState state, IdGenerator ids, String playerId, String colonyId, String entryId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    ProductionQueueEntry entry = find(state, colonyId, entryId);
    if (entry == null) return;
    creditPartialChainProgress(state, colonyId, entry, qty -> Warehouse.add(state, colonyId, entry.productTypeId, qty));
    state.productionQueue.remove(entry);
    tryStartNextProductionEntry(state, ids, colonyId);
  }

  private static ProductionQueueEntry find(GameState state, String colonyId, String entryId) {
    for (ProductionQueueEntry e : state.productionQueue) {
      if (e.id.equals(entryId) && e.colonyId.equals(colonyId)) return e;
    }
    return null;
  }

  /**
   * Schreibt bei Abbruch eines laufenden Auftrags einen anteiligen
   * Teilerfolg gut: pro Kettenschritt wird {@code floor(quantityToProduce ×
   * verstrichener Zeitanteil)} gutgeschrieben (Abrundung – ein zu 90%
   * fertiges Einzelmodul zählt als 0, nicht als 0,9), der bereits aus dem
   * Lager entnommene, aber nicht mehr benötigte Anteil wird zurückerstattet.
   * {@code creditRoot} bestimmt, wohin der Wurzelschritt-Anteil gebucht wird
   * (Lager bei Produktion, künftig Flotte bei Schiffen, Garnison bei
   * Rekrutierung) – alle anderen Schritte landen immer im Lager. Kein Effekt
   * bei {@code queued}/{@code stopped} (dort wurde noch nichts entnommen).
   */
  public static void creditPartialChainProgress(GameState state, String colonyId, ProductionQueueEntry entry, DoubleConsumer creditRoot) {
    if (entry.status != ProductionQueueStatus.running || entry.startedAt == null || entry.endsAt == null) return;
    double elapsedFraction = Formulas.clamp(
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
      // XP unabhängig von der (abgerundeten) Stückzahl – zeitbasiert, damit auch ein
      // abgebrochener Auftrag mit z. B. nur einer Einheit (credited rundet auf 0) die investierte Zeit nicht verliert.
      Specializations.registerProduced(state, colonyId, step.productTypeId, step.hours * elapsedFraction);
    }
  }

  // --- Produktion: sequentielle Ausführung -----------------------------------

  public static void tryStartNextProductionEntry(GameState state, IdGenerator ids, String colonyId) {
    List<ProductionQueueEntry> queue = productionQueueFor(state, colonyId);
    for (ProductionQueueEntry e : queue) if (e.status == ProductionQueueStatus.running) return;
    for (ProductionQueueEntry e : queue) {
      if (e.status == ProductionQueueStatus.queued) {
        startProductionEntry(state, ids, e);
        return;
      }
    }
  }

  private static void startProductionEntry(GameState state, IdGenerator ids, ProductionQueueEntry entry) {
    ChainPlan plan = ChainPlanner.planChain(state, entry.colonyId, entry.productTypeId, entry.quantity, "b_industry");
    if (!plan.feasible && !entry.autoProduceMissing) {
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_QUEUE_STOPPED;
      Notifications.notify(state, ids, de.nebula.model.NotificationType.Problem, Notifications.CODE_QUEUE_STOPPED,
          "Produktionswarteschlange angehalten: nicht genug Vorprodukte für \""
              + ProductCatalog.find(entry.productTypeId).name + "\" vorhanden.", entry.colonyId, null);
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

  public static void completeProductionEntry(GameState state, IdGenerator ids, ProductionQueueEntry entry) {
    Warehouse.add(state, entry.colonyId, entry.productTypeId, entry.quantity);
    Specializations.registerProducedChain(state, entry.colonyId, entry.plan);
    state.productionQueue.remove(entry);
    if (entry.requeueOnComplete) {
      ProductionQueueEntry fresh = new ProductionQueueEntry();
      fresh.id = ids.next("pq");
      fresh.colonyId = entry.colonyId;
      fresh.productTypeId = entry.productTypeId;
      fresh.quantity = entry.quantity;
      fresh.autoProduceMissing = entry.autoProduceMissing;
      fresh.requeueOnComplete = true;
      fresh.status = ProductionQueueStatus.queued;
      fresh.stoppedReasonCode = null;
      fresh.plan = EMPTY_CHAIN_PLAN;
      fresh.startedAt = null;
      fresh.endsAt = null;
      state.productionQueue.add(fresh);
    }
    tryStartNextProductionEntry(state, ids, entry.colonyId);
  }

  /** Ereignisbasiert: einziger Zeitvergleich je laufendem Auftrag statt einer Pro-Einheit-Schleife. */
  public static void processProductionQueue(GameState state, IdGenerator ids, long t) {
    List<ProductionQueueEntry> due = state.productionQueue.stream()
        .filter(e -> e.status == ProductionQueueStatus.running && e.endsAt != null && e.endsAt <= t)
        .toList();
    for (ProductionQueueEntry entry : due) completeProductionEntry(state, ids, entry);
  }
}
