package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetStatus;
import de.nebula.model.Gateway;
import de.nebula.model.Planet;
import de.nebula.model.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifiziert den Eleriumkapsel-Sprungtreibstoff: 0,01 Kapseln je Schiff und
 * Gateway-Sprung, entnommen aus dem eigenen TANK der Flotte
 * (Umsetzungskonzept/26_...md). Betankt wird ausschließlich über den eigenen
 * Befehl {@code refuelFleet} – abgetankt wird mit {@code drainFleetFuel} bzw.
 * zwischen Flotten mit {@code transferFuelBetweenFleets}. Nur GANZE Kapseln
 * verlassen den Tank; die angebrochene bleibt an Bord.
 */
class FleetCommandsJumpFuelTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId, Colony home) {
  }

  private static Bootstrapped newBootstrappedState() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    String playerId = state.players.get(0).id;
    Colony home = state.colonies.stream().filter(c -> c.isHomeworld).findFirst().orElseThrow();
    return new Bootstrapped(state, ids, playerId, home);
  }

  private static String neighborOf(GameState state, String systemId) {
    Gateway gw = state.gateways.stream().filter(g -> g.systemId.equals(systemId)).findFirst().orElseThrow();
    return gw.reachableSystemIds.get(0);
  }

  /** Erstes erreichbares System in genau {@code hops} Gateway-Sprüngen Entfernung von {@code fromSystemId}. */
  private static String systemAtHopDistance(GameState state, String fromSystemId, int hops) {
    Map<String, Integer> dist = de.nebula.engine.Graph.bfsHops(GatewayCommands.gatewayRoutes(state), fromSystemId);
    return dist.entrySet().stream()
        .filter(e -> e.getValue() == hops)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new AssertionError("kein System in " + hops + " Sprüngen Entfernung gefunden"));
  }

  private static Fleet fleetNamed(GameState state, String playerId, String name) {
    return FleetCommands.fleetsOf(state, playerId).stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
  }

  private static double totalShips(Fleet fleet) {
    return fleet.ships.stream().mapToDouble(g -> g.quantity).sum();
  }

  @Test
  void newCommanderStartsWithFuelInTheTankAndCapsulesInStock() {
    Bootstrapped b = newBootstrappedState();
    assertEquals(10.0, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID));
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    assertTrue(freighter.fuelCapsules > 0, "Startflotten laufen betankt aus");
  }

  /** Tankgröße ist 1.000 Kapseln JE SCHIFF – eine reine Flotteneigenschaft. */
  @Test
  void tankCapacityScalesWithShipCount() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    Fleet combat = fleetNamed(b.state(), b.playerId(), "Kampfflotte Testheim");
    assertEquals(GameConstants.JUMP_FUEL_TANK_PER_SHIP * totalShips(freighter), FleetCommands.fuelTankCapacity(freighter), 1e-9);
    assertEquals(GameConstants.JUMP_FUEL_TANK_PER_SHIP * totalShips(combat), FleetCommands.fuelTankCapacity(combat), 1e-9);
  }

  /**
   * Betanken verschiebt Kapseln aus dem Kolonielager in den Tank – und lässt die
   * FRACHT unberührt. Das ist der Kern der Abgrenzung: der Tank ist kein Laderaum.
   */
  @Test
  void refuellingMovesCapsulesFromStockIntoTheTankWithoutTouchingCargo() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    double stockBefore = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    double tankBefore = freighter.fuelCapsules;
    int cargoEntriesBefore = freighter.cargo.size();

    FleetCommands.refuelFleet(b.state(), b.playerId(), freighter.id, 4);

    assertEquals(stockBefore - 4, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID), 1e-9);
    assertEquals(tankBefore + 4, freighter.fuelCapsules, 1e-9);
    assertEquals(cargoEntriesBefore, freighter.cargo.size(), "Treibstoff darf niemals als Fracht auftauchen");
  }

  @Test
  void refuellingIsRejectedWithoutStockOrBeyondTankCapacity() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");

    CommandException tooMuch = assertThrows(CommandException.class,
        () -> FleetCommands.refuelFleet(b.state(), b.playerId(), freighter.id, 999));
    assertTrue(tooMuch.getMessage().contains("Standort"), tooMuch.getMessage());

    // Genug Vorrat, aber über die Tankgröße hinaus.
    Warehouse.add(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID, 5000);
    CommandException tankFull = assertThrows(CommandException.class,
        () -> FleetCommands.refuelFleet(b.state(), b.playerId(), freighter.id,
            FleetCommands.fuelTankCapacity(freighter) + 1));
    assertTrue(tankFull.getMessage().contains("Tank"), tankFull.getMessage());
  }

  @Test
  void refuellingRequiresBeingLandedAtAnOwnColony() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    freighter.locationColonyId = null; // im System, aber nicht angedockt

    CommandException ex = assertThrows(CommandException.class,
        () -> FleetCommands.refuelFleet(b.state(), b.playerId(), freighter.id, 1));
    assertTrue(ex.getMessage().contains("umschlagen"), ex.getMessage());
  }

  /**
   * Ein einzelner Sprung kostet weniger als eine ganze Kapsel: der Tank sinkt um
   * genau diesen Bruchteil, der Rest der Kapsel bleibt als angebrochene an Bord.
   */
  @Test
  void singleShipSingleHopBroachesExactlyOneCapsule() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    assertEquals(1.0, totalShips(freighter), 0.0001, "Startfrachter sollte genau 1 Schiff sein");
    String destination = neighborOf(b.state(), freighter.systemId);
    double tankBefore = freighter.fuelCapsules;

    FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination);

    assertEquals(tankBefore - GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP, freighter.fuelCapsules, 1e-9,
        "Der Tank führt den Bruchteil selbst – er IST die angebrochene Kapsel");
    assertEquals(FleetStatus.InTransit, freighter.status);
    assertEquals(destination, freighter.destinationSystemId);
  }

  /** Über viele Sprünge entspricht der Tankverbrauch exakt der Rate. */
  @Test
  void overManyJumpsTheTankDrainsAtExactlyTheRate() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    String destination = neighborOf(b.state(), freighter.systemId);
    Warehouse.add(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID, 100);
    FleetCommands.refuelFleet(b.state(), b.playerId(), freighter.id, 100);
    double tankBefore = freighter.fuelCapsules;

    int jumps = 500;
    for (int i = 0; i < jumps; i++) {
      freighter.status = FleetStatus.Stationed;
      freighter.destinationSystemId = null;
      FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination);
    }
    double verbraucht = tankBefore - freighter.fuelCapsules;
    double erwartet = jumps * totalShips(freighter) * GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP;

    assertEquals(erwartet, verbraucht, 1.0, "Langfristiger Verbrauch muss der Rate entsprechen");
    assertEquals(verbraucht, Math.floor(verbraucht), 1e-9, "Abgebucht wurden nur ganze Kapseln");
  }

  @Test
  void costScalesWithShipCountAndHopCount() {
    Bootstrapped b = newBootstrappedState();
    Fleet combat = fleetNamed(b.state(), b.playerId(), "Kampfflotte Testheim");
    double ships = totalShips(combat);
    assertTrue(ships >= 4 && ships <= 16, "Startkampfflotte: Korvette(2-8)+Zerstörer(1-5)+Kreuzer(1-3)");

    String twoHopsAway = systemAtHopDistance(b.state(), combat.systemId, 2);
    double tankBefore = combat.fuelCapsules;
    FleetCommands.moveFleet(b.state(), b.playerId(), combat.id, twoHopsAway);

    double expected = ships * 2 * GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP;
    assertEquals(tankBefore - expected, combat.fuelCapsules, 1e-9);
    assertEquals(2, combat.pendingHops.size() + 1, "Route sollte genau 2 Sprünge (1 laufend + 1 pending) umfassen");
  }

  /** Leerer Tank blockiert den Sprung – Kolonielager helfen NICHT mehr aus der Ferne. */
  @Test
  void emptyTankBlocksTheJumpEvenWhenTheColonyHasCapsules() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    String destination = neighborOf(b.state(), freighter.systemId);
    String originSystem = freighter.systemId;
    freighter.fuelCapsules = 0;
    double stockBefore = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    assertTrue(stockBefore > 0, "Die Kolonie hat durchaus Kapseln – sie helfen nur nicht mehr");

    CommandException ex = assertThrows(CommandException.class,
        () -> FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination));
    assertTrue(ex.getMessage().contains("Tank"), ex.getMessage());

    assertEquals(FleetStatus.Stationed, freighter.status, "Flotte darf bei abgelehntem Sprung nicht losfliegen");
    assertEquals(originSystem, freighter.systemId);
    assertEquals(stockBefore, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID), 1e-9);
  }

  @Test
  void jumpFuelProductIsTheExistingEleriumkapselProduct() {
    assertEquals("p_elerium_kapsel", GameConstants.JUMP_FUEL_PRODUCT_ID);
    assertEquals(0.01, GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP, 0.0001);
    assertEquals(1000, GameConstants.JUMP_FUEL_TANK_PER_SHIP, 0.0001);
  }

  /**
   * Der Kern der Nutzervorgabe: der Tank darf auch als Lager dienen, Treibstoff
   * fließt in BEIDE Richtungen – aber nur in ganzen Kapseln.
   */
  @Test
  void fuelFlowsBothWaysBetweenTankAndColonyStock() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    double stockBefore = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    double tankBefore = freighter.fuelCapsules;

    FleetCommands.refuelFleet(b.state(), b.playerId(), freighter.id, 4);
    FleetCommands.drainFleetFuel(b.state(), b.playerId(), freighter.id, 4);

    assertEquals(stockBefore, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID), 1e-9);
    assertEquals(tankBefore, freighter.fuelCapsules, 1e-9);
  }

  /** Eine angebrochene Kapsel bleibt an Bord und lässt sich nicht abtanken. */
  @Test
  void aBroachedCapsuleCannotBeDrained() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    freighter.fuelCapsules = 3.4;   // 3 ganze + eine zu 60 % verflogene

    assertEquals(3, FleetCommands.drainableFuel(freighter), 1e-9);
    FleetCommands.drainFleetFuel(b.state(), b.playerId(), freighter.id, 3);
    assertEquals(0.4, freighter.fuelCapsules, 1e-9, "Die angebrochene Kapsel bleibt im Tank");

    CommandException ex = assertThrows(CommandException.class,
        () -> FleetCommands.drainFleetFuel(b.state(), b.playerId(), freighter.id, 1));
    assertTrue(ex.getMessage().contains("angebrochene"), ex.getMessage());
  }

  /** Rettungsweg: eine gestrandete Flotte wird von einer anderen im selben System betankt. */
  @Test
  void aStrandedFleetCanBeRefuelledByAnotherFleetInTheSameSystem() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    Fleet combat = fleetNamed(b.state(), b.playerId(), "Kampfflotte Testheim");
    freighter.fuelCapsules = 0;                    // gestrandet
    combat.fuelCapsules = 10;
    combat.locationColonyId = null;                // beide nur "im System", keine Kolonie noetig
    freighter.locationColonyId = null;

    FleetCommands.transferFuelBetweenFleets(b.state(), b.playerId(), combat.id, freighter.id, 6);

    assertEquals(4, combat.fuelCapsules, 1e-9);
    assertEquals(6, freighter.fuelCapsules, 1e-9);
  }

  @Test
  void fuelTransferRequiresBothFleetsInTheSameSystem() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    Fleet combat = fleetNamed(b.state(), b.playerId(), "Kampfflotte Testheim");
    combat.fuelCapsules = 10;
    freighter.systemId = "sys_woanders";

    CommandException ex = assertThrows(CommandException.class,
        () -> FleetCommands.transferFuelBetweenFleets(b.state(), b.playerId(), combat.id, freighter.id, 1));
    assertTrue(ex.getMessage().contains("selben System"), ex.getMessage());
  }
}
