/**
 * Ein Messpunkt für die Universums-Stabilitätsstatistik (siehe Feature
 * "Statistiken"). Wird periodisch vom Tick-Loop aufgezeichnet, damit sich
 * die Entwicklung von Wirtschaft und Bevölkerung über die Zeit
 * beobachten lässt – unabhängig vom eigenen Spielstand.
 */
export interface UniverseStatSnapshot {
  at: number;
  colonyCount: number;
  strugglingColonyCount: number;
  totalPopulation: number;
  totalCredits: number;
  avgInfrastructurePct: number;
  avgSecurityPct: number;
  avgStandardOfLivingPct: number;
  avgLoyaltyPct: number;
  openSellOrderCount: number;
}

/**
 * Der entschiedene Krieg (`VictoryCommands` im Backend): Eine Partei besitzt
 * als EINZIGE noch Kolonien – alle gegnerischen Kolonien sind gefallen.
 * `null`, solange das Spiel offen ist.
 */
export interface GameVictory {
  partyId: string;
  partyName: string;
  /** true = Lager (mehrere Kommandanten), false = einzelner Kommandant. */
  camp: boolean;
  members: string[];
  defeated: string[];
  declaredAt: number;
  colonies: number;
}
