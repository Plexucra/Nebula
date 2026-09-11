package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.ColonyPowerState;
import de.nebula.model.EnergyStorage;
import de.nebula.model.EnergyStorageView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Energiespeicher (Umsetzungskonzept/32_...md): Elerium fließt zuerst in den
 * Speicher, Ketten sehen ihn nicht, die Infrastruktur zieht zuerst daraus, und
 * eine gesenkte Vorhaltemenge gibt den Überschuss ans Lager zurück.
 */
class EnergyStorageTest {

  private record Bootstrapped(GameState state, IdGenerator ids, String playerId, String colonyId) {
  }

  private static Bootstrapped newState() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    String colonyId = state.players.get(0).homeworldColonyId;
    // Sauberer Ausgangspunkt: kein Elerium im Lager, Speicher leer.
    state.warehouse.removeIf(w -> w.colonyId.equals(colonyId) && w.productTypeId.equals(GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID));
    return new Bootstrapped(state, ids, state.players.get(0).id, colonyId);
  }

  private static int infrastructureLevel(Bootstrapped b) {
    for (Building bl : b.state().buildings) if (bl.colonyId.equals(b.colonyId()) && bl.typeId.equals("b_infrastructure")) return bl.level;
    return 0;
  }

  @Test
  void incomingEleriumFillsTheStorageFirstAndChainsDoNotSeeIt() {
    Bootstrapped b = newState();
    double target = EnergyStorageCommands.effectiveTarget(b.state(), b.colonyId());
    assertTrue(target > 0, "automatische Vorhaltemenge folgt der Infrastrukturstufe");

    Warehouse.add(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, target + 7);
    assertEquals(target, EnergyStorageCommands.stored(b.state(), b.colonyId()), 1e-9, "Speicher voll bis zur Vorhaltemenge");
    assertEquals(7, Warehouse.qty(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID), 1e-9, "nur der Rest liegt im Lager");

    // Eine Kette, die Elerium braucht, deckt sich NUR aus dem Lager (7), nie aus dem Speicher.
    ChainPlan plan = ChainPlanner.planChain(b.state(), b.colonyId(), "p_elerium_kapsel", 20, "b_industry");
    ChainPlanStep elerium = plan.steps.stream().filter(s -> s.productTypeId.equals(GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID)).findFirst().orElseThrow();
    assertEquals(7, elerium.quantityFromWarehouse, 1e-9);
    assertEquals(13, elerium.quantityToProduce, 1e-9);
  }

  @Test
  void upkeepDrawsFromStorageBeforeWarehouseAndCountsBothAsCoverage() {
    Bootstrapped b = newState();
    Warehouse.add(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, 5);
    assertEquals(5, EnergyStorageCommands.stored(b.state(), b.colonyId()), 1e-9);
    // Mindestens eine ganze Zelle fällig machen: Übertragskonto vorfüllen.
    b.state().fractionPots.put(FractionPot.key("power", b.colonyId()), 0.999);
    Economy.consumePower(b.state(), b.colonyId());
    double due = b.state().powerStates.stream().filter(p -> p.colonyId.equals(b.colonyId())).findFirst().orElseThrow().dueToday;
    assertTrue(due >= 1, "ein Kolonietag Infrastrukturverbrauch ist mindestens eine Zelle");
    assertEquals(5 - due, EnergyStorageCommands.stored(b.state(), b.colonyId()), 1e-9, "die fälligen Zellen kamen aus dem Speicher");
    assertEquals(0, Warehouse.qty(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID), 1e-9);
    ColonyPowerState ps = b.state().powerStates.stream().filter(p -> p.colonyId.equals(b.colonyId())).findFirst().orElseThrow();
    assertTrue(ps.coverageRatio > 0.99, "versorgt, obwohl das Lager leer ist");
  }

  @Test
  void loweringTheReserveReleasesSurplusIntoTheWarehouse() {
    Bootstrapped b = newState();
    EnergyStorageCommands.setReserve(b.state(), b.playerId(), b.colonyId(), 50.0);
    Warehouse.add(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, 50);
    assertEquals(50, EnergyStorageCommands.stored(b.state(), b.colonyId()), 1e-9);

    EnergyStorageCommands.setReserve(b.state(), b.playerId(), b.colonyId(), 20.0);
    assertEquals(20, EnergyStorageCommands.stored(b.state(), b.colonyId()), 1e-9);
    assertEquals(30, Warehouse.qty(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID), 1e-9);

    // Zurück auf Automatik: die Vorhaltemenge folgt wieder der Infrastrukturstufe.
    EnergyStorageCommands.setReserve(b.state(), b.playerId(), b.colonyId(), null);
    EnergyStorageView view = EnergyStorageCommands.view(b.state(), b.colonyId());
    assertTrue(view.automatic);
    assertEquals(EnergyStorageCommands.defaultTarget(b.state(), b.colonyId()), view.reserveTarget, 1e-9);
    assertTrue(infrastructureLevel(b) > 0);

    assertThrows(CommandException.class, () -> EnergyStorageCommands.setReserve(b.state(), b.playerId(), b.colonyId(), -1.0));
  }

  /**
   * Eine Abfrage ohne Kolonie-Angabe (etwa ein fehlerhafter Client-Aufruf) muss sauber
   * abgewiesen werden und darf KEINEN Eintrag anlegen: ein Speicher mit
   * {@code colonyId == null} stünde für immer in der Liste und ließe jede spätere Suche
   * über ihn stolpern – bis hin zum Energie-Tick, sobald eine neue Kolonie entsteht.
   */
  @Test
  void aQueryWithoutColonyIsRejectedWithoutLeavingATrace() {
    Bootstrapped b = newState();
    assertThrows(CommandException.class, () -> EnergyStorageCommands.storageOf(b.state(), null));
    assertThrows(CommandException.class, () -> EnergyStorageCommands.view(b.state(), null));
    assertTrue(b.state().energyStorages.stream().allMatch(s -> s.colonyId != null), "kein Eintrag ohne Kolonie");

    // Und der Kolonietag läuft danach unverändert weiter.
    Warehouse.add(b.state(), b.colonyId(), GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, 5);
    Economy.consumePower(b.state(), b.colonyId());
    assertEquals(0, EnergyStorageCommands.stored(b.state(), null), 1e-9);
  }

  @Test
  void storageBelongsToTheColonyOnly() {
    Bootstrapped b = newState();
    EnergyStorage s = EnergyStorageCommands.storageOf(b.state(), b.colonyId());
    assertEquals(b.colonyId(), s.colonyId);
    assertEquals(1, b.state().energyStorages.size(), "einmal angelegt, danach wiederverwendet");
    EnergyStorageCommands.storageOf(b.state(), b.colonyId());
    assertEquals(1, b.state().energyStorages.size());
  }

  /** Gesamttest 11.9.2026: die Abfrage einer frischen Kolonie ohne Speicher-Eintrag warf eine NullPointerException. */
  @Test
  void viewWorksBeforeAnyStorageEntryExists() {
    Bootstrapped b = newState();
    b.state().energyStorages.removeIf(s -> s.colonyId.equals(b.colonyId()));
    EnergyStorageView v = EnergyStorageCommands.view(b.state(), b.colonyId());
    assertEquals(0, v.stored, 1e-9);
    assertTrue(v.automatic);
    assertEquals(0, v.storedCoverageGameHours, 1e-9, "leerer Speicher deckt null Stunden");
    assertTrue(v.reserveTarget > 0);
  }
}
