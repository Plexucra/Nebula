package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.nebula.npcbot.Json.text;

/**
 * Raum- und Bodenkrieg eines Bots.
 *
 * <p><b>Raum</b> ({@link SpaceState}): die Kampfflotte blockiert zu Hause
 * (nur eine blockierende Flotte ist angreifbar – ohne diese Haltung hätte
 * kein Gegner je ein Ziel), zieht als RAIDER gegen gegnerische
 * Blockadeflotten, wenn die Stärkeschätzung ({@link Catalog#SHIP_MILITARY_WEIGHT})
 * deutlich zu ihren Gunsten steht, zieht sich bei schweren Verlusten zurück
 * und kehrt bei Bedrohung der Heimat um.</p>
 *
 * <p><b>Boden</b> ({@link InvasionPhase}) – die eigentliche Neuerung: eine
 * vollständige Landungsoperation gegen die vom Koordinator zugeteilte
 * Zielkolonie, entlang der Regeln aus Umsetzungskonzept/28 und /30:</p>
 * <ol>
 *   <li>Bedarf aus der (öffentlich abfragbaren) Garnison des Ziels ableiten:
 *       Drohnenklasse nach Kontermatrix, Drohnenzahl nach Kampfwert × 3,
 *       Soldaten für Kommando (5 Drohnen je Soldat) UND Belagerung (der
 *       Loyalitätsverlust je Tick ist Soldaten/Bevölkerung – zu wenige
 *       Soldaten gewinnen nie, Umsetzungskonzept/30 §B).</li>
 *   <li>Mannschaftstransporter über die Werft, Soldaten/Drohnen über das
 *       Ausbildungszentrum (Zutaten vorgefertigt im Industriekomplex).</li>
 *   <li>Soldaten einschiffen, Drohnen als Fracht in den (vom Handel
 *       geliehenen) Frachter, Kampfflotte als Eskorte, gemeinsam zum
 *       Zielsystem.</li>
 *   <li>Dort: blockierende Feindflotte am Zielplaneten mit der Eskorte
 *       angreifen, sofern beherrschbar; beide Transportflotten in den Orbit,
 *       landen, Verband bilden.</li>
 *   <li>Bodengefecht eröffnen, Kampf- und Belagerungsphase beobachten;
 *       Rückzug bei Verlust der aktiven Drohnen im Kampf oder bei stehender
 *       Belagerung (Loyalität sinkt nicht mehr); nach Eroberung Versorgung
 *       anfordern und heimkehren.</li>
 * </ol>
 */
final class Military {
  enum SpaceState {IDLE, TRAVELING, ENGAGING, RETURNING}

  enum InvasionPhase {NONE, BUILDING, LOADING, TRAVELING, ORBIT, LANDED, FIGHTING, RETURNING}

  private static final double RAID_SUPERIORITY = 1.2;
  private static final double ESCORT_SUPERIORITY = 0.8;
  private static final double RETREAT_BELOW_SHARE = 0.4;
  /**
   * Sicherheitsfaktor der Drohnenzahl gegenüber dem AKTUELL gesichteten
   * Gegnerbestand. Deutlich über 1, weil zwischen der Bedarfsrechnung und der
   * Landung Spieltage liegen: Die Zielkolonie rekrutiert in der Zwischenzeit
   * weiter und aktiviert im Gefecht nachrückende Reserven (Mechanik/05 §4).
   * Mit dem alten Wert 1,5 gingen im Testlauf drei von vier Bodengefechten
   * verloren, obwohl die Rechnung beim Aufbruch aufging.
   */
  private static final double DRONE_SUPERIORITY = 3.0;
  /**
   * Soldaten als Anteil der Zielbevölkerung. 5 % senken die Loyalität zwar
   * schneller, als sie sich erholt – aber in der Belagerung kämpfen 10 % der
   * Bevölkerung als Aufständische zurück (Mechanik/05 §2): Gegen 18 000
   * Einwohner starben im Testlauf 44 der 1000 Soldaten JE TICK, und weil der
   * Loyalitätsverlust an der Soldatenzahl hängt, wurde die Belagerung immer
   * langsamer, während sie schmolz – bei Loyalität 35 war der Verband alle.
   * Mit 10 % hält die Landung die 18 bis 20 Ticks bis zur Übergabe durch.
   */
  private static final double SIEGE_SOLDIER_SHARE = 0.10;
  private static final int MIN_SOLDIERS = 12;
  /**
   * Untergrenze der Drohnenzahl je Landung. Sie muss den ÜBLICHEN Bestand einer
   * verteidigten Kolonie schlagen, nicht den zufällig gerade gesichteten: Beim
   * Aufbruch steht die Zielgarnison oft bei null, bis zur Landung vergehen aber
   * Spieltage, in denen jede Kolonie ihre Standardgarnison (8 Soldaten, 40
   * Drohnen) aufbaut. Mit 30 Drohnen gingen fünf von sechs Landungen verloren.
   */
  private static final int MIN_DRONES = 120;
  private static final int HOME_SOLDIER_RESERVE = 2;
  private static final int HOME_DRONE_RESERVE = 10;
  private static final int SIEGE_STALL_TICKS = 6;
  private static final int ROLE_MISMATCH_ABORT_TICKS = 60;
  private int roleMismatchTicks;

  private final Bot bot;
  private String combatFleetId;
  private double initialStrength;
  private SpaceState space = SpaceState.IDLE;
  private String spaceTargetSystemId;
  private int startShipCount;
  private String battleId;
  private int battlesFought;
  private int battlesWon;
  private int battlesLost;
  private int retreats;

  private InvasionPhase phase = InvasionPhase.NONE;
  private String targetColonyId;
  private String targetSystemId;
  private String targetPlanetId;
  private String targetName = "?";
  private String landingFleetId;
  private String groupId;
  private String groundBattleId;
  private int neededSoldiers;
  private int neededDrones;
  /** Bedarf VOR der Frachtraum-Deckelung – daraus folgt, wie viele Frachter noch fehlen. */
  private int neededDronesUncapped;
  private String droneType = Catalog.DRONE_MEDIUM;
  private int wave;
  private double lastLoyalty = -1;
  private int stallTicks;
  private int landings;
  private int conquests;
  private int groundDefeats;
  private long phaseSince;
  private String blockedReason = "";

  Military(Bot bot) {
    this.bot = bot;
  }

  // --- Sicht für Situation/Monitoring ------------------------------------------------

  void bootstrap() {
    for (JsonNode f : bot.world.ownFleets()) {
      if (World.hasWarships(f)) {
        combatFleetId = text(f, "id");
        initialStrength = World.strength(f);
        break;
      }
    }
    phaseSince = System.currentTimeMillis();
  }

