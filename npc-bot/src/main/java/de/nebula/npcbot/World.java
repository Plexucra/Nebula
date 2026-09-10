package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.GameConnection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.nebula.npcbot.Json.isNull;
import static de.nebula.npcbot.Json.list;
import static de.nebula.npcbot.Json.text;

/**
 * Die Weltsicht eines Bots: alle Abfragen laufen über das reguläre
 * WebSocket-Protokoll und werden JE ENTSCHEIDUNGSTAKT gecacht – dieselbe
 * Frage wird dem Server innerhalb eines Takts nur einmal gestellt (die
 * Module fragen sich sonst gegenseitig dieselben Flotten/Kolonien ab).
 * Nach einem eigenen Befehl, der eine Antwort veraltet, ruft das Modul
 * {@link #invalidate(String...)} für die betroffenen Abfragen auf.
 *
 * <p>Enthält außerdem das öffentlich bekannte Gateway-Netz als Graph
 * ({@link #hops}) – Reisezeiten sind der wichtigste Faktor jeder Zielwahl.</p>
 */
final class World {
  private GameConnection c;
  private final Map<String, JsonNode> cache = new HashMap<>();
  private Map<String, List<String>> adjacency = new HashMap<>();
  private int adjacencyRouteCount = -1;
  private final Map<String, Map<String, Integer>> hopCache = new HashMap<>();

  World(GameConnection connection) {
    this.c = connection;
  }

  void setConnection(GameConnection connection) {
    this.c = connection;
    cache.clear();
  }

  void beginTick() {
    cache.clear();
  }

  void invalidate(String... types) {
    for (String type : types) cache.keySet().removeIf(k -> k.startsWith(type + "|"));
  }

  JsonNode q(String type) {
    return q(type, Map.of());
  }

  JsonNode q(String type, Map<String, Object> payload) {
    String key = type + "|" + payload;
    JsonNode cached = cache.get(key);
    if (cached != null) return cached;
    JsonNode result = c.call(type, payload);
    if (result == null) result = com.fasterxml.jackson.databind.node.NullNode.getInstance();
    cache.put(key, result);
    return result;
  }

  // --- Spieler ---------------------------------------------------------------

  List<JsonNode> players() {
    return list(q("players"));
  }

  JsonNode player(String id) {
    for (JsonNode p : players()) if (Json.eq(text(p, "id"), id)) return p;
    return null;
  }

  JsonNode playerByName(String name) {
    for (JsonNode p : players()) if (Json.eq(text(p, "name"), name)) return p;
    return null;
  }

  // --- Kolonien ----------------------------------------------------------------

  List<JsonNode> ownColonies() {
    return list(q("colonies"));
  }

  JsonNode colony(String id) {
    return q("colony", Map.of("id", id));
  }

  List<JsonNode> coloniesInSystem(String systemId) {
    return list(q("coloniesInSystem", Map.of("systemId", systemId)));
  }

  List<JsonNode> planetsInSystem(String systemId) {
    return list(q("planetsInSystem", Map.of("systemId", systemId)));
  }

  JsonNode planet(String id) {
    return q("planet", Map.of("id", id));
  }

  List<JsonNode> buildings(String colonyId) {
    return list(q("buildings", Map.of("colonyId", colonyId)));
  }

  int buildingLevel(String colonyId, String typeId) {
    for (JsonNode b : buildings(colonyId)) if (Json.eq(text(b, "typeId"), typeId)) return Json.integer(b, "level");
    return 0;
  }

  boolean buildingPending(String colonyId, String typeId) {
    for (JsonNode b : buildings(colonyId)) if (Json.eq(text(b, "typeId"), typeId)) return !isNull(b.path("pendingOrder"));
    return false;
  }

  List<JsonNode> warehouse(String colonyId) {
    return list(q("warehouse", Map.of("colonyId", colonyId)));
  }

  double stock(String colonyId, String productTypeId) {
    for (JsonNode w : warehouse(colonyId)) if (Json.eq(text(w, "productTypeId"), productTypeId)) return Json.dbl(w, "quantity");
    return 0;
  }

  JsonNode stats(String colonyId) {
    return q("colonyStats", Map.of("id", colonyId));
  }

  double population(String colonyId) {
    return Json.dbl(q("population", Map.of("id", colonyId)), "currentCount");
  }

  boolean blackout(String colonyId) {
    return q("isBlackout", Map.of("colonyId", colonyId)).asBoolean(false);
  }

