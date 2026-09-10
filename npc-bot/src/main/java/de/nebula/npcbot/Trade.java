package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.nebula.npcbot.Json.text;

/**
 * Der eine Startfrachter je Bot in drei Verwendungen, nach Priorität:
 * <ol>
 *   <li><b>Versorgungsfahrt</b> zu einer eigenen jungen Kolonie (frisch
 *       gegründet oder erobert): Elerium, Nahrung, Medizin aus dem Heimatlager.
 *       Ohne sie fiele eine neue Kolonie ab dem ersten Tick in den Blackout
 *       (Umsetzungskonzept/24 §F, TODO.md).</li>
 *   <li><b>Militärische Reservierung</b>: Military leiht den Frachter als
 *       Drohnentransporter für eine Landung (Umsetzungskonzept/28 §A – Drohnen
 *       sind Fracht, nur Soldaten fahren im Mannschaftstransporter).</li>
 *   <li><b>Handelsfahrt</b> zur nächsten Handelsgilde-Station: Spezialware
 *       oberhalb der Bevölkerungsreserve verkaufen, Einkaufsliste der
 *       Wirtschaft abarbeiten (Elerium zuerst), Kreditreserve für Löhne und
 *       Unterhalt nie antasten.</li>
 * </ol>
 */
final class Trade {
  enum State {IDLE, TO_HUB, TO_HOME, DELIVER_OUT, DELIVER_BACK}

  private static final double MIN_EXPORT_BATCH = 10;
  private static final double MAX_EXPORT_PER_TRIP = 30;
  private static final double CREDIT_RESERVE_BASE = 800;
  private static final double IMPORT_BATCH = 20;
  private static final double TRIP_BUDGET_SHARE = 0.15;
  /** Sicherheitsaufschlag beim Betanken: Hin- und Rückweg plus Umwege. */
  private static final double FUEL_TRIP_MARGIN = 2.5;
  private final Map<String, Double> referenceAsk = new LinkedHashMap<>();

  private final Bot bot;
  private String freighterFleetId;
  private String hubSystemId;
  private State state = State.IDLE;
  private boolean reserved;
  private final Map<String, Map<String, Double>> pendingDeliveries = new LinkedHashMap<>();
  private String deliveryColonyId;
  private int trips;
  private int deliveries;
  private double revenue;
  private double spent;

  Trade(Bot bot) {
    this.bot = bot;
  }

  State state() {
    return state;
  }

  int trips() {
    return trips;
  }

  int deliveries() {
    return deliveries;
  }

  double revenue() {
    return revenue;
  }

  double spent() {
    return spent;
  }

  String freighterFleetId() {
    if (freighterFleetId == null) {
      for (JsonNode f : bot.world.ownFleets()) {
        if (World.hasShip(f, Catalog.FREIGHTER) && !World.hasWarships(f)) {
          freighterFleetId = text(f, "id");
          break;
        }
      }
    }
    return freighterFleetId;
  }

  /** Military bittet um den Frachter – gewährt nur, wenn er untätig zu Hause liegt. */
  boolean reserve() {
    if (reserved) return true;
    if (state != State.IDLE || !freighterAtHome()) return false;
    reserved = true;
    bot.monitor.log("Frachter für die Landungsoperation reserviert – Handel ruht.");
    return true;
  }

  void release() {
    if (reserved) bot.monitor.log("Frachter wieder für den Handel frei.");
    reserved = false;
    freighterFleetId = null;
  }

  void requestDelivery(String colonyId, Map<String, Double> goods) {
    pendingDeliveries.put(colonyId, new LinkedHashMap<>(goods));
    bot.monitor.event("DELIVERY_REQUESTED", "Versorgungsfahrt geplant für Kolonie " + colonyId + ": " + goods, "colonyId", colonyId);
  }

  boolean freighterAtHome() {
    JsonNode f = bot.world.ownFleet(freighterFleetId());
    return World.stationed(f) && Json.eq(text(f, "locationColonyId"), bot.homeColonyId);
  }

