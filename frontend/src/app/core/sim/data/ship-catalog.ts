import { ShipTypeDef } from '../../models';

/**
 * Kontersystem, siehe Mechanik/03_..., §2 (verbindlich, überschreibt die
 * frühere Formulierung in Konzeption/03_...): Korvette schlägt Kreuzer,
 * Kreuzer schlägt Zerstörer, Zerstörer schlägt Korvette. Der jeweilige
 * Multiplikator (×2 im Vorteil, ×0,5 im Nachteil, sonst ×1) wird erst im
 * Kampfsystem angewendet (Mechanik/04_..., §4) — hier steht nur, wer wen
 * kontert. Keine attack/hull-Werte: militärischer Wert = Produktionsaufwand,
 * siehe ShipTypeDef.
 */
export const SHIP_CATALOG: ShipTypeDef[] = [
  { productTypeId: 'p_corvette', class: 'Corvette', cargoMassKg: 0, cargoVolumeM3: 0, carrierSlotUsage: 1, countersClass: 'Cruiser' },
  { productTypeId: 'p_destroyer', class: 'Destroyer', cargoMassKg: 0, cargoVolumeM3: 0, carrierSlotUsage: 2, countersClass: 'Corvette' },
  { productTypeId: 'p_cruiser', class: 'Cruiser', cargoMassKg: 0, cargoVolumeM3: 0, carrierSlotUsage: 4, countersClass: 'Destroyer' },
  // Grobe Erstschätzung, siehe ProductType-Massen/Volumina in product-catalog.ts
  // (z. B. 1 Einheit Ferrometallerz = 800.000 kg – "genaue Zahl offen").
  { productTypeId: 'p_freighter', class: 'Freighter', cargoMassKg: 2_000_000, cargoVolumeM3: 3000, carrierSlotUsage: 3, countersClass: null },
  { productTypeId: 'p_carrier', class: 'Carrier', cargoMassKg: 0, cargoVolumeM3: 0, carrierSlotUsage: 0, countersClass: null },
  { productTypeId: 'p_trooptransport', class: 'TroopTransport', cargoMassKg: 0, cargoVolumeM3: 0, carrierSlotUsage: 2, countersClass: null },
];

export function findShipDef(productTypeId: string): ShipTypeDef {
  const found = SHIP_CATALOG.find(s => s.productTypeId === productTypeId);
  if (!found) throw new Error(`Unbekannter ShipType: ${productTypeId}`);
  return found;
}
