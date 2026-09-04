import { Id } from './common.model';

export type TradeLocationType = 'Station' | 'Depot';

export interface SellOrder {
  id: Id;
  systemId: Id;
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
}

export interface BuyOrder {
  id: Id;
  systemId: Id;
  buyerId: Id;
  productTypeId: Id;
  quantity: number;
  pricePerUnit: number;
  createdAt: number;
}

export interface ConsumptionState {
  colonyId: Id;
  previousN: number;
  currentBudget: number;
  perGoodDemand: { productTypeId: Id; need: number; boughtSmoothed: number }[];
}