  void tick(Strategy.Plan plan, String specialty, Map<String, Double> shopping) {
    if (reserved) return;
    String fleetId = freighterFleetId();
    if (fleetId == null) return;
    JsonNode fleet = bot.world.ownFleet(fleetId);
    if (fleet == null) {
      freighterFleetId = null;
      state = State.IDLE;
      return;
    }
    switch (state) {
      case IDLE -> startTrip(fleet, plan, specialty, shopping);
      case TO_HUB -> atHub(fleet, plan, specialty, shopping);
      case TO_HOME -> atHome(fleet);
      case DELIVER_OUT -> atDeliveryTarget(fleet);
      case DELIVER_BACK -> atHome(fleet);
    }
  }

  // --- Abfahrt ----------------------------------------------------------------------

  private void startTrip(JsonNode fleet, Strategy.Plan plan, String specialty, Map<String, Double> shopping) {
    if (!World.stationed(fleet)) return;
    if (!Json.eq(text(fleet, "locationColonyId"), bot.homeColonyId)) {
      if (Json.eq(text(fleet, "systemId"), bot.homeSystemId)) dockHome(fleet);
      else moveHome(fleet);
      return;
    }
    if (!pendingDeliveries.isEmpty() && startDelivery(fleet)) return;

    if (hubSystemId == null) hubSystemId = bot.world.nearestHub(bot.homeSystemId);
    if (hubSystemId == null || hubSystemId.equals(bot.homeSystemId)) return;

    double loadQty = 0;
    if (plan.exportAllowed && specialty != null) {
      // Doppelte Bevölkerungsreserve bleibt zu Hause (die Bevölkerung wächst, der
      // Startvorrat ist ihr Puffer). Je Fahrt nur eine kleine Menge: die Handelsgilde
      // nimmt je Besuch ein 5er-Los und senkt danach ihr Gebot um 10 % – mehr findet
      // nur Abnehmer, wenn ein anderer Kommandant gerade importiert.
      double reserve = Catalog.CONSUMER_NEED_PER_CAPITA_PER_HOUR.containsKey(specialty)
          ? Economy.reserveQty(bot.world.population(bot.homeColonyId), specialty) * 2 : 5;
      double exportable = bot.world.stock(bot.homeColonyId, specialty) - reserve;
      loadQty = Math.floor(Math.min(Math.min(exportable, MAX_EXPORT_PER_TRIP), bot.world.maxLoadable(text(fleet, "id"), specialty)));
    }
    boolean urgentImport = shopping.containsKey(Catalog.ELERIUM) || shopping.containsKey(Catalog.FOOD);
    if (loadQty < MIN_EXPORT_BATCH && !(urgentImport && bot.world.wallet() > creditReserve() + 50)) return;
    try {
      if (loadQty >= 1) bot.call("loadCargo", Map.of("fleetId", text(fleet, "id"), "productTypeId", specialty, "quantity", loadQty));
      topUpFuel(text(fleet, "id"), bot.world.hops(bot.homeSystemId, hubSystemId));
      bot.call("moveFleet", Map.of("fleetId", text(fleet, "id"), "destinationSystemId", hubSystemId));
      state = State.TO_HUB;
      bot.monitor.log("Handelsfahrt: " + (long) loadQty + "x " + specialty + " -> " + bot.world.systemName(hubSystemId)
          + (urgentImport ? " (Einkauf: " + shopping.keySet() + ")" : ""));
      bot.world.invalidate("fleets");
    } catch (CommandException e) {
      bot.monitor.log("Handelsfahrt abgelehnt: " + e.getMessage());
    }
  }

