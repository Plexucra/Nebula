import { Id } from './common.model';

export type TradeLocationType = 'Station' | 'Depot';

export interface SellOrder {
  id: Id;
  systemId: Id;
  /**
   * `'Depot'` = am Planetaren Handelsposten einer konkreten Kolonie
   * (`depotColonyId` gesetzt) eingestellt – entweder von deren Besitzer
   * (über "Anbieten" im Lagerbestand) oder von einer dort gelandeten
   * fremden Flotte (`sourceFleetId` gesetzt, siehe `createSellOrderFromFleet`).
   * `'Station'` = am Systemhandelsposten (keine Landung nötig), `depotColonyId`
   * ist dann `null`.
   */
  locationType: TradeLocationType;
  depotColonyId: Id | null;
  sellerId: Id;
  sellerName: string;
  productTypeId: Id;
  quantity: number;
  remainingQuantity: number;
  pricePerUnit: number;
  createdAt: number;
  /**
   * true = sobald diese Order durch einen Kauf vollständig verkauft ist
   * (`remainingQuantity` erreicht 0), wird im selben Vorgang eine neue Order
   * mit identischer `quantity`/`pricePerUnit` angelegt (siehe "Anbieten" im
   * Lagerbestand, Konzeption/Umsetzungskonzept/10_...md, §6). Von Hand über
   * `createSellOrder` erzeugte Einzel-Orders lassen dieses Feld `false`.
   */
  autoRelist: boolean;
  /**
   * Gesetzt, wenn die Order aus der Fracht einer Flotte heraus eingestellt
   * wurde (`createSellOrderFromFleet`, siehe Klassendoku `locationType`) –
   * ein Abbruch erstattet dann in die Fracht dieser Flotte zurück (falls sie
   * noch existiert) statt in ein Kolonielager, da die Ware nie dort lag.
   */
  sourceFleetId: Id | null;
}

export type HubOrderSide = 'Buy' | 'Sell';

/**
 * Kauf- oder Verkaufs-Order im Orderbuch einer Handelsgilde-Station
 * (Konzeption/Umsetzungskonzept/22_...md) – anders als {@link SellOrder} ist
 * das hier ein echtes zweiseitiges Orderbuch mit sofortiger (Teil-)Ausführung
 * beim Kreuzen. `ownerId === null` kennzeichnet eine Order der Handelsgilde
 * selbst (Market-Maker) statt eines Spielers – solche Orders lassen sich
 * nicht zurückziehen.
 */
export interface HubOrder {
  id: Id;
  systemId: Id;
  productTypeId: Id;
  side: HubOrderSide;
  ownerId: Id | null;
  ownerName: string;
  limitPrice: number;
  quantity: number;
  remainingQuantity: number;
  escrowedCredits: number;
  createdAt: number;
}

/** Eine Warenposition im unbegrenzten Depot eines Kommandanten an einer Handelsgilde-Station. */
export interface HubDepotEntry {
  systemId: Id;
  ownerId: Id;
  productTypeId: Id;
  quantity: number;
}

export interface ConsumptionState {
  colonyId: Id;
  previousN: number;
  currentBudget: number;
  perGoodDemand: { productTypeId: Id; need: number; boughtSmoothed: number }[];
}
