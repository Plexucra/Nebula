package de.nebula.model;

/** Ausbau-/Neubauauftrag eines {@link Building} – Teilobjekt, siehe {@code Building.pendingOrder} im TS-Original. */
public class PendingBuildingOrder {
  public int targetLevel;
  public long startedAt;
  public long completesAt;
}
