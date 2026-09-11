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
  /** Höchstzahl gleicher Schiffe in EINEM Werftauftrag – 38 Transporter einzeln zu bestellen dauert zu lange. */
  private static final int MAX_SHIPS_PER_ORDER = 10;
  /** Höchstmenge Sprungtreibstoff je Auftrag – die Warteschlange ist sequentiell, ein Riesenlos blockiert alles andere. */
  private static final double JUMP_FUEL_MAX_BATCH = 1500;
  /** Höchstmenge Soldaten bzw. Drohnen je Ausbildungsauftrag. */
  private static final int RECRUIT_MAX_BATCH = 250;
  private static final double ELECTRONICS_IMPORT_MIN_WALLET = 20000;
  /** Rückfallpreis ohne Gebot der Bevölkerung (WorldSeed.STARTER_SELL_ORDER_PRICE, seit Umsetzungskonzept/38: 20 Cr). */
  private static final double DEFAULT_CONSUMER_PRICE = 20;
  /** Spezialware nicht endlos stapeln: oberhalb dieses Vielfachen der Reserve ruht die Charge. */
  private static final double SPECIALTY_STOCK_CAP_FACTOR = 10;
  private static final Pattern MISSING = Pattern.compile("(p_[a-z_]+) \\((\\d+) benötigt, (\\d+) vorhanden\\)");

  record Health(String colonyId, String name, boolean home, double population, double loyalty, double standardOfLiving,
                boolean blackout, double eleriumHours, double foodCoverage, int infrastructure, int industry,
                int shipyard, int academy, int soldiers, int drones, double housingCapacity) {
  }

  /**
   * Ab diesem Anteil belegten Wohnraums hat der Wohnkomplex Vorrang vor dem
   * Ausbauplan. Vorher stand er in jedem Plan erst hinter Industrie 8: im
   * Gesamttest 11.9.2026 standen alle 40 Bots auf Wohnkomplex 1, ihre
   * Bevölkerung lag nach Minuten am Limit (20 000), und der Wohnraum statt der
   * Versorgung deckelte die ganze Wirtschaft.
   */
  private static final double HOUSING_EXPAND_AT_SHARE = 0.8;

  private final Bot bot;
  private final Map<String, Set<String>> materialOrders = new HashMap<>();
  private final Map<String, Health> health = new LinkedHashMap<>();
  private final Map<String, Double> shopping = new LinkedHashMap<>();
  private final Map<String, Integer> blackoutTicks = new HashMap<>();
  private final Map<String, Integer> lastReorderTick = new HashMap<>();
  private final Map<String, String> energyGuardLogged = new HashMap<>();
  private static final int REORDER_COOLDOWN_TICKS = 6;
  private final Map<String, Integer> lastPriceChangeTick = new HashMap<>();
  /** Die Bevölkerung stellt ihre Gebote einmal je Spieltag neu (15 s Realzeit bei Tempo 4) – öfter umpreisen bringt nichts. */
  private static final int PRICE_COOLDOWN_TICKS = 2;
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
    double hours = 0;
    for (Map.Entry<String, Double> e : products.entrySet()) {
      hours += chainHours(id, e.getKey(), e.getValue());
    }
    // Energie: die Vorhaltemenge des Energiespeichers (Konzept 32) auf den Verbrauch über die
    // Laufzeit des Auftrags plus Reserve setzen und warten, bis er voll ist. Der Kettenbedarf
    // selbst spielt keine Rolle mehr – die Kette nimmt nur, was im LAGER liegt, und produziert
    // den Rest; der Speicher bleibt unangetastet.
    JsonNode storage = bot.world.energyStorage(id);
    double need = Math.ceil(perHour * (hours + GUARD_RESERVE_HOURS));
    double stored = Json.dbl(storage, "stored");
    double target = Json.dbl(storage, "reserveTarget");
    if (target < need) {
      try {
        bot.call("setEnergyReserve", Map.of("colonyId", id, "reserveTarget", need));
        bot.world.invalidate("energyStorage", "warehouse");
        bot.monitor.event("ENERGY_RESERVE_SET", h.name() + ": Vorhaltemenge des Energiespeichers auf " + (long) need
            + " gesetzt (" + purpose + ", Laufzeit " + fmtHours(hours) + " + Reserve)", "colonyId", id, "target", need, "purpose", purpose);
      } catch (CommandException e) {
        bot.monitor.log(h.name() + ": Vorhaltemenge abgelehnt: " + e.getMessage());
      }
    }
    if (stored >= need) {
      energyGuardLogged.remove(id + purpose);
      return true;
    }
    // Der Speicher füllt sich aus jeder eintreffenden Charge – eine passende Charge einreihen.
    double missing = Math.ceil(need - stored);
    boolean queued = false;
    for (JsonNode q : bot.world.productionQueue(id)) {
      if (productsOf(q).contains(Catalog.ELERIUM) && !Json.bool(q, "requeueOnComplete") && Json.dbl(q, "quantity") >= missing * 0.9) queued = true;
    }
    if (!queued) queueProduction(id, Catalog.ELERIUM, missing, false);
    String key = id + purpose;
    if (!energyGuardLogged.containsKey(key)) {
      energyGuardLogged.put(key, purpose);
      bot.monitor.event("ENERGY_GUARD", h.name() + ": " + purpose + " wartet auf den Energiespeicher – Laufzeit " + fmtHours(hours)
          + " kostet " + (long) (perHour * hours) + ", Reserve " + (long) (perHour * GUARD_RESERVE_HOURS)
          + " → " + (long) need + " nötig, " + (long) stored + " im Speicher, Charge x" + (long) missing + " eingereiht",
          "colonyId", id, "purpose", purpose, "need", need, "stored", stored, "hours", hours);
    }
    return false;
  }
  /** Kettenlaufzeiten je "Kolonie|Produkt|Menge": {Takt der Abfrage, Stunden} – siehe {@link #chainHours}. */
  private final Map<String, double[]> chainHoursCache = new HashMap<>();
  /** So viele Takte gilt eine abgefragte Kettenlaufzeit für die Energie-Wache. */
  private static final int CHAIN_HOURS_CACHE_TICKS = 10;

  /**
   * Laufzeit einer Produktionskette für die Energie-Wache, höchstens alle
   * {@link #CHAIN_HOURS_CACHE_TICKS} Takte frisch vom Server. Jede Vorschau ist
   * dort eine komplette Kettenplanung; die Wache fragte sie vorher bei jedem
   * Takt für jeden Werft-, Ausbildungs- und Baustoffwunsch neu ab – bei
   * zwanzig Bots dutzende Planungen je Sekunde für eine Zahl, die sich nur mit
   * Lager, Bevölkerung und Gebäudestufen langsam verschiebt. Wo es auf die
   * genaue Zahl ankommt (Bestellung, Meldung), wird weiter direkt abgefragt.
   */
  private double chainHours(String colonyId, String productTypeId, double quantity) {
    String key = colonyId + "|" + productTypeId + "|" + quantity;
    double[] cached = chainHoursCache.get(key);
    if (cached != null && bot.tickNo - cached[0] < CHAIN_HOURS_CACHE_TICKS) return cached[1];
    double hours = Json.dbl(bot.world.previewChain(colonyId, productTypeId, quantity), "totalHours");
    chainHoursCache.put(key, new double[]{bot.tickNo, hours});
    return hours;
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
      // Speicher plus Lager: der Energiespeicher (Konzept 32) ist die Reserve, die
      // Ketten nicht anfassen können; das Lager der Rest.
      double stock = w.stock(id, Catalog.ELERIUM) + Json.dbl(w.energyStorage(id), "stored");
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
          World.unitCount(garrison, Catalog.SOLDIER), World.droneCount(garrison), w.housingCapacity(id)));
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
    activateDefenseIfBuilt(h);
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
      if (!"stopped".equals(text(q, "status"))) continue;
      int code = q.path("stoppedReasonCode").asInt();
      if (code == 509) {
        // Löhne nicht bezahlbar (Umsetzungskonzept/38): der Auftrag bleibt stehen und wird
        // fortgesetzt, sobald das Guthaben reicht – verwerfen und neu bestellen liefe im Kreis.
        double wages = Json.dbl(q.path("plan"), "wageCredits");
        if (bot.world.wallet() >= wages + 1) {
          try {
            bot.call("resumeProduction", Map.of("colonyId", id, "entryId", text(q, "id")));
            bot.monitor.log(h.name() + ": Auftrag " + text(q, "productTypeId") + " nach Lohnstopp fortgesetzt (" + (long) wages + " Cr Löhne)");
            bot.world.invalidate("productionQueue", "wallet");
          } catch (CommandException e) {
            bot.monitor.log(h.name() + ": Fortsetzen nach Lohnstopp abgelehnt: " + e.getMessage());
          }
        }
        continue;
      }
      cancelProduction(id, text(q, "id"));
      // 504 = seit dem Einreihen unter die Mindestdauer gefallen (Industrie ausgebaut); die
      // Nachbestellung im nächsten Takt hebt die Menge über raiseToMinimum wieder an.
      String reason = code == 504 ? "Los unter der Mindestdauer" : "Vorprodukte fehlten ohne Auto-Produktion";
      bot.monitor.log(h.name() + ": angehaltenen Auftrag " + text(q, "productTypeId") + " verworfen (" + reason + ")");
    }
    if (h.eleriumHours() < ELERIUM_SHOPPING_BELOW_HOURS) {
      shopping.merge(Catalog.ELERIUM, Math.max(5, Math.ceil(perHour * 240 - bot.world.stock(id, Catalog.ELERIUM))), Double::sum);
    }
    // Sprungtreibstoff nach der GRÖSSTEN eigenen Flotte bevorraten, nicht mit zehn
    // Kapseln pauschal: Der Verbrauch hängt an der Masse (Umsetzungskonzept/34) –
    // eine Kampfflotte mit zwei Kreuzern braucht für zwölf Sprünge über 3000
    // Kapseln. Mit dem alten Festwert stand jede Flotte nach dem ersten Sprung.
    if (h.home() && !queuedProducts.contains(Catalog.JUMP_FUEL)) {
      double perHopBiggestFleet = 0;
      for (JsonNode f : bot.world.ownFleets()) {
        perHopBiggestFleet = Math.max(perHopBiggestFleet, bot.world.fleetJumpFuelPerHop(f));
      }
      double target = Math.max(10, Math.ceil(perHopBiggestFleet * Catalog.DEFAULT_TRIP_HOPS));
      double stock = bot.world.stock(id, Catalog.JUMP_FUEL);
      if (stock < target * 0.5) {
        double qty = Math.min(JUMP_FUEL_MAX_BATCH, Math.ceil(target - stock));
        queueProduction(id, Catalog.JUMP_FUEL, qty, false);
        bot.monitor.log(h.name() + ": Sprungtreibstoff nachbestellt (x" + (long) qty + ", Ziel " + (long) target + ")");
      }
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
          bot.call("queueProductionBundle", Map.of("colonyId", id, "products", products, "autoProduceMissing", true, "requeueOnComplete", false,
              "raiseToMinimum", true));
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
        // (Economy.purchase im Backend); ohne Elektronik-Order bleibt ein Drittel der Löhne
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
   * Die Bevölkerung kauft AUSSCHLIESSLICH über ihre Gebote am EIGENEN
   * Handelsposten der Kolonie (Umsetzungskonzept/38): je Gut ein stehendes
   * Gebot aus ihrem Tagesbudget, das eine Verkaufsorder zum oder unter dem
   * Gebot sofort kreuzt. Die Startausstattung legt seit Umsetzungskonzept/20
   * nur für Grundnahrung eine Auto-Relist-Order an; deshalb legt der Bot für
   * jedes Grundbedarfsgut eine dauerhaft nachfüllende Order ZUM GEBOT an
   * (Rückfall {@link #DEFAULT_CONSUMER_PRICE}, wenn gerade keines steht).
   */
  private void ensureLocalSellOrders(Health h) {
    String id = h.colonyId();
    JsonNode colony = bot.world.colony(id);
    String systemId = text(colony, "systemId");
    if (systemId == null) return;
    Set<String> covered = new HashSet<>();
    for (JsonNode o : bot.world.sellOrders(systemId)) {
      if (!Json.eq(text(o, "sourceColonyId"), id) || !Json.bool(o, "autoRelist")) continue;
      covered.add(text(o, "productTypeId"));
    }
    adjustPrices(h, systemId);
    for (String good : List.of(Catalog.FOOD, Catalog.MEDICINE, Catalog.ELECTRONICS)) {
      if (covered.contains(good)) continue;
      double stock = Math.floor(bot.world.stock(id, good));
      double qty = Math.min(stock, reserveQty(h.population(), good));
      if (qty < 1) continue;
      double bid = bot.world.populationBid(id, good);
      double price = bid > 0 ? bid : DEFAULT_CONSUMER_PRICE;
      try {
        bot.call("createSellOrder", Map.of("colonyId", id, "productTypeId", good, "quantity", qty, "pricePerUnit", price, "autoRelist", true));
        bot.monitor.event("SELL_ORDER_CREATED", h.name() + ": lokale Dauer-Verkaufsorder " + good + " x" + (long) qty + " @ " + price
            + (bid > 0 ? " (Gebot der Bevölkerung)" : " (Rückfallpreis)"), "colonyId", id, "product", good, "qty", qty, "price", price);
        bot.world.invalidate("sellOrders", "warehouse", "hubOrders");
      } catch (CommandException e) {
        bot.monitor.log(h.name() + ": Verkaufsorder " + good + " abgelehnt: " + e.getMessage());
      }
    }
  }

  /**
   * Preispolitik (Umsetzungskonzept/38): der Bot verkauft ins Gebot. Die
   * Bevölkerung stellt je Gut ein Gebot aus ihrem Tagesbudget; eine
   * Verkaufsorder darüber verkauft nichts, eine darunter verschenkt die
   * Differenz nicht (Ausführung zum Preis der älteren Order, also des
   * Gebots). Jede eigene Dauerorder wird deshalb auf das aktuelle Gebot
   * gesetzt, sobald es sich bewegt. Das frühere Preisband nach Deckung
   * entfällt – die Bevölkerung sagt jetzt selbst, was sie zahlen kann.
   */
  private void adjustPrices(Health h, String systemId) {
    String id = h.colonyId();
    for (JsonNode o : bot.world.sellOrders(systemId)) {
      if (!Json.eq(text(o, "sourceColonyId"), id) || !Json.bool(o, "autoRelist")) continue;
      String good = text(o, "productTypeId");
      if (!Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.containsKey(good)) continue;
      String key = id + ":" + good;
      if (bot.tickNo - lastPriceChangeTick.getOrDefault(key, -1000) < PRICE_COOLDOWN_TICKS) continue;
      double bid = bot.world.populationBid(id, good);
      if (bid <= 0) continue;
      double price = Json.dbl(o, "limitPrice");
      if (Math.abs(price - bid) < 0.01) continue;
      try {
        bot.call("updateSellOrderPrice", Map.of("orderId", text(o, "id"), "pricePerUnit", bid));
        lastPriceChangeTick.put(key, bot.tickNo);
        bot.monitor.event("PRICE_ADJUSTED", h.name() + ": " + good + " " + price + " -> " + bid + " Cr (Gebot der Bevölkerung)",
            "colonyId", id, "product", good, "from", price, "to", bid);
        bot.world.invalidate("sellOrders", "warehouse", "hubOrders", "wallet");
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
    // Wohnraum vor dem Plan, sobald er knapp wird – aber nicht in Notlagen
    // (Blackout, Hunger): dort bremst der Plan bewusst jedes Wachstum.
    if (housingTight(h, plan) && !w.buildingPending(id, Catalog.HABITAT) && tryBuild(h, plan, Catalog.HABITAT)) return;
    for (int i = 0; i < plan.buildPriority.size(); i++) {
      String typeId = plan.buildPriority.get(i);
      int cap = plan.buildCaps.get(i);
      int level = w.buildingLevel(id, typeId);
      if (level >= cap || w.buildingPending(id, typeId)) continue;
      if (typeId.equals(Catalog.DEFENSE) && !h.home()) continue;
      if (tryBuild(h, plan, typeId)) return;
      // sonst (z. B. Credits) – nächstgünstigere Priorität versuchen
    }
  }

  private static boolean housingTight(Health h, Strategy.Plan plan) {
    return plan.allowInfrastructureGrowth && h.housingCapacity() > 0 && h.population() >= HOUSING_EXPAND_AT_SHARE * h.housingCapacity();
  }

  /**
   * Reiht die nächste Stufe eines Gebäudes ein. {@code true}, wenn damit für
   * diesen Takt etwas angestoßen ist – der Ausbau selbst, eine
   * Infrastrukturstufe für den fehlenden Bebauungsplatz oder die fehlenden
   * Baustoffe; {@code false}, wenn der Ausbau an etwas anderem scheiterte
   * (z. B. Credits).
   */
  private boolean tryBuild(Health h, Strategy.Plan plan, String typeId) {
    String id = h.colonyId();
    World w = bot.world;
    int level = w.buildingLevel(id, typeId);
    try {
      bot.call("queueBuilding", Map.of("colonyId", id, "buildingTypeId", typeId));
      bot.monitor.event("BUILD_ORDERED", h.name() + ": " + typeId + " Stufe " + level + " -> " + (level + 1), "colonyId", id, "type", typeId, "level", level + 1);
      w.invalidate("buildings");
      return true;
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
        return true;
      }
      return queueMissingMaterials(h, msg);
    }
  }

  /**
   * Liest die Ablehnung eines Ausbaus („Fehlende Baustoffe: …") oder eines
   * Werftauftrags („Fehlende Vorprodukte: …" – die Werft montiert nur, was im
   * Lager liegt) und reiht das Fehlende als EIN Bündel in die Produktion ein.
   * {@code true}, wenn die Meldung diese Form hatte und damit erledigt ist –
   * auch dann, wenn alles Fehlende schon in der Warteschlange steht.
   */
  boolean queueMissingMaterials(Health h, String message) {
    if (message == null || !(message.startsWith("Fehlende Baustoffe") || message.startsWith("Fehlende Vorprodukte"))) return false;
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
      bot.call("queueProductionBundle", Map.of("colonyId", h.colonyId(), "products", products, "autoProduceMissing", true, "requeueOnComplete", false,
          "raiseToMinimum", true));
      already.addAll(products.keySet());
      bot.monitor.log(h.name() + ": " + (message.startsWith("Fehlende Vorprodukte") ? "Vorprodukte" : "Baustoffe") + " gebündelt eingereiht: " + products);
    } catch (CommandException e) {
      bot.monitor.log(h.name() + ": Produktion der fehlenden Vorprodukte abgelehnt: " + e.getMessage());
    }
    return true;
  }

  // --- Werft ------------------------------------------------------------------------

  private void manageShipyard(Health h, Strategy.Plan plan) {
    if (h.shipyard() < 1 || !h.home()) return;
    String id = h.colonyId();
    List<JsonNode> yard = bot.world.shipyardQueue(id);
    if (!yard.isEmpty()) return;
    // Transporter haben Vorrang vor Kriegsschiffen, solange die Landungsoperation
    // noch nicht genug Platz hat (Military.prepare rechnet den Bedarf aus dem
    // Soldatenbedarf und der Katalogkapazität von 27 Plätzen je Schiff aus).
    if (plan.wantTransports > bot.military.transportsOwned()) {
      int missing = plan.wantTransports - bot.military.transportsOwned();
      orderShip(h, Catalog.TROOP_TRANSPORT, "TRANSPORT_ORDERED", Math.min(missing, MAX_SHIPS_PER_ORDER));
      return;
    }
    // Frachter tragen die Drohnen der Landung. Einer reicht dafür nicht: 48 schwere
    // Drohnen je Frachter gegen eine Garnison, die selbst 87 aktive Drohnen stellt.
    if (plan.wantFreighters > freightersOwned()) {
      int missing = plan.wantFreighters - freightersOwned();
      orderShip(h, Catalog.FREIGHTER, "FREIGHTER_ORDERED", Math.min(missing, MAX_SHIPS_PER_ORDER));
      return;
    }
    if (plan.wantWarships && bot.military.warshipCount() < MAX_WARSHIPS) {
      String type = warshipRotation++ % 3 == 2 ? Catalog.WARSHIP_TYPES.get(1) : Catalog.WARSHIP_TYPES.get(0);
      orderShip(h, type, "WARSHIP_ORDERED", 1);
    }
  }

  /** Frachter im Lager, in Flotten und in der Werft-Warteschlange. */
  private int freightersOwned() {
    int n = (int) bot.world.stock(bot.homeColonyId, Catalog.FREIGHTER);
    for (JsonNode f : bot.world.ownFleets()) n += (int) World.shipCount(f, Catalog.FREIGHTER);
    for (JsonNode q : bot.world.shipyardQueue(bot.homeColonyId)) {
      if (Json.eq(text(q, "shipProductTypeId"), Catalog.FREIGHTER)) n += (int) Math.max(1, Json.dbl(q, "quantity"));
    }
    return n;
  }

  /**
   * Werftauftrag in zwei Schritten: die Werft montiert nur, was im Lager liegt.
   * Fehlen Vorprodukte, lehnt der Server ab und nennt sie – dann werden sie wie
   * Baustoffe als EIN Bündel in die Produktionswarteschlange gestellt
   * ({@link #queueMissingMaterials}); der Werftauftrag kommt im nächsten Takt,
   * in dem alles im Lager liegt. Die Energie-/Versorgungs-Wache gilt für beides:
   * die Vorkette zieht Elerium aus dem Lager.
   */
  private void orderShip(Health h, String shipType, String eventType, int quantity) {
    String id = h.colonyId();
    double qty = Math.max(1, quantity);
    if (!energyGuard(h, Map.of(shipType, qty), "Werftauftrag " + shipType)) return;
    JsonNode preview = bot.world.previewChain(id, shipType, qty);
    double eta = Json.dbl(preview, "totalHours");
    try {
      bot.call("queueShip", Map.of("colonyId", id, "shipProductTypeId", shipType, "quantity", qty, "requeueOnComplete", false));
      bot.monitor.event(eventType, h.name() + ": Werftauftrag " + (long) qty + "x " + shipType + " (Montage " + fmtHours(eta) + ")",
          "colonyId", id, "ship", shipType, "qty", qty, "etaGameHours", eta);
      bot.world.invalidate("shipyardQueue");
    } catch (CommandException e) {
      if (queueMissingMaterials(h, e.getMessage())) return;
      bot.monitor.log(h.name() + ": Werftauftrag " + shipType + " abgelehnt: " + e.getMessage());
    }
  }

  // --- Ausbildungszentrum -------------------------------------------------------------

  /**
   * Eine gebaute Verteidigungsanlage wirkt erst, wenn sie AKTIVIERT ist
   * (BuildingCommands.activateDefense, 12 Spielstunden Vorlauf). Die Bots
   * bauten sie und ließen sie dann kalt stehen – die Anlage kostete Unterhalt,
   * ohne je eine Landung abzuwehren.
   */
  private void activateDefenseIfBuilt(Health h) {
    for (JsonNode b : bot.world.buildings(h.colonyId())) {
      if (!Json.eq(text(b, "typeId"), Catalog.DEFENSE) || Json.integer(b, "level") < 1) continue;
      String state = text(b, "activationState");
      if ("Active".equals(state) || "Activating".equals(state)) continue;
      try {
        bot.call("activateDefense", Map.of("colonyId", h.colonyId(), "buildingId", text(b, "id")));
        bot.monitor.event("DEFENSE_ACTIVATED", h.name() + ": Verteidigungsanlage wird aktiviert", "colonyId", h.colonyId());
        bot.world.invalidate("buildings");
      } catch (CommandException e) {
        bot.monitor.log(h.name() + ": Aktivierung der Verteidigungsanlage abgelehnt: " + e.getMessage());
      }
    }
  }

  private void manageAcademy(Health h, Strategy.Plan plan) {
    if (h.academy() < 1 || !h.home()) return;
    if (h.loyalty() <= Catalog.RECRUIT_MIN_LOYALTY_PCT) return;
    String id = h.colonyId();
    if (!bot.world.recruitmentQueue(id).isEmpty()) return;
    JsonNode garrison = bot.world.garrison(id);
    int soldiers = World.unitCount(garrison, Catalog.SOLDIER);
    int drones = World.unitCount(garrison, plan.wantDroneType);
    // Loskrößen in der Größenordnung des BEDARFS: Die Belagerung einer Kolonie
    // mit 20 000 Einwohnern braucht 1000 Soldaten (Loyalitätsverlust je Tick =
    // Soldaten/Bevölkerung). In Zehnerlosen, von denen immer nur EINES in der
    // Warteschlange steht, sind das hundert aufeinanderfolgende Aufträge – die
    // Landungsoperation kam so nie zustande.
    if (soldiers < plan.wantSoldiers) {
      recruit(h, Catalog.SOLDIER, Math.min(RECRUIT_MAX_BATCH, plan.wantSoldiers - soldiers));
    } else if (drones < plan.wantDrones) {
      recruit(h, plan.wantDroneType, Math.min(RECRUIT_MAX_BATCH, plan.wantDrones - drones));
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
      // raiseToMinimum: der Server hebt Aufträge unter der Mindestdauer (10 Spielminuten)
      // selbst auf die Mindestmenge an, statt sie abzulehnen.
      bot.call("queueProduction", Map.of("colonyId", colonyId, "productTypeId", productTypeId, "quantity", Math.floor(quantity),
          "autoProduceMissing", true, "requeueOnComplete", requeue, "raiseToMinimum", true));
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
