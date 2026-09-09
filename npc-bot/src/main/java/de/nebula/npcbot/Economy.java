package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.nebula.npcbot.Json.text;

/**
 * Wirtschaftsführung für JEDE eigene Kolonie (Heimatwelt, gegründete und
 * eroberte Kolonien gleichermaßen). Reihenfolge je Kolonie und Takt:
 *
 * <ol>
 *   <li><b>Energie zuerst.</b> Die Lehre aus dem Live-Spiel (alle 20
 *       Bot-Kolonien nach Stunden im Blackout, Bevölkerung 0, siehe TODO.md):
 *       ein Dauerauftrag für Stabilisiertes Elerium, und wenn der Bestand
 *       unter 36 Stunden Reichweite fällt, wird die sequentielle
 *       Warteschlange umsortiert – laufende Fremdaufträge werden abgebrochen
 *       (anteilige Gutschrift), wartende hinter das Elerium gestellt.</li>
 *   <li><b>Grundbedarf.</b> Nahrung/Medizin so bevorraten, dass die lokale
 *       Auto-Relist-Verkaufsorder der Bevölkerung nie leerläuft; Export nur
 *       oberhalb dieser Reserve (der alte Bot exportierte bis auf 5 Stück
 *       und ließ 2000 Einwohner hungern).</li>
 *   <li><b>Spezialisierung</b> als kleine, immer wieder nachgelegte Charge.</li>
 *   <li><b>Ausbau</b> nach der Prioritätenliste des Plans; fehlende
 *       Baustoffe als EIN gebündelter Auftrag; Infrastruktur nur, wenn die
 *       Energiereserve das trägt.</li>
 *   <li><b>Werft/Ausbildungszentrum</b> nach den Wünschen des Plans
 *       (Transporter, Kriegsschiffe, Soldaten, Drohnen) – jeweils hinter der
 *       Energie- und Versorgungs-Wache ({@link #energyGuard}).</li>
 * </ol>
 */
final class Economy {
  /** Reichweite (Spielstunden) des Elerium-Bestands, unter der die Warteschlange umsortiert wird. */
  private static final double ELERIUM_REORDER_BELOW_HOURS = 36;
  /** Infrastruktur-Ausbau nur, wenn die Energiereserve mindestens so lange reicht. */
  private static final double ELERIUM_HOURS_FOR_INFRA_GROWTH = 150;
  /** Unter dieser Reichweite steht Elerium auf der Einkaufsliste der Handelsfahrt. */
  private static final double ELERIUM_SHOPPING_BELOW_HOURS = 200;
  /** Lagerreserve je Grundbedarf: so viele Spielstunden Verbrauch der Bevölkerung. */
  private static final double CONSUMER_RESERVE_HOURS = 120;
  private static final int MAX_WARSHIPS = 30;
  private static final double ELECTRONICS_IMPORT_MIN_WALLET = 20000;
  /** Preis der Start-Nahrungsorder (WorldSeed.STARTER_SELL_ORDER_PRICE), falls keine mehr existiert. */
  private static final double DEFAULT_CONSUMER_PRICE = 450;
  /** Spezialware nicht endlos stapeln: oberhalb dieses Vielfachen der Reserve ruht die Charge. */
  private static final double SPECIALTY_STOCK_CAP_FACTOR = 10;
  private static final Pattern MISSING = Pattern.compile("(p_[a-z_]+) \\((\\d+) benötigt, (\\d+) vorhanden\\)");

  record Health(String colonyId, String name, boolean home, double population, double loyalty, double standardOfLiving,
                boolean blackout, double eleriumHours, double foodCoverage, int infrastructure, int industry,
                int shipyard, int academy, int soldiers, int drones) {
  }

  private final Bot bot;
  private final Map<String, Set<String>> materialOrders = new HashMap<>();
  private final Map<String, Health> health = new LinkedHashMap<>();
  private final Map<String, Double> shopping = new LinkedHashMap<>();
  private final Map<String, Integer> blackoutTicks = new HashMap<>();
  private final Map<String, Integer> lastReorderTick = new HashMap<>();
  private final Map<String, String> energyGuardLogged = new HashMap<>();
  private static final int REORDER_COOLDOWN_TICKS = 6;
  private final Map<String, Integer> lastPriceChangeTick = new HashMap<>();
  private static final int PRICE_COOLDOWN_TICKS = 8;
  private static final double MIN_CONSUMER_PRICE = 15;
  /** Reserve (Spielstunden Verbrauch), die nach Abzug des Kettenbedarfs im Lager bleiben muss. */
  private static final double GUARD_RESERVE_HOURS = 240;

