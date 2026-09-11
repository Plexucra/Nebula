package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.data.ProductCosts;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.BuildingCategory;
import de.nebula.model.BuildingType;
import de.nebula.model.Colony;
import de.nebula.model.ColonyPowerState;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.MarketOrder;
import de.nebula.model.NotificationType;
import de.nebula.model.Planet;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationGrowthState;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.PopulationSupply;
import de.nebula.model.ProductType;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.RecruitmentQueueEntry;
import de.nebula.model.ShipyardQueueEntry;
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
 * ({@link #colonyDay}, Umsetzungskonzept/36). Seit Umsetzungskonzept/38 mit
 * Löhnen je Arbeitsstunde, Kauforders der Bevölkerung und zwei Klassen
 * (Arbeiter und Akademiker):
 *
 * <p><b>Gebote statt Einkauf.</b> Die Bevölkerung stellt am EIGENEN
 * planetaren Handelsposten stehende Kauforders ({@link #refreshPopulationBids}):
 * Menge ist die Lücke bis zum Vorrat von {@code POPULATION_STOCK_TARGET_DAYS}
 * Tagesbedarfen, das Limit folgt aus Tagesbudget, Guthaben und Bedarf der
 * gesamten Bevölkerung. Das Orderbuch bedient die Gebote, sobald eine
 * passende Verkaufsorder erscheint; am Kolonietag werden sie erneuert.</p>
 *
 * <p><b>Güterstaffel.</b> Je Wohnstufe kommt ein Pflichtgut hinzu
 * ({@code CONSUMER_GOODS_ORDER}); das nächste Gut ist das Wachstumsgut, das
 * mitgekauft wird und ohne volle Deckung die Kolonie an der Stufengrenze
 * hält. Akademiker brauchen zusätzlich {@code ACADEMIC_GOODS_ORDER} nach
 * Zentrumsstufe und entstehen nur in bezahlte Plätze eines Forschungszentrums.</p>
 *
 * <p>Die Reihenfolge im Kolonietag ist tragend – NICHT umstellen:
 * {@link #recalcCoreStats} liest den in {@link #consumePower} gesetzten
 * Energiestand und den in {@link #consumeFromStock} gesetzten Lebensstandard,
 * {@link #growPopulationAndMoneySupply} die Kernwerte und die Deckungen
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

  /** Bei Gründung: sofort Gebote stellen, erster Kolonietag einen Spieltag später – die Gründungszeit ist die Tageszeit der Kolonie. */
  public static void startColonyRhythm(GameState state, IdGenerator ids, Colony colony) {
    refreshPopulationBids(state, ids, colony);
    scheduleColonyDay(state, colony.id, colony.foundedAt + (long) GameConstants.GAME_DAY_MS);
  }

  /** Ereignis {@code COLONY_DAY}: der ganze Tag EINER Kolonie. Eine nicht mehr existierende Kolonie ist ein veraltetes Ereignis. */
  public static void colonyDay(GameState state, IdGenerator ids, String colonyId, long t) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) return;
    consumePower(state, colonyId);
    payUpkeepAndWages(state, ids, colony);
    refreshPopulationBids(state, ids, colony);
    consumeFromStock(state, ids, colony);
    recalcCoreStats(state, colony, t);
    growPopulationAndMoneySupply(state, ids, colony);
    notifyColonyState(state, ids, colony);
    notifyTreasuryState(state, ids, colony.ownerId);
  }

  private static double stockOf(Population population, String goodId) {
    return population.stock.getOrDefault(goodId, 0.0);
  }

  // --- Bedarf: Staffel der Arbeiter, Güter der Akademiker -----------------------

  /**
   * Der Bedarf EINER Kolonie für einen Spieltag (Umsetzungskonzept/38, Teil D):
   * Pflichtgüter der Wohnstufe, das Wachstumsgut der nächsten Stufe und die
   * Akademikergüter der Zentrumsstufen, jeweils mit Tagesbedarf über die
   * gesamte Bevölkerung. Reihenfolge = Einkaufsreihenfolge = Vorrang.
   */
  public static final class Demand {
    public final Map<String, Double> dailyNeed = new LinkedHashMap<>();
    public final Map<String, PopulationSupply.Group> group = new LinkedHashMap<>();
    public int stage;
    public double stageCap;
    public String growthGood;
    public int researchLevel;
    public List<String> essentials = List.of();
    public List<String> academicGoods = List.of();
  }

  static double housingCapacityPerLevel() {
    Integer perLevel = BuildingCatalog.find(GameConstants.HOUSING_BUILDING_ID).housingCapacityPerLevel;
    return perLevel == null ? 20000 : perLevel;
  }

  public static Demand demand(GameState state, Colony colony, Population population) {
    Demand d = new Demand();
    double total = population.currentCount;
    double workers = population.workers();
    double academics = population.academics;
    double perLevel = housingCapacityPerLevel();
    d.stage = Formulas.consumerStage(total, perLevel, GameConstants.HOUSING_GROWTH_FACTOR);
    d.stageCap = Formulas.consumerStageCap(d.stage, perLevel, GameConstants.HOUSING_GROWTH_FACTOR);
    List<String> order = GameConstants.CONSUMER_GOODS_ORDER;
    int essentialCount = Math.min(d.stage, order.size());
    d.essentials = order.subList(0, essentialCount);
    d.growthGood = d.stage < order.size() ? order.get(d.stage) : null;
    for (int i = 0; i < essentialCount; i++) {
      String good = order.get(i);
      d.dailyNeed.put(good, total * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(good) * GameConstants.GAME_DAY_HOURS);
      d.group.put(good, PopulationSupply.Group.Essential);
    }
    if (d.growthGood != null) {
      d.dailyNeed.put(d.growthGood, total * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.get(d.growthGood) * GameConstants.GAME_DAY_HOURS);
      d.group.put(d.growthGood, PopulationSupply.Group.Growth);
    }
    d.researchLevel = GameQueries.getBuildingLevel(state, colony.id, GameConstants.RESEARCH_BUILDING_ID);
    if (d.researchLevel >= 1 || academics > 0) {
      // Ohne Akademiker fragt die Kolonie ihre Güter für einen Startterm nach – sonst
      // gäbe es nie eine Deckung, aus der der erste Akademiker entstehen könnte.
      double persons = Math.max(academics, workers * GameConstants.ACADEMIC_SEED_SHARE_OF_WORKERS);
      List<String> aOrder = GameConstants.ACADEMIC_GOODS_ORDER;
      int levels = Math.max(d.researchLevel, 1);
      d.academicGoods = aOrder.subList(0, Math.min(aOrder.size(), GameConstants.ACADEMIC_BASE_GOODS_COUNT + levels - 1));
      for (String good : d.academicGoods) {
        double need = persons * GameConstants.ACADEMIC_NEED_PER_CAPITA_PER_HOUR.get(good) * GameConstants.GAME_DAY_HOURS;
        d.dailyNeed.merge(good, need, Double::sum);
        d.group.putIfAbsent(good, PopulationSupply.Group.Academic);
      }
    }
    return d;
  }

  /** Bedarf eines Konsumguts je Spieltag bei dieser Bevölkerung (bruchteilig – die Stückelung macht das Übertragskonto). */
  public static double dailyNeed(double population, String goodId) {
    return population * GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR.getOrDefault(goodId, 0.0) * GameConstants.GAME_DAY_HOURS;
  }

  /** Bezahlte Akademikerplätze der Kolonie – Summe über die Forschungszentren, je Stufe verdoppelt (Umsetzungskonzept/38). */
  public static double researchCapacity(GameState state, String colonyId) {
    double sum = 0;
    for (Building b : state.buildings) {
      if (!b.colonyId.equals(colonyId)) continue;
      BuildingType type = BuildingCatalog.find(b.typeId);
      if (type.category != BuildingCategory.Research || type.researchCapacityPerLevel == null) continue;
      sum += Formulas.housingCapacity(type.researchCapacityPerLevel, b.level);
    }
    return sum;
  }

  /**
   * Deckel der Akademiker: die Kapazität der höchsten Zentrumsstufe, deren
   * Güter alle voll gedeckt sind (Deckung ≥ 1,0 am letzten Kolonietag). Ohne
   * Zentrum 0; fehlt schon ein Gut der Stufe 1, ebenfalls 0.
   */
  public static double academicCap(GameState state, Colony colony, Demand d) {
    if (d.researchLevel < 1) return 0;
    Map<String, Double> coverage = state.consumptionCoverage.getOrDefault(colony.id, Map.of());
    BuildingType type = BuildingCatalog.find(GameConstants.RESEARCH_BUILDING_ID);
    double perLevel = type.researchCapacityPerLevel == null ? 0 : type.researchCapacityPerLevel;
    List<String> aOrder = GameConstants.ACADEMIC_GOODS_ORDER;
    double cap = 0;
    for (int level = 1; level <= d.researchLevel; level++) {
      int goods = Math.min(aOrder.size(), GameConstants.ACADEMIC_BASE_GOODS_COUNT + level - 1);
      boolean covered = true;
      for (int i = 0; i < goods; i++) {
        if (coverage.getOrDefault(aOrder.get(i), 0.0) < Formulas.FOOD_COVERAGE_FOR_GROWTH) {
          covered = false;
          break;
        }
      }
      if (!covered) break;
      cap = Formulas.housingCapacity(perLevel, level);
    }
    return Math.min(cap, researchCapacity(state, colony.id));
  }

  /**
   * Deckel der Güterstaffel für das Wachstum: die Stufengrenze, solange das
   * Wachstumsgut nicht voll gedeckt ist, sonst unbegrenzt (der Wohnraum
   * begrenzt dann). Vor dem ersten Kolonietag gilt das Gut als gedeckt – eine
   * frisch gegründete Kolonie hängt nicht an einer ungemessenen Lage.
   */
  public static double goodsCap(GameState state, Colony colony, Demand d) {
    if (d.growthGood == null) return Double.MAX_VALUE;
    Map<String, Double> coverage = state.consumptionCoverage.get(colony.id);
    if (coverage == null) return Double.MAX_VALUE;
    return coverage.getOrDefault(d.growthGood, 0.0) >= Formulas.FOOD_COVERAGE_FOR_GROWTH ? Double.MAX_VALUE : d.stageCap;
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

  // --- Unterhalt und Forschungsgehälter -------------------------------------------

  /**
   * Gebäude- und Flottenunterhalt EINES Spieltags sowie die Gehälter der
   * Akademiker (24 Stunden je Kopf zum Lohnsatz, Umsetzungskonzept/38, Teil D),
   * vom Kommandanten- ins Bevölkerungs-Wallet. Reicht das Guthaben nicht für
   * alle Akademiker, gehen die unbezahlten sofort zurück zu den Arbeitern –
   * es gibt keine unbezahlten Akademiker. Die Löhne der Produktion bucht
   * {@link Wages} beim Start jedes Auftrags.
   */
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
    double day = GameConstants.GAME_DAY_HOURS;
    payFromOwnerWallet(state, ids, ownerWallet, popWallet, buildingUpkeep * day, TransactionReason.BuildingUpkeep, "Gebäudeunterhalt");
    payFromOwnerWallet(state, ids, ownerWallet, popWallet, fleetUpkeep * day, TransactionReason.FleetUpkeep, "Flottenunterhalt");

    Population population = ColonyCommands.population(state, colony.id);
    if (population == null || population.academics < 1) return;
    double perAcademic = Formulas.academicWagePerDay();
    double due = population.academics * perAcademic;
    double paid = payFromOwnerWallet(state, ids, ownerWallet, popWallet, due, TransactionReason.Wage,
        "Forschungsgehalt für " + Math.round(population.academics) + " Akademiker");
    if (paid + 0.001 < due) {
      double keep = Math.floor(paid / perAcademic);
      double unpaid = population.academics - keep;
      population.academics = keep;
      Notifications.notify(state, ids, NotificationType.Warnung, Notifications.CODE_POPULATION_SHRINKING,
          Math.round(unpaid) + " Akademiker in \"" + colony.name + "\" sind unbezahlt in die Arbeiterschaft zurückgekehrt – "
              + "das Guthaben reichte nicht für die Forschungsgehälter.", colony.id, Notifications.colonyLink(colony.id));
    }
  }

  /** Credits je Schiff und Spielstunde, das an einer Kolonie liegt. */
  public static final double FLEET_UPKEEP_PER_SHIP_PER_HOUR = 0.5;

  /** @return der tatsächlich gezahlte Betrag (gedeckelt durch das Guthaben) */
  private static double payFromOwnerWallet(GameState state, IdGenerator ids, Wallet ownerWallet,
                                           Wallet popWallet, double amount, TransactionReason reason, String note) {
    // ownerWallet ist das LIVE-Objekt aus state.wallets – vorherige Buchungen
    // desselben Tages sind in balance bereits enthalten.
    double affordable = Math.min(amount, Math.max(ownerWallet.balance, 0));
    if (affordable > 0.001) Ledger.recordTx(state, ids, ownerWallet.id, popWallet.id, affordable, reason, note);
    return affordable > 0.001 ? affordable : 0;
  }

  // --- Gebote der Bevölkerung (Umsetzungskonzept/38, Teil C) ------------------------

  /**
   * Erneuert die Kauforders der Bevölkerung: alte zurückziehen (Escrow zurück),
   * Einkommen glätten, Tagesbudget bilden, je Gut mit Vorratslücke ein Gebot
   * stellen und matchen.
   *
   * <p>Verteilung des Budgets in zwei Schritten: erst deckt es in
   * Einkaufsreihenfolge, was jedes Gut zum aktuellen Briefkurs kostet
   * (Grundsicherung – Grundnahrung vor Luxus), dann hebt der Rest alle Gebote
   * im Verhältnis {@code Ankerpreis × Tagesbedarf}. Ist ein Grundgut teurer
   * als das ganze Budget, liegt sein Gebot unter dem Brief, es wird nichts
   * gekauft, und der Kommandant sieht am Gebot, was die Bevölkerung tragen
   * kann.</p>
   */
  public static void refreshPopulationBids(GameState state, IdGenerator ids, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    Wallet popWallet = GameQueries.findWallet(state, WalletOwnerType.Population, colony.id);
    if (population == null || popWallet == null) return;
    MarketCommands.cancelPopulationBids(state, colony.id);
    if (population.currentCount <= 0) return;

    // Einkommen: der Zufluss seit dem letzten Kolonietag, geglättet.
    double inflow = state.populationInflowSinceLastDay.getOrDefault(colony.id, 0.0);
    state.populationInflowSinceLastDay.remove(colony.id);
    Double previous = state.populationDailyIncome.get(colony.id);
    double alpha = Formulas.smoothingAlpha(GameConstants.POPULATION_INCOME_SMOOTHING_DAYS * GameConstants.GAME_DAY_HOURS,
        GameConstants.GAME_DAY_HOURS);
    double income = previous == null ? inflow : previous * (1 - alpha) + inflow * alpha;
    state.populationDailyIncome.put(colony.id, income);

    double wallet = Math.max(popWallet.balance, 0);
    double budget = Math.min(wallet, income + wallet / GameConstants.POPULATION_STOCK_TARGET_DAYS);
    state.populationDailyBudget.put(colony.id, budget);
    if (budget < 0.01) return;

    Demand d = demand(state, colony, population);
    Map<String, Double> gaps = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : d.dailyNeed.entrySet()) {
      double target = Math.ceil(e.getValue() * GameConstants.POPULATION_STOCK_TARGET_DAYS);
      double gap = target - stockOf(population, e.getKey());
      if (gap >= 1) gaps.put(e.getKey(), gap);
    }
    if (gaps.isEmpty()) return;

    // 1. Grundsicherung in Einkaufsreihenfolge zum aktuellen Brief.
    double remaining = budget;
    Map<String, Double> share = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : gaps.entrySet()) {
      Double ask = cheapestAsk(state, colony, e.getKey());
      double base = 0;
      if (ask != null && remaining > 0) {
        base = Math.min(remaining, ask * e.getValue());
        remaining -= base;
      }
      share.put(e.getKey(), base);
    }
    // 2. Der Rest im Verhältnis Ankerpreis × Tagesbedarf.
    double weightSum = 0;
    for (String good : gaps.keySet()) weightSum += ProductCosts.of(good) * d.dailyNeed.get(good);
    Map<String, Double> surplus = new LinkedHashMap<>();
    for (String good : gaps.keySet()) {
      double weight = weightSum > 0 ? ProductCosts.of(good) * d.dailyNeed.get(good) / weightSum : 1.0 / gaps.size();
      surplus.put(good, remaining * weight);
    }
    for (Map.Entry<String, Double> e : gaps.entrySet()) {
      // Die Grundsicherung gilt je Stück der Lücke (so trifft das Gebot den
      // Brief); der Überschuss wird auf den ganzen Wochenvorrat umgelegt, nicht
      // nur auf die Lücke – sonst zahlt eine satte Kolonie für die letzten
      // 30 Stück das Zehnfache des Ankerpreises (im 40-Bot-Lauf 9 → 138 Cr).
      double gap = e.getValue();
      double target = Math.ceil(d.dailyNeed.get(e.getKey()) * GameConstants.POPULATION_STOCK_TARGET_DAYS);
      double perUnit = share.get(e.getKey()) / gap + surplus.get(e.getKey()) / Math.max(gap, target);
      double limit = Math.floor(perUnit * 100 + 1e-6) / 100.0; // auf Cent, ohne Rundungsfehler unter den Brief zu rutschen
      if (limit < 0.01) continue;
      MarketCommands.createPopulationBid(state, ids, colony, e.getKey(), e.getValue(), limit);
    }
  }

  /** Günstigster Brief am eigenen Posten, den die Bevölkerung kaufen darf; {@code null} ohne Order. */
  static Double cheapestAsk(GameState state, Colony colony, String goodId) {
    List<MarketOrder> asks = ownPostOrders(state, colony, goodId);
    return asks.isEmpty() ? null : asks.get(0).limitPrice;
  }

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
    if (order.ownerId == null || order.populationColonyId != null) return false;
    return order.ownerId.equals(colony.ownerId) || TreatyCommands.hasTradeAgreement(state, colony.ownerId, order.ownerId);
  }

  // --- Verbrauch und Lebensstandard ----------------------------------------

  /**
   * Isst den Tagesbedarf aller nachgefragten Güter aus dem Vorrat und misst
   * daran die Versorgung: je Gut {@code gedeckt × (1 + 0,5 × Vorratsreichweite/Ziel)},
   * also 1,0 wenn der Tag gedeckt war, bis 1,5 mit vollem Vorrat, 0 ohne
   * Essen – ein leerer Vorrat bekommt keinen Bonus. Daraus zwei Lebensstandards
   * (Umsetzungskonzept/38, Teil D): der der Arbeiter aus den Pflichtgütern
   * (Grundnahrung doppelt gewichtet), der der Akademiker aus ALLEN Gütern.
   * Das Wachstumsgut zählt nur für die Akademiker und den Staffeldeckel.
   * Beide geglättet über {@code LIVING_STANDARD_SMOOTHING_TAU_HOURS}.
   */
  public static void consumeFromStock(GameState state, IdGenerator ids, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    if (population == null || stats == null) return;
    Demand d = demand(state, colony, population);

    double workerSum = 0, workerWeight = 0, academicSum = 0, academicWeight = 0;
    Map<String, Double> coverageByGood = new LinkedHashMap<>();
    for (Map.Entry<String, Double> e : d.dailyNeed.entrySet()) {
      String goodId = e.getKey();
      double need = e.getValue();
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
      if (d.group.get(goodId) == PopulationSupply.Group.Essential) {
        workerSum += coverage * weight;
        workerWeight += weight;
      }
      academicSum += coverage * weight;
      academicWeight += weight;
    }
    state.consumptionCoverage.put(colony.id, coverageByGood);
    warnAboutSupplyGaps(state, ids, colony, population, coverageByGood);

    double alpha = Formulas.smoothingAlpha(Formulas.LIVING_STANDARD_SMOOTHING_TAU_HOURS, GameConstants.GAME_DAY_HOURS);
    double blackoutFactor = PowerGrid.isBlackout(state, colony.id) ? Formulas.BLACKOUT_STAT_FACTOR : 1;

    double prevRaw = state.rawStandardOfLiving.getOrDefault(colony.id, stats.standardOfLivingPct);
    double newStandard = workerWeight > 0 ? (workerSum / workerWeight) * 100 : prevRaw;
    double smoothedRaw = prevRaw * (1 - alpha) + newStandard * alpha;
    state.rawStandardOfLiving.put(colony.id, smoothedRaw);
    stats.standardOfLivingPct = Formulas.clamp(smoothedRaw * blackoutFactor, 0, 200);

    double prevAcademic = state.rawAcademicStandardOfLiving.getOrDefault(colony.id, stats.academicStandardOfLivingPct);
    double newAcademic = academicWeight > 0 ? (academicSum / academicWeight) * 100 : prevAcademic;
    double smoothedAcademic = prevAcademic * (1 - alpha) + newAcademic * alpha;
    state.rawAcademicStandardOfLiving.put(colony.id, smoothedAcademic);
    stats.academicStandardOfLivingPct = Formulas.clamp(smoothedAcademic * blackoutFactor, 0, 200);
  }

  /** Unter dieser Deckung gilt ein Gut als unversorgt (Problem-Code {@code CODE_SUPPLY_GAP}, Umsetzungskonzept/32_...md, Teil B). */
  private static final double SUPPLY_WARNING_BELOW = 0.5;

  /**
   * Warnt den Kommandanten, wenn ein nachgefragtes Gut fehlt – weil am eigenen
   * Handelsposten keine Verkaufsorder steht oder weil sie über dem Gebot der
   * Bevölkerung liegt. Die Entscheidung bleibt beim Kommandanten; die Warnung
   * nennt nur, was fehlt und was die Bevölkerung bietet.
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
      Double ask = cheapestAsk(state, colony, e.getKey());
      Double bid = null;
      for (MarketOrder o : MarketCommands.populationBids(state, colony.id)) if (o.productTypeId.equals(e.getKey())) bid = o.limitPrice;
      String goodName = ProductCatalog.find(e.getKey()).name;
      String bidText = bid == null ? "" : " Die Bevölkerung bietet " + bid + " Cr je Stück.";
      String message = ask != null
          ? "Die Bevölkerung von \"" + colony.name + "\" kann sich " + goodName + " nicht leisten (Deckung "
              + Math.round(e.getValue() * 100) + " %) – die günstigste Verkaufsorder liegt bei " + ask + " Cr." + bidText
          : "In \"" + colony.name + "\" gibt es keine kaufbare Verkaufsorder für " + goodName + " – die Bevölkerung kauft nur am eigenen "
              + "Handelsposten und nur von Ihnen oder von Handelsvertragspartnern." + bidText;
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

  /**
   * Wachstum der Gesamtbevölkerung gegen Wohnraum, Nahrungs- und Staffeldeckel
   * (Umsetzungskonzept/38, Teil D), danach der Wechsel zwischen Arbeitern und
   * Akademikern – die Gesamtzahl ändert nur das Wachstum, der Wechsel nie.
   * Wachstumsgeld entsteht wie bisher nur über dem Höchststand des Planeten.
   */
  public static void growPopulationAndMoneySupply(GameState state, IdGenerator ids, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    Planet planet = ColonyCommands.planet(state, colony.planetId);
    if (population == null || stats == null || planet == null) return;

    double capacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
    Demand d = demand(state, colony, population);
    double goodsCap = goodsCap(state, colony, d);
    // Der Nahrungsdeckel (Umsetzungskonzept/34_...md, §J 5) liest die Deckung
    // aus DIESEM Tag: consumeFromStock läuft unmittelbar vorher.
    double perHour = Formulas.populationGrowthDelta(population.currentCount, capacity, goodsCap, stats.standardOfLivingPct,
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

    // Akademiker: aus den Arbeitern in bezahlte Plätze, bei Mangel zurück.
    double cap = academicCap(state, colony, d);
    double academicPerHour;
    if (stats.academicStandardOfLivingPct < Formulas.LIVING_STANDARD_SHRINK_BELOW_PCT && population.academics > 0) {
      academicPerHour = Formulas.academicShrinkDelta(population.academics, stats.academicStandardOfLivingPct);
      if (population.academics > cap) academicPerHour = Math.min(academicPerHour, Formulas.academicGrowthDelta(population.academics, population.workers(), cap));
    } else if (population.academics > cap || stats.academicStandardOfLivingPct >= Formulas.LIVING_STANDARD_GROWTH_FROM_PCT) {
      academicPerHour = Formulas.academicGrowthDelta(population.academics, population.workers(), cap);
    } else {
      academicPerHour = 0;
    }
    if (academicPerHour > 0 && PowerGrid.isBlackout(state, colony.id)) academicPerHour = 0;
    double academicDelta = FractionPot.due(state, FractionPot.key("academics", colony.id), academicPerHour * GameConstants.GAME_DAY_HOURS);
    population.academics = Formulas.clamp(population.academics + academicDelta, 0, newCount);

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

  /** Wachstumszustand samt Staffeldeckel – für Anzeige und Verlauf. */
  public static PopulationGrowthState growthState(GameState state, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    if (population == null || stats == null) return PopulationGrowthState.Holding;
    double capacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
    Demand d = demand(state, colony, population);
    return Formulas.populationGrowthState(population.currentCount, capacity, goodsCap(state, colony, d),
        stats.standardOfLivingPct, ColonyCommands.foodCoverage(state, colony.id));
  }

  /** Wachstumsrate je Spielstunde samt Staffeldeckel – für Anzeige und Verlauf. */
  public static double growthPerHour(GameState state, Colony colony) {
    Population population = ColonyCommands.population(state, colony.id);
    PlanetStats stats = ColonyCommands.colonyStats(state, colony.id);
    if (population == null || stats == null) return 0;
    double capacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
    Demand d = demand(state, colony, population);
    return Formulas.populationGrowthDelta(population.currentCount, capacity, goodsCap(state, colony, d),
        stats.standardOfLivingPct, stats.securityPct, ColonyCommands.foodCoverage(state, colony.id));
  }

  /** Forschungsniveau eines Kommandanten: die Akademiker aller seiner Kolonien (Umsetzungskonzept/38, Teil D). */
  public static int researchLevel(GameState state, String playerId) {
    double sum = 0;
    for (Colony c : state.colonies) {
      if (!c.ownerId.equals(playerId)) continue;
      Population p = ColonyCommands.population(state, c.id);
      if (p != null) sum += p.academics;
    }
    return (int) Math.round(sum);
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
          "Ihr Guthaben ist aufgebraucht. Gebäude- und Flottenunterhalt laufen weiter, neue Aufträge starten ohne Löhne nicht – "
              + "Einnahmen schaffen Verkäufe an die Gebote Ihrer Bevölkerung, entlasten tut ein Rückbau.",
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

  /** Versorgungslage für die Oberfläche: je Gut Vorrat, Tagesbedarf, Reichweite, Deckung, Gebot und Brief; dazu Klassen, Staffel und Budget. */
  public static PopulationSupply populationSupply(GameState state, String colonyId) {
    PopulationSupply result = new PopulationSupply();
    result.targetDays = GameConstants.POPULATION_STOCK_TARGET_DAYS;
    Population population = ColonyCommands.population(state, colonyId);
    if (population == null) return result;
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) return result;
    PlanetStats stats = ColonyCommands.colonyStats(state, colonyId);
    Long next = GameEvents.scheduledAt(state, GameEventType.COLONY_DAY, colonyId);
    result.nextPurchaseAt = next != null ? next : 0;
    Map<String, Double> coverage = state.consumptionCoverage.getOrDefault(colonyId, Map.of());
    Demand d = demand(state, colony, population);
    Map<String, MarketOrder> bids = new LinkedHashMap<>();
    for (MarketOrder o : MarketCommands.populationBids(state, colonyId)) bids.put(o.productTypeId, o);
    for (Map.Entry<String, Double> e : d.dailyNeed.entrySet()) {
      PopulationSupply.Good good = new PopulationSupply.Good();
      good.productTypeId = e.getKey();
      good.name = ProductCatalog.find(e.getKey()).name;
      good.group = d.group.get(e.getKey());
      good.stock = stockOf(population, e.getKey());
      good.dailyNeed = e.getValue();
      good.daysLeft = good.dailyNeed > 0 ? good.stock / good.dailyNeed : 0;
      good.coverage = coverage.get(e.getKey());
      good.askPrice = cheapestAsk(state, colony, e.getKey());
      good.orderAvailable = good.askPrice != null;
      MarketOrder bid = bids.get(e.getKey());
      good.bidPrice = bid != null ? bid.limitPrice : null;
      good.bidQuantity = bid != null ? bid.remainingQuantity : 0;
      result.goods.add(good);
    }
    result.workers = population.workers();
    result.academics = population.academics;
    result.consumerStage = d.stage;
    result.consumerStageCap = d.stageCap;
    result.growthGoodId = d.growthGood;
    result.growthGoodCovered = goodsCap(state, colony, d) == Double.MAX_VALUE;
    result.researchCapacity = researchCapacity(state, colonyId);
    result.academicCap = academicCap(state, colony, d);
    result.researchLevel = d.researchLevel;
    result.academicStandardOfLivingPct = stats != null ? stats.academicStandardOfLivingPct : 0;
    result.dailyIncome = state.populationDailyIncome.getOrDefault(colonyId, 0.0);
    result.dailyBudget = state.populationDailyBudget.getOrDefault(colonyId, 0.0);
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
   * Gebäude- und Flottenunterhalt, Forschungsgehälter und die Löhne der
   * laufenden Aufträge (Lohn des Auftrags über seine Laufzeit verteilt).
   * Dieselbe Rechnung wie in {@link #payUpkeepAndWages} und {@link Wages},
   * nur als Rate statt als Tagesbetrag – die Zahl, die in der Kopfzeile neben
   * dem Guthaben steht.
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
      if (p != null) outflow += p.academics * GameConstants.WAGE_PER_WORK_HOUR;
      outflow += runningWagesPerHour(state, colony.id);
      // Einnahmen: was die Bevölkerung dieser Kolonie je Spielstunde für
      // Konsumgüter ausgibt, landet über die Verkaufsorders beim Kommandanten.
      inflow += consumptionSpendPerHour(state, colony);
    }
    return inflow - outflow;
  }

  /** Löhne der laufenden Aufträge aller drei Warteschlangen einer Kolonie, auf ihre Laufzeit verteilt. */
  private static double runningWagesPerHour(GameState state, String colonyId) {
    double perHour = 0;
    for (ProductionQueueEntry e : state.productionQueue) {
      if (e.colonyId.equals(colonyId) && e.status == ProductionQueueStatus.running && e.plan.totalHours > 0) perHour += e.plan.wageCredits / e.plan.totalHours;
    }
    for (ShipyardQueueEntry e : state.shipyardQueue) {
      if (e.colonyId.equals(colonyId) && e.status == ProductionQueueStatus.running && e.plan.totalHours > 0) perHour += e.plan.wageCredits / e.plan.totalHours;
    }
    for (RecruitmentQueueEntry e : state.recruitmentQueue) {
      if (e.colonyId.equals(colonyId) && e.status == ProductionQueueStatus.running && e.plan.totalHours > 0) perHour += e.plan.wageCredits / e.plan.totalHours;
    }
    return perHour;
  }

  /**
   * Konsumausgaben der Bevölkerung einer Kolonie je Spielstunde an den EIGENEN
   * Kommandanten: Tagesbedarf × tatsächliche Deckung × Ausführungspreis, wobei
   * der Preis der niedrigere von eigenem Brief und Gebot ist (der Preis der
   * älteren Order, näherungsweise). Ohne kreuzendes Paar fließt nichts.
   */
  private static double consumptionSpendPerHour(GameState state, Colony colony) {
    Population p = ColonyCommands.population(state, colony.id);
    if (p == null || p.currentCount <= 0) return 0;
    Map<String, Double> coverage = state.consumptionCoverage.get(colony.id);
    if (coverage == null) return 0;
    Demand d = demand(state, colony, p);
    Map<String, Double> bids = new LinkedHashMap<>();
    for (MarketOrder o : MarketCommands.populationBids(state, colony.id)) bids.put(o.productTypeId, o.limitPrice);

    double spend = 0;
    for (Map.Entry<String, Double> e : d.dailyNeed.entrySet()) {
      double covered = coverage.getOrDefault(e.getKey(), 0.0);
      if (covered <= 0) continue;
      double needPerHour = e.getValue() / GameConstants.GAME_DAY_HOURS;
      // Nur eigene Orders zahlen auf das eigene Konto ein – fremde Orders am
      // eigenen Posten liefern zwar Waren, das Geld geht aber woandershin.
      double bestOwnPrice = Double.NaN;
      for (MarketOrder o : ownPostOrders(state, colony, e.getKey())) {
        if (!colony.ownerId.equals(o.ownerId)) continue;
        if (Double.isNaN(bestOwnPrice) || o.limitPrice < bestOwnPrice) bestOwnPrice = o.limitPrice;
      }
      Double bid = bids.get(e.getKey());
      if (Double.isNaN(bestOwnPrice) || bid == null || bid + 1e-9 < bestOwnPrice) continue;
      spend += needPerHour * Math.min(covered, 1.0) * Math.min(bestOwnPrice, bid);
    }
    return spend;
  }
}
