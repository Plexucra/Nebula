package de.nebula.model;

/**
 * Eine Zeile des Versorgungsinventars einer Kolonie (Umsetzungskonzept/25_...md):
 * was liegt im Lager, wie schnell wird es verbraucht und wie lange reicht es noch.
 *
 * <p>Bewusst mehr als ein roher Lagerauszug: die interessante Frage ist nicht
 * „wie viel habe ich", sondern „wie lange komme ich damit hin". {@code quantity}
 * ist immer eine ganze Stückzahl – Bruchteile leben ausschließlich in den
 * Übertragskonten und werden hier als {@code pendingFraction} sichtbar gemacht,
 * damit die Buchführung nachvollziehbar bleibt.</p>
 */
public class SupplyInventoryEntry {
  public String productTypeId;
  public String name;
  public ProductCategory category;
  /** Lagerbestand in ganzen Stücken. */
  public double quantity;
  /** Verbrauch je Spielstunde; 0 bei allem, was die Kolonie nicht laufend verbraucht. */
  public double consumptionPerGameHour;
  /** Wie viele Spielstunden der Bestand beim aktuellen Verbrauch noch reicht; {@code null} ohne laufenden Verbrauch. */
  public Double coverageGameHours;
  /** Offener Rest im Übertragskonto (immer kleiner als ein Stück), siehe {@code FractionPot}. */
  public double pendingFraction;
}