  /**
   * Die Energie-Wache. Schiffsketten und Sprungtreibstoff verbrauchen SELBST
   * Stabilisiertes Elerium (Mannschaftstransporter 188, Korvette 50, Zerstörer
   * 455 Stück), und der Kettenplaner bedient sich dafür zuerst aus dem Lager –
   * beim Start des Auftrags, komplett. Danach belegt der Auftrag die einzige
   * Warteschlange für Tage, in denen kein Elerium nachkommt. Genau so starben
   * die Kolonien im Live-Spiel. Deshalb darf ein großer Auftrag erst starten,
   * wenn das Lager seinen Elerium-Bedarf PLUS den Verbrauch der Infrastruktur
   * über seine Laufzeit plus zehn Tage Reserve hält; bis dahin wird stattdessen
   * eine passende Elerium-Charge eingereiht.
   */
  boolean energyGuard(Health h, Map<String, Double> products, String purpose) {
    String id = h.colonyId();
    double perHour = Catalog.eleriumPerHour(h.infrastructure());
    double demand = 0;
    double hours = 0;
    for (Map.Entry<String, Double> e : products.entrySet()) {
      demand += bot.world.chainDemand(e.getKey(), e.getValue(), Catalog.ELERIUM);
      if (e.getKey().equals(Catalog.ELERIUM)) demand -= e.getValue();
      hours += Json.dbl(bot.world.previewChain(id, e.getKey(), e.getValue()), "totalHours");
    }
    double need = Math.ceil(demand + perHour * (hours + GUARD_RESERVE_HOURS));
    double stock = bot.world.stock(id, Catalog.ELERIUM);
    // Zweite Wache: Grundbedarf. Ein langer Auftrag blockiert auch die Nahrungs-
    // und Medizinchargen – im Testlauf verhungerte der Nahrungs-Spezialist hinter
    // seinen eigenen Transportermodulen, während seine Bevölkerung 27 000 Cr
    // unausgegeben hielt. Die Chargen sind bereits eingereiht (manageConsumerGoods);
    // der große Auftrag wartet, bis sie durch sind.
    for (String good : List.of(Catalog.FOOD, Catalog.MEDICINE)) {
      if (h.population() <= 20) continue;
      // Wie beim Elerium: Verbrauch der Bevölkerung über die Laufzeit des Auftrags plus
      // Reserve muss im Lager liegen, bevor der Auftrag die Warteschlange belegt.
      double rate = Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(good);
      double goodNeed = Math.ceil(h.population() * rate * (hours + GUARD_RESERVE_HOURS));
      double goodStock = bot.world.stock(id, good);
      if (goodStock >= goodNeed) continue;
      double goodMissing = Math.ceil(goodNeed - goodStock);
      boolean batchQueued = false;
      for (JsonNode q : bot.world.productionQueue(id)) {
        if (productsOf(q).contains(good) && Json.dbl(q, "quantity") >= goodMissing * 0.9) batchQueued = true;
      }
      if (!batchQueued) queueProduction(id, good, goodMissing, false);
      String key = id + purpose + good;
      if (!energyGuardLogged.containsKey(key)) {
        energyGuardLogged.put(key, purpose);
        bot.monitor.event("SUPPLY_GUARD", h.name() + ": " + purpose + " wartet auf " + good + " – Laufzeit " + fmtHours(hours)
            + " kostet " + (long) (h.population() * rate * hours) + ", Reserve " + (long) (h.population() * rate * GUARD_RESERVE_HOURS)
            + " → " + (long) goodNeed + " nötig, " + (long) goodStock + " im Lager, Charge x" + (long) goodMissing + " eingereiht",
            "colonyId", id, "purpose", purpose, "good", good, "need", goodNeed, "stock", goodStock);
      }
      return false;
    }
    if (stock >= need) {
      energyGuardLogged.remove(id + purpose);
      return true;
    }
    double missing = Math.ceil(need - stock);
    boolean queued = false;
    for (JsonNode q : bot.world.productionQueue(id)) {
      if (productsOf(q).contains(Catalog.ELERIUM) && !Json.bool(q, "requeueOnComplete") && Json.dbl(q, "quantity") >= missing * 0.9) queued = true;
    }
    if (!queued) {
      queueProduction(id, Catalog.ELERIUM, missing, false);
    }
    String key = id + purpose;
    if (!energyGuardLogged.containsKey(key)) {
      energyGuardLogged.put(key, purpose);
      bot.monitor.event("ENERGY_GUARD", h.name() + ": " + purpose + " wartet auf Elerium – Kette zieht " + (long) demand
          + ", Laufzeit " + fmtHours(hours) + " kostet " + (long) (perHour * hours) + ", Reserve " + (long) (perHour * GUARD_RESERVE_HOURS)
          + " → " + (long) need + " nötig, " + (long) stock + " im Lager, Charge x" + (long) missing + " eingereiht",
          "colonyId", id, "purpose", purpose, "need", need, "stock", stock, "demand", demand, "hours", hours);
    }
    return false;
  }
  private int warshipRotation;
  private int reorders;

  Economy(Bot bot) {
    this.bot = bot;
  }

  Collection<Health> health() {
    return health.values();
  }

  Health home() {
    return health.get(bot.homeColonyId);
  }

  Map<String, Double> shoppingList() {
    return shopping;
  }

  int reorders() {
    return reorders;
  }

