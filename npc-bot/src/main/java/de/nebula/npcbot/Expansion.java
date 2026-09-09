package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.nebula.npcbot.Json.text;

/**
 * Besiedlung des eigenen Heimatsystems (Rolle SETTLER), entlang
 * Umsetzungskonzept/24: Kolonisationsschiff verlangt Loyalität ≥ 90 %,
 * mindestens 4000 Einwohner (2000 wandern aus) und 16 000 Cr Prämie; feste
 * Bauwoche; danach Flug in den Orbit des Zielplaneten, Gründung, ein
 * Spieltag Landung. Die neue Kolonie startet ohne Vorräte – der Frachter
 * bringt Elerium, Nahrung und Medizin nach ({@code Trade.requestDelivery}).
 *
 * <p>Zielplanet: der größte nutzbare Planet des Heimatsystems, auf dem noch
 * keine eigene Kolonie steht; per {@code CLAIM} beim Koordinator angemeldet,
 * damit kein Lagerkollege denselben Planeten anstrebt.</p>
 */
final class Expansion {
  enum Phase {NONE, WAITING, SHIP_QUEUED, SHIP_READY, COLONIZING}

  private static final Map<String, Integer> SIZE_RANK = Map.of("Riesig", 4, "Groß", 3, "Mittel", 2, "Klein", 1);

  private final Bot bot;
  private Phase phase = Phase.NONE;
  private String targetPlanetId;
  private String targetPlanetName = "?";
  private String colonyFleetId;
  private String blockedReason = "";
  private int founded;
  private long shipOrderedAt;

  Expansion(Bot bot) {
    this.bot = bot;
  }

  boolean active() {
    return phase == Phase.SHIP_QUEUED || phase == Phase.SHIP_READY || phase == Phase.COLONIZING;
  }

  String phase() {
    return phase.name();
  }

  String blockedReason() {
    return blockedReason;
  }

  int founded() {
    return founded;
  }

  String targetPlanetId() {
    return targetPlanetId;
  }

  /** Der Koordinator hat den Planeten einem Lagerkollegen zugesprochen – einen anderen wählen. */
  void deny(String planetId) {
    if (planetId != null && planetId.equals(targetPlanetId) && !active()) {
      denied.add(planetId);
      targetPlanetId = null;
    }
  }

  private final java.util.Set<String> denied = new java.util.HashSet<>();

  void tick(Strategy.Plan plan, Strategy.Situation s, Strategy.Assignment a, Strategy strategy) {
    try {
      run(plan, s, a, strategy);
    } catch (CommandException e) {
      bot.monitor.log("Expansion: Befehl abgelehnt: " + e.getMessage());
    }
  }

  private void run(Strategy.Plan plan, Strategy.Situation s, Strategy.Assignment a, Strategy strategy) {
    if (a.role != Strategy.MilitaryRole.SETTLER && !active()) {
      phase = Phase.NONE;
      return;
    }
    switch (phase) {
      case NONE, WAITING -> waiting(s, strategy);
      case SHIP_QUEUED -> shipQueued();
      case SHIP_READY -> shipReady();
      case COLONIZING -> colonizing();
    }
  }

  private void waiting(Strategy.Situation s, Strategy strategy) {
    if (targetPlanetId == null) pickTarget();
    if (targetPlanetId == null) {
      blockedReason = "kein freier nutzbarer Planet im Heimatsystem";
      return;
    }
    if (strategy == Strategy.EMERGENCY_POWER || strategy == Strategy.UNDER_ATTACK || strategy == Strategy.RECOVER) {
      blockedReason = "wartet (" + strategy + ")";
      phase = Phase.WAITING;
      return;
    }
    StringBuilder why = new StringBuilder();
    if (s.shipyardLevel < 1) why.append("keine Werft; ");
    if (s.homeLoyalty < Catalog.COLONY_SHIP_MIN_LOYALTY_PCT) why.append(String.format("Loyalität %.0f%% < 90%%; ", s.homeLoyalty));
    if (s.homePopulation < 2 * Catalog.START_POPULATION + 50) why.append(String.format("Bevölkerung %.0f < 4050; ", s.homePopulation));
    if (s.wallet < Catalog.COLONIST_PREMIUM + 500) why.append(String.format("Credits %.0f < 16500; ", s.wallet));
    if (why.length() > 0) {
      blockedReason = why.toString();
      phase = Phase.WAITING;
      return;
    }
    if (!bot.world.shipyardQueue(bot.homeColonyId).isEmpty()) {
      blockedReason = "Werft belegt";
      return;
    }
    // Der Werftauftrag holt die 1,27 Mio. Rohstoffeinheiten selbst mit Industrietempo
    // (Konzept 31 §I); nur Energie- und Versorgungs-Wache gelten.
    Economy.Health home = bot.economy.home();
    if (home != null && !bot.economy.energyGuard(home, Map.of(Catalog.COLONY_SHIP, 1.0), "Kolonisationsschiff")) {
      blockedReason = "wartet auf Energie-/Versorgungsreserve für das Kolonisationsschiff";
      return;
    }
    JsonNode preview = bot.world.previewChain(bot.homeColonyId, Catalog.COLONY_SHIP, 1);
    bot.call("queueShip", Map.of("colonyId", bot.homeColonyId, "shipProductTypeId", Catalog.COLONY_SHIP, "quantity", 1.0,
        "autoProduceMissing", true, "requeueOnComplete", false));
    bot.world.invalidate("shipyardQueue", "wallet", "population");
    shipOrderedAt = System.currentTimeMillis();
    phase = Phase.SHIP_QUEUED;
    blockedReason = "";
    bot.monitor.event("COLONY_SHIP_ORDERED", "Kolonisationsschiff bestellt für " + targetPlanetName + " (Kettenvorschau "
        + Economy.fmtHours(Json.dbl(preview, "totalHours")) + " zzgl. feste Bauwoche)", "planet", targetPlanetId, "etaGameHours", Json.dbl(preview, "totalHours"));
  }