  double powerCoverage(String colonyId) {
    return q("powerCoverage", Map.of("colonyId", colonyId)).asDouble(1);
  }

  /** Energiespeicher der Kolonie (Umsetzungskonzept/32): stored, reserveTarget, automatic, warehouseStock. */
  JsonNode energyStorage(String colonyId) {
    return q("energyStorage", Map.of("colonyId", colonyId));
  }

  JsonNode consumptionCoverage(String colonyId) {
    return q("consumptionCoverage", Map.of("colonyId", colonyId));
  }

  List<JsonNode> productionQueue(String colonyId) {
    return list(q("productionQueue", Map.of("colonyId", colonyId)));
  }

  List<JsonNode> shipyardQueue(String colonyId) {
    return list(q("shipyardQueue", Map.of("colonyId", colonyId)));
  }

  List<JsonNode> recruitmentQueue(String colonyId) {
    return list(q("recruitmentQueue", Map.of("colonyId", colonyId)));
  }

  /** Garnison einer Kolonie – öffentlich abfragbar, also auch für fremde Kolonien (Aufklärung). */
  JsonNode garrison(String colonyId) {
    JsonNode g = q("groundForces", Map.of("colonyId", colonyId));
    return isNull(g) ? null : g;
  }

  /** Verkaufsorders im System – die Bevölkerung einer Kolonie kauft nur aus denen an ihrem eigenen Handelsposten (depotColonyId, Umsetzungskonzept/36). */
  List<JsonNode> sellOrders(String systemId) {
    return list(q("sellOrders", Map.of("systemId", systemId)));
  }

  private Map<String, Double> shipTankCapacities;

  /**
   * Fassungsvermögen des Treibstofftanks EINER Flotte in Eleriumkapseln –
   * Summe der Schiffstanks aus {@code shipTypes}. Seit
   * Umsetzungskonzept/34_...md hängt der Sprungverbrauch an der Schiffsmasse;
   * eine feste Kapselzahl je Schiff (der frühere Bot-Richtwert) lässt schwere
   * Flotten mit leerem Tank stehen.
   */
  double fleetTankCapacity(JsonNode fleet) {
    if (shipTankCapacities == null) {
      Map<String, Double> all = new HashMap<>();
      for (JsonNode d : list(c.call("shipTypes", Map.of()))) {
        all.put(text(d, "productTypeId"), Json.dbl(d, "fuelTankCapacity"));
      }
      shipTankCapacities = all;
    }
    double sum = 0;
    for (JsonNode s : fleet.path("ships")) {
      sum += shipTankCapacities.getOrDefault(text(s, "shipProductTypeId"), 0.0) * Json.dbl(s, "quantity");
    }
    return sum;
  }

  private Double troopCapacityPerTransport;

  /**
   * Wie viele Soldaten EIN Mannschaftstransporter fasst – aus dem Katalog des
   * Servers ({@code shipTypes}), nicht aus einer Bot-Konstante. Der frühere
   * Richtwert im {@code Catalog} stand auf 1000, während das Schiff seit der
   * Massenskala (Umsetzungskonzept/27) 27 Soldaten trägt: die Landungsoperation
   * forderte deshalb dauerhaft mehr Soldaten an, als an Bord passten, und blieb
   * endlos in der Verladung stehen.
   */
  double troopCapacityPerTransport() {
    if (troopCapacityPerTransport == null) {
      double found = 0;
      for (JsonNode d : list(c.call("shipTypes", Map.of()))) {
        if (Json.eq(text(d, "productTypeId"), Catalog.TROOP_TRANSPORT)) found = Json.dbl(d, "troopCapacity");
      }
      troopCapacityPerTransport = found > 0 ? found : 1;
    }
    return troopCapacityPerTransport;
  }

  private Map<String, double[]> productSizes;

  /**
   * Masse (kg) und Volumen (m³) EINES Stücks aus dem Produktkatalog des Servers.
   * Der Bot braucht beides, um vor dem Beladen auszurechnen, wie viel überhaupt
   * in seinen Frachter passt: Eine schwere Drohne wiegt 583 t, ein Frachter
   * trägt 28,4 kt – also 48 Stück. Ohne die Rechnung lief die Landungsoperation
   * in „Massekapazität der Flotte reicht nicht aus" und blieb hängen.
   */
  double[] productSize(String productTypeId) {
    if (productSizes == null) {
      Map<String, double[]> all = new HashMap<>();
      for (JsonNode p : list(c.call("productTypes", Map.of()))) {
        all.put(text(p, "id"), new double[]{Json.dbl(p, "massKg"), Json.dbl(p, "volumeM3")});
      }
      productSizes = all;
    }
    return productSizes.getOrDefault(productTypeId, new double[]{0, 0});
  }