  /** Nur lesen – bewertet jede eigene Kolonie, ohne einen Befehl abzusetzen. */
  void assess() {
    World w = bot.world;
    Map<String, Health> fresh = new LinkedHashMap<>();
    for (JsonNode c : w.ownColonies()) {
      String id = text(c, "id");
      int infra = w.buildingLevel(id, Catalog.INFRASTRUCTURE);
      double perHour = Catalog.eleriumPerHour(infra);
      double stock = w.stock(id, Catalog.ELERIUM);
      double hours = perHour > 0 ? stock / perHour : Double.MAX_VALUE;
      JsonNode stats = w.stats(id);
      JsonNode coverage = w.consumptionCoverage(id);
      double food = Json.dbl(coverage, Catalog.FOOD, 1);
      boolean blackout = w.blackout(id);
      blackoutTicks.merge(id, blackout ? 1 : 0, (a, b) -> b == 0 ? 0 : a + b);
      JsonNode garrison = w.garrison(id);
      fresh.put(id, new Health(id, text(c, "name"), Json.bool(c, "isHomeworld"), w.population(id),
          Json.dbl(stats, "loyaltyPct"), Json.dbl(stats, "standardOfLivingPct"), blackout, hours, food, infra,
          w.buildingLevel(id, Catalog.INDUSTRY), w.buildingLevel(id, Catalog.SHIPYARD), w.buildingLevel(id, Catalog.ACADEMY),
          World.unitCount(garrison, Catalog.SOLDIER), World.droneCount(garrison)));
    }
    for (String gone : new ArrayList<>(health.keySet())) {
      if (!fresh.containsKey(gone)) {
        bot.monitor.event("COLONY_LOST", "Kolonie " + health.get(gone).name() + " ist nicht mehr in eigener Hand", "colonyId", gone);
      }
    }
    for (String added : fresh.keySet()) {
      if (!health.containsKey(added) && !health.isEmpty()) {
        bot.monitor.event("COLONY_GAINED", "Neue eigene Kolonie " + fresh.get(added).name(), "colonyId", added);
        // Eroberte wie gegründete Kolonien starten ohne brauchbare Vorräte (Konquest-
        // schäden bzw. leeres Lager) – Erstversorgung anfordern, unabhängig davon,
        // welcher Zustandsautomat die Kolonie eingebracht hat.
        bot.trade.requestDelivery(added, Map.of(Catalog.ELERIUM, 25.0, Catalog.FOOD, 150.0, Catalog.MEDICINE, 40.0));
      }
    }
    health.clear();
    health.putAll(fresh);
  }

  /** Ist die Spezialisierung dauerhaft nicht produzierbar (Blackout seit vielen Takten)? – fließt in die Neuverhandlung der Arbeitsteilung ein. */
  boolean capable() {
    Health h = home();
    return h != null && blackoutTicks.getOrDefault(h.colonyId(), 0) < 6 && h.industry() >= 1;
  }

  void act(Strategy.Plan plan, String specialty) {
    shopping.clear();
    for (Health h : new ArrayList<>(health.values())) {
      try {
        manage(h, plan, specialty);
      } catch (CommandException e) {
        bot.monitor.log("Wirtschaft " + h.name() + ": Befehl abgelehnt: " + e.getMessage());
      }
    }
  }

  private void manage(Health h, Strategy.Plan plan, String specialty) {
    String id = h.colonyId();
    World w = bot.world;
    List<JsonNode> queue = w.productionQueue(id);
    Set<String> queuedProducts = new HashSet<>();
    for (JsonNode q : queue) queuedProducts.addAll(productsOf(q));
    materialOrders.computeIfAbsent(id, k -> new HashSet<>()).retainAll(queuedProducts);

    if (h.industry() >= 1) {
      managePower(h, queue, queuedProducts);
      manageConsumerGoods(h, queuedProducts, specialty);
      manageSpecialty(h, queuedProducts, specialty);
    } else {
      shopping.merge(Catalog.ELERIUM, 20.0, Double::sum);
    }
    manageBuildings(h, plan);
    manageShipyard(h, plan);
    manageAcademy(h, plan);
  }

  // --- Energie -------------------------------------------------------------------

