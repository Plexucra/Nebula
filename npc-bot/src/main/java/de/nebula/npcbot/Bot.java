package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;
import de.nebula.npcbot.ws.GameConnection;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.nebula.npcbot.Json.text;

/**
 * Ein NPC-Kommandant – angemeldet über das reguläre WebSocket-Protokoll wie
 * jeder menschliche Spieler (Umsetzungskonzept/14, Teil 2: kein privilegierter
 * Zugriff, keine externe Intelligenz). Seit Umsetzungskonzept/31 nicht mehr
 * ein einzelner Zustandsautomat, sondern eine kleine strategische KI:
 *
 * <pre>
 *   Weltsicht (World)  ->  Lage (Situation)  ->  Lagerabstimmung (Coordination)
 *        -> Strategie (Strategy.choose)  ->  Plan (Strategy.plan)
 *        -> Ausführung: Diplomacy, Economy, Trade, Military, Expansion
 *        -> Monitoring (Monitor: Log + JSONL je Takt und Ereignis)
 * </pre>
 *
 * Ziel des Lagers ist der langfristige Sieg über das andere Lager: eigene
 * Kolonien gründen (vorzugsweise im eigenen System), gegnerische Kolonien
 * erobern, dazu Arbeitsteilung und Verträge im eigenen Lager.
 */
public class Bot {
  /** Entscheidungstakt in SPIELSTUNDEN (ein Kampftick), nie unter einer Realsekunde – bei hohem Tempo-Regler würde die Serverlast sonst explodieren. */
  static final long TICK_INTERVAL_MS = Math.max(1000, GameSpeed.hoursToMs(Catalog.COMBAT_TICK_HOURS));

  final int index;
  final String camp;
  final String enemyCamp;
  final String botName;
  final String server;

  String playerId;
  String homeColonyId;
  String homePlanetId;
  String homeSystemId;

  GameConnection connection;
  final World world;
  final Monitor monitor;
  final Economy economy;
  final Trade trade;
  final Diplomacy diplomacy;
  final Military military;
  final Expansion expansion;
  final Coordination coordination;

  Strategy strategy = Strategy.BUILD_UP;
  int tickNo;
  private final long startedAt = System.currentTimeMillis();

  public Bot(int index, String camp, String server, Path logDir) {
    this.index = index;
    this.camp = camp;
    this.enemyCamp = camp.equals("NORD") ? "SUED" : "NORD";
    this.server = server;
    this.botName = String.format("NPC-%s-%02d", displayName(camp), index);
    this.connection = new GameConnection(server);
    this.world = new World(connection);
    this.monitor = new Monitor(botName, logDir);
    this.economy = new Economy(this);
    this.trade = new Trade(this);
    this.diplomacy = new Diplomacy(this);
    this.military = new Military(this);
    this.expansion = new Expansion(this);
    this.coordination = new Coordination(this);
  }

  static String displayName(String camp) {
    return camp.equals("NORD") ? "Nord" : "Sued";
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> opts = parseArgs(args);
    String server = opts.getOrDefault("server", "ws://localhost:8080/game");
    int index = Integer.parseInt(opts.getOrDefault("index", "1"));
    String camp = opts.getOrDefault("camp", "NORD").toUpperCase();
    if (!camp.equals("NORD") && !camp.equals("SUED")) throw new IllegalArgumentException("--camp muss NORD oder SUED sein, war: " + camp);
    // Einzelprozess (run-army.sh): menschliches Log auf stdout, JSONL-Spur nach --logdir (Standard: logs/)
    Path logDir = Path.of(opts.getOrDefault("logdir", "logs"));
    Bot bot = new Bot(index, camp, server, null);
    bot.monitorJsonlOnly(logDir);
    bot.run();
  }

  private Monitor jsonlMonitor;

  private void monitorJsonlOnly(Path logDir) {
    jsonlMonitor = new Monitor(botName, logDir);
  }

  static Map<String, String> parseArgs(String[] args) {
    Map<String, String> result = new HashMap<>();
    for (String arg : args) {
      if (!arg.startsWith("--") || !arg.contains("=")) continue;
      String[] parts = arg.substring(2).split("=", 2);
      result.put(parts[0], parts[1]);
    }
    return result;
  }

