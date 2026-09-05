package de.nebula.model;

/** Die vier zentralen Planetenwerte, siehe Konzeption/07_..., §5. */
public class PlanetStats {
  public String colonyId;
  public double infrastructurePct;
  public double securityPct;
  public double standardOfLivingPct;
  /** Loyalität ist bei 100% gedeckelt. */
  public double loyaltyPct;
  public long lastRecalculatedAt;
}
