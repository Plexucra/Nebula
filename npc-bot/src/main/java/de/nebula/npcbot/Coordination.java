package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.nebula.npcbot.Json.text;

/**
 * Arbeitsteilung und Zielabstimmung eines Lagers – ausschließlich über das
 * Ingame-Nachrichtensystem ({@link Protocol}), ohne Seitenkanal und ohne
 * externe Intelligenz.
 *
 * <p><b>Koordinator</b> ist der Bot mit dem niedrigsten Index, der lebt.
 * Alle anderen schicken ihm regelmäßig einen {@code STATUS}; er verteilt
 * regelmäßig {@code ASSIGN} (Spezialisierung, Militärrolle, Ziel) – jede
 * Zuteilung ist zugleich sein Lebenszeichen. Bleibt es aus, übernimmt nach
 * einer nach Index gestaffelten Frist der nächste Bot (Failover); erhält ein
 * Koordinator eine Zuteilung von einem niedrigeren Index, tritt er zurück.</p>
 *
 * <p><b>Neuverhandlung</b> (Nutzervorgabe: "die lokalen Begebenheiten können
 * sich ändern"): meldet ein Mitglied mehrfach {@code capable=false} (anhaltender
 * Blackout, Kolonie verloren) oder verstummt es, wird seine Spezialisierung
 * dem Mitglied mit der unkritischsten Rolle übertragen – Nahrung vor Medizin
 * vor Baustoffen – und beide erhalten eine neue Zuteilung. Genauso wandern
 * Invasionsziele weiter, sobald eine Kolonie erobert ist oder ein Angreifer
 * ausfällt.</p>
 *
 * <p><b>Militärische Arbeitsteilung</b>: je Lager ein Mix aus INVADER
 * (Landungsoperation), RAIDER (Eskorte des Invasionspartners bzw. Angriff
 * auf gegnerische Blockaden), SETTLER (eigenes System besiedeln) und
 * DEFENDER (Ausbau, Abwehr, Verteidigungsanlage). Ein bedrohtes Mitglied
 * wird vorübergehend DEFENDER; Invasionsziele werden eindeutig vergeben und
 * nach Garnisonsstärke, Bevölkerung, Entfernung und gegnerischer
 * Flottenpräsenz bewertet – aus der Sicht des jeweiligen Angreifers.</p>
 */
final class Coordination {
  private static final int STATUS_INTERVAL = 3;
  private static final int ASSIGN_INTERVAL = 6;
  private static final int STALE_STATUS_TICKS = 36;
  private static final int FAILOVER_BASE_TICKS = 18;
  private static final int INCAPABLE_STATUSES = 3;
  private static final int STARTUP_GRACE_TICKS = 40;

  private final Bot bot;
  private int tickNo;
  private boolean coordinator;
  private String coordinatorId;
  private int coordinatorIndex = Integer.MAX_VALUE;
  private int lastAssignTick = -1000;
  private final Strategy.Assignment assignment = new Strategy.Assignment();
  private long seq;
  private int messagesSent;
  private int messagesReceived;
  private int renegotiations;
  private int takeovers;

  // Koordinator-Zustand
  private final Map<String, Map<String, String>> statusByPlayer = new HashMap<>();
  private final Map<String, Integer> statusTick = new HashMap<>();
  private final Map<String, Integer> incapableCount = new HashMap<>();
  private final Map<String, String> specialtyOf = new LinkedHashMap<>();
  private final Map<String, Strategy.MilitaryRole> roleOf = new LinkedHashMap<>();
  private final Map<String, String> targetColonyOf = new HashMap<>();
  private final Map<String, String> planetClaims = new HashMap<>();
  private final Set<String> deniedPlanets = new HashSet<>();

  Coordination(Bot bot) {
    this.bot = bot;
  }

  void bootstrap() {
    coordinator = bot.index == 1;
    coordinatorId = coordinator ? bot.playerId : null;
    coordinatorIndex = coordinator ? bot.index : Integer.MAX_VALUE;
    // Vorläufige Selbstzuteilung, bis der Koordinator etwas anderes sagt – kein Bot steht anfangs still.
    assignment.specialty = defaultSpecialty(bot.index - 1);
    assignment.role = defaultRole(bot.index - 1);
    if (coordinator) {
      assignment.coordinatorId = bot.playerId;
      bot.monitor.event("COORDINATOR", "Dieser Bot koordiniert das Lager " + bot.camp, "camp", bot.camp);
    }
  }