  public void run() throws Exception {
    monitor.log("Starte, verbinde mit " + server + " ...");
    connection.connect();
    registerAndBootstrap();
    monitor.log("Bereit als " + botName + " (playerId=" + playerId + ", Heimatsystem=" + world.systemName(homeSystemId)
        + ", Startflotte " + String.format("%.0f", military.initialStrength()) + ", Takt " + TICK_INTERVAL_MS + " ms, Koordinator=" + coordination.isCoordinator() + ")");
    while (true) {
      try {
        tick();
      } catch (CommandException e) {
        monitor.log("Takt: Befehl abgelehnt: " + e.getMessage());
      } catch (RuntimeException e) {
        monitor.log("Takt-Fehler: " + e.getMessage());
        if (String.valueOf(e.getMessage()).contains("fehlgeschlagen")) reconnect();
      }
      Thread.sleep(TICK_INTERVAL_MS);
    }
  }

  private void reconnect() {
    try {
      monitor.log("Verbindung verloren – neu verbinden ...");
      connection.close();
      connection = new GameConnection(server);
      connection.connect();
      world.setConnection(connection);
      connection.call("login", Map.of("playerId", playerId));
      monitor.log("Wieder angemeldet.");
    } catch (Exception e) {
      monitor.log("Neuverbindung fehlgeschlagen: " + e.getMessage());
    }
  }

  private void registerAndBootstrap() {
    // Ein Neustart des Bot-Prozesses darf keinen zweiten Kommandanten gleichen Namens
    // anlegen (so entstand im Live-Spiel ein doppelter NPC-Nord-01): existiert der
    // Name bereits, meldet sich der Bot dort an und setzt auf dem Spielstand auf.
    JsonNode existing = world.playerByName(botName);
    JsonNode player;
    if (existing != null) {
      player = connection.call("login", Map.of("playerId", text(existing, "id")));
      monitor.log("Bestehender Kommandant " + botName + " gefunden – Anmeldung statt Neuregistrierung.");
    } else {
      player = connection.call("registerPlayer", Map.of(
          "commanderName", botName, "homeworldName", botName + "-Heimat", "role", "Npc", "campId", camp));
    }
    world.beginTick();
    playerId = text(player, "id");
    homeColonyId = text(player, "homeworldColonyId");
    homeSystemId = text(player, "homeSystemId");
    homePlanetId = text(world.colony(homeColonyId), "planetId");
    military.bootstrap();
    coordination.bootstrap();
    monitor.event("REGISTERED", "Registriert als " + botName, "playerId", playerId, "homeSystem", homeSystemId, "homeColony", homeColonyId);
  }

  // --- Takt ------------------------------------------------------------------------

  void tick() {
    world.beginTick();
    tickNo++;
    economy.assess();
    Strategy.Situation s = assess();
    coordination.tick(s);
    Strategy.Assignment a = coordination.assignment();
    if (!ensureHome()) {
      // Ohne eigene Kolonie gibt es nichts zu bewirtschaften – der Kommandant bleibt
      // angemeldet (Diplomatie, Lagerstatus), handelt aber nicht mehr. Das Spiel kennt
      // keine Eliminierung; ein eroberter Heimatwelt-Besitzer existiert einfach weiter.
      metrics(s, a);
      return;
    }
    Strategy next = Strategy.choose(s, a, strategy);
    if (next != strategy) {
      monitor.event("STRATEGY", strategy + " -> " + next + " (" + describe(s) + ")", "from", strategy.name(), "to", next.name());
      strategy = next;
    }
    Strategy.Plan plan = Strategy.plan(strategy, s);
    military.prepare(plan, a);
    safe("Diplomatie", diplomacy::tick);
    safe("Wirtschaft", () -> economy.act(plan, a.specialty));
    safe("Handel", () -> trade.tick(plan, a.specialty, economy.shoppingList()));
    safe("Militär", () -> military.act(plan, s, a, strategy));
    safe("Expansion", () -> expansion.tick(plan, s, a, strategy));
    safe("Aufklärung", this::exploreWhereWeStand);
    metrics(s, a);
  }

