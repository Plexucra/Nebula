package de.nebula.model;

/** Instanz eines Gebäudes auf einer Kolonie – Umsetzungskonzept/01_..., §1. */
public class Building {
  public String id;
  public String colonyId;
  public String typeId;
  public int level;
  /** {@code null}, solange kein Ausbau-/Neubauauftrag läuft. */
  public PendingBuildingOrder pendingOrder;
  public DefenseActivationState activationState;
  public Long activationCompletesAt;
}
