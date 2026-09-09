package de.nebula.model;

import java.util.List;

/**
 * Statischer Bauplan/Katalogeintrag – Daten in {@code shared/catalog/buildings.json},
 * Regeln in Umsetzungskonzept/17_...md. Keine Höchststufe mehr (außer der
 * planetweiten Grenze für Infrastruktur), keine Bebauungspunkte – jede Stufe
 * eines Nicht-Infrastruktur-Gebäudes belegt genau einen Bebauungsplatz.
 */
public class BuildingType {
  public String id;
  public String name;
  public BuildingCategory category;
  public String description;
  public double baseCostPerLevel;
  public double baseHoursPerLevel;
  public double upkeepPerLevel;
  /** Für Housing: Wohnkapazität pro Level. {@code null} sonst. */
  public Integer housingCapacityPerLevel;
  /** Baustoffbedarf je Ausbau, siehe {@link BuildingMaterial}. */
  public List<BuildingMaterial> materials;
}
