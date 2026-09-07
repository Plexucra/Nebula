package de.nebula.model;

/**
 * Kauf- oder Verkaufs-Order im Orderbuch einer Handelsgilde-Station
 * (Umsetzungskonzept/22_...md) – Ersatz für das nie verwendete alte
 * {@code BuyOrder}-Modell (kein {@code remainingQuantity}, kein Escrow-Feld,
 * daher für ein echtes Orderbuch ungeeignet). {@code ownerId == null}
 * kennzeichnet eine Order der Handelsgilde selbst (Market-Maker) statt eines
 * Spielers.
 */
public class HubOrder {
  public String id;
  public String systemId;
  public String productTypeId;
  public HubOrderSide side;
  /** {@code null} = Handelsgilde (Market-Maker), sonst die Spieler-ID. */
  public String ownerId;
  public String ownerName;
  public double limitPrice;
  public double quantity;
  public double remainingQuantity;
  /**
   * NUR bei Kauf-Orders belegt: die beim Einstellen sofort aus dem Wallet
   * gebuchten Credits (Menge × Limitpreis), die bei jeder Ausführung um genau
   * den gezahlten Betrag sinken und beim Entfernen der Order (voll ausgeführt
   * oder zurückgezogen) als Rest erstattet werden – Escrow als laufender
   * Saldo statt einer neu berechneten Differenz, damit nichts auseinanderlaufen
   * kann. Bei Verkaufs-Orders ist die Ware selbst über {@code remainingQuantity}
   * gebunden, ein zusätzliches Feld ist dafür nicht nötig.
   */
  public double escrowedCredits;
  public long createdAt;
  /** Monotoner Zähler für Preis-Zeit-Priorität innerhalb derselben Millisekunde, siehe {@code GameState.hubOrderSeq}. */
  public long seq;
}
