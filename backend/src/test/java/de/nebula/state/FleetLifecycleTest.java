package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.model.Battle;
import de.nebula.model.BattleStatus;
import de.nebula.model.BlockadeAnchor;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.Gateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zwei Lücken aus den Testläufen der Bot-Armee (Umsetzungskonzept/31_...md, §H):
 * eine per Gateway abreisende Flotte ließ ihre Blockade stehen, und eine im
 * Gefecht restlos vernichtete Flotte blieb als Geisterflotte mit null Schiffen
 * bestehen.
 */
class FleetLifecycleTest {

  private record Arena(GameState state, IdGenerator ids, String attackerId, String defenderId, Colony defenderHome) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Angreifer", "Angreiferheim", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, "Verteidiger", "Verteidigerheim", ids), ids);
    Colony defenderHome = ColonyCommands.colony(state, state.players.get(1).homeworldColonyId);
    return new Arena(state, ids, state.players.get(0).id, state.players.get(1).id, defenderHome);
  }

  private static Fleet combatFleet(GameState state, String playerId) {
    return FleetCommands.fleetsOf(state, playerId).stream()
        .filter(f -> f.ships.stream().anyMatch(s -> s.shipProductTypeId.equals("p_cruiser")))
        .findFirst().orElseThrow();
  }

  @Test
  void gatewayDepartureLiftsTheBlockade() {
    Arena a = newArena();
    Fleet fleet = combatFleet(a.state(), a.attackerId());
    Colony home = ColonyCommands.colony(a.state(), a.state().players.get(0).homeworldColonyId);
    BlockadeCommands.formBlockade(a.state(), a.ids(), a.attackerId(), fleet.id, new BlockadeAnchor.PlanetOrbit(home.planetId));
    assertEquals(1, a.state().blockades.size());

    Gateway gw = a.state().gateways.stream().filter(g -> g.systemId.equals(fleet.systemId)).findFirst().orElseThrow();
    FleetCommands.moveFleet(a.state(), a.attackerId(), fleet.id, gw.reachableSystemIds.get(0));

    assertEquals(FleetStatus.InTransit, fleet.status);
    assertTrue(a.state().blockades.isEmpty(), "die Blockade endet mit dem Absprung");
    // Und nach der Rückkehr lässt sich wieder blockieren – vorher scheiterte das an der eigenen, verwaisten Blockade.
    fleet.status = FleetStatus.Stationed;
    fleet.systemId = home.systemId;
    fleet.locationType = FleetLocationType.ColonyOrbit;
    fleet.locationColonyId = home.id;
    fleet.locationPlanetId = home.planetId;
    BlockadeCommands.formBlockade(a.state(), a.ids(), a.attackerId(), fleet.id, new BlockadeAnchor.PlanetOrbit(home.planetId));
    assertEquals(1, a.state().blockades.size());
  }

  @Test
  void annihilatedFleetDisappearsWithBlockadeAndTroops() {
    Arena a = newArena();
    Fleet attacker = combatFleet(a.state(), a.attackerId());
    Fleet defender = combatFleet(a.state(), a.defenderId());
    // Krasses Kräfteverhältnis, damit das Gefecht in wenigen Ticks entschieden ist.
    attacker.ships = List.of(new FleetShipGroup("p_cruiser", 30));
    defender.ships = List.of(new FleetShipGroup("p_corvette", 1));
    // Angreifer ins System des Verteidigers versetzen (Reisezeit ist hier nicht Gegenstand).
    attacker.systemId = a.defenderHome().systemId;
    attacker.locationType = FleetLocationType.System;
    attacker.locationColonyId = null;
    attacker.locationPlanetId = null;
    BlockadeCommands.formBlockade(a.state(), a.ids(), a.defenderId(), defender.id, new BlockadeAnchor.PlanetOrbit(a.defenderHome().planetId));
    // Eingeschiffte Truppen gehen mit dem Transporter unter.
    defender.ships = List.of(new FleetShipGroup("p_corvette", 1), new FleetShipGroup("p_trooptransport", 1));
    TroopTransportCommands.embarkSoldiers(a.state(), a.ids(), a.defenderId(), defender.id, 1);
    assertTrue(a.state().groundForceGroups.stream().anyMatch(g -> g.fleetId != null));

    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    BattleCommands.engageBattle(a.state(), a.ids(), a.attackerId(), attacker.id, defender.id);
    Battle battle = a.state().battles.get(0);
    for (int i = 0; i < 20 && battle.status == BattleStatus.Active; i++) {
      battle.nextTickAt = 0;
      BattleCommands.processBattles(a.state(), a.ids(), System.currentTimeMillis());
    }
    assertEquals(BattleStatus.Ended, battle.status);

    assertFalse(a.state().fleets.stream().anyMatch(f -> f.id.equals(defender.id)), "keine Geisterflotte mit null Schiffen");
    assertTrue(a.state().blockades.isEmpty(), "die Blockade der vernichteten Flotte ist weg");
    assertTrue(a.state().fleets.stream().anyMatch(f -> f.id.equals(attacker.id)), "der Sieger bleibt");
  }
}
