package de.nebula.model;

public class FleetShipGroup {
  public String shipProductTypeId;
  public double quantity;

  public FleetShipGroup() {
  }

  public FleetShipGroup(String shipProductTypeId, double quantity) {
    this.shipProductTypeId = shipProductTypeId;
    this.quantity = quantity;
  }
}
