package de.nebula.model;

/** Ein Messpunkt der Universums-Stabilitätsstatistik, periodisch vom Tick-Loop aufgezeichnet. */
public class UniverseStatSnapshot {
  public long at;
  public int colonyCount;
  public int strugglingColonyCount;
  public double totalPopulation;
  public double totalCredits;
  public double avgInfrastructurePct;
  public double avgSecurityPct;
  public double avgStandardOfLivingPct;
  public double avgLoyaltyPct;
  public int openSellOrderCount;
}