  private void managePower(Health h, List<JsonNode> queue, Set<String> queuedProducts) {
    String id = h.colonyId();
    double perHour = Catalog.eleriumPerHour(h.infrastructure());
    // Zehn Tage je Charge: je seltener der Dauerauftrag an die Reihe muss, desto
    // seltener muss die Warteschlange für ihn umsortiert werden. Der Startauftrag
    // (3 Stück je Umlauf, WorldSeed) zählt nicht als Dauerauftrag – er war für
    // Infrastruktur 2/3 bemessen und ist der Grund der Blackouts im Live-Spiel.
    double batch = Math.max(6, Math.ceil(perHour * 240));
    boolean standing = false;
    for (JsonNode q : queue) {
      if (productsOf(q).contains(Catalog.ELERIUM) && Json.bool(q, "requeueOnComplete") && Json.dbl(q, "quantity") >= batch * 0.5) standing = true;
    }
    if (!standing) {
      queueProduction(id, Catalog.ELERIUM, batch, true);
      bot.monitor.log(h.name() + ": Elerium-Dauerauftrag eingereiht (x" + (long) batch + ", Reichweite " + fmtHours(h.eleriumHours()) + ")");
      bot.world.invalidate("productionQueue");
      queue = bot.world.productionQueue(id);
    }
    int lastReorder = lastReorderTick.getOrDefault(id, -1000);
    if (h.eleriumHours() < ELERIUM_REORDER_BELOW_HOURS && !eleriumRunning(queue) && bot.tickNo - lastReorder >= REORDER_COOLDOWN_TICKS) {
      prioritizeElerium(h, queue, perHour);
    }
    for (JsonNode q : queue) {
      if ("stopped".equals(text(q, "status"))) {
        cancelProduction(id, text(q, "id"));
        bot.monitor.log(h.name() + ": angehaltenen Auftrag " + text(q, "productTypeId") + " verworfen (Vorprodukte fehlten ohne Auto-Produktion)");
      }
    }
    if (h.eleriumHours() < ELERIUM_SHOPPING_BELOW_HOURS) {
      shopping.merge(Catalog.ELERIUM, Math.max(5, Math.ceil(perHour * 240 - bot.world.stock(id, Catalog.ELERIUM))), Double::sum);
    }
    if (h.home() && bot.world.stock(id, Catalog.JUMP_FUEL) < 10 && !queuedProducts.contains(Catalog.JUMP_FUEL)) {
      queueProduction(id, Catalog.JUMP_FUEL, 10, false);
      bot.monitor.log(h.name() + ": Sprungtreibstoff nachbestellt (x10)");
    }
  }

  private boolean eleriumRunning(List<JsonNode> queue) {
    for (JsonNode q : queue) if ("running".equals(text(q, "status")) && productsOf(q).contains(Catalog.ELERIUM)) return true;
    return false;
  }

  /**
   * Die Warteschlange ist strikt sequentiell und startet immer den ERSTEN
   * wartenden Eintrag. Damit Elerium sofort dran ist: laufenden Fremdauftrag
   * abbrechen (anteilige Gutschrift durch den Server), alle wartenden
   * Fremdaufträge abbrechen (verlustfrei, sie haben noch nichts verbraucht)
   * und in alter Reihenfolge hinter dem Elerium neu einreihen.
   */
  private void prioritizeElerium(Health h, List<JsonNode> queue, double perHour) {
    String id = h.colonyId();
    lastReorderTick.put(id, bot.tickNo);
    List<JsonNode> requeue = new ArrayList<>();
    // Was der laufende Auftrag laut seinem Plan an Elerium zieht und wie lange er die
    // Warteschlange belegt, bestimmt die Größe der vorgezogenen Charge – sonst nimmt
    // er sich beim Neustart dieselbe kleine Charge sofort wieder (Endlosschleife).
    double extra = 0;
    for (JsonNode q : queue) {
      if (productsOf(q).contains(Catalog.ELERIUM)) continue;
      if ("running".equals(text(q, "status"))) {
        for (JsonNode step : q.path("plan").path("steps")) {
          if (Json.eq(text(step, "productTypeId"), Catalog.ELERIUM)) extra += Json.dbl(step, "quantityNeeded");
        }
        extra += perHour * Json.dbl(q.path("plan"), "totalHours");
      }
      cancelProduction(id, text(q, "id"));
      if ("running".equals(text(q, "status")) || "queued".equals(text(q, "status"))) requeue.add(q);
    }
    double batch = Math.ceil(Math.max(perHour * 240, extra + perHour * 240) - bot.world.stock(id, Catalog.ELERIUM));
    if (batch >= 1) queueProduction(id, Catalog.ELERIUM, batch, false);
    for (JsonNode q : requeue) {
      try {
        JsonNode bundled = q.path("bundledProducts");
        if (!Json.isNull(bundled) && bundled.isObject()) {
          Map<String, Double> products = new LinkedHashMap<>();
          bundled.fields().forEachRemaining(e -> products.put(e.getKey(), e.getValue().asDouble()));
          bot.call("queueProductionBundle", Map.of("colonyId", id, "products", products, "autoProduceMissing", true, "requeueOnComplete", false));
        } else {
          queueProduction(id, text(q, "productTypeId"), Json.dbl(q, "quantity"), Json.bool(q, "requeueOnComplete"));
        }
      } catch (CommandException e) {
        bot.monitor.log(h.name() + ": Neu-Einreihen von " + text(q, "productTypeId") + " abgelehnt: " + e.getMessage());
      }
    }
    reorders++;
    bot.monitor.event("QUEUE_REORDERED", h.name() + ": Elerium vorgezogen (Reichweite " + fmtHours(h.eleriumHours()) + ", Charge x"
        + (long) Math.max(0, batch) + "), " + requeue.size() + " Aufträge nach hinten", "colonyId", id, "eleriumHours", h.eleriumHours(), "batch", batch);
    bot.world.invalidate("productionQueue", "warehouse");
  }

