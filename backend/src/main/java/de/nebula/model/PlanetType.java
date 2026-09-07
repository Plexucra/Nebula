package de.nebula.model;

/**
 * Wirtschaftlich relevante Oberflächen-/Zusammensetzungsklasse, siehe
 * Konzeption/Umsetzungskonzept/Nebula_Planetentypen_Rohstoffprofile_
 * Produktionsbaum.md, §5. Bestimmt den Fördergüte-Bereich je Rohstoff (§6) –
 * keine exakte Astronomieklasse.
 */
public enum PlanetType {
  TemperierterBiosphaerenplanet, Silikatplanet, Wuestenplanet, Ozeanplanet, Eisplanet,
  Vulkanplanet, Metallplanet, Kohlenstoffplanet, Supererde, Planetoid, Gasriese, Eisriese, Schwefelplanet,
  /** Mond eines Gasriesen – im Gegensatz zum Gasriesen selbst besiedelbar, siehe {@code WorldSeed}. */
  Gasriesenmond
}
