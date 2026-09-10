package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO.md: "Baustoffe verbrauchen einander als Vorprodukt – Reihenfolge ist eine Falle."
 * {@code p_leiterbuendel} enthält {@code p_leitermetall} 1:1 (siehe products.json). Ein
 * Bauauftrag, der BEIDE direkt braucht ({@code b_infrastructure} ab Stufe 4), konnte nie beide
 * Sollmengen gleichzeitig im Lager haben, solange man sie als zwei separate
 * {@code queueProduction}-Aufträge einreiht: der zweite (Leiterbündel) verbraucht per
 * {@code autoProduceMissing} die Menge Leitermetall wieder, die der erste gerade erst
 * eingelagert hat. {@link ProductionCommands#queueProductionBundleCore} behebt das, indem
 * {@link ChainPlanner#planChain(GameState, String, Map, String)} den Bedarf für mehrere
 * Wurzelprodukte in EINEM Rutsch aggregiert, statt sie als unabhängige Aufträge um denselben
 * Lagerbestand konkurrieren zu lassen.
 */
class ProductionBundleTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String colonyId) {
  }

  private static Bootstrapped newBootstrappedColony() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    WorldSeed.Seed seed = WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids);
    GameStateSeeder.bootstrap(state, seed, ids);
    return new Bootstrapped(state, ids, state.colonies.get(0).id);
  }

  private static double warehouseQty(GameState state, String colonyId, String productTypeId) {
    return state.warehouse.stream()
        .filter(w -> w.colonyId.equals(colonyId) && w.productTypeId.equals(productTypeId))
        .mapToDouble(w -> w.quantity)
        .findFirst().orElse(0);
  }

  @Test
  void bundledOrderProducesBothOverlappingRootsInFullDespiteSharedIngredient() {
    Bootstrapped b = newBootstrappedColony();
    // Startaufträge (Grundnahrung/Elerium) räumen, damit unser Bündelauftrag sofort läuft –
    // die sequentielle Warteschlange lässt sonst nur einen laufenden Eintrag zu.
    b.state().productionQueue.clear();

    Map<String, Double> demand = new LinkedHashMap<>();
    demand.put("p_leitermetall", 13.0);
    demand.put("p_leiterbuendel", 13.0);
    ProductionCommands.queueProductionBundleCore(b.state(), b.ids(), b.colonyId(), demand, true, false);

    assertEquals(1, b.state().productionQueue.size(), "Beide Produkte müssen EIN Auftrag sein, keine zwei");
    ProductionQueueEntry entry = b.state().productionQueue.get(0);
    assertEquals(ProductionQueueStatus.running, entry.status, "Bündelauftrag muss sofort starten (Industriekomplex vorhanden)");
    assertNotNull(entry.endsAt);

    // Kein echtes Warten nötig: der Kettenplaner hat endsAt schon berechnet, wir spulen direkt dorthin.
    GameEvents.fireNow(b.state(), b.ids(), GameEventType.PRODUCTION_COMPLETED, entry.id);

    assertTrue(b.state().productionQueue.isEmpty(), "Auftrag muss nach Fertigstellung entfernt sein");
    assertEquals(13.0, warehouseQty(b.state(), b.colonyId(), "p_leitermetall"), 1e-9,
        "Der DIREKTE Leitermetall-Bedarf muss vollständig im Lager landen, nicht vom Leiterbündel mit-aufgezehrt");
    assertEquals(13.0, warehouseQty(b.state(), b.colonyId(), "p_leiterbuendel"), 1e-9,
        "Das Leiterbündel selbst muss ebenfalls vollständig im Lager landen");
  }
}
