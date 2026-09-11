package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO 11.9.2026: Produktionsaufträge unter {@link GameConstants#MIN_PRODUCTION_ORDER_GAME_HOURS}
 * (10 Spielminuten) werden nicht angenommen – Schutz vor einer Flut von Fertigstellungsereignissen.
 * Die Ablehnung nennt die Mindeststückzahl; ein wartender Auftrag, der beim Start darunter fällt,
 * wird gestoppt (Code 504), und die Warteschlange läuft mit dem nächsten weiter.
 */
class MinimumOrderDurationTest {

  private static final double MIN = GameConstants.MIN_PRODUCTION_ORDER_GAME_HOURS;
  private static final String FERRO = "p_ferrometall";

  private record Bootstrapped(GameState state, IdGenerator ids, String colonyId, String playerId) {
  }

  private static Bootstrapped newColony() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    return new Bootstrapped(state, ids, state.colonies.get(0).id, state.players.get(0).id);
  }

  /** Wie {@link #newColony()}, aber ohne Startaufträge – der nächste Auftrag läuft sofort. */
  private static Bootstrapped emptyQueue() {
    Bootstrapped b = newColony();
    b.state().productionQueue.clear();
    return b;
  }

  private static void setIndustryLevel(Bootstrapped b, int level) {
    b.state().buildings.stream()
        .filter(x -> x.colonyId.equals(b.colonyId()) && x.typeId.equals("b_industry"))
        .findFirst().orElseThrow().level = level;
  }

  private static double hours(Bootstrapped b, String productTypeId, double quantity) {
    return ChainPlanner.planChain(b.state(), b.colonyId(), productTypeId, quantity, "b_industry").totalHours;
  }

  private static ProductionQueueEntry entry(Bootstrapped b, int index) {
    return ProductionCommands.productionQueueFor(b.state(), b.colonyId()).get(index);
  }

  @Test
  void tooSmallOrderIsRejectedAndTheMessageNamesTheExactMinimum() {
    Bootstrapped b = emptyQueue();
    double n = ProductionCommands.minimumProductionQuantity(b.state(), b.colonyId(), FERRO);
    assertTrue(n > 1, "Ein Stück Ferrometall muss unter der Mindestdauer liegen, sonst prüft der Test nichts");
    assertTrue(hours(b, FERRO, n) >= MIN, "Die Mindestmenge muss die Mindestdauer erreichen");
    assertTrue(hours(b, FERRO, n - 1) < MIN, "Ein Stück weniger darf sie nicht erreichen – sonst ist es nicht die Mindestmenge");

    CommandException e = assertThrows(CommandException.class,
        () -> ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, n - 1, true, false, false));
    assertTrue(e.getMessage().contains("Mindestens " + (long) n + " Stück"), e.getMessage());
    assertTrue(e.getMessage().contains("ineffizient"), e.getMessage());
    assertTrue(b.state().productionQueue.isEmpty(), "Ein abgelehnter Auftrag darf nicht in der Warteschlange landen");

    ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, n, true, false, false);
    assertEquals(ProductionQueueStatus.running, entry(b, 0).status);
  }

  @Test
  void raiseToMinimumLiftsTheOrderInsteadOfRejectingIt() {
    Bootstrapped b = emptyQueue();
    double n = ProductionCommands.minimumProductionQuantity(b.state(), b.colonyId(), FERRO);
    ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, 1, true, false, true);
    assertTrue(entry(b, 0).quantity > n, "Mit Puffer über der knappen Mindestmenge " + n + ": " + entry(b, 0).quantity);
    assertTrue(entry(b, 0).plan.totalHours >= MIN * ProductionCommands.SYSTEM_RAISE_HEADROOM);
  }

  @Test
  void bundleIsRaisedInProportion() {
    Bootstrapped b = emptyQueue();
    Map<String, Double> demand = new LinkedHashMap<>();
    demand.put("p_leitermetall", 1.0);
    demand.put("p_leiterbuendel", 1.0);
    Map<String, Double> queued = ProductionCommands.queueProductionBundleCore(b.state(), b.ids(), b.colonyId(), demand, true, false, true);
    assertTrue(queued.get("p_leitermetall") > 1, "Das Bündel muss angehoben worden sein: " + queued);
    assertEquals(queued.get("p_leitermetall"), queued.get("p_leiterbuendel"), "Beide Baustoffe im gleichen Verhältnis");
    assertEquals(queued, entry(b, 0).bundledProducts);
    assertTrue(entry(b, 0).plan.totalHours >= MIN);
  }

  @Test
  void starterOrdersAreRaisedToTheMinimum() {
    Bootstrapped b = newColony();
    for (ProductionQueueEntry e : ProductionCommands.productionQueueFor(b.state(), b.colonyId())) {
      assertTrue(e.status != ProductionQueueStatus.stopped, "Startauftrag " + e.productTypeId + " darf nicht gestoppt sein");
      assertTrue(hours(b, e.productTypeId, e.quantity) >= MIN || e.status == ProductionQueueStatus.running,
          "Startauftrag " + e.productTypeId + " × " + e.quantity + " liegt unter der Mindestdauer");
    }
    ProductionQueueEntry running = entry(b, 0);
    assertEquals(ProductionQueueStatus.running, running.status);
    assertTrue(running.plan.totalHours >= MIN, "Der laufende Startauftrag muss die Mindestdauer erreichen");
  }

  /**
   * Browsertest 11.9.2026: genau auf die Mindestmenge angehoben, fielen die wartenden
   * Startaufträge vor ihrem Start wieder darunter (Bevölkerung und Spezialisierung wachsen) –
   * ein neuer Kommandant fand zwei gestoppte Daueraufträge vor.
   */
  @Test
  void starterOrdersSurviveTheFirstGameDays() {
    Bootstrapped b = newColony();
    GameEvents.runDue(b.state(), b.ids(), Clock.now() + (long) Clock.hoursToMs(48));
    for (ProductionQueueEntry e : ProductionCommands.productionQueueFor(b.state(), b.colonyId())) {
      assertTrue(e.stoppedReasonCode == null || e.stoppedReasonCode != Notifications.CODE_ORDER_TOO_SMALL,
          "Startauftrag " + e.productTypeId + " × " + e.quantity + " ist als zu klein gestoppt");
    }
  }

  @Test
  void queuedOrderThatFellBelowTheMinimumIsStoppedAndTheQueueMovesOn() {
    Bootstrapped b = emptyQueue();
    double n = ProductionCommands.minimumProductionQuantity(b.state(), b.colonyId(), FERRO);
    ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, n * 10, true, false, false);
    ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, n, true, false, false);
    ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, n * 1000, true, false, false);
    ProductionQueueEntry first = entry(b, 0);
    ProductionQueueEntry small = entry(b, 1);
    ProductionQueueEntry big = entry(b, 2);
    assertEquals(ProductionQueueStatus.queued, small.status);

    // Beim Einreihen reichte n gerade – nach dem Ausbau des Industriekomplexes nicht mehr.
    setIndustryLevel(b, 50);
    ProductionCommands.cancelProduction(b.state(), b.ids(), b.playerId(), b.colonyId(), first.id);

    assertEquals(ProductionQueueStatus.stopped, small.status);
    assertEquals(Notifications.CODE_ORDER_TOO_SMALL, small.stoppedReasonCode);
    assertEquals(ProductionQueueStatus.running, big.status, "Der zu kleine Auftrag darf die Warteschlange nicht anhalten");
    assertTrue(b.state().notifications.stream().anyMatch(x -> x.code == Notifications.CODE_ORDER_TOO_SMALL
        && x.message.contains("Mindestens")), "Benachrichtigung mit Mindestmenge fehlt");

    CommandException e = assertThrows(CommandException.class,
        () -> ProductionCommands.resumeProduction(b.state(), b.ids(), b.playerId(), b.colonyId(), small.id));
    assertTrue(e.getMessage().contains("abbrechen"), e.getMessage());
    assertEquals(ProductionQueueStatus.stopped, small.status);
  }

  @Test
  void standingOrderGrowsWhenItsNextRoundWouldFallBelowTheMinimum() {
    Bootstrapped b = emptyQueue();
    double n = ProductionCommands.minimumProductionQuantity(b.state(), b.colonyId(), FERRO);
    ProductionCommands.queueProductionCore(b.state(), b.ids(), b.colonyId(), FERRO, n, true, true, false);
    ProductionQueueEntry standing = entry(b, 0);

    setIndustryLevel(b, 50);
    GameEvents.fireNow(b.state(), b.ids(), GameEventType.PRODUCTION_COMPLETED, standing.id);

    ProductionQueueEntry next = entry(b, 0);
    assertTrue(next.quantity > n, "Der Dauerauftrag muss mitwachsen (" + n + " → " + next.quantity + ")");
    assertEquals(ProductionQueueStatus.running, next.status);
    assertTrue(next.plan.totalHours >= MIN);
  }
}
