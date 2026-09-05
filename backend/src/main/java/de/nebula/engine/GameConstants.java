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

  /** = {@code TICK_MS / REAL_MS_PER_GAME_HOUR}: Spielstunden, die EIN Tick (1s Realzeit) abdeckt. */
  public static final double TICK_GAME_HOURS = 1000.0 / Clock.REAL_MS_PER_GAME_HOUR;

  public static final double GAME_DAY_MS = Clock.hoursToMs(24);

  /** Ausgleichsfonds gegen Geldhortung (Konzeption/Spieldesign/06_..., §8 und Mechanik/10_..., §7). */
  public static final double WEALTH_TAX_THRESHOLD = 1000;
  public static final double WEALTH_TAX_RATE = 0.001;
  public static final double COLONY_TAX_RATE = 0.01;

  /** PowerUpkeepJob (Umsetzungskonzept/01_..., §3). */
  public static final double ELERIUM_UPKEEP_PER_POWERGRID_LEVEL = 0.005;
  public static final String POWERGRID_FUEL_PRODUCT_ID = "p_elerium_stabil";

  /** Bevölkerungs-Konsum: Reihenfolge und Pro-Kopf-Bedarf je Grundkonsumgut. */
  public static final List<String> CONSUMER_GOODS_ORDER = List.of("p_grundnahrung", "p_grundmedizin", "p_unterhaltungselektronik");
  public static final Map<String, Double> CONSUMER_NEED_PER_CAPITA = Map.of(
      "p_grundnahrung", 0.0004, "p_grundmedizin", 0.00015, "p_unterhaltungselektronik", 0.0001);

  public static final long STATS_SNAPSHOT_INTERVAL_MS = 10000;
  public static final int STATS_HISTORY_LIMIT = 400;

  /** Ohne neue Produktion sinkt eine Spezialisierung nach dieser Gnadenfrist um eine Stufe pro erneut überschrittener Frist. */
  public static final long SPECIALIZATION_DECAY_GRACE_MS = 16000;

  /** Reisezeit je einzelnem Gateway-Sprung (Spielstunden), siehe {@code Fleet.pendingHops}. */
  public static final double HOURS_PER_GATEWAY_HOP = 4;

  /** Bodentruppen-Crewing (Mechanik/05_..., §3-4). */
  public static final List<String> DRONE_PRODUCT_IDS = List.of("p_drone_light", "p_drone_medium", "p_drone_heavy");
  public static final int DRONES_PER_SOLDIER = 5;
}
