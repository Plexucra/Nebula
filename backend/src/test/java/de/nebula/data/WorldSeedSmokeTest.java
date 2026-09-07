package de.nebula.data;

import de.nebula.model.Building;
import de.nebula.model.Colony;
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
    assertEquals(5, seed.planets.size());

    StarSystem homeSystem = seed.systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    assertEquals(seed.player.homeSystemId, homeSystem.id);
    assertEquals(5, homeSystem.planetIds.size());

    Colony home = seed.colonies.stream().filter(c -> c.id.equals(seed.player.homeworldColonyId)).findFirst().orElseThrow();
    assertTrue(home.isHomeworld);

    // Minimalstart (Umsetzungskonzept/17_...md): Wohnkomplex 1 + Industriekomplex 1 + Infrastruktur 2.
    List<Building> homeBuildings = seed.buildings.stream().filter(b -> b.colonyId.equals(home.id)).toList();
    assertEquals(3, homeBuildings.size());
    assertEquals(1, homeBuildings.stream().filter(b -> b.typeId.equals("b_habitat")).findFirst().orElseThrow().level);
    assertEquals(1, homeBuildings.stream().filter(b -> b.typeId.equals("b_industry")).findFirst().orElseThrow().level);
    assertEquals(2, homeBuildings.stream().filter(b -> b.typeId.equals("b_infrastructure")).findFirst().orElseThrow().level);
    assertTrue(homeBuildings.stream().noneMatch(b -> b.typeId.equals("b_shipyard")), "keine Werft im Start");
    assertTrue(seed.warehouse.stream().noneMatch(w -> w.productTypeId.equals("p_stahl")), "keine Baustoffe im Startlager");

    long connectedGraphCheck = seed.gateways.stream().mapToLong(g -> g.reachableSystemIds.size()).filter(n -> n == 0).count();
    assertEquals(0, connectedGraphCheck, "jedes Gateway sollte mindestens einen Nachbarn haben (zusammenhängender Graph)");

    long tradeHubCount = seed.systems.stream().filter(s -> s.isTradeHub).count();
    assertTrue(tradeHubCount >= 1 && tradeHubCount <= 8);

    // Dauerauftrag je Grundkonsumgut (Grundnahrung, Grundmedizin) + Stabilisiertes Elerium
    assertEquals(3, seed.productionQueue.size());

    // Start-Verkaufsorders für die Grundkonsumgüter: ohne sie hätte die Bevölkerung
    // nichts zu kaufen (runConsumption kauft NUR aus sellOrders), siehe
    // Umsetzungskonzept/15_...md, Auftrag 1.
    assertEquals(2, seed.sellOrders.size());
    assertTrue(seed.sellOrders.stream().allMatch(o -> o.autoRelist && o.remainingQuantity > 0 && o.pricePerUnit > 0),
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
    assertEquals(2, additional.buildings.stream().filter(b -> b.typeId.equals("b_infrastructure")).findFirst().orElseThrow().level);
  }
}
