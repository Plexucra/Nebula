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
 * Verifiziert den Eleriumkapsel-Sprungtreibstoff (Nutzervorgabe, siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §9.4): 0,01 Kapseln je Schiff und
 * Gateway-Sprung, Startvorrat 10, Entnahme aus den Kolonielagern des Flottenbesitzers
 * (Heimatkolonie zuerst), Ablehnung des Sprungs bei zu wenig Kapseln.
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
  void newCommanderStartsWithTenJumpFuelCapsulesAtHomeColony() {
    Bootstrapped b = newBootstrappedState();
    assertEquals(10.0, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID));
  }

  /**
   * Seit Umsetzungskonzept/25_...md wird Treibstoff nur in GANZEN Kapseln
   * abgebucht: ein Schiff auf einem Sprung kostet 0,01 Kapseln, das sammelt sich
   * im Übertragskonto und wird erst beim hundertsten Sprung fällig. Ein einzelner
   * Flug darf das Lager deshalb nicht anrühren.
   */
  @Test
  void singleShipSingleHopTakesNothingFromStockButBooksIntoThePot() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    assertEquals(1.0, totalShips(freighter), 0.0001, "Startfrachter sollte genau 1 Schiff sein");
    String destination = neighborOf(b.state(), freighter.systemId);

    double before = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination);
    double after = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);

    assertEquals(before, after, 1e-9, "Ein einzelner Sprung kostet weniger als eine ganze Kapsel");
    assertEquals(GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP,
        FractionPot.pending(b.state(), FractionPot.key("jumpfuel", b.playerId())), 1e-9,
        "…er muss aber im Übertragskonto stehen, damit die Rate langfristig stimmt");
    assertEquals(FleetStatus.InTransit, freighter.status);
    assertEquals(destination, freighter.destinationSystemId);
  }

  /**
   * Der eigentliche Nachweis: über viele Sprünge hinweg wird EXAKT so viel
   * verbraucht wie die Rate vorgibt – die Ganzzahligkeit verschiebt den
   * Verbrauch nur, sie verändert ihn nicht.
   */
  @Test
  void overManyJumpsTheAverageConsumptionMatchesTheRateExactly() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    String destination = neighborOf(b.state(), freighter.systemId);
    Warehouse.add(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID, 100);
    double before = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);

    int jumps = 500;
    for (int i = 0; i < jumps; i++) {
      freighter.status = FleetStatus.Stationed;      // Flotte für den nächsten Sprung zurücksetzen
      freighter.destinationSystemId = null;
      FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination);
    }
    double verbraucht = before - Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    double erwartet = jumps * totalShips(freighter) * GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP;

    assertEquals(erwartet, verbraucht, 1.0, "Langfristiger Verbrauch muss der Rate entsprechen (± eine offene Kapsel)");
    assertEquals(verbraucht, Math.floor(verbraucht), 1e-9, "Abgebucht wurden ausschließlich ganze Kapseln");
  }

  @Test
  void costScalesWithShipCountAndHopCount() {
    Bootstrapped b = newBootstrappedState();
    Fleet combat = fleetNamed(b.state(), b.playerId(), "Kampfflotte Testheim");
    double ships = totalShips(combat);
    assertTrue(ships >= 4 && ships <= 16, "Startkampfflotte: Korvette(2-8)+Zerstörer(1-5)+Kreuzer(1-3)");

    String twoHopsAway = systemAtHopDistance(b.state(), combat.systemId, 2);
    FleetCommands.moveFleet(b.state(), b.playerId(), combat.id, twoHopsAway);

    // Auch hier bleibt der Betrag unter einer ganzen Kapsel und landet im Topf.
    double expected = ships * 2 * GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP;
    assertEquals(expected, FractionPot.pending(b.state(), FractionPot.key("jumpfuel", b.playerId())), 1e-9);
    assertEquals(2, combat.pendingHops.size() + 1, "Route sollte genau 2 Sprünge (1 laufend + 1 pending) umfassen");
  }

  @Test
  void insufficientFuelBlocksTheJumpAndLeavesFleetAndWarehouseUnchanged() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    String destination = neighborOf(b.state(), freighter.systemId);
    String originSystem = freighter.systemId;

    // Lager leeren UND das Übertragskonto so weit füllen, dass dieser Sprung eine
    // ganze Kapsel fällig macht – erst dann kann überhaupt etwas fehlen
    // (Umsetzungskonzept/25_...md).
    double current = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    Warehouse.add(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID, -current);
    assertEquals(0.0, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID));
    b.state().fractionPots.put(FractionPot.key("jumpfuel", b.playerId()), 0.995);

    CommandException ex = assertThrows(CommandException.class,
        () -> FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination));
    assertTrue(ex.getMessage().contains("Eleriumkapseln"), "Fehlermeldung sollte auf Eleriumkapseln hinweisen: " + ex.getMessage());

    assertEquals(FleetStatus.Stationed, freighter.status, "Flotte darf bei abgelehntem Sprung nicht losfliegen");
    assertEquals(originSystem, freighter.systemId);
    assertEquals(0.0, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID), "Lager darf bei Ablehnung nicht negativ oder verändert werden");
  }

  @Test
  void fuelIsDrawnFromHomeColonyFirstThenFromOtherOwnedColonies() {
    Bootstrapped b = newBootstrappedState();
    Fleet freighter = fleetNamed(b.state(), b.playerId(), "Handelsflotte Testheim");
    String destination = neighborOf(b.state(), freighter.systemId);

    // Heimatlager auf GENAU eine Kapsel setzen und das Übertragskonto so füllen,
    // dass dieser Sprung zwei ganze Kapseln fällig macht – eine kann die Heimat
    // decken, für die zweite muss die andere Kolonie einspringen.
    double current = Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID);
    Warehouse.add(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID, 1 - current);
    assertEquals(1.0, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID), 0.0001);
    b.state().fractionPots.put(FractionPot.key("jumpfuel", b.playerId()), 1.995);

    // Zweite eigene Kolonie auf einem der übrigen (immer besiedelbaren) Heimatplaneten gründen
    // und dort zusätzliche Kapseln einlagern.
    Planet secondPlanet = b.state().planets.stream()
        .filter(p -> p.systemId.equals(freighter.systemId) && p.orbitIndex == 1).findFirst().orElseThrow();
    Colony secondColony = foundColonyDirectly(b, secondPlanet);
    Warehouse.add(b.state(), secondColony.id, GameConstants.JUMP_FUEL_PRODUCT_ID, 3.0);

    FleetCommands.moveFleet(b.state(), b.playerId(), freighter.id, destination);

    assertEquals(0.0, Warehouse.qty(b.state(), b.home().id, GameConstants.JUMP_FUEL_PRODUCT_ID), 0.0001,
        "Heimatlager sollte zuerst komplett aufgebraucht werden");
    assertEquals(2.0, Warehouse.qty(b.state(), secondColony.id, GameConstants.JUMP_FUEL_PRODUCT_ID), 0.0001,
        "die zweite fällige Kapsel muss aus der anderen Kolonie kommen");
    assertEquals(FleetStatus.InTransit, freighter.status);
  }

  @Test
  void jumpFuelProductIsTheExistingEleriumkapselProduct() {
    assertEquals("p_elerium_kapsel", GameConstants.JUMP_FUEL_PRODUCT_ID);
    assertEquals(0.01, GameConstants.JUMP_FUEL_PER_SHIP_PER_HOP, 0.0001);
  }

  /**
   * Gründet eine zweite Kolonie ohne den Umweg über Werft und Kolonisationsschiff
   * (Umsetzungskonzept/24_...md): dieser Test braucht nur ein zweites eigenes
   * Lager, nicht den Kolonisationsablauf. Nutzt bewusst denselben öffentlichen
   * Abschlussweg wie der Tick, damit die Kolonie identisch aufgebaut ist.
   */
  private static Colony foundColonyDirectly(Bootstrapped b, Planet planet) {
    de.nebula.model.Colonization request = new de.nebula.model.Colonization();
    request.id = b.ids().next("cln");
    request.planetId = planet.id;
    request.systemId = planet.systemId;
    request.ownerId = b.playerId();
    request.colonyName = planet.name + "-Kolonie";
    request.startedAt = 0;
    request.endsAt = 0;
    b.state().colonizations.add(request);
    ColonyCommands.processColonizations(b.state(), b.ids(), 1);
    return b.state().colonies.stream().filter(c -> c.planetId.equals(planet.id)).findFirst().orElseThrow();
  }
}
