package de.nebula.npcbot;

import java.util.List;
import java.util.Map;

/**
 * Die Produkt- und Gebäude-Ids, mit denen die Bot-KI plant, plus die
 * Regelzahlen, die sie zum Abschätzen braucht. Seit 11.9.2026 steht hier
 * keine abgetippte Zahl mehr:
 * <ul>
 *   <li>Regelzahlen (Bedarfe, Loyalitätsschwellen, Kampftakt, Startbevölkerung,
 *       Eleriumverbrauch) kommen aus {@code shared/game-constants.json}
 *       ({@link SharedConstants}) – derselben Datei wie im Backend.</li>
 *   <li>Katalogwerte (Kampfwert je Schiff und Drohne, Tankgröße,
 *       Truppenkapazität, Frachtraum, Rezepte) fragt {@link World} beim
 *       Server ab ({@code shipTypes}, {@code productTypes}, Flottenansicht).</li>
 * </ul>
 * Alles, was sich zur Laufzeit ändert (Preise, Kettendauern, Bestände), fragt
 * der Bot ohnehin beim Server ab.
 */
final class Catalog {
  private Catalog() {
  }

  // --- Grundbedarf der Bevölkerung (GameConstants.CONSUMER_NEED_PER_CAPITA_PER_HOUR) ---
  static final String FOOD = "p_grundnahrung";
  static final String MEDICINE = "p_grundmedizin";
  static final String ELECTRONICS = "p_unterhaltungselektronik";
  static final Map<String, Double> CONSUMER_NEED_PER_CAPITA_PER_HOUR = SharedConstants.numberMap("consumerNeedPerCapitaPerGameHour");

  // --- Energie (Formulas.infrastructureEleriumPerHour) -----------------------
  static final String ELERIUM = "p_elerium_stabil";
  static final String JUMP_FUEL = "p_elerium_kapsel";
  static final double ELERIUM_UPKEEP_BASE_PER_HOUR = SharedConstants.number("eleriumUpkeepBasePerHour");
  static final double ELERIUM_UPKEEP_LEVEL_EXPONENT = SharedConstants.number("eleriumUpkeepLevelExponent");

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
  // Die Truppenkapazität eines Transporters steht NICHT mehr hier: sie hing mit
  // 1000 Soldaten um den Faktor 37 neben dem Katalog (27) und ließ jede
  // Landungsoperation in der Verladung hängen. Sie kommt jetzt zur Laufzeit vom
  // Server – World.troopCapacityPerTransport(). Ebenso die militärische
  // Gewichtung je Schiffstyp (World.strength) und der Kampfwert je Drohne
  // (World.droneValue): beide folgen aus dem Katalog des Servers.

  // --- Bodentruppen (GroundUnitCatalog, products.json) -------------------------
  static final String SOLDIER = "p_soldier";
  static final String DRONE_LIGHT = "p_drone_light";
  static final String DRONE_MEDIUM = "p_drone_medium";
  static final String DRONE_HEAVY = "p_drone_heavy";
  static final List<String> DRONES = List.of(DRONE_LIGHT, DRONE_MEDIUM, DRONE_HEAVY);
  static final int DRONES_PER_SOLDIER = (int) SharedConstants.number("dronesPerSoldier");
  /** Kontermatrix aus ground-units.json: Schlüssel schlägt Wert (leicht > schwer > mittel > leicht). */
  static final Map<String, String> DRONE_COUNTERS = Map.of(
      DRONE_LIGHT, DRONE_HEAVY, DRONE_MEDIUM, DRONE_LIGHT, DRONE_HEAVY, DRONE_MEDIUM);

  /** Welche Drohnenklasse die gegebene kontert (Umkehrung von {@link #DRONE_COUNTERS}). */
  static String counterFor(String droneType) {
    for (Map.Entry<String, String> e : DRONE_COUNTERS.entrySet()) if (e.getValue().equals(droneType)) return e.getKey();
    return DRONE_MEDIUM;
  }

  // --- Spielregeln, die der Bot zum Planen braucht ---------------------------------
  static final double COLONY_SHIP_MIN_LOYALTY_PCT = SharedConstants.number("colonyShipMinLoyaltyPct");
  static final double START_POPULATION = SharedConstants.number("startPopulation");
  /** Was die Werft je Kolonisationsschiff an Kolonistenprämie verlangt (ShipyardCommands.colonistPremiumPerShip). */
  static final double COLONIST_PREMIUM = START_POPULATION * SharedConstants.number("creditsPerNewInhabitant");
  static final double RECRUIT_MIN_LOYALTY_PCT = SharedConstants.number("recruitMinLoyaltyPct");
  static final int COMBAT_TICK_HOURS = (int) SharedConstants.number("combatTickGameHours");
  /** Richtwert für eine Fahrt, wenn das Ziel (noch) nicht feststeht. */
  static final int DEFAULT_TRIP_HOPS = 12;
}
