package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;
import de.nebula.npcbot.ws.GameConnection;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

  private static final long TICK_INTERVAL_MS = 8000;
  /**
   * Mindestlaufzeit vor dem ersten möglichen Angriff (siehe {@link #tryLaunchAttack}).
   * Ein einzelnes Kampfschiff braucht in diesem Prototyp – trotz Zeitkompression
   * ({@code Clock.REAL_MS_PER_GAME_HOUR}) – wegen seiner tiefen, mehrstufigen
   * Fertigungskette (rohstoffnahe Vorprodukte -> Baugruppen -> Schiffsrumpf)
   * je nach Ausgangslager real eher zehn(e) Minuten als Sekunden bis zur ersten
   * fertigen Einheit. Ein Angriff wartet deshalb NICHT auf tatsächlichen
   * Flottenzuwachs (das würde die Verifikation unpraktikabel in die Länge
   * ziehen), sondern nur auf diese Mindest-Aufbauzeit – die eigentliche
   * Stärkeschätzung in {@link #tryLaunchAttack} vergleicht dann die (durch
   * die zufällige Startausstattung ohnehin unterschiedlich starken) aktuellen
   * Flotten beider Seiten ganz reell über den Server. Bewusst dokumentierte
   * Vereinfachung, siehe Umsetzungskonzept/14_...md, Teil 2.
   */
  private static final long ATTACK_READY_DELAY_MS = 90_000;
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
        "commanderName", botName, "homeworldName", botName + "-Heimat"));
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
        break;
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
        mySpecialtyProduct = Catalog.SPECIALTY_PRODUCTS.get(0);
        log("Koordinator – eigene Spezialisierung: " + mySpecialtyProduct);
      }
      for (Map.Entry<String, String> e : playerIdByName.entrySet()) {
        String name = e.getKey();
        String id = e.getValue();
        if (id.equals(playerId) || !isOwnCampName(name) || specializationSentTo.contains(id)) continue;
        int otherIndex = parseIndex(name);
        if (otherIndex <= 0) continue;
        String product = Catalog.SPECIALTY_PRODUCTS.get((otherIndex - 1) % Catalog.SPECIALTY_PRODUCTS.size());
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
   * Parst "Fehlende Baustoffe: p_stahl (10 benötigt, 3 vorhanden), ..." und
   * reiht die fehlenden Produkte mit automatischer Vorkette ein (einmalig je
   * Produkt, solange der Auftrag in der Warteschlange steht).
   */
  private boolean queueMissingMaterials(String message) {
    if (message == null || !message.startsWith("Fehlende Baustoffe")) return false;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(p_[a-z_]+) \\((\\d+) benötigt, (\\d+) vorhanden\\)").matcher(message);
    while (m.find()) {
      String productTypeId = m.group(1);
      double missing = Double.parseDouble(m.group(2)) - Double.parseDouble(m.group(3));
      if (materialOrdersQueued.contains(productTypeId)) continue;
      try {
        connection.call("queueProduction", Map.of(
            "colonyId", homeColonyId, "productTypeId", productTypeId,
            "quantity", Math.max(1, Math.ceil(missing)), "autoProduceMissing", true, "requeueOnComplete", false));
        materialOrdersQueued.add(productTypeId);
        log("Baustoff-Produktion eingereiht: " + productTypeId + " x" + (long) Math.ceil(missing));
      } catch (CommandException e) {
        log("Baustoff-Produktion abgelehnt: " + e.getMessage());
      }
    }
    return true;
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
    JsonNode warehouse = connection.call("warehouse", Map.of("colonyId", homeColonyId));
    for (JsonNode w : warehouse) {
      if ("p_elerium_stabil".equals(text(w, "productTypeId"))) return w.path("quantity").asDouble(0);
    }
    return 0;
  }

  private JsonNode ownCombatFleet() {
    JsonNode fleets = connection.call("fleets");
    for (JsonNode f : fleets) {
      if (text(f, "id").equals(combatFleetId)) return f;
    }
    return null;
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
