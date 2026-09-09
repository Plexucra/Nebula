package de.nebula.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Werte, die BEIDE Anwendungen brauchen und die deshalb nur an EINER Stelle
 * stehen dürfen: {@code shared/game-constants.json} im Repo-Wurzelverzeichnis
 * (Umsetzungskonzept/15_...md, Auftrag 3). Das Backend liest die Datei hier
 * zur Laufzeit aus dem Klassenpfad (Maven kopiert {@code ../shared} über eine
 * zusätzliche Resource-Definition mit hinein), das Frontend importiert
 * dieselbe Datei beim Build (siehe {@code core/shared-constants.ts}).
 *
 * <p>Bewusst nur für die wenigen Werte, die das Frontend UNABHÄNGIG vom
 * Server benötigt – alles andere (Formeln, Kataloge, Regeln) lebt
 * ausschließlich im Backend und erreicht das Frontend über die
 * WebSocket-Befehle.</p>
 */
public final class SharedConstants {
  private SharedConstants() {
  }

  private static final JsonNode ROOT = load();

  private static JsonNode load() {
    String resource = "shared/game-constants.json";
    try (InputStream in = SharedConstants.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Datei fehlt im Klassenpfad: " + resource);
      return new ObjectMapper().readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Datei konnte nicht geladen werden: " + resource, e);
    }
  }

  /** Ausgangs-Zeitkompression OHNE Tempo-Regler – für die wirksame Kompression siehe {@link Clock#REAL_MS_PER_GAME_HOUR}. */
  public static double baseRealMsPerGameHour() {
    return ROOT.path("baseRealMsPerGameHour").asDouble();
  }

  /**
   * Separat einstellbarer Spielzeit-Multiplikator (1 = Ausgangstempo). Der
   * EINZIGE Regler für schnellere Testläufe; alles Zeitabhängige leitet sich
   * über {@link Clock#REAL_MS_PER_GAME_HOUR} daraus ab.
   */
  public static double gameSpeedMultiplier() {
    double value = ROOT.path("gameSpeedMultiplier").asDouble();
    if (value <= 0) throw new IllegalStateException("gameSpeedMultiplier muss größer als 0 sein, ist aber " + value);
    return value;
  }

  /**
   * Zweiter, davon unabhängiger Test-Regler: Teiler für JEDE Fertigungsdauer
   * (Industrie, Werft, Ausbildungszentrum), siehe {@code ChainPlanner}. 1 =
   * unveränderte Balance; nur für Testläufe gedacht, in denen die sehr langen
   * Schiffsketten (Mannschaftstransporter, Kolonisationsschiff) erreichbar
   * sein sollen (Umsetzungskonzept/31_...md).
   */
  public static double productionSpeedMultiplier() {
    JsonNode node = ROOT.path("productionSpeedMultiplier");
    double value = node.isMissingNode() ? 1 : node.asDouble();
    if (value <= 0) throw new IllegalStateException("productionSpeedMultiplier muss größer als 0 sein, ist aber " + value);
    return value;
  }

  public static double notificationRetentionGameHours() {
    return ROOT.path("notificationRetentionGameHours").asDouble();
  }

  public static double messageRetentionGameHours() {
    return ROOT.path("messageRetentionGameHours").asDouble();
  }

  /** Kündigungsfristen für Friedens-/Handelsverträge (Umsetzungskonzept/21_...md). */
  public static double peaceTreatyTerminationNoticeGameHours() {
    return ROOT.path("peaceTreatyTerminationNoticeGameHours").asDouble();
  }

  public static double tradeAgreementTerminationNoticeGameHours() {
    return ROOT.path("tradeAgreementTerminationNoticeGameHours").asDouble();
  }

  // --- Bebauung / Infrastruktur (Umsetzungskonzept/17_...md) ---------------
  public static int slotsPerInfrastructureLevel() {
    return ROOT.path("slotsPerInfrastructureLevel").asInt();
  }

  /** Energiespeicher: automatische Vorhaltemenge in Spielstunden Infrastrukturverbrauch (Umsetzungskonzept/32_...md). */
  public static double energyReserveDefaultGameHours() {
    JsonNode node = ROOT.path("energyReserveDefaultGameHours");
    return node.isMissingNode() ? 240 : node.asDouble();
  }

  public static double eleriumUpkeepBasePerHour() {
    return ROOT.path("eleriumUpkeepBasePerHour").asDouble();
  }

  public static double eleriumUpkeepLevelExponent() {
    return ROOT.path("eleriumUpkeepLevelExponent").asDouble();
  }

  public static int maxInfrastructureByPlanetSize(String size) {
    JsonNode n = ROOT.path("maxInfrastructureByPlanetSize").path(size);
    if (n.isMissingNode()) throw new IllegalStateException("Keine Infrastruktur-Obergrenze für Planetengröße " + size);
    return n.asInt();
  }

  public static double infrastructureCostGrowthPerLevel() {
    return ROOT.path("infrastructureCostGrowthPerLevel").asDouble();
  }

  public static double buildingMaterialLevelExponent() {
    return ROOT.path("buildingMaterialLevelExponent").asDouble();
  }

  // --- Bevölkerung (Umsetzungskonzept/17_...md, Teil C) ---------------------
  public static double livingStandardShrinkBelowPct() {
    return ROOT.path("livingStandardShrinkBelowPct").asDouble();
  }

  public static double livingStandardGrowthFromPct() {
    return ROOT.path("livingStandardGrowthFromPct").asDouble();
  }

  public static double populationShrinkRatePerHour() {
    return ROOT.path("populationShrinkRatePerHour").asDouble();
  }

  /** Basis-Wachstumsrate des logistischen Bevölkerungswachstums je Spielstunde. */
  public static double populationBaseGrowthRatePerHour() {
    return ROOT.path("populationBaseGrowthRatePerHour").asDouble();
  }

  /** Faktor, um den sich Wohnkapazität, Credits und Baustoffe je Wohnkomplex-Stufe vervielfachen. */
  public static double housingCapacityGrowthFactor() {
    return ROOT.path("housingCapacityGrowthFactor").asDouble();
  }

  // --- Kolonisation (Umsetzungskonzept/24_...md) ---------------------------
  /**
   * Bevölkerung, mit der eine Kolonie startet – sowohl die Heimatwelt bei der
   * Registrierung als auch jede per Kolonisationsschiff gegründete Kolonie.
   * Zugleich die Zahl Kolonisten, die ein Kolonisationsschiff der Startkolonie
   * entzieht.
   */
  public static double startPopulation() {
    return ROOT.path("startPopulation").asDouble();
  }

  /** Feste Bauzeit eines Kolonisationsschiffs in Spielstunden (eine Spielwoche), ohne jeden Bonus. */
  public static double colonyShipBuildGameHours() {
    return ROOT.path("colonyShipBuildGameHours").asDouble();
  }

  /** Mindestloyalität der Bau-Kolonie, damit sich überhaupt Kolonisten finden. */
  public static double colonyShipMinLoyaltyPct() {
    return ROOT.path("colonyShipMinLoyaltyPct").asDouble();
  }

  /** Dauer der eigentlichen Landung/Koloniegründung in Spielstunden (ein Spieltag). */
  public static double colonizationGameHours() {
    return ROOT.path("colonizationGameHours").asDouble();
  }

  /** Fassungsvermögen des Treibstofftanks je Schiff in Eleriumkapseln (Umsetzungskonzept/26_...md). */
  public static double jumpFuelTankPerShip() {
    return ROOT.path("jumpFuelTankPerShip").asDouble();
  }
}
