package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.engine.Rng;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.DefenseActivationState;
import de.nebula.model.Fleet;
import de.nebula.model.FleetCargoEntry;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Landung (Umsetzungskonzept/04_...md, Mechanik/05_...md §7-8) über die echten Befehle:
 * von Bord auf die Planetenoberfläche, unter Landungsabwehr, und von dort per
 * {@code moveGroundForces} weiter in eine eigene Kolonie. Bewusst NICHT geprüft: Bodengefecht,
 * Eroberung – das ist ausdrücklich kein Teil dieses Durchgangs (siehe {@code LandingCommands}).
 */
class LandingCommandsTest {

  private record Arena(GameState state, IdGenerator ids, String attackerId, String defenderId,
                        String attackerColonyId, String attackerPlanetId,
                        String defenderColonyId, String defenderPlanetId, String defenderSystemId) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Angreifer", "Angreiferheim", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, "Verteidiger", "Verteidigerheim", ids), ids);
    String attackerId = state.players.get(0).id;
    String defenderId = state.players.get(1).id;
    Colony attackerColony = ColonyCommands.colony(state, state.players.get(0).homeworldColonyId);
    Colony defenderColony = ColonyCommands.colony(state, state.players.get(1).homeworldColonyId);
    return new Arena(state, ids, attackerId, defenderId,
        attackerColony.id, attackerColony.planetId,
        defenderColony.id, defenderColony.planetId, defenderColony.systemId);
  }

  private static Fleet fleetInOrbit(Arena a, String planetId, String systemId, int transporters,
                                     int freighters, String droneProductTypeId, double droneQty) {
    Fleet fleet = new Fleet();
    fleet.id = a.ids().next("flt");
    fleet.ownerId = a.attackerId();
    fleet.name = "Landungsflotte";
    fleet.locationType = FleetLocationType.PlanetOrbit;
    fleet.locationColonyId = null;
    fleet.locationPlanetId = planetId;
    fleet.systemId = systemId;
    fleet.status = FleetStatus.Stationed;
    List<FleetShipGroup> ships = new ArrayList<>();
    if (transporters > 0) ships.add(new FleetShipGroup("p_trooptransport", transporters));
    if (freighters > 0) ships.add(new FleetShipGroup("p_freighter", freighters));
    fleet.ships = ships;
    fleet.cargo = new ArrayList<>();
    if (droneQty > 0) {
      FleetCargoEntry c = new FleetCargoEntry();
      c.productTypeId = droneProductTypeId;
      c.quantity = droneQty;
      fleet.cargo.add(c);
    }
    fleet.fuelCapsules = 0;
    fleet.destinationSystemId = null;
    fleet.pendingHops = List.of();
    fleet.departedAt = null;
    fleet.arrivesAt = null;
    a.state().fleets.add(fleet);
    return fleet;
  }

  /** Reine Testvorbereitung: Soldaten direkt an Bord setzen, ohne den Umweg über eine Kolonie. */
  private static void putSoldiersAboard(Arena a, Fleet fleet, int count) {
    GroundForceGroup aboard = new GroundForceGroup();
    aboard.id = a.ids().next("gfg");
    aboard.ownerId = fleet.ownerId;
    aboard.fleetId = fleet.id;
    aboard.units = new ArrayList<>();
    GroundForceUnitStack stack = new GroundForceUnitStack();
    stack.unitProductTypeId = GameConstants.SOLDIER_PRODUCT_ID;
    stack.reserveCount = count;
    aboard.units.add(stack);
    a.state().groundForceGroups.add(aboard);
  }

  private static Building activateDefense(Arena a, int level) {
    Building defense = new Building();
    defense.id = a.ids().next("bld");
    defense.colonyId = a.defenderColonyId();
    defense.typeId = GameConstants.PLANETARY_DEFENSE_BUILDING_ID;
    defense.level = level;
    defense.activationState = DefenseActivationState.Active;
    a.state().buildings.add(defense);
    return defense;
  }

  private static double unitCount(GroundForceGroup group, String unitProductTypeId) {
    return group.units.stream().filter(u -> u.unitProductTypeId.equals(unitProductTypeId))
        .mapToDouble(u -> u.activeCount + u.reserveCount).sum();
  }

  private static double transporterCount(Fleet fleet) {
    return fleet.ships.stream().filter(g -> g.shipProductTypeId.equals("p_trooptransport"))
        .mapToDouble(g -> g.quantity).sum();
  }

  @Test
  void landingWithoutOppositionArrivesInFull() {
    Arena a = newArena();
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 2, 1, "p_drone_light", 50);
    putSoldiersAboard(a, fleet, 300);

    GroundForceGroup surface = LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId());

    assertEquals(a.defenderPlanetId(), surface.planetId);
    assertEquals(300, unitCount(surface, GameConstants.SOLDIER_PRODUCT_ID), 0.001);
    assertEquals(50, unitCount(surface, "p_drone_light"), 0.001);
    assertNull(TroopTransportCommands.embarkedForces(a.state(), fleet.id),
        "Die Bord-Gruppe muss nach vollständiger Landung verschwinden");
    assertEquals(0, FleetCargo.qty(fleet, "p_drone_light"), 0.001);
    assertTrue(a.state().fleets.contains(fleet), "Ohne Krieg/Verteidigung bleibt die Flotte unversehrt");
    assertEquals(2, transporterCount(fleet), 0.001);
  }

  @Test
  void landingWithNothingAboardIsRejected() {
    Arena a = newArena();
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 1, 0, null, 0);

    var e = assertThrows(CommandException.class,
        () -> LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId()));
    assertTrue(e.getMessage().contains("Soldaten") || e.getMessage().contains("Drohnen"), e.getMessage());
  }

  @Test
  void activeWarDefenseDestroysTransportersAndProportionalCargo() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    activateDefense(a, 5);
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 10, 1, "p_drone_light", 100);
    putSoldiersAboard(a, fleet, 1000);

    // Gleicher Seed vor UND im Aufruf: dieselbe erste Zufallszahl, damit das erwartete
    // Ergebnis vorab unabhängig nachgerechnet werden kann.
    double roll = Rng.seeded(42).next();
    double capacity = 5 * Formulas.LANDING_DEFENSE_CAPACITY_PER_LEVEL;
    int expectedDestroyed = (int) Math.min(Math.floor(capacity * (0.5 + 0.5 * roll)), 10);
    assertTrue(expectedDestroyed > 0, "Testvoraussetzung verletzt: mit diesem Seed/Level gibt es keinen Verlust");

    GroundForceGroup surface = LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId(), Rng.seeded(42));

    assertEquals(10 - expectedDestroyed, transporterCount(fleet), 0.001,
        "Die Flotte muss genau die berechnete Zahl Transporter verlieren");
    double lostFraction = (double) expectedDestroyed / 10;
    assertEquals(1000 - Math.ceil(1000 * lostFraction), unitCount(surface, GameConstants.SOLDIER_PRODUCT_ID), 0.001);
    assertEquals(100 - Math.ceil(100 * lostFraction), unitCount(surface, "p_drone_light"), 0.001);
    // Adressiert an den KOMMANDANTEN, nicht an seine Heimatwelt
    // (Umsetzungskonzept/34_...md, §J 9).
    assertEquals(1, a.state().notifications.stream()
        .filter(n -> a.attackerId().equals(n.playerId)).count(),
        "Der Angreifer muss über die Landungsabwehr benachrichtigt werden");
    assertEquals(1, a.state().notifications.stream()
        .filter(n -> a.defenderColonyId().equals(n.colonyId)
            && n.code == Notifications.CODE_LANDING_INTERCEPTED).count(),
        "Der Verteidiger erfährt es an der angegriffenen Kolonie");
  }

  @Test
  void inactiveDefenseDoesNotFireEvenAtWar() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    Building defense = activateDefense(a, 10);
    defense.activationState = DefenseActivationState.Inactive;
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 3, 0, null, 0);
    putSoldiersAboard(a, fleet, 100);

    LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId());
    assertEquals(3, transporterCount(fleet), 0.001, "Inaktive Verteidigung darf nicht feuern");
  }

  @Test
  void peaceMeansNoDefenseFireDespiteActiveDefense() {
    Arena a = newArena();
    activateDefense(a, 10); // kein Krieg erklärt
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 3, 0, null, 0);
    putSoldiersAboard(a, fleet, 100);

    LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId());
    assertEquals(3, transporterCount(fleet), 0.001, "Ohne Kriegszustand darf die Verteidigung nicht feuern");
  }

  @Test
  void totalTransporterLossLeavesNoGhostGroup() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    // Kapazität so hoch, dass selbst der ungünstigste Zufallswurf (0.5-Faktor) den einzigen
    // Transporter noch abschießt – das Ergebnis ist damit unabhängig vom Seed.
    activateDefense(a, 100);
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 1, 0, null, 0);
    putSoldiersAboard(a, fleet, 100);

    GroundForceGroup surface = LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId());

    assertEquals(0, transporterCount(fleet), 0.001, "Testvoraussetzung: der einzige Transporter muss verloren gehen");
    assertEquals(null, surface, "Bei Totalverlust darf keine (leere) Boden-Gruppe entstehen");
    assertTrue(a.state().groundForceGroups.stream().noneMatch(g -> a.attackerId().equals(g.ownerId) && a.defenderPlanetId().equals(g.planetId)),
        "Keine Geister-Gruppe auf dem Zielplaneten");
  }

  @Test
  void moveToAForeignColonyOnTheSamePlanetIsRejected() {
    Arena a = newArena();
    Fleet fleet = fleetInOrbit(a, a.defenderPlanetId(), a.defenderSystemId(), 1, 0, null, 0);
    putSoldiersAboard(a, fleet, 50);
    GroundForceGroup surface = LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.defenderPlanetId());

    var e = assertThrows(CommandException.class,
        () -> LandingCommands.moveGroundForces(a.state(), a.attackerId(), surface.id, a.defenderColonyId()));
    assertTrue(e.getMessage().contains("eigene"), e.getMessage());
  }

  @Test
  void moveToAnOwnColonyArrivesInTheGarrisonAfterOneCombatTick() {
    Arena a = newArena();
    // Landung auf dem EIGENEN Heimatplaneten – realistischer Fall: Verstärkung der eigenen Kolonie.
    Fleet fleet = fleetInOrbit(a, a.attackerPlanetId(),
        ColonyCommands.colony(a.state(), a.attackerColonyId()).systemId, 1, 1, "p_drone_light", 20);
    putSoldiersAboard(a, fleet, 150);
    GroundForceGroup surface = LandingCommands.land(a.state(), a.ids(), a.attackerId(), fleet.id, a.attackerPlanetId());

    double garrisonSoldiersBefore = garrisonCount(a, a.attackerColonyId(), GameConstants.SOLDIER_PRODUCT_ID);
    double garrisonDronesBefore = garrisonCount(a, a.attackerColonyId(), "p_drone_light");

    LandingCommands.moveGroundForces(a.state(), a.attackerId(), surface.id, a.attackerColonyId());
    // Vor Ablauf des Kampftakts ist noch nichts angekommen.
    GameEvents.runDue(a.state(), a.ids(), surface.moveCompletesAt - 1);
    assertTrue(a.state().groundForceGroups.contains(surface), "Vor Fälligkeit darf der Verband noch nicht ankommen");

    GameEvents.runDue(a.state(), a.ids(), surface.moveCompletesAt);
    assertTrue(a.state().groundForceGroups.stream().noneMatch(g -> g.id.equals(surface.id)),
        "Der gelandete Verband muss nach Ankunft verschwinden");
    assertEquals(garrisonSoldiersBefore + 150, garrisonCount(a, a.attackerColonyId(), GameConstants.SOLDIER_PRODUCT_ID), 0.001);
    assertEquals(garrisonDronesBefore + 20, garrisonCount(a, a.attackerColonyId(), "p_drone_light"), 0.001);
  }

  private static double garrisonCount(Arena a, String colonyId, String unitProductTypeId) {
    GroundForceGroup garrison = RecruitmentCommands.groundForces(a.state(), colonyId);
    return garrison == null ? 0 : unitCount(garrison, unitProductTypeId);
  }
}
