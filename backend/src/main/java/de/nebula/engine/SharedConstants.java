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
   * Teiler für JEDE Fertigungsdauer (Industrie, Werft, Ausbildungszentrum),
   * siehe {@code ChainPlanner}. Ursprünglich ein reiner Test-Regler
   * (Umsetzungskonzept/31_...md), inzwischen die BALANCE-Konstante für das
   * Fertigungstempo: kalibriert auf "erster Träger unter Idealbedingungen in
   * einem Spielmonat" (200 spezialisierte Kolonien, Industriekomplex 12,
   * Spezialisierung 50 → 30 Spieltage).
   *
   * <p>Der Regler greift bewusst ERST NACH der Arbeitskraft-Bremse. Würde man
   * stattdessen {@code baseProductionHours} teilen, verschöbe sich mit der
   * Dauer auch die Bremsschwelle und jede Kolonie bräuchte das Neunfache an
   * Bevölkerung (beim Trägermodul 3,5 Mio. statt 394 000 Einwohner). So bleibt
   * der Einwohnerbedarf, wo er war, und nur die Zeit wird gestaucht.</p>
   */
  public static double productionSpeedMultiplier() {
    JsonNode node = ROOT.path("productionSpeedMultiplier");
    double value = node.isMissingNode() ? 1 : node.asDouble();
    if (value <= 0) throw new IllegalStateException("productionSpeedMultiplier muss größer als 0 sein, ist aber " + value);
    return value;
  }

  // --- REALZEIT-AUSNAHME ---------------------------------------------------
  // Die folgenden vier Werte sind die EINZIGEN Zeitangaben des Spiels, die
  // NICHT in Spielstunden rechnen und deshalb NICHT über Clock.hoursToMs
  // umgerechnet werden dürfen. Sie richten sich danach, wann ein MENSCH wieder
  // an den Rechner kommt, nicht danach, wie schnell die Spieluhr läuft.
  // Begründung siehe "_realTimeException" in shared/game-constants.json.

  /** REALZEIT-AUSNAHME: Aufbewahrung einer Benachrichtigung in echten Tagen. */
  public static double notificationRetentionRealDays() {
    return ROOT.path("notificationRetentionRealDays").asDouble();
  }

  /** REALZEIT-AUSNAHME: Aufbewahrung einer Spieler-Nachricht in echten Tagen. */
  public static double messageRetentionRealDays() {
    return ROOT.path("messageRetentionRealDays").asDouble();
  }

  /** REALZEIT-AUSNAHME: Mindestabstand zweier gleicher Versorgungswarnungen in echten Minuten. */
  public static double supplyWarningCooldownRealMinutes() {
    return ROOT.path("supplyWarningCooldownRealMinutes").asDouble();
  }

  /** REALZEIT-AUSNAHME: Nach so vielen echten Tagen ohne Anmeldung wird ein Kommandant gelöscht. */
  public static double inactivePlayerDeletionRealDays() {
    return ROOT.path("inactivePlayerDeletionRealDays").asDouble();
  }
  // --- Ende REALZEIT-AUSNAHME ---------------------------------------------

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
  /** Trägersprung ohne Gateway (Umsetzungskonzept/06_...md): Zeitfaktor gegenüber einem Gateway-Sprung. */
  public static double carrierTransitTimeFactor() {
    return ROOT.path("carrierTransitTimeFactor").asDouble();
  }

  /** Trägersprung ohne Gateway: Treibstofffaktor gegenüber einem Gateway-Sprung. */
  public static double carrierTransitFuelFactor() {
    return ROOT.path("carrierTransitFuelFactor").asDouble();
  }

  /** Kapseln je Korvettenmasse und Sprung, siehe {@code GameConstants.JUMP_FUEL_PER_CORVETTE_MASS_PER_HOP}. */
  public static double jumpFuelPerCorvetteMassPerHop() {
    return ROOT.path("jumpFuelPerCorvetteMassPerHop").asDouble();
  }

  /** Reichweite einer vollen Tankfüllung in Sprüngen – daraus folgt das Fassungsvermögen je Schiff. */
  public static double jumpFuelTankRangeHops() {
    return ROOT.path("jumpFuelTankRangeHops").asDouble();
  }
}
