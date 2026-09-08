package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;
import de.nebula.npcbot.ws.GameConnection;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ein einzelner NPC-Bot-Prozess – EIN Kommandant, angemeldet über das
 * reguläre WebSocket-Protokoll wie jeder menschliche Spieler (siehe
 * Umsetzungskonzept/14_...md, Teil 2: "verhalten sich wie echte Spieler",
 * KEIN privilegierter Direktzugriff auf den Server-Zustand). Startet als
 * eigener Prozess über {@code --index}/{@code --camp}/{@code --server}
 * (siehe {@code run-army.sh}) und läuft dann als autonome Entscheidungs-
 * schleife, bis der Prozess beendet wird.
 *
 * <p>Die Klasse ist bewusst NICHT in mehrere Kollaborateure aufgeteilt
 * (Wirtschaft/Diplomatie/Kampf als eigene Klassen) – bei genau einem
 * einfachen Zustandsautomaten pro Bot-Prozess wäre das reine
 * Indirektionskosten ohne Wiederverwendungsnutzen.</p>
 */
public class Bot {

  /**
   * Entscheidungstakt des Bots, in SPIELSTUNDEN (8 Spielstunden = 8 s Realzeit
   * bei Tempo 1, unveränderter Ausgangswert). Bewusst an der Spieluhr und
   * nicht an der Realzeit: bei erhöhtem Tempo-Regler wächst der Bot-Kolonie
   * je Realsekunde entsprechend mehr zu, und ein starrer Realzeit-Takt würde
   * den Bot gegenüber der beschleunigten Welt träge machen.
   */
  private static final long TICK_INTERVAL_MS = GameSpeed.hoursToMs(8);
  /**
   * Mindestlaufzeit vor dem ersten möglichen Angriff (siehe {@link #tryLaunchAttack}),
   * in SPIELSTUNDEN. Ein einzelnes Kampfschiff braucht in diesem Prototyp wegen
   * seiner tiefen, mehrstufigen Fertigungskette (rohstoffnahe Vorprodukte ->
   * Baugruppen -> Schiffsrumpf) je nach Ausgangslager rund 36 Spielstunden bis
   * zur ersten fertigen Einheit. Ein Angriff wartet deshalb NICHT auf
   * tatsächlichen Flottenzuwachs (das würde die Verifikation unpraktikabel in
   * die Länge ziehen), sondern nur auf diese Mindest-Aufbauzeit – die
   * eigentliche Stärkeschätzung in {@link #tryLaunchAttack} vergleicht dann die
   * (durch die zufällige Startausstattung ohnehin unterschiedlich starken)
   * aktuellen Flotten beider Seiten ganz reell über den Server. Bewusst
   * dokumentierte Vereinfachung, siehe Umsetzungskonzept/14_...md, Teil 2.
   */
  private static final long ATTACK_READY_DELAY_MS = GameSpeed.hoursToMs(36);
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private enum AttackState {IDLE, TRAVELING, ENGAGING}

  private final int index;
  private final String camp;
  private final String enemyCamp;
  private final String botName;
  private final String server;

  private String playerId;
  private String homeColonyId;
  private String homePlanetId;
  private String homeSystemId;
  private String combatFleetId;
  private int initialCombatShipCount;
  private long readyAtMs;
  private String freighterFleetId;
  /** Nächstgelegene Handelsgilde-Station (per BFS, siehe {@link #findNearestTradeHub}) – einmalig ermittelt, Topologie ist statisch. */
  private String hubSystemId;

  private enum TradeState {IDLE, TRAVELING_TO_HUB, TRAVELING_HOME}

  private TradeState tradeState = TradeState.IDLE;

  private final boolean coordinator;
  private String mySpecialtyProduct;
  private boolean specializationQueued;
  private final Set<String> specializationSentTo = new HashSet<>();

  private final Map<String, JsonNode> playersById = new LinkedHashMap<>();
  private final Map<String, String> playerIdByName = new LinkedHashMap<>();
  private final Set<String> declaredWarWith = new HashSet<>();

  private boolean blockading;
  /** Baustoffe, für die bereits ein Produktionsauftrag läuft – wird geleert, sobald die Warteschlange sie nicht mehr enthält. */
  private final Set<String> materialOrdersQueued = new HashSet<>();
  private int shipTypeRotation;

  private AttackState attackState = AttackState.IDLE;
  private String attackTargetSystemId;
  private int startAttackShipCount;
  private String currentBattleId;

  private final GameConnection connection;

  public Bot(int index, String camp, String server) {
    this.index = index;
    this.camp = camp;
    this.enemyCamp = camp.equals("NORD") ? "SUED" : "NORD";
    this.server = server;
    this.botName = String.format("NPC-%s-%02d", displayName(camp), index);
    this.coordinator = index == 1;
    this.connection = new GameConnection(server);
  }

  private static String displayName(String camp) {
    return camp.equals("NORD") ? "Nord" : "Sued";
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> opts = parseArgs(args);
    String server = opts.getOrDefault("server", "ws://localhost:8080/game");
    int index = Integer.parseInt(opts.getOrDefault("index", "1"));
    String camp = opts.getOrDefault("camp", "NORD").toUpperCase();
    if (!camp.equals("NORD") && !camp.equals("SUED")) {
      throw new IllegalArgumentException("--camp muss NORD oder SUED sein, war: " + camp);
    }
    Bot bot = new Bot(index, camp, server);
    bot.run();
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> result = new HashMap<>();
    for (String arg : args) {
      if (!arg.startsWith("--") || !arg.contains("=")) continue;
      String[] parts = arg.substring(2).split("=", 2);
      result.put(parts[0], parts[1]);
    }
    return result;
  }

