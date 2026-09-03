import { Id } from './common.model';

export type WalletOwnerType = 'Player' | 'Population';

export interface Wallet {
  id: Id;
  ownerType: WalletOwnerType;
  ownerId: Id;
  balance: number;
}

export type TransactionReason =
  | 'Wage'
  | 'Consumption'
  | 'FleetUpkeep'
  | 'BuildingUpkeep'
  | 'GatewayFee'
  | 'Trade'
  | 'MoneyCreation'
  | 'Transfer'
  | 'Construction'
  | 'Production'
  | 'Recruitment'
  | 'Tax'
  | 'Subsidy';

export interface Transaction {
  id: Id;
  fromWalletId: Id | null;
  toWalletId: Id | null;
  amount: number;
  reason: TransactionReason;
  at: number;
  note?: string;
}
