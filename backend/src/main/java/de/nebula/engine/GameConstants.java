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
   * Realzeit-Abstand zweier Ticks. Muss zum {@code @Scheduled(every = ...)}
   * in {@code GameTick} passen – dort steht derselbe Wert als Textliteral,
   * weil Annotationswerte Konstanten sein müssen.
   */
  public static final double TICK_MS = 1000;

  /**
   * Spielstunden, die EIN Tick abdeckt = {@code TICK_MS / REAL_MS_PER_GAME_HOUR}.
   * Wächst mit dem Tempo-Regler ({@link Clock#GAME_SPEED_MULTIPLIER}): der Tick
   * bleibt eine Realsekunde lang, deckt aber mehr Spielzeit ab. JEDE
   * tick-weise verbuchte Größe ist deshalb als RATE JE SPIELSTUNDE definiert
   * und wird hiermit multipliziert – nie als fester Betrag je Tick.
   */
  public static final double TICK_GAME_HOURS = TICK_MS / Clock.REAL_MS_PER_GAME_HOUR;

  public static final double GAME_DAY_MS = Clock.hoursToMs(24);

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
   * Test-Regler für das Fertigungstempo (1 = unveränderte Balance), siehe
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
   * <p>Ausdrücklich JE SPIELSTUNDE, nicht je Tick: der Bedarf war vorher ein
   * fester Betrag je Tick und hätte sich als einzige laufende Größe dem
   * Tempo-Regler entzogen (bei doppeltem Tempo hätte die Bevölkerung je
   * Spieltag nur noch halb so viel gegessen, während die Produktion mitzieht).
   * Die Zahlen sind gegenüber der Tick-Fassung um den Faktor
   * {@code 1 / TICK_GAME_HOURS} bei Tempo 1 (2,5) angehoben, das Verhalten bei
   * Tempo 1 ist damit unverändert.</p>
   */
  public static final List<String> CONSUMER_GOODS_ORDER = List.of("p_grundnahrung", "p_grundmedizin", "p_unterhaltungselektronik");
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
   * Aufbewahrungsfristen für Benachrichtigungen und Nachrichten OHNE gesetztes
   * "Beibehalten"-Kennzeichen, gezählt in SPIELZEIT (siehe
   * {@code RetentionCleanup}, Umsetzungskonzept/15_...md, Auftrag 2). Die
   * Zahlenwerte stehen an EINER Stelle in {@code shared/game-constants.json}
   * (siehe {@link SharedConstants}), damit auch das Frontend sie für seine
   * Hinweistexte kennt, ohne sie zu duplizieren.
   *
   * <p>Umrechnung in Realzeit bei Tempo 1 ({@code Clock.REAL_MS_PER_GAME_HOUR
   * = 2500}): 48 Spielstunden (2 Spieltage) ≈ 2 Realminuten, 168 Spielstunden
   * (7 Spieltage) ≈ 7 Realminuten – bei höherem
   * {@link Clock#GAME_SPEED_MULTIPLIER} entsprechend weniger.</p>
   */
  public static final double NOTIFICATION_RETENTION_GAME_HOURS = SharedConstants.notificationRetentionGameHours();
  public static final double MESSAGE_RETENTION_GAME_HOURS = SharedConstants.messageRetentionGameHours();

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
   * Treibstoff für Gateway-Sprünge (Nutzervorgabe): jeder Sprung verbraucht je Schiff der
   * springenden Flotte {@link #JUMP_FUEL_PER_SHIP_PER_HOP} Eleriumkapseln, entnommen aus den
   * Kolonielagern des Flottenbesitzers (siehe {@code FleetCommands.moveFleet}). Neue
   * Kommandanten starten mit einem kleinen Vorrat ({@code WorldSeed.STARTER_JUMP_FUEL_QUANTITY}),
   * der für viele Sprünge weniger Schiffe reicht; wer viele Frachter gleichzeitig bewegt, muss
   * die Sprungkosten zunehmend einplanen und selbst Eleriumkapseln nachproduzieren.
   */
  public static final String JUMP_FUEL_PRODUCT_ID = "p_elerium_kapsel";
  public static final double JUMP_FUEL_PER_SHIP_PER_HOP = 0.01;
  /**
   * Fassungsvermögen des Treibstofftanks JE SCHIFF in Eleriumkapseln
   * (Umsetzungskonzept/26_...md). Der Tank ist von der Fracht getrennt: er
   * belegt keine Lade­kapazität, dafür kann Treibstoff auch nicht wieder
   * ausgeladen werden – sonst wäre er ein Frachtraum durch die Hintertür.
   */
  public static final double JUMP_FUEL_TANK_PER_SHIP = SharedConstants.jumpFuelTankPerShip();

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