  private Map<String, double[]> shipCargo;

  /** Frachtkapazität (kg, m³) EINES Schiffs dieses Typs aus dem Schiffskatalog des Servers. */
  double[] shipCargoCapacity(String shipProductTypeId) {
    if (shipCargo == null) {
      Map<String, double[]> all = new HashMap<>();
      for (JsonNode d : list(c.call("shipTypes", Map.of()))) {
        all.put(text(d, "productTypeId"), new double[]{Json.dbl(d, "cargoMassKg"), Json.dbl(d, "cargoVolumeM3")});
      }
      shipCargo = all;
    }
    return shipCargo.getOrDefault(shipProductTypeId, new double[]{0, 0});
  }

  /** Hat dieser Kommandant das System schon erforscht (Rohstoffkonzentrationen aufgedeckt)? */
  boolean hasExploredSystem(String systemId) {
    return q("hasExploredSystem", Map.of("systemId", systemId)).asBoolean(false);
  }

  /**
   * Frachtkapazität einer Flotte samt Auslastung (fleetCargoCapacity, ohne
   * Produktbezug). Wie {@link #troopCapacity} gegen eine verschwundene Flotte
   * abgesichert – die Abfrage läuft im Takt vor den abgesicherten Modulen.
   */
  JsonNode cargoCapacity(String fleetId) {
    try {
      return q("fleetCargoCapacity", Map.of("fleetId", fleetId));
    } catch (de.nebula.npcbot.ws.CommandException e) {
      return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }
  }

  private Map<String, Map<String, Double>> recipes;

  /** Rezept eines Produkts aus dem statischen Katalog ({@code productTypes}) – einmal geladen, gilt für den ganzen Lauf. */
  Map<String, Double> recipe(String productTypeId) {
    if (recipes == null) {
      Map<String, Map<String, Double>> all = new HashMap<>();
      for (JsonNode p : list(c.call("productTypes", Map.of()))) {
        Map<String, Double> r = new LinkedHashMap<>();
        for (JsonNode in : p.path("recipe")) r.put(text(in, "inputProductTypeId"), Json.dbl(in, "quantity"));
        all.put(text(p, "id"), r);
      }
      recipes = all;
    }
    return recipes.getOrDefault(productTypeId, Map.of());
  }

  /**
   * Wie viel {@code ingredient} die komplette Kette von {@code quantity} × {@code productTypeId}
   * höchstens aus dem Lager zieht (rekursiv über den Katalog; der Kettenplaner deckt jeden
   * Nicht-Wurzel-Schritt zuerst aus dem Lagerbestand). Obergrenze, weil bereits vorhandene
   * Zwischenprodukte den Bedarf verringern.
   */
  double chainDemand(String productTypeId, double quantity, String ingredient) {
    double total = 0;
    for (Map.Entry<String, Double> in : recipe(productTypeId).entrySet()) {
      double q = in.getValue() * quantity;
      if (in.getKey().equals(ingredient)) total += q;
      else total += chainDemand(in.getKey(), q, ingredient);
    }
    return total;
  }

  JsonNode previewChain(String colonyId, String productTypeId, double quantity) {
    return q("previewProductionChain", Map.of("colonyId", colonyId, "productTypeId", productTypeId, "quantity", quantity));
  }

  List<JsonNode> colonizations() {
    return list(q("colonizations"));
  }

  double wallet() {
    return Json.dbl(q("wallet"), "balance");
  }

  /**
   * Guthaben der KOLONIALBEVÖLKERUNG. Es ist die Kaufkraft, aus der die
   * Konsumeinnahmen des Kommandanten kommen – liegt hier viel Geld, während die
   * Versorgung zu 100 % gedeckt ist, verkauft der Bot zu billig (siehe
   * {@code Economy.adjustPrices}).
   */
  double populationWallet(String colonyId) {
    return Json.dbl(q("populationWallet", Map.of("colonyId", colonyId)), "balance");
  }

  // --- Flotten ------------------------------------------------------------------

  List<JsonNode> ownFleets() {
    return list(q("fleets"));
  }

  JsonNode ownFleet(String fleetId) {
    for (JsonNode f : ownFleets()) if (Json.eq(text(f, "id"), fleetId)) return f;
    return null;
  }

  List<JsonNode> allFleets() {
    return list(q("allFleets"));
  }