  // --- Grundbedarf ----------------------------------------------------------------

  private void manageConsumerGoods(Health h, Set<String> queuedProducts, String specialty) {
    String id = h.colonyId();
    ensureLocalSellOrders(h);
    for (String good : List.of(Catalog.FOOD, Catalog.MEDICINE, Catalog.ELECTRONICS)) {
      double target = reserveQty(h.population(), good);
      double stock = bot.world.stock(id, good);
      if (stock >= target) continue;
      double missing = Math.ceil(target - stock);
      // Unterhaltungselektronik kostet an der Station rund 260 Cr je Stück – bei
      // 0,2 Stück je Stunde und 2000 Einwohnern über 1200 Cr je Spieltag. Nur wer
      // reich ist, kauft sie; alle anderen leben mit maximal 75 % Lebensstandard.
      if (good.equals(Catalog.ELECTRONICS)) {
        if (bot.world.wallet() > ELECTRONICS_IMPORT_MIN_WALLET) shopping.merge(good, Math.min(5, missing), Double::sum);
        // Das Budget der Bevölkerung wird zu je einem Drittel auf die drei Güter verteilt
        // (EconomyTick.runConsumption); ohne Elektronik-Order bleibt ein Drittel der Löhne
        // dauerhaft im Bevölkerungs-Wallet liegen und der Kommandant blutet aus. Lokal
        // produzieren, wenn die Tier-5-Kette in vertretbarer Zeit läuft.
        if (!queuedProducts.contains(good) && electronicsFeasible(h)) {
          queueProduction(id, good, Math.max(10, missing), false);
          bot.monitor.log(h.name() + ": " + good + " für die eigene Bevölkerung eingereiht (x" + (long) Math.max(10, missing) + ")");
        }
        continue;
      }
      shopping.merge(good, missing, Double::sum);
      if (good.equals(specialty) || queuedProducts.contains(good)) continue;
      queueProduction(id, good, Math.max(10, missing), false);
      bot.monitor.log(h.name() + ": " + good + " für die eigene Bevölkerung eingereiht (x" + (long) Math.max(10, missing)
          + ", Bestand " + (long) stock + " / Reserve " + (long) target + ")");
    }
  }

  /**
   * Die Bevölkerung kauft AUSSCHLIESSLICH aus Verkaufsorders ihres Systems
   * ({@code EconomyTick.runConsumption}); die Startausstattung legt seit
   * Umsetzungskonzept/20 nur noch für Grundnahrung eine Auto-Relist-Order an.
   * Ohne eigene Orders für Medizin (und Elektronik) bleibt deren Versorgung
   * dauerhaft 0, der Lebensstandard klemmt bei 50 %, die Bevölkerung wächst
   * nicht – und damit entsteht kein neues Geld. Deshalb legt der Bot für jedes
   * Grundbedarfsgut eine dauerhaft nachfüllende Order zum Preis der
   * Start-Nahrungsorder an (eine eroberte/gegründete Kolonie hat gar keine).
   */
  private void ensureLocalSellOrders(Health h) {
    String id = h.colonyId();
    JsonNode colony = bot.world.colony(id);
    String systemId = text(colony, "systemId");
    if (systemId == null) return;
    Set<String> covered = new HashSet<>();
    double referencePrice = 0;
    for (JsonNode o : bot.world.sellOrders(systemId)) {
      if (!Json.eq(text(o, "depotColonyId"), id) || !Json.bool(o, "autoRelist")) continue;
      covered.add(text(o, "productTypeId"));
      if (Json.eq(text(o, "productTypeId"), Catalog.FOOD)) referencePrice = Json.dbl(o, "pricePerUnit");
    }
    if (referencePrice <= 0) referencePrice = DEFAULT_CONSUMER_PRICE;
    adjustPrices(h, systemId);
    for (String good : List.of(Catalog.FOOD, Catalog.MEDICINE, Catalog.ELECTRONICS)) {
      if (covered.contains(good)) continue;
      double stock = Math.floor(bot.world.stock(id, good));
      double qty = Math.min(stock, reserveQty(h.population(), good));
      if (qty < 1) continue;
      try {
        bot.call("createSellOrder", Map.of("colonyId", id, "productTypeId", good, "quantity", qty, "pricePerUnit", referencePrice, "autoRelist", true));
        bot.monitor.event("SELL_ORDER_CREATED", h.name() + ": lokale Dauer-Verkaufsorder " + good + " x" + (long) qty + " @ " + referencePrice
            + " für die eigene Bevölkerung", "colonyId", id, "product", good, "qty", qty, "price", referencePrice);
        bot.world.invalidate("sellOrders", "warehouse");
      } catch (CommandException e) {
        bot.monitor.log(h.name() + ": Verkaufsorder " + good + " abgelehnt: " + e.getMessage());
      }
    }
  }

