package de.nebula.model;

/** Aktiver Zustand der Bevölkerungsentwicklung, siehe {@code Formulas.populationGrowthDelta}. */
public enum PopulationGrowthState {
  Shrinking, Holding, Growing, Overcrowded,
  /**
   * Die Nahrungsversorgung deckt gerade den BESTAND, aber keinen Zuwachs
   * (Umsetzungskonzept/34_...md, §J 5). Eigener Zustand statt eines stillen
   * {@code Holding}, weil der Kommandant sonst nicht sieht, WARUM seine
   * Kolonie stehen bleibt – die Wohnkapazität ließe 20 000 zu, der Acker aber
   * nur 6 000.
   */
  FoodLimited
}
