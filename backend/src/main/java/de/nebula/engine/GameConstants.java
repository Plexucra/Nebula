package de.nebula.engine;

import java.util.List;
import java.util.Map;

/**
 * 1:1-Portierung der modul-weiten Konstanten aus dem Kopf von
 * {@code simulated-game-api.service.ts}, soweit von bereits portierten
 * Systemen benötigt. Wächst mit jeder weiteren Phase (siehe
 * Umsetzungskonzept/13_...md) um die dort jeweils benötigten Konstanten.
 */
public final class GameConstants {
  private GameConstants() {
  }

  /**
   * Der Spieltag – der Takt der Kolonie-Wirtschaft (Umsetzungskonzept/36):
   * Unterhalt, Löhne, Einkauf, Verbrauch, Kernwerte und Wachstum werden je
   * Kolonie EINMAL je Spieltag verbucht ({@code Economy.colonyDay}). Jede
   * dieser Größen ist als RATE JE SPIELSTUNDE definiert und wird mit
   * {@link #GAME_DAY_HOURS} multipliziert – nie als fester Betrag je Schritt.
   */
  public static final double GAME_DAY_HOURS = 24;
  public static final double GAME_DAY_MS = Clock.hoursToMs(GAME_DAY_HOURS);

  /**
   * Tageseinkauf der Bevölkerung (Umsetzungskonzept/36): Vorratsziel in
   * Tagesbedarfen je Grundkonsumgut und die Schwelle, unter der eine neue
   * Order am eigenen Handelsposten einen sofortigen Notkauf auslöst.
   */
  public static final double POPULATION_STOCK_TARGET_DAYS = SharedConstants.populationStockTargetDays();
  public static final double POPULATION_EMERGENCY_PURCHASE_BELOW_DAYS = SharedConstants.populationEmergencyPurchaseBelowDays();

  /** Ausgleichsfonds gegen Geldhortung (Konzeption/Spieldesign/06_..., §8 und Mechanik/10_..., §7). */
  public static final double WEALTH_TAX_THRESHOLD = 1000;
  public static final double WEALTH_TAX_RATE = 0.001;
  public static final double COLONY_TAX_RATE = 0.01;

  /**
   * Elerium-Verbrauch des Gebäudes "Infrastruktur" (Umsetzungskonzept/17_...md,
   * Teil A): je Spielstunde {@code BASE × Stufe^EXPONENT} – leicht überlinear,
   * Werte aus {@code shared/game-constants.json}.
   */
  public static final double ELERIUM_UPKEEP_BASE_PER_HOUR = SharedConstants.eleriumUpkeepBasePerHour();
  public static final double ELERIUM_UPKEEP_LEVEL_EXPONENT = SharedConstants.eleriumUpkeepLevelExponent();
  public static final String INFRASTRUCTURE_FUEL_PRODUCT_ID = "p_elerium_stabil";
  /** Automatische Vorhaltemenge des Energiespeichers in Spielstunden Infrastrukturverbrauch (Umsetzungskonzept/32_...md). */
  public static final double ENERGY_RESERVE_DEFAULT_GAME_HOURS = SharedConstants.energyReserveDefaultGameHours();
  public static final String INFRASTRUCTURE_BUILDING_ID = "b_infrastructure";
  /** Planetare Abwehr (Mechanik/05_...md §9, {@code LandingCommands}-Landungsabwehr). */
  public static final String PLANETARY_DEFENSE_BUILDING_ID = "b_defense";
  /** Bebauungsplätze je Infrastruktur-Stufe; jede Stufe jedes anderen Gebäudes belegt genau einen. */
  public static final int SLOTS_PER_INFRASTRUCTURE_LEVEL = SharedConstants.slotsPerInfrastructureLevel();
  public static final double INFRASTRUCTURE_COST_GROWTH_PER_LEVEL = SharedConstants.infrastructureCostGrowthPerLevel();
  public static final double BUILDING_MATERIAL_LEVEL_EXPONENT = SharedConstants.buildingMaterialLevelExponent();
  /** Faktor je Wohnkomplex-Stufe für Kapazität, Credits und Baustoffe (Umsetzungskonzept/17_...md, Teil C). */
  public static final double HOUSING_GROWTH_FACTOR = SharedConstants.housingCapacityGrowthFactor();

