package de.nebula.model;

/**
 * Eine Warenposition im UNBEGRENZTEN Depot eines Kommandanten an EINER
 * Handelsgilde-Station (Umsetzungskonzept/22_...md) – Gegenstück zu
 * {@link WarehouseEntry}, aber je (System, Besitzer, Ware) statt je Kolonie,
 * da Handelsgilde-Stationen keine Kolonie besitzen.
 */
public class HubDepotEntry {
  public String systemId;
  public String ownerId;
  public String productTypeId;
  public double quantity;
}
