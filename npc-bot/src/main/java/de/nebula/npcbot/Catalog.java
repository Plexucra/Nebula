package de.nebula.npcbot;

import java.util.List;
import java.util.Map;

/**
 * Handverlesener Ausschnitt aus den Katalogen des Backends
 * ({@code shared/catalog/*.json}) – bewusst NICHT importiert (keine
 * Modulabhängigkeit, siehe {@code pom.xml}), sondern auf das für die Bot-KI
 * Nötige reduziert dupliziert. Alles, was sich zur Laufzeit ändert (Preise,
 * Kettendauern, Bestände), fragt der Bot beim Server ab; hier stehen nur
 * Produkt-Ids und die wenigen Formelkonstanten, die er zum Abschätzen
 * braucht.
 */
final class Catalog {
  private Catalog() {
  }

  // --- Grundbedarf der Bevölkerung (GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR) ---
  static final String FOOD = "p_grundnahrung";
  static final String MEDICINE = "p_grundmedizin";
  static final String ELECTRONICS = "p_unterhaltungselektronik";
  static final Map<String, Double> CONSUMER_NEED_PER_CAPITA_PER_HOUR = Map.of(
      FOOD, 0.0002, MEDICINE, 0.0001, ELECTRONICS, 0.0001);

  // --- Energie (Formulas.infrastructureEleriumPerHour) -----------------------
  static final String ELERIUM = "p_elerium_stabil";
  static final String JUMP_FUEL = "p_elerium_kapsel";
  static final double ELERIUM_UPKEEP_BASE_PER_HOUR = 0.005;
  static final double ELERIUM_UPKEEP_LEVEL_EXPONENT = 1.25;

  static double eleriumPerHour(int infrastructureLevel) {
    return infrastructureLevel <= 0 ? 0 : ELERIUM_UPKEEP_BASE_PER_HOUR * Math.pow(infrastructureLevel, ELERIUM_UPKEEP_LEVEL_EXPONENT);
  }

  // --- Baustoff-Spezialisierungen (Tier 2) -----------------------------------
  static final List<String> SPECIALTY_PRODUCTS = List.of(
      "p_stahl", "p_leitermetall", "p_leichtmetalllegierung", "p_glaswerkstoff", "p_keramikwerkstoff",
      "p_hochtemplegierung", "p_katalysatormetall", "p_magnetwerkstoff", "p_halbleiterrohstoff", "p_verbundwerkstoff");
  static final String STEEL = "p_stahl";

  // --- Gebäude ---------------------------------------------------------------
  static final String INFRASTRUCTURE = "b_infrastructure";
  static final String INDUSTRY = "b_industry";
  static final String SHIPYARD = "b_shipyard";
  static final String HABITAT = "b_habitat";
  static final String ACADEMY = "b_academy";
  static final String DEFENSE = "b_defense";

  // --- Schiffe (ShipCatalog) ----------------------------------------------------
  static final List<String> WARSHIP_TYPES = List.of("p_corvette", "p_destroyer", "p_cruiser");
  static final String FREIGHTER = "p_freighter";
  static final String TROOP_TRANSPORT = "p_trooptransport";
  static final String COLONY_SHIP = "p_colonyship";
  static final double TROOP_CAPACITY_PER_TRANSPORT = 1000;
  /**
   * Militärische Gewichtung je Schiffstyp = {@code carrierSlotUsage} (1:10:100),
   * seit Umsetzungskonzept/27_...md zugleich das Verhältnis von Masse und
   * Arbeitsaufwand – eine brauchbare Näherung des echten Kampfwerts.
   */
  static final Map<String, Double> SHIP_MILITARY_WEIGHT = Map.of(
      "p_corvette", 1.0, "p_destroyer", 10.0, "p_cruiser", 100.0);

  // --- Bodentruppen (GroundUnitCatalog, products.json) -------------------------
  static final String SOLDIER = "p_soldier";
  static final String DRONE_LIGHT = "p_drone_light";
  static final String DRONE_MEDIUM = "p_drone_medium";
  static final String DRONE_HEAVY = "p_drone_heavy";
  static final List<String> DRONES = List.of(DRONE_LIGHT, DRONE_MEDIUM, DRONE_HEAVY);
  static final int DRONES_PER_SOLDIER = 5;
  /** Kampfwert je Drohne = workHoursPerUnit × baseProductionHours (Formulas.productionAspect). */
  static final Map<String, Double> DRONE_VALUE = Map.of(
      DRONE_LIGHT, 17.0 * 2.29, DRONE_MEDIUM, 19.0 * 2.53, DRONE_HEAVY, 20.0 * 2.65);
  /** Kontermatrix aus ground-units.json: Schlüssel schlägt Wert (leicht > schwer > mittel > leicht). */
  static final Map<String, String> DRONE_COUNTERS = Map.of(
      DRONE_LIGHT, DRONE_HEAVY, DRONE_MEDIUM, DRONE_LIGHT, DRONE_HEAVY, DRONE_MEDIUM);

  /** Welche Drohnenklasse die gegebene kontert (Umkehrung von {@link #DRONE_COUNTERS}). */
  static String counterFor(String droneType) {
    for (Map.Entry<String, String> e : DRONE_COUNTERS.entrySet()) if (e.getValue().equals(droneType)) return e.getKey();
    return DRONE_MEDIUM;
  }

  // --- Spielregeln, die der Bot zum Planen braucht ---------------------------------
  static final double COLONY_SHIP_MIN_LOYALTY_PCT = 90;
  static final double START_POPULATION = 2000;
  static final double COLONIST_PREMIUM = START_POPULATION * 8;
  static final double RECRUIT_MIN_LOYALTY_PCT = 50;
  static final double SIEGE_SURRENDER_LOYALTY_PCT = 2;
  static final int COMBAT_TICK_HOURS = 8;
  static final double FUEL_TOP_UP_QTY = 5;
}