  List<JsonNode> blockadesInSystem(String systemId) {
    return list(q("blockadesInSystem", Map.of("systemId", systemId)));
  }

  List<JsonNode> attackableFleetsInSystem(String systemId) {
    return list(q("attackableFleetsInSystem", Map.of("systemId", systemId)));
  }

  List<JsonNode> activeBattles() {
    return list(q("activeBattles"));
  }

  List<JsonNode> activeGroundBattles() {
    return list(q("activeGroundBattles"));
  }

  List<JsonNode> landedGroundForces() {
    return list(q("landedGroundForces"));
  }

  List<JsonNode> attackableColoniesForGroup(String groupId) {
    return list(q("attackableColoniesForGroup", Map.of("groupId", groupId)));
  }

  double maxLoadable(String fleetId, String productTypeId) {
    return Json.dbl(q("fleetCargoCapacity", Map.of("fleetId", fleetId, "productTypeId", productTypeId)), "maxLoadableQuantity");
  }

  /**
   * Truppenkapazität einer Flotte. Eine verschwundene Flotte (gefallen,
   * zusammengelegt, aufgelöst) beantwortet der Server mit „Unbekannte Flotte" –
   * und weil diese Abfrage im Takt VOR den abgesicherten Modulen steht, riss
   * die Ausnahme den ganzen Bot-Takt mit: Im Testlauf stand ein Kommandant
   * dadurch über zwanzig Minuten still ("Takt: Befehl abgelehnt: Unbekannte
   * Flotte", jede Sekunde). Hier wird sie deshalb zu einem leeren Ergebnis.
   */
  JsonNode troopCapacity(String fleetId) {
    try {
      return q("fleetTroopCapacity", Map.of("fleetId", fleetId));
    } catch (de.nebula.npcbot.ws.CommandException e) {
      return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }
  }

  // --- Diplomatie / Nachrichten -------------------------------------------------

  List<JsonNode> activeWars() {
    return list(q("activeWars"));
  }

  List<JsonNode> treaties() {
    return list(q("treaties"));
  }

  List<JsonNode> incomingTreatyOffers() {
    return list(q("incomingTreatyOffers"));
  }

  List<JsonNode> incomingPeaceOffers() {
    return list(q("incomingPeaceOffers"));
  }

  List<JsonNode> inbox() {
    return list(q("inbox"));
  }

  // --- Handelsgilde ---------------------------------------------------------------

  List<JsonNode> hubOrders(String systemId) {
    return list(q("hubOrders", Map.of("systemId", systemId)));
  }

  List<JsonNode> hubDepot(String systemId) {
    return list(q("hubDepot", Map.of("systemId", systemId)));
  }

  // --- Galaxie ------------------------------------------------------------------------

  private JsonNode systemsSource;
  private Map<String, JsonNode> systemsById = Map.of();

  /**
   * Systeme nach Id. Die Antwort auf {@code visibleSystems} ist je Takt gecacht;
   * die Map dazu wird nur neu gebaut, wenn die Antwort eine andere ist –
   * {@link #systemName} läuft in Schleifen über Flotten und Ziele und baute
   * die Map aus 200 Systemen vorher bei jedem Aufruf neu.
   */
  Map<String, JsonNode> systems() {
    JsonNode source = q("visibleSystems");
    if (source != systemsSource) {
      Map<String, JsonNode> out = new LinkedHashMap<>();
      for (JsonNode s : list(source)) out.put(text(s, "id"), s);
      systemsSource = source;
      systemsById = out;
    }
    return systemsById;
  }

  String systemName(String systemId) {
    JsonNode s = systems().get(systemId);
    return s == null ? systemId : text(s, "name");
  }

  Set<String> hubSystemIds() {
    Set<String> hubs = new HashSet<>();
    for (JsonNode s : systems().values()) if (Json.bool(s, "isTradeHub")) hubs.add(text(s, "id"));
    return hubs;
  }

  private void ensureAdjacency() {
    List<JsonNode> routes = list(q("galaxyRoutes"));
    if (routes.size() == adjacencyRouteCount) return;
    Map<String, List<String>> adj = new HashMap<>();
    for (JsonNode r : routes) {
      String a = text(r, "a");
      String b = text(r, "b");
      adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
      adj.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }
    adjacency = adj;
    adjacencyRouteCount = routes.size();
    hopCache.clear();
  }

