package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.engine.Graph;
import de.nebula.model.Building;
import de.nebula.model.Colonization;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetStatus;
import de.nebula.model.FleetSystemTarget;
import de.nebula.model.PlanetStats;
import de.nebula.model.Planet;
import de.nebula.model.Population;
import de.nebula.model.ShipyardQueueEntry;
import de.nebula.model.StarSystem;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die GANZE Kolonisationsreise über die echten Befehle, nicht über handgesetzten
 * Zustand: Kolonisationsschiff in der Werft bauen → aus dem Lager in eine Flotte
 * übergeben → betanken → per Gateway ins Nachbarsystem springen → dort in den
 * Orbit eines besiedelbaren Planeten wechseln → kolonisieren → Spieltag ablaufen
 * lassen.
 *
 * <p>Ergänzt {@link ColonyCommandsUsabilityTest} (prüft die Landeregeln mit einer
 * bereits im Orbit gesetzten Flotte) und {@link ShipyardColonyShipTest} (prüft den
 * Bau): die Übergänge DAZWISCHEN – Lager → Flotte → Reise → Orbit – waren bislang
 * von keinem Test berührt, und genau dort entscheidet sich, ob ein Kommandant
 * einen ANDEREN Planeten überhaupt erreichen kann.</p>
 */
class ColonizationJourneyTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId, String colonyId) {
  }

  private static Bootstrapped newStateWithStockedShipyard() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    String playerId = state.players.get(0).id;
    String colonyId = state.players.get(0).homeworldColonyId;

    Building shipyard = new Building();
    shipyard.id = ids.next("bld");
    shipyard.colonyId = colonyId;
    shipyard.typeId = "b_shipyard";
    shipyard.level = 1;
    state.buildings.add(shipyard);

    // Baubedingungen des Schiffs (eigener Test: ShipyardColonyShipTest) – hier nur
    // Vorbedingung, damit die REISE geprüft werden kann.
    for (PlanetStats s : state.planetStats) if (s.colonyId.equals(colonyId)) s.loyaltyPct = 100;
    population(state, colonyId).currentCount = 4 * GameConstants.START_POPULATION;
    GameQueries.findWallet(state, WalletOwnerType.Player, playerId).balance =
        10 * ShipyardCommands.colonistPremiumPerShip();
    for (var input : ProductCatalog.find(GameConstants.COLONY_SHIP_PRODUCT_ID).recipe) {
      Warehouse.add(state, colonyId, input.inputProductTypeId, input.quantity);
    }
    return new Bootstrapped(state, ids, playerId, colonyId);
  }

  private static Population population(GameState state, String colonyId) {
    return state.populations.stream().filter(p -> p.colonyId.equals(colonyId)).findFirst().orElseThrow();
  }

  /** Baut EIN Kolonisationsschiff fertig und liefert den Zeitpunkt der Fertigstellung. */
  private static long buildColonyShip(Bootstrapped b) {
    ShipyardCommands.queueShip(b.state(), b.ids(), b.playerId(), b.colonyId(),
        GameConstants.COLONY_SHIP_PRODUCT_ID, 1, false, false);
    ShipyardQueueEntry entry = b.state().shipyardQueue.get(0);
    ShipyardCommands.processShipyardCompletions(b.state(), b.ids(), entry.endsAt);
    assertEquals(1, Warehouse.qty(b.state(), b.colonyId(), GameConstants.COLONY_SHIP_PRODUCT_ID), 0.001,
        "Vorbedingung: das Schiff muss im Lager der Bau-Kolonie liegen");
    return entry.endsAt;
  }

  private static Fleet onlyColonyFleet(Bootstrapped b) {
    return b.state().fleets.stream()
        .filter(f -> f.ownerId.equals(b.playerId()))
        .filter(f -> f.ships.stream().anyMatch(g ->
            GameConstants.COLONY_SHIP_PRODUCT_ID.equals(g.shipProductTypeId) && g.quantity > 0))
        .findFirst().orElseThrow(() -> new AssertionError("Keine Flotte mit Kolonisationsschiff gefunden"));
  }

  private static StarSystem homeSystem(GameState state) {
    return state.systems.stream().filter(s -> s.isHomeSystem).findFirst().orElseThrow();
  }

  /** Ein besiedelbarer Planet im System {@code systemId}, auf dem noch keine Kolonie steht. */
  private static Planet freeUsablePlanet(GameState state, String systemId) {
    return state.planets.stream()
        .filter(p -> p.systemId.equals(systemId) && p.usable)
        .filter(p -> state.colonies.stream().noneMatch(c -> c.planetId.equals(p.id)))
        .findFirst().orElseThrow(() -> new AssertionError("Kein freier besiedelbarer Planet in " + systemId));
  }

  /**
   * Der kurze Weg: Nachbarplanet im EIGENEN System. Zeigt, dass ein frisch
   * gebautes Schiff ohne jeden Handgriff am Zustand tatsächlich zu einer zweiten
   * Kolonie führt.
   */
  @Test
  void aFreshlyBuiltShipColonizesANeighbourPlanetInTheHomeSystem() {
    Bootstrapped b = newStateWithStockedShipyard();
    buildColonyShip(b);

    FleetCommands.transferShipsToFleet(b.state(), b.ids(), b.playerId(), b.colonyId(),
        GameConstants.COLONY_SHIP_PRODUCT_ID, 1, null);
    Fleet fleet = onlyColonyFleet(b);
    assertEquals(0, Warehouse.qty(b.state(), b.colonyId(), GameConstants.COLONY_SHIP_PRODUCT_ID), 0.001,
        "Das Schiff verlässt beim Flottenübergabe-Befehl das Lager");
    assertEquals(FleetStatus.Stationed, fleet.status);

    Planet target = freeUsablePlanet(b.state(), homeSystem(b.state()).id);
    FleetCommands.moveFleetWithinSystem(b.state(), b.ids(), b.playerId(), fleet.id,
        new FleetSystemTarget.PlanetOrbit(target.id));
    assertEquals(FleetLocationType.PlanetOrbit, fleet.locationType);
    assertEquals(target.id, fleet.locationPlanetId);

    Colonization running = ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), target.id);
    ColonyCommands.processColonizations(b.state(), b.ids(), running.endsAt);

    Colony colony = b.state().colonies.stream()
        .filter(c -> c.planetId.equals(target.id)).findFirst().orElse(null);
    assertNotNull(colony, "Nach Ablauf des Spieltags muss die zweite Kolonie stehen");
    assertEquals(b.playerId(), colony.ownerId);
    assertEquals(GameConstants.START_POPULATION, population(b.state(), colony.id).currentCount, 0.001);
    assertNotNull(GameQueries.findWallet(b.state(), WalletOwnerType.Population, colony.id),
        "Die neue Kolonie braucht ein Bevölkerungs-Wallet, sonst kann sie nichts kaufen");
    assertEquals(
        List.of("b_habitat:1", "b_industry:1", GameConstants.INFRASTRUCTURE_BUILDING_ID + ":2"),
        b.state().buildings.stream().filter(x -> x.colonyId.equals(colony.id))
            .map(x -> x.typeId + ":" + x.level).sorted().toList(),
        "Startbebauung der neuen Kolonie");
    assertTrue(b.state().colonizations.isEmpty());
  }

  /**
   * Der eigentliche Zweck des Schiffs: ein ANDERES System. Geprüft wird die
   * vollständige Kette inklusive Betanken und echtem Gateway-Sprung – ein
   * Kolonisationsschiff ist unbewaffnet und reist allein, deshalb muss genau
   * diese Ein-Schiff-Flotte fliegen können.
   */
  @Test
  void aColonyShipCanJumpToANeighbourSystemAndFoundAColonyThere() {
    Bootstrapped b = newStateWithStockedShipyard();
    buildColonyShip(b);
    FleetCommands.transferShipsToFleet(b.state(), b.ids(), b.playerId(), b.colonyId(),
        GameConstants.COLONY_SHIP_PRODUCT_ID, 1, null);
    Fleet fleet = onlyColonyFleet(b);

    String home = homeSystem(b.state()).id;
    String neighbour = neighbourSystem(b.state(), home);

    // Treibstoff aus dem Startvorrat der Heimatkolonie in den Flottentank. Ein
    // Sprung kostet nach Masse (Umsetzungskonzept/34_...md, F7) – das
    // Kolonisationsschiff ist schwer, genau dafür ist der Startvorrat bemessen.
    double needed = Math.ceil(FleetCommands.jumpFuelPerHop(fleet));
    double fuelBefore = Warehouse.qty(b.state(), b.colonyId(), GameConstants.JUMP_FUEL_PRODUCT_ID);
    assertTrue(fuelBefore >= needed, "Vorbedingung: Startvorrat trägt einen Sprung, brauchte " + needed);
    FleetCommands.refuelFleet(b.state(), b.playerId(), fleet.id, needed);

    FleetCommands.moveFleet(b.state(), b.playerId(), fleet.id, neighbour);
    assertEquals(FleetStatus.InTransit, fleet.status);
    FleetCommands.processFleetArrivals(b.state(), b.ids(), fleet.arrivesAt);
    assertEquals(FleetStatus.Stationed, fleet.status, "Nach dem Sprung muss die Flotte stationiert sein");
    assertEquals(neighbour, fleet.systemId);

    Planet target = freeUsablePlanet(b.state(), neighbour);
    FleetCommands.moveFleetWithinSystem(b.state(), b.ids(), b.playerId(), fleet.id,
        new FleetSystemTarget.PlanetOrbit(target.id));

    Colonization running = ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), target.id);
    assertEquals(neighbour, running.systemId);
    ColonyCommands.processColonizations(b.state(), b.ids(), running.endsAt);

    Colony colony = b.state().colonies.stream()
        .filter(c -> c.planetId.equals(target.id)).findFirst().orElse(null);
    assertNotNull(colony, "Die Kolonie im Nachbarsystem muss entstehen");
    assertEquals(neighbour, colony.systemId);
    assertEquals(2, ColonyCommands.coloniesOf(b.state(), b.playerId()).size(),
        "Heimatwelt + neue Kolonie");
  }

  /**
   * Nach der Landung bleibt weder ein Geisterschiff noch eine schiffslose
   * Geisterflotte übrig: eine reine Kolonisationsflotte hat nach dem Verbrauch
   * ihres einzigen Schiffs nichts mehr, was sie sein könnte.
   */
  @Test
  void theEmptyFleetIsRemovedAfterLanding() {
    Bootstrapped b = newStateWithStockedShipyard();
    buildColonyShip(b);
    FleetCommands.transferShipsToFleet(b.state(), b.ids(), b.playerId(), b.colonyId(),
        GameConstants.COLONY_SHIP_PRODUCT_ID, 1, null);
    Fleet fleet = onlyColonyFleet(b);
    Planet target = freeUsablePlanet(b.state(), homeSystem(b.state()).id);
    FleetCommands.moveFleetWithinSystem(b.state(), b.ids(), b.playerId(), fleet.id,
        new FleetSystemTarget.PlanetOrbit(target.id));

    ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), target.id);

    assertTrue(b.state().fleets.stream().noneMatch(f -> f.id.equals(fleet.id)),
        "Die leere Flotte muss verschwinden");
    assertTrue(FleetCommands.fleetsOf(b.state(), b.playerId()).stream()
            .noneMatch(f -> f.ships.isEmpty() || f.ships.stream().allMatch(g -> g.quantity <= 0)),
        "Keine schiffslose Flotte in der Übersicht");
  }

  /**
   * Gegenprobe: eine gemischte Flotte überlebt die Landung – nur das
   * Kolonisationsschiff verschwindet, die übrigen Schiffe bleiben mit ihrem
   * Treibstoff an Ort und Stelle.
   */
  @Test
  void aMixedFleetKeepsItsOtherShipsAfterLanding() {
    Bootstrapped b = newStateWithStockedShipyard();
    buildColonyShip(b);
    Warehouse.add(b.state(), b.colonyId(), "p_corvette", 2);
    FleetCommands.transferShipsToFleet(b.state(), b.ids(), b.playerId(), b.colonyId(),
        GameConstants.COLONY_SHIP_PRODUCT_ID, 1, null);
    Fleet fleet = onlyColonyFleet(b);
    FleetCommands.transferShipsToFleet(b.state(), b.ids(), b.playerId(), b.colonyId(),
        "p_corvette", 2, fleet.id);
    FleetCommands.refuelFleet(b.state(), b.playerId(), fleet.id, 2);

    Planet target = freeUsablePlanet(b.state(), homeSystem(b.state()).id);
    FleetCommands.moveFleetWithinSystem(b.state(), b.ids(), b.playerId(), fleet.id,
        new FleetSystemTarget.PlanetOrbit(target.id));
    ColonyCommands.colonizePlanet(b.state(), b.ids(), b.playerId(), target.id);

    assertTrue(b.state().fleets.stream().anyMatch(f -> f.id.equals(fleet.id)), "Die Flotte bleibt bestehen");
    assertEquals(List.of("p_corvette:2.0"),
        fleet.ships.stream().map(g -> g.shipProductTypeId + ":" + g.quantity).toList());
    assertEquals(2, fleet.fuelCapsules, 0.001, "Der Treibstoff der verbliebenen Schiffe bleibt im Tank");
  }

  /** Ein Gateway-Nachbarsystem der Heimat, das mindestens einen freien besiedelbaren Planeten hat. */
  private static String neighbourSystem(GameState state, String home) {
    for (Graph.Route r : GatewayCommands.gatewayRoutes(state)) {
      String other = r.a().equals(home) ? r.b() : r.b().equals(home) ? r.a() : null;
      if (other == null) continue;
      boolean hasFreeUsable = state.planets.stream()
          .anyMatch(p -> p.systemId.equals(other) && p.usable
              && state.colonies.stream().noneMatch(c -> c.planetId.equals(p.id)));
      if (hasFreeUsable) return other;
    }
    throw new AssertionError("Kein Gateway-Nachbarsystem mit freiem besiedelbarem Planeten");
  }
}