  private boolean startDelivery(JsonNode fleet) {
    String colonyId = pendingDeliveries.keySet().iterator().next();
    JsonNode colony = bot.world.colony(colonyId);
    if (Json.isNull(colony) || !Json.eq(text(colony, "ownerId"), bot.playerId)) {
      pendingDeliveries.remove(colonyId);
      return false;
    }
    Map<String, Double> goods = pendingDeliveries.get(colonyId);
    int loaded = 0;
    for (Map.Entry<String, Double> e : goods.entrySet()) {
      double keep = e.getKey().equals(Catalog.ELERIUM) ? 8 : Economy.reserveQty(bot.world.population(bot.homeColonyId), e.getKey());
      double available = Math.floor(bot.world.stock(bot.homeColonyId, e.getKey()) - keep);
      double qty = Math.min(available, e.getValue());
      if (qty < 1) continue;
      try {
        bot.call("loadCargo", Map.of("fleetId", text(fleet, "id"), "productTypeId", e.getKey(), "quantity", qty));
        loaded++;
      } catch (CommandException ex) {
        bot.monitor.log("Versorgungsfracht " + e.getKey() + " abgelehnt: " + ex.getMessage());
      }
    }
    if (loaded == 0) return false;
    deliveryColonyId = colonyId;
    pendingDeliveries.remove(colonyId);
    String targetSystem = text(colony, "systemId");
    topUpFuel(text(fleet, "id"), bot.world.hops(bot.homeSystemId, targetSystem));
    try {
      if (!targetSystem.equals(bot.homeSystemId)) {
        bot.call("moveFleet", Map.of("fleetId", text(fleet, "id"), "destinationSystemId", targetSystem));
      }
      state = State.DELIVER_OUT;
      bot.monitor.event("DELIVERY_STARTED", "Versorgungsfahrt zu " + text(colony, "name") + " gestartet", "colonyId", colonyId);
      bot.world.invalidate("fleets");
      return true;
    } catch (CommandException e) {
      bot.monitor.log("Versorgungsfahrt abgelehnt: " + e.getMessage());
      return false;
    }
  }

  // --- Unterwegs / Ankunft ---------------------------------------------------------------

  private void atHub(JsonNode fleet, Strategy.Plan plan, String specialty, Map<String, Double> shopping) {
    if (!World.stationed(fleet) || !hubSystemId.equals(text(fleet, "systemId"))) return;
    String fleetId = text(fleet, "id");
    if (specialty != null) sell(fleetId, specialty);
    buy(fleetId, shopping);
    moveHome(fleet);
    state = State.TO_HOME;
    trips++;
  }

  private void atDeliveryTarget(JsonNode fleet) {
    if (!World.stationed(fleet)) return;
    JsonNode colony = bot.world.colony(deliveryColonyId);
    if (Json.isNull(colony) || !Json.eq(text(colony, "systemId"), text(fleet, "systemId"))) return;
    String fleetId = text(fleet, "id");
    try {
      if (!Json.eq(text(fleet, "locationColonyId"), deliveryColonyId)) {
        bot.call("moveFleetWithinSystem", Map.of("fleetId", fleetId, "target", Map.of("kind", "ColonyOrbit", "colonyId", deliveryColonyId)));
        bot.world.invalidate("fleets");
        fleet = bot.world.ownFleet(fleetId);
      }
      List<String> unloaded = new ArrayList<>();
      for (JsonNode c : fleet.path("cargo")) {
        double qty = Json.dbl(c, "quantity");
        if (qty < 1e-9) continue;
        bot.call("unloadCargo", Map.of("fleetId", fleetId, "productTypeId", text(c, "productTypeId"), "quantity", qty));
        unloaded.add((long) qty + "x " + text(c, "productTypeId"));
      }
      deliveries++;
      bot.monitor.event("DELIVERY_DONE", "Versorgung bei " + text(colony, "name") + " entladen: " + unloaded, "colonyId", deliveryColonyId);
      if (!Json.eq(text(fleet, "systemId"), bot.homeSystemId)) moveHome(fleet);
      state = State.DELIVER_BACK;
      bot.world.invalidate("fleets", "warehouse");
    } catch (CommandException e) {
      bot.monitor.log("Versorgung entladen fehlgeschlagen: " + e.getMessage());
    }
  }

  private void atHome(JsonNode fleet) {
    if (!World.stationed(fleet) || !bot.homeSystemId.equals(text(fleet, "systemId"))) return;
    String fleetId = text(fleet, "id");
    if (!Json.eq(text(fleet, "locationColonyId"), bot.homeColonyId)) {
      if (!dockHome(fleet)) return;
      fleet = bot.world.ownFleet(fleetId);
    }
    for (JsonNode c : fleet.path("cargo")) {
      double qty = Json.dbl(c, "quantity");
      if (qty < 1e-9) continue;
      try {
        bot.call("unloadCargo", Map.of("fleetId", fleetId, "productTypeId", text(c, "productTypeId"), "quantity", qty));
      } catch (CommandException e) {
        bot.monitor.log("Entladen zu Hause fehlgeschlagen: " + e.getMessage());
      }
    }
    bot.world.invalidate("fleets", "warehouse");
    state = State.IDLE;
  }