  /** Gateway-Sprünge zwischen zwei Systemen (BFS über das öffentliche Netz), {@code Integer.MAX_VALUE} ohne Pfad. */
  int hops(String from, String to) {
    if (from == null || to == null) return Integer.MAX_VALUE;
    if (from.equals(to)) return 0;
    ensureAdjacency();
    Map<String, Integer> dist = hopCache.get(from);
    if (dist == null) {
      dist = new HashMap<>();
      Deque<String> queue = new ArrayDeque<>();
      dist.put(from, 0);
      queue.add(from);
      while (!queue.isEmpty()) {
        String cur = queue.poll();
        int d = dist.get(cur);
        for (String n : adjacency.getOrDefault(cur, List.of())) {
          if (!dist.containsKey(n)) {
            dist.put(n, d + 1);
            queue.add(n);
          }
        }
      }
      hopCache.put(from, dist);
    }
    return dist.getOrDefault(to, Integer.MAX_VALUE);
  }

  String nearestHub(String fromSystemId) {
    String best = null;
    int bestHops = Integer.MAX_VALUE;
    for (String hub : hubSystemIds()) {
      int h = hops(fromSystemId, hub);
      if (h < bestHops) {
        bestHops = h;
        best = hub;
      }
    }
    return best;
  }

  // --- Hilfsrechnungen auf Flotten/Verbänden ----------------------------------------

  static boolean hasShip(JsonNode fleet, String shipType) {
    for (JsonNode s : fleet.path("ships")) if (Json.eq(text(s, "shipProductTypeId"), shipType) && Json.dbl(s, "quantity") > 0) return true;
    return false;
  }

  static double shipCount(JsonNode fleet, String shipType) {
    for (JsonNode s : fleet.path("ships")) if (Json.eq(text(s, "shipProductTypeId"), shipType)) return Json.dbl(s, "quantity");
    return 0;
  }

  static int shipCount(JsonNode fleet) {
    int sum = 0;
    for (JsonNode s : fleet.path("ships")) sum += (int) Json.dbl(s, "quantity");
    return sum;
  }

  static boolean hasWarships(JsonNode fleet) {
    for (JsonNode s : fleet.path("ships")) {
      if (Catalog.WARSHIP_TYPES.contains(text(s, "shipProductTypeId")) && Json.dbl(s, "quantity") > 0) return true;
    }
    return false;
  }

  static double strength(JsonNode fleet) {
    double sum = 0;
    for (JsonNode s : fleet.path("ships")) {
      sum += Catalog.SHIP_MILITARY_WEIGHT.getOrDefault(text(s, "shipProductTypeId"), 0.0) * Json.dbl(s, "quantity");
    }
    return sum;
  }

  static boolean stationed(JsonNode fleet) {
    return fleet != null && "Stationed".equals(text(fleet, "status"));
  }

  static double cargoQty(JsonNode fleet, String productTypeId) {
    for (JsonNode c : fleet.path("cargo")) if (Json.eq(text(c, "productTypeId"), productTypeId)) return Json.dbl(c, "quantity");
    return 0;
  }

  /** Einheiten eines Verbands (aktiv + Reserve) je Typ. */
  static int unitCount(JsonNode group, String unitType) {
    if (isNull(group)) return 0;
    for (JsonNode u : group.path("units")) {
      if (Json.eq(text(u, "unitProductTypeId"), unitType)) return Json.integer(u, "activeCount") + Json.integer(u, "reserveCount");
    }
    return 0;
  }

  static int activeCount(JsonNode group, String unitType) {
    if (isNull(group)) return 0;
    for (JsonNode u : group.path("units")) if (Json.eq(text(u, "unitProductTypeId"), unitType)) return Json.integer(u, "activeCount");
    return 0;
  }

  static int droneCount(JsonNode group) {
    int sum = 0;
    for (String d : Catalog.DRONES) sum += unitCount(group, d);
    return sum;
  }

  /** Kampfwert der AKTIVEN Drohnen eines Verbands (nur sie kämpfen, Mechanik/05 §3). */
  static double activeDroneValue(JsonNode group) {
    double sum = 0;
    for (String d : Catalog.DRONES) sum += activeCount(group, d) * Catalog.DRONE_VALUE.get(d);
    return sum;
  }

  /** Häufigste Drohnenklasse eines Verbands – Grundlage der Konterwahl. */
  static String dominantDrone(JsonNode group) {
    String best = null;
    int bestCount = 0;
    for (String d : Catalog.DRONES) {
      int n = unitCount(group, d);
      if (n > bestCount) {
        bestCount = n;
        best = d;
      }
    }
    return best;
  }
}
