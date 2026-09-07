package de.nebula.model;

/**
 * {@code Infrastructure}: ausschließlich das Gebäude "Infrastruktur"
 * (Bebauungsplätze, Elerium-Verbrauch, planetweit begrenzt).
 * {@code Housing}: Wohnkomplex – liefert allein die Wohnkapazität.
 * Siehe Umsetzungskonzept/17_...md.
 */
public enum BuildingCategory {
  Infrastructure, Housing, ProductionFacility, PlanetaryDefense
}