  private boolean dockHome(JsonNode fleet) {
    try {
      bot.call("moveFleetWithinSystem", Map.of("fleetId", text(fleet, "id"), "target", Map.of("kind", "ColonyOrbit", "colonyId", bot.homeColonyId)));
      bot.world.invalidate("fleets");
      return true;
    } catch (CommandException e) {
      bot.monitor.log("Andocken zu Hause fehlgeschlagen: " + e.getMessage());
      return false;
    }
  }

  private void moveHome(JsonNode fleet) {
    try {
      bot.call("moveFleet", Map.of("fleetId", text(fleet, "id"), "destinationSystemId", bot.homeSystemId));
      bot.world.invalidate("fleets");
    } catch (CommandException e) {
      bot.monitor.log("Rückreise fehlgeschlagen: " + e.getMessage());
    }
  }

  // --- Börse ----------------------------------------------------------------------------

  private void sell(String fleetId, String product) {
    JsonNode fleet = bot.world.ownFleet(fleetId);
    double onboard = fleet == null ? 0 : World.cargoQty(fleet, product);
    if (onboard >= 1) {
      try {
        bot.call("unloadCargoToHubDepot", Map.of("fleetId", fleetId, "productTypeId", product, "quantity", onboard));
      } catch (CommandException e) {
        bot.monitor.log("Entladen ins Stationsdepot fehlgeschlagen: " + e.getMessage());
      }
    }
    bot.world.invalidate("hubDepot", "fleets");
    double depotQty = 0;
    for (JsonNode e : bot.world.hubDepot(hubSystemId)) if (Json.eq(text(e, "productTypeId"), product)) depotQty = Math.floor(Json.dbl(e, "quantity"));
    if (depotQty < 1) return;
    double bid = bestPrice(product, "Buy");
    if (bid <= 0) return;
    double before = bot.world.wallet();
    try {
      bot.call("createHubSellOrder", Map.of("systemId", hubSystemId, "productTypeId", product, "quantity", depotQty, "pricePerUnit", bid));
      bot.world.invalidate("wallet", "hubOrders");
      double gained = bot.world.wallet() - before;
      revenue += Math.max(0, gained);
      bot.monitor.log("Verkaufsorder: " + (long) depotQty + "x " + product + " @ " + bid + " (sofort erlöst: " + String.format("%.0f", gained) + " Cr)");
    } catch (CommandException e) {
      bot.monitor.log("Verkaufsorder abgelehnt: " + e.getMessage());
    }
  }

  private void buy(String fleetId, Map<String, Double> shopping) {
    List<String> order = new ArrayList<>();
    if (shopping.containsKey(Catalog.ELERIUM)) order.add(Catalog.ELERIUM);
    for (String p : shopping.keySet()) if (!order.contains(p)) order.add(p);
    double tripBudget = Math.min(bot.world.wallet() - creditReserve(), bot.world.wallet() * TRIP_BUDGET_SHARE);
    for (String product : order) {
      cancelOwnOrders(product, "Buy");
      double budget = Math.min(tripBudget, bot.world.wallet() - creditReserve());
      if (budget < 5) break;
      double ask = bestPrice(product, "Sell");
      if (ask <= 0) continue;
      // Die Handelsgilde hebt ihren Brief je 5er-Los um 10 % und senkt ihn nie wieder –
      // wer immer weiter kauft, zahlt bald das Zehnfache. Über dem Doppelten des ersten
      // gesehenen Preises wird nicht gekauft (Ausnahme: Elerium, der Blackout ist teurer).
      double reference = referenceAsk.computeIfAbsent(product, k -> ask);
      if (ask > reference * 2 && !product.equals(Catalog.ELERIUM)) continue;
      double quantity = Math.floor(Math.min(Math.min(budget / ask, IMPORT_BATCH), Math.max(1, shopping.get(product))));
      if (quantity < 1) continue;
      try {
        bot.call("createHubBuyOrder", Map.of("systemId", hubSystemId, "productTypeId", product, "quantity", quantity, "pricePerUnit", ask));
        bot.world.invalidate("wallet", "hubDepot", "hubOrders");
      } catch (CommandException e) {
        bot.monitor.log("Einkaufsorder " + product + " abgelehnt: " + e.getMessage());
        continue;
      }
      double loadable = Math.floor(bot.world.maxLoadable(fleetId, product));
      double inDepot = 0;
      for (JsonNode e : bot.world.hubDepot(hubSystemId)) if (Json.eq(text(e, "productTypeId"), product)) inDepot = Math.floor(Json.dbl(e, "quantity"));
      double take = Math.min(loadable, inDepot);
      if (take < 1) continue;
      try {
        bot.call("loadCargoFromHubDepot", Map.of("fleetId", fleetId, "productTypeId", product, "quantity", take));
        spent += take * ask;
        tripBudget -= take * ask;
        bot.monitor.log("Eingekauft: " + (long) take + "x " + product + " @ " + ask);
        bot.world.invalidate("fleets", "hubDepot");
      } catch (CommandException e) {
        bot.monitor.log("Verladung des Einkaufs fehlgeschlagen: " + e.getMessage());
      }
    }
  }