  private void run() throws Exception {
    log("Starte, verbinde mit " + server + " ...");
    connection.connect();
    registerAndBootstrap();
    log("Bereit als " + botName + " (playerId=" + playerId + ", Heimatsystem=" + homeSystemId
        + ", Kampfflotte=" + combatFleetId + ", Startstärke=" + initialCombatShipCount + " Schiffe, Koordinator=" + coordinator + ")");
    while (true) {
      try {
        tick();
      } catch (Exception e) {
        log("Tick-Fehler: " + e.getMessage());
      }
      Thread.sleep(TICK_INTERVAL_MS);
    }
  }

  private void registerAndBootstrap() {
    JsonNode player = connection.call("registerPlayer", Map.of(
        "commanderName", botName, "homeworldName", botName + "-Heimat", "role", "Npc", "campId", camp));
    playerId = text(player, "id");
    homeColonyId = text(player, "homeworldColonyId");
    homeSystemId = text(player, "homeSystemId");

    JsonNode colony = connection.call("colony", Map.of("id", homeColonyId));
    homePlanetId = text(colony, "planetId");

    JsonNode fleets = connection.call("fleets");
    for (JsonNode f : fleets) {
      if (isCombatFleet(f)) {
        combatFleetId = text(f, "id");
        initialCombatShipCount = shipCount(f);
      } else if (hasFreighter(f)) {
        freighterFleetId = text(f, "id");
      }
    }
    if (combatFleetId == null) throw new IllegalStateException("Keine Kampfflotte in der Startausstattung gefunden.");
    readyAtMs = System.currentTimeMillis() + ATTACK_READY_DELAY_MS;
  }

  // --- Tick-Ablauf -----------------------------------------------------------

  private void tick() {
    safe(this::refreshPlayers);
    safe(this::coordinateSpecialization);
    safe(this::maintainEconomy);
    safe(this::maintainTrade);
    safe(this::maintainDiplomacy);
    safe(this::maintainDefense);
    safe(this::maintainOffense);
  }

  private void safe(Runnable step) {
    try {
      step.run();
    } catch (CommandException e) {
      log("Befehl abgelehnt: " + e.getMessage());
    }
  }

  /** Aktualisiert die bekannte Spielerliste – Grundlage für Lagerzugehörigkeit, Zielwahl und Kriegserklärungen. */
  private void refreshPlayers() {
    JsonNode players = connection.call("players");
    for (JsonNode p : players) {
      String id = text(p, "id");
      String name = text(p, "name");
      playersById.put(id, p);
      playerIdByName.put(name, id);
    }
  }

  private boolean isOwnCampName(String name) {
    return name.startsWith("NPC-" + displayName(camp) + "-");
  }

  private boolean isEnemyCampName(String name) {
    return name.startsWith("NPC-" + displayName(enemyCamp) + "-");
  }

  // --- Teil 1: Spezialisierungs-Koordination über das Nachrichtensystem ------

  /**
   * Deterministische, ABSICHTLICH einfache Koordination (keine Verhandlung,
   * siehe Umsetzungskonzept/14_...md, Teil 2): der Bot mit {@code index==1}
   * je Lager berechnet für jeden Lagerkollegen ein festes Produkt aus
   * {@link Catalog#SPECIALTY_PRODUCTS} anhand von dessen Index und verschickt
   * es per {@code sendMessage}, sobald der jeweilige Kollege registriert ist
   * (Retry über mehrere Ticks, da die Startreihenfolge der 20 Prozesse nicht
   * garantiert ist). Alle neun anderen lesen ihre Zuteilung passiv aus der
   * Inbox.
   */
  private void coordinateSpecialization() {
    if (coordinator) {
      if (mySpecialtyProduct == null) {
        mySpecialtyProduct = Catalog.specialtyForIndex(index);
        log("Koordinator – eigene Spezialisierung: " + mySpecialtyProduct);
      }
      for (Map.Entry<String, String> e : playerIdByName.entrySet()) {
        String name = e.getKey();
        String id = e.getValue();
        if (id.equals(playerId) || !isOwnCampName(name) || specializationSentTo.contains(id)) continue;
        int otherIndex = parseIndex(name);
        if (otherIndex <= 0) continue;
        String product = Catalog.specialtyForIndex(otherIndex);
        connection.call("sendMessage", Map.of("toPlayerId", id, "subject", "Spezialisierung", "body", product));
        specializationSentTo.add(id);
        log("Spezialisierung an " + name + " gesendet: " + product);
      }
    } else if (mySpecialtyProduct == null) {
      String coordinatorId = playerIdByName.get("NPC-" + displayName(camp) + "-01");
      if (coordinatorId == null) return;
      JsonNode inbox = connection.call("inbox");
      for (JsonNode msg : inbox) {
        if (text(msg, "fromPlayerId").equals(coordinatorId) && "Spezialisierung".equals(text(msg, "subject"))) {
          mySpecialtyProduct = text(msg, "body");
          connection.call("markMessageRead", Map.of("id", text(msg, "id")));
          log("Spezialisierung vom Koordinator übernommen: " + mySpecialtyProduct);
          break;
        }
      }
    }
  }

