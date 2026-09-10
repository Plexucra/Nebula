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

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 1:1-Portierung der Produktions-Sektion (sequentielle Warteschlange) aus
 * {@code simulated-game-api.service.ts}, siehe Umsetzungskonzept/10_
 * Sequentielle_Produktionsauftraege_und_Ereignissystem.md und
 * Umsetzungskonzept/13_...md, Phase 5.
 */
public final class ProductionCommands {
  private ProductionCommands() {
  }

  public static List<WarehouseEntry> warehouseFor(GameState state, String colonyId) {
    return state.warehouse.stream().filter(w -> w.colonyId.equals(colonyId) && w.quantity > 0).toList();
  }

  public static List<Specialization> specializationsFor(GameState state, String colonyId) {
    return state.specializations.stream().filter(s -> s.colonyId.equals(colonyId)).toList();
  }

  public static List<ProductionQueueEntry> productionQueueFor(GameState state, String colonyId) {
    return state.productionQueue.stream().filter(q -> q.colonyId.equals(colonyId)).toList();
  }

  /**
   * Verschiebt einen wartenden Auftrag in der Warteschlange um eine Position.
   *
   * <p>Pro Kolonie läuft immer nur EIN Auftrag – die Reihenfolge entscheidet
   * damit darüber, was zuerst fertig wird, und ist eine der wichtigsten
   * Stellschrauben des Spiels. Sie ließ sich bislang überhaupt nicht ändern:
   * Wer versehentlich einen langen Auftrag vor einen dringenden setzte, musste
   * abbrechen und neu einreihen.</p>
   *
   * <p>Der LAUFENDE Auftrag bleibt an seiner Stelle – er hat bereits Rohstoffe
   * gebunden und eine Endzeit.</p>
   */
  public static void moveProductionEntry(GameState state, String playerId, String colonyId, String entryId, int direction) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    List<ProductionQueueEntry> queue = new ArrayList<>(productionQueueFor(state, colonyId));
    int index = -1;
    for (int i = 0; i < queue.size(); i++) if (queue.get(i).id.equals(entryId)) index = i;
    if (index < 0) throw new CommandException("Unbekannter Auftrag.");
    ProductionQueueEntry entry = queue.get(index);
    if (entry.status == ProductionQueueStatus.running) {
      throw new CommandException("Der laufende Auftrag lässt sich nicht verschieben – erst abbrechen.");
    }
    int target = index + (direction < 0 ? -1 : 1);
    if (target < 0 || target >= queue.size()) return;
    if (queue.get(target).status == ProductionQueueStatus.running) {
      throw new CommandException("Vor den laufenden Auftrag lässt sich nichts schieben – erst abbrechen.");
    }
    // Die Gesamtliste enthält die Aufträge ALLER Kolonien; getauscht werden
    // deshalb die Positionen der beiden Einträge in genau dieser Liste.
    int a = state.productionQueue.indexOf(entry);
    int b = state.productionQueue.indexOf(queue.get(target));
    if (a < 0 || b < 0) return;
    state.productionQueue.set(a, queue.get(target));
    state.productionQueue.set(b, entry);
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
    LinkedHashMap<String, Double> demand = new LinkedHashMap<>();
    demand.put(productTypeId, quantity);
    queueProductionBundleCore(state, ids, colonyId, demand, autoProduceMissing, requeueOnComplete);
  }

  /**
   * Wie {@link #queueProduction}, aber für MEHRERE direkt angeforderte Wurzelprodukte in
   * EINEM Auftrag (siehe {@link ProductionQueueEntry#bundledProducts}) – der Fix dafür, dass
   * ein Bauauftrag mehrere Baustoffe direkt zugleich braucht, von denen einer Vorprodukt eines
   * anderen ist (z. B. {@code p_leitermetall} und {@code p_leiterbuendel}, siehe TODO.md):
   * getrennte Einzelaufträge würden sich sonst gegenseitig den Lagerbestand wegnehmen.
   */
  public static void queueProductionBundle(GameState state, IdGenerator ids, String playerId, String colonyId,
                                            Map<String, Double> products, boolean autoProduceMissing,
                                            boolean requeueOnComplete) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    queueProductionBundleCore(state, ids, colonyId, products, autoProduceMissing, requeueOnComplete);
  }

  /** Ungeprüfter Kern von {@link #queueProductionBundle} – für eine künftige NPC-KI gedacht. */
  public static void queueProductionBundleCore(GameState state, IdGenerator ids, String colonyId,
                                                Map<String, Double> products, boolean autoProduceMissing,
                                                boolean requeueOnComplete) {
    if (products.isEmpty()) throw new CommandException("Mindestens ein Produkt erforderlich.");
    LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : products.entrySet()) {
      // Nur ganze Stücke (Umsetzungskonzept/25_...md) – der Kettenplaner rechnet mit
      // ganzzahligen Rezeptmengen weiter, damit im Lager nie ein Bruchteil landet.
      double quantity = Math.floor(e.getValue());
      if (quantity <= 0) throw new CommandException("Menge muss größer als 0 sein.");
      ProductType product = ProductCatalog.find(e.getKey());
      if (product.category == ProductCategory.Ship || product.category == ProductCategory.GroundUnit) {
        throw new CommandException("Schiffe und Bodeneinheiten werden über Werft bzw. Ausbildungszentrum in Auftrag gegeben.");
      }
      normalized.put(e.getKey(), quantity);
    }
    if (GameQueries.getBuildingLevel(state, colonyId, "b_industry") < 1) {
      throw new CommandException("Ohne Industriekomplex ist keine Fertigung möglich.");
    }
    Map.Entry<String, Double> first = normalized.entrySet().iterator().next();
    ProductionQueueEntry entry = new ProductionQueueEntry();
    entry.id = ids.next("pq");
    entry.colonyId = colonyId;
    entry.productTypeId = first.getKey();
    entry.quantity = first.getValue();
    entry.bundledProducts = normalized.size() > 1 ? normalized : null;
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = ChainPlan.EMPTY;
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
    if (quantity <= 0) return ChainPlan.EMPTY;
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
    creditPartialChainProgress(state, colonyId, entry.status, entry.startedAt, entry.endsAt, entry.plan,
        (pid, qty) -> Warehouse.add(state, colonyId, pid, qty));
    state.productionQueue.remove(entry);
    GameEvents.cancel(state, GameEventType.PRODUCTION_COMPLETED, entry.id);
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
   * {@code creditRoot} bestimmt, wohin ein Wurzelschritt-Anteil gebucht wird (Produkt-Id
   * mitgegeben, da ein Auftrag mehrere Wurzelprodukte bündeln kann, siehe
   * {@link ProductionQueueEntry#bundledProducts}; Lager bei Produktion und Werft, Garnison
   * bei Rekrutierung) – alle anderen Schritte landen immer im Lager. Kein Effekt bei
   * {@code queued}/{@code stopped} (dort wurde noch nichts entnommen).
   *
   * <p>EINE Fassung für alle drei Warteschlangen (Produktion, Werft, Ausbildung) –
   * vorher stand dieselbe Rechnung dreimal da, mit zwei verschiedenen Arten, den
   * Wurzelschritt zu erkennen.</p>
   */
  static void creditPartialChainProgress(GameState state, String colonyId, ProductionQueueStatus status,
                                         Long startedAt, Long endsAt, ChainPlan plan,
                                         BiConsumer<String, Double> creditRoot) {
    if (status != ProductionQueueStatus.running || startedAt == null || endsAt == null) return;
    double elapsedFraction = Formulas.clamp((double) (Clock.now() - startedAt) / Math.max(endsAt - startedAt, 1), 0, 1);
    for (ChainPlanStep step : plan.steps) {
      double refund = Math.floor(step.quantityFromWarehouse * (1 - elapsedFraction));
      if (refund > 0) Warehouse.add(state, colonyId, step.productTypeId, refund);
      double credited = Math.floor(step.quantityToProduce * elapsedFraction);
      if (credited > 0) {
        if (step.isRoot) creditRoot.accept(step.productTypeId, credited); else Warehouse.add(state, colonyId, step.productTypeId, credited);
      }
      // XP unabhängig von der (abgerundeten) Stückzahl – zeitbasiert, damit auch ein
      // abgebrochener Auftrag mit z. B. nur einer Einheit (credited rundet auf 0) die investierte Zeit nicht verliert.
      Specializations.registerProduced(state, colonyId, step.productTypeId, step.hours * elapsedFraction);
    }
  }

  // --- Produktion: sequentielle Ausführung -----------------------------------

  public static void tryStartNextProductionEntry(GameState state, IdGenerator ids, String colonyId) {
    // Ohne Industriekomplex bleiben wartende Aufträge einfach "queued" – kein
    // Fehler, kein stopped-Status: sie starten von selbst, sobald das Gebäude
    // fertig ist (siehe GameTick.processBuildingCompletions). Nötig seit dem
    // Minimalstart aus Umsetzungskonzept/17_...md, bei dem die Start-
    // Daueraufträge vor dem ersten Industriekomplex eingereiht werden.
    if (GameQueries.getBuildingLevel(state, colonyId, "b_industry") < 1) return;
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
    ChainPlan plan = entry.bundledProducts != null
        ? ChainPlanner.planChain(state, entry.colonyId, entry.bundledProducts, "b_industry")
        : ChainPlanner.planChain(state, entry.colonyId, entry.productTypeId, entry.quantity, "b_industry");
    if (!plan.feasible && !entry.autoProduceMissing) {
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_QUEUE_STOPPED;
      String label = entry.bundledProducts != null
          ? entry.bundledProducts.keySet().stream().map(id -> ProductCatalog.find(id).name).collect(java.util.stream.Collectors.joining(", "))
          : ProductCatalog.find(entry.productTypeId).name;
      Notifications.notify(state, ids, de.nebula.model.NotificationType.Problem, Notifications.CODE_QUEUE_STOPPED,
          "Produktionswarteschlange angehalten: nicht genug Vorprodukte für \"" + label + "\" vorhanden.", entry.colonyId, null);
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
    GameEvents.schedule(state, GameEventType.PRODUCTION_COMPLETED, entry.id, endsAt);
  }

  /** Ereignis {@code PRODUCTION_COMPLETED} – veraltet, wenn der Auftrag nicht mehr läuft oder ein anderes Ende trägt. */
  static void completeIfDue(GameState state, IdGenerator ids, String entryId, long at) {
    for (ProductionQueueEntry e : state.productionQueue) {
      if (e.id.equals(entryId)) {
        if (e.status == ProductionQueueStatus.running && e.endsAt != null && e.endsAt == at) completeProductionEntry(state, ids, e);
        return;
      }
    }
  }

  public static void completeProductionEntry(GameState state, IdGenerator ids, ProductionQueueEntry entry) {
    if (entry.bundledProducts != null) {
      for (Map.Entry<String, Double> e : entry.bundledProducts.entrySet()) {
        Warehouse.add(state, entry.colonyId, e.getKey(), e.getValue());
        Economy.emergencyPurchase(state, ids, entry.colonyId, e.getKey());
      }
    } else {
      Warehouse.add(state, entry.colonyId, entry.productTypeId, entry.quantity);
      // Das Einlagern hat eine schlafende Dauerorder nachgefüllt (Warehouse.addRaw);
      // eine knappe Bevölkerung kauft daraus sofort (Umsetzungskonzept/36).
      Economy.emergencyPurchase(state, ids, entry.colonyId, entry.productTypeId);
    }
    Specializations.registerProducedChain(state, entry.colonyId, entry.plan);
    state.productionQueue.remove(entry);
    if (entry.requeueOnComplete) {
      ProductionQueueEntry fresh = new ProductionQueueEntry();
      fresh.id = ids.next("pq");
      fresh.colonyId = entry.colonyId;
      fresh.productTypeId = entry.productTypeId;
      fresh.quantity = entry.quantity;
      fresh.bundledProducts = entry.bundledProducts;
      fresh.autoProduceMissing = entry.autoProduceMissing;
      fresh.requeueOnComplete = true;
      fresh.status = ProductionQueueStatus.queued;
      fresh.stoppedReasonCode = null;
      fresh.plan = ChainPlan.EMPTY;
      fresh.startedAt = null;
      fresh.endsAt = null;
      state.productionQueue.add(fresh);
    }
    tryStartNextProductionEntry(state, ids, entry.colonyId);
  }

}
