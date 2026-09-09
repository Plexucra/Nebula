package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.ColonyPowerState;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.Planet;
import de.nebula.model.Player;
import de.nebula.model.PlanetStats;
import de.nebula.model.NotificationType;
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

      double buildingUpkeep = 0;
      for (Building b : state.buildings) {
        if (b.colonyId.equals(colony.id) && b.level > 0) {
          buildingUpkeep += BuildingCatalog.find(b.typeId).upkeepPerLevel * b.level;
        }
      }
      buildingUpkeep *= GameConstants.TICK_GAME_HOURS;

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

  /**
   * Wie viel des (bruchteiligen) Bedarfs die Bevölkerung mit dem Budget aus den
   * vorliegenden Orders decken KÖNNTE – reine Bewertung der Versorgungslage,
   * ohne etwas zu kaufen. Bewusst bruchteilig: sie misst die Marktlage, nicht
   * die Stückelung der tatsächlichen Warenbewegung.
   */
  private static double purchasableQuantity(List<SellOrder> orders, double budget, double need) {
    double spend = 0;
    double qty = 0;
    for (SellOrder order : orders) {
      if (spend >= budget || qty >= need - 1e-9) break;
      double affordable = (budget - spend) / order.pricePerUnit;
      double take = Math.min(Math.min(affordable, order.remainingQuantity), need - qty);
      if (take <= 1e-9) continue;
      spend += take * order.pricePerUnit;
      qty += take;
    }
    return qty;
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

      double budgetAlpha = Formulas.smoothingAlpha(Formulas.CONSUMPTION_BUDGET_SMOOTHING_TAU_HOURS);
      double prevN = state.consumptionBudget.getOrDefault(colony.id, popWallet.balance * 0.1);
      double income = Math.max(popWallet.balance - prevN, 0);
      double n = (1 - budgetAlpha) * prevN + budgetAlpha * income;
      double budget = Math.min(popWallet.balance, Math.max(n, popWallet.balance / 10));
      state.consumptionBudget.put(colony.id, n);

      double remaining = budget;
      double coverageSum = 0;
      double weightSum = 0;
      Map<String, Double> coverageByGood = new HashMap<>();
      for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
        // Pro-Kopf-Bedarf ist eine RATE JE SPIELSTUNDE – erst hier auf den Tick
        // heruntergerechnet, damit der Verbrauch mit dem Tempo-Regler skaliert.
        double need = population.currentCount * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(goodId)
            * GameConstants.TICK_GAME_HOURS;
        if (need <= 0) continue;
        double goodBudget = remaining * (1.0 / GameConstants.CONSUMER_GOODS_ORDER.size());
        List<SellOrder> orders = new ArrayList<>();
        for (SellOrder o : state.sellOrders) {
          if (o.systemId.equals(colony.systemId) && o.productTypeId.equals(goodId) && o.remainingQuantity > 0) orders.add(o);
        }
        orders.sort((a, b) -> Double.compare(a.pricePerUnit, b.pricePerUnit));

        // Die Versorgungslage wird JEDEN Tick am (bruchteiligen) Bedarf gemessen,
        // damit der Lebensstandard weiter fein reagiert – auch in den vielen Ticks,
        // in denen noch kein ganzes Stück fällig ist. Sie sagt: hätte die
        // Bevölkerung kaufen können, was sie gerade braucht?
        double coverage = Formulas.clamp(purchasableQuantity(orders, goodBudget, need) / need, 0, 1.5);

        // Gekauft wird dagegen ausschließlich in GANZEN Stücken: der Bruchteilbedarf
        // wandert ins Übertragskonto und löst erst dort eine echte Warenbewegung aus
        // (Umsetzungskonzept/25_...md). Früheres Aufrunden je Tick machte aus 0,05
        // Stück Bedarf eine ganze Einheit je Sekunde – ein Vielfaches dessen, was
        // die Startproduktion je decken konnte; Abschneiden hätte den Kauf nie
        // stattfinden lassen.
        double due = FractionPot.due(state, FractionPot.key("consume", colony.id, goodId), need);
        double spend = 0;
        double bought = 0;
        for (SellOrder order : orders) {
          if (spend >= goodBudget || bought >= due - 1e-9) break;
          double affordableQty = Math.floor((goodBudget - spend) / order.pricePerUnit);
          double qty = Math.min(Math.min(affordableQty, Math.floor(order.remainingQuantity)), due - bought);
          if (qty < 1) continue;
          double cost = qty * order.pricePerUnit;
          Wallet sellerWallet = GameQueries.findWallet(state, WalletOwnerType.Player, order.sellerId);
          MarketCommands.settleSellOrderPurchase(state, ids, order, qty);
          if (sellerWallet != null) Ledger.recordTx(state, ids, popWallet.id, sellerWallet.id, cost, TransactionReason.Consumption, "Konsum " + goodId);
          spend += cost;
          bought += qty;
        }
        remaining -= spend;
        coverageByGood.put(goodId, coverage);
        double weight = goodId.equals("p_grundnahrung") ? 2 : 1;
        coverageSum += coverage * weight;
        weightSum += weight;
      }
      state.consumptionCoverage.put(colony.id, coverageByGood);
      warnAboutSupplyGaps(state, ids, colony, coverageByGood);
      double prevRaw = state.rawStandardOfLiving.getOrDefault(colony.id, stats.standardOfLivingPct);
      double newStandard = weightSum > 0 ? (coverageSum / weightSum) * 100 : prevRaw;
      double standardAlpha = Formulas.smoothingAlpha(Formulas.LIVING_STANDARD_SMOOTHING_TAU_HOURS);
      double smoothedRaw = prevRaw * (1 - standardAlpha) + newStandard * standardAlpha;
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
      // colonyId ist null, solange ein Verband an Bord einer Flotte ist (Umsetzungskonzept/28_...md) –
      // eingeschiffte Truppen zählen naturgemäß nicht zur Sicherheit einer Kolonie.
      for (GroundForceGroup g : state.groundForceGroups) if (colony.id.equals(g.colonyId)) garrison = g;

      // Nur aktive (kommandierte) Drohnen tragen zur Sicherheit bei – Soldaten besitzen
      // keine eigene Kampfwirkung, Reserven kämpfen nicht (Mechanik/05_..., §3).
      double garrisonStrength = 0;
      if (garrison != null) {
        for (GroundForceUnitStack u : garrison.units) {
          if (u.unitProductTypeId.equals("p_soldier")) continue;
          ProductType product = ProductCatalog.find(u.unitProductTypeId);
          garrisonStrength += u.activeCount * Formulas.productionAspect(product.workHoursPerUnit, product.baseProductionHours);
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
      // Der Nahrungsdeckel (Umsetzungskonzept/34_...md, §J 5) liest die Deckung
      // aus DIESEM Tick: runConsumption läuft unmittelbar vorher (GameTick).
      double delta = Formulas.populationGrowthDelta(population.currentCount, capacity, stats.standardOfLivingPct,
          stats.securityPct, ColonyCommands.foodCoverage(state, colony.id))
          * GameConstants.TICK_GAME_HOURS;
      // Blackout unterbindet nur Wachstum – Schrumpfung durch Überbevölkerung (negatives Delta) läuft unabhängig davon normal weiter.
      if (delta > 0 && PowerGrid.isBlackout(state, colony.id)) delta = 0;
      // Einwohner sind ganze Menschen: der Bruchteil je Tick (bei 120 Einwohnern
      // rund 0,5) sammelt sich im Übertragskonto, bis eine ganze Person daraus
      // wird (Umsetzungskonzept/25_...md). Die RATE bleibt bruchteilig, sie ist
      // eine Geschwindigkeit und keine Stückzahl.
      double wholeDelta = FractionPot.due(state, FractionPot.key("population", colony.id), delta);
      double newCount = Math.max(0, population.currentCount + wholeDelta);
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
      int level = GameQueries.getBuildingLevel(state, colony.id, GameConstants.INFRASTRUCTURE_BUILDING_ID);
      double prevRatio = 1;
      for (ColonyPowerState p : state.powerStates) if (p.colonyId.equals(colony.id)) prevRatio = p.coverageRatio;
      ColonyPowerState ps = new ColonyPowerState();
      ps.colonyId = colony.id;
      if (level <= 0) {
        ps.coverageRatio = 1;
        next.add(ps);
        continue;
      }
      double need = Formulas.infrastructureEleriumPerHour(level) * GameConstants.TICK_GAME_HOURS;
      // Speicher plus Lager: der Energiespeicher (Umsetzungskonzept/32_...md) ist die
      // Reserve, die Produktionsketten nicht anfassen; das Lager der Rest.
      double stock = Math.floor(EnergyStorageCommands.totalFuel(state, colony.id));
      // Verbrauch in GANZEN Zellen über das Übertragskonto: bei Infrastruktur 6
      // sind je Tick nur 0,0188 Zellen fällig, eine ganze also erst alle 53 Ticks
      // (Umsetzungskonzept/25_...md).
      double due = FractionPot.due(state, FractionPot.key("power", colony.id), need);
      double covered = EnergyStorageCommands.drawForUpkeep(state, colony.id, Math.min(due, stock));
      // Ist gerade nichts fällig, entscheidet der blanke Vorrat: eine Kolonie ohne
      // eine einzige Zelle im Lager gilt als unversorgt, auch wenn in diesem Tick
      // nichts abgebucht wurde.
      double instantRatio = due > 0 ? covered / due : (stock >= 1 ? 1 : 0);
      double alpha = Formulas.smoothingAlpha(Formulas.POWER_COVERAGE_SMOOTHING_TAU_HOURS);
      ps.coverageRatio = prevRatio * (1 - alpha) + instantRatio * alpha;
      next.add(ps);
    }
    state.powerStates.clear();
    state.powerStates.addAll(next);
  }

  /** Problem-Code: ein Grundbedarfsgut ist für die Bevölkerung nicht (ausreichend) zu kaufen (Umsetzungskonzept/32_...md, Teil B). */
  /** Unter dieser Deckung gilt ein Gut als unversorgt. */
  private static final double SUPPLY_WARNING_BELOW = 0.5;
  /** Höchstens eine Warnung je Gut und Kolonie je Spieltag. */
  /**
   * REALZEIT-AUSNAHME (siehe {@code GameConstants.SUPPLY_WARNING_COOLDOWN_REAL_MS}):
   * Mindestabstand zweier gleicher Versorgungswarnungen in ECHTEN Minuten.
   *
   * <p>Vorher stand hier eine Frist von 24 SPIELSTUNDEN. Bei
   * {@code gameSpeedMultiplier = 4} sind das 15 Realsekunden – je Kolonie und
   * je fehlendem Gut. Die Glocke enthielt dadurch praktisch nur noch
   * Versorgungswarnungen und verdrängte alles andere, auch Kampfmeldungen.
   * Wie oft ein Mensch dieselbe Warnung sehen will, hängt nicht am
   * Tempo-Regler – deshalb Realzeit.</p>
   */

  /**
   * Warnt den Kommandanten, wenn ein Grundbedarfsgut nicht zu kaufen ist – weil
   * keine Verkaufsorder steht (im Testlauf der Bot-Armee blieb der Lebensstandard
   * deshalb bei 50 %, Konzept 31 Befund 3) oder weil die Bevölkerung sich den Preis
   * nicht leisten kann (Befund 11). Die Entscheidung bleibt beim Kommandanten; die
   * Warnung nennt nur, was fehlt.
   */
  private static void warnAboutSupplyGaps(GameState state, IdGenerator ids, Colony colony, Map<String, Double> coverageByGood) {
    Population population = null;
    for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p;
    if (population == null || population.currentCount < 1) return;
    long now = Clock.now();
    // REALZEIT-AUSNAHME: bereits Realzeit-Millisekunden, NICHT über Clock.hoursToMs.
    long cooldownMs = GameConstants.SUPPLY_WARNING_COOLDOWN_REAL_MS;
    for (Map.Entry<String, Double> e : coverageByGood.entrySet()) {
      if (e.getValue() >= SUPPLY_WARNING_BELOW) continue;
      String key = colony.id + ":" + e.getKey();
      Long last = state.lastSupplyWarningAt.get(key);
      if (last != null && now - last < cooldownMs) continue;
      state.lastSupplyWarningAt.put(key, now);
      boolean anyOrder = state.sellOrders.stream()
          .anyMatch(o -> o.systemId.equals(colony.systemId) && o.productTypeId.equals(e.getKey()) && o.remainingQuantity > 0);
      String goodName = de.nebula.data.ProductCatalog.find(e.getKey()).name;
      String message = anyOrder
          ? "Die Bevölkerung von \"" + colony.name + "\" kann sich " + goodName + " nicht leisten (Deckung "
              + Math.round(e.getValue() * 100) + " %) – Preis der Verkaufsorder prüfen, das Bevölkerungs-Wallet gibt nicht mehr her."
          : "In \"" + colony.name + "\" gibt es keine Verkaufsorder für " + goodName + " – die Bevölkerung kauft nur aus Orders ihres Systems, "
              + "der Lebensstandard bleibt ohne dieses Gut gedeckelt.";
      Notifications.notify(state, ids, NotificationType.Problem, Notifications.CODE_SUPPLY_GAP, message,
          colony.id, Notifications.colonyLink(colony.id));
    }
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
    if (t - state.lastStatsSnapshotAt < Clock.hoursToMs(GameConstants.STATS_SNAPSHOT_INTERVAL_GAME_HOURS)) return;
    state.lastStatsSnapshotAt = t;
    // Bevölkerungsverlauf je Kolonie im selben Takt mitschreiben (Umsetzungskonzept/18_...md).
    PopulationHistory.record(state, t);
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

  /**
   * Meldet die drei Lagen, die eine Kolonie bzw. ein Reich still zugrunde
   * richten: Energieausfall, anhaltender Bevölkerungsrückgang und ein leer
   * laufendes Konto.
   *
   * <p>Vorher gab es dafür KEINE Benachrichtigung. In einem Testlauf fiel eine
   * Kolonie von 7.791 auf 115 Einwohner und das Guthaben von 139.581 auf 0
   * Credits, ohne dass die Oberfläche das an irgendeiner Stelle gemeldet
   * hätte – der Blackout war nur an einer Farbänderung und einem angehängten
   * Halbsatz zu erkennen.</p>
   *
   * <p>Alle drei Meldungen sind FLANKENGETRIEBEN
   * ({@link Notifications#edgeTriggered}): sie kommen einmal beim Eintritt in
   * die Lage und einmal, wenn sie vorbei ist – nicht bei jedem Tick.</p>
   */
  public static void notifyColonyAndTreasuryStates(GameState state, IdGenerator ids) {
    for (Colony colony : state.colonies) {
      boolean blackout = PowerGrid.isBlackout(state, colony.id);
      if (Notifications.edgeTriggered(state, "blackout:" + colony.id, blackout)) {
        if (blackout) {
          Notifications.notify(state, ids, NotificationType.Problem, Notifications.CODE_BLACKOUT,
              "Energieausfall in \"" + colony.name + "\": Der Infrastruktur fehlt Stabilisiertes Elerium. "
                  + "Produktion, Sicherheit und Lebensstandard sind stark eingeschränkt, "
                  + "bis wieder Nachschub im Lager liegt.",
              colony.id, Notifications.colonyLink(colony.id));
        } else {
          Notifications.notify(state, ids, NotificationType.Info, Notifications.CODE_POWER_RESTORED,
              "Die Energieversorgung von \"" + colony.name + "\" läuft wieder.",
              colony.id, Notifications.colonyLink(colony.id));
        }
      }

      Population population = null;
      for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p;
      if (population == null) continue;
      // Gemeldet wird erst ein DEUTLICHER Rückgang, nicht jedes Zucken um Null:
      // die Rate ist eine geglättete Größe je Spielstunde.
      boolean shrinking = population.growthRatePerInterval < -0.001 && population.currentCount > 0;
      if (Notifications.edgeTriggered(state, "shrinking:" + colony.id, shrinking) && shrinking) {
        Notifications.notify(state, ids, NotificationType.Warnung, Notifications.CODE_POPULATION_SHRINKING,
            "Die Bevölkerung von \"" + colony.name + "\" schrumpft. Lebensstandard und Sicherheit prüfen – "
                + "unter 30 % Lebensstandard wandern die Menschen ab.",
            colony.id, Notifications.colonyLink(colony.id));
      }
    }

    for (Player player : state.players) {
      Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
      if (wallet == null) continue;
      double perHour = treasuryFlowPerHour(state, player.id);
      boolean empty = wallet.balance <= 0.5;
      // "Läuft leer" = negatives Ergebnis UND weniger als ein Spieltag Reserve.
      boolean draining = !empty && perHour < 0 && wallet.balance < Math.abs(perHour) * 24;

      if (Notifications.edgeTriggered(state, "treasuryEmpty:" + player.id, empty) && empty) {
        Notifications.notify(state, ids, NotificationType.Problem, Notifications.CODE_TREASURY_EMPTY,
            "Ihr Guthaben ist aufgebraucht. Gebäude- und Flottenunterhalt laufen weiter – "
                + "Einnahmen schaffen Verkaufsorders für Konsumgüter, entlasten tut ein Rückbau.",
            null, "/konto");
      }
      if (Notifications.edgeTriggered(state, "treasuryLow:" + player.id, draining) && draining) {
        Notifications.notify(state, ids, NotificationType.Warnung, Notifications.CODE_TREASURY_LOW,
            "Ihre laufenden Kosten übersteigen die Einnahmen (" + Math.round(perHour)
                + " Cr je Spielstunde). Das Guthaben reicht noch keinen Spieltag.",
            null, "/konto");
      }
    }
  }

  /**
   * Saldo des Kommandanten-Wallets je SPIELSTUNDE: Konsumeinnahmen minus
   * Löhne, Gebäude- und Flottenunterhalt. Dieselbe Rechnung wie in
   * {@link #payUpkeepAndWages}/{@link #runConsumption}, nur nicht auf einen
   * Tick heruntergebrochen – die Zahl, die in der Kopfzeile neben dem Guthaben
   * steht und deren Fehlen den Bankrott im Testlauf unsichtbar gemacht hat.
   */
  public static double treasuryFlowPerHour(GameState state, String playerId) {
    double outflow = 0;
    double inflow = 0;
    for (Colony colony : state.colonies) {
      if (!colony.ownerId.equals(playerId)) continue;
      for (Building b : state.buildings) {
        if (b.colonyId.equals(colony.id) && b.level > 0) {
          outflow += BuildingCatalog.find(b.typeId).upkeepPerLevel * b.level;
        }
      }
      for (var f : state.fleets) {
        if (colony.id.equals(f.locationColonyId)) {
          for (var g : f.ships) outflow += g.quantity * 0.5;
        }
      }
      for (Population p : state.populations) {
        if (p.colonyId.equals(colony.id)) outflow += p.currentCount * 0.02;
      }
      // Einnahmen: was die Bevölkerung dieser Kolonie je Spielstunde für
      // Konsumgüter ausgibt, landet über die Verkaufsorders beim Kommandanten.
      inflow += consumptionSpendPerHour(state, colony);
    }
    return inflow - outflow;
  }

  /**
   * Konsumausgaben der Bevölkerung einer Kolonie je Spielstunde – Gegenstück zu
   * {@link #runConsumption}, dort aber je Tick und je Gut aufgelöst. Hier
   * genügt die Rate: Pro-Kopf-Bedarf × Einwohner × tatsächliche Deckung ×
   * günstigster Preis, über die Grundgüter summiert.
   */
  private static double consumptionSpendPerHour(GameState state, Colony colony) {
    double population = 0;
    for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p.currentCount;
    if (population <= 0) return 0;
    Map<String, Double> coverage = state.consumptionCoverage.get(colony.id);
    if (coverage == null) return 0;

    double spend = 0;
    for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
      double covered = coverage.getOrDefault(goodId, 0.0);
      if (covered <= 0) continue;
      double need = population * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(goodId);
      // Nur eigene Orders zahlen auf das eigene Konto ein – fremde Orders in
      // demselben System liefern zwar Waren, das Geld geht aber woandershin.
      double bestOwnPrice = Double.NaN;
      for (SellOrder o : state.sellOrders) {
        if (!o.systemId.equals(colony.systemId) || o.remainingQuantity <= 0) continue;
        if (!o.productTypeId.equals(goodId) || !colony.ownerId.equals(o.sellerId)) continue;
        if (Double.isNaN(bestOwnPrice) || o.pricePerUnit < bestOwnPrice) bestOwnPrice = o.pricePerUnit;
      }
      if (Double.isNaN(bestOwnPrice)) continue;
      spend += need * Math.min(covered, 1.0) * bestOwnPrice;
    }
    return spend;
  }
}