  JsonNode combatFleet() {
    return combatFleetId == null ? null : bot.world.ownFleet(combatFleetId);
  }

  double fleetStrength() {
    JsonNode f = combatFleet();
    return f == null ? 0 : World.strength(f);
  }

  double initialStrength() {
    return initialStrength;
  }

  int warshipCount() {
    int n = 0;
    for (JsonNode f : bot.world.ownFleets()) {
      for (String t : Catalog.WARSHIP_TYPES) n += (int) World.shipCount(f, t);
    }
    return n;
  }

  int transportsOwned() {
    int n = (int) bot.world.stock(bot.homeColonyId, Catalog.TROOP_TRANSPORT);
    for (JsonNode f : bot.world.ownFleets()) n += (int) World.shipCount(f, Catalog.TROOP_TRANSPORT);
    for (JsonNode q : bot.world.shipyardQueue(bot.homeColonyId)) if (Json.eq(text(q, "shipProductTypeId"), Catalog.TROOP_TRANSPORT)) n++;
    return n;
  }

  boolean fleetInBattle() {
    return space == SpaceState.ENGAGING;
  }

  boolean invasionActive() {
    return phase != InvasionPhase.NONE && phase != InvasionPhase.BUILDING;
  }

  String invasionPhase() {
    return phase.name();
  }

  String spaceState() {
    return space.name();
  }

