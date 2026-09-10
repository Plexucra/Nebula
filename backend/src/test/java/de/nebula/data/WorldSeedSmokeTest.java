package de.nebula.data;

import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.Planet;
import de.nebula.model.StarSystem;
import de.nebula.state.IdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kein Dauertest, sondern eine einmalige Verifikation, dass {@link WorldSeed#createWorldSeed}
 * und {@link WorldSeed#createAdditionalPlayerSeed} durchlaufen und plausible Ergebnisse liefern
 * (Umsetzungskonzept/13_...md, Phase 3). Wird nach erfolgreicher Verifikation ggf. wieder entfernt.
 */
class WorldSeedSmokeTest {

  @Test
  void createWorldSeedProducesPlausibleGalaxy() {
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);

    assertEquals(200, seed.systems.size());

    StarSystem homeSystem = seed.systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    assertEquals(seed.player.homeSystemId, homeSystem.id);
    // 5 benannte, immer besiedelbare Heimatplaneten + 4-6 zusätzliche unbesiedelbare Himmelskörper
    // (Nutzervorgabe: jedes System hat auch unbesiedelbare Körper, siehe WorldSeed.UNUSABLE_BODIES_MIN/MAX).
    assertTrue(homeSystem.planetIds.size() >= 9 && homeSystem.planetIds.size() <= 11);
    List<Planet> homePlanets = seed.planets.stream().filter(p -> p.systemId.equals(homeSystem.id)).toList();
    assertEquals(5, homePlanets.stream().filter(p -> p.usable).count(), "genau 5 besiedelbare Heimatplaneten");
    assertTrue(homePlanets.stream().filter(p -> !p.usable).allMatch(p -> p.resourceConcentration.isEmpty()),
        "unbesiedelbare Himmelskörper zeigen keine Rohstoffkonzentration");

    // Jedes System der Galaxie hat Himmelskörper – auch Handelsgilde-Stationen und
    // gewöhnliche, noch unbesuchte Systeme (früherer Fehler: nur das Heimatsystem
    // hatte Planeten).
    assertTrue(seed.systems.stream().allMatch(s -> !s.planetIds.isEmpty()), "jedes System sollte mindestens einen Planeten haben");
    assertTrue(seed.planets.size() > 5, "es sollten auch außerhalb des Heimatsystems Planeten existieren");
    long systemsWithPlanetsInList = seed.planets.stream().map(p -> p.systemId).distinct().count();
    assertEquals(200, systemsWithPlanetsInList, "jedes System sollte in seed.planets vertreten sein");

    Colony home = seed.colonies.stream().filter(c -> c.id.equals(seed.player.homeworldColonyId)).findFirst().orElseThrow();
    assertTrue(home.isHomeworld);

    // Start (Nutzerentscheidung): Wohnkomplex 1 + Industriekomplex 5 + Infrastruktur 6.
    List<Building> homeBuildings = seed.buildings.stream().filter(b -> b.colonyId.equals(home.id)).toList();
    assertEquals(3, homeBuildings.size());
    assertEquals(1, homeBuildings.stream().filter(b -> b.typeId.equals("b_habitat")).findFirst().orElseThrow().level);
    assertEquals(5, homeBuildings.stream().filter(b -> b.typeId.equals("b_industry")).findFirst().orElseThrow().level);
    assertEquals(6, homeBuildings.stream().filter(b -> b.typeId.equals("b_infrastructure")).findFirst().orElseThrow().level);
    assertTrue(homeBuildings.stream().noneMatch(b -> b.typeId.equals("b_shipyard")), "keine Werft im Start");
    assertTrue(seed.warehouse.stream().noneMatch(w -> w.productTypeId.equals("p_stahl")), "keine Baustoffe im Startlager");

    // Startvorrat an Eleriumkapseln für Gateway-Sprünge: bemessen auf die erste
    // Kolonisationsfahrt (Umsetzungskonzept/34_...md, F7 – Verbrauch nach Masse).
    double starterCapsules = seed.warehouse.stream()
        .filter(w -> w.colonyId.equals(home.id) && w.productTypeId.equals("p_elerium_kapsel"))
        .findFirst().orElseThrow().quantity;
    assertTrue(starterCapsules >= 3 * de.nebula.data.ShipCatalog.find(de.nebula.engine.GameConstants.COLONY_SHIP_PRODUCT_ID).jumpFuelPerHop,
        "Der Startvorrat muss drei Sprünge eines Kolonisationsschiffs tragen, war: " + starterCapsules);

    // Heimatplanet-Rohstoffprofil (Nutzervorgabe): Nahrungsrohstoffe + Elerium liegen immer
    // zwischen 50 und 60 %, alle übrigen (bis auf eine Zufalls-Ausnahme) unter 10 %.
    Planet homeworldPlanet = homePlanets.stream().filter(p -> p.orbitIndex == 0).findFirst().orElseThrow();
    List<String> foodAndElerium = List.of("res_eis", "res_atmosphaere", "res_salz", "res_kohlenstoff", "res_elerium");
    for (var c : homeworldPlanet.resourceConcentration) {
      if (foodAndElerium.contains(c.resourceTypeId)) {
        assertTrue(c.concentration >= 50 && c.concentration <= 60,
            c.resourceTypeId + " sollte zwischen 50 und 60 % liegen, war " + c.concentration);
      }
    }
    long goodOutsideFoodAndElerium = homeworldPlanet.resourceConcentration.stream()
        .filter(c -> !foodAndElerium.contains(c.resourceTypeId) && c.concentration >= 50).count();
    assertEquals(1, goodOutsideFoodAndElerium, "genau eine Zufalls-Ausnahme außerhalb Nahrung/Elerium sollte gut sein");

    long connectedGraphCheck = seed.gateways.stream().mapToLong(g -> g.reachableSystemIds.size()).filter(n -> n == 0).count();
    assertEquals(0, connectedGraphCheck, "jedes Gateway sollte mindestens einen Nachbarn haben (zusammenhängender Graph)");

    long tradeHubCount = seed.systems.stream().filter(s -> s.isTradeHub).count();
    assertTrue(tradeHubCount >= 1 && tradeHubCount <= 8);

    // Zwei Daueraufträge für Grundnahrung (Umsetzungskonzept/20_...md, schnellere erste
    // Charge) + Stabilisiertes Elerium.
    assertEquals(3, seed.productionQueue.size());
    assertEquals(2, seed.productionQueue.stream().filter(q -> q.productTypeId.equals("p_grundnahrung")).count());

    // Start-Verkaufsorder für Grundnahrung: ohne sie hätte die Bevölkerung nichts zu
    // kaufen (der Tageseinkauf kauft NUR aus Orders am Handelsposten), siehe Umsetzungskonzept/15_...md,
    // Auftrag 1. Seit Umsetzungskonzept/20_...md bootstrappt das Spiel nur noch dieses
    // eine Grundkonsumgut, Grundmedizin baut der Spieler selbst auf.
    assertEquals(1, seed.sellOrders.size());
    assertTrue(seed.sellOrders.stream().allMatch(o -> o.autoRelist && o.remainingQuantity > 0 && o.limitPrice > 0 && o.planetId != null),
        "Start-Verkaufsorders müssen wiederkehrend, bestückt und bepreist sein");
    assertTrue(seed.sellOrders.stream().anyMatch(o -> o.productTypeId.equals("p_grundnahrung")),
        "Es muss eine Verkaufsorder für Grundnahrung geben");
    assertEquals(1, seed.groundForceGroups.size());
    assertEquals(2, seed.fleets.size()); // Spieler: Frachter+Kampfflotte

    // --- zweiter Kommandant in derselben Galaxie -----------------------------
    WorldSeed.AdditionalSeed additional = WorldSeed.createAdditionalPlayerSeed(seed.systems, "Zweitkommandant", "Zweitheim", ids);
    assertTrue(additional.newSystem.isHomeSystem);
    assertEquals(1, additional.newGateway.reachableSystemIds.size());
    assertEquals(additional.linkedSystemId, additional.newGateway.reachableSystemIds.get(0));
    assertEquals(6, additional.buildings.stream().filter(b -> b.typeId.equals("b_infrastructure")).findFirst().orElseThrow().level);
  }
}
