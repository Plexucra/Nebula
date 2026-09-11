package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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

  /**
   * @param raiseToMinimum {@code true}: ein Auftrag unter der Mindestdauer
   *     ({@link GameConstants#MIN_PRODUCTION_ORDER_GAME_HOURS}) wird auf die Mindestmenge angehoben
   *     statt abgelehnt – für Aufrufer ohne Mensch davor (Bots).
   */
  public static void queueProduction(GameState state, IdGenerator ids, String playerId, String colonyId,
                                      String productTypeId, double quantity, boolean autoProduceMissing,
                                      boolean requeueOnComplete, boolean raiseToMinimum) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    queueProductionCore(state, ids, colonyId, productTypeId, quantity, autoProduceMissing, requeueOnComplete, raiseToMinimum);
  }

  /** Ungeprüfter Kern von {@link #queueProduction} – für eine künftige NPC-KI gedacht (siehe TS-Original). */
  public static void queueProductionCore(GameState state, IdGenerator ids, String colonyId, String productTypeId,
                                          double quantity, boolean autoProduceMissing, boolean requeueOnComplete,
                                          boolean raiseToMinimum) {
    LinkedHashMap<String, Double> demand = new LinkedHashMap<>();
    demand.put(productTypeId, quantity);
    queueProductionBundleCore(state, ids, colonyId, demand, autoProduceMissing, requeueOnComplete, raiseToMinimum);
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
                                            boolean requeueOnComplete, boolean raiseToMinimum) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    queueProductionBundleCore(state, ids, colonyId, products, autoProduceMissing, requeueOnComplete, raiseToMinimum);
  }

  /** Wie {@link #queueProductionBundleCore(GameState, IdGenerator, String, Map, boolean, boolean, boolean)}, ohne Anheben. */
  public static void queueProductionBundleCore(GameState state, IdGenerator ids, String colonyId,
                                                Map<String, Double> products, boolean autoProduceMissing,
                                                boolean requeueOnComplete) {
    queueProductionBundleCore(state, ids, colonyId, products, autoProduceMissing, requeueOnComplete, false);
  }

  /**
   * Ungeprüfter Kern von {@link #queueProductionBundle} – für eine künftige NPC-KI gedacht.
   *
   * <p>Mindestdauer (TODO 11.9.2026): Aufträge, die kürzer als
   * {@link GameConstants#MIN_PRODUCTION_ORDER_GAME_HOURS} liefen, werden abgelehnt – die Meldung
   * nennt die Mindestmenge. Grund ist die Flut von Fertigstellungsereignissen, die eine Serie
   * winziger Aufträge (vor allem mit „Nach Erfolg erneut einreihen") auslöst. Mit
   * {@code raiseToMinimum} wird der Auftrag stattdessen angehoben, und zwar mit Puffer auf
   * {@link #SYSTEM_RAISE_HEADROOM} × Mindestdauer.</p>
   *
   * @return die tatsächlich eingereihten Mengen je Produkt
   */
  public static Map<String, Double> queueProductionBundleCore(GameState state, IdGenerator ids, String colonyId,
                                                              Map<String, Double> products, boolean autoProduceMissing,
                                                              boolean requeueOnComplete, boolean raiseToMinimum) {
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
    Map<String, Double> order = normalized;
    if (raiseToMinimum) {
      order = raisedWithHeadroom(state, colonyId, normalized);
    } else {
      double hours = ChainPlanner.planChain(state, colonyId, normalized, "b_industry").totalHours;
      if (hours < GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS) {
        throw new CommandException(tooSmallMessage(normalized, hours, raisedToMinimum(state, colonyId, normalized)));
      }
    }
    ProductionQueueEntry entry = new ProductionQueueEntry();
    entry.id = ids.next("pq");
    entry.colonyId = colonyId;
    applyQuantities(entry, order, order.size() > 1);
    entry.autoProduceMissing = autoProduceMissing;
    entry.requeueOnComplete = requeueOnComplete;
    entry.status = ProductionQueueStatus.queued;
    entry.stoppedReasonCode = null;
    entry.plan = ChainPlan.EMPTY;
    entry.startedAt = null;
    entry.endsAt = null;
    state.productionQueue.add(entry);
    tryStartNextProductionEntry(state, ids, colonyId);
    return order;
  }

  // --- Mindestdauer ------------------------------------------------------------

  /**
   * Puffer für Aufträge, die das System selbst anhebt (Startaufträge, Neu-Einreihen eines
   * Dauerauftrags, „Fehlende Baustoffe produzieren", Bots mit {@code raiseToMinimum}): doppelte
   * Mindestdauer. Genau auf die Mindestmenge angehoben, fielen wartende Startaufträge im
   * Browsertest vor ihrem Start schon wieder darunter – die Kolonie wächst, die Spezialisierung
   * steigt, jede Fertigung wird schneller – und ein neuer Kommandant fand zwei gestoppte
   * Daueraufträge vor.
   */
  static final double SYSTEM_RAISE_HEADROOM = 2;

  /** Kleinste Stückzahl eines Einzelauftrags, die die Mindestdauer erreicht – für Vorschau und Bots. */
  public static double minimumProductionQuantity(GameState state, String colonyId, String productTypeId) {
    ProductCatalog.find(productTypeId);
    return raisedToMinimum(state, colonyId, Map.of(productTypeId, 1.0)).get(productTypeId);
  }

  /**
   * Kleinste Mengen, mit denen ein Auftrag die Mindestdauer
   * {@link GameConstants#MIN_PRODUCTION_ORDER_GAME_HOURS} erreicht: alle Produkte mit DEMSELBEN
   * Faktor hochskaliert (ein Bündel bleibt im Verhältnis), ganze Stücke. Unverändert, wenn der
   * Auftrag schon lang genug ist.
   *
   * <p>Bisektion über den Faktor. Die Dauer wächst monoton mit der Menge (der Lagerabzug ist
   * durch den Bestand gedeckelt), und beim Faktor {@code Mindestdauer / Stunden der
   * Wurzelschritte} reichen schon die Wurzelschritte allein – die deckt der Planer nie aus dem
   * Lager, ihre Dauer wächst also linear mit.</p>
   */
  public static Map<String, Double> raisedToMinimum(GameState state, String colonyId, Map<String, Double> demand) {
    return raisedTo(state, colonyId, demand, GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS);
  }

  /** Wie {@link #raisedToMinimum}, aber auf {@link #SYSTEM_RAISE_HEADROOM} × Mindestdauer – für Aufträge, die das System anhebt. */
  static Map<String, Double> raisedWithHeadroom(GameState state, String colonyId, Map<String, Double> demand) {
    return raisedTo(state, colonyId, demand, GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS * SYSTEM_RAISE_HEADROOM);
  }

  private static Map<String, Double> raisedTo(GameState state, String colonyId, Map<String, Double> demand, double min) {
    ChainPlan plan = ChainPlanner.planChain(state, colonyId, demand, "b_industry");
    if (plan.totalHours >= min) return demand;
    double rootHours = 0;
    for (ChainPlanStep s : plan.steps) if (s.isRoot) rootHours += s.hours;
    if (rootHours <= 0) return demand;
    double lo = 1;
    double hi = Math.max(1, min / rootHours) * (1 + 1e-9);
    for (int i = 0; i < 60 && hi - lo > 1e-9 * hi; i++) {
      double mid = (lo + hi) / 2;
      if (ChainPlanner.planChain(state, colonyId, scaled(demand, mid), "b_industry").totalHours >= min) hi = mid;
      else lo = mid;
    }
    return scaled(demand, hi);
  }

  private static Map<String, Double> scaled(Map<String, Double> demand, double factor) {
    LinkedHashMap<String, Double> out = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : demand.entrySet()) out.put(e.getKey(), Math.ceil(e.getValue() * factor));
    return out;
  }

  /** Die Wurzelprodukte eines Auftrags samt Menge – Einzelauftrag oder Bündel. */
  private static Map<String, Double> demandOf(ProductionQueueEntry entry) {
    if (entry.bundledProducts != null) return entry.bundledProducts;
    LinkedHashMap<String, Double> demand = new LinkedHashMap<>();
    demand.put(entry.productTypeId, entry.quantity);
    return demand;
  }

  private static void applyQuantities(ProductionQueueEntry entry, Map<String, Double> quantities, boolean bundled) {
    Map.Entry<String, Double> first = quantities.entrySet().iterator().next();
    entry.productTypeId = first.getKey();
    entry.quantity = first.getValue();
    entry.bundledProducts = bundled ? quantities : null;
  }

  /**
   * Hebt einen Auftrag, den das System selbst anlegt (Startaufträge aus dem WorldSeed, die am
   * Befehl vorbei in die Warteschlange kommen), auf die Mindestmenge an.
   */
  public static void raiseToMinimum(GameState state, ProductionQueueEntry entry) {
    applyQuantities(entry, raisedWithHeadroom(state, entry.colonyId, demandOf(entry)), entry.bundledProducts != null);
  }

  private static String quantityLabel(Map<String, Double> demand) {
    return demand.entrySet().stream()
        .map(e -> (long) (double) e.getValue() + " × " + ProductCatalog.find(e.getKey()).name)
        .collect(Collectors.joining(", "));
  }

  static String tooSmallMessage(Map<String, Double> demand, double hours, Map<String, Double> minimum) {
    String need = minimum.size() == 1
        ? "Mindestens " + (long) (double) minimum.values().iterator().next() + " Stück einreihen."
        : "Mindestmengen für dieses Bündel: " + quantityLabel(minimum) + ".";
    return "Auftrag zu klein: " + quantityLabel(demand) + " wäre nach "
        + String.format(Locale.GERMAN, "%.1f", hours * 60) + " Spielminuten fertig. Aufträge unter "
        + String.format(Locale.GERMAN, "%.0f", GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS * 60)
        + " Spielminuten nimmt der Industriekomplex nicht an – eine Serie von Kleinstaufträgen rüstet die "
        + "Anlagen für jedes Los neu und ist ineffizient, größere Lose fertigen dieselbe Menge ohne Leerlauf. " + need;
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
    if (entry.stoppedReasonCode != null && entry.stoppedReasonCode == Notifications.CODE_ORDER_TOO_SMALL) {
      // Ein zu kleiner Auftrag wird durch Fortsetzen nicht größer – Grund nennen statt still wieder zu stoppen.
      Map<String, Double> demand = demandOf(entry);
      double hours = ChainPlanner.planChain(state, colonyId, demand, "b_industry").totalHours;
      if (hours < GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS) {
        throw new CommandException(tooSmallMessage(demand, hours, raisedToMinimum(state, colonyId, demand))
            + " Den Auftrag abbrechen und mit größerer Menge neu einreihen.");
      }
    }
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
      if (e.status != ProductionQueueStatus.queued) continue;
      if (startProductionEntry(state, ids, e)) return;
      // Fehlende Vorprodukte halten die Warteschlange an wie bisher. Ein zu kleiner Auftrag
      // wird durch Warten dagegen nicht größer – dann darf der nächste ran.
      if (e.stoppedReasonCode == null || e.stoppedReasonCode != Notifications.CODE_ORDER_TOO_SMALL) return;
    }
  }

  /**
   * @return {@code true}, wenn der Auftrag läuft; {@code false}, wenn er gestoppt wurde
   *     (Vorprodukte fehlen oder er ist inzwischen kürzer als die Mindestdauer – etwa weil der
   *     Industriekomplex seit dem Einreihen ausgebaut wurde)
   */
  private static boolean startProductionEntry(GameState state, IdGenerator ids, ProductionQueueEntry entry) {
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
      return false;
    }
    if (plan.totalHours < GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS) {
      Map<String, Double> demand = demandOf(entry);
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_ORDER_TOO_SMALL;
      Notifications.notify(state, ids, de.nebula.model.NotificationType.Problem, Notifications.CODE_ORDER_TOO_SMALL,
          "Produktionsauftrag gestoppt. " + tooSmallMessage(demand, plan.totalHours, raisedToMinimum(state, entry.colonyId, demand))
              + " Den gestoppten Auftrag abbrechen und neu einreihen.", entry.colonyId, null);
      return false;
    }
    // Löhne je Arbeitsstunde (Umsetzungskonzept/38, Teil B): ohne Guthaben startet nichts –
    // geprüft VOR dem Lagerabzug, gebucht danach.
    String label = quantityLabel(demandOf(entry));
    if (!Wages.affordable(state, entry.colonyId, plan)) {
      entry.plan = plan;
      entry.status = ProductionQueueStatus.stopped;
      entry.stoppedReasonCode = Notifications.CODE_WAGES_UNPAID;
      Wages.notifyUnpaid(state, ids, entry.colonyId, plan, label, "Produktionswarteschlange");
      return false;
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
    GameEvents.schedule(state, GameEventType.PRODUCTION_COMPLETED, entry.id, endsAt);
    return true;
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
        MarketCommands.matchColonyPost(state, ids, entry.colonyId, e.getKey());
      }
    } else {
      Warehouse.add(state, entry.colonyId, entry.productTypeId, entry.quantity);
      // Das Einlagern hat eine schlafende Dauerorder nachgefüllt (Warehouse.addRaw);
      // das stehende Gebot der Bevölkerung kreuzt damit sofort (Umsetzungskonzept/38).
      MarketCommands.matchColonyPost(state, ids, entry.colonyId, entry.productTypeId);
    }
    Specializations.registerProducedChain(state, entry.colonyId, entry.plan);
    state.productionQueue.remove(entry);
    if (entry.requeueOnComplete) {
      ProductionQueueEntry fresh = new ProductionQueueEntry();
      fresh.id = ids.next("pq");
      fresh.colonyId = entry.colonyId;
      // Der Dauerauftrag wächst mit, wenn er inzwischen unter die Mindestdauer fiele (Industrie
      // ausgebaut, Spezialisierung gestiegen) – sonst stoppte er nach jedem Ausbau beim nächsten
      // Umlauf und müsste von Hand neu eingereiht werden.
      applyQuantities(fresh, raisedWithHeadroom(state, entry.colonyId, demandOf(entry)), entry.bundledProducts != null);
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
