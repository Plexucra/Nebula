package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Building;
import de.nebula.model.DefenseActivationState;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 1:1-Portierung von {@code runTick}/{@code TICK_MS} aus
 * {@code simulated-game-api.service.ts} (Umsetzungskonzept/13_...md, Phase 12
 * – hier vorgezogen auf "sobald eine frühere Phase erstmals etwas zum
 * Anwenden hat", siehe Bebauung in Phase 5). NICHT an einen eingeloggten
 * Nutzer gebunden: die gemeinsame Galaxie simuliert immer weiter, unabhängig
 * davon, wer gerade verbunden ist.
 *
 * <p>Wächst mit jeder weiteren portierten Phase um die entsprechende
 * {@code processXxx}-Methode (siehe TS {@code runTick} für die vollständige,
 * noch zu portierende Reihenfolge). Solange eine Phase fehlt, führt dieser
 * Tick für sie schlicht nichts aus – kein Platzhalterverhalten, das die
 * spätere echte Logik verdecken könnte.</p>
 */
@ApplicationScoped
public class GameTick {

  private final GameState state;
  private final IdGenerator ids;

  public GameTick(GameState state, IdGenerator ids) {
    this.state = state;
    this.ids = ids;
  }

  @Scheduled(every = "1s")
  void tick() {
    if (state.players.isEmpty()) return;
    long t = Clock.now();
    synchronized (state) {
      // Reihenfolge 1:1 wie TS runTick – NICHT umstellen, spätere Schritte
      // verlassen sich auf bereits aktualisierte Werte früherer Schritte
      // (z. B. recalcCoreStats auf den in consumePowerUpkeep gesetzten
      // coverageRatio, growPopulationAndMoneySupply auf recalcCoreStats).
      processBuildingCompletions(t);
      EconomyTick.consumePowerUpkeep(state);
      processDefenseActivations(t);
      FleetCommands.processFleetArrivals(state, t);
      BattleCommands.processBattles(state, ids, t);
      ProductionCommands.processProductionQueue(state, ids, t);
      ShipyardCommands.processShipyardCompletions(state, ids, t);
      RecruitmentCommands.processRecruitmentCompletions(state, ids, t);
      Specializations.decaySpecializations(state, t);
      EconomyTick.payUpkeepAndWages(state, ids);
      EconomyTick.runConsumption(state, ids);
      MarketCommands.replenishDormantSellOrders(state);
      TreatyCommands.processExpiredTerminations(state, ids, t);
      EconomyTick.recalcCoreStats(state, t);
      EconomyTick.growPopulationAndMoneySupply(state, ids);
      EconomyTick.runWealthRedistributionIfDue(state, ids, t);
      EconomyTick.recordStatsSnapshotIfDue(state, t);
      RetentionCleanup.purgeExpired(state, t);
    }
  }

  private void processBuildingCompletions(long t) {
    for (Building b : state.buildings) {
      if (b.pendingOrder != null && b.pendingOrder.completesAt <= t) {
        b.level = b.pendingOrder.targetLevel;
        b.pendingOrder = null;
        // Ein fertiger Industriekomplex weckt wartende Produktionsaufträge (Minimalstart, Umsetzungskonzept/17_...md).
        ProductionCommands.tryStartNextProductionEntry(state, ids, b.colonyId);
      }
    }
  }

  private void processDefenseActivations(long t) {
    for (Building b : state.buildings) {
      if (b.activationState == DefenseActivationState.Activating
          && b.activationCompletesAt != null && b.activationCompletesAt <= t) {
        b.activationState = DefenseActivationState.Active;
        b.activationCompletesAt = null;
      }
    }
  }
}
