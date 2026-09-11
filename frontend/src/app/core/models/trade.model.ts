import { Id } from './common.model';

export type MarketOrderSide = 'Buy' | 'Sell';

/**
 * Kauf- oder Verkaufs-Order in EINEM Orderbuch je Handelsort
 * (Konzeption/Umsetzungskonzept/37): an einer Handelsgilde-Station
 * (`planetId === null`, Umsetzungskonzept/22) oder am Planetaren Handelsposten
 * eines Planeten (`planetId` gesetzt). Kauf- und Verkaufs-Orders kreuzen sich
 * sofort, auch teilweise, zum Preis der älteren Order. `ownerId === null`
 * kennzeichnet eine Order der Handelsgilde selbst (Market-Maker, nur an
 * Stationen) – solche Orders lassen sich nicht zurückziehen.
 *
 * Am Posten handeln zwei Kommandanten nur mit Handelsvertrag (das Matching
 * überspringt Paare ohne Vertrag); auch die Bevölkerung einer Kolonie kauft
 * aus der Verkaufsseite nur beim eigenen Kommandanten oder dessen
 * Handelsvertragspartnern.
 */
export interface MarketOrder {
  id: Id;
  systemId: Id;
  /** `null` = Handelsgilde-Station im System, sonst der Planet des Handelspostens. */
  planetId: Id | null;
  productTypeId: Id;
  side: MarketOrderSide;
  ownerId: Id | null;
  ownerName: string;
  limitPrice: number;
  quantity: number;
  remainingQuantity: number;
  escrowedCredits: number;
  createdAt: number;
  /**
   * Dauerorder (nur Verkauf): leer gekauft füllt sie sich sofort aus ihrer
   * Quelle nach (Kolonielager bzw. Depot); reicht die nicht, bleibt sie mit
   * Restmenge 0 stehen, bis wieder Nachschub kommt.
   */
  autoRelist: boolean;
  /** Kolonie auf dem Planeten, aus deren Lager diese Verkaufs-Order gespeist ist; `null` = aus dem Depot. */
  sourceColonyId: Id | null;
  /**
   * Gebot der BEVÖLKERUNG dieser Kolonie (Umsetzungskonzept/38): `ownerId` ist
   * ihr Kommandant (Vertragsregel), das Geld kommt aus dem Bevölkerungs-Wallet,
   * die Ware geht in ihren Vorrat. Nicht zurückziehbar, nicht umpreisbar – die
   * Bevölkerung stellt es an jedem Kolonietag neu. `null` = normale Order.
   */
  populationColonyId: Id | null;
}

/**
 * Eine Warenposition im unbegrenzten Depot eines Kommandanten an einem
 * Handelsort. Wer eine Kolonie auf dem Planeten hat, braucht am Posten kein
 * Depot – sein Lager ist sein Depot.
 */
export interface DepotEntry {
  systemId: Id;
  planetId: Id | null;
  ownerId: Id;
  productTypeId: Id;
  quantity: number;
}
