package de.nebula.state;

import de.nebula.data.ShipCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.StarSystem;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trägersprung ohne Gateway (Umsetzungskonzept/06_...md, {@code CarrierTransit}).
 *
 * <p>Die zentrale Regel und der eigentliche Grund für diese Tests: Ein Träger
 * nimmt die ÜBRIGEN Schiffe der Flotte an Bord. Reicht sein Laderaum dafür
 * nicht, wird der Sprung ABGEBROCHEN – er startet nicht teilweise und lässt
 * auch keine Schiffe zurück.</p>
 */
class CarrierTransitTest {

  private static final double CARRIER_CAPACITY = ShipCatalog.find("p_carrier").carrierSlotCapacity;
  private static final double CRUISER_USAGE = ShipCatalog.find("p_cruiser").carrierSlotUsage;

  private record Arena(GameState state, IdGenerator ids, String playerId, Colony home) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    Colony home = state.colonies.stream().filter(c -> c.isHomeworld).findFirst().orElseThrow();
    return new Arena(state, ids, state.players.get(0).id, home);
  }

  /** Eine frische Flotte mit genau der angegebenen Zusammensetzung, im Heimatsystem, voll betankt. */
  private static Fleet fleetWith(Arena a, List<FleetShipGroup> ships) {
    Fleet fleet = new Fleet();
    fleet.id = a.ids().next("flt");
    fleet.ownerId = a.playerId();
    fleet.name = "Testflotte";
    fleet.systemId = a.home().systemId;
    fleet.status = FleetStatus.Stationed;
    fleet.locationType = de.nebula.model.FleetLocationType.System;
    fleet.ships = new ArrayList<>(ships);
    fleet.cargo = new ArrayList<>();
    fleet.pendingHops = List.of();
    // Voller Tank: der Verbrauch hängt seit Umsetzungskonzept/34_...md an der
    // Schiffsmasse, eine feste Kapselzahl wäre für einen Träger ein Tropfen.
    fleet.fuelCapsules = FleetCommands.fuelTankCapacity(fleet);
    a.state().fleets.add(fleet);
    return fleet;
  }

  /** Irgendein System, das NICHT das Heimatsystem ist. */
  private static String otherSystemId(Arena a) {
    return a.state().systems.stream()
        .filter(s -> !s.id.equals(a.home().systemId))
        .map(s -> s.id).findFirst().orElseThrow();
  }

  // --- Kapazität -------------------------------------------------------------

  @Test
  void carrierCarriesTheOtherShipsButNotItself() {
    Arena a = newArena();
    Fleet fleet = fleetWith(a, List.of(
        new FleetShipGroup("p_carrier", 1),
        new FleetShipGroup("p_corvette", 10)));

    assertEquals(CARRIER_CAPACITY, FleetCommands.carrierSlotCapacity(fleet), 1e-9,
        "Der Träger stellt seine volle Kapazität bereit.");
    // Der Träger selbst hat carrierSlotUsage 0 – er fliegt aus eigener Kraft.
    assertEquals(10 * ShipCatalog.find("p_corvette").carrierSlotUsage,
        FleetCommands.carrierSlotLoad(fleet), 1e-9,
        "Nur die mitfliegenden Schiffe belegen Slots, der Träger nicht sich selbst.");
  }

  @Test
  void jumpIsPossibleWhenTheCarrierHasRoom() {
    Arena a = newArena();
    Fleet fleet = fleetWith(a, List.of(
        new FleetShipGroup("p_carrier", 1),
        new FleetShipGroup("p_corvette", 5)));

    var preview = FleetCommands.carrierJumpPreview(a.state(), fleet.id, otherSystemId(a));
    assertNotNull(preview);
    assertTrue(preview.possible(), "Fünf Korvetten passen locker in einen Träger.");
    assertTrue(preview.ms() > 0, "Ein Sprung dauert Zeit.");
    assertTrue(preview.fuelNeeded() > 0, "Ein Sprung kostet Treibstoff.");
  }

  /** DIE Kernregel: Reicht der Laderaum nicht, bricht der Sprung ab. */
  @Test
  void jumpIsRefusedWhenTheCarriersCannotHoldEveryShip() {
    Arena a = newArena();
    // Ein Träger fasst 400 Slots, ein Kreuzer belegt 100 – fünf passen nicht.
    int tooManyCruisers = (int) Math.ceil(CARRIER_CAPACITY / CRUISER_USAGE) + 1;
    Fleet fleet = fleetWith(a, List.of(
        new FleetShipGroup("p_carrier", 1),
        new FleetShipGroup("p_cruiser", tooManyCruisers)));
    String destination = otherSystemId(a);

    var preview = FleetCommands.carrierJumpPreview(a.state(), fleet.id, destination);
    assertNotNull(preview);
    assertFalse(preview.possible(), "Der Laderaum reicht nicht.");
    assertNotNull(preview.reason());

    var error = assertThrows(CommandException.class,
        () -> FleetCommands.moveFleet(a.state(), a.playerId(), fleet.id, destination, true));
    assertTrue(error.getMessage().contains("fassen die übrigen Schiffe nicht"),
        "Die Meldung nennt den Grund, nicht nur ein Scheitern: " + error.getMessage());
    assertTrue(error.getMessage().contains("Trägerschiffe"),
        "Die Meldung sagt, dass Trägerschiffe fehlen: " + error.getMessage());

    assertEquals(FleetStatus.Stationed, fleet.status,
        "Ein abgebrochener Sprung lässt die Flotte stehen – sie startet nicht teilweise.");
    assertEquals(a.home().systemId, fleet.systemId);
  }

  @Test
  void jumpIsRefusedWithoutAnyCarrier() {
    Arena a = newArena();
    Fleet fleet = fleetWith(a, List.of(new FleetShipGroup("p_corvette", 3)));
    var error = assertThrows(CommandException.class,
        () -> FleetCommands.moveFleet(a.state(), a.playerId(), fleet.id, otherSystemId(a), true));
    assertTrue(error.getMessage().contains("Keine Trägerschiffe"), error.getMessage());
  }

  // --- Ablauf ----------------------------------------------------------------

  /**
   * Der eigentliche Zweck des Trägers: ein Ziel erreichen, zu dem KEINE
   * Gateway-Kette führt. Der reguläre Flug scheitert dort, der Trägersprung
   * nicht.
   */
  @Test
  void carrierReachesASystemWithoutAnyGatewayRoute() {
    Arena a = newArena();
    // Ein System ohne jede Gateway-Verbindung: eigens angelegt, damit der Test
    // nicht davon abhängt, ob die zufällige Topologie zufällig eine Insel hat.
    StarSystem isolated = new StarSystem();
    isolated.id = a.ids().next("sys");
    isolated.name = "Inselsystem";
    isolated.x = 0.9;
    isolated.y = 0.9;
    isolated.planetIds = List.of();
    isolated.gatewayId = null;
    a.state().systems.add(isolated);

    Fleet fleet = fleetWith(a, List.of(
        new FleetShipGroup("p_carrier", 1),
        new FleetShipGroup("p_corvette", 2)));

    assertThrows(CommandException.class,
        () -> FleetCommands.moveFleet(a.state(), a.playerId(), fleet.id, isolated.id),
        "Ohne Gateway-Pfad gibt es keine reguläre Route.");

    FleetCommands.moveFleet(a.state(), a.playerId(), fleet.id, isolated.id, true);
    assertEquals(FleetStatus.InTransit, fleet.status);
    assertEquals(isolated.id, fleet.destinationSystemId);
    assertTrue(fleet.pendingHops.isEmpty(), "Ein Trägersprung ist EIN Sprung ohne Zwischenstationen.");

    FleetCommands.processFleetArrivals(a.state(), a.ids(), fleet.arrivesAt);
    assertEquals(FleetStatus.Stationed, fleet.status);
    assertEquals(isolated.id, fleet.systemId);
  }

  @Test
  void carrierJumpCostsMoreTimeAndFuelThanAGatewayHop() {
    Arena a = newArena();
    Fleet fleet = fleetWith(a, List.of(
        new FleetShipGroup("p_carrier", 1),
        new FleetShipGroup("p_corvette", 2)));
    String destination = otherSystemId(a);

    var carrier = FleetCommands.carrierJumpPreview(a.state(), fleet.id, destination);
    var gateway = FleetCommands.routePreview(a.state(), fleet.id, destination);
    assertNotNull(carrier);
    assertTrue(carrier.possible());

    double plainFuelPerHop = FleetCommands.jumpFuelPerHop(fleet);
    assertTrue(carrier.fuelNeeded() > plainFuelPerHop,
        "Der Trägersprung ist teurer als ein einzelner Gateway-Sprung.");
    if (gateway != null) {
      assertTrue(carrier.ms() > gatewayTravelMs(gateway.hops()),
          "Der Trägersprung ist langsamer als dieselbe Strecke über Gateways.");
    }
  }

  private static double gatewayTravelMs(int hops) {
    return de.nebula.engine.Clock.hoursToMs(hops * GameConstants.HOURS_PER_GATEWAY_HOP);
  }

  @Test
  void carrierJumpIsRefusedWithoutEnoughFuel() {
    Arena a = newArena();
    Fleet fleet = fleetWith(a, List.of(
        new FleetShipGroup("p_carrier", 1),
        new FleetShipGroup("p_corvette", 2)));
    fleet.fuelCapsules = 0;

    var error = assertThrows(CommandException.class,
        () -> FleetCommands.moveFleet(a.state(), a.playerId(), fleet.id, otherSystemId(a), true));
    assertTrue(error.getMessage().contains("Treibstoff"), error.getMessage());
    assertEquals(FleetStatus.Stationed, fleet.status);
  }
}