  // --- Kolonisation (Umsetzungskonzept/24_...md) ---------------------------
  /**
   * Startbevölkerung einer Kolonie – Heimatwelt bei der Registrierung UND
   * jede per Kolonisationsschiff gegründete Kolonie. Zugleich die Zahl
   * Kolonisten, die ein Kolonisationsschiff seiner Bau-Kolonie entzieht.
   */
  public static final double START_POPULATION = SharedConstants.startPopulation();
  /** Produkt-Id des Kolonisationsschiffs (Werftbau, siehe {@code ShipyardCommands.queueShip}). */
  public static final String COLONY_SHIP_PRODUCT_ID = "p_colonyship";
  /**
   * Feste Bauzeit des Kolonisationsschiffs in Spielstunden. Bewusst KEIN
   * Bonus-Ziel: weder Werftstufe noch Spezialisierung, Fördergüte, Blackout
   * oder Arbeitskraft verändern sie (siehe {@code ChainPlanner.computeProductionHours}).
   */
  public static final double COLONY_SHIP_BUILD_HOURS = SharedConstants.colonyShipBuildGameHours();
  /** Ohne diese Loyalität in der Bau-Kolonie finden sich keine Kolonisten. */
  public static final double COLONY_SHIP_MIN_LOYALTY_PCT = SharedConstants.colonyShipMinLoyaltyPct();
  /** Dauer der Landung/Koloniegründung in Spielstunden, nachdem "kolonisieren" ausgelöst wurde. */
  public static final double COLONIZATION_HOURS = SharedConstants.colonizationGameHours();
  /**
   * Balance-Konstante für das Fertigungstempo (1 = ungestaucht), siehe
   * {@link SharedConstants#productionSpeedMultiplier()} und
   * {@code ChainPlanner.computeProductionHours}.
   */
  public static final double PRODUCTION_SPEED_MULTIPLIER = SharedConstants.productionSpeedMultiplier();

  /**
   * Bevölkerungs-Konsum: Reihenfolge und Pro-Kopf-Bedarf je Grundkonsumgut
   * UND SPIELSTUNDE. Mit Umsetzungskonzept/17_...md, Teil C gesenkt
   * (Grundnahrung 0,001 → 0,0002, Grundmedizin 0,000375 → 0,0001, Elektronik
   * 0,00025 → 0,0001), damit die Startkolonie (120 Einwohner,
   * Industriekomplex 1) ihre Bevölkerung mit ≈ 50 % Warteschlangen-Auslastung
   * aus eigener Kraft versorgen kann – Herleitung aus echten ChainPlan-Stunden
   * dort.
   *
   * <p>Ausdrücklich JE SPIELSTUNDE: der Bedarf war einmal ein fester Betrag je
   * Realzeit-Tick und hätte sich als einzige laufende Größe dem Tempo-Regler
   * entzogen. Der Tagesbedarf ({@code Economy.dailyNeed}) ist Bevölkerung ×
   * Rate × {@link #GAME_DAY_HOURS}.</p>
   */
  public static final List<String> CONSUMER_GOODS_ORDER = List.of("p_grundnahrung", "p_grundmedizin", "p_unterhaltungselektronik");
  /**
   * Das Grundnahrungsmittel – das einzige Konsumgut, an dem nicht nur der
   * Lebensstandard hängt, sondern das WACHSTUM selbst
   * (Umsetzungskonzept/34_...md, §J 5: eine Kolonie wächst nicht über das
   * hinaus, was sie ernähren kann).
   */
  public static final String FOOD_PRODUCT_ID = "p_grundnahrung";
  public static final Map<String, Double> CONSUMER_NEED_PER_CAPITA_PER_HOUR = Map.of(
      "p_grundnahrung", 0.0002, "p_grundmedizin", 0.0001, "p_unterhaltungselektronik", 0.0001);

  /**
   * Takt der Universums-Statistik und des Bevölkerungsverlaufs in
   * SPIELSTUNDEN (4 Spielstunden = 10 s Realzeit bei Tempo 1). Bewusst
   * spielzeit- und nicht realzeitgebunden: der Verlauf ist eine Aussage über
   * die Kolonie­geschichte, seine Auflösung muss deshalb an der Spieluhr
   * hängen wie jede andere Frist auch.
   */
  public static final double STATS_SNAPSHOT_INTERVAL_GAME_HOURS = 4;
  public static final int STATS_HISTORY_LIMIT = 400;

