package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.ColonyPowerState;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.Planet;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.ProductType;
import de.nebula.model.SellOrder;
import de.nebula.model.TransactionReason;
import de.nebula.model.UniverseStatSnapshot;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1-Portierung der "Bevölkerung/Geld"-Tick-Sektion aus
 * {@code simulated-game-api.service.ts} (Umsetzungskonzept/13_...md, Phase 6).
 * Reine {@code processXxx}-Methoden, aufgerufen von {@link GameTick} in
 * exakt der TS-Reihenfolge (siehe {@code runTick}).
 */
public final class EconomyTick {
  private EconomyTick() {
  }

  /** Zeitreihe aggregierter Stabilitätskennzahlen über die gesamte Galaxie, siehe {@link #recordStatsSnapshotIfDue}. */
  public static List<UniverseStatSnapshot> universeStats(GameState state) {
    return List.copyOf(state.universeStats);
  }

  public static void payUpkeepAndWages(GameState state, IdGenerator ids) {
    for (Colony colony : state.colonies) {
      Wallet ownerWallet = GameQueries.findWallet(state, WalletOwnerType.Player, colony.ownerId);
      Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
      if (ownerWallet == null || popWallet == null) continue;

      double overbuild = BuildingCommands.overbuildFactor(state, colony.planetId);
      double buildingUpkeep = 0;
      for (Building b : state.buildings) {
        if (b.colonyId.equals(colony.id) && b.level > 0) {
          buildingUpkeep += BuildingCatalog.find(b.typeId).upkeepPerLevel * b.level;
        }
      }
      buildingUpkeep *= overbuild * GameConstants.TICK_GAME_HOURS;

      double fleetUpkeep = 0;
      for (var f : state.fleets) {
        if (colony.id.equals(f.locationColonyId)) {
          for (var g : f.ships) fleetUpkeep += g.quantity * 0.5;
        }
      }
      fleetUpkeep *= GameConstants.TICK_GAME_HOURS;

      double population = 0;
      for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p.currentCount;
      double wage = population * 0.02 * GameConstants.TICK_GAME_HOURS;

      payFromOwnerWallet(state, ids, colony, ownerWallet, popWallet, buildingUpkeep, TransactionReason.BuildingUpkeep, "Gebäudeunterhalt");
      payFromOwnerWallet(state, ids, colony, ownerWallet, popWallet, fleetUpkeep, TransactionReason.FleetUpkeep, "Flottenunterhalt");
      payFromOwnerWallet(state, ids, colony, ownerWallet, popWallet, wage, TransactionReason.Wage, "Löhne");
    }
  }

  private static void payFromOwnerWallet(GameState state, IdGenerator ids, Colony colony, Wallet ownerWallet,
                                          Wallet popWallet, double amount, TransactionReason reason, String note) {
    double available = Math.max(GameQueries.findWallet(state, WalletOwnerType.Player, colony.ownerId) != null
        ? GameQueries.findWallet(state, WalletOwnerType.Player, colony.ownerId).balance : 0, 0);
    double affordable = Math.min(amount, available);
    if (affordable > 0.001) Ledger.recordTx(state, ids, ownerWallet.id, popWallet.id, affordable, reason, note);
  }