  private static int parseIndex(String botName) {
    try {
      return Integer.parseInt(botName.substring(botName.lastIndexOf('-') + 1));
    } catch (Exception e) {
      return -1;
    }
  }

  // --- Wirtschaft: Gebäude, Produktionswarteschlange, Werft ------------------

  private void maintainEconomy() {
    if (mySpecialtyProduct != null && !specializationQueued) {
      // Kleine Charge OHNE Dauerauftrag (Umsetzungskonzept/17_...md): bei
      // Industriekomplex 1 ist die sequentielle Warteschlange der Engpass –
      // ein ×20-Dauerauftrag würde Nahrung/Medizin/Elerium für Stunden
      // blockieren und die Kolonie in den Blackout treiben.
      connection.call("queueProduction", Map.of(
          "colonyId", homeColonyId, "productTypeId", mySpecialtyProduct,
          "quantity", 3.0, "autoProduceMissing", true, "requeueOnComplete", false));
      specializationQueued = true;
      log("Spezialisierungsproduktion eingereiht: " + mySpecialtyProduct + " x3");
    }

    JsonNode queue = connection.call("productionQueue", Map.of("colonyId", homeColonyId));
    Set<String> queuedProducts = new HashSet<>();
    for (JsonNode q : queue) queuedProducts.add(text(q, "productTypeId"));
    materialOrdersQueued.retainAll(queuedProducts);
    // Spezialisierung als kleine Charge immer wieder nachlegen, sobald sie abgearbeitet ist.
    if (mySpecialtyProduct != null && !queuedProducts.contains(mySpecialtyProduct)) specializationQueued = false;
    // Elerium-Nachschub: der Startauftrag ist auf Infrastruktur 2/3 ausgelegt –
    // jede weitere Stufe verbraucht überlinear mehr, deshalb bei knappem Lager
    // eine Extra-Charge (einmalig, solange sie in der Warteschlange steht).
    if (!queuedProducts.contains("p_elerium_stabil") || eleriumStock() < 10) {
      if (!materialOrdersQueued.contains("p_elerium_stabil") && eleriumStock() < 10) {
        try {
          connection.call("queueProduction", Map.of("colonyId", homeColonyId, "productTypeId", "p_elerium_stabil",
              "quantity", 4.0, "autoProduceMissing", true, "requeueOnComplete", false));
          materialOrdersQueued.add("p_elerium_stabil");
          log("Elerium-Nachschub eingereiht (Lager " + eleriumStock() + ").");
        } catch (CommandException e) {
          log("Elerium-Nachschub abgelehnt: " + e.getMessage());
        }
      }
    }

    JsonNode buildings = connection.call("buildings", Map.of("colonyId", homeColonyId));
    Map<String, Integer> levelByType = new HashMap<>();
    Set<String> pendingTypes = new HashSet<>();
    for (JsonNode b : buildings) {
      String typeId = text(b, "typeId");
      levelByType.put(typeId, b.path("level").asInt(0));
      if (!b.path("pendingOrder").isMissingNode() && !b.path("pendingOrder").isNull()) pendingTypes.add(typeId);
    }
    for (String typeId : Catalog.BUILD_PRIORITY) {
      int cap = Catalog.BUILD_CAP.get(typeId);
      int level = levelByType.getOrDefault(typeId, 0);
      if (level >= cap || pendingTypes.contains(typeId)) continue;
      try {
        connection.call("queueBuilding", Map.of("colonyId", homeColonyId, "buildingTypeId", typeId));
        log("Ausbau eingereiht: " + typeId + " (Stufe " + level + " -> " + (level + 1) + ")");
        break;
      } catch (CommandException e) {
        // Umsetzungskonzept/17_...md: die beiden strukturellen Ablehnungen
        // löst der Bot selbst auf – ohne das stünde er dauerhaft still.
        if (e.getMessage().contains("Bebauungsplatz")) {
          if (!pendingTypes.contains(Catalog.INFRASTRUCTURE)) {
            try {
              connection.call("queueBuilding", Map.of("colonyId", homeColonyId, "buildingTypeId", Catalog.INFRASTRUCTURE));
              log("Kein Bebauungsplatz – Infrastruktur-Ausbau eingereiht.");
            } catch (CommandException infra) {
              queueMissingMaterials(infra.getMessage());
            }
          }
          break;
        }
        if (queueMissingMaterials(e.getMessage())) break;
        // sonst z. B. nicht genug Credits für DIESE Stufe – nächstgünstigere Priorität versuchen
      }
    }

    int shipyardLevel = levelByType.getOrDefault("b_shipyard", 0);
    JsonNode shipyardQueue = connection.call("shipyardQueue", Map.of("colonyId", homeColonyId));
    if (shipyardLevel >= 1 && shipyardQueue.isEmpty() && currentCombatShipCount() < Catalog.MAX_COMBAT_SHIPS) {
      String shipType = Catalog.WARSHIP_TYPES.get(shipTypeRotation % Catalog.WARSHIP_TYPES.size());
      shipTypeRotation++;
      connection.call("queueShip", Map.of(
          "colonyId", homeColonyId, "shipProductTypeId", shipType,
          "quantity", 2.0, "autoProduceMissing", true, "requeueOnComplete", false));
      log("Werftauftrag eingereiht: " + shipType + " x2");
    }
  }

