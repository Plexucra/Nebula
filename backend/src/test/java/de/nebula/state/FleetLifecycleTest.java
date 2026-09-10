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

  /**
   * Umsetzungskonzept/34_...md, §J 7: Eine Orbit-Blockade sperrt den Orbit –
   * passieren darf nur, wer einen Friedens- oder Handelsvertrag hat. Wer sich
   * den Weg freikämpfen will, wird nicht abgewiesen, sondern steht anschließend
   * im Gefecht mit der blockierenden Flotte.
   */
  @Test
  void eineBlockadeSperrtDenOrbitUndZwingtDenAnfliegerInsGefecht() {
    Arena a = newArena();
    Fleet blockader = combatFleet(a.state(), a.defenderId());
    Fleet ankommend = combatFleet(a.state(), a.attackerId());
    BlockadeCommands.formBlockade(a.state(), a.ids(), a.defenderId(), blockader.id,
        new BlockadeAnchor.PlanetOrbit(a.defenderHome().planetId));

    // Der Anflieger steht im Systemraum desselben Systems – der bleibt frei.
    ankommend.systemId = a.defenderHome().systemId;
    ankommend.locationType = FleetLocationType.System;
    ankommend.locationColonyId = null;
    ankommend.locationPlanetId = null;

    // 1. Ohne Vertrag UND ohne Krieg bleibt der Orbit zu – mit einer Meldung, die sagt, was fehlt.
    var target = new de.nebula.model.FleetSystemTarget.PlanetOrbit(a.defenderHome().planetId);
    CommandException zu = org.junit.jupiter.api.Assertions.assertThrows(CommandException.class,
        () -> FleetCommands.moveFleetWithinSystem(a.state(), a.ids(), a.attackerId(), ankommend.id, target));
    assertTrue(zu.getMessage().contains("blockiert") && zu.getMessage().contains("Krieg"), zu.getMessage());
    assertEquals(FleetLocationType.System, ankommend.locationType, "Ohne Krieg bleibt die Flotte, wo sie war");

    // 2. Mit Handelsvertrag passiert dieselbe Flotte ungehindert.
    TreatyCommands.offerTreaty(a.state(), a.ids(), a.attackerId(), a.defenderId(), de.nebula.model.TreatyType.Trade);
    var angebot = a.state().treatyOffers.get(0);
    TreatyCommands.respondToTreatyOffer(a.state(), a.ids(), a.defenderId(), angebot.id, true);
    FleetCommands.moveFleetWithinSystem(a.state(), a.ids(), a.attackerId(), ankommend.id, target);
    assertEquals(FleetLocationType.PlanetOrbit, ankommend.locationType, "Mit Vertrag ist der Orbit offen");
    assertTrue(a.state().battles.isEmpty(), "Ein Vertragspartner fliegt ein, ohne zu kämpfen");

    // 3. Im Krieg führt derselbe Einflug ins Gefecht – die Flotte wird NICHT gestoppt.
    FleetCommands.moveFleetWithinSystem(a.state(), a.ids(), a.attackerId(), ankommend.id,
        new de.nebula.model.FleetSystemTarget.System());
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    FleetCommands.moveFleetWithinSystem(a.state(), a.ids(), a.attackerId(), ankommend.id, target);
    assertEquals(FleetLocationType.PlanetOrbit, ankommend.locationType, "Die Flotte fliegt ein …");
    assertEquals(1, a.state().battles.size(), "… und steht dort im Gefecht mit der Blockade");
    assertEquals(blockader.id, a.state().battles.get(0).defenderFleetId);
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
      GameEvents.fireNow(a.state(), a.ids(), GameEventType.BATTLE_ROUND, battle.id);
    }
    assertEquals(BattleStatus.Ended, battle.status);

    assertFalse(a.state().fleets.stream().anyMatch(f -> f.id.equals(defender.id)), "keine Geisterflotte mit null Schiffen");
    assertTrue(a.state().blockades.isEmpty(), "die Blockade der vernichteten Flotte ist weg");
    assertTrue(a.state().fleets.stream().anyMatch(f -> f.id.equals(attacker.id)), "der Sieger bleibt");
  }
}