  /**
   * Preispolitik. Die Bevölkerung kauft nur, was ihr Budget hergibt
   * ({@code EconomyTick.purchasableQuantity}); ihr Einkommen sind Löhne und
   * Unterhalt (rund 0,02 Cr je Kopf und Stunde) plus die Geldschöpfung beim
   * Wachstum. Der Startpreis von 450 Cr je Stück ist dagegen nur bezahlbar,
   * solange die Kolonie wächst – im Testlauf fiel jede Kolonie bei rund 10 000
   * Einwohnern in die Hungersnot, während der Kommandant auf 50 000 Cr saß.
   * Weil der Erlös bei budgetgebundenen Käufern gleich bleibt (Budget ×
   * 1 statt weniger Stück × höherer Preis), ist ein niedrigerer Preis für den
   * Bot kostenlos: Versorgung unter 85 % senkt den Preis um 30 %, Versorgung
   * am Anschlag hebt ihn um 15 % – mit Abkühlzeit, damit die Glättung der
   * Versorgungslage nachkommt.
   */
  private void adjustPrices(Health h, String systemId) {
    String id = h.colonyId();
    JsonNode coverage = bot.world.consumptionCoverage(id);
    for (JsonNode o : bot.world.sellOrders(systemId)) {
      if (!Json.eq(text(o, "depotColonyId"), id) || !Json.bool(o, "autoRelist")) continue;
      String good = text(o, "productTypeId");
      if (!Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.containsKey(good)) continue;
      String key = id + ":" + good;
      if (bot.tickNo - lastPriceChangeTick.getOrDefault(key, -1000) < PRICE_COOLDOWN_TICKS) continue;
      double cov = Json.dbl(coverage, good, -1);
      if (cov < 0 || h.population() < 20) continue;
      double price = Json.dbl(o, "pricePerUnit");
      double newPrice = price;
      if (cov < 0.85 && Json.dbl(o, "remainingQuantity") > 0) newPrice = Math.max(MIN_CONSUMER_PRICE, Math.round(price * 0.7));
      else if (cov >= 1.45 && price < DEFAULT_CONSUMER_PRICE) newPrice = Math.min(DEFAULT_CONSUMER_PRICE, Math.round(price * 1.15));
      if (newPrice == price) continue;
      try {
        bot.call("cancelSellOrder", Map.of("orderId", text(o, "id")));
        bot.world.invalidate("warehouse", "sellOrders");
        double qty = Math.floor(Math.min(bot.world.stock(id, good), Math.max(20, reserveQty(h.population(), good))));
        if (qty >= 1) {
          bot.call("createSellOrder", Map.of("colonyId", id, "productTypeId", good, "quantity", qty, "pricePerUnit", newPrice, "autoRelist", true));
        }
        lastPriceChangeTick.put(key, bot.tickNo);
        bot.monitor.event("PRICE_ADJUSTED", h.name() + ": " + good + " " + (long) price + " -> " + (long) newPrice + " Cr (Versorgung "
            + Math.round(cov * 100) + " %)", "colonyId", id, "product", good, "from", price, "to", newPrice, "coverage", cov);
        bot.world.invalidate("sellOrders", "warehouse");
      } catch (CommandException e) {
        bot.monitor.log(h.name() + ": Preisänderung " + good + " abgelehnt: " + e.getMessage());
      }
    }
  }

  private final Map<String, Boolean> electronicsFeasible = new HashMap<>();
  private final Map<String, Integer> electronicsCheckedTick = new HashMap<>();
  private static final double ELECTRONICS_MAX_BATCH_HOURS = 400;

  /** Einmal je 120 Takte prüfen, ob zehn Stück Unterhaltungselektronik in vertretbarer Zeit (≤ 400 Spielstunden) entstehen. */
  private boolean electronicsFeasible(Health h) {
    String id = h.colonyId();
    if (bot.tickNo - electronicsCheckedTick.getOrDefault(id, -1000) < 120) return electronicsFeasible.getOrDefault(id, false);
    electronicsCheckedTick.put(id, bot.tickNo);
    double hours = Json.dbl(bot.world.previewChain(id, Catalog.ELECTRONICS, 10), "totalHours", Double.MAX_VALUE);
    boolean feasible = hours <= ELECTRONICS_MAX_BATCH_HOURS;
    if (!electronicsFeasible.containsKey(id)) {
      bot.monitor.log(h.name() + ": Unterhaltungselektronik lokal " + (feasible ? "machbar" : "zu teuer") + " (10 Stück = " + fmtHours(hours) + ")");
    }
    electronicsFeasible.put(id, feasible);
    return feasible;
  }

  /** Lagerreserve eines Grundbedarfs in Stück: Verbrauch der Bevölkerung über {@link #CONSUMER_RESERVE_HOURS}. */
  static double reserveQty(double population, String good) {
    double rate = Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.getOrDefault(good, 0.0);
    return Math.max(10, Math.ceil(population * rate * CONSUMER_RESERVE_HOURS));
  }

