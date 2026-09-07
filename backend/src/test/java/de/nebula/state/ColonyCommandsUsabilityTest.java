package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.model.Colony;
import de.nebula.model.Planet;
import de.nebula.model.StarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifiziert, dass unbesiedelbare Himmelskörper (Nutzervorgabe, siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §7.4) tatsächlich nicht
 * kolonisiert werden können, während gewöhnliche besiedelbare Planeten weiterhin gehen.
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

  @Test
  void colonizingAnUnusablePlanetIsRejected() {
    Bootstrapped b = newBootstrappedState();
    StarSystem home = b.state().systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    Planet unusable = b.state().planets.stream()
        .filter(p -> p.systemId.equals(home.id) && !p.usable)
        .findFirst().orElseThrow();

    CommandException ex = assertThrows(CommandException.class,
        () -> ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), unusable.id));
    assertTrue(ex.getMessage().contains("nicht besiedelbar"), ex.getMessage());
    assertTrue(b.state().colonies.stream().noneMatch(c -> c.planetId.equals(unusable.id)));
  }

  @Test
  void colonizingAUsablePlanetStillWorks() {
    Bootstrapped b = newBootstrappedState();
    StarSystem home = b.state().systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
    Planet usable = b.state().planets.stream()
        .filter(p -> p.systemId.equals(home.id) && p.usable && p.orbitIndex == 1)
        .findFirst().orElseThrow();

    Colony colony = ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), usable.id);
    assertEquals(usable.id, colony.planetId);
    assertTrue(b.state().colonies.contains(colony));
  }
}
