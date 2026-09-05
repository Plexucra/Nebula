package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Colony;
import de.nebula.model.Planet;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.List;

/**
 * 1:1-Portierung der "Planeten/Kolonien"-Sektion aus
 * {@code simulated-game-api.service.ts} (Umsetzungskonzept/13_...md, Phase 5).
 */
public final class ColonyCommands {
  private ColonyCommands() {
  }

  public static List<Colony> coloniesOf(GameState state, String playerId) {
    return state.colonies.stream().filter(c -> c.ownerId.equals(playerId)).toList();
  }

  public static List<Colony> coloniesInSystem(GameState state, String systemId) {
    return state.colonies.stream().filter(c -> c.systemId.equals(systemId)).toList();
  }

  public static Colony colony(GameState state, String id) {
    return state.colonies.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
  }

  public static PlanetStats colonyStats(GameState state, String colonyId) {
    return state.planetStats.stream().filter(s -> s.colonyId.equals(colonyId)).findFirst().orElse(null);
  }

  public static Population population(GameState state, String colonyId) {
    return state.populations.stream().filter(p -> p.colonyId.equals(colonyId)).findFirst().orElse(null);
  }

  public static PopulationMoneySupplyState moneySupplyState(GameState state, String planetId) {
    return state.moneySupplyStates.stream().filter(m -> m.planetId.equals(planetId)).findFirst().orElse(null);
  }

  /** Deckung (0..1,5) je Grundkonsumgut – Diagnosewert für die Statistik-Seite, siehe {@code EconomyTick.runConsumption}. */
  public static java.util.Map<String, Double> consumptionCoverage(GameState state, String colonyId) {
    return state.consumptionCoverage.getOrDefault(colonyId, java.util.Map.of());
  }

  public static Planet planet(GameState state, String id) {
    return state.planets.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null);
  }

  public static List<Planet> planetsInSystem(GameState state, String systemId) {
    return state.planets.stream().filter(p -> p.systemId.equals(systemId)).toList();
  }

  private static final double COLONIZE_COST = 800;

  public static Colony colonizePlanet(GameState state, IdGenerator ids, String playerId, String planetId) {
    var player = GameQueries.requirePlayer(state, playerId);
    Planet planet = planet(state, planetId);
    if (planet == null) throw new CommandException("Unbekannter Planet.");
    boolean alreadyOwned = state.colonies.stream().anyMatch(c -> c.planetId.equals(planetId) && c.ownerId.equals(player.id));
    if (alreadyOwned) throw new CommandException("Auf diesem Planeten besteht bereits eine eigene Kolonie.");

    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    if (wallet == null || wallet.balance < COLONIZE_COST) throw new CommandException("Nicht genug Credits für eine Kolonialgründung.");

    long t = Clock.now();
    Colony colony = new Colony();
    colony.id = ids.next("col");
    colony.planetId = planetId;
    colony.systemId = planet.systemId;
    colony.ownerId = player.id;
    colony.name = planet.name + "-Kolonie";
    colony.foundedAt = t;
    colony.isHomeworld = false;
    state.colonies.add(colony);

    PlanetStats stats = new PlanetStats();
    stats.colonyId = colony.id;
    stats.infrastructurePct = 15;
    stats.securityPct = 5;
    stats.standardOfLivingPct = 30;
    stats.loyaltyPct = 55;
    stats.lastRecalculatedAt = t;
    state.planetStats.add(stats);

    Population population = new Population();
    population.colonyId = colony.id;
    population.currentCount = 25;
    population.growthRatePerInterval = 0;
    state.populations.add(population);

    boolean moneySupplyExists = state.moneySupplyStates.stream().anyMatch(m -> m.planetId.equals(planetId));
    if (!moneySupplyExists) {
      PopulationMoneySupplyState money = new PopulationMoneySupplyState();
      money.planetId = planetId;
      money.historicalPeakPopulation = 25;
      money.lastPopulation = 25;
      state.moneySupplyStates.add(money);
    }

    Wallet popWallet = new Wallet();
    popWallet.id = ids.next("wal");
    popWallet.ownerType = WalletOwnerType.Population;
    popWallet.ownerId = colony.id;
    popWallet.balance = 30;
    state.wallets.add(popWallet);

    Ledger.recordTx(state, ids, wallet.id, GameQueries.homeworldPopulationWalletId(state, player.id), COLONIZE_COST,
        TransactionReason.Construction, "Kolonialgründung " + colony.name);
    return colony;
  }
}
