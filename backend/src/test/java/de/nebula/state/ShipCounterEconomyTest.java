package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.model.BattleOutcome;
import de.nebula.model.BattleStatus;
import de.nebula.model.BlockadeAnchor;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.ProductType;
import de.nebula.model.RecipeInput;
import de.nebula.model.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sichert die zentrale Zusage des Konterkreises ab (Mechanik/03_..., §2:
 * "Kontersystem, zyklisch, AUF BASIS BAUKOSTEN"): <b>kein Schiff darf so
 * billig oder so schnell zu bauen sein, dass es seinen eigenen Konter
 * produktionswirtschaftlich schlägt.</b>
 *
 * <p>Das ist keine theoretische Sorge. Vor der Massenskala aus
 * Umsetzungskonzept/27_...md war genau das der Fall: der Kreuzer kostete das
 * 1,9-fache einer Korvette und hatte den 8-fachen Kampfwert, weil
 * {@link Formulas#productionAspect} nur die Endmontage zählt, die tatsächlichen
 * Kosten aber in der Unterkette stecken. Bei gleichem Produktionsbudget verlor
 * die Korvette gegen den Kreuzer, den sie laut Konzeption kontert – der
 * Konterkreis war in dieser Richtung wirkungslos.</p>
 *
 * <p>Der Test kauft beiden Seiten für dasselbe Budget so viele Schiffe wie
 * möglich und lässt {@link BattleCommands} das Gefecht zu Ende rechnen. Er
 * bildet die Schadensformel bewusst NICHT nach, sondern fährt die echten
 * Befehle – eine Änderung an {@code computeSideDamage}, {@code applyDamage}
 * oder an den Kampffaktoren schlägt hier durch.</p>
 */
class ShipCounterEconomyTest {

  /** Korvette schlägt Kreuzer, Kreuzer schlägt Zerstörer, Zerstörer schlägt Korvette (Mechanik/03_..., §2). */
  private static final Map<String, String> COUNTERS = Map.of(
      "p_corvette", "p_cruiser",
      "p_cruiser", "p_destroyer",
      "p_destroyer", "p_corvette");

  /**
   * Budget als Vielfaches der Kosten des teuersten Schiffs. Groß genug, dass
   * die Ganzzahl-Abrundung der Stückzahlen (ein halber Kreuzer ist nichts
   * wert) das Ergebnis nicht mehr verschiebt.
   */
  private static final int BUDGET_IN_MOST_EXPENSIVE_SHIPS = 200;

  /**
   * Gesamte Produktionszeit EINER Einheit samt vollständiger Vorkette, in
   * Spielstunden. Bewusst eine eigene, topologisch korrekte Auflösung statt
   * {@link ChainPlanner#planChain}: der sortiert die Kette allein nach
   * {@code tier} und zählt Rezeptkanten INNERHALB einer Ebene nicht mit
   * (Umsetzungskonzept/27_...md, §F Punkt 2). Für die Kostenfrage hier muss
   * die Kette vollständig sein.
   */
  private static double chainHours(String productTypeId) {
    double hours = 0;
    Deque<Object[]> stack = new ArrayDeque<>();
    stack.push(new Object[]{productTypeId, 1.0});
    while (!stack.isEmpty()) {
      Object[] cur = stack.pop();
      ProductType product = ProductCatalog.find((String) cur[0]);
      double quantity = (double) cur[1];
      hours += quantity * product.baseProductionHours;
      for (RecipeInput input : product.recipe) {
        stack.push(new Object[]{input.inputProductTypeId, quantity * input.quantity});
      }
    }
    return hours;
  }

  private record Arena(GameState state, IdGenerator ids, String attackerId, String defenderId, String systemId) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Angreifer", "Angreiferheim", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, "Verteidiger", "Verteidigerheim", ids), ids);
    String attackerId = state.players.get(0).id;
    String defenderId = state.players.get(1).id;
    // Ein System ohne Handelsgilde-Station: dort ist keine Blockade und damit kein Gefecht möglich.
    StarSystem arena = state.systems.stream().filter(s -> !s.isTradeHub).findFirst().orElseThrow();
    DiplomacyCommands.declareWar(state, ids, attackerId, defenderId);
    return new Arena(state, ids, attackerId, defenderId, arena.id);
  }

  private static Fleet placeFleet(Arena arena, String ownerId, String name, String shipProductTypeId, int quantity) {
    Fleet fleet = new Fleet();
    fleet.id = arena.ids().next("flt");
    fleet.ownerId = ownerId;
    fleet.name = name;
    fleet.locationType = FleetLocationType.System;
    fleet.locationColonyId = null;
    fleet.locationPlanetId = null;
    fleet.systemId = arena.systemId();
    fleet.status = FleetStatus.Stationed;
    fleet.ships = List.of(new FleetShipGroup(shipProductTypeId, quantity));
    fleet.cargo = List.of();
    fleet.fuelCapsules = 0;
    fleet.destinationSystemId = null;
    fleet.pendingHops = List.of();
    fleet.departedAt = null;
    fleet.arrivesAt = null;
    arena.state().fleets.add(fleet);
    return fleet;
  }

  /**
   * Lässt {@code counterType} (Angreifer) gegen {@code targetType} (Verteidiger)
   * antreten, beide für dasselbe Budget gekauft, und gibt das Ergebnis zurück.
   */
  private static BattleOutcome fightAtEqualBudget(String counterType, String targetType, double budgetHours) {
    Arena arena = newArena();
    int counterCount = (int) Math.floor(budgetHours / chainHours(counterType));
    int targetCount = (int) Math.floor(budgetHours / chainHours(targetType));
    assertTrue(counterCount > 0 && targetCount > 0, "Budget zu klein für beide Seiten");

    Fleet attacker = placeFleet(arena, arena.attackerId(), "Konter", counterType, counterCount);
    Fleet defender = placeFleet(arena, arena.defenderId(), "Ziel", targetType, targetCount);
    BlockadeCommands.formBlockade(arena.state(), arena.ids(), arena.defenderId(), defender.id, new BlockadeAnchor.Gateway());
    BattleCommands.engageBattle(arena.state(), arena.ids(), arena.attackerId(), attacker.id, defender.id);

    var battle = BattleCommands.activeBattleForFleet(arena.state(), attacker.id);
    assertNotNull(battle, "Gefecht wurde nicht eröffnet");
    long t = Clock.now();
    for (int tick = 0; tick < 1000 && battle.status == BattleStatus.Active; tick++) {
      t += (long) Clock.hoursToMs(Formulas.COMBAT_TICK_HOURS);
      BattleCommands.processBattles(arena.state(), arena.ids(), t);
    }
    assertEquals(BattleStatus.Ended, battle.status, "Gefecht kam in 1000 Ticks zu keinem Ende");
    return battle.outcome;
  }

  /**
   * Die eigentliche Zusage: für dasselbe Produktionsbudget gewinnt IMMER das
   * konternde Schiff. Der Angreifer ist hier das konternde Schiff, ein Sieg
   * ist also {@link BattleOutcome#AttackerVictory}.
   */
  @Test
  void counterShipWinsAtEqualProductionBudget() {
    double budget = BUDGET_IN_MOST_EXPENSIVE_SHIPS
        * COUNTERS.keySet().stream().mapToDouble(ShipCounterEconomyTest::chainHours).max().orElseThrow();
    for (Map.Entry<String, String> pair : COUNTERS.entrySet()) {
      String counter = pair.getKey();
      String target = pair.getValue();
      assertEquals(BattleOutcome.AttackerVictory, fightAtEqualBudget(counter, target, budget),
          ProductCatalog.find(counter).name + " kontert " + ProductCatalog.find(target).name
              + ", verliert aber bei gleichem Produktionsbudget – der Konterkreis (Mechanik/03_..., §2) "
              + "ist in dieser Richtung wirkungslos. Prüfen: Kettenkosten gegen "
              + "workHoursPerUnit × baseProductionHours (Umsetzungskonzept/27_...md, §C).");
    }
  }

  /**
   * Dieselbe Aussage als geschlossene Formel, damit ein Verstoß nicht nur als
   * "verloren", sondern mit Abstand zur Grenze sichtbar wird.
   *
   * <p>Bei Kampfmasse {@code M = Stückzahl × Produktionsaufwand} gewinnt Seite A
   * genau dann, wenn {@code M_A² × mult_A > M_B² × mult_B}. Im Konterfall ist
   * {@code mult_A = 2} und {@code mult_B = 0,5}, die Bedingung also
   * {@code M_A / M_B > 0,5}. Weil beide Seiten dasselbe Budget ausgeben, ist
   * {@code M = Budget × Kampfwert / Kettenkosten} – es zählt allein die
   * Effizienz {@code e = Kampfwert je Kettenstunde}.</p>
   */
  @Test
  void counterEfficiencyStaysAboveTheHalfThreshold() {
    double threshold = Math.sqrt(Formulas.counterMultiplier(false, true) / Formulas.counterMultiplier(true, false));
    assertEquals(0.5, threshold, 1e-9, "Schwelle folgt aus ×2/×0,5 – Kontermatrix geändert?");
    for (Map.Entry<String, String> pair : COUNTERS.entrySet()) {
      double ratio = efficiency(pair.getKey()) / efficiency(pair.getValue());
      assertTrue(ratio > threshold,
          ProductCatalog.find(pair.getKey()).name + " kontert " + ProductCatalog.find(pair.getValue()).name
              + ": Effizienzverhältnis " + String.format("%.2f", ratio) + " ≤ " + threshold);
    }
  }

  /** Kampfwert je Spielstunde Produktionskette – die einzige Größe, die über Sieg oder Niederlage bei gleichem Budget entscheidet. */
  private static double efficiency(String productTypeId) {
    ProductType product = ProductCatalog.find(productTypeId);
    return Formulas.productionAspect(product.workHoursPerUnit, product.baseProductionHours) / chainHours(productTypeId);
  }
}
