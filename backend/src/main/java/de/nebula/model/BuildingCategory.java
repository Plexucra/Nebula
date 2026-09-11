package de.nebula.model;

/**
 * {@code Infrastructure}: ausschließlich das Gebäude "Infrastruktur"
 * (Bebauungsplätze, Elerium-Verbrauch, planetweit begrenzt).
 * {@code Housing}: Wohnkomplex – liefert allein die Wohnkapazität.
 * {@code Research}: Forschungszentrum – bezahlte Plätze für Akademiker
 * (Umsetzungskonzept/38_...md), Kostenkurve wie der Wohnkomplex.
 * Siehe Umsetzungskonzept/17_...md.
 */
public enum BuildingCategory {
  Infrastructure, Housing, ProductionFacility, PlanetaryDefense, Research
}
