package de.nebula.model;

public class RecipeInput {
  public String inputProductTypeId;
  public double quantity;

  public RecipeInput() {
  }

  public RecipeInput(String inputProductTypeId, double quantity) {
    this.inputProductTypeId = inputProductTypeId;
    this.quantity = quantity;
  }
}
