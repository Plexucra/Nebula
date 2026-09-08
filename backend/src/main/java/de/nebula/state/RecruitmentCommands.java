package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.Colony;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.PlanetStats;
import de.nebula.model.ProductCategory;
import de.nebula.model.ProductType;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.RecruitmentQueueEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1-Portierung der "Bodentruppen"-Sektion aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 8). Rekrutierungs-Warteschlange
 * strukturell identisch zu {@link ProductionCommands}; zusätzlich
 * Crewing-Verteilung (Soldaten kommandieren Drohnen aus der Ferne, siehe
 * {@link #recalcCrewing}).
 */
public final class RecruitmentCommands {
  private RecruitmentCommands() {
  }

  private static final ChainPlan EMPTY_CHAIN_PLAN = new ChainPlan(0, List.of(), true);

  public static GroundForceGroup groundForces(GameState state, String colonyId) {
    return state.groundForceGroups.stream().filter(g -> g.colonyId.equals(colonyId)).findFirst().orElse(null);
  }

  public static List<RecruitmentQueueEntry> recruitmentQueueFor(GameState state, String colonyId) {
    return state.recruitmentQueue.stream().filter(q -> q.colonyId.equals(colonyId)).toList();
  }

  public static void queueRecruitment(GameState state, IdGenerator ids, String playerId, String colonyId,
                                       String unitProductTypeId, double quantity, boolean autoProduceMissing, boolean requeueOnComplete) {
    quantity = Math.floor(quantity); // nur ganze Einheiten (Umsetzungskonzept/25_...md)
    GameQueries.requireOwnColony(state, playerId, colonyId);
    if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
    ProductType product = ProductCatalog.find(unitProductTypeId);
    if (product.category != ProductCategory.GroundUnit) throw new CommandException("Kein Bodentruppen-Typ.");
    if (GameQueries.getBuildingLevel(state, colonyId, "b_academy") < 1) throw new CommandException("Ohne Ausbildungszentrum keine Rekrutierung möglich.");
    PlanetStats stats = ColonyCommands.colonyStats(state, colonyId);
    if (stats == null || stats.loyaltyPct <= 50) throw new CommandException("Rekrutierung erfordert eine Loyalität über 50%.");

    RecruitmentQueueEntry entry = new RecruitmentQueueEntry();
    entry.id = ids.next("rq");
    entry.colonyId = colonyId;
    entry.unitProductTypeId = unitProductTypeId;
    entry.quantity = quantity;
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = EMPTY_CHAIN_PLAN;
    entry.startedAt = null;
    entry.endsAt = null;
    state.recruitmentQueue.add(entry);
    tryStartNextRecruitmentEntry(state, ids, colonyId);
  }

  public static void resumeRecruitment(GameState state, IdGenerator ids, String playerId, String colonyId, String entryId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    RecruitmentQueueEntry entry = find(state, colonyId, entryId);
    if (entry == null || entry.status != ProductionQueueStatus.stopped) return;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    tryStartNextRecruitmentEntry(state, ids, colonyId);
  }

  public static void cancelRecruitment(GameState state, IdGenerator ids, String playerId, String colonyId, String entryId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    RecruitmentQueueEntry entry = find(state, colonyId, entryId);
    if (entry == null) return;
    creditPartialChainProgress(state, colonyId, entry, qty -> addUnitToGarrison(state, ids, colonyId, entry.unitProductTypeId, (int) qty));
    state.recruitmentQueue.remove(entry);
    tryStartNextRecruitmentEntry(state, ids, colonyId);
  }

  private static RecruitmentQueueEntry find(GameState state, String colonyId, String entryId) {
    for (RecruitmentQueueEntry e : state.recruitmentQueue) {
      if (e.id.equals(entryId) && e.colonyId.equals(colonyId)) return e;
    }
    return null;
  }

  private static void creditPartialChainProgress(GameState state, String colonyId, RecruitmentQueueEntry entry, java.util.function.DoubleConsumer creditRoot) {
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

  public static void tryStartNextRecruitmentEntry(GameState state, IdGenerator ids, String colonyId) {
    List<RecruitmentQueueEntry> queue = recruitmentQueueFor(state, colonyId);
    for (RecruitmentQueueEntry e : queue) if (e.status == ProductionQueueStatus.running) return;
    for (RecruitmentQueueEntry e : queue) {
      if (e.status == ProductionQueueStatus.queued) {
        startRecruitmentEntry(state, ids, e);
        return;
      }
    }
  }

  private static void startRecruitmentEntry(GameState state, IdGenerator ids, RecruitmentQueueEntry entry) {
    ChainPlan plan = ChainPlanner.planChain(state, entry.colonyId, entry.unitProductTypeId, entry.quantity, "b_academy");
    if (!plan.feasible && !entry.autoProduceMissing) {
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_QUEUE_STOPPED;
      Notifications.notify(state, ids, de.nebula.model.NotificationType.Problem, Notifications.CODE_QUEUE_STOPPED,
          "Rekrutierungs-Warteschlange angehalten: nicht genug Vorprodukte für \""
              + ProductCatalog.find(entry.unitProductTypeId).name + "\" vorhanden.", entry.colonyId, null);
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

  private static void completeRecruitmentEntry(GameState state, IdGenerator ids, RecruitmentQueueEntry entry) {
    addUnitToGarrison(state, ids, entry.colonyId, entry.unitProductTypeId, (int) entry.quantity);
    Specializations.registerProducedChain(state, entry.colonyId, entry.plan);
    state.recruitmentQueue.remove(entry);
    if (entry.requeueOnComplete) {
      RecruitmentQueueEntry fresh = new RecruitmentQueueEntry();
      fresh.id = ids.next("rq");
      fresh.colonyId = entry.colonyId;
      fresh.unitProductTypeId = entry.unitProductTypeId;
      fresh.quantity = entry.quantity;
      fresh.autoProduceMissing = entry.autoProduceMissing;
      fresh.requeueOnComplete = true;
      fresh.status = ProductionQueueStatus.queued;
      fresh.stoppedReasonCode = null;
      fresh.plan = EMPTY_CHAIN_PLAN;
      fresh.startedAt = null;
      fresh.endsAt = null;
      state.recruitmentQueue.add(fresh);
    }
    tryStartNextRecruitmentEntry(state, ids, entry.colonyId);
  }

  public static void processRecruitmentCompletions(GameState state, IdGenerator ids, long t) {
    List<RecruitmentQueueEntry> due = state.recruitmentQueue.stream()
        .filter(e -> e.status == ProductionQueueStatus.running && e.endsAt != null && e.endsAt <= t)
        .toList();
    for (RecruitmentQueueEntry entry : due) completeRecruitmentEntry(state, ids, entry);
  }

  private static void addUnitToGarrison(GameState state, IdGenerator ids, String colonyId, String unitProductTypeId, int count) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) return;
    GroundForceGroup group = groundForces(state, colonyId);
    if (group == null) {
      group = new GroundForceGroup();
      group.id = ids.next("gfg");
      group.ownerId = colony.ownerId;
      group.colonyId = colonyId;
      group.units = new ArrayList<>();
      state.groundForceGroups.add(group);
    }
    // Neu fertiggestellte Einheiten landen zunächst als "unzugeordnet" in
    // reserveCount – recalcCrewing() unten verteilt Soldaten und Drohnen
    // anschließend gemeinsam neu (Mechanik/05_..., §3-4).
    GroundForceUnitStack stack = null;
    for (GroundForceUnitStack u : group.units) if (u.unitProductTypeId.equals(unitProductTypeId)) stack = u;
    if (stack == null) {
      GroundForceUnitStack s = new GroundForceUnitStack();
      s.unitProductTypeId = unitProductTypeId;
      s.activeCount = 0;
      s.reserveCount = count;
      group.units.add(s);
    } else {
      stack.reserveCount += count;
    }
    // Spezialisierungs-XP wird von den Aufrufern vergeben (registerProducedChain
    // bei voller Fertigstellung, creditPartialChainProgress bei Abbruch).
    recalcCrewing(state, colonyId);
  }

  /**
   * Verteilt Soldaten proportional auf die drei Drohnenklassen und
   * bestimmt daraus, wie viele Drohnen je Klasse aktiv (kommandiert,
   * kampffähig) bzw. Reserve (unkommandiert) sind – Mechanik/05_..., §3-4.
   */
  private static void recalcCrewing(GameState state, String colonyId) {
    GroundForceGroup group = groundForces(state, colonyId);
    if (group == null) return;
    java.util.function.Function<String, Integer> totalOf = id -> {
      for (GroundForceUnitStack u : group.units) if (u.unitProductTypeId.equals(id)) return u.activeCount + u.reserveCount;
      return 0;
    };
    int totalSoldiers = totalOf.apply("p_soldier");
    int[] droneTotals = new int[GameConstants.DRONE_PRODUCT_IDS.size()];
    int totalDrones = 0;
    for (int i = 0; i < GameConstants.DRONE_PRODUCT_IDS.size(); i++) {
      droneTotals[i] = totalOf.apply(GameConstants.DRONE_PRODUCT_IDS.get(i));
      totalDrones += droneTotals[i];
    }
    int commandCapacity = totalSoldiers * GameConstants.DRONES_PER_SOLDIER;

    int[] activeDrones = new int[droneTotals.length];
    if (totalDrones == 0) {
      // bleibt 0
    } else if (commandCapacity >= totalDrones) {
      activeDrones = droneTotals.clone();
    } else {
      // gleiches Besetzungsverhältnis für alle drei Klassen (Mechanik/05_..., §4) →
      // verfügbare Kommandokapazität proportional zum Bestand jeder Klasse verteilen.
      for (int i = 0; i < droneTotals.length; i++) {
        activeDrones[i] = (int) Math.floor((double) (commandCapacity * droneTotals[i]) / totalDrones);
      }
    }
    int totalActiveDrones = 0;
    for (int a : activeDrones) totalActiveDrones += a;
    int soldiersActive = Math.min(totalSoldiers, (int) Math.ceil((double) totalActiveDrones / GameConstants.DRONES_PER_SOLDIER));

    for (GroundForceUnitStack u : group.units) {
      int droneIdx = GameConstants.DRONE_PRODUCT_IDS.indexOf(u.unitProductTypeId);
      if (droneIdx != -1) {
        u.activeCount = activeDrones[droneIdx];
        u.reserveCount = droneTotals[droneIdx] - activeDrones[droneIdx];
      } else if (u.unitProductTypeId.equals("p_soldier")) {
        u.activeCount = soldiersActive;
        u.reserveCount = totalSoldiers - soldiersActive;
      }
    }
  }
}