  Strategy.Assignment assignment() {
    return assignment;
  }

  boolean isCoordinator() {
    return coordinator;
  }

  Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("coordinator", coordinator);
    m.put("coordinatorIndex", coordinatorIndex == Integer.MAX_VALUE ? null : coordinatorIndex);
    m.put("msgsSent", messagesSent);
    m.put("msgsReceived", messagesReceived);
    m.put("renegotiations", renegotiations);
    m.put("takeovers", takeovers);
    m.put("ticksSinceAssign", tickNo - lastAssignTick);
    return m;
  }

  // --- Takt ------------------------------------------------------------------------

  void tick(Strategy.Situation s) {
    tickNo++;
    try {
      readInbox();
    } catch (CommandException e) {
      bot.monitor.log("Posteingang: " + e.getMessage());
    }
    if (coordinator) {
      statusByPlayer.put(bot.playerId, statusFields(s));
      statusTick.put(bot.playerId, tickNo);
      if (tickNo % ASSIGN_INTERVAL == 1) {
        try {
          assignAll();
        } catch (CommandException e) {
          bot.monitor.log("Zuteilung fehlgeschlagen: " + e.getMessage());
        }
      }
    } else {
      if (tickNo % STATUS_INTERVAL == 0 && coordinatorId != null) send(coordinatorId, Protocol.STATUS, statusFields(s));
      if (tickNo > STARTUP_GRACE_TICKS && tickNo - lastAssignTick > FAILOVER_BASE_TICKS + 3 * bot.index) takeOver();
    }
  }

  private void takeOver() {
    coordinator = true;
    coordinatorId = bot.playerId;
    coordinatorIndex = bot.index;
    assignment.coordinatorId = bot.playerId;
    takeovers++;
    lastAssignTick = tickNo;
    bot.monitor.event("COORDINATOR_TAKEOVER", "Kein Lebenszeichen des Koordinators seit " + (tickNo - lastAssignTick)
        + " Takten – dieser Bot übernimmt die Koordination von " + bot.camp, "camp", bot.camp);
  }

  private Map<String, String> statusFields(Strategy.Situation s) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("idx", String.valueOf(bot.index));
    f.put("colonies", String.valueOf(s.colonies));
    f.put("pop", String.valueOf(Math.round(s.homePopulation)));
    f.put("blackout", String.valueOf(s.blackout));
    f.put("capable", String.valueOf(bot.economy.capable()));
    f.put("specialty", assignment.specialty);
    f.put("role", assignment.role.name());
    f.put("fleet", String.valueOf(Math.round(s.fleetStrength)));
    f.put("transports", String.valueOf(s.transports));
    f.put("soldiers", String.valueOf(s.soldiers));
    f.put("drones", String.valueOf(s.drones));
    f.put("colonyShips", String.valueOf(s.colonyShips));
    f.put("strategy", bot.strategy.name());
    f.put("invasion", bot.military.invasionPhase());
    f.put("underAttack", String.valueOf(s.underGroundAttack));
    f.put("enemyAtHome", String.valueOf(s.enemyFleetAtHome));
    f.put("wallet", String.valueOf(Math.round(s.wallet)));
    f.put("homeSystem", bot.homeSystemId);
    return f;
  }

  // --- Posteingang -------------------------------------------------------------------

  private void readInbox() {
    for (JsonNode msg : bot.world.inbox()) {
      if (Json.bool(msg, "read")) continue;
      String subject = text(msg, "subject");
      String from = text(msg, "fromPlayerId");
      JsonNode sender = bot.world.player(from);
      String senderName = sender == null ? "?" : text(sender, "name");
      if (subject != null && subject.startsWith(Protocol.PREFIX) && bot.isOwnCampName(senderName)) {
        messagesReceived++;
        Map<String, String> f = Protocol.decode(text(msg, "body"));
        int senderIndex = Bot.parseIndex(senderName);
        switch (subject) {
          case Protocol.STATUS -> {
            statusByPlayer.put(from, f);
            statusTick.put(from, tickNo);
          }
          case Protocol.ASSIGN -> onAssign(from, senderIndex, senderName, f);
          case Protocol.CLAIM -> onClaim(from, senderName, f);
          case Protocol.CLAIM_OK -> bot.monitor.log("Anspruch bestätigt: " + f);
          case Protocol.CLAIM_DENY -> {
            deniedPlanets.add(f.getOrDefault("id", ""));
            bot.expansion.deny(f.getOrDefault("id", ""));
            bot.monitor.log("Anspruch abgelehnt: " + f);
          }
          case Protocol.REPORT -> bot.monitor.event("REPORT_RECEIVED", senderName + " meldet: " + f, "from", senderName);
          default -> {
          }
        }
      }
      try {
        bot.call("markMessageRead", Map.of("id", text(msg, "id")));
      } catch (CommandException ignored) {
        // bereits gelesen
      }
    }
    bot.world.invalidate("inbox");
  }

  private void onAssign(String from, int senderIndex, String senderName, Map<String, String> f) {
    boolean self = from.equals(bot.playerId);
    if (!self && coordinator && senderIndex < bot.index) {
      coordinator = false;
      bot.monitor.event("COORDINATOR_STEPDOWN", senderName + " koordiniert mit niedrigerem Index – dieser Bot tritt zurück", "to", senderName);
    } else if (!self && coordinator) {
      return; // ein höherer Index hält sich fälschlich für den Koordinator – ignorieren, er tritt bei unserer nächsten Zuteilung zurück
    }
    if (!self && senderIndex > coordinatorIndex && tickNo - lastAssignTick <= FAILOVER_BASE_TICKS) return;
    coordinatorId = from;
    coordinatorIndex = senderIndex;
    lastAssignTick = tickNo;
    String oldSpecialty = assignment.specialty;
    Strategy.MilitaryRole oldRole = assignment.role;
    String oldTarget = assignment.targetColonyId;
    assignment.specialty = f.getOrDefault("specialty", assignment.specialty);
    try {
      assignment.role = Strategy.MilitaryRole.valueOf(f.getOrDefault("role", assignment.role.name()));
    } catch (IllegalArgumentException ignored) {
      // unbekannte Rolle – alte behalten
    }
    assignment.targetColonyId = blankToNull(f.get("targetColonyId"));
    assignment.targetSystemId = blankToNull(f.get("targetSystemId"));
    assignment.targetPlanetId = blankToNull(f.get("targetPlanetId"));
    assignment.coordinatorId = from;
    assignment.seq = Long.parseLong(f.getOrDefault("seq", "0"));
    if (!Json.eq(oldSpecialty, assignment.specialty) || oldRole != assignment.role || !Json.eq(oldTarget, assignment.targetColonyId)) {
      bot.monitor.event("ASSIGNMENT", "Zuteilung von " + senderName + ": " + assignment
          + (oldSpecialty != null && !oldSpecialty.equals(assignment.specialty) ? " (Spezialisierung vorher " + oldSpecialty + ")" : ""),
          "specialty", assignment.specialty, "role", assignment.role.name(), "target", assignment.targetColonyId, "from", senderName);
    }
  }

  private void onClaim(String from, String senderName, Map<String, String> f) {
    if (!coordinator) return;
    String id = f.getOrDefault("id", "");
    String holder = planetClaims.get(id);
    boolean ok = holder == null || holder.equals(from);
    if (ok) planetClaims.put(id, from);
    send(from, ok ? Protocol.CLAIM_OK : Protocol.CLAIM_DENY, Map.of("kind", f.getOrDefault("kind", "planet"), "id", id));
    bot.monitor.log("Anspruch von " + senderName + " auf " + id + (ok ? " bestätigt" : " abgelehnt (bereits vergeben)"));
  }

  void claim(String kind, String id) {
    if (coordinator) {
      planetClaims.putIfAbsent(id, bot.playerId);
      return;
    }
    if (coordinatorId != null) send(coordinatorId, Protocol.CLAIM, Map.of("kind", kind, "id", id));
  }

  void report(String kind, Map<String, String> fields) {
    Map<String, String> f = new LinkedHashMap<>(fields);
    f.put("kind", kind);
    if (coordinator) {
      bot.monitor.event("REPORT_RECEIVED", "eigene Meldung: " + f, "from", bot.botName);
      return;
    }
    if (coordinatorId != null) send(coordinatorId, Protocol.REPORT, f);
  }

  private void send(String toPlayerId, String subject, Map<String, String> fields) {
    try {
      bot.call("sendMessage", Map.of("toPlayerId", toPlayerId, "subject", subject, "body", Protocol.encode(fields)));
      messagesSent++;
    } catch (CommandException e) {
      bot.monitor.log("Nachricht " + subject + " an " + toPlayerId + " abgelehnt: " + e.getMessage());
    }
  }

  // --- Zuteilung (nur Koordinator) ---------------------------------------------------------

  private void assignAll() {
    List<JsonNode> members = new ArrayList<>();
    for (JsonNode p : bot.world.players()) if (bot.isOwnCampName(text(p, "name"))) members.add(p);
    members.sort((a, b) -> Integer.compare(Bot.parseIndex(text(a, "name")), Bot.parseIndex(text(b, "name"))));

    List<JsonNode> alive = new ArrayList<>();
    for (JsonNode m : members) {
      String id = text(m, "id");
      boolean fresh = id.equals(bot.playerId) || tickNo < STARTUP_GRACE_TICKS
          || tickNo - statusTick.getOrDefault(id, -1000) <= STALE_STATUS_TICKS;
      if (fresh) alive.add(m);
      else if (specialtyOf.containsKey(id)) {
        bot.monitor.event("MEMBER_LOST", text(m, "name") + " verstummt (" + specialtyOf.get(id) + ") – Rolle wird neu vergeben", "member", text(m, "name"));
        specialtyOf.remove(id);
        roleOf.remove(id);
        targetColonyOf.remove(id);
      }
    }
    if (alive.isEmpty()) return;

    // 1. Spezialisierungen: stabil, Lücken nach Bot-Index füllen (derselbe Schlüssel wie die
    //    vorläufige Selbstzuteilung in bootstrap – sonst wechselt jeder Bot beim ersten ASSIGN die Rolle)
    for (JsonNode m : alive) {
      String id = text(m, "id");
      int rank = Math.max(0, Bot.parseIndex(text(m, "name")) - 1);
      if (!specialtyOf.containsKey(id)) specialtyOf.put(id, defaultSpecialty(rank));
      if (!roleOf.containsKey(id)) roleOf.put(id, defaultRole(rank));
    }
    ensureCriticalSpecialtiesCovered(alive);
    renegotiateIncapable(alive);

    // 2. Militärrollen: Bedrohte werden Verteidiger
    boolean threat = false;
    for (JsonNode m : alive) {
      Map<String, String> st = statusByPlayer.get(text(m, "id"));
      boolean attacked = st != null && ("true".equals(st.get("underAttack")) || "true".equals(st.get("enemyAtHome")));
      if (attacked) threat = true;
    }

    // 3. Ziele
    List<JsonNode> invaders = new ArrayList<>();
    List<JsonNode> raiders = new ArrayList<>();
    for (JsonNode m : alive) {
      Strategy.MilitaryRole role = effectiveRole(m);
      if (role == Strategy.MilitaryRole.INVADER) invaders.add(m);
      if (role == Strategy.MilitaryRole.RAIDER) raiders.add(m);
    }
    List<EnemyColony> enemies = enemyColonies();
    Set<String> taken = new HashSet<>();
    Map<String, EnemyColony> targetByInvader = new HashMap<>();
    for (JsonNode inv : invaders) {
      String id = text(inv, "id");
      String home = text(inv, "homeSystemId");
      EnemyColony keep = null;
      String previous = targetColonyOf.get(id);
      for (EnemyColony e : enemies) if (e.colonyId.equals(previous) && !taken.contains(e.colonyId)) keep = e;
      if (keep == null) {
        double bestScore = Double.MAX_VALUE;
        for (EnemyColony e : enemies) {
          if (taken.contains(e.colonyId)) continue;
          double score = e.score(bot.world.hops(home, e.systemId));
          if (score < bestScore) {
            bestScore = score;
            keep = e;
          }
        }
        if (keep != null && previous != null && !previous.equals(keep.colonyId)) {
          bot.monitor.event("TARGET_REASSIGNED", text(inv, "name") + ": Ziel " + previous + " -> " + keep.name, "member", text(inv, "name"));
        }
      }
      if (keep != null) {
        taken.add(keep.colonyId);
        targetColonyOf.put(id, keep.colonyId);
        targetByInvader.put(id, keep);
      } else {
        targetColonyOf.remove(id);
      }
    }

    seq++;
    for (JsonNode m : alive) {
      String id = text(m, "id");
      Map<String, String> f = new LinkedHashMap<>();
      f.put("specialty", specialtyOf.get(id));
      Strategy.MilitaryRole role = effectiveRole(m);
      f.put("role", role.name());
      f.put("seq", String.valueOf(seq));
      f.put("coordinatorIndex", String.valueOf(bot.index));
      EnemyColony target = null;
      if (role == Strategy.MilitaryRole.INVADER) target = targetByInvader.get(id);
      if (role == Strategy.MilitaryRole.RAIDER) {
        int k = raiders.indexOf(m);
        if (k < invaders.size()) target = targetByInvader.get(text(invaders.get(k), "id"));
        if (target == null && !enemies.isEmpty()) {
          String home = text(m, "homeSystemId");
          double best = Double.MAX_VALUE;
          for (EnemyColony e : enemies) {
            double score = e.enemyFleetStrength <= 0 ? Double.MAX_VALUE : bot.world.hops(home, e.systemId) * 30.0 + e.enemyFleetStrength;
            if (score < best) {
              best = score;
              target = e;
            }
          }
        }
      }
      if (target != null) {
        f.put("targetColonyId", target.colonyId);
        f.put("targetSystemId", target.systemId);
        f.put("targetPlanetId", target.planetId);
      }
      if (id.equals(bot.playerId)) {
        onAssign(bot.playerId, bot.index, bot.botName, f);
        continue;
      }
      send(id, Protocol.ASSIGN, f);
    }
    lastAssignTick = tickNo;
    bot.monitor.event("ASSIGN_ROUND", "Zuteilung Nr. " + seq + " an " + alive.size() + " Mitglieder (" + invaders.size() + " Invasoren, "
        + raiders.size() + " Raider, Bedrohung=" + threat + ", " + enemies.size() + " Feindkolonien bekannt)", "seq", seq, "members", alive.size(),
        "invaders", invaders.size(), "raiders", raiders.size(), "enemyColonies", enemies.size(), "threat", threat);
  }

  private Strategy.MilitaryRole effectiveRole(JsonNode member) {
    String id = text(member, "id");
    Map<String, String> st = statusByPlayer.get(id);
    if (st != null && ("true".equals(st.get("underAttack")) || "true".equals(st.get("enemyAtHome")))) return Strategy.MilitaryRole.DEFENDER;
    return roleOf.getOrDefault(id, Strategy.MilitaryRole.DEFENDER);
  }

  /** Nahrung und Medizin dürfen nie unbesetzt sein – notfalls wechselt ein Baustoff-Produzent. */
  private void ensureCriticalSpecialtiesCovered(List<JsonNode> alive) {
    for (String critical : List.of(Catalog.FOOD, Catalog.MEDICINE)) {
      boolean covered = false;
      for (JsonNode m : alive) if (critical.equals(specialtyOf.get(text(m, "id")))) covered = true;
      if (covered) continue;
      for (JsonNode m : alive) {
        String id = text(m, "id");
        if (criticality(specialtyOf.get(id)) == 1) {
          bot.monitor.event("ROLE_RENEGOTIATED", text(m, "name") + " übernimmt " + critical + " (vorher " + specialtyOf.get(id) + ") – Rolle war unbesetzt",
              "member", text(m, "name"), "specialty", critical);
          specialtyOf.put(id, critical);
          renegotiations++;
          break;
        }
      }
    }
  }

  private void renegotiateIncapable(List<JsonNode> alive) {
    for (JsonNode m : alive) {
      String id = text(m, "id");
      Map<String, String> st = statusByPlayer.get(id);
      boolean capable = st == null || !"false".equals(st.get("capable"));
      incapableCount.merge(id, capable ? 0 : 1, (a, b) -> b == 0 ? 0 : a + b);
      if (incapableCount.get(id) < INCAPABLE_STATUSES) continue;
      String mySpec = specialtyOf.get(id);
      if (criticality(mySpec) <= 1) continue;
      JsonNode partner = null;
      int partnerCrit = Integer.MAX_VALUE;
      for (JsonNode other : alive) {
        String oid = text(other, "id");
        if (oid.equals(id) || incapableCount.getOrDefault(oid, 0) > 0) continue;
        int crit = criticality(specialtyOf.get(oid));
        if (crit < criticality(mySpec) && crit < partnerCrit) {
          partnerCrit = crit;
          partner = other;
        }
      }
      if (partner == null) continue;
      String pid = text(partner, "id");
      String theirs = specialtyOf.get(pid);
      specialtyOf.put(pid, mySpec);
      specialtyOf.put(id, theirs);
      incapableCount.put(id, 0);
      renegotiations++;
      bot.monitor.event("ROLE_RENEGOTIATED", text(m, "name") + " kann " + mySpec + " nicht mehr liefern (Blackout/Verlust) – "
          + text(partner, "name") + " übernimmt, " + text(m, "name") + " wechselt auf " + theirs,
          "member", text(m, "name"), "partner", text(partner, "name"), "specialty", mySpec);
    }
  }

  private static int criticality(String specialty) {
    if (Catalog.FOOD.equals(specialty)) return 3;
    if (Catalog.MEDICINE.equals(specialty)) return 2;
    return 1;
  }

  static String defaultSpecialty(int rank) {
    if (rank % 5 == 4) return Catalog.SPECIALTY_PRODUCTS.get((rank / 5) % Catalog.SPECIALTY_PRODUCTS.size());
    return rank % 2 == 0 ? Catalog.FOOD : Catalog.MEDICINE;
  }

  static Strategy.MilitaryRole defaultRole(int rank) {
    return switch (rank % 4) {
      case 0 -> Strategy.MilitaryRole.INVADER;
      case 1 -> Strategy.MilitaryRole.RAIDER;
      case 2 -> Strategy.MilitaryRole.SETTLER;
      default -> Strategy.MilitaryRole.DEFENDER;
    };
  }

  // --- Aufklärung ------------------------------------------------------------------------------

  private record EnemyColony(String colonyId, String name, String systemId, String planetId, double garrisonValue,
                             double population, double enemyFleetStrength) {
    double score(int hops) {
      double distance = hops == Integer.MAX_VALUE ? 1e6 : hops * 30.0;
      return garrisonValue * 2 + population * 0.01 + distance + enemyFleetStrength * 2;
    }
  }

  private List<EnemyColony> enemyColonies() {
    Map<String, Double> enemyFleetBySystem = new HashMap<>();
    for (JsonNode f : bot.world.allFleets()) {
      JsonNode owner = bot.world.player(text(f, "ownerId"));
      if (owner == null || !bot.isEnemyCampName(text(owner, "name")) || !World.hasWarships(f)) continue;
      enemyFleetBySystem.merge(text(f, "systemId"), World.strength(f), Double::sum);
    }
    Set<String> systems = new HashSet<>();
    List<EnemyColony> out = new ArrayList<>();
    for (JsonNode p : bot.world.players()) {
      if (!bot.isEnemyCampName(text(p, "name"))) continue;
      String sys = text(p, "homeSystemId");
      if (sys == null || !systems.add(sys)) continue;
      for (JsonNode c : bot.world.coloniesInSystem(sys)) {
        JsonNode owner = bot.world.player(text(c, "ownerId"));
        if (owner == null || !bot.isEnemyCampName(text(owner, "name"))) continue;
        String cid = text(c, "id");
        out.add(new EnemyColony(cid, text(c, "name"), sys, text(c, "planetId"), World.activeDroneValue(bot.world.garrison(cid)),
            bot.world.population(cid), enemyFleetBySystem.getOrDefault(sys, 0.0)));
      }
    }
    return out;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
