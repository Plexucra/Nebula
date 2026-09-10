package de.nebula.model;

/**
 * Kauf- oder Verkaufs-Order in EINEM Orderbuch je Handelsort
 * (Umsetzungskonzept/37): an einer Handelsgilde-Station ({@code planetId ==
 * null}, Umsetzungskonzept/22) oder am Planetaren Handelsposten eines
 * Planeten ({@code planetId} gesetzt). {@code ownerId == null} kennzeichnet
 * eine Order der Handelsgilde selbst (Market-Maker, nur an Stationen).
 *
 * <p>Am Posten kann eine Verkaufs-Order aus dem Lager einer Kolonie auf dem
 * Planeten gespeist sein ({@code sourceColonyId}) – dann füllt sie sich als
 * Dauerorder ({@code autoRelist}) aus diesem Lager nach und bleibt leer als
 * "schlafende" Order stehen. Ohne {@code sourceColonyId} speist sie sich aus
 * dem Depot des Kommandanten an diesem Ort.</p>
 */
public class MarketOrder {
  public String id;
  public String systemId;
  /** {@code null} = Handelsgilde-Station im System, sonst der Planet des Handelspostens. */
  public String planetId;
  public String productTypeId;
  public MarketOrderSide side;
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
  /** Monotoner Zähler für Preis-Zeit-Priorität innerhalb derselben Millisekunde, siehe {@code GameState.marketOrderSeq}. */
  public long seq;
  /**
   * Dauerorder (nur Verkauf): leer gekauft füllt sie sich sofort mit bis zu
   * {@code quantity} aus ihrer Quelle nach (Kolonielager oder Depot); reicht
   * die Quelle nicht, bleibt sie mit Restmenge 0 "schlafend" stehen und wird
   * beim nächsten Zugang geweckt ({@code MarketCommands.replenishDormantSellOrders}).
   */
  public boolean autoRelist;
  /** Kolonie auf dem Planeten, aus deren Lager diese Verkaufs-Order gespeist ist; {@code null} = aus dem Depot. */
  public String sourceColonyId;
}