  private void manageSpecialty(Health h, Set<String> queuedProducts, String specialty) {
    if (specialty == null || !h.home() || queuedProducts.contains(specialty)) return;
    boolean consumer = Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.containsKey(specialty);
    double cap = consumer ? reserveQty(h.population(), specialty) * SPECIALTY_STOCK_CAP_FACTOR : 200;
    if (bot.world.stock(h.colonyId(), specialty) >= cap) return;
    double batch = consumer
        ? Math.max(20, Math.ceil(h.population() * Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(specialty) * 240))
        : 10;
    queueProduction(h.colonyId(), specialty, batch, false);
    bot.monitor.log(h.name() + ": Spezialisierungscharge eingereiht: " + specialty + " x" + (long) batch);
  }

  // --- Ausbau ---------------------------------------------------------------------

  private void manageBuildings(Health h, Strategy.Plan plan) {
    String id = h.colonyId();
    World w = bot.world;
    for (String typeId : plan.buildPriority) {
      int cap = plan.buildCap.getOrDefault(typeId, 1);
      int level = w.buildingLevel(id, typeId);
      if (level >= cap || w.buildingPending(id, typeId)) continue;
      if (typeId.equals(Catalog.DEFENSE) && !h.home()) continue;
      try {
        bot.call("queueBuilding", Map.of("colonyId", id, "buildingTypeId", typeId));
        bot.monitor.event("BUILD_ORDERED", h.name() + ": " + typeId + " Stufe " + level + " -> " + (level + 1), "colonyId", id, "type", typeId, "level", level + 1);
        w.invalidate("buildings");
        return;
      } catch (CommandException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("Bebauungsplatz")) {
          if (plan.allowInfrastructureGrowth && h.eleriumHours() >= ELERIUM_HOURS_FOR_INFRA_GROWTH && !w.buildingPending(id, Catalog.INFRASTRUCTURE)) {
            try {
              bot.call("queueBuilding", Map.of("colonyId", id, "buildingTypeId", Catalog.INFRASTRUCTURE));
              bot.monitor.event("BUILD_ORDERED", h.name() + ": Infrastruktur (kein Bebauungsplatz für " + typeId + ")", "colonyId", id, "type", Catalog.INFRASTRUCTURE, "level", h.infrastructure() + 1);
              w.invalidate("buildings");
            } catch (CommandException infra) {
              queueMissingMaterials(h, infra.getMessage());
            }
          }
          return;
        }
        if (queueMissingMaterials(h, msg)) return;
        // sonst (z. B. Credits) – nächstgünstigere Priorität versuchen
      }
    }
  }

  private boolean queueMissingMaterials(Health h, String message) {
    if (message == null || !message.startsWith("Fehlende Baustoffe")) return false;
    Set<String> already = materialOrders.computeIfAbsent(h.colonyId(), k -> new HashSet<>());
    Matcher m = MISSING.matcher(message);
    LinkedHashMap<String, Double> products = new LinkedHashMap<>();
    while (m.find()) {
      String pid = m.group(1);
      double missing = Double.parseDouble(m.group(2)) - Double.parseDouble(m.group(3));
      if (already.contains(pid)) continue;
      products.put(pid, Math.max(1, Math.ceil(missing)));
    }
    if (products.isEmpty()) return true;
    if (h.industry() < 1) {
      for (Map.Entry<String, Double> e : products.entrySet()) shopping.merge(e.getKey(), e.getValue(), Double::sum);
      return true;
    }
    if (!energyGuard(h, products, "Baustoffe " + products.keySet())) return true;
    try {
      bot.call("queueProductionBundle", Map.of("colonyId", h.colonyId(), "products", products, "autoProduceMissing", true, "requeueOnComplete", false));
      already.addAll(products.keySet());
      bot.monitor.log(h.name() + ": Baustoffe gebündelt eingereiht: " + products);
    } catch (CommandException e) {
      bot.monitor.log(h.name() + ": Baustoff-Produktion abgelehnt: " + e.getMessage());
    }
    return true;
  }

  // --- Werft ------------------------------------------------------------------------

  private void manageShipyard(Health h, Strategy.Plan plan) {
    if (h.shipyard() < 1 || !h.home()) return;
    String id = h.colonyId();
    List<JsonNode> yard = bot.world.shipyardQueue(id);
    if (!yard.isEmpty()) return;
    if (plan.wantTransport && bot.military.transportsOwned() == 0) {
      orderShip(h, Catalog.TROOP_TRANSPORT, "TRANSPORT_ORDERED");
      return;
    }
    if (plan.wantWarships && bot.military.warshipCount() < MAX_WARSHIPS) {
      String type = warshipRotation++ % 3 == 2 ? Catalog.WARSHIP_TYPES.get(1) : Catalog.WARSHIP_TYPES.get(0);
      orderShip(h, type, "WARSHIP_ORDERED");
    }
  }

  /**
   * Werftauftrag mit Auto-Produktion: seit dem Kettenplaner jeden Schritt in
   * seiner eigenen Anlage rechnet (Konzept 31 §I), läuft die Vorkette mit
   * Industrietempo und nur die Endmontage mit Werfttempo – und der Auftrag
   * belegt die Werft-Warteschlange, nicht die des Industriekomplexes, der frei
   * bleibt für Nahrung, Medizin und Elerium. Die Energie-/Versorgungs-Wache
   * gilt trotzdem: die Kette zieht Elerium aus dem Lager.
   */
  private void orderShip(Health h, String shipType, String eventType) {
    String id = h.colonyId();
    if (!energyGuard(h, Map.of(shipType, 1.0), "Werftauftrag " + shipType)) return;
    JsonNode preview = bot.world.previewChain(id, shipType, 1);
    double eta = Json.dbl(preview, "totalHours");
    try {
      bot.call("queueShip", Map.of("colonyId", id, "shipProductTypeId", shipType, "quantity", 1.0,
          "autoProduceMissing", true, "requeueOnComplete", false));
      bot.monitor.event(eventType, h.name() + ": Werftauftrag " + shipType + " (Kettenvorschau " + fmtHours(eta) + ")",
          "colonyId", id, "ship", shipType, "etaGameHours", eta);
      bot.world.invalidate("shipyardQueue");
    } catch (CommandException e) {
      bot.monitor.log(h.name() + ": Werftauftrag " + shipType + " abgelehnt: " + e.getMessage());
    }
  }

  // --- Ausbildungszentrum -------------------------------------------------------------

  private void manageAcademy(Health h, Strategy.Plan plan) {
    if (h.academy() < 1 || !h.home()) return;
    if (h.loyalty() <= Catalog.RECRUIT_MIN_LOYALTY_PCT) return;
    String id = h.colonyId();
    if (!bot.world.recruitmentQueue(id).isEmpty()) return;
    JsonNode garrison = bot.world.garrison(id);
    int soldiers = World.unitCount(garrison, Catalog.SOLDIER);
    int drones = World.unitCount(garrison, plan.wantDroneType);
    if (soldiers < plan.wantSoldiers) {
      recruit(h, Catalog.SOLDIER, Math.min(10, plan.wantSoldiers - soldiers));
    } else if (drones < plan.wantDrones) {
      recruit(h, plan.wantDroneType, Math.min(20, plan.wantDrones - drones));
    }
  }

  private void recruit(Health h, String unitType, int quantity) {
    // Wie beim Werftauftrag: die Kette läuft in der Akademie-Warteschlange, ihre
    // Vorprodukte mit Industrietempo (Konzept 31 §I); nur die Wachen gelten.
    if (!energyGuard(h, Map.of(unitType, (double) quantity), "Rekrutierung " + unitType)) return;
    try {
      bot.call("queueRecruitment", Map.of("colonyId", h.colonyId(), "unitProductTypeId", unitType, "quantity", (double) quantity,
          "autoProduceMissing", true, "requeueOnComplete", false));
      bot.monitor.event("RECRUIT_ORDERED", h.name() + ": " + quantity + "x " + unitType + " im Ausbildungszentrum", "colonyId", h.colonyId(), "unit", unitType, "qty", quantity);
      bot.world.invalidate("recruitmentQueue");
    } catch (CommandException e) {
      bot.monitor.log(h.name() + ": Rekrutierung " + unitType + " abgelehnt: " + e.getMessage());
    }
  }

  // --- Hilfen ---------------------------------------------------------------------------

  private void queueProduction(String colonyId, String productTypeId, double quantity, boolean requeue) {
    try {
      bot.call("queueProduction", Map.of("colonyId", colonyId, "productTypeId", productTypeId, "quantity", Math.floor(quantity),
          "autoProduceMissing", true, "requeueOnComplete", requeue));
      bot.world.invalidate("productionQueue");
    } catch (CommandException e) {
      bot.monitor.log("Produktionsauftrag " + productTypeId + " abgelehnt: " + e.getMessage());
    }
  }

  private void cancelProduction(String colonyId, String entryId) {
    try {
      bot.call("cancelProduction", Map.of("colonyId", colonyId, "entryId", entryId));
      bot.world.invalidate("productionQueue", "warehouse");
    } catch (CommandException e) {
      bot.monitor.log("Abbruch " + entryId + " abgelehnt: " + e.getMessage());
    }
  }

  private static Set<String> productsOf(JsonNode queueEntry) {
    Set<String> out = new HashSet<>();
    String p = text(queueEntry, "productTypeId");
    if (p != null) out.add(p);
    JsonNode bundled = queueEntry.path("bundledProducts");
    if (!Json.isNull(bundled) && bundled.isObject()) bundled.fieldNames().forEachRemaining(out::add);
    return out;
  }

  static String fmtHours(double gameHours) {
    if (gameHours == Double.MAX_VALUE) return "∞";
    if (gameHours < 48) return String.format("%.0f h", gameHours);
    return String.format("%.1f Tage", gameHours / 24);
  }
}