  String blockedReason() {
    return blockedReason;
  }

  Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("space", space.name());
    m.put("invasion", phase.name());
    m.put("target", targetName);
    m.put("battles", battlesFought);
    m.put("battlesWon", battlesWon);
    m.put("battlesLost", battlesLost);
    m.put("retreats", retreats);
    m.put("landings", landings);
    m.put("conquests", conquests);
    m.put("groundDefeats", groundDefeats);
    m.put("needSoldiers", neededSoldiers);
    m.put("needDrones", neededDrones);
    m.put("wave", wave);
    m.put("blocked", blockedReason);
    return m;
  }

  boolean enemyFleetAtHome() {
    for (JsonNode f : bot.world.allFleets()) {
      if (!Json.eq(text(f, "systemId"), bot.homeSystemId) || Json.eq(text(f, "ownerId"), bot.playerId)) continue;
      JsonNode owner = bot.world.player(text(f, "ownerId"));
      if (owner != null && bot.isEnemyCampName(text(owner, "name")) && World.hasWarships(f) && World.stationed(f)) return true;
    }
    return false;
  }

  boolean underGroundAttack() {
    for (JsonNode b : bot.world.activeGroundBattles()) if (Json.eq(text(b, "defenderId"), bot.playerId)) return true;
    return false;
  }

  // --- Planung: Wünsche an die Wirtschaft ------------------------------------------------

  /** Vor der Wirtschaft: Soldaten-/Drohnenbedarf für das zugeteilte Ziel in den Plan schreiben. */
  void prepare(Strategy.Plan plan, Strategy.Assignment a) {
    if (a.role != Strategy.MilitaryRole.INVADER) return;
    if (phase == InvasionPhase.NONE) {
      if (a.targetColonyId == null) {
        blockedReason = "kein Ziel zugeteilt";
        return;
      }
      adoptTarget(a);
      phase = InvasionPhase.BUILDING;
      phaseSince = System.currentTimeMillis();
    }
    if (phase == InvasionPhase.BUILDING) {
      sizeForce();
      plan.wantTransports = neededTransports();
      plan.wantFreighters = neededFreighters();
      plan.wantDroneType = droneType;
      int aboard = landingFleetId == null ? 0 : (int) Json.dbl(bot.world.troopCapacity(landingFleetId), "soldiersAboard");
      JsonNode freighterFleet = bot.trade.freighterFleetId() == null ? null : bot.world.ownFleet(bot.trade.freighterFleetId());
      int loaded = freighterFleet == null ? 0 : (int) World.cargoQty(freighterFleet, droneType);
      plan.wantSoldiers = Math.max(0, neededSoldiers + HOME_SOLDIER_RESERVE - aboard);
      plan.wantDrones = Math.max(0, neededDrones + (droneType.equals(Catalog.DRONE_LIGHT) ? HOME_DRONE_RESERVE : 0) - loaded);
    }
  }

  /**
   * Wie viele Drohnen des Typs in den (vom Handel geliehenen) Frachter passen –
   * gerechnet aus Masse UND Volumen, beides aus dem Produktkatalog des Servers.
   */
  private int droneCargoCapacity(String type) {
    String freighterId = bot.trade.freighterFleetId();
    if (freighterId == null) return 0;
    JsonNode capacity = bot.world.cargoCapacity(freighterId);
    double freeMass = Json.dbl(capacity, "capacityMassKg") - Json.dbl(capacity, "usedMassKg");
    double freeVolume = Json.dbl(capacity, "capacityVolumeM3") - Json.dbl(capacity, "usedVolumeM3");
    double[] size = bot.world.productSize(type);
    if (size[0] <= 0 && size[1] <= 0) return 0;
    double byMass = size[0] > 0 ? Math.floor(freeMass / size[0]) : Double.MAX_VALUE;
    double byVolume = size[1] > 0 ? Math.floor(freeVolume / size[1]) : Double.MAX_VALUE;
    return (int) Math.max(0, Math.min(byMass, byVolume));
  }

  /**
   * Zahl der Frachter, die {@link #neededDrones} Drohnen tragen. Der erste ist
   * der Handelsfrachter; alles darüber muss die Werft bauen. Ohne diese Rechnung
   * war die Landung auf eine Frachterladung gedeckelt (48 schwere Drohnen) und
   * verlor gegen jede Kolonie, die selbst eine Invasion vorbereitet – deren
   * Garnison stand im Testlauf bei 87 aktiven Drohnen.
   */
  private int neededFreighters() {
    double perFreighter = dronesPerFreighter(droneType);
    if (perFreighter <= 0) return 1;
    return Math.max(1, (int) Math.ceil(neededDronesUncapped / perFreighter));
  }

  /** Wie viele Drohnen dieses Typs EIN Frachter trägt (Masse und Volumen, beide aus dem Katalog). */
  private double dronesPerFreighter(String type) {
    double[] ship = bot.world.shipCargoCapacity(Catalog.FREIGHTER);
    double[] drone = bot.world.productSize(type);
    if (drone[0] <= 0 && drone[1] <= 0) return 0;
    double byMass = drone[0] > 0 ? Math.floor(ship[0] / drone[0]) : Double.MAX_VALUE;
    double byVolume = drone[1] > 0 ? Math.floor(ship[1] / drone[1]) : Double.MAX_VALUE;
    return Math.max(0, Math.min(byMass, byVolume));
  }

  /** Zahl der Mannschaftstransporter für {@link #neededSoldiers} – 27 Plätze je Schiff (Katalog). */
  private int neededTransports() {
    double perTransport = bot.world.troopCapacityPerTransport();
    return (int) Math.ceil(neededSoldiers / Math.max(1, perTransport));
  }

  private void adoptTarget(Strategy.Assignment a) {
    targetColonyId = a.targetColonyId;
    targetSystemId = a.targetSystemId;
    targetPlanetId = a.targetPlanetId;
    JsonNode colony = bot.world.colony(targetColonyId);
    targetName = Json.isNull(colony) ? targetColonyId : text(colony, "name");
    if (targetPlanetId == null && !Json.isNull(colony)) targetPlanetId = text(colony, "planetId");
    if (targetSystemId == null && !Json.isNull(colony)) targetSystemId = text(colony, "systemId");
  }

  private void sizeForce() {
    JsonNode garrison = bot.world.garrison(targetColonyId);
    double population = bot.world.population(targetColonyId);
    String dominant = World.dominantDrone(garrison);
    droneType = dominant == null ? Catalog.DRONE_MEDIUM : Catalog.counterFor(dominant);
    double enemyValue = World.activeDroneValue(garrison);
    double myValue = Catalog.DRONE_VALUE.get(droneType);
    // Konter wirkt ×2 auf den eigenen Schaden – die Hälfte des nominellen Werts genügt, plus Sicherheitsaufschlag.
    int drones = (int) Math.ceil(enemyValue * DRONE_SUPERIORITY / (myValue * 2));
    neededDrones = Math.max(MIN_DRONES, drones) * (1 + wave);
    // Mehr, als in den Frachtraum passt, kann nicht mitkommen: Eine schwere
    // Drohne wiegt 583 t, ein Frachter trägt 28,4 kt – also 48 Stück. Ohne
    // diese Deckelung forderte der Aufbau Drohnen an, die anschließend beim
    // Beladen abgelehnt wurden ("Massekapazität der Flotte reicht nicht aus"),
    // und die Landungsoperation kam nie los.
    neededDronesUncapped = neededDrones;
    int fits = droneCargoCapacity(droneType);
    if (fits > 0) neededDrones = Math.max(1, Math.min(neededDrones, fits));
    int forCommand = (int) Math.ceil(neededDrones / (double) Catalog.DRONES_PER_SOLDIER);
    int forSiege = (int) Math.ceil(population * SIEGE_SOLDIER_SHARE);
    neededSoldiers = Math.max(MIN_SOLDIERS, Math.max(forCommand, forSiege)) * (1 + wave);
  }

  // --- Ausführung -------------------------------------------------------------------------

  void act(Strategy.Plan plan, Strategy.Situation s, Strategy.Assignment a, Strategy strategy) {
    try {
      spaceTick(s, a, strategy);
    } catch (CommandException e) {
      bot.monitor.log("Raumkampf: Befehl abgelehnt: " + e.getMessage());
    }
    try {
      invasionTick(s, a, strategy);
    } catch (CommandException e) {
      bot.monitor.log("Landungsoperation: Befehl abgelehnt: " + e.getMessage());
    }
  }

  // --- Raum ---------------------------------------------------------------------------------

  private void spaceTick(Strategy.Situation s, Strategy.Assignment a, Strategy strategy) {
    JsonNode fleet = combatFleet();
    if (fleet == null || World.shipCount(fleet) == 0) {
      adoptNewCombatFleet();
      return;
    }
    switch (space) {
      case IDLE -> {
        boolean home = Json.eq(text(fleet, "systemId"), bot.homeSystemId);
        if (!World.stationed(fleet)) return;
        if (!home) {
          moveFleet(combatFleetId, bot.homeSystemId);
          space = SpaceState.RETURNING;
          return;
        }
        if (strategy == Strategy.UNDER_ATTACK && engageIfSuperior(bot.homeSystemId, 1.0)) return;
        if (strategy == Strategy.RAID && a.hasTarget() && !a.targetSystemId.equals(bot.homeSystemId)) {
          if (launchRaid(a.targetSystemId)) return;
        }
        ensureHomeBlockade(fleet);
      }
      case TRAVELING -> {
        if (World.stationed(fleet) && Json.eq(text(fleet, "systemId"), spaceTargetSystemId)) {
          if (!engageIfSuperior(spaceTargetSystemId, ESCORT_SUPERIORITY)) {
            // Nichts (mehr) angreifbar oder zu stark – als Eskorte bleibt die Flotte im System, solange dort eine Landung läuft.
            if (phase == InvasionPhase.NONE || !spaceTargetSystemId.equals(targetSystemId)) {
              bot.monitor.log("Im Zielsystem " + bot.world.systemName(spaceTargetSystemId) + " kein angreifbares Ziel – Rückkehr.");
              moveFleet(combatFleetId, bot.homeSystemId);
              space = SpaceState.RETURNING;
            }
          }
        }
      }
      case ENGAGING -> monitorBattle(fleet);
      case RETURNING -> {
        if (World.stationed(fleet) && Json.eq(text(fleet, "systemId"), bot.homeSystemId)) {
          space = SpaceState.IDLE;
          ensureHomeBlockade(fleet);
        }
      }
    }
  }

  private void adoptNewCombatFleet() {
    // Kampfflotte vernichtet: neu gebaute Kriegsschiffe aus dem Lager in eine neue Flotte stellen.
    for (String type : Catalog.WARSHIP_TYPES) {
      double stock = bot.world.stock(bot.homeColonyId, type);
      if (stock < 1) continue;
      Map<String, Object> payload = new HashMap<>();
      payload.put("colonyId", bot.homeColonyId);
      payload.put("shipProductTypeId", type);
      payload.put("quantity", stock);
      payload.put("targetFleetId", combatFleetId != null && bot.world.ownFleet(combatFleetId) != null ? combatFleetId : null);
      bot.call("transferShipsToFleet", payload);
      bot.world.invalidate("fleets", "warehouse");
      if (combatFleetId == null || bot.world.ownFleet(combatFleetId) == null) {
        for (JsonNode f : bot.world.ownFleets()) if (World.hasWarships(f)) combatFleetId = text(f, "id");
      }
      bot.monitor.event("FLEET_REBUILT", (long) stock + "x " + type + " in die Kampfflotte gestellt", "type", type, "qty", stock);
    }
    if (combatFleetId != null && bot.world.ownFleet(combatFleetId) == null) combatFleetId = null;
  }

  private void ensureHomeBlockade(JsonNode fleet) {
    for (JsonNode b : bot.world.blockadesInSystem(bot.homeSystemId)) if (Json.eq(text(b, "fleetId"), combatFleetId)) return;
    if (!Json.eq(text(fleet, "locationPlanetId"), bot.homePlanetId)) {
      bot.call("moveFleetWithinSystem", Map.of("fleetId", combatFleetId, "target", Map.of("kind", "ColonyOrbit", "colonyId", bot.homeColonyId)));
      bot.world.invalidate("fleets");
    }
    // Neu gebaute Kriegsschiffe aus dem Lager mitnehmen, solange die Flotte zu Hause liegt.
    for (String type : Catalog.WARSHIP_TYPES) {
      double stock = bot.world.stock(bot.homeColonyId, type);
      if (stock >= 1) {
        bot.call("transferShipsToFleet", Map.of("colonyId", bot.homeColonyId, "shipProductTypeId", type, "quantity", stock, "targetFleetId", combatFleetId));
        bot.monitor.event("FLEET_REINFORCED", (long) stock + "x " + type + " der Kampfflotte hinzugefügt", "type", type, "qty", stock);
        bot.world.invalidate("fleets", "warehouse");
      }
    }
    try {
      bot.call("formBlockade", Map.of("fleetId", combatFleetId, "anchor", Map.of("kind", "PlanetOrbit", "planetId", bot.homePlanetId)));
      bot.monitor.log("Heimatplanet blockiert (Verteidigungshaltung).");
      bot.world.invalidate("blockadesInSystem");
    } catch (CommandException e) {
      // bereits blockiert o. ä.
    }
  }

  private boolean launchRaid(String systemId) {
    JsonNode strongest = strongestEnemyBlockade(systemId);
    if (strongest == null) {
      blockedReason = "kein blockierendes Feindziel in " + bot.world.systemName(systemId);
      return false;
    }
    double mine = fleetStrength();
    double theirs = World.strength(strongest);
    if (mine < theirs * RAID_SUPERIORITY) {
      blockedReason = String.format("Feindflotte zu stark (%.0f vs. %.0f)", mine, theirs);
      return false;
    }
    bot.trade.topUpFuel(combatFleetId, bot.world.hops(bot.homeSystemId, systemId));
    moveFleet(combatFleetId, systemId);
    spaceTargetSystemId = systemId;
    startShipCount = World.shipCount(combatFleet());
    space = SpaceState.TRAVELING;
    bot.monitor.event("RAID_LAUNCHED", String.format("Angriff auf %s (Stärke %.0f vs. %.0f)", bot.world.systemName(systemId), mine, theirs),
        "system", systemId, "mine", mine, "theirs", theirs);
    return true;
  }

  /** Feindliche, blockierende Flotten in einem System – nur sie sind angreifbar (BattleCommands.engageBattle). */
  private JsonNode strongestEnemyBlockade(String systemId) {
    JsonNode best = null;
    double bestStrength = -1;
    for (JsonNode f : bot.world.attackableFleetsInSystem(systemId)) {
      double s = World.strength(f);
      if (s > bestStrength) {
        bestStrength = s;
        best = f;
      }
    }
    return best;
  }

  private boolean engageIfSuperior(String systemId, double superiority) {
    JsonNode target = strongestEnemyBlockade(systemId);
    if (target == null) return false;
    double mine = fleetStrength();
    double theirs = World.strength(target);
    if (mine < theirs * superiority) {
      blockedReason = String.format("Gegner in %s zu stark (%.0f vs. %.0f)", bot.world.systemName(systemId), mine, theirs);
      return false;
    }
    try {
      bot.call("engageBattle", Map.of("attackerFleetId", combatFleetId, "defenderFleetId", text(target, "id")));
    } catch (CommandException e) {
      bot.monitor.log("Gefechtsbeginn abgelehnt: " + e.getMessage());
      return false;
    }
    bot.world.invalidate("activeBattles", "fleets");
    for (JsonNode b : bot.world.activeBattles()) if (Json.eq(text(b, "attackerFleetId"), combatFleetId)) battleId = text(b, "id");
    startShipCount = World.shipCount(combatFleet());
    space = SpaceState.ENGAGING;
    battlesFought++;
    bot.monitor.event("BATTLE_STARTED", String.format("Gefecht %s in %s gegen %s (Stärke %.0f vs. %.0f)", battleId,
        bot.world.systemName(systemId), text(target, "name"), mine, theirs), "battleId", battleId, "system", systemId);
    return true;
  }

  private void monitorBattle(JsonNode fleet) {
    JsonNode active = null;
    for (JsonNode b : bot.world.activeBattles()) if (Json.eq(text(b, "id"), battleId)) active = b;
    if (active == null) {
      int ships = World.shipCount(fleet);
      boolean survived = ships > 0;
      if (survived) battlesWon++; else battlesLost++;
      bot.monitor.event("BATTLE_ENDED", "Gefecht " + battleId + " beendet – Flotte hat " + ships + "/" + startShipCount + " Schiffe",
          "battleId", battleId, "shipsLeft", ships, "shipsStart", startShipCount);
      battleId = null;
      if (survived) {
        moveFleet(combatFleetId, bot.homeSystemId);
        space = SpaceState.RETURNING;
      } else {
        space = SpaceState.IDLE;
      }
      return;
    }
    int now = World.shipCount(fleet);
    if (now < startShipCount * RETREAT_BELOW_SHARE) {
      try {
        bot.call("retreatFromBattle", Map.of("battleId", battleId));
        retreats++;
        bot.monitor.event("BATTLE_RETREAT", "Rückzug aus " + battleId + " (" + now + "/" + startShipCount + " Schiffe)", "battleId", battleId);
      } catch (CommandException e) {
        bot.monitor.log("Rückzug abgelehnt: " + e.getMessage());
      }
      bot.world.invalidate("activeBattles", "fleets");
    }
  }

  // --- Boden ---------------------------------------------------------------------------------

  private void invasionTick(Strategy.Situation s, Strategy.Assignment a, Strategy strategy) {
    if (phase == InvasionPhase.NONE) return;
    if (a.role != Strategy.MilitaryRole.INVADER && phase == InvasionPhase.BUILDING) {
      // Eine vorübergehende Rolle (DEFENDER, solange eine Feindflotte durchzieht) soll
      // den Aufbau nicht verwerfen – erst ein dauerhafter Rollenwechsel beendet ihn.
      roleMismatchTicks++;
      blockedReason = "Rolle " + a.role + " – Aufbau ruht";
      if (roleMismatchTicks >= ROLE_MISMATCH_ABORT_TICKS) abort("Rolle dauerhaft geändert – Aufbau der Landungsoperation beendet");
      return;
    }
    roleMismatchTicks = 0;
    switch (phase) {
      case BUILDING -> building(strategy);
      case LOADING -> loading();
      case TRAVELING -> traveling();
      case ORBIT -> orbit();
      case LANDED -> landed();
      case FIGHTING -> fighting();
      case RETURNING -> returning();
      default -> {
      }
    }
  }

  private void building(Strategy strategy) {
    JsonNode target = bot.world.colony(targetColonyId);
    if (Json.isNull(target) || !isEnemy(text(target, "ownerId"))) {
      abort("Ziel " + targetName + " ist keine gegnerische Kolonie mehr");
      return;
    }
    // Nur Energie-Notlage und Angriff auf die Heimat unterbrechen den Aufbau; eine
    // Hungersnot ist im Prototyp eine Preisfrage (Economy.adjustPrices) und kein Grund,
    // die Kriegsvorbereitung ruhen zu lassen.
    if (strategy == Strategy.EMERGENCY_POWER || strategy == Strategy.UNDER_ATTACK || strategy == Strategy.RECOVER) {
      blockedReason = "Aufbau ruht (" + strategy + ")";
      return;
    }
    // 1. Transporter als Landungsflotte – ALLE fertigen Transporter aus dem Lager
    //    kommen in dieselbe Flotte, bis ihre Truppenkapazität den Bedarf deckt.
    if (landingFleetId == null || bot.world.ownFleet(landingFleetId) == null) {
      landingFleetId = null;
      for (JsonNode f : bot.world.ownFleets()) if (World.hasShip(f, Catalog.TROOP_TRANSPORT)) landingFleetId = text(f, "id");
    }
    double inStock = Math.floor(bot.world.stock(bot.homeColonyId, Catalog.TROOP_TRANSPORT));
    if (inStock >= 1) {
      // Neue Schiffe nehmen NUR Flotten auf, die gerade bei dieser Kolonie liegen
      // (FleetCommands.transferShipsToFleet). Liegt die Landungsflotte woanders,
      // wird sie zuerst herangeholt; klappt auch das nicht, entsteht eine neue
      // Flotte, statt den ganzen Takt an einer Ablehnung scheitern zu lassen.
      dockAtHome(landingFleetId);
      JsonNode landing = landingFleetId == null ? null : bot.world.ownFleet(landingFleetId);
      boolean landingAtHome = landing != null && World.stationed(landing)
          && Json.eq(text(landing, "locationColonyId"), bot.homeColonyId);
      Map<String, Object> payload = new HashMap<>();
      payload.put("colonyId", bot.homeColonyId);
      payload.put("shipProductTypeId", Catalog.TROOP_TRANSPORT);
      payload.put("quantity", inStock);
      payload.put("targetFleetId", landingAtHome ? landingFleetId : null);
      try {
        bot.call("transferShipsToFleet", payload);
        bot.world.invalidate("fleets", "warehouse", "fleetTroopCapacity");
        if (!landingAtHome) {
          for (JsonNode f : bot.world.ownFleets()) if (World.hasShip(f, Catalog.TROOP_TRANSPORT)) landingFleetId = text(f, "id");
        }
        bot.monitor.event("TRANSPORT_READY", (long) inStock + " Mannschaftstransporter in Dienst gestellt (Flotte " + landingFleetId + ")",
            "fleetId", landingFleetId, "qty", inStock);
      } catch (CommandException e) {
        bot.monitor.log("Transporter einreihen abgelehnt: " + e.getMessage());
      }
    }
    if (landingFleetId == null) {
      blockedReason = "kein Mannschaftstransporter (" + transportEta() + ")";
      return;
    }
    collectFreighters();
    double landingCapacity = Json.dbl(bot.world.troopCapacity(landingFleetId), "capacitySoldiers");
    if (landingCapacity < neededSoldiers) {
      blockedReason = "Transportraum " + (long) landingCapacity + "/" + neededSoldiers + " Soldaten (" + transportEta() + ")";
      return;
    }
    // 2. Truppen – bereits eingeschiffte Soldaten und bereits verladene Drohnen zählen mit
    //    (nach einem Neustart des Bots stehen sie sonst als fehlend da und würden doppelt rekrutiert).
    JsonNode garrison = bot.world.garrison(bot.homeColonyId);
    int aboard = (int) Json.dbl(bot.world.troopCapacity(landingFleetId), "soldiersAboard");
    int soldiers = World.unitCount(garrison, Catalog.SOLDIER) + aboard;
    JsonNode freighterFleet = bot.trade.freighterFleetId() == null ? null : bot.world.ownFleet(bot.trade.freighterFleetId());
    int drones = World.unitCount(garrison, droneType) + (freighterFleet == null ? 0 : (int) World.cargoQty(freighterFleet, droneType));
    if (soldiers < neededSoldiers + HOME_SOLDIER_RESERVE || drones < neededDrones) {
      blockedReason = "Truppen: " + soldiers + "/" + (neededSoldiers + HOME_SOLDIER_RESERVE) + " Soldaten, " + drones + "/" + neededDrones + " " + droneType;
      return;
    }
    // 3. Frachter für die Drohnen
    if (!bot.trade.reserve()) {
      blockedReason = "Frachter unterwegs (" + bot.trade.state() + ")";
      return;
    }
    blockedReason = "";
    phase = InvasionPhase.LOADING;
    phaseSince = System.currentTimeMillis();
  }

  /** Fertige Frachter aus dem Lager in die Frachtflotte stellen – sie tragen die Drohnen der Landung. */
  private void collectFreighters() {
    String freighterId = bot.trade.freighterFleetId();
    double inStock = Math.floor(bot.world.stock(bot.homeColonyId, Catalog.FREIGHTER));
    if (inStock < 1) return;
    dockAtHome(freighterId);
    JsonNode freighter = freighterId == null ? null : bot.world.ownFleet(freighterId);
    boolean atHome = freighter != null && World.stationed(freighter)
        && Json.eq(text(freighter, "locationColonyId"), bot.homeColonyId);
    if (!atHome) return;
    try {
      bot.call("transferShipsToFleet", Map.of("colonyId", bot.homeColonyId, "shipProductTypeId", Catalog.FREIGHTER,
          "quantity", inStock, "targetFleetId", freighterId));
      bot.world.invalidate("fleets", "warehouse", "fleetCargoCapacity");
      bot.monitor.event("FREIGHTER_READY", (long) inStock + " Frachter in die Frachtflotte gestellt (Drohnentransport)",
          "fleetId", freighterId, "qty", inStock);
    } catch (CommandException e) {
      bot.monitor.log("Frachter einreihen abgelehnt: " + e.getMessage());
    }
  }

  /**
   * Holt eine Flotte an die Heimatkolonie, sofern sie im Heimatsystem steht und
   * nicht unterwegs ist. Ohne das schlägt jeder Befehl fehl, der die Flotte
   * "bei dieser Kolonie" verlangt (Schiffe übernehmen, Soldaten einschiffen).
   */
  private void dockAtHome(String fleetId) {
    if (fleetId == null) return;
    JsonNode fleet = bot.world.ownFleet(fleetId);
    if (fleet == null || !World.stationed(fleet)) return;
    if (!Json.eq(text(fleet, "systemId"), bot.homeSystemId)) return;
    if (Json.eq(text(fleet, "locationColonyId"), bot.homeColonyId)) return;
    try {
      bot.call("moveFleetWithinSystem", Map.of("fleetId", fleetId,
          "target", Map.of("kind", "ColonyOrbit", "colonyId", bot.homeColonyId)));
      bot.world.invalidate("fleets", "fleetTroopCapacity");
    } catch (CommandException e) {
      bot.monitor.log("Andocken der Flotte " + fleetId + " abgelehnt: " + e.getMessage());
    }
  }

  private String transportEta() {
    for (JsonNode q : bot.world.shipyardQueue(bot.homeColonyId)) {
      if (Json.eq(text(q, "shipProductTypeId"), Catalog.TROOP_TRANSPORT)) {
        JsonNode ends = q.path("endsAt");
        if (!Json.isNull(ends)) {
          // endsAt ist Spielzeit des Servers – gegen seine Spieluhr rechnen, nicht gegen die eigene Wanduhr.
          long remainingMs = ends.asLong() - bot.gameNow();
          return "Werft: noch " + Economy.fmtHours(remainingMs / GameSpeed.REAL_MS_PER_GAME_HOUR);
        }
        return "Werft: " + text(q, "status");
      }
    }
    return "nicht bestellt";
  }

  private void loading() {
    JsonNode landing = bot.world.ownFleet(landingFleetId);
    String freighterId = bot.trade.freighterFleetId();
    JsonNode freighter = freighterId == null ? null : bot.world.ownFleet(freighterId);
    if (landing == null || freighter == null) {
      abort("Landungs- oder Frachtflotte verschwunden");
      return;
    }
    dockAtHome(landingFleetId);
    landing = bot.world.ownFleet(landingFleetId);
    if (landing == null || !World.stationed(landing) || !Json.eq(text(landing, "locationColonyId"), bot.homeColonyId)) {
      // Erst andocken, dann einschiffen – ein Einschiffungsbefehl an eine Flotte,
      // die nicht bei der Kolonie liegt, wird abgelehnt und kostet nur einen Takt.
      blockedReason = "Landungsflotte legt an der Heimatkolonie an";
      return;
    }
    JsonNode capacityView = bot.world.troopCapacity(landingFleetId);
    double aboard = Json.dbl(capacityView, "soldiersAboard");
    double free = Json.dbl(capacityView, "capacitySoldiers") - aboard;
    if (aboard < neededSoldiers) {
      // Nie mehr anfordern, als an Bord passt UND in der Garnison steht – sonst
      // lehnt der Server jeden Takt ab und die Operation kommt nie in Fahrt.
      int inGarrison = World.unitCount(bot.world.garrison(bot.homeColonyId), Catalog.SOLDIER);
      int wanted = (int) Math.min(Math.min(neededSoldiers - aboard, free), inGarrison);
      if (wanted > 0) {
        bot.call("embarkSoldiers", Map.of("fleetId", landingFleetId, "quantity", (double) wanted));
        bot.world.invalidate("fleetTroopCapacity", "groundForces");
        aboard += wanted;
      }
      if (aboard < neededSoldiers) {
        // Zurück in den Aufbau. WICHTIG: Nur dort schreibt Military.prepare den
        // Soldatenbedarf in den Plan – bliebe der Bot in der Verladung stehen,
        // hörte das Ausbildungszentrum auf zu rekrutieren und die Operation
        // wartete ewig auf Soldaten, die niemand mehr ausbildet.
        blockedReason = "Soldaten an Bord " + (long) aboard + "/" + neededSoldiers + " (Garnison " + inGarrison + ")";
        phase = InvasionPhase.BUILDING;
        return;
      }
    }
    if (!World.stationed(freighter) || !Json.eq(text(freighter, "locationColonyId"), bot.homeColonyId)) {
      blockedReason = "Frachter noch nicht an der Heimatkolonie";
      return;
    }
    double loaded = World.cargoQty(freighter, droneType);
    if (loaded < neededDrones) {
      // Nur einlagern und verladen, was die Garnison HAT und was in den Frachter
      // passt – sonst lehnt der Server jeden Takt ab ("Die Garnison hat nur N
      // davon"), die Soldaten stehen an Bord und die Operation kommt nie los.
      int inGarrison = World.unitCount(bot.world.garrison(bot.homeColonyId), droneType);
      double missing = Math.min(neededDrones - loaded, inGarrison);
      if (missing >= 1) {
        bot.call("storeDrones", Map.of("colonyId", bot.homeColonyId, "unitProductTypeId", droneType, "quantity", missing));
        bot.call("loadCargo", Map.of("fleetId", freighterId, "productTypeId", droneType, "quantity", missing));
        bot.world.invalidate("fleets", "warehouse", "groundForces", "fleetCargoCapacity");
        loaded += missing;
      }
      if (loaded < neededDrones) {
        // Zurück in den Aufbau: die Wirtschaft bekommt den Drohnenbedarf über
        // den Plan erneut vorgelegt (Military.prepare).
        blockedReason = "Drohnen an Bord " + (long) loaded + "/" + neededDrones + " " + droneType
            + " (Garnison " + inGarrison + ")";
        phase = InvasionPhase.BUILDING;
        return;
      }
    }
    int hops = bot.world.hops(bot.homeSystemId, targetSystemId);
    bot.trade.topUpFuel(landingFleetId, hops);
    bot.trade.topUpFuel(freighterId, hops);
    if (!moveFleet(landingFleetId, targetSystemId)) {
      blockedReason = "Landungsflotte kann nicht ablegen (Treibstoff?) – neuer Versuch im nächsten Takt";
      return;
    }
    moveFleet(freighterId, targetSystemId);
    boolean escort = false;
    if (space == SpaceState.IDLE && combatFleet() != null && World.stationed(combatFleet())) {
      bot.trade.topUpFuel(combatFleetId, hops);
      moveFleet(combatFleetId, targetSystemId);
      spaceTargetSystemId = targetSystemId;
      startShipCount = World.shipCount(combatFleet());
      space = SpaceState.TRAVELING;
      escort = true;
    }
    phase = InvasionPhase.TRAVELING;
    phaseSince = System.currentTimeMillis();
    bot.monitor.event("INVASION_LAUNCHED", String.format("Landungsoperation gegen %s: %d Soldaten, %d %s, Eskorte=%s, %d Sprünge",
            targetName, neededSoldiers, neededDrones, droneType, escort, bot.world.hops(bot.homeSystemId, targetSystemId)),
        "target", targetColonyId, "system", targetSystemId, "soldiers", neededSoldiers, "drones", neededDrones, "droneType", droneType, "escort", escort);
  }

  private void traveling() {
    JsonNode landing = bot.world.ownFleet(landingFleetId);
    JsonNode freighter = bot.world.ownFleet(bot.trade.freighterFleetId());
    if (landing == null || freighter == null) {
      abort("Transportflotte unterwegs verloren");
      return;
    }
    // Eine Flotte, die noch zu Hause steht (Ablegen war abgelehnt), erneut losschicken.
    for (String fleetId : List.of(landingFleetId, bot.trade.freighterFleetId())) {
      JsonNode f = bot.world.ownFleet(fleetId);
      if (f != null && World.stationed(f) && Json.eq(text(f, "systemId"), bot.homeSystemId) && !targetSystemId.equals(bot.homeSystemId)) {
        bot.trade.topUpFuel(fleetId, bot.world.hops(bot.homeSystemId, targetSystemId));
        moveFleet(fleetId, targetSystemId);
      }
    }
    boolean there = World.stationed(landing) && Json.eq(text(landing, "systemId"), targetSystemId)
        && World.stationed(freighter) && Json.eq(text(freighter, "systemId"), targetSystemId);
    if (!there) return;
    phase = InvasionPhase.ORBIT;
    phaseSince = System.currentTimeMillis();
    bot.monitor.log("Transportflotten im Zielsystem " + bot.world.systemName(targetSystemId) + " angekommen.");
  }

  private void orbit() {
    // Eskorte kämpft noch? Dann warten – die Landung selbst kennt keine Blockadesperre (Befund, siehe Konzept 31).
    if (space == SpaceState.ENGAGING) return;
    JsonNode target = bot.world.colony(targetColonyId);
    if (Json.isNull(target) || !isEnemy(text(target, "ownerId"))) {
      abort("Ziel " + targetName + " ist keine gegnerische Kolonie mehr");
      return;
    }
    String freighterId = bot.trade.freighterFleetId();
    for (String fleetId : List.of(landingFleetId, freighterId)) {
      JsonNode f = bot.world.ownFleet(fleetId);
      if (f == null) continue;
      if (!Json.eq(text(f, "locationPlanetId"), targetPlanetId) || !"PlanetOrbit".equals(text(f, "locationType"))) {
        bot.call("moveFleetWithinSystem", Map.of("fleetId", fleetId, "target", Map.of("kind", "PlanetOrbit", "planetId", targetPlanetId)));
      }
    }
    bot.world.invalidate("fleets");
    int soldiersBefore = (int) Json.dbl(bot.world.troopCapacity(landingFleetId), "soldiersAboard");
    JsonNode freighter = bot.world.ownFleet(freighterId);
    double dronesBefore = freighter == null ? 0 : World.cargoQty(freighter, droneType);
    for (String fleetId : List.of(landingFleetId, freighterId)) {
      try {
        bot.call("land", Map.of("fleetId", fleetId, "targetPlanetId", targetPlanetId));
      } catch (CommandException e) {
        bot.monitor.log("Landung mit Flotte " + fleetId + " abgelehnt: " + e.getMessage());
      }
    }
    bot.world.invalidate("landedGroundForces", "fleets", "fleetTroopCapacity");
    JsonNode group = landedGroup();
    landings++;
    int soldiers = World.unitCount(group, Catalog.SOLDIER);
    int drones = World.droneCount(group);
    bot.monitor.event("LANDED", String.format("Gelandet auf %s: %d Soldaten, %d Drohnen (eingeschifft: %d / %.0f – Differenz = Landungsabwehr)",
            targetName, soldiers, drones, soldiersBefore, dronesBefore),
        "target", targetColonyId, "soldiers", soldiers, "drones", drones, "soldiersEmbarked", soldiersBefore, "dronesEmbarked", dronesBefore);
    phase = InvasionPhase.LANDED;
    phaseSince = System.currentTimeMillis();
  }

  private JsonNode landedGroup() {
    for (JsonNode g : bot.world.landedGroundForces()) if (Json.eq(text(g, "planetId"), targetPlanetId)) return g;
    return null;
  }

  private void landed() {
    JsonNode group = landedGroup();
    if (group == null) {
      bot.monitor.event("LANDING_WIPED", "Kein Verband auf " + targetName + " – Landung vollständig abgewehrt", "target", targetColonyId);
      groundDefeats++;
      wave++;
      beginReturn();
      return;
    }
    groupId = text(group, "id");
    List<JsonNode> attackable = bot.world.attackableColoniesForGroup(groupId);
    String chosen = null;
    for (JsonNode c : attackable) if (Json.eq(text(c, "id"), targetColonyId)) chosen = targetColonyId;
    if (chosen == null && !attackable.isEmpty()) chosen = text(attackable.get(0), "id");
    if (chosen == null) {
      abort("Auf " + targetName + " gibt es keine angreifbare Kolonie mehr");
      return;
    }
    try {
      JsonNode battle = bot.call("engageGroundBattle", Map.of("groupId", groupId, "targetColonyId", chosen));
      groundBattleId = text(battle, "id");
      targetColonyId = chosen;
      lastLoyalty = -1;
      stallTicks = 0;
      phase = InvasionPhase.FIGHTING;
      phaseSince = System.currentTimeMillis();
      bot.monitor.event("GROUND_BATTLE_STARTED", "Bodengefecht " + groundBattleId + " gegen " + targetName + " eröffnet (Phase " + text(battle, "phase") + ")",
          "battleId", groundBattleId, "target", chosen, "phase", text(battle, "phase"));
      bot.world.invalidate("activeGroundBattles");
    } catch (CommandException e) {
      bot.monitor.event("GROUND_BATTLE_REFUSED", "Bodenangriff abgelehnt: " + e.getMessage(), "target", chosen);
      wave++;
      beginReturn();
    }
  }

  private void fighting() {
    JsonNode battle = null;
    for (JsonNode b : bot.world.activeGroundBattles()) if (Json.eq(text(b, "id"), groundBattleId)) battle = b;
    if (battle == null) {
      JsonNode colony = bot.world.colony(targetColonyId);
      boolean conquered = !Json.isNull(colony) && Json.eq(text(colony, "ownerId"), bot.playerId);
      if (conquered) {
        conquests++;
        bot.monitor.event("CONQUERED", "Kolonie " + targetName + " erobert!", "colonyId", targetColonyId, "battleId", groundBattleId);
        bot.coordination.report("conquered", Map.of("colonyId", targetColonyId, "systemId", targetSystemId));
        bot.trade.requestDelivery(targetColonyId, Map.of(Catalog.ELERIUM, 25.0, Catalog.FOOD, 150.0, Catalog.MEDICINE, 40.0));
      } else {
        groundDefeats++;
        wave++;
        bot.monitor.event("GROUND_BATTLE_LOST", "Bodengefecht " + groundBattleId + " gegen " + targetName + " verloren/abgebrochen", "battleId", groundBattleId, "target", targetColonyId);
      }
      beginReturn();
      return;
    }
    JsonNode group = landedGroup();
    String phaseName = text(battle, "phase");
    int ticks = Json.integer(battle, "ticksResolved");
    double loyaltyAfter = -1;
    JsonNode ticksNode = battle.path("ticks");
    if (ticksNode.isArray() && ticksNode.size() > 0) loyaltyAfter = Json.dbl(ticksNode.get(ticksNode.size() - 1), "loyaltyPctAfter", -1);
    if ("Combat".equals(phaseName) && ticks > 0 && World.activeDroneValue(group) <= 0) {
      retreatGround("keine aktiven Drohnen mehr im Kampf");
      return;
    }
    if ("Siege".equals(phaseName) && ticks > 0) {
      if (lastLoyalty >= 0 && loyaltyAfter >= lastLoyalty - 0.01) stallTicks++; else stallTicks = 0;
      lastLoyalty = loyaltyAfter;
      if (stallTicks >= SIEGE_STALL_TICKS) {
        retreatGround("Belagerung steht – Loyalität sinkt nicht mehr (" + String.format("%.1f", loyaltyAfter) + " %)");
        return;
      }
    }
    if (ticks > 0 && ticks % 3 == 0) {
      bot.monitor.log("Bodengefecht " + groundBattleId + ": Phase " + phaseName + ", Tick " + ticks + ", Soldaten "
          + World.unitCount(group, Catalog.SOLDIER) + ", aktive Drohnen-Wert " + String.format("%.0f", World.activeDroneValue(group))
          + (loyaltyAfter >= 0 ? ", Loyalität " + String.format("%.1f", loyaltyAfter) + " %" : ""));
    }
  }

  private void retreatGround(String reason) {
    try {
      bot.call("retreatFromGroundBattle", Map.of("battleId", groundBattleId));
      bot.monitor.event("GROUND_RETREAT", "Rückzug aus Bodengefecht " + groundBattleId + ": " + reason, "battleId", groundBattleId, "reason", reason);
    } catch (CommandException e) {
      bot.monitor.log("Rückzug abgelehnt: " + e.getMessage());
    }
    groundDefeats++;
    wave++;
    bot.world.invalidate("activeGroundBattles", "landedGroundForces");
    beginReturn();
  }

  private void beginReturn() {
    // Ein noch stehender Verband auf fremdem Boden bleibt dort (kein Wiedereinschiffen im Protokoll) – er zählt als verloren.
    for (String fleetId : new String[]{landingFleetId, bot.trade.freighterFleetId()}) {
      JsonNode f = fleetId == null ? null : bot.world.ownFleet(fleetId);
      if (f != null && World.stationed(f) && !Json.eq(text(f, "systemId"), bot.homeSystemId)) moveFleet(fleetId, bot.homeSystemId);
    }
    if (space == SpaceState.TRAVELING && targetSystemId != null && targetSystemId.equals(spaceTargetSystemId)) {
      JsonNode cf = combatFleet();
      if (cf != null && World.stationed(cf)) {
        moveFleet(combatFleetId, bot.homeSystemId);
        space = SpaceState.RETURNING;
      }
    }
    phase = InvasionPhase.RETURNING;
    phaseSince = System.currentTimeMillis();
  }

  private void returning() {
    JsonNode landing = landingFleetId == null ? null : bot.world.ownFleet(landingFleetId);
    JsonNode freighter = bot.trade.freighterFleetId() == null ? null : bot.world.ownFleet(bot.trade.freighterFleetId());
    boolean landingHome = landing == null || (World.stationed(landing) && Json.eq(text(landing, "systemId"), bot.homeSystemId));
    boolean freighterHome = freighter == null || (World.stationed(freighter) && Json.eq(text(freighter, "systemId"), bot.homeSystemId));
    if (!landingHome || !freighterHome) return;
    bot.trade.release();
    bot.monitor.event("INVASION_ENDED", "Landungsoperation gegen " + targetName + " abgeschlossen, Flotten zu Hause", "target", targetColonyId);
    phase = InvasionPhase.NONE;
    groupId = null;
    groundBattleId = null;
    phaseSince = System.currentTimeMillis();
  }

  private void abort(String reason) {
    bot.monitor.event("INVASION_ABORTED", reason, "target", targetColonyId, "phase", phase.name());
    if (phase == InvasionPhase.BUILDING) {
      phase = InvasionPhase.NONE;
      bot.trade.release();
    } else {
      beginReturn();
    }
  }

  // --- Hilfen -------------------------------------------------------------------------------

  private boolean isEnemy(String ownerId) {
    JsonNode p = bot.world.player(ownerId);
    return p != null && bot.isEnemyCampName(text(p, "name"));
  }

  private boolean moveFleet(String fleetId, String systemId) {
    try {
      bot.call("moveFleet", Map.of("fleetId", fleetId, "destinationSystemId", systemId));
      bot.world.invalidate("fleets");
      return true;
    } catch (CommandException e) {
      bot.monitor.log("Flottenbewegung " + fleetId + " -> " + bot.world.systemName(systemId) + " abgelehnt: " + e.getMessage());
      return false;
    }
  }

  List<String> fleetsAway() {
    List<String> away = new ArrayList<>();
    for (JsonNode f : bot.world.ownFleets()) {
      if (!Json.eq(text(f, "systemId"), bot.homeSystemId)) away.add(text(f, "name") + "@" + bot.world.systemName(text(f, "systemId")));
    }
    return away;
  }
}