  /**
   * Jedes System, in dem eine eigene Flotte gerade steht, einmal erforschen –
   * das deckt die Rohstoffkonzentrationen auf (`FleetCommands.exploreSystem`)
   * und kostet nichts als den Befehl. Ohne das blieb die Erkundung als einzige
   * Mechanik von den Bots unberührt.
   */
  private void exploreWhereWeStand() {
    for (JsonNode f : world.ownFleets()) {
      if (!World.stationed(f)) continue;
      String systemId = text(f, "systemId");
      if (systemId == null || world.hasExploredSystem(systemId)) continue;
      try {
        call("exploreSystem", Map.of("fleetId", text(f, "id")));
        monitor.event("SYSTEM_EXPLORED", "System " + world.systemName(systemId) + " erforscht", "systemId", systemId);
        world.invalidate("hasExploredSystem");
      } catch (CommandException e) {
        // bereits erforscht o. ä. – unkritisch
      }
    }
  }

  private boolean eliminatedLogged;

  /**
   * Die Heimatkolonie kann erobert werden (Testlauf B: NPC-Nord-02 verlor seine
   * Heimatwelt an NPC-Sued-01). Danach zeigt {@code homeColonyId} auf fremden
   * Besitz und jeder Befehl darauf wird abgelehnt. Gibt es eine andere eigene
   * Kolonie, wird sie zur neuen Heimat; sonst ist der Bot ausgeschaltet.
   */
  private boolean ensureHome() {
    JsonNode home = world.colony(homeColonyId);
    if (!Json.isNull(home) && Json.eq(text(home, "ownerId"), playerId)) return true;
    for (JsonNode c : world.ownColonies()) {
      String id = text(c, "id");
      homeColonyId = id;
      homeSystemId = text(c, "systemId");
      homePlanetId = text(c, "planetId");
      monitor.event("HOME_MOVED", "Heimatkolonie verloren – " + text(c, "name") + " ist die neue Heimat", "colonyId", id);
      return true;
    }
    if (!eliminatedLogged) {
      eliminatedLogged = true;
      monitor.event("ELIMINATED", "Keine eigene Kolonie mehr – Kommandant ist ausgeschaltet (bleibt angemeldet)");
    }
    return false;
  }

  private void safe(String module, Runnable step) {
    try {
      step.run();
    } catch (CommandException e) {
      monitor.log(module + ": Befehl abgelehnt: " + e.getMessage());
    } catch (RuntimeException e) {
      if (String.valueOf(e.getMessage()).contains("fehlgeschlagen")) throw e;
      monitor.log(module + ": Fehler: " + e);
    }
  }

  private Strategy.Situation assess() {
    Strategy.Situation s = new Strategy.Situation();
    for (Economy.Health h : economy.health()) {
      s.colonies++;
      if (h.blackout()) s.blackout = true;
      s.minEleriumHours = Math.min(s.minEleriumHours, h.eleriumHours());
      if (h.population() > 20) {
        s.minFoodCoverage = Math.min(s.minFoodCoverage, h.foodCoverage());
        s.minStandardOfLiving = Math.min(s.minStandardOfLiving, h.standardOfLiving());
      }
      if (h.home()) {
        s.homeLoyalty = h.loyalty();
        s.homePopulation = h.population();
        s.shipyardLevel = h.shipyard();
        s.academyLevel = h.academy();
        s.soldiers = h.soldiers();
        s.drones = h.drones();
      }
    }
    s.wallet = world.wallet();
    s.fleetStrength = military.fleetStrength();
    s.initialFleetStrength = military.initialStrength();
    s.fleetInBattle = military.fleetInBattle();
    s.transports = military.transportsOwned();
    s.colonyShips = (int) world.stock(homeColonyId, Catalog.COLONY_SHIP);
    for (JsonNode f : world.ownFleets()) s.colonyShips += (int) World.shipCount(f, Catalog.COLONY_SHIP);
    s.underGroundAttack = military.underGroundAttack();
    s.enemyFleetAtHome = military.enemyFleetAtHome();
    s.invasionActive = military.invasionActive();
    s.settlingActive = expansion.active();
    return s;
  }

  private static String describe(Strategy.Situation s) {
    return String.format("Blackout=%s, Elerium %s, Nahrung %.0f%%, Lebensstandard %.0f, Flotte %.0f/%.0f, Angriff=%s, Feind@Heimat=%s",
        s.blackout, Economy.fmtHours(s.minEleriumHours), s.minFoodCoverage * 100, s.minStandardOfLiving, s.fleetStrength,
        s.initialFleetStrength, s.underGroundAttack, s.enemyFleetAtHome);
  }

