import { Id } from './common.model';

export type GatewayDiscoveryState = 'Hidden' | 'Discovered' | 'Activating' | 'Active';

export interface Gateway {
  id: Id;
  systemId: Id;
  state: GatewayDiscoveryState;
  discoveredAt: number | null;
  activatedAt: number | null;
  activatingCompletesAt: number | null;
  reachableSystemIds: Id[];
}

export interface GatewayWeightEntry {
  playerId: Id;
  playerName: string;
  weight: number;
}