  /**
   * ======================= REALZEIT-AUSNAHME =============================
   * Die EINZIGEN Zeitangaben des Spiels, die NICHT in Spielstunden rechnen.
   *
   * <p>Regel im Rest des Codes: Jede Dauer steht in Spielstunden und wird über
   * {@link Clock#hoursToMs(double)} umgerechnet, damit sie am Tempo-Regler
   * hängt. Diese vier Werte tun das BEWUSST NICHT – sie sind bereits
   * Realzeit-Millisekunden und dürfen NIEMALS durch {@code Clock.hoursToMs}
   * laufen.</p>
   *
   * <p><b>Warum die Ausnahme:</b> Aufbewahrungsfristen und Inaktivität sind
   * keine Spielmechanik. Sie messen, wann ein MENSCH wieder an den Rechner
   * kommt – und der wird nicht schneller, wenn die Spieluhr schneller läuft.
   * In Spielzeit gerechnet war eine Nachricht bei {@code gameSpeedMultiplier
   * = 4} nach 105 Realsekunden gelöscht und der einzige Link auf einen
   * Kampfbericht nach 30 Realsekunden verschwunden; das Nachrichten- und
   * Benachrichtigungssystem war damit praktisch funktionslos.</p>
   *
   * <p>Alle Verwendungsstellen sind mit dem Wort {@code REALZEIT-AUSNAHME}
   * markiert: {@code RetentionCleanup}, {@code Economy.warnAboutSupplyGaps}.</p>
   */
  public static final long NOTIFICATION_RETENTION_REAL_MS =
      (long) (SharedConstants.notificationRetentionRealDays() * 24 * 60 * 60 * 1000);
  /** REALZEIT-AUSNAHME, siehe {@link #NOTIFICATION_RETENTION_REAL_MS}. */
  public static final long MESSAGE_RETENTION_REAL_MS =
      (long) (SharedConstants.messageRetentionRealDays() * 24 * 60 * 60 * 1000);
  /** REALZEIT-AUSNAHME, siehe {@link #NOTIFICATION_RETENTION_REAL_MS}. */
  public static final long SUPPLY_WARNING_COOLDOWN_REAL_MS =
      (long) (SharedConstants.supplyWarningCooldownRealMinutes() * 60 * 1000);
  /** REALZEIT-AUSNAHME, siehe {@link #NOTIFICATION_RETENTION_REAL_MS}. */
  public static final long INACTIVE_PLAYER_DELETION_REAL_MS =
      (long) (SharedConstants.inactivePlayerDeletionRealDays() * 24 * 60 * 60 * 1000);
  /** REALZEIT-AUSNAHME, siehe {@link #NOTIFICATION_RETENTION_REAL_MS}: gelesene Post und alle Benachrichtigungen an NPC-Kommandanten. */
  public static final long NPC_MAIL_RETENTION_REAL_MS =
      (long) (SharedConstants.npcMailRetentionRealMinutes() * 60 * 1000);
  // ===================== Ende REALZEIT-AUSNAHME ===========================

  /**
   * Lohn je Einwohner und Spielstunde, gezahlt vom Kommandanten an das
   * Bevölkerungs-Wallet seiner Kolonie ({@code Economy.colonyDay}). Eine
   * Quelle für Lohn, Preisanker der Handelsgilde ({@code ProductCosts}) und
   * die Kreditreserve der Bots – siehe {@code shared/game-constants.json}.
   */
  public static final double WAGE_PER_CAPITA_PER_HOUR = SharedConstants.wagePerCapitaPerGameHour();

  /**
   * Kündigungsfristen für Friedens-/Handelsverträge (Umsetzungskonzept/21_...md,
   * {@code TreatyCommands.terminateTreaty}): eine Kündigung endet den Vertrag
   * NICHT sofort, sondern erst nach dieser Frist – bis dahin bleibt er voll
   * gültig (Friedensvertrag blockiert weiter {@code declareWar}, Handelsvertrag
   * erlaubt weiter planetaren Handel zwischen den Parteien).
   */
  public static final double PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS = SharedConstants.peaceTreatyTerminationNoticeGameHours();
  public static final double TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS = SharedConstants.tradeAgreementTerminationNoticeGameHours();

  /**
   * Ohne neue Produktion sinkt eine Spezialisierung nach dieser Gnadenfrist um
   * eine Stufe pro erneut überschrittener Frist. In SPIELSTUNDEN (6,4 h = 16 s
   * Realzeit bei Tempo 1, unveränderter Ausgangswert): eine Frist, nach der
   * Können verlernt wird, gehört an die Spieluhr, nicht an die Realzeit.
   */
  public static final double SPECIALIZATION_DECAY_GRACE_GAME_HOURS = 6.4;

  /** Reisezeit je einzelnem Gateway-Sprung (Spielstunden), siehe {@code Fleet.pendingHops}. */
  public static final double HOURS_PER_GATEWAY_HOP = 4;