  /**
   * Parst "Fehlende Baustoffe: p_stahl (10 benötigt, 3 vorhanden), ..." und reiht ALLE
   * fehlenden Produkte als EINEN gebündelten Auftrag ein (statt je Produkt einen eigenen):
   * einer der Baustoffe kann Vorprodukt eines anderen sein (z. B. `p_leiterbuendel` enthält
   * `p_leitermetall`), separate Aufträge würden sich dann gegenseitig den gerade erst
   * eingelagerten Bestand wieder wegnehmen und die Bedingung nie gleichzeitig erfüllen (siehe
   * TODO.md). `ProductionCommands.queueProductionBundle` rechnet den gemeinsamen Bedarf in
   * einem Rutsch. Nur einmalig, solange der Auftrag in der Warteschlange steht.
   */
  private boolean queueMissingMaterials(String message) {
    if (message == null || !message.startsWith("Fehlende Baustoffe")) return false;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(p_[a-z_]+) \\((\\d+) benötigt, (\\d+) vorhanden\\)").matcher(message);
    LinkedHashMap<String, Double> products = new LinkedHashMap<>();
    while (m.find()) {
      String productTypeId = m.group(1);
      double missing = Double.parseDouble(m.group(2)) - Double.parseDouble(m.group(3));
      if (materialOrdersQueued.contains(productTypeId)) continue;
      products.put(productTypeId, (double) Math.max(1, Math.ceil(missing)));
    }
    if (products.isEmpty()) return true;
    try {
      connection.call("queueProductionBundle", Map.of(
          "colonyId", homeColonyId, "products", products, "autoProduceMissing", true, "requeueOnComplete", false));
      materialOrdersQueued.addAll(products.keySet());
      log("Baustoff-Produktion gebündelt eingereiht: " + products.entrySet().stream()
          .map(e -> e.getKey() + " x" + e.getValue().longValue()).collect(java.util.stream.Collectors.joining(", ")));
    } catch (CommandException e) {
      log("Baustoff-Produktion abgelehnt: " + e.getMessage());
    }
    return true;
  }

  // --- Teil 2: Handel an Handelsgilde-Stationen (Umsetzungskonzept/22_...md, §H) ---

  /**
   * Frachter-Kreislauf je Bot: Heimatkolonie beladen -> zur nächstgelegenen
   * Handelsgilde-Station fliegen -> dort die eigene Spezialware ins
   * unbegrenzte Stationsdepot entladen und sofort als Verkaufs-Order
   * einstellen (Preis = bestes vorhandenes Kaufgebot, garantiert sofortige
   * Ausführung gegen Market-Maker oder andere Kommandanten, siehe
   * {@link #sellCargoAtHub}) -> mit den Erlösen genau die Grundbedarfe/
   * Baustoffe nachkaufen, die die eigene Kolonie nicht selbst herstellt
   * (siehe {@link #buyImportsAtHub}) -> zurück nach Hause -> Fracht ins
   * Kolonielager entladen, wo sie automatisch die "schlafenden"
   * Auto-Relist-Verkaufsorders der eigenen Bevölkerung bzw. die nächste
   * Ausbaustufe speist ({@code MarketCommands.replenishDormantSellOrders}
   * bzw. {@link #queueMissingMaterials}). Ein einzelner Frachter je Bot
   * reicht für diesen Kreislauf – bewusst keine eigene Frachterflotten-
   * Beschaffung, siehe {@link #hasFreighter}.
   */
  private void maintainTrade() {
    if (mySpecialtyProduct == null) return; // Handelsrolle noch nicht zugeteilt
    if (freighterFleetId == null) {
      findFreighterFleet();
      if (freighterFleetId == null) return;
    }
    switch (tradeState) {
      case IDLE -> tryStartExport();
      case TRAVELING_TO_HUB -> checkHubArrival();
      case TRAVELING_HOME -> checkHomeArrival();
    }
  }

  /**
   * Füllt den Treibstofftank einer bei der Heimatkolonie gelandeten Flotte auf
   * (Umsetzungskonzept/26_...md). Seit der Tank eingeführt wurde, zieht ein
   * Sprung KEINEN Treibstoff mehr aus dem Kolonielager – ohne Betanken bliebe
   * jede Flotte irgendwann stehen. Fehler werden bewusst geschluckt: ein voller
   * Tank oder ein leeres Lager sind normale Zustände, kein Grund abzubrechen.
   */
  private void topUpFuel(String fleetId) {
    try {
      connection.call("refuelFleet", Map.of("fleetId", fleetId, "quantity", Catalog.FUEL_TOP_UP_QTY));
    } catch (CommandException e) {
      // Tank voll oder keine Kapseln im Lager - beides unkritisch.
    }
  }

  private void findFreighterFleet() {
    JsonNode fleets = connection.call("fleets");
    for (JsonNode f : fleets) {
      if (hasFreighter(f)) {
        freighterFleetId = text(f, "id");
        return;
      }
    }
  }

