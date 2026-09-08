package de.nebula.state;

import de.nebula.data.ShipCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.PlanetStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verladung von Bodentruppen (Umsetzungskonzept/28_...md). Geprüft wird die
 * Aufteilung, die den ganzen Entwurf trägt: <b>Soldaten nur im
 * Mannschaftstransporter, Drohnen nur als Fracht</b> – und dass sich keiner
 * der beiden Wege für den jeweils anderen missbrauchen lässt.
 */
class TroopTransportTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId, String colonyId) {
  }

  private static Bootstrapped newState() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    return new Bootstrapped(state, ids, state.players.get(0).id, state.players.get(0).homeworldColonyId);
  }

  /** Stellt eine Flotte aus {@code quantity} Schiffen bei der Heimatkolonie ab. */
  private static Fleet fleetAtColony(Bootstrapped b, String shipProductTypeId, int quantity) {
    var colony = ColonyCommands.colony(b.state(), b.colonyId());
    Fleet fleet = new Fleet();
    fleet.id = b.ids().next("flt");
    fleet.ownerId = b.playerId();
    fleet.name = "Testflotte";
    fleet.locationType = FleetLocationType.ColonyOrbit;
    fleet.locationColonyId = b.colonyId();
    fleet.locationPlanetId = colony.planetId;
    fleet.systemId = b.state().players.get(0).homeSystemId;
    fleet.status = FleetStatus.Stationed;
    fleet.ships = List.of(new FleetShipGroup(shipProductTypeId, quantity));
    fleet.cargo = new ArrayList<>();
    fleet.fuelCapsules = 0;
    fleet.destinationSystemId = null;
    fleet.pendingHops = List.of();
    fleet.departedAt = null;
    fleet.arrivesAt = null;
    b.state().fleets.add(fleet);
    return fleet;
  }

  private static void putSoldiersInGarrison(Bootstrapped b, int count) {
    GroundForceGroup garrison = RecruitmentCommands.groundForces(b.state(), b.colonyId());
    for (GroundForceUnitStack u : garrison.units) {
      if (u.unitProductTypeId.equals(GameConstants.SOLDIER_PRODUCT_ID)) {
        u.activeCount = 0;
        u.reserveCount = count;
        return;
      }
    }
  }

  private static double garrisonCount(Bootstrapped b, String unitProductTypeId) {
    GroundForceGroup garrison = RecruitmentCommands.groundForces(b.state(), b.colonyId());
    if (garrison == null) return 0;
    return garrison.units.stream().filter(u -> u.unitProductTypeId.equals(unitProductTypeId))
        .mapToDouble(u -> u.activeCount + u.reserveCount).sum();
  }

  @Test
  void troopCapacityComesOnlyFromTheTroopTransport() {
    assertEquals(1000, ShipCatalog.find("p_trooptransport").troopCapacity, 0.001);
    for (var ship : ShipCatalog.CATALOG) {
      if (ship.productTypeId.equals("p_trooptransport")) continue;
      assertEquals(0, ship.troopCapacity, 0.001,
          ship.productTypeId + " darf keine Soldaten aufnehmen – nur der Mannschaftstransporter (28_...md, §A).");
    }
  }

  @Test
  void soldiersEmbarkUpToCapacityAndNoFurther() {
    Bootstrapped b = newState();
    Fleet fleet = fleetAtColony(b, "p_trooptransport", 2);
    putSoldiersInGarrison(b, 2500);

    TroopTransportCommands.embarkSoldiers(b.state(), b.ids(), b.playerId(), fleet.id, 2000);
    assertEquals(2000, TroopTransportCommands.soldiersAboard(b.state(), fleet.id), 0.001);
    assertEquals(500, garrisonCount(b, GameConstants.SOLDIER_PRODUCT_ID), 0.001);

    // 2 Transporter × 1000 Plätze sind voll.
    var tooMany = assertThrows(CommandException.class,
        () -> TroopTransportCommands.embarkSoldiers(b.state(), b.ids(), b.playerId(), fleet.id, 1));
    assertTrue(tooMany.getMessage().contains("Platz"), tooMany.getMessage());
  }

  @Test
  void aFleetWithoutTroopTransportTakesNoSoldiers() {
    Bootstrapped b = newState();
    Fleet freighters = fleetAtColony(b, "p_freighter", 5);
    putSoldiersInGarrison(b, 100);

    var e = assertThrows(CommandException.class,
        () -> TroopTransportCommands.embarkSoldiers(b.state(), b.ids(), b.playerId(), freighters.id, 1));
    assertTrue(e.getMessage().contains("Mannschaftstransporter"), e.getMessage());
  }

  /**
   * Der Kern der Aufteilung: eingeschiffte Soldaten verschwinden aus der
   * Garnison – auch aus der Sicherheitsrechnung von
   * {@link EconomyTick#recalcCoreStats} – und kommen beim Ausschiffen
   * vollständig zurück.
   */
  @Test
  void embarkedSoldiersLeaveTheColonyAndComeBack() {
    Bootstrapped b = newState();
    Fleet fleet = fleetAtColony(b, "p_trooptransport", 1);
    putSoldiersInGarrison(b, 300);

    TroopTransportCommands.embarkSoldiers(b.state(), b.ids(), b.playerId(), fleet.id, 300);
    assertEquals(0, garrisonCount(b, GameConstants.SOLDIER_PRODUCT_ID), 0.001);

    GroundForceGroup aboard = TroopTransportCommands.embarkedForces(b.state(), fleet.id);
    assertNotNull(aboard);
    assertNull(aboard.colonyId, "Ein Verband an Bord gehört zu keiner Kolonie mehr (GroundForceGroup)");
    assertEquals(fleet.id, aboard.fleetId);
    // Gegenprobe, dass der Tick den Verband ohne colonyId verträgt.
    EconomyTick.recalcCoreStats(b.state(), 0);
    PlanetStats stats = ColonyCommands.colonyStats(b.state(), b.colonyId());
    assertNotNull(stats);

    TroopTransportCommands.disembarkSoldiers(b.state(), b.ids(), b.playerId(), fleet.id, 300);
    assertEquals(300, garrisonCount(b, GameConstants.SOLDIER_PRODUCT_ID), 0.001);
    assertNull(TroopTransportCommands.embarkedForces(b.state(), fleet.id),
        "Der leere Verband an Bord muss verschwinden, keine Geistergruppe zurücklassen");
  }

  /** Drohnen sind Maschinen: Garnison → Lager → Frachter, und wieder zurück. */
  @Test
  void dronesTravelAsOrdinaryFreight() {
    Bootstrapped b = newState();
    Fleet freighter = fleetAtColony(b, "p_freighter", 1);
    assertEquals(10, garrisonCount(b, "p_drone_light"), 0.001, "Startgarnison laut WorldSeed");

    TroopTransportCommands.storeDrones(b.state(), b.playerId(), b.colonyId(), "p_drone_light", 10);
    assertEquals(0, garrisonCount(b, "p_drone_light"), 0.001);
    assertEquals(10, Warehouse.qty(b.state(), b.colonyId(), "p_drone_light"), 0.001);

    FleetCommands.loadCargo(b.state(), b.playerId(), freighter.id, "p_drone_light", 10);
    assertEquals(10, FleetCargo.qty(freighter, "p_drone_light"), 0.001);
    assertEquals(0, Warehouse.qty(b.state(), b.colonyId(), "p_drone_light"), 0.001);

    FleetCommands.unloadCargo(b.state(), b.playerId(), freighter.id, "p_drone_light", 10);
    TroopTransportCommands.deployDrones(b.state(), b.ids(), b.playerId(), b.colonyId(), "p_drone_light", 10);
    assertEquals(10, garrisonCount(b, "p_drone_light"), 0.001);
  }

  /**
   * Die Sperre, ohne die der ganze Entwurf umgehbar wäre: kämen Soldaten je
   * ins Lager, ließen sie sich sonst als Ware an {@code troopCapacity} vorbei
   * verschiffen.
   */
  @Test
  void soldiersAreNeverFreight() {
    Bootstrapped b = newState();
    Fleet freighter = fleetAtColony(b, "p_freighter", 1);
    Warehouse.add(b.state(), b.colonyId(), GameConstants.SOLDIER_PRODUCT_ID, 100);

    var e = assertThrows(CommandException.class, () -> FleetCommands.loadCargo(
        b.state(), b.playerId(), freighter.id, GameConstants.SOLDIER_PRODUCT_ID, 1));
    assertTrue(e.getMessage().contains("Mannschaftstransporter"), e.getMessage());

    assertThrows(CommandException.class, () -> TroopTransportCommands.storeDrones(
        b.state(), b.playerId(), b.colonyId(), GameConstants.SOLDIER_PRODUCT_ID, 1),
        "Soldaten dürfen auch nicht über den Drohnenweg ins Lager");
  }
}
