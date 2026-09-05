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

    List<Building> homeBuildings = seed.buildings.stream().filter(b -> b.colonyId.equals(home.id)).toList();
    Building industry = homeBuildings.stream().filter(b -> b.typeId.equals("b_industry")).findFirst().orElseThrow();
    Building shipyard = homeBuildings.stream().filter(b -> b.typeId.equals("b_shipyard")).findFirst().orElseThrow();
    assertEquals(4, industry.level);
    assertEquals(3, shipyard.level);

    long connectedGraphCheck = seed.gateways.stream().mapToLong(g -> g.reachableSystemIds.size()).filter(n -> n == 0).count();
    assertEquals(0, connectedGraphCheck, "jedes Gateway sollte mindestens einen Nachbarn haben (zusammenhängender Graph)");

    long tradeHubCount = seed.systems.stream().filter(s -> s.isTradeHub).count();
    assertTrue(tradeHubCount >= 1 && tradeHubCount <= 8);

    assertEquals(2, seed.productionQueue.size()); // 1x Grundnahrung + 1x Stabilisiertes Elerium
    assertEquals(1, seed.groundForceGroups.size());
    assertEquals(2, seed.fleets.size()); // Spieler: Frachter+Kampfflotte

    // --- zweiter Kommandant in derselben Galaxie -----------------------------
    WorldSeed.AdditionalSeed additional = WorldSeed.createAdditionalPlayerSeed(seed.systems, "Zweitkommandant", "Zweitheim", ids);
    assertTrue(additional.newSystem.isHomeSystem);
    assertEquals(1, additional.newGateway.reachableSystemIds.size());
    assertEquals(additional.linkedSystemId, additional.newGateway.reachableSystemIds.get(0));
    List<Building> additionalBuildings = additional.buildings;
    Building additionalIndustry = additionalBuildings.stream().filter(b -> b.typeId.equals("b_industry")).findFirst().orElseThrow();
    assertEquals(4, additionalIndustry.level);
  }
}
