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

  /** Tier-1-Grundbedarf Nahrung – Handelsrolle der meisten Bots, siehe {@link #specialtyForIndex}. */
  static final String FOOD_PRODUCT = "p_grundnahrung";
  /** Tier-1-Grundbedarf Medizin – zweite Handelsrolle der meisten Bots, siehe {@link #specialtyForIndex}. */
  static final String MEDICINE_PRODUCT = "p_grundmedizin";
  /**
   * Baustoff, den Nahrungs-/Medizin-Spezialisten am Handelsposten für den
   * eigenen Infrastruktur-/Industrieausbau einkaufen (Umsetzungskonzept/
   * 17_...md): {@code p_stahl} ist in {@code buildings.json} das einzige
   * Material, das JEDER der drei Ausbaupfade ({@link #BUILD_PRIORITY} plus
   * Infrastruktur) bereits ab Stufe 1 braucht.
   */
  static final String TRADE_IMPORT_MATERIAL = "p_stahl";

  /**
   * Nur jeder {@code MATERIALS_SPECIALIST_EVERY}-te Bot (nach Index)
   * spezialisiert sich auf Baustoffe statt auf Nahrung/Medizin – Nutzervorgabe:
   * "sehr wenige" sollen sich um Baustoffe kümmern, die breite Masse um die
   * Grundbedarfe der eigenen Bevölkerung.
   */
  static final int MATERIALS_SPECIALIST_EVERY = 5;

  /** Menge der eigenen Spezialware, die IMMER im Heimatlager bleibt (füttert die lokale Auto-Relist-Verkaufsorder der Bevölkerung). */
  static final double TRADE_RESERVE_QTY = 5;
  /** Erst ab dieser exportierbaren Menge lohnt sich eine Handelsfahrt (ein Frachter je Fahrt). */
  static final double TRADE_MIN_EXPORT_BATCH = 5;
  /** Unterhalb dieses Lagerbestands wird ein Grundbedarf/Baustoff am Handelsposten nachgekauft. */
  static final double TRADE_IMPORT_LOW_WATERMARK = 10;
  /** Treibstoff, den ein Bot vor jeder Abreise nachzutanken versucht (Umsetzungskonzept/26_...md). */
  static final double FUEL_TOP_UP_QTY = 5;
  /** Feste Einkaufslosgröße je Grundbedarf/Baustoff und Stationsbesuch. */
  static final double TRADE_IMPORT_BATCH = 10;

  /**
   * Verteilt die Handelsrolle über den Bot-Index (Umsetzungskonzept/22_...md,
   * §H: "das mit den NPC machen wir später" – dies ist die Nachreichung).
   * Die meisten Bots produzieren abwechselnd Nahrungs- oder Medizin-
   * Grundbedarf für den eigenen Handelsposten-Export, nur jeder
   * {@link #MATERIALS_SPECIALIST_EVERY}-te stellt stattdessen eines der
   * Tier-2-Baustoffe aus {@link #SPECIALTY_PRODUCTS} her (rotierend über
   * mehrere Baustoff-Spezialisten hinweg).
   */
  static String specialtyForIndex(int index) {
    if (index % MATERIALS_SPECIALIST_EVERY == 0) {
      int materialIndex = (index / MATERIALS_SPECIALIST_EVERY - 1) % SPECIALTY_PRODUCTS.size();
      return SPECIALTY_PRODUCTS.get(materialIndex);
    }
    return index % 2 == 1 ? FOOD_PRODUCT : MEDICINE_PRODUCT;
  }

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

  /**
   * Ausbaupriorität und Obergrenzen je Gebäudetyp (Umsetzungskonzept/17_...md):
   * aus dem Minimalstart (nur Wohnkomplex 1 + Infrastruktur 2) heraus zuerst
   * der Industriekomplex – ohne ihn gibt es weder Produktion noch Baustoffe.
   * Infrastruktur hat keine eigene Obergrenze in der Liste: sie wird gebaut,
   * sobald ein anderer Ausbau mangels Bebauungsplatz abgelehnt wird.
   */
  static final Map<String, Integer> BUILD_CAP;

  static {
    Map<String, Integer> caps = new LinkedHashMap<>();
    caps.put("b_industry", 8);
    caps.put("b_shipyard", 5);
    caps.put("b_habitat", 6);
    BUILD_CAP = Map.copyOf(caps);
  }

  static final List<String> BUILD_PRIORITY = List.of("b_industry", "b_shipyard", "b_habitat");
  static final String INFRASTRUCTURE = "b_infrastructure";

  /** Obergrenze der insgesamt gehaltenen Kampfschiffe (Summe aller drei Klassen) – verhindert unbegrenztes Werft-Wachstum. */
  static final int MAX_COMBAT_SHIPS = 40;
}