  /**
   * Bevölkerungs-Konsum: kauft aktiv von {@link SellOrder}s am Systemmarkt
   * (siehe {@link MarketCommands#settleSellOrderPurchase}). Glättet ihr
   * Konsumbudget (analog EMA) und ihren Lebensstandard, damit ein einzelner
   * leerer/voller Tick keinen harten Sprung auslöst.
   */
  public static void runConsumption(GameState state, IdGenerator ids) {
    for (Colony colony : state.colonies) {
      Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
      Population population = null;
      for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p;
      PlanetStats stats = null;
      for (PlanetStats s : state.planetStats) if (s.colonyId.equals(colony.id)) stats = s;
      if (popWallet == null || population == null || stats == null) continue;

      double prevN = state.consumptionBudget.getOrDefault(colony.id, popWallet.balance * 0.1);
      double income = Math.max(popWallet.balance - prevN, 0);
      double n = 0.9 * prevN + 0.1 * income;
      double budget = Math.min(popWallet.balance, Math.max(n, popWallet.balance / 10));
      state.consumptionBudget.put(colony.id, n);

      double remaining = budget;
      double coverageSum = 0;
      double weightSum = 0;
      Map<String, Double> coverageByGood = new HashMap<>();
      for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
        double need = population.currentCount * GameConstants.CONSUMER_NEED_PER_CAPITA.get(goodId);
        if (need <= 0) continue;
        double goodBudget = remaining * (1.0 / GameConstants.CONSUMER_GOODS_ORDER.size());
        List<SellOrder> orders = new ArrayList<>();
        for (SellOrder o : state.sellOrders) {
          if (o.systemId.equals(colony.systemId) && o.productTypeId.equals(goodId) && o.remainingQuantity > 0) orders.add(o);
        }
        orders.sort((a, b) -> Double.compare(a.pricePerUnit, b.pricePerUnit));
        double spend = 0;
        double bought = 0;
        for (SellOrder order : orders) {
          if (spend >= goodBudget || bought >= need) break;
          double affordableQty = Math.floor((goodBudget - spend) / order.pricePerUnit);
          double qty = Math.min(Math.min(affordableQty, order.remainingQuantity), Math.ceil(need - bought));
          if (qty <= 0) continue;
          double cost = qty * order.pricePerUnit;
          Wallet sellerWallet = GameQueries.findWallet(state, WalletOwnerType.Player, order.sellerId);
          MarketCommands.settleSellOrderPurchase(state, ids, order, qty);
          if (sellerWallet != null) Ledger.recordTx(state, ids, popWallet.id, sellerWallet.id, cost, TransactionReason.Consumption, "Konsum " + goodId);
          spend += cost;
          bought += qty;
        }
        remaining -= spend;
        double coverage = Formulas.clamp(bought / need, 0, 1.5);
        coverageByGood.put(goodId, coverage);
        double weight = goodId.equals("p_grundnahrung") ? 2 : 1;
        coverageSum += coverage * weight;
        weightSum += weight;
      }
      state.consumptionCoverage.put(colony.id, coverageByGood);
      double prevRaw = state.rawStandardOfLiving.getOrDefault(colony.id, stats.standardOfLivingPct);
      double newStandard = weightSum > 0 ? (coverageSum / weightSum) * 100 : prevRaw;
      double smoothedRaw = prevRaw * 0.7 + newStandard * 0.3;
      state.rawStandardOfLiving.put(colony.id, smoothedRaw);
      double effective = PowerGrid.isBlackout(state, colony.id) ? smoothedRaw * Formulas.BLACKOUT_STAT_FACTOR : smoothedRaw;
      stats.standardOfLivingPct = Formulas.clamp(effective, 0, 200);
    }
  }

  public static void recalcCoreStats(GameState state, long t) {
    for (PlanetStats stats : state.planetStats) {
      Colony colony = null;
      for (Colony c : state.colonies) if (c.id.equals(stats.colonyId)) colony = c;
      if (colony == null) continue;
      double population = 0;
      for (Population p : state.populations) if (p.colonyId.equals(stats.colonyId)) population = p.currentCount;

      double builtCapacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
      GroundForceGroup garrison = null;
      for (GroundForceGroup g : state.groundForceGroups) if (g.colonyId.equals(colony.id)) garrison = g;

      // Nur aktive (kommandierte) Drohnen tragen zur Sicherheit bei – Soldaten besitzen
      // keine eigene Kampfwirkung, Reserven kämpfen nicht (Mechanik/05_..., §3).
      double garrisonStrength = 0;
      if (garrison != null) {
        for (GroundForceUnitStack u : garrison.units) {
          if (u.unitProductTypeId.equals("p_soldier")) continue;
          ProductType product = ProductCatalog.find(u.unitProductTypeId);
          garrisonStrength += u.activeCount * Formulas.productionAspect(product.baseWorkforceRequired, product.baseProductionHours);
        }
      }
      double infra = Formulas.infrastructurePct(builtCapacity, population);
      double rawSecurity = Formulas.securityPct(garrisonStrength, population, stats.loyaltyPct);
      double security = PowerGrid.isBlackout(state, colony.id) ? rawSecurity * Formulas.BLACKOUT_STAT_FACTOR : rawSecurity;
      double loyaltyDelta = Formulas.loyaltyDelta(colony.isHomeworld, stats.standardOfLivingPct, security) * GameConstants.TICK_GAME_HOURS;
      double loyalty = Formulas.clamp(stats.loyaltyPct + loyaltyDelta, 0, 100);

      stats.infrastructurePct = infra;
      stats.securityPct = security;
      stats.loyaltyPct = loyalty;
      stats.lastRecalculatedAt = t;
    }
  }

  public static void growPopulationAndMoneySupply(GameState state, IdGenerator ids) {
    for (Colony colony : state.colonies) {
      Population population = null;
      for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p;
      PlanetStats stats = null;
      for (PlanetStats s : state.planetStats) if (s.colonyId.equals(colony.id)) stats = s;
      Planet planet = null;
      for (Planet p : state.planets) if (p.id.equals(colony.planetId)) planet = p;
      if (population == null || stats == null || planet == null) continue;

      double capacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
      double delta = Formulas.populationGrowthDelta(population.currentCount, capacity, stats.standardOfLivingPct, stats.securityPct)
          * GameConstants.TICK_GAME_HOURS;
      // Blackout unterbindet nur Wachstum – Schrumpfung durch Überbevölkerung (negatives Delta) läuft unabhängig davon normal weiter.
      if (delta > 0 && PowerGrid.isBlackout(state, colony.id)) delta = 0;
      double newCount = Math.max(0, population.currentCount + delta);
      population.currentCount = newCount;
      population.growthRatePerInterval = delta;

      PopulationMoneySupplyState moneyState = null;
      for (PopulationMoneySupplyState m : state.moneySupplyStates) if (m.planetId.equals(colony.planetId)) moneyState = m;
      if (moneyState != null && newCount > moneyState.historicalPeakPopulation) {
        double growthDelta = newCount - moneyState.historicalPeakPopulation;
        double created = growthDelta * Formulas.CREDITS_PER_NEW_INHABITANT;
        Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
        if (popWallet != null) Ledger.recordTx(state, ids, null, popWallet.id, created, TransactionReason.MoneyCreation, "Bevölkerungswachstum über Höchststand");
        moneyState.historicalPeakPopulation = newCount;
        moneyState.lastPopulation = newCount;
      } else if (moneyState != null) {
        moneyState.lastPopulation = newCount;
      }
    }
  }

  /**
   * PowerUpkeepJob (Umsetzungskonzept/01_..., §3): zieht je Kolonie mit
   * aktivem Energienetz laufend Elerium-Zellen aus dem Lager. Reicht der
   * Bestand nicht, sinkt {@code coverageRatio} – geglättet, damit ein
   * einzelner leerer Tick nicht sofort einen harten Kapazitätssprung auslöst.
   */
  public static void consumePowerUpkeep(GameState state) {
    List<ColonyPowerState> next = new ArrayList<>();
    for (Colony colony : state.colonies) {
      int level = GameQueries.getBuildingLevel(state, colony.id, "b_powergrid");
      double prevRatio = 1;
      for (ColonyPowerState p : state.powerStates) if (p.colonyId.equals(colony.id)) prevRatio = p.coverageRatio;
      ColonyPowerState ps = new ColonyPowerState();
      ps.colonyId = colony.id;
      if (level <= 0) {
        ps.coverageRatio = 1;
        next.add(ps);
        continue;
      }
      double need = level * GameConstants.ELERIUM_UPKEEP_PER_POWERGRID_LEVEL * GameConstants.TICK_GAME_HOURS;
      double stock = Warehouse.qty(state, colony.id, GameConstants.POWERGRID_FUEL_PRODUCT_ID);
      double covered = Math.min(need, stock);
      if (covered > 0) Warehouse.add(state, colony.id, GameConstants.POWERGRID_FUEL_PRODUCT_ID, -covered);
      double instantRatio = need > 0 ? covered / need : 1;
      ps.coverageRatio = prevRatio * 0.8 + instantRatio * 0.2;
      next.add(ps);
    }
    state.powerStates.clear();
    state.powerStates.addAll(next);
  }

  /**
   * Ausgleichsfonds gegen Geldhortung (Konzeption/Spieldesign/06_..., §8 und
   * Mechanik/10_..., §7): einmal pro Spieltag zahlen große Spieler-/
   * Kommandanten-Wallets 0,1% ihres Guthabens und jede Kolonie 1% ihres
   * Bevölkerungs-Wallets in einen galaxieweiten Topf ein, der im selben Lauf
   * komplett pro Kopf an alle Bevölkerungs-Wallets zurückverteilt wird.
   */
  public static void runWealthRedistributionIfDue(GameState state, IdGenerator ids, long t) {
    if (t - state.lastWealthRedistributionAt < GameConstants.GAME_DAY_MS) return;
    state.lastWealthRedistributionAt = t;

    double pot = 0;
    for (Wallet wallet : state.wallets) {
      if (wallet.ownerType != WalletOwnerType.Player || wallet.balance <= GameConstants.WEALTH_TAX_THRESHOLD) continue;
      double tax = wallet.balance * GameConstants.WEALTH_TAX_RATE;
      if (tax <= 0.001) continue;
      Ledger.recordTx(state, ids, wallet.id, null, tax, TransactionReason.Tax, "Vermögenssteuer (Ausgleichsfonds)");
      pot += tax;
    }
    for (Colony colony : state.colonies) {
      Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
      if (popWallet == null || popWallet.balance <= 0) continue;
      double tax = popWallet.balance * GameConstants.COLONY_TAX_RATE;
      if (tax <= 0.001) continue;
      Ledger.recordTx(state, ids, popWallet.id, null, tax, TransactionReason.Tax, "Kolonialabgabe (Ausgleichsfonds)");
      pot += tax;
    }
    if (pot <= 0.001) return;

    double totalPopulation = 0;
    for (Population p : state.populations) totalPopulation += p.currentCount;
    if (totalPopulation <= 0) return;
    double perCapita = pot / totalPopulation;
    for (Population population : state.populations) {
      Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, population.colonyId);
      if (popWallet == null) continue;
      double share = population.currentCount * perCapita;
      if (share <= 0.001) continue;
      Ledger.recordTx(state, ids, null, popWallet.id, share, TransactionReason.Subsidy, "Ausgleichsfonds-Ausschüttung");
    }
  }

  public static void recordStatsSnapshotIfDue(GameState state, long t) {
    if (t - state.lastStatsSnapshotAt < GameConstants.STATS_SNAPSHOT_INTERVAL_MS) return;
    state.lastStatsSnapshotAt = t;
    if (state.colonies.isEmpty()) return;

    List<PlanetStats> stats = state.planetStats;
    double totalInfra = 0, totalSecurity = 0, totalStandard = 0, totalLoyalty = 0;
    int struggling = 0;
    for (PlanetStats s : stats) {
      totalInfra += s.infrastructurePct;
      totalSecurity += s.securityPct;
      totalStandard += s.standardOfLivingPct;
      totalLoyalty += s.loyaltyPct;
      if (s.standardOfLivingPct < 30 || s.loyaltyPct < 20) struggling++;
    }
    int n = stats.size();
    double totalPopulation = 0;
    for (Population p : state.populations) totalPopulation += p.currentCount;
    double totalCredits = 0;
    for (Wallet w : state.wallets) totalCredits += w.balance;
    int openSellOrders = 0;
    for (SellOrder o : state.sellOrders) if (o.remainingQuantity > 0) openSellOrders++;

    UniverseStatSnapshot snapshot = new UniverseStatSnapshot();
    snapshot.at = t;
    snapshot.colonyCount = state.colonies.size();
    snapshot.strugglingColonyCount = struggling;
    snapshot.totalPopulation = totalPopulation;
    snapshot.totalCredits = Math.round(totalCredits);
    snapshot.avgInfrastructurePct = n > 0 ? totalInfra / n : 0;
    snapshot.avgSecurityPct = n > 0 ? totalSecurity / n : 0;
    snapshot.avgStandardOfLivingPct = n > 0 ? totalStandard / n : 0;
    snapshot.avgLoyaltyPct = n > 0 ? totalLoyalty / n : 0;
    snapshot.openSellOrderCount = openSellOrders;

    state.universeStats.add(snapshot);
    while (state.universeStats.size() > GameConstants.STATS_HISTORY_LIMIT) state.universeStats.remove(0);
  }
}