  private void tryStartExport() {
    JsonNode fleet = ownFleet(freighterFleetId);
    if (fleet == null || !"Stationed".equals(text(fleet, "status")) || !homeColonyId.equals(text(fleet, "locationColonyId"))) {
      return; // Frachter ist noch unterwegs oder nicht zu Hause gelandet
    }
    if (hubSystemId == null) {
      hubSystemId = findNearestTradeHub();
      // Kollision Heimatsystem == Handelsposten ist laut Umsetzungskonzept/22_...md, §G
      // möglich, aber selten – der Frachter kann dann nicht "dorthin reisen" (Ziel ==
      // Ausgangssystem), diese Kolonie handelt schlicht nicht am eigenen Heimatposten.
      if (hubSystemId == null || hubSystemId.equals(homeSystemId)) return;
    }
    double exportable = warehouseQty(mySpecialtyProduct) - Catalog.TRADE_RESERVE_QTY;
    double capacityQty = maxLoadable(freighterFleetId, mySpecialtyProduct);
    double loadQty = Math.floor(Math.min(exportable, capacityQty));
    if (loadQty < Catalog.TRADE_MIN_EXPORT_BATCH) return;
    try {
      connection.call("loadCargo", Map.of("fleetId", freighterFleetId, "productTypeId", mySpecialtyProduct, "quantity", loadQty));
      // Fuer Hin- UND Rueckreise betanken: an der Station gibt es keine eigene Kolonie.
      topUpFuel(freighterFleetId);
      connection.call("moveFleet", Map.of("fleetId", freighterFleetId, "destinationSystemId", hubSystemId));
      tradeState = TradeState.TRAVELING_TO_HUB;
      log("Handelsfahrt gestartet: " + (long) loadQty + "x " + mySpecialtyProduct + " -> Station " + hubSystemId);
    } catch (CommandException e) {
      log("Verladung zur Handelsfahrt abgelehnt: " + e.getMessage());
    }
  }

  private void checkHubArrival() {
    JsonNode fleet = ownFleet(freighterFleetId);
    if (fleet == null || !"Stationed".equals(text(fleet, "status")) || !hubSystemId.equals(text(fleet, "systemId"))) {
      return; // noch unterwegs
    }
    sellCargoAtHub();
    buyImportsAtHub();
    try {
      connection.call("moveFleet", Map.of("fleetId", freighterFleetId, "destinationSystemId", homeSystemId));
      tradeState = TradeState.TRAVELING_HOME;
      log("Rückreise zur Heimatkolonie gestartet.");
    } catch (CommandException e) {
      log("Rückreise fehlgeschlagen: " + e.getMessage());
    }
  }

  private void checkHomeArrival() {
    JsonNode fleet = ownFleet(freighterFleetId);
    if (fleet == null || !"Stationed".equals(text(fleet, "status")) || !homeSystemId.equals(text(fleet, "systemId"))) {
      return; // noch unterwegs
    }
    // moveFleet setzt die Flotte nach der Ankunft nur allgemein "im System" ab
    // (locationColonyId bleibt null, siehe FleetCommands.moveFleetWithinSystem)
    // – zum Ent-/Beladen am Kolonielager muss erst explizit an der Kolonie
    // angedockt werden, ein reiner Ortswechsel INNERHALB desselben Systems
    // ohne Reisezeit.
    if (!homeColonyId.equals(text(fleet, "locationColonyId"))) {
      try {
        connection.call("moveFleetWithinSystem", Map.of(
            "fleetId", freighterFleetId, "target", Map.of("kind", "ColonyOrbit", "colonyId", homeColonyId)));
      } catch (CommandException e) {
        log("Andocken an der Heimatkolonie fehlgeschlagen: " + e.getMessage());
        return;
      }
      fleet = ownFleet(freighterFleetId);
    }
    for (JsonNode c : fleet.path("cargo")) {
      String productTypeId = text(c, "productTypeId");
      double qty = c.path("quantity").asDouble(0);
      if (qty < 1e-9) continue;
      try {
        connection.call("unloadCargo", Map.of("fleetId", freighterFleetId, "productTypeId", productTypeId, "quantity", qty));
      } catch (CommandException e) {
        log("Entladen der Handelsfracht zu Hause fehlgeschlagen: " + e.getMessage());
      }
    }
    tradeState = TradeState.IDLE;
    log("Handelsfahrt abgeschlossen.");
  }

  private void sellCargoAtHub() {
    JsonNode fleet = ownFleet(freighterFleetId);
    double onboard = fleet == null ? 0 : cargoQty(fleet, mySpecialtyProduct);
    if (onboard >= 1) {
      try {
        connection.call("unloadCargoToHubDepot", Map.of(
            "fleetId", freighterFleetId, "productTypeId", mySpecialtyProduct, "quantity", onboard));
      } catch (CommandException e) {
        log("Entladen ins Stationsdepot fehlgeschlagen: " + e.getMessage());
      }
    }
    double depotQty = Math.floor(hubDepotQty(mySpecialtyProduct));
    if (depotQty < 1) return;
    double bidPrice = bestPrice(mySpecialtyProduct, "Buy");
    if (bidPrice <= 0) return; // keine Gegenseite vorhanden (sollte wegen Market-Maker nicht vorkommen)
    try {
      connection.call("createHubSellOrder", Map.of(
          "systemId", hubSystemId, "productTypeId", mySpecialtyProduct, "quantity", depotQty, "pricePerUnit", bidPrice));
      log("Verkaufsorder aufgegeben: " + (long) depotQty + "x " + mySpecialtyProduct + " @ " + bidPrice);
    } catch (CommandException e) {
      log("Verkaufsorder abgelehnt: " + e.getMessage());
    }
  }

