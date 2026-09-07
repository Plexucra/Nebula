package de.nebula.model;

/**
 * Baustoffbedarf eines Gebäudetyps: ab Zielstufe {@code fromLevel} wird je
 * Ausbau {@code ceil(baseQuantity × Zielstufe^BUILDING_MATERIAL_LEVEL_EXPONENT)}
 * Stück aus dem Kolonielager abgezogen (Umsetzungskonzept/17_...md, Teil B).
 */
public class BuildingMaterial {
  public String productTypeId;
  public double baseQuantity;
  public int fromLevel;
}
