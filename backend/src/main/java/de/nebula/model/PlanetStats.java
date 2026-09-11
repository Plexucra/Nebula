package de.nebula.model;

/** Die vier zentralen Planetenwerte, siehe Konzeption/07_..., §5. */
public class PlanetStats {
  public String colonyId;
  public double infrastructurePct;
  public double securityPct;
  /** Lebensstandard der ARBEITER aus ihren Pflichtgütern – trägt Loyalität, Sicherheit und Wachstum wie bisher. */
  public double standardOfLivingPct;
  /**
   * Lebensstandard der AKADEMIKER aus allen ihren Gütern (Umsetzungskonzept/38_...md,
   * Teil D) – entscheidet allein über Zuwachs und Rückgang der Akademiker.
   */
  public double academicStandardOfLivingPct;
  /** Loyalität ist bei 100% gedeckelt. */
  public double loyaltyPct;
  public long lastRecalculatedAt;
}