  private double creditReserve() {
    return CREDIT_RESERVE_BASE + bot.world.population(bot.homeColonyId) * 0.02 * 48;
  }

  private double bestPrice(String productTypeId, String side) {
    double best = -1;
    for (JsonNode o : bot.world.hubOrders(hubSystemId)) {
      if (!Json.eq(text(o, "productTypeId"), productTypeId) || !side.equals(text(o, "side"))) continue;
      if (Json.dbl(o, "remainingQuantity") <= 0 || Json.eq(text(o, "ownerId"), bot.playerId)) continue;
      double price = Json.dbl(o, "limitPrice");
      if ("Buy".equals(side) ? price > best : (best < 0 || price < best)) best = price;
    }
    return best;
  }

  private void cancelOwnOrders(String productTypeId, String side) {
    for (JsonNode o : bot.world.hubOrders(hubSystemId)) {
      if (!Json.eq(text(o, "productTypeId"), productTypeId) || !side.equals(text(o, "side")) || !Json.eq(text(o, "ownerId"), bot.playerId)) continue;
      try {
        bot.call("cancelHubOrder", Map.of("orderId", text(o, "id")));
      } catch (CommandException ignored) {
        // zwischenzeitlich ausgeführt
      }
    }
    bot.world.invalidate("hubOrders", "wallet");
  }

  /**
   * Betankt an der Heimatkolonie mit dem, was da ist – eine feste Menge würde
   * bei knappem Kapselbestand komplett abgelehnt, und eine frisch aus der Werft
   * gestellte Flotte hat einen leeren Tank (so blieb im Testlauf ein voll
   * beladener Mannschaftstransporter zu Hause stehen, während Eskorte und
   * Drohnenfrachter allein ins Zielsystem flogen).
   */
  void topUpFuel(String fleetId) {
    topUpFuel(fleetId, Catalog.DEFAULT_TRIP_HOPS);
  }

  /**
   * Betankt für eine Fahrt über {@code hops} Sprünge. Ein voller Tank reicht für
   * {@code jumpFuelTankRangeHops} (50) Sprünge – der Verbrauch je Sprung ist
   * also {@code Fassungsvermögen / 50}. Vorher wurde immer auf VOLL getankt und
   * erst unter der Hälfte nachgelegt: Eine Kampfflotte mit zwei Kreuzern fasst
   * 12 600 Kapseln, der Bot hielt aber nur zehn im Lager vor – die Flotte kam
   * nie über den ersten Sprung hinaus ("Nicht genug Treibstoff im Tank
   * (benötigt 1610, im Tank 56)" im Testlauf).
   */
  void topUpFuel(String fleetId, int hops) {
    JsonNode fleet = bot.world.ownFleet(fleetId);
    if (fleet == null) return;
    double capacity = bot.world.fleetTankCapacity(fleet);
    double tank = Json.dbl(fleet, "fuelCapsules");
    if (capacity <= 0) return;
    double needed = Math.min(capacity, Math.ceil(capacity / Catalog.FUEL_TANK_RANGE_HOPS * Math.max(1, hops) * FUEL_TRIP_MARGIN));
    if (tank >= needed) return;
    double stock = Math.floor(bot.world.stock(bot.homeColonyId, Catalog.JUMP_FUEL));
    double qty = Math.min(Math.ceil(needed - tank), stock);
    if (qty < 1) return;
    try {
      bot.call("refuelFleet", Map.of("fleetId", fleetId, "quantity", qty));
      bot.world.invalidate("fleets", "warehouse");
    } catch (CommandException e) {
      // Tank voll – unkritisch
    }
  }
}
