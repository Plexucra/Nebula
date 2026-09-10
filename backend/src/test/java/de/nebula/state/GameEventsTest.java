package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetStatus;
import de.nebula.model.PendingBuildingOrder;
import de.nebula.model.PlayerRole;
import de.nebula.model.ScheduledEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Ereignisplaner ersetzt die Tick-Schleife: Fälligkeiten werden dort
 * geplant, wo sie entstehen, ein Ereignis je Typ und Ziel, veraltete
 * Ereignisse werden verworfen, wiederkehrende Aufgaben planen sich selbst.
 */
class GameEventsTest {

  private record World(GameState state, IdGenerator ids, String playerId, Colony home) {
  }

  private static World newWorld() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Tester", "Heimat", ids, PlayerRole.Normal, null), ids);
    return new World(state, ids, state.players.get(0).id, state.colonies.get(0));
  }

  @Test
  void oneEventPerTypeAndTarget_rescheduleReplacesTheOldOne() {
    GameState state = new GameState();
    GameEvents.schedule(state, GameEventType.FLEET_ARRIVED, "flt_1", 1000);
    GameEvents.schedule(state, GameEventType.FLEET_ARRIVED, "flt_1", 2000);
    GameEvents.schedule(state, GameEventType.FLEET_ARRIVED, "flt_2", 1500);

    assertEquals(2, state.events.size(), "Eine Neuplanung ersetzt die alte Fälligkeit desselben Ziels");
    assertEquals(2000L, GameEvents.scheduledAt(state, GameEventType.FLEET_ARRIVED, "flt_1"));
    GameEvents.cancel(state, GameEventType.FLEET_ARRIVED, "flt_2");
    assertNull(GameEvents.scheduledAt(state, GameEventType.FLEET_ARRIVED, "flt_2"));
    assertEquals(1, state.events.size());
  }

  @Test
  void eventsFireInTimeOrderAndOnlyWhenDue() {
    GameState state = new GameState();
    GameEvents.schedule(state, GameEventType.TREATY_ENDED, "b", 300);
    GameEvents.schedule(state, GameEventType.TREATY_ENDED, "a", 100);
    GameEvents.schedule(state, GameEventType.TREATY_ENDED, "c", 200);
    List<ScheduledEvent> order = List.copyOf(state.events);
    assertEquals(List.of("a", "c", "b"), order.stream().map(e -> e.targetId).toList());

    // Ohne Kommandanten plant runDue nur die wiederkehrenden Aufgaben ein und feuert
    // die fälligen Fremdziele – die Behandler finden keinen Vertrag und tun nichts.
    GameEvents.runDue(state, new IdGenerator(), 150);
    assertNull(GameEvents.scheduledAt(state, GameEventType.TREATY_ENDED, "a"), "Fälliges Ereignis ist abgearbeitet");
    assertNotNull(GameEvents.scheduledAt(state, GameEventType.TREATY_ENDED, "c"), "Noch nicht fällig");
  }

  @Test
  void recurringJobsScheduleThemselvesAndTheColonyDayRunsOncePerGameDay() {
    World w = newWorld();
    long t0 = Clock.now();
    GameEvents.runDue(w.state(), w.ids(), t0);
    Long colonyDay = GameEvents.scheduledAt(w.state(), GameEventType.COLONY_DAY, w.home().id);
    assertNotNull(colonyDay, "Der Kolonietag ist je Kolonie als wiederkehrendes Ereignis geplant");
    assertEquals(w.home().foundedAt + (long) GameConstants.GAME_DAY_MS, colonyDay,
        "… einen Spieltag nach der Gründung, die Gründungszeit ist die Tageszeit der Kolonie");
    assertNotNull(GameEvents.scheduledAt(w.state(), GameEventType.STATS_SNAPSHOT, ""));
    assertNotNull(GameEvents.scheduledAt(w.state(), GameEventType.RETENTION_CLEANUP, ""));
    assertEquals(t0 + (long) GameConstants.GAME_DAY_MS,
        GameEvents.scheduledAt(w.state(), GameEventType.WEALTH_REDISTRIBUTION, ""),
        "Der Ausgleichsfonds zieht erstmals nach einem Spieltag ein");
    assertFalse(w.state().universeStats.isEmpty(), "Die erste Momentaufnahme entsteht sofort");

    // Nach dem Kolonietag ist der nächste relativ zur Fälligkeit geplant, nicht zur Abarbeitung.
    GameEvents.runDue(w.state(), w.ids(), colonyDay + 5_000);
    assertEquals(colonyDay + (long) GameConstants.GAME_DAY_MS,
        GameEvents.scheduledAt(w.state(), GameEventType.COLONY_DAY, w.home().id));
  }

  @Test
  void aCancelledBuildOrderLeavesAStaleEventThatIsIgnored() {
    World w = newWorld();
    Building industry = w.state().buildings.stream()
        .filter(b -> b.colonyId.equals(w.home().id) && b.typeId.equals("b_industry")).findFirst().orElseThrow();
    int levelBefore = industry.level;
    long completesAt = Clock.now() + 60_000;
    PendingBuildingOrder order = new PendingBuildingOrder();
    order.targetLevel = levelBefore + 1;
    order.startedAt = Clock.now();
    order.completesAt = completesAt;
    industry.pendingOrder = order;
    GameEvents.schedule(w.state(), GameEventType.BUILDING_COMPLETED, industry.id, completesAt);

    // Abbruch nimmt die Planung zurück ...
    BuildingCommands.cancelBuildingOrder(w.state(), w.ids(), w.playerId(), w.home().id, industry.id);
    assertNull(industry.pendingOrder);
    assertNull(GameEvents.scheduledAt(w.state(), GameEventType.BUILDING_COMPLETED, industry.id));

    // ... und selbst ein liegen gebliebenes Ereignis täte nichts, weil das Gebäude
    // keine Fälligkeit mehr trägt.
    GameEvents.schedule(w.state(), GameEventType.BUILDING_COMPLETED, industry.id, completesAt);
    GameEvents.fireNow(w.state(), w.ids(), GameEventType.BUILDING_COMPLETED, industry.id);
    assertEquals(levelBefore, industry.level, "Ein veraltetes Ereignis darf den Ausbau nicht nachträglich vollenden");

    // Mit gültiger Fälligkeit vollendet das Ereignis den Ausbau.
    industry.pendingOrder = order;
    GameEvents.schedule(w.state(), GameEventType.BUILDING_COMPLETED, industry.id, completesAt);
    GameEvents.fireNow(w.state(), w.ids(), GameEventType.BUILDING_COMPLETED, industry.id);
    assertEquals(levelBefore + 1, industry.level);
    assertNull(industry.pendingOrder);
  }

  @Test
  void aFleetHopIsScheduledWhereTheFlightStartsAndArrivesExactlyThen() {
    World w = newWorld();
    Fleet fleet = w.state().fleets.stream().filter(f -> f.ownerId.equals(w.playerId())).findFirst().orElseThrow();
    String neighbour = GatewayCommands.gateway(w.state(), fleet.systemId).reachableSystemIds.get(0);
    FleetCommands.moveFleet(w.state(), w.playerId(), fleet.id, neighbour);
    assertEquals(FleetStatus.InTransit, fleet.status);
    assertEquals(fleet.arrivesAt, GameEvents.scheduledAt(w.state(), GameEventType.FLEET_ARRIVED, fleet.id));

    GameEvents.runDue(w.state(), w.ids(), fleet.arrivesAt - 1);
    assertEquals(FleetStatus.InTransit, fleet.status, "Eine Millisekunde vor der Ankunft passiert nichts");
    GameEvents.runDue(w.state(), w.ids(), fleet.arrivesAt);
    assertEquals(FleetStatus.Stationed, fleet.status);
    assertEquals(neighbour, fleet.systemId);
    assertNull(GameEvents.scheduledAt(w.state(), GameEventType.FLEET_ARRIVED, fleet.id));
  }

  @Test
  void gameClockOffsetShiftsNowButNotDurations() {
    long before = Clock.now();
    try {
      Clock.setGameNow(before - 8 * 3_600_000L);
      assertTrue(Clock.now() < before - 8 * 3_600_000L + 5_000, "Die Spieluhr geht acht Stunden nach");
      assertTrue(Clock.offsetMs() > 8 * 3_600_000L - 5_000);
      assertEquals(Clock.hoursToMs(1), Clock.hoursToMs(1), "Dauern hängen nicht am Versatz");
      assertTrue(Math.abs(Clock.realNow() - System.currentTimeMillis()) < 1_000, "Die Realuhr bleibt die Wanduhr");
    } finally {
      Clock.setGameNow(System.currentTimeMillis());
    }
  }
}
