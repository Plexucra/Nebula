package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flotten zusammenlegen und aufteilen (Umsetzungskonzept/33_...md). Kern jeder
 * Prüfung ist dieselbe Regel: eine Flotte darf nie mehr tragen, als ihre
 * Schiffe fassen – beim Aufteilen auf BEIDEN Seiten.
 */
class FleetCompositionTest {

  private record Arena(GameState state, IdGenerator ids, String playerId, Colony home) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    Colony home = state.colonies.stream().filter(c -> c.isHomeworld).findFirst().orElseThrow();
    return new Arena(state, ids, state.players.get(0).id, home);
  }

  private static Fleet freighter(Arena a) {
    return FleetCommands.fleetsOf(a.state(), a.playerId()).stream()
        .filter(f -> f.ships.stream().anyMatch(s -> s.shipProductTypeId.equals("p_freighter")))
        .findFirst().orElseThrow();
  }

  private static Fleet combat(Arena a) {
    return FleetCommands.fleetsOf(a.state(), a.playerId()).stream()
        .filter(f -> f.ships.stream().anyMatch(s -> s.shipProductTypeId.equals("p_cruiser")))
        .findFirst().orElseThrow();
  }

  private static double shipCount(Fleet fleet, String shipProductTypeId) {
    for (FleetShipGroup g : fleet.ships) if (g.shipProductTypeId.equals(shipProductTypeId)) return g.quantity;
    return 0;
  }

  /** Soldaten an Bord, ohne den Umweg über eine Garnison – der Testaufbau interessiert sich nur für das Ergebnis. */
  private static void putSoldiersAboard(Arena a, Fleet fleet, int soldiers) {
    GroundForceGroup group = new GroundForceGroup();
    group.id = a.ids().next("gfg");
    group.ownerId = fleet.ownerId;
    group.colonyId = null;
    group.fleetId = fleet.id;
    group.units = new ArrayList<>();
    GroundForceUnitStack stack = new GroundForceUnitStack();
    stack.unitProductTypeId = GameConstants.SOLDIER_PRODUCT_ID;
    stack.activeCount = 0;
    stack.reserveCount = soldiers;
    group.units.add(stack);
    a.state().groundForceGroups.add(group);
  }

  // --- Zusammenlegen ---------------------------------------------------------

  @Test
  void mergeMovesShipsCargoFuelAndTroopsAndDissolvesTheSource() {
    Arena a = newArena();
    Fleet target = combat(a);
    Fleet source = freighter(a);
    double cruisersBefore = shipCount(target, "p_cruiser");
    double fuelBefore = target.fuelCapsules + source.fuelCapsules;
    FleetCargo.add(source, "p_grundnahrung", 40);
    source.ships = List.of(new FleetShipGroup("p_freighter", 1), new FleetShipGroup("p_trooptransport", 1));
    putSoldiersAboard(a, source, 120);

    FleetCompositionCommands.mergeFleets(a.state(), a.playerId(), target.id, source.id);

    assertFalse(a.state().fleets.stream().anyMatch(f -> f.id.equals(source.id)), "die Quellflotte ist aufgelöst");
    assertEquals(cruisersBefore, shipCount(target, "p_cruiser"), 1e-9, "eigene Schiffe bleiben");
    assertEquals(1, shipCount(target, "p_freighter"), 1e-9, "der Frachter ist übernommen");
    assertEquals(1, shipCount(target, "p_trooptransport"), 1e-9);
    assertEquals(40, FleetCargo.qty(target, "p_grundnahrung"), 1e-9, "die Fracht ist mitgekommen");
    assertEquals(fuelBefore, target.fuelCapsules, 0.01, "beide Tanks addieren sich");
    assertEquals(120, TroopTransportCommands.soldiersAboard(a.state(), target.id), 1e-9, "die Soldaten sind an Bord der Zielflotte");
    assertTrue(a.state().groundForceGroups.stream().noneMatch(g -> source.id.equals(g.fleetId)), "kein Verband ohne Flotte");
  }

  @Test
  void mergeRequiresTheSamePlaceAndAnIdleFleet() {
    Arena a = newArena();
    Fleet target = combat(a);
    Fleet source = freighter(a);

    // Andere Position im selben System: Frachter legt ab, die Kampfflotte bleibt im Kolonieorbit.
    FleetCommands.moveFleetWithinSystem(a.state(), a.playerId(), source.id, new de.nebula.model.FleetSystemTarget.System());
    CommandException differentPlace = assertThrows(CommandException.class,
        () -> FleetCompositionCommands.mergeFleets(a.state(), a.playerId(), target.id, source.id));
    assertTrue(differentPlace.getMessage().contains("selben Ort"), differentPlace.getMessage());

    // Wieder am selben Ort, aber unterwegs.
    FleetCommands.moveFleetWithinSystem(a.state(), a.playerId(), source.id,
        new de.nebula.model.FleetSystemTarget.ColonyOrbit(a.home().id));
    source.status = FleetStatus.InTransit;
    CommandException inTransit = assertThrows(CommandException.class,
        () -> FleetCompositionCommands.mergeFleets(a.state(), a.playerId(), target.id, source.id));
    assertTrue(inTransit.getMessage().contains("unterwegs"), inTransit.getMessage());

    assertThrows(CommandException.class,
        () -> FleetCompositionCommands.mergeFleets(a.state(), a.playerId(), target.id, target.id));
  }

  // --- Aufteilen -------------------------------------------------------------

  @Test
  void splitMovesTheChosenShipsCargoSoldiersAndAShareOfTheFuel() {
    Arena a = newArena();
    Fleet source = combat(a);
    source.ships = List.of(new FleetShipGroup("p_corvette", 4), new FleetShipGroup("p_freighter", 2),
        new FleetShipGroup("p_trooptransport", 2));
    source.fuelCapsules = 8;
    FleetCargo.add(source, "p_grundnahrung", 100);
    putSoldiersAboard(a, source, 1500);

    Fleet fresh = FleetCompositionCommands.splitFleet(a.state(), a.ids(), a.playerId(), source.id,
        Map.of("p_corvette", 1.0, "p_freighter", 1.0, "p_trooptransport", 1.0),
        Map.of("p_grundnahrung", 60.0), 900, "Landungsverband");

    assertEquals("Landungsverband", fresh.name);
    assertEquals(source.systemId, fresh.systemId);
    assertEquals(source.locationType, fresh.locationType);
    assertEquals(source.locationColonyId, fresh.locationColonyId);
    assertEquals(FleetStatus.Stationed, fresh.status);

    assertEquals(1, shipCount(fresh, "p_corvette"), 1e-9);
    assertEquals(3, shipCount(source, "p_corvette"), 1e-9);
    assertEquals(1, shipCount(fresh, "p_freighter"), 1e-9);
    assertEquals(60, FleetCargo.qty(fresh, "p_grundnahrung"), 1e-9);
    assertEquals(40, FleetCargo.qty(source, "p_grundnahrung"), 1e-9);
    assertEquals(900, TroopTransportCommands.soldiersAboard(a.state(), fresh.id), 1e-9);
    assertEquals(600, TroopTransportCommands.soldiersAboard(a.state(), source.id), 1e-9);
    // 3 von 8 Schiffen gehen mit, also auch 3/8 des Tanks.
    assertEquals(3.0, fresh.fuelCapsules, 0.01);
    assertEquals(5.0, source.fuelCapsules, 0.01);
  }

  @Test
  void splitRefusesWhenEitherSideCannotCarryItsCargo() {
    Arena a = newArena();
    Fleet source = combat(a);
    source.ships = List.of(new FleetShipGroup("p_corvette", 2), new FleetShipGroup("p_freighter", 1));
    FleetCargo.add(source, "p_grundnahrung", 100);

    // Alle 100 Stück sollen mit – aber nur Korvetten (ohne Laderaum) gehen mit.
    CommandException newSide = assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of("p_corvette", 1.0), Map.of("p_grundnahrung", 100.0), 0, "Zu voll"));
    assertTrue(newSide.getMessage().contains("abgespaltene Flotte"), newSide.getMessage());

    // Umgekehrt: der Frachter geht, die Fracht bleibt bei den Korvetten zurück.
    CommandException oldSide = assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of("p_freighter", 1.0), Map.of(), 0, "Leer los"));
    assertTrue(oldSide.getMessage().contains("Ursprungsflotte"), oldSide.getMessage());

    assertEquals(1, FleetCommands.fleetsOf(a.state(), a.playerId()).stream().filter(f -> f.id.equals(source.id)).count());
    assertEquals(100, FleetCargo.qty(source, "p_grundnahrung"), 1e-9, "nach einem Fehler ist nichts verschoben");
  }

  @Test
  void splitRefusesWhenSoldiersWouldLoseTheirTransport() {
    Arena a = newArena();
    Fleet source = combat(a);
    source.ships = List.of(new FleetShipGroup("p_corvette", 2), new FleetShipGroup("p_trooptransport", 1));
    putSoldiersAboard(a, source, 800);

    // Der einzige Mannschaftstransporter geht mit, die Soldaten sollen bleiben.
    CommandException stranded = assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of("p_trooptransport", 1.0), Map.of(), 0, "Ohne Truppe"));
    assertTrue(stranded.getMessage().contains("Ursprungsflotte"), stranded.getMessage());

    // Und andersherum: mehr Soldaten, als der mitgegebene Transporter fasst.
    CommandException overloaded = assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of("p_corvette", 1.0), Map.of(), 800, "Ohne Platz"));
    assertTrue(overloaded.getMessage().contains("Platz für 0 Soldaten"), overloaded.getMessage());

    // Der zulässige Fall: Transporter UND Soldaten gehen gemeinsam.
    Fleet fresh = FleetCompositionCommands.splitFleet(a.state(), a.ids(), a.playerId(), source.id,
        Map.of("p_trooptransport", 1.0), Map.of(), 800, "Landung");
    assertEquals(800, TroopTransportCommands.soldiersAboard(a.state(), fresh.id), 1e-9);
    assertEquals(0, TroopTransportCommands.soldiersAboard(a.state(), source.id), 1e-9);
    assertNotNull(TroopTransportCommands.embarkedForces(a.state(), fresh.id));
    assertTrue(a.state().groundForceGroups.stream().noneMatch(g -> source.id.equals(g.fleetId)),
        "der leere Verband der Ursprungsflotte ist aufgelöst");
  }

  @Test
  void splitNeedsShipsOnBothSides() {
    Arena a = newArena();
    Fleet source = combat(a);
    source.ships = List.of(new FleetShipGroup("p_corvette", 2));

    assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of(), Map.of(), 0, "Nichts"));
    CommandException all = assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of("p_corvette", 2.0), Map.of(), 0, "Alles"));
    assertTrue(all.getMessage().contains("mindestens ein Schiff behalten"), all.getMessage());
    assertThrows(CommandException.class, () -> FleetCompositionCommands.splitFleet(
        a.state(), a.ids(), a.playerId(), source.id, Map.of("p_corvette", 5.0), Map.of(), 0, "Zu viele"));
  }

  @Test
  void splitFallsBackToAGeneratedName() {
    Arena a = newArena();
    Fleet source = combat(a);
    source.ships = List.of(new FleetShipGroup("p_corvette", 2));
    Fleet fresh = FleetCompositionCommands.splitFleet(a.state(), a.ids(), a.playerId(), source.id,
        Map.of("p_corvette", 1.0), Map.of(), 0, "   ");
    assertFalse(fresh.name.isBlank());
    assertEquals(FleetLocationType.ColonyOrbit, fresh.locationType);
  }
}