  private void pickTarget() {
    String best = null;
    int bestRank = -1;
    for (JsonNode p : bot.world.planetsInSystem(bot.homeSystemId)) {
      if (!Json.bool(p, "usable")) continue;
      String pid = text(p, "id");
      if (denied.contains(pid)) continue;
      boolean own = false;
      for (JsonNode c : bot.world.ownColonies()) if (Json.eq(text(c, "planetId"), pid)) own = true;
      for (JsonNode c : bot.world.colonizations()) if (Json.eq(text(c, "planetId"), pid)) own = true;
      if (own) continue;
      int rank = SIZE_RANK.getOrDefault(text(p, "size"), 0);
      if (rank > bestRank) {
        bestRank = rank;
        best = pid;
        targetPlanetName = text(p, "name");
      }
    }
    targetPlanetId = best;
    if (best != null) bot.coordination.claim("planet", best);
  }

  private void shipQueued() {
    if (bot.world.stock(bot.homeColonyId, Catalog.COLONY_SHIP) >= 1) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("colonyId", bot.homeColonyId);
      payload.put("shipProductTypeId", Catalog.COLONY_SHIP);
      payload.put("quantity", 1.0);
      payload.put("targetFleetId", null);
      bot.call("transferShipsToFleet", payload);
      bot.world.invalidate("fleets", "warehouse");
      for (JsonNode f : bot.world.ownFleets()) if (World.hasShip(f, Catalog.COLONY_SHIP)) colonyFleetId = text(f, "id");
      phase = Phase.SHIP_READY;
      bot.monitor.event("COLONY_SHIP_READY", "Kolonisationsschiff fertig nach " + Economy.fmtHours((System.currentTimeMillis() - shipOrderedAt) / GameSpeed.REAL_MS_PER_GAME_HOUR)
          + " (Flotte " + colonyFleetId + ")", "fleetId", colonyFleetId);
      return;
    }
    boolean queued = false;
    for (JsonNode q : bot.world.shipyardQueue(bot.homeColonyId)) if (Json.eq(text(q, "shipProductTypeId"), Catalog.COLONY_SHIP)) queued = true;
    if (!queued) {
      bot.monitor.event("COLONY_SHIP_LOST", "Kolonisationsschiff weder in Werft noch im Lager – Auftrag verworfen?", "planet", targetPlanetId);
      phase = Phase.WAITING;
    }
  }

  private void shipReady() {
    JsonNode fleet = bot.world.ownFleet(colonyFleetId);
    if (fleet == null) {
      phase = Phase.WAITING;
      return;
    }
    if (!Json.eq(text(fleet, "locationPlanetId"), targetPlanetId)) {
      bot.call("moveFleetWithinSystem", Map.of("fleetId", colonyFleetId, "target", Map.of("kind", "PlanetOrbit", "planetId", targetPlanetId)));
      bot.world.invalidate("fleets");
    }
    JsonNode colonization = bot.call("colonizePlanet", Map.of("planetId", targetPlanetId));
    phase = Phase.COLONIZING;
    bot.world.invalidate("colonizations", "fleets");
    bot.monitor.event("COLONIZATION_STARTED", "Gründung auf " + targetPlanetName + " läuft (" + text(colonization, "colonyName") + ")",
        "planet", targetPlanetId, "colonizationId", text(colonization, "id"));
  }

  private void colonizing() {
    for (JsonNode c : bot.world.colonizations()) if (Json.eq(text(c, "planetId"), targetPlanetId)) return;
    JsonNode colony = null;
    for (JsonNode c : bot.world.ownColonies()) if (Json.eq(text(c, "planetId"), targetPlanetId)) colony = c;
    if (colony == null) {
      bot.monitor.event("COLONIZATION_FAILED", "Gründung auf " + targetPlanetName + " ohne Kolonie beendet", "planet", targetPlanetId);
      phase = Phase.WAITING;
      targetPlanetId = null;
      return;
    }
    founded++;
    bot.monitor.event("COLONY_FOUNDED", "Neue Kolonie " + text(colony, "name") + " auf " + targetPlanetName + " gegründet", "colonyId", text(colony, "id"), "planet", targetPlanetId);
    bot.coordination.report("founded", Map.of("colonyId", text(colony, "id"), "planetId", targetPlanetId));
    Map<String, Double> supplies = new LinkedHashMap<>();
    supplies.put(Catalog.ELERIUM, 25.0);
    supplies.put(Catalog.FOOD, 150.0);
    supplies.put(Catalog.MEDICINE, 40.0);
    bot.trade.requestDelivery(text(colony, "id"), supplies);
    phase = Phase.WAITING;
    targetPlanetId = null;
    colonyFleetId = null;
  }

  static List<String> phases() {
    return List.of(Phase.NONE.name(), Phase.WAITING.name(), Phase.SHIP_QUEUED.name(), Phase.SHIP_READY.name(), Phase.COLONIZING.name());
  }
}
