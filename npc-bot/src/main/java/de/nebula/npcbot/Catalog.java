package de.nebula.npcbot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handverlesener Ausschnitt aus {@code de.nebula.data.ProductCatalog}/
 * {@code ShipCatalog}/{@code BuildingCatalog} des Backends – bewusst NICHT
 * importiert (keine Modulabhängigkeit, siehe {@code pom.xml}), sondern auf
 * das für die Bot-KI Nötige reduziert dupliziert (Umsetzungskonzept/14_...md,
 * Teil 2, "Wirtschafts-/Militär-Heuristik").
 */
final class Catalog {
  private Catalog() {
  }

  /**
   * Fixe Liste von zehn Tier-2-Zwischenprodukten (je eines der Grundmetall-
   * Legierungen/Zwischenwerkstoffe aus {@code ProductCatalog}) – EXAKT so
   * viele wie Bots je Lager, damit der Koordinator (siehe {@code Bot
   * #sendSpecializationAssignments}) jedem Lagermitglied inklusive sich
   * selbst genau ein Produkt zuteilen kann. Bewusste Vereinfachung: eine
   * deterministische Fixliste statt einer dynamischen Marktanalyse.
   */
  static final List<String> SPECIALTY_PRODUCTS = List.of(
      "p_stahl", "p_leichtmetalllegierung", "p_hochtemplegierung", "p_leitermetall",
      "p_katalysatormetall", "p_magnetwerkstoff", "p_halbleiterrohstoff", "p_keramikwerkstoff",
      "p_glaswerkstoff", "p_verbundwerkstoff");

  /** Kampfschiffstypen in Bau-Rotation, siehe {@code ShipCatalog} – deckt bewusst den vollen Konterkreis ab. */
  static final List<String> WARSHIP_TYPES = List.of("p_corvette", "p_destroyer", "p_cruiser");

  /**
   * Grobe militärische Gewichtung je Schiffstyp – 1:1 aus {@code ShipTypeDef
   * .carrierSlotUsage} übernommen (Korvette 1, Zerstörer 2, Kreuzer 4) als
   * einfacher, dokumentierter Stärkevergleich zwischen zwei Flotten. Bewusst
   * KEINE Nachbildung der vollen Kontermultiplikator-/Schadensformel aus
   * {@code BattleCommands} – für die Angriffsentscheidung reicht eine grobe
   * Schätzung, das eigentliche Gefecht rechnet der Server exakt.
   */
  static final Map<String, Double> SHIP_MILITARY_WEIGHT = Map.of(
      "p_corvette", 1.0, "p_destroyer", 2.0, "p_cruiser", 4.0);

  /** Ausbaupriorität und Obergrenzen je Gebäudetyp – "bis zu sinnvollen Obergrenzen", nicht bis Maxstufe (dort 15-20). */
  static final Map<String, Integer> BUILD_CAP;

  static {
    Map<String, Integer> caps = new LinkedHashMap<>();
    caps.put("b_industry", 10);
    caps.put("b_shipyard", 7);
    caps.put("b_habitat", 12);
    caps.put("b_powergrid", 8);
    BUILD_CAP = Map.copyOf(caps);
  }

  static final List<String> BUILD_PRIORITY = List.of("b_industry", "b_shipyard", "b_habitat", "b_powergrid");

  /** Obergrenze der insgesamt gehaltenen Kampfschiffe (Summe aller drei Klassen) – verhindert unbegrenztes Werft-Wachstum. */
  static final int MAX_COMBAT_SHIPS = 40;
}
