package de.nebula.model;

/**
 * Eine Warenposition im unbegrenzten Depot eines Kommandanten an einem
 * Handelsort: Handelsgilde-Station ({@code planetId == null},
 * Umsetzungskonzept/22) oder Planetarer Handelsposten ({@code planetId}
 * gesetzt, Umsetzungskonzept/37). Wer eine Kolonie auf dem Planeten hat,
 * braucht dort kein Depot – sein Lager ist sein Depot.
 */
public class DepotEntry {
  public String systemId;
  public String planetId;
  public String ownerId;
  public String productTypeId;
  public double quantity;
}
