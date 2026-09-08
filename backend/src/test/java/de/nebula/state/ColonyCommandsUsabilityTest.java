package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colonization;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.Planet;
import de.nebula.model.Population;
import de.nebula.model.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifiziert zum einen, dass unbesiedelbare Himmelskörper (Nutzervorgabe, siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §7.4) nicht kolonisiert
 * werden können, zum anderen den Kolonisationsschiff-Ablauf aus
 * Umsetzungskonzept/24_...md: ohne Schiff im Orbit geht gar nichts, mit Schiff
 * entsteht die Kolonie erst nach einem Spieltag.
 */
class ColonyCommandsUsabilityTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId) {
  }

  private static Bootstrapped newBootstrappedState() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    return new Bootstrapped(state, ids, state.players.get(0).id);
  }

  /** Eigene, im Orbit des Planeten stationierte Flotte mit einem Kolonisationsschiff. */
  private static Fleet colonyShipInOrbit(Bootstrapped b, Planet planet) {
    Fleet fleet = new Fleet();
    fleet.id = b.ids().next("flt");
    fleet.ownerId = b.playerId();
    fleet.name = "Kolonisationsflotte";
    fleet.locationType = FleetLocationType.PlanetOrbit;
    fleet.locationColonyId = null;
    fleet.locationPlanetId = planet.id;
    fleet.systemId = planet.systemId;
    fleet.status = FleetStatus.Stationed;
    fleet.ships = new ArrayList<>(List.of(new FleetShipGroup(GameConstants.COLONY_SHIP_PRODUCT_ID, 1)));
    fleet.cargo = new ArrayList<>();
    fleet.pendingHops = List.of();
    b.state().fleets.add(fleet);
    return fleet;
  }

  private static Planet usableNeighbour(Bootstrapped b) {
    StarSystem home = b.state().systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    return b.state().planets.stream()
        .filter(p -> p.systemId.equals(home.id) && p.usable && p.orbitIndex == 1)
        .findFirst().orElseThrow();
  }

  @Test
  void colonizingAnUnusablePlanetIsRejected() {
    Bootstrapped b = newBootstrappedState();
    StarSystem home = b.state().systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    Planet unusable = b.state().planets.stream()
        .filter(p -> p.systemId.equals(home.id) && !p.usable)
        .findFirst().orElseThrow();
    colonyShipInOrbit(b, unusable);

    CommandException ex = assertThrows(CommandException.class,
        () -> ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), unusable.id));
    assertTrue(ex.getMessage().contains("nicht besiedelbar"), ex.getMessage());
    assertTrue(b.state().colonies.stream().noneMatch(c -> c.planetId.equals(unusable.id)));
  }

  @Test
  void colonizingWithoutAColonyShipInOrbitIsRejected() {
    Bootstrapped b = newBootstrappedState();
    Planet usable = usableNeighbour(b);

    CommandException ex = assertThrows(CommandException.class,
        () -> ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), usable.id));
    assertTrue(ex.getMessage().contains("Kolonisationsschiff"), ex.getMessage());
    assertTrue(b.state().colonizations.isEmpty());
    assertTrue(b.state().colonies.stream().noneMatch(c -> c.planetId.equals(usable.id)));
  }

  @Test
  void colonyShipStartsAColonizationAndIsConsumed() {
    Bootstrapped b = newBootstrappedState();
    Planet usable = usableNeighbour(b);
    Fleet fleet = colonyShipInOrbit(b, usable);

    Colonization running = ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), usable.id);
    assertEquals(usable.id, running.planetId);
    assertEquals(0, fleet.ships.get(0).quantity, "Das Kolonisationsschiff wird bei der Landung verbraucht");
    assertTrue(b.state().colonies.stream().noneMatch(c -> c.planetId.equals(usable.id)),
        "Vor Ablauf des Spieltags darf es noch KEINE Kolonie geben");
    assertEquals(Clock.hoursToMs(GameConstants.COLONIZATION_HOURS), running.endsAt - running.startedAt, 1);
  }

  @Test
  void colonyAppearsWithStartPopulationOnceColonizationIsDue() {
    Bootstrapped b = newBootstrappedState();
    Planet usable = usableNeighbour(b);
    colonyShipInOrbit(b, usable);
    Colonization running = ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), usable.id);

    ColonyCommands.processColonizations(b.state(), b.ids(), running.endsAt);

    assertTrue(b.state().colonizations.isEmpty(), "Der Vorgang muss nach dem Abschluss verschwinden");
    Colony colony = b.state().colonies.stream().filter(c -> c.planetId.equals(usable.id)).findFirst().orElse(null);
    assertNotNull(colony, "Nach Ablauf des Spieltags muss die Kolonie stehen");
    Population population = b.state().populations.stream()
        .filter(p -> p.colonyId.equals(colony.id)).findFirst().orElseThrow();
    assertEquals(GameConstants.START_POPULATION, population.currentCount, 0.001,
        "Die Kolonisten des Schiffs sind die Startbevölkerung");
  }

  @Test
  void secondColonizationOfTheSamePlanetIsRejectedWhileOneIsRunning() {
    Bootstrapped b = newBootstrappedState();
    Planet usable = usableNeighbour(b);
    colonyShipInOrbit(b, usable);
    ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), usable.id);
    colonyShipInOrbit(b, usable);

    CommandException ex = assertThrows(CommandException.class,
        () -> ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), usable.id));
    assertTrue(ex.getMessage().contains("läuft bereits"), ex.getMessage());
  }
}
