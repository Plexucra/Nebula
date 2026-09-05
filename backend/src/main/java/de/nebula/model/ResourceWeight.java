package de.nebula.model;

public class ResourceWeight {
  public String resourceTypeId;
  public double weight;

  public ResourceWeight() {
  }

  public ResourceWeight(String resourceTypeId, double weight) {
    this.resourceTypeId = resourceTypeId;
    this.weight = weight;
  }
}
