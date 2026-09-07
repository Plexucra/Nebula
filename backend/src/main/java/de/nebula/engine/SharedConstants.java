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

  public static double realMsPerGameHour() {
    return ROOT.path("realMsPerGameHour").asDouble();
  }

  public static double notificationRetentionGameHours() {
    return ROOT.path("notificationRetentionGameHours").asDouble();
  }

  public static double messageRetentionGameHours() {
    return ROOT.path("messageRetentionGameHours").asDouble();
  }

  // --- Bebauung / Infrastruktur (Umsetzungskonzept/17_...md) ---------------
  public static int slotsPerInfrastructureLevel() {
    return ROOT.path("slotsPerInfrastructureLevel").asInt();
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
}