  /**
   * Trägersprung ohne Gateway (Umsetzungskonzept/06_...md, {@code CarrierTransit}).
   * Ein Trägerschiff nimmt die übrigen Schiffe der Flotte an Bord und springt
   * geradlinig zu einem beliebigen bekannten System – auch ohne Gateway-Kette.
   *
   * <p>Die Distanz wird in "Referenz-Sprüngen" gemessen: Die Luftlinie zwischen
   * Start und Ziel (Galaxie-Koordinaten {@code StarSystem.x/y}, Einheitsquadrat)
   * wird durch die MITTLERE LÄNGE EINER GATEWAY-KANTE geteilt – letztere rechnet
   * {@code FleetCommands.averageGatewayEdgeLength} aus der tatsächlichen
   * Topologie aus, statt sie zu raten. Ein Trägersprung über dieselbe Strecke
   * dauert dann das {@link #CARRIER_TRANSIT_TIME_FACTOR}-fache eines
   * Gateway-Sprungs und kostet das {@link #CARRIER_TRANSIT_FUEL_FACTOR}-fache
   * an Treibstoff – teuer und langsam, dafür unabhängig von Gateways, Zöllen
   * und Blockaden und auch dorthin möglich, wohin keine Gateway-Kette führt.</p>
   */
  public static final double CARRIER_TRANSIT_TIME_FACTOR = SharedConstants.carrierTransitTimeFactor();
  public static final double CARRIER_TRANSIT_FUEL_FACTOR = SharedConstants.carrierTransitFuelFactor();
  /** Rückfallwert für die mittlere Gateway-Kantenlänge, falls es (noch) keine Kanten gibt. */
  public static final double CARRIER_FALLBACK_HOP_DISTANCE = 0.1;

  /**
   * Treibstoff für Gateway-Sprünge (Umsetzungskonzept/26_...md, neu bemessen in
   * Umsetzungskonzept/34_...md, Entscheidung F7): Verbrauch hängt an MASSE und
   * DISTANZ. Ein Sprung kostet je Schiff
   * {@link #JUMP_FUEL_PER_CORVETTE_MASS_PER_HOP} Eleriumkapseln je
   * KORVETTENMASSE – dieselbe Bezugsgröße, in der auch die Trägerslots rechnen
   * ({@code ShipTypeDef.carrierSlotUsage}). Eine Korvette kostet damit eine
   * Kapsel je Sprung, ein Kreuzer hundert. Die Distanz steckt in der Zahl der
   * Sprünge (beim Trägersprung in Referenzsprüngen).
   *
   * <p>Vorher waren es pauschal 0,01 Kapseln je SCHIFF und Sprung gegen einen
   * Tank von 1 000 Kapseln je Schiff: eine Tankfüllung reichte für 100 000
   * Sprünge, Treibstoff war faktisch keine Ressource, sondern nur eine Hürde
   * beim allerersten Flug.</p>
   */
  public static final String JUMP_FUEL_PRODUCT_ID = "p_elerium_kapsel";
  public static final double JUMP_FUEL_PER_CORVETTE_MASS_PER_HOP = SharedConstants.jumpFuelPerCorvetteMassPerHop();
  /**
   * Reichweite einer vollen Tankfüllung in Sprüngen. Das Fassungsvermögen je
   * Schiff ist daraus abgeleitet (Verbrauch je Sprung × Reichweite), nicht
   * umgekehrt: die Reichweite ist deshalb für JEDE Flotte gleich, egal wie
   * groß ihre Schiffe sind – teuer wird die Größe beim Betanken, nicht beim
   * Fliegen. Der Tank bleibt von der Fracht getrennt (Umsetzungskonzept/26):
   * er belegt keine Ladekapazität, dafür kann Treibstoff auch nicht wieder
   * ausgeladen werden.
   */
  public static final double JUMP_FUEL_TANK_RANGE_HOPS = SharedConstants.jumpFuelTankRangeHops();
  /** Bezugsschiff der Massenskala (Umsetzungskonzept/27_...md, §A) – ein Trägerslot und eine Kapsel je Sprung. */
  public static final String CORVETTE_PRODUCT_ID = "p_corvette";

  /** Bodentruppen-Crewing (Mechanik/05_..., §3-4). */
  public static final List<String> DRONE_PRODUCT_IDS = List.of("p_drone_light", "p_drone_medium", "p_drone_heavy");
  public static final int DRONES_PER_SOLDIER = 5;
  /**
   * Die einzige Bodeneinheit, die keine Maschine ist – und deshalb die
   * einzige, die NICHT als Fracht reisen darf (Umsetzungskonzept/28_...md,
   * siehe {@code TroopTransportCommands.requireNotASoldier}). Soldaten
   * fahren ausschließlich im Mannschaftstransporter.
   */
  public static final String SOLDIER_PRODUCT_ID = "p_soldier";
}