  private void metrics(Strategy.Situation s, Strategy.Assignment a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("tick", tickNo);
    m.put("uptimeMin", (System.currentTimeMillis() - startedAt) / 60000.0);
    m.put("strategy", strategy.name());
    m.put("role", a.role.name());
    m.put("specialty", a.specialty);
    m.put("target", a.targetColonyId);
    m.put("colonies", s.colonies);
    m.put("pop", Math.round(s.homePopulation));
    m.put("loyalty", Math.round(s.homeLoyalty));
    m.put("sol", Math.round(s.minStandardOfLiving));
    m.put("foodCoverage", Math.round(s.minFoodCoverage * 100) / 100.0);
    m.put("blackout", s.blackout);
    m.put("eleriumHours", s.minEleriumHours == Double.MAX_VALUE ? null : Math.round(s.minEleriumHours));
    m.put("wallet", Math.round(s.wallet));
    m.put("fleet", Math.round(s.fleetStrength));
    m.put("warships", military.warshipCount());
    m.put("transports", s.transports);
    m.put("colonyShips", s.colonyShips);
    m.put("soldiers", s.soldiers);
    m.put("drones", s.drones);
    m.put("shipyard", s.shipyardLevel);
    m.put("academy", s.academyLevel);
    m.put("treaties", diplomacy.treatiesWithCamp());
    m.put("tradeState", trade.state().name());
    m.put("tradeTrips", trade.trips());
    m.put("deliveries", trade.deliveries());
    m.put("revenue", Math.round(trade.revenue()));
    m.put("spent", Math.round(trade.spent()));
    m.put("queueReorders", economy.reorders());
    m.put("expansion", expansion.phase());
    m.put("expansionBlocked", expansion.blockedReason());
    m.put("founded", expansion.founded());
    m.putAll(prefixed("mil", military.stats()));
    m.putAll(prefixed("coord", coordination.stats()));
    Map<String, Object> cols = new LinkedHashMap<>();
    for (Economy.Health h : economy.health()) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("name", h.name());
      c.put("pop", Math.round(h.population()));
      c.put("loyalty", Math.round(h.loyalty()));
      c.put("blackout", h.blackout());
      c.put("eleriumHours", h.eleriumHours() == Double.MAX_VALUE ? null : Math.round(h.eleriumHours()));
      c.put("infra", h.infrastructure());
      c.put("industry", h.industry());
      cols.put(h.colonyId(), c);
    }
    m.put("colonyDetails", cols);
    monitor.metrics(m);
    if (jsonlMonitor != null) jsonlMonitor.metrics(m);
    if (tickNo % 12 == 1) {
      monitor.log(String.format("Takt %d: %s als %s/%s | Bev %.0f, Loyalität %.0f, Elerium %s, Blackout=%s | Flotte %.0f, Kriegsschiffe %d, Transporter %d, Soldaten %d, Drohnen %d | Credits %.0f | Handel %s (%d Fahrten) | Invasion %s%s | Expansion %s%s",
          tickNo, strategy, a.role, a.specialty, s.homePopulation, s.homeLoyalty, Economy.fmtHours(s.minEleriumHours), s.blackout,
          s.fleetStrength, military.warshipCount(), s.transports, s.soldiers, s.drones, s.wallet, trade.state(), trade.trips(),
          military.invasionPhase(), military.blockedReason().isEmpty() ? "" : " [" + military.blockedReason() + "]",
          expansion.phase(), expansion.blockedReason().isEmpty() ? "" : " [" + expansion.blockedReason() + "]"));
    }
  }

  private static Map<String, Object> prefixed(String prefix, Map<String, Object> m) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : m.entrySet()) out.put(prefix + "." + e.getKey(), e.getValue());
    return out;
  }

  // --- Hilfen ----------------------------------------------------------------------------

  JsonNode call(String type, Map<String, Object> payload) {
    JsonNode result = connection.call(type, payload);
    return result == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : result;
  }

  boolean isOwnCampName(String name) {
    return name != null && name.startsWith("NPC-" + displayName(camp) + "-");
  }

  boolean isEnemyCampName(String name) {
    return name != null && name.startsWith("NPC-" + displayName(enemyCamp) + "-");
  }

  static int parseIndex(String botName) {
    try {
      return Integer.parseInt(botName.substring(botName.lastIndexOf('-') + 1));
    } catch (Exception e) {
      return -1;
    }
  }
}
