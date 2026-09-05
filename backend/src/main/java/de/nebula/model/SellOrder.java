package de.nebula.model;

/**
 * {@code Depot} = am Planetaren Handelsposten einer konkreten Kolonie
 * ({@code depotColonyId} gesetzt) eingestellt – entweder von deren Besitzer
 * ("Anbieten" im Lagerbestand) oder von einer dort gelandeten fremden Flotte
 * ({@code sourceFleetId} gesetzt). {@code Station} = am Systemhandelsposten
 * (keine Landung nötig), {@code depotColonyId} dann {@code null}.
 */
public class SellOrder {
  public String id;
  public String systemId;
  public TradeLocationType locationType;
  public String depotColonyId;
  public String sellerId;
  public String sellerName;
  public String productTypeId;
  public double quantity;
  public double remainingQuantity;
  public double pricePerUnit;
  public long createdAt;
  /**
   * true = sobald diese Order vollständig verkauft ist, wird im selben
   * Vorgang eine neue Order mit identischer quantity/pricePerUnit angelegt
   * (siehe "Anbieten" im Lagerbestand, Umsetzungskonzept/10_...md, §6).
   */
  public boolean autoRelist;
  /**
   * Gesetzt, wenn die Order aus der Fracht einer Flotte heraus eingestellt
   * wurde – ein Abbruch erstattet dann in die Fracht dieser Flotte zurück
   * (falls sie noch existiert) statt in ein Kolonielager.
   */
  public String sourceFleetId;
}
