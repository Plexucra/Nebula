package de.nebula.model;

/** Statischer Bauplan/Katalogeintrag – Umsetzungskonzept/01_..., §1. */
public class BuildingType {
  public String id;
  public String name;
  public BuildingCategory category;
  public String description;
  public int maxLevel;
  public double buildPointsPerLevel;
  public double baseCostPerLevel;
  public double baseHoursPerLevel;
  public double upkeepPerLevel;
  /** Für ProductionFacility: parallele Produktionsslots pro Level. {@code null} sonst. */
  public Integer productionSlotsPerLevel;
  /** Für Infrastructure: Bevölkerungs-Referenzkapazität pro Level. {@code null} sonst. */
  public Integer populationCapacityPerLevel;
}
