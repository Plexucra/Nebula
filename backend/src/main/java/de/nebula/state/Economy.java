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
import de.nebula.model.NotificationType;
import de.nebula.model.Planet;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.PopulationSupply;
import de.nebula.model.ProductType;
import de.nebula.model.MarketOrder;
import de.nebula.model.TransactionReason;
import de.nebula.model.UniverseStatSnapshot;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Wirtschaft einer Kolonie als EIN Ereignis je Spieltag – der Kolonietag
 * ({@link #colonyDay}, Umsetzungskonzept/36). Vorher lief dasselbe als
 * galaxieweiter Schritt jede Realsekunde ({@code EconomyTick.economyStep});
 * jetzt rechnet jede Kolonie einmal je Spieltag zu ihrer eigenen Tageszeit
 * (Gründungszeit + n Tage), und dazwischen rechnet niemand.
 *
 * <p><b>Tageseinkauf und Vorrat.</b> Die Bevölkerung kauft ausschließlich am
 * EIGENEN planetaren Handelsposten (Orders mit {@code depotColonyId} = diese
 * Kolonie) und füllt ihren Vorrat je Grundkonsumgut auf
 * {@code POPULATION_STOCK_TARGET_DAYS} Tagesbedarfe auf. Gegessen wird aus
 * dem Vorrat; der Vorrat federt Orderlücken, Blockaden und das Wachstum
 * innerhalb eines Tages ab. Genau ein Käufer je Posten – deshalb gibt es
 * keinen Wettlauf mehrerer Kolonien um dieselbe Order.</p>
 *
 * <p><b>Notkauf.</b> Sinkt der Vorrat unter
 * {@code POPULATION_EMERGENCY_PURCHASE_BELOW_DAYS} und am eigenen Posten
 * erscheint eine Order (neu, umgepreist oder nachgefüllt), kauft die Kolonie
 * sofort nach ({@link #emergencyPurchase}) – der Orderbuch-Push, begrenzt auf
 * den Fall, in dem er etwas bringt.</p>
 *
 * <p>Die Reihenfolge im Kolonietag ist tragend – NICHT umstellen:
 * {@link #recalcCoreStats} liest den in {@link #consumePower} gesetzten
 * Energiestand und den in {@link #consumeFromStock} gesetzten Lebensstandard,
 * {@link #growPopulationAndMoneySupply} die Kernwerte und die Nahrungsdeckung
 * desselben Tages, und die Flankenmeldungen am Ende sehen den fertigen
 * Zustand. {@code t} ist die Ereigniszeit.</p>
 */
public final class Economy {
  private Economy() {
  }

  // --- Kolonietag ------------------------------------------------------------

  /** Plant den nächsten Kolonietag; {@code at} ist die Fälligkeit (Spielzeit). */
  public static void scheduleColonyDay(GameState state, String colonyId, long at) {
    GameEvents.schedule(state, GameEventType.COLONY_DAY, colonyId, at);
  }

  /** Bei Gründung: sofort einkaufen, erster Kolonietag einen Spieltag später – die Gründungszeit ist die Tageszeit der Kolonie. */
  public static void startColonyRhythm(GameState state, IdGenerator ids, Colony colony) {
    stockUp(state, ids, colony.id);
    scheduleColonyDay(state, colony.id, colony.foundedAt + (long) GameConstants.GAME_DAY_MS);
  }

  /** Ereignis {@code COLONY_DAY}: der ganze Tag EINER Kolonie. Eine nicht mehr existierende Kolonie ist ein veraltetes Ereignis. */
  public static void colonyDay(GameState state, IdGenerator ids, String colonyId, long t) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) return;
    consumePower(state, colonyId);
    payUpkeepAndWages(state, ids, colony);
    purchase(state, ids, colony, null);
    consumeFromStock(state, ids, colony);
    recalcCoreStats(state, colony, t);
    growPopulationAndMoneySupply(state, ids, colony);
    notifyColonyState(state, ids, colony);
    notifyTreasuryState(state, ids, colony.ownerId);
  }

  /** Bedarf eines Grundkonsumguts je Spieltag bei dieser Bevölkerung (bruchteilig – die Stückelung macht das Übertragskonto). */
  public static double dailyNeed(double population, String goodId) {
    return population * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.getOrDefault(goodId, 0.0) * GameConstants.GAME_DAY_HOURS;
  }

  private static double stockOf(Population population, String goodId) {
    return population.stock.getOrDefault(goodId, 0.0);
  }

  // --- Energie -----------------------------------------------------------------

  /**
   * Infrastrukturverbrauch EINES Spieltags (Umsetzungskonzept/17_...md, Teil A),
   * zuerst aus dem Energiespeicher, dann aus dem Lager, in ganzen Zellen über
   * das Übertragskonto. Was nicht gedeckt ist, bleibt als {@code shortfall}
   * stehen und wird beim nächsten Elerium-Zugang sofort nachgeholt
   * ({@link PowerGrid#settleShortfall}); ein neuer Tag beginnt ohne Altlast –
   * eine Infrastruktur im Blackout hat nicht gearbeitet und schuldet nichts.
   */
  public static void consumePower(GameState state, String colonyId) {
    int level = GameQueries.getBuildingLevel(state, colonyId, GameConstants.INFRASTRUCTURE_BUILDING_ID);
    ColonyPowerState ps = PowerGrid.stateOf(state, colonyId);
    if (level <= 0) {
      ps.coverageRatio = 1;
      ps.dueToday = 0;
      ps.shortfall = 0;
      return;
    }
    double need = Formulas.infrastructureEleriumPerHour(level) * GameConstants.GAME_DAY_HOURS;
    double stock = Math.floor(EnergyStorageCommands.totalFuel(state, colonyId));
    double due = FractionPot.due(state, FractionPot.key("power", colonyId), need);
    double covered = EnergyStorageCommands.drawForUpkeep(state, colonyId, Math.min(due, stock));
    ps.dueToday = due;
    ps.shortfall = due - covered;
    // Ist gerade nichts fällig, entscheidet der blanke Vorrat: eine Kolonie ohne
    // eine einzige Zelle gilt als unversorgt, auch wenn heute nichts abgebucht wurde.
    ps.coverageRatio = due > 0 ? covered / due : (stock >= 1 ? 1 : 0);
  }

  // --- Unterhalt und Löhne --------------------------------------------------

  /** Gebäude- und Flottenunterhalt sowie Löhne EINES Spieltags, vom Kommandanten- ins Bevölkerungs-Wallet. */
  public static void payUpkeepAndWages(GameState state, IdGenerator ids, Colony colony) {
    Wallet ownerWallet = GameQueries.findWallet(state, WalletOwnerType.Player, colony.ownerId);
    Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
    if (ownerWallet == null || popWallet == null) return;

    double buildingUpkeep = 0;
    for (Building b : state.buildings) {
      if (b.colonyId.equals(colony.id) && b.level > 0) {
        buildingUpkeep += BuildingCatalog.find(b.typeId).upkeepPerLevel * b.level;
      }
    }
    double fleetUpkeep = 0;
    for (var f : state.fleets) {
      if (colony.id.equals(f.locationColonyId)) {
        for (var g : f.ships) fleetUpkeep += g.quantity * FLEET_UPKEEP_PER_SHIP_PER_HOUR;
      }
    }
    Population population = ColonyCommands.population(state, colony.id);
    double wage = (population != null ? population.currentCount : 0) * WAGE_PER_CAPITA_PER_HOUR;

    double day = GameConstants.GAME_DAY_HOURS;
    payFromOwnerWallet(state, ids, ownerWallet, popWallet, buildingUpkeep * day, TransactionReason.BuildingUpkeep, "Gebäudeunterhalt");
    payFromOwnerWallet(state, ids, ownerWallet, popWallet, fleetUpkeep * day, TransactionReason.FleetUpkeep, "Flottenunterhalt");
    payFromOwnerWallet(state, ids, ownerWallet, popWallet, wage * day, TransactionReason.Wage, "Löhne");
  }

  /** Credits je Schiff und Spielstunde, das an einer Kolonie liegt. */
  public static final double FLEET_UPKEEP_PER_SHIP_PER_HOUR = 0.5;
  /** Credits je Einwohner und Spielstunde – EINE Quelle in {@code shared/game-constants.json} (auch Preisanker in {@code ProductCosts}). */
  public static final double WAGE_PER_CAPITA_PER_HOUR = GameConstants.WAGE_PER_CAPITA_PER_HOUR;

  private static void payFromOwnerWallet(GameState state, IdGenerator ids, Wallet ownerWallet,
                                          Wallet popWallet, double amount, TransactionReason reason, String note) {
    // ownerWallet ist das LIVE-Objekt aus state.wallets – vorherige Buchungen
    // desselben Tages sind in balance bereits enthalten.
    double affordable = Math.min(amount, Math.max(ownerWallet.balance, 0));
    if (affordable > 0.001) Ledger.recordTx(state, ids, ownerWallet.id, popWallet.id, affordable, reason, note);
  }

  // --- Tageseinkauf ------------------------------------------------------------

  /** Füllt den Vorrat aller Grundkonsumgüter auf – für Gründung und Seed; sonst Teil des Kolonietags. */
  public static void stockUp(GameState state, IdGenerator ids, String colonyId) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony != null) purchase(state, ids, colony, null);
  }

  /**
   * Notkauf: eine Order am eigenen Handelsposten ist neu, umgepreist oder
   * nachgefüllt. Liegt der Vorrat dieses Guts unter der Notkauf-Schwelle, kauft
   * die Bevölkerung sofort auf, statt bis zum nächsten Kolonietag zu warten.
   * Für alle anderen Güter und volle Vorräte ist das ein Nichts – der
   * Regelfall bleibt der Tageseinkauf.
   */
  public static void emergencyPurchase(GameState state, IdGenerator ids, String colonyId, String goodId) {
    if (colonyId == null || !GameConstants.CONSUMER_GOODS_ORDER.contains(goodId)) return;
    Colony colony = ColonyCommands.colony(state, colonyId);
    Population population = ColonyCommands.population(state, colonyId);
    if (colony == null || population == null) return;
    double need = dailyNeed(population.currentCount, goodId);
    if (need <= 0) return;
    if (stockOf(population, goodId) / need >= GameConstants.POPULATION_EMERGENCY_PURCHASE_BELOW_DAYS) return;
    purchase(state, ids, colony, goodId);
  }

  /**
   * Kauft je Grundkonsumgut so viel nach, dass der Vorrat das Ziel
   * ({@code POPULATION_STOCK_TARGET_DAYS} Tagesbedarfe) erreicht – aus dem
   * Bevölkerungs-Wallet, zu gleichen Teilen auf die Güter verteilt, wobei ein
   * nicht ausgegebener Anteil den folgenden Gütern zufließt. {@code onlyGoodId}
   * beschränkt den Kauf auf ein Gut (Notkauf).
   */
  private static void purchase(GameState state, IdGenerator ids, Colony colony, String onlyGoodId) {
    Population population = ColonyCommands.population(state, colony.id);
    Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
    if (population == null || popWallet == null || population.currentCount <= 0) return;

    double remaining = Math.max(popWallet.balance, 0);
    int goodsLeft = GameConstants.CONSUMER_GOODS_ORDER.size();
    for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
      double goodBudget = remaining / goodsLeft--;
      if (onlyGoodId != null && !onlyGoodId.equals(goodId)) continue;
      double target = Math.ceil(dailyNeed(population.currentCount, goodId) * GameConstants.POPULATION_STOCK_TARGET_DAYS);
      double toBuy = target - stockOf(population, goodId);
      if (toBuy < 1) continue;
      remaining -= buyAtOwnPost(state, ids, colony, population, popWallet, goodId, toBuy, goodBudget);
    }
  }

  /**
   * Kauft bis zu {@code quantity} Stück am eigenen Handelsposten, günstigste
   * Order zuerst, in ganzen Stücken und im Budget. Mehrere Durchgänge, weil
   * eine leer gekaufte Dauerorder sich sofort aus dem Lager nachfüllt
   * ({@link MarketCommands#settleSellOrderPurchase}) und dann weiter liefern kann.
   *
   * @return ausgegebene Credits
   */
  private static double buyAtOwnPost(GameState state, IdGenerator ids, Colony colony, Population population,
                                     Wallet popWallet, String goodId, double quantity, double budget) {
    double spent = 0;
    double bought = 0;
    for (int pass = 0; pass < MAX_PURCHASE_PASSES && bought < quantity; pass++) {
      boolean progress = false;
      for (MarketOrder order : ownPostOrders(state, colony, goodId)) {
        if (spent >= budget - 1e-9 || bought >= quantity) break;
        double affordable = Math.floor((budget - spent) / order.limitPrice);
        double qty = Math.min(Math.min(affordable, Math.floor(order.remainingQuantity)), quantity - bought);
        if (qty < 1) continue;
        double cost = Math.round(qty * order.limitPrice * 100) / 100.0;
        // Ohne Käufer-Id: die Ware geht in den Vorrat, nicht in ein Lager oder Depot.
        MarketCommands.settleAsk(state, ids, order, qty, cost, popWallet.id, null);
        spent += cost;
        bought += qty;
        progress = true;
      }
      if (!progress) break;
    }
    if (bought > 0) population.stock.merge(goodId, bought, Double::sum);
    return spent;
  }

  private static final int MAX_PURCHASE_PASSES = 10;

  /**
   * Kaufbare Verkaufs-Orders am Handelsposten des Planeten dieser Kolonie,
   * günstigste zuerst. Die Bevölkerung kauft nur bei ihrem eigenen
   * Kommandanten oder bei Kommandanten, mit denen er einen Handelsvertrag hat
   * (dieselbe Vertragsregel wie im Matching des Postens, Konzept 05 §14) –
   * ein fremder Händler ohne Vertrag erreicht die Bevölkerung nicht.
   */
  static List<MarketOrder> ownPostOrders(GameState state, Colony colony, String goodId) {
    List<MarketOrder> orders = new ArrayList<>();
    for (MarketOrder o : MarketCommands.sellOrdersAtPost(state, colony.systemId, colony.planetId, goodId)) {
      if (mayPopulationBuyFrom(state, colony, o)) orders.add(o);
    }
    return orders;
  }

  /** Verkäufer ist der eigene Kommandant oder ein Handelsvertragspartner. Orders ohne Eigentümer (Handelsgilde) gibt es am Posten nicht. */
  static boolean mayPopulationBuyFrom(GameState state, Colony colony, MarketOrder order) {
    if (order.ownerId == null) return false;
    return order.ownerId.equals(colony.ownerId) || TreatyCommands.hasTradeAgreement(state, colony.ownerId, order.ownerId);
  }

  // --- Verbrauch und Lebensstandard ----------------------------------------

  /**
   * Isst den Tagesbedarf aus dem Vorrat und misst daran die Versorgung: je Gut
   * {@code gedeckt × (1 + 0,5 × Vorratsreichweite/Ziel)}, also 1,0 wenn der
   * Tag gedeckt war, bis 1,5 mit vollem Vorrat, 0 ohne Essen – ein leerer
   * Vorrat bekommt keinen Bonus. Daraus der Lebensstandard (Grundnahrung
   * doppelt gewichtet), geglättet über {@code LIVING_STANDARD_SMOOTHING_TAU_HOURS}.
   */
  public static void consumeFromStock(GameState state, IdGenerator ids, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    if (population == null || stats == null) return;

    double coverageSum = 0;
    double weightSum = 0;
    Map<String, Double> coverageByGood = new LinkedHashMap<>();
    for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
      double need = dailyNeed(population.currentCount, goodId);
      if (need <= 0) continue;
      // Gegessen wird in GANZEN Stücken: der Bruchteil wandert ins Übertragskonto
      // (Umsetzungskonzept/25_...md).
      double due = FractionPot.due(state, FractionPot.key("consume", colony.id, goodId), need);
      double have = stockOf(population, goodId);
      double eaten = Math.min(have, due);
      double left = have - eaten;
      population.stock.put(goodId, left);
      double fed = due > 0 ? eaten / due : (have >= 1 ? 1 : 0);
      double reserveShare = Math.min(1, left / need / GameConstants.POPULATION_STOCK_TARGET_DAYS);
      double coverage = Formulas.clamp(fed * (1 + 0.5 * reserveShare), 0, 1.5);
      coverageByGood.put(goodId, coverage);
      double weight = goodId.equals(GameConstants.FOOD_PRODUCT_ID) ? 2 : 1;
      coverageSum += coverage * weight;
      weightSum += weight;
    }
    state.consumptionCoverage.put(colony.id, coverageByGood);
    warnAboutSupplyGaps(state, ids, colony, population, coverageByGood);

    double prevRaw = state.rawStandardOfLiving.getOrDefault(colony.id, stats.standardOfLivingPct);
    double newStandard = weightSum > 0 ? (coverageSum / weightSum) * 100 : prevRaw;
    double alpha = Formulas.smoothingAlpha(Formulas.LIVING_STANDARD_SMOOTHING_TAU_HOURS, GameConstants.GAME_DAY_HOURS);
    double smoothedRaw = prevRaw * (1 - alpha) + newStandard * alpha;
    state.rawStandardOfLiving.put(colony.id, smoothedRaw);
    double effective = PowerGrid.isBlackout(state, colony.id) ? smoothedRaw * Formulas.BLACKOUT_STAT_FACTOR : smoothedRaw;
    stats.standardOfLivingPct = Formulas.clamp(effective, 0, 200);
  }

  /** Unter dieser Deckung gilt ein Gut als unversorgt (Problem-Code {@code CODE_SUPPLY_GAP}, Umsetzungskonzept/32_...md, Teil B). */
  private static final double SUPPLY_WARNING_BELOW = 0.5;

  /**
   * Warnt den Kommandanten, wenn ein Grundbedarfsgut fehlt – weil am eigenen
   * Handelsposten keine Verkaufsorder steht oder weil die Bevölkerung sich den
   * Preis nicht leisten kann. Die Entscheidung bleibt beim Kommandanten; die
   * Warnung nennt nur, was fehlt.
   *
   * <p>REALZEIT-AUSNAHME ({@code GameConstants.SUPPLY_WARNING_COOLDOWN_REAL_MS}):
   * der Mindestabstand zweier gleicher Warnungen zählt in ECHTEN Minuten. Wie
   * oft ein Mensch dieselbe Warnung sehen will, hängt nicht am Tempo-Regler.</p>
   */
  private static void warnAboutSupplyGaps(GameState state, IdGenerator ids, Colony colony, Population population,
                                          Map<String, Double> coverageByGood) {
    if (population.currentCount < 1) return;
    // REALZEIT-AUSNAHME: Realuhr auf BEIDEN Seiten des Vergleichs (siehe Clock), NICHT über Clock.hoursToMs.
    long now = Clock.realNow();
    long cooldownMs = GameConstants.SUPPLY_WARNING_COOLDOWN_REAL_MS;
    for (Map.Entry<String, Double> e : coverageByGood.entrySet()) {
      if (e.getValue() >= SUPPLY_WARNING_BELOW) continue;
      String key = colony.id + ":" + e.getKey();
      Long last = state.lastSupplyWarningAt.get(key);
      if (last != null && now - last < cooldownMs) continue;
      state.lastSupplyWarningAt.put(key, now);
      boolean anyOrder = !ownPostOrders(state, colony, e.getKey()).isEmpty();
      String goodName = ProductCatalog.find(e.getKey()).name;
      String message = anyOrder
          ? "Die Bevölkerung von \"" + colony.name + "\" kann sich " + goodName + " nicht leisten (Deckung "
              + Math.round(e.getValue() * 100) + " %) – Preis der Verkaufsorder prüfen, das Bevölkerungs-Wallet gibt nicht mehr her."
          : "In \"" + colony.name + "\" gibt es keine kaufbare Verkaufsorder für " + goodName + " – die Bevölkerung kauft nur am eigenen "
              + "Handelsposten und nur von Ihnen oder von Handelsvertragspartnern; der Lebensstandard bleibt ohne dieses Gut gedeckelt.";
      Notifications.notify(state, ids, NotificationType.Problem, Notifications.CODE_SUPPLY_GAP, message,
          colony.id, Notifications.colonyLink(colony.id));
    }
  }

  // --- Kernwerte und Wachstum ----------------------------------------------

  public static void recalcCoreStats(GameState state, Colony colony, long t) {
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    if (stats == null) return;
    Population p = ColonyCommands.population(state, colony.id);
    double population = p != null ? p.currentCount : 0;

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
    double loyaltyDelta = Formulas.loyaltyDelta(colony.isHomeworld, stats.standardOfLivingPct, security) * GameConstants.GAME_DAY_HOURS;

    stats.infrastructurePct = infra;
    stats.securityPct = security;
    stats.loyaltyPct = Formulas.clamp(stats.loyaltyPct + loyaltyDelta, 0, 100);
    stats.lastRecalculatedAt = t;
  }

  public static void growPopulationAndMoneySupply(GameState state, IdGenerator ids, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    Planet planet = ColonyCommands.planet(state, colony.planetId);
    if (population == null || stats == null || planet == null) return;

    double capacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
    // Der Nahrungsdeckel (Umsetzungskonzept/34_...md, §J 5) liest die Deckung
    // aus DIESEM Tag: consumeFromStock läuft unmittelbar vorher.
    double perHour = Formulas.populationGrowthDelta(population.currentCount, capacity, stats.standardOfLivingPct,
        stats.securityPct, ColonyCommands.foodCoverage(state, colony.id));
    // Blackout unterbindet nur Wachstum – Schrumpfung durch Überbevölkerung (negatives Delta) läuft unabhängig davon normal weiter.
    if (perHour > 0 && PowerGrid.isBlackout(state, colony.id)) perHour = 0;
    double delta = perHour * GameConstants.GAME_DAY_HOURS;
    // Einwohner sind ganze Menschen: der Bruchteil sammelt sich im Übertragskonto
    // (Umsetzungskonzept/25_...md). Die RATE bleibt bruchteilig.
    double wholeDelta = FractionPot.due(state, FractionPot.key("population", colony.id), delta);
    double newCount = Math.max(0, population.currentCount + wholeDelta);
    population.currentCount = newCount;
    population.growthRatePerInterval = perHour;

    PopulationMoneySupplyState moneyState = ColonyCommands.moneySupplyState(state, colony.planetId);
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

  // --- Flankenmeldungen -----------------------------------------------------

  /**
   * Meldet die Lagen, die eine Kolonie still zugrunde richten: Energieausfall
   * und anhaltender Bevölkerungsrückgang. FLANKENGETRIEBEN
   * ({@link Notifications#edgeTriggered}): einmal beim Eintritt, einmal beim
   * Ende – nicht an jedem Tag.
   */
  public static void notifyColonyState(GameState state, IdGenerator ids, Colony colony) {
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

    Population population = ColonyCommands.population(state, colony.id);
    if (population == null) return;
    // Gemeldet wird erst ein DEUTLICHER Rückgang, mit Hysterese: ab -0,01
    // Einwohner je Spielstunde, entwarnt erst bei echtem Wachstum.
    boolean wasShrinking = Boolean.TRUE.equals(state.notificationEdgeState.get("shrinking:" + colony.id));
    boolean shrinking = population.currentCount > 0
        && (wasShrinking ? population.growthRatePerInterval <= 0 : population.growthRatePerInterval < -0.01);
    if (Notifications.edgeTriggered(state, "shrinking:" + colony.id, shrinking) && shrinking) {
      Notifications.notify(state, ids, NotificationType.Warnung, Notifications.CODE_POPULATION_SHRINKING,
          "Die Bevölkerung von \"" + colony.name + "\" schrumpft. Lebensstandard und Sicherheit prüfen – "
              + "unter 30 % Lebensstandard wandern die Menschen ab.",
          colony.id, Notifications.colonyLink(colony.id));
    }
  }

  /** Leer laufendes oder leeres Konto des Kommandanten – flankengetrieben, an ihn adressiert. */
  public static void notifyTreasuryState(GameState state, IdGenerator ids, String playerId) {
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, playerId);
    if (wallet == null) return;
    double perHour = treasuryFlowPerHour(state, playerId);
    boolean empty = wallet.balance <= 0.5;
    // "Läuft leer" = negatives Ergebnis UND weniger als ein Spieltag Reserve.
    boolean draining = !empty && perHour < 0 && wallet.balance < Math.abs(perHour) * GameConstants.GAME_DAY_HOURS;

    if (Notifications.edgeTriggered(state, "treasuryEmpty:" + playerId, empty) && empty) {
      // An den Kommandanten adressiert – ohne Adresse wäre die Meldung global
      // und stünde in JEDER fremden Glocke (siehe Notifications.notifyPlayer).
      Notifications.notifyPlayer(state, ids, NotificationType.Problem, Notifications.CODE_TREASURY_EMPTY,
          "Ihr Guthaben ist aufgebraucht. Gebäude- und Flottenunterhalt laufen weiter – "
              + "Einnahmen schaffen Verkaufsorders für Konsumgüter, entlasten tut ein Rückbau.",
          playerId, "/konto");
    }
    if (Notifications.edgeTriggered(state, "treasuryLow:" + playerId, draining) && draining) {
      Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_TREASURY_LOW,
          "Ihre laufenden Kosten übersteigen die Einnahmen (" + Math.round(perHour)
              + " Cr je Spielstunde). Das Guthaben reicht noch keinen Spieltag.",
          playerId, "/konto");
    }
  }

  // --- Anzeige -----------------------------------------------------------------

  /** Versorgungslage für die Oberfläche: Vorrat, Tagesbedarf, Reichweite, Deckung je Gut und der nächste Einkauf. */
  public static PopulationSupply populationSupply(GameState state, String colonyId) {
    PopulationSupply result = new PopulationSupply();
    result.targetDays = GameConstants.POPULATION_STOCK_TARGET_DAYS;
    result.emergencyBelowDays = GameConstants.POPULATION_EMERGENCY_PURCHASE_BELOW_DAYS;
    Population population = ColonyCommands.population(state, colonyId);
    if (population == null) return result;
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) return result;
    Long next = GameEvents.scheduledAt(state, GameEventType.COLONY_DAY, colonyId);
    result.nextPurchaseAt = next != null ? next : 0;
    Map<String, Double> coverage = state.consumptionCoverage.getOrDefault(colonyId, Map.of());
    for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
      PopulationSupply.Good good = new PopulationSupply.Good();
      good.productTypeId = goodId;
      good.name = ProductCatalog.find(goodId).name;
      good.stock = stockOf(population, goodId);
      good.dailyNeed = dailyNeed(population.currentCount, goodId);
      good.daysLeft = good.dailyNeed > 0 ? good.stock / good.dailyNeed : 0;
      good.coverage = coverage.get(goodId);
      good.orderAvailable = !ownPostOrders(state, colony, goodId).isEmpty();
      result.goods.add(good);
    }
    return result;
  }

  // --- Galaxieweite Aufgaben -------------------------------------------------

  /**
   * Ausgleichsfonds gegen Geldhortung (Konzeption/Spieldesign/06_..., §8 und
   * Mechanik/10_..., §7): einmal pro Spieltag (Ereignis {@code WEALTH_REDISTRIBUTION})
   * zahlen große Spieler-/Kommandanten-Wallets 0,1% ihres Guthabens und jede
   * Kolonie 1% ihres Bevölkerungs-Wallets in einen galaxieweiten Topf ein, der
   * im selben Lauf komplett pro Kopf an alle Bevölkerungs-Wallets
   * zurückverteilt wird.
   */
  public static void runWealthRedistribution(GameState state, IdGenerator ids) {
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

  /** Zeitreihe aggregierter Stabilitätskennzahlen über die gesamte Galaxie, siehe {@link #recordStatsSnapshot}. */
  public static List<UniverseStatSnapshot> universeStats(GameState state) {
    return List.copyOf(state.universeStats);
  }

  /** Ereignis {@code STATS_SNAPSHOT}, alle {@code STATS_SNAPSHOT_INTERVAL_GAME_HOURS}. */
  public static void recordStatsSnapshot(GameState state, long t) {
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
    for (MarketOrder o : state.marketOrders) {
      if (o.planetId != null && o.side == de.nebula.model.MarketOrderSide.Sell && o.remainingQuantity > 0) openSellOrders++;
    }

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
   * Saldo des Kommandanten-Wallets je SPIELSTUNDE: Konsumeinnahmen minus
   * Löhne, Gebäude- und Flottenunterhalt. Dieselbe Rechnung wie in
   * {@link #payUpkeepAndWages}/{@link #purchase}, nur als Rate statt als
   * Tagesbetrag – die Zahl, die in der Kopfzeile neben dem Guthaben steht.
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
          for (var g : f.ships) outflow += g.quantity * FLEET_UPKEEP_PER_SHIP_PER_HOUR;
        }
      }
      Population p = ColonyCommands.population(state, colony.id);
      if (p != null) outflow += p.currentCount * WAGE_PER_CAPITA_PER_HOUR;
      // Einnahmen: was die Bevölkerung dieser Kolonie je Spielstunde für
      // Konsumgüter ausgibt, landet über die Verkaufsorders beim Kommandanten.
      inflow += consumptionSpendPerHour(state, colony);
    }
    return inflow - outflow;
  }

  /**
   * Konsumausgaben der Bevölkerung einer Kolonie je Spielstunde – Gegenstück
   * zum Tageseinkauf als Rate: Pro-Kopf-Bedarf × Einwohner × tatsächliche
   * Deckung × günstigster eigener Preis, über die Grundgüter summiert.
   */
  private static double consumptionSpendPerHour(GameState state, Colony colony) {
    Population p = ColonyCommands.population(state, colony.id);
    if (p == null || p.currentCount <= 0) return 0;
    Map<String, Double> coverage = state.consumptionCoverage.get(colony.id);
    if (coverage == null) return 0;

    double spend = 0;
    for (String goodId : GameConstants.CONSUMER_GOODS_ORDER) {
      double covered = coverage.getOrDefault(goodId, 0.0);
      if (covered <= 0) continue;
      double need = p.currentCount * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(goodId);
      // Nur eigene Orders zahlen auf das eigene Konto ein – fremde Orders am
      // eigenen Posten liefern zwar Waren, das Geld geht aber woandershin.
      double bestOwnPrice = Double.NaN;
      for (MarketOrder o : ownPostOrders(state, colony, goodId)) {
        if (!colony.ownerId.equals(o.ownerId)) continue;
        if (Double.isNaN(bestOwnPrice) || o.limitPrice < bestOwnPrice) bestOwnPrice = o.limitPrice;
      }
      if (Double.isNaN(bestOwnPrice)) continue;
      spend += need * Math.min(covered, 1.0) * bestOwnPrice;
    }
    return spend;
  }
}