  /**
   * Kauft am Handelsposten genau die Grundbedarfe/Baustoffe nach, die die
   * eigene Kolonie NICHT selbst herstellt: Nahrung und Medizin für jeden
   * außer dem jeweiligen Erzeuger (beide speisen sonst dauerhaft leere
   * Auto-Relist-Orders der Bevölkerung), zusätzlich {@link
   * Catalog#TRADE_IMPORT_MATERIAL} für jeden, der nicht ohnehin
   * Baustoff-Spezialist ist (Nutzervorgabe: Baustoff-Spezialisten sind
   * bewusst wenige, siehe {@link Catalog#MATERIALS_SPECIALIST_EVERY}).
   */
  private void buyImportsAtHub() {
    List<String> needs = new ArrayList<>();
    if (!Catalog.FOOD_PRODUCT.equals(mySpecialtyProduct)) needs.add(Catalog.FOOD_PRODUCT);
    if (!Catalog.MEDICINE_PRODUCT.equals(mySpecialtyProduct)) needs.add(Catalog.MEDICINE_PRODUCT);
    if (!isMaterialsSpecialist()) needs.add(Catalog.TRADE_IMPORT_MATERIAL);
    for (String productTypeId : needs) {
      if (warehouseQty(productTypeId) >= Catalog.TRADE_IMPORT_LOW_WATERMARK) continue;
      buyOneProductAtHub(productTypeId);
    }
  }

  private void buyOneProductAtHub(String productTypeId) {
    cancelOwnOrders(productTypeId, "Buy"); // Reste einer evtl. nicht voll ausgeführten Order von einem früheren Besuch aufräumen
    double askPrice = bestPrice(productTypeId, "Sell");
    if (askPrice <= 0) return;
    double affordable = Math.floor(walletBalance() / askPrice);
    double quantity = Math.min(affordable, Catalog.TRADE_IMPORT_BATCH);
    if (quantity < 1) return;
    try {
      connection.call("createHubBuyOrder", Map.of(
          "systemId", hubSystemId, "productTypeId", productTypeId, "quantity", quantity, "pricePerUnit", askPrice));
    } catch (CommandException e) {
      log("Einkaufsorder abgelehnt (" + productTypeId + "): " + e.getMessage());
      return;
    }
    double loadable = Math.floor(maxLoadable(freighterFleetId, productTypeId));
    if (loadable < 1) return; // Order nicht (sofort) ausgeführt – Rest bleibt für den nächsten Besuch im Orderbuch
    try {
      connection.call("loadCargoFromHubDepot", Map.of("fleetId", freighterFleetId, "productTypeId", productTypeId, "quantity", loadable));
      log("Eingekauft: " + (long) loadable + "x " + productTypeId + " @ " + askPrice);
    } catch (CommandException e) {
      log("Verladung des Einkaufs fehlgeschlagen: " + e.getMessage());
    }
  }

  private boolean isMaterialsSpecialist() {
    return Catalog.SPECIALTY_PRODUCTS.contains(mySpecialtyProduct);
  }

