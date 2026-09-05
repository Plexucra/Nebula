package de.nebula.model;

/** Aktuell im TS-Original nirgends erzeugt/konsumiert (kein Käufer-seitiges Limit-Order-Feature implementiert) – nur der Vollständigkeit halber mitportiert. */
public class BuyOrder {
  public String id;
  public String systemId;
  public String buyerId;
  public String productTypeId;
  public double quantity;
  public double pricePerUnit;
  public long createdAt;
}
