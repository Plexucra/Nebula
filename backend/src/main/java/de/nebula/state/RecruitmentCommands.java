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

  /** Der Verband IN einer Kolonie – Verbände an Bord einer Flotte haben {@code colonyId == null} (siehe {@link GroundForceGroup}). */
  public static GroundForceGroup groundForces(GameState state, String colonyId) {
    return state.groundForceGroups.stream()
        .filter(g -> colonyId.equals(g.colonyId)).findFirst().orElse(null);
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
    if (stats == null || stats.loyaltyPct <= GameConstants.RECRUIT_MIN_LOYALTY_PCT) {
      throw new CommandException("Rekrutierung erfordert eine Loyalität über " + (long) GameConstants.RECRUIT_MIN_LOYALTY_PCT + "%.");
    }

    RecruitmentQueueEntry entry = new RecruitmentQueueEntry();
    entry.id = ids.next("rq");
    entry.colonyId = colonyId;
    entry.unitProductTypeId = unitProductTypeId;
    entry.quantity = quantity;
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = ChainPlan.EMPTY;
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
    ProductionCommands.creditPartialChainProgress(state, colonyId, entry.status, entry.startedAt, entry.endsAt, entry.plan,
        (pid, qty) -> addUnitToGarrison(state, ids, colonyId, pid, (int) (double) qty));
    state.recruitmentQueue.remove(entry);
    GameEvents.cancel(state, GameEventType.RECRUITMENT_COMPLETED, entry.id);
    tryStartNextRecruitmentEntry(state, ids, colonyId);
  }

  private static RecruitmentQueueEntry find(GameState state, String colonyId, String entryId) {
    for (RecruitmentQueueEntry e : state.recruitmentQueue) {
      if (e.id.equals(entryId) && e.colonyId.equals(colonyId)) return e;
    }
    return null;
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
    // Löhne je Arbeitsstunde (Umsetzungskonzept/38, Teil B) – auch die Ausbildung zahlt.
    String label = (long) entry.quantity + " × " + ProductCatalog.find(entry.unitProductTypeId).name;
    if (!Wages.affordable(state, entry.colonyId, plan)) {
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_WAGES_UNPAID;
      Wages.notifyUnpaid(state, ids, entry.colonyId, plan, label, "Rekrutierungs-Warteschlange");
      return;
    }
    for (ChainPlanStep step : plan.steps) {
      if (step.quantityFromWarehouse > 0) Warehouse.add(state, entry.colonyId, step.productTypeId, -step.quantityFromWarehouse);
    }
    Wages.pay(state, ids, entry.colonyId, plan, label);
    long startedAt = Clock.now();
    long endsAt = startedAt + (long) Clock.hoursToMs(plan.totalHours);
    entry.plan = plan;
    entry.status = ProductionQueueStatus.running;
    entry.startedAt = startedAt;
    entry.endsAt = endsAt;
    GameEvents.schedule(state, GameEventType.RECRUITMENT_COMPLETED, entry.id, endsAt);
  }

  /** Ereignis {@code RECRUITMENT_COMPLETED} – veraltet, wenn der Auftrag nicht mehr läuft oder ein anderes Ende trägt. */
  static void completeIfDue(GameState state, IdGenerator ids, String entryId, long at) {
    for (RecruitmentQueueEntry e : state.recruitmentQueue) {
      if (e.id.equals(entryId)) {
        if (e.status == ProductionQueueStatus.running && e.endsAt != null && e.endsAt == at) completeRecruitmentEntry(state, ids, e);
        return;
      }
    }
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
      fresh.plan = ChainPlan.EMPTY;
      fresh.startedAt = null;
      fresh.endsAt = null;
      state.recruitmentQueue.add(fresh);
    }
    tryStartNextRecruitmentEntry(state, ids, entry.colonyId);
  }

  /** Soldaten aus einem Mannschaftstransporter in die Garnison übernehmen ({@code TroopTransportCommands.disembarkSoldiers}). */
  static void addSoldiersToGarrison(GameState state, IdGenerator ids, String colonyId, int count) {
    addUnitToGarrison(state, ids, colonyId, GameConstants.SOLDIER_PRODUCT_ID, count);
  }

  /** Eingelagerte Drohnen zurück in die Garnison stellen ({@code TroopTransportCommands.deployDrones}). */
  static void addDronesToGarrison(GameState state, IdGenerator ids, String colonyId, String droneProductTypeId, int count) {
    addUnitToGarrison(state, ids, colonyId, droneProductTypeId, count);
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

  /** Wie {@link #recalcCrewing(GroundForceGroup)}, für den Verband IN einer Kolonie. */
  static void recalcCrewing(GameState state, String colonyId) {
    recalcCrewing(groundForces(state, colonyId));
  }

  /**
   * Verteilt Soldaten proportional auf die drei Drohnenklassen und
   * bestimmt daraus, wie viele Drohnen je Klasse aktiv (kommandiert,
   * kampffähig) bzw. Reserve (unkommandiert) sind – Mechanik/05_..., §3-4.
   *
   * <p>Bewusst auf dem VERBAND statt auf der Kolonie definiert: dieselbe
   * Aktivierung gilt für einen gelandeten Verband auf der Planetenoberfläche
   * ({@code LandingCommands.land}) und für die Nachaktivierung mitten im
   * Bodengefecht ({@code GroundBattleCommands}, §4: "treffen zusätzliche
   * Soldaten ein, können sie Reserve-Waffenträger aktivieren") – eine
   * Garnison ist nur der häufigste Fall davon, nicht der einzige.</p>
   */
  static void recalcCrewing(GroundForceGroup group) {
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