  /** BFS über das (öffentlich bekannte, siehe {@code GatewayCommands.visibleSystems}) Gateway-Netz zur nächstgelegenen Handelsgilde-Station. */
  private String findNearestTradeHub() {
    JsonNode systems = connection.call("visibleSystems");
    Set<String> hubIds = new HashSet<>();
    for (JsonNode s : systems) {
      if (s.path("isTradeHub").asBoolean(false)) hubIds.add(text(s, "id"));
    }
    if (hubIds.isEmpty()) return null;
    JsonNode routes = connection.call("galaxyRoutes");
    Map<String, List<String>> adjacency = new HashMap<>();
    for (JsonNode r : routes) {
      String a = text(r, "a");
      String b = text(r, "b");
      adjacency.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
      adjacency.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    visited.add(homeSystemId);
    queue.add(homeSystemId);
    while (!queue.isEmpty()) {
      String current = queue.poll();
      if (hubIds.contains(current)) return current;
      for (String neighbor : adjacency.getOrDefault(current, List.of())) {
        if (visited.add(neighbor)) queue.add(neighbor);
      }
    }
    return null;
  }

  private double hubDepotQty(String productTypeId) {
    JsonNode entries = connection.call("hubDepot", Map.of("systemId", hubSystemId));
    for (JsonNode e : entries) {
      if (productTypeId.equals(text(e, "productTypeId"))) return e.path("quantity").asDouble(0);
    }
    return 0;
  }

  /** Bestes Kursangebot im Orderbuch der aktuellen Handelsgilde-Station für {@code side} ("Buy"/"Sell") – höchstes Gebot bzw. günstigster Brief. */
  private double bestPrice(String productTypeId, String side) {
    JsonNode orders = connection.call("hubOrders", Map.of("systemId", hubSystemId));
    double best = -1;
    for (JsonNode o : orders) {
      if (!productTypeId.equals(text(o, "productTypeId")) || !side.equals(text(o, "side"))) continue;
      if (o.path("remainingQuantity").asDouble(0) <= 0) continue;
      double price = o.path("limitPrice").asDouble(0);
      if ("Buy".equals(side)) {
        if (price > best) best = price;
      } else if (best < 0 || price < best) {
        best = price;
      }
    }
    return best;
  }

  private void cancelOwnOrders(String productTypeId, String side) {
    JsonNode orders = connection.call("hubOrders", Map.of("systemId", hubSystemId));
    for (JsonNode o : orders) {
      if (!productTypeId.equals(text(o, "productTypeId")) || !side.equals(text(o, "side"))) continue;
      if (!playerId.equals(text(o, "ownerId"))) continue;
      try {
        connection.call("cancelHubOrder", Map.of("orderId", text(o, "id")));
      } catch (CommandException ignored) {
        // Order wurde zwischen Abfrage und Stornierung evtl. bereits voll ausgeführt – kein Problem.
      }
    }
  }

  private double walletBalance() {
    JsonNode wallet = connection.call("wallet");
    return wallet.path("balance").asDouble(0);
  }

  private double maxLoadable(String fleetId, String productTypeId) {
    JsonNode capacity = connection.call("fleetCargoCapacity", Map.of("fleetId", fleetId, "productTypeId", productTypeId));
    return capacity.path("maxLoadableQuantity").asDouble(0);
  }

  private static double cargoQty(JsonNode fleet, String productTypeId) {
    for (JsonNode c : fleet.path("cargo")) {
      if (productTypeId.equals(text(c, "productTypeId"))) return c.path("quantity").asDouble(0);
    }
    return 0;
  }

  private static boolean hasFreighter(JsonNode fleet) {
    for (JsonNode s : fleet.path("ships")) {
      if ("p_freighter".equals(text(s, "shipProductTypeId")) && s.path("quantity").asDouble(0) > 0) return true;
    }
    return false;
  }

  // --- Diplomatie: pauschaler Krieg gegen das gesamte gegnerische Lager ------

  private void maintainDiplomacy() {
    for (Map.Entry<String, String> e : playerIdByName.entrySet()) {
      String name = e.getKey();
      String id = e.getValue();
      if (!isEnemyCampName(name) || declaredWarWith.contains(id)) continue;
      try {
        connection.call("declareWar", Map.of("otherPlayerId", id));
        log("Krieg erklärt an " + name);
      } catch (CommandException ex) {
        // "bereits im Krieg" ist der Normalfall bei jedem erneuten Tick, sobald einmal erklärt.
      }
      declaredWarWith.add(id);
    }
  }

  // --- Verteidigung: eigene Heimatkolonie mit der Kampfflotte blockieren -----

  /**
   * Macht die eigene Kampfflotte angreifbar (siehe {@code BlockadeCommands}:
   * nur eine blockierende Flotte ist überhaupt Ziel eines {@code engageBattle}).
   * Jeder Bot blockiert reflexartig die eigene Heimatwelt – symmetrisch für
   * beide Lager, damit ein später angreifender Bot immer ein gültiges Ziel
   * vorfindet, sobald er ankommt.
   */
  private void maintainDefense() {
    if (blockading) return;
    JsonNode fleets = connection.call("fleets");
    for (JsonNode f : fleets) {
      if (!text(f, "id").equals(combatFleetId)) continue;
      if (!"Stationed".equals(text(f, "status"))) return;
      Map<String, Object> anchor = Map.of("kind", "PlanetOrbit", "planetId", homePlanetId);
      try {
        connection.call("formBlockade", Map.of("fleetId", combatFleetId, "anchor", anchor));
        log("Heimatkolonie blockiert (Verteidigungshaltung).");
      } catch (CommandException e) {
        // "wird bereits blockiert" o.ä. – Zustand ist ohnehin der gewünschte.
      }
      blockading = true;
      return;
    }
  }

  // --- Offensive: Ziel wählen, anreisen, angreifen, bei Unterlegenheit zurückziehen ---

  private void maintainOffense() {
    switch (attackState) {
      case IDLE -> tryLaunchAttack();
      case TRAVELING -> checkArrival();
      case ENGAGING -> monitorBattle();
    }
  }

  /**
   * Deterministische 1:1-Zielzuweisung: Bot Nr. N eines Lagers zielt auf
   * Bot Nr. N des gegnerischen Lagers (statt einer aufwendigen Zielsuche) –
   * bewusste Vereinfachung, siehe Umsetzungskonzept/14_...md, Teil 2.
   * Angriffsschwelle: (a) die Mindest-Aufbauzeit {@link #ATTACK_READY_DELAY_MS}
   * ist verstrichen, (b) die eigene Flotte ist gemäß der groben Stärkeschätzung
   * aus {@link Catalog#SHIP_MILITARY_WEIGHT} der Zielflotte überlegen – beide
   * Flottenstärken kommen live vom Server (echter Vergleich, keine Fiktion),
   * auch wenn die absolute Stärke wegen der langen Schiffs-Fertigungsketten
   * innerhalb der Mindest-Aufbauzeit meist noch die zufällige Startflotte ist.
   */
  private void tryLaunchAttack() {
    if (System.currentTimeMillis() < readyAtMs) return;
    String targetName = "NPC-" + displayName(enemyCamp) + "-" + String.format("%02d", index);
    String targetId = playerIdByName.get(targetName);
    if (targetId == null) return;
    JsonNode targetPlayer = playersById.get(targetId);
    if (targetPlayer == null) return;
    String targetSystemId = text(targetPlayer, "homeSystemId");
    if (targetSystemId == null || targetSystemId.equals(homeSystemId)) return;

    JsonNode myFleet = ownCombatFleet();
    if (myFleet == null || !"Stationed".equals(text(myFleet, "status"))) return;
    double myStrength = weighedStrength(myFleet.path("ships"));

    JsonNode allFleets = connection.call("allFleets");
    JsonNode targetFleet = null;
    for (JsonNode f : allFleets) {
      if (text(f, "ownerId").equals(targetId) && hasWarships(f)) {
        targetFleet = f;
        break;
      }
    }
    if (targetFleet == null) return;
    double targetStrength = weighedStrength(targetFleet.path("ships"));
    if (myStrength < targetStrength * 1.1) return;

    topUpFuel(combatFleetId);
    connection.call("moveFleet", Map.of("fleetId", combatFleetId, "destinationSystemId", targetSystemId));
    attackTargetSystemId = targetSystemId;
    startAttackShipCount = currentCombatShipCount();
    attackState = AttackState.TRAVELING;
    log("Angriff gestartet gegen " + targetName + " (eigene Stärke " + myStrength + " vs. " + targetStrength + "), Reise nach " + targetSystemId);
  }

  private void checkArrival() {
    JsonNode myFleet = ownCombatFleet();
    if (myFleet == null) return;
    if ("Stationed".equals(text(myFleet, "status")) && attackTargetSystemId.equals(text(myFleet, "systemId"))) {
      attackState = AttackState.ENGAGING;
      log("Kampfflotte im Zielsystem " + attackTargetSystemId + " angekommen.");
    }
  }

  private void monitorBattle() {
    if (currentBattleId == null) {
      JsonNode candidates = connection.call("attackableFleetsInSystem", Map.of("systemId", attackTargetSystemId));
      if (candidates.isEmpty()) return; // Ziel blockiert (noch) nicht – nächster Tick erneut versuchen
      String defenderFleetId = text(candidates.get(0), "id");
      try {
        connection.call("engageBattle", Map.of("attackerFleetId", combatFleetId, "defenderFleetId", defenderFleetId));
      } catch (CommandException e) {
        log("Gefechtsbeginn fehlgeschlagen: " + e.getMessage());
        return;
      }
      JsonNode activeBattles = connection.call("activeBattles");
      for (JsonNode b : activeBattles) {
        if (text(b, "attackerFleetId").equals(combatFleetId)) {
          currentBattleId = text(b, "id");
          log("Gefecht gestartet: " + currentBattleId);
          break;
        }
      }
      return;
    }

    JsonNode activeBattles = connection.call("activeBattles");
    boolean stillActive = false;
    for (JsonNode b : activeBattles) {
      if (text(b, "id").equals(currentBattleId)) {
        stillActive = true;
        break;
      }
    }
    if (!stillActive) {
      log("Gefecht " + currentBattleId + " beendet – kehre in den Aufbaumodus zurück.");
      resetAttackState();
      return;
    }

    int myShipsNow = currentCombatShipCount();
    if (myShipsNow < startAttackShipCount * 0.35) {
      try {
        connection.call("retreatFromBattle", Map.of("battleId", currentBattleId));
        log("Rückzug aus Gefecht " + currentBattleId + " (deutlich unterlegen: " + myShipsNow + "/" + startAttackShipCount + " Schiffe).");
      } catch (CommandException e) {
        log("Rückzug fehlgeschlagen: " + e.getMessage());
      }
      resetAttackState();
    }
  }

  private void resetAttackState() {
    attackState = AttackState.IDLE;
    attackTargetSystemId = null;
    currentBattleId = null;
  }

  // --- Hilfsmethoden -----------------------------------------------------

  private double eleriumStock() {
    return warehouseQty("p_elerium_stabil");
  }

  private double warehouseQty(String productTypeId) {
    JsonNode warehouse = connection.call("warehouse", Map.of("colonyId", homeColonyId));
    for (JsonNode w : warehouse) {
      if (productTypeId.equals(text(w, "productTypeId"))) return w.path("quantity").asDouble(0);
    }
    return 0;
  }

  private JsonNode ownFleet(String fleetId) {
    JsonNode fleets = connection.call("fleets");
    for (JsonNode f : fleets) {
      if (text(f, "id").equals(fleetId)) return f;
    }
    return null;
  }

  private JsonNode ownCombatFleet() {
    return ownFleet(combatFleetId);
  }

  private int currentCombatShipCount() {
    JsonNode fleet = ownCombatFleet();
    return fleet == null ? 0 : shipCount(fleet);
  }

  private static boolean isCombatFleet(JsonNode fleet) {
    return hasWarships(fleet);
  }

  private static boolean hasWarships(JsonNode fleet) {
    for (JsonNode s : fleet.path("ships")) {
      if (Catalog.WARSHIP_TYPES.contains(text(s, "shipProductTypeId")) && s.path("quantity").asDouble(0) > 0) return true;
    }
    return false;
  }

  private static int shipCount(JsonNode fleet) {
    int sum = 0;
    for (JsonNode s : fleet.path("ships")) sum += (int) s.path("quantity").asDouble(0);
    return sum;
  }

  private static double weighedStrength(JsonNode ships) {
    double sum = 0;
    for (JsonNode s : ships) {
      String type = text(s, "shipProductTypeId");
      double weight = Catalog.SHIP_MILITARY_WEIGHT.getOrDefault(type, 0.0);
      sum += weight * s.path("quantity").asDouble(0);
    }
    return sum;
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isMissingNode() || v.isNull() ? null : v.asText();
  }

  private void log(String message) {
    System.out.println("[" + LocalTime.now().format(TIME_FMT) + " " + botName + "] " + message);
  }
}
