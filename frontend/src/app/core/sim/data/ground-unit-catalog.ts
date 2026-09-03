import { GroundUnitTypeDef } from '../../models';

/**
 * Transport-Slot-Verbrauch nach Mechanik/05_..., §6 (0,05 / 1 / 1 / 20 –
 * hier vereinfacht). Keine attack/defense-Werte: militärischer Wert =
 * Produktionsaufwand, siehe GroundUnitTypeDef.
 *
 * Konterrichtung (analog zur Schiffs-Kontermatrix, siehe Mechanik/03_...,
 * §2 und die Entscheidung in Mechanik/04_..., "Offene Zahlenfragen"):
 * Leichte Drohne schlägt Schwere, Schwere schlägt Mittlere,
 * Mittlere schlägt Leichte.
 */
export const GROUND_UNIT_CATALOG: GroundUnitTypeDef[] = [
  { productTypeId: 'p_soldier', class: 'Soldier', transportSlotUsage: 0.05, countersClass: null },
  { productTypeId: 'p_drone_light', class: 'LightDrone', transportSlotUsage: 1, countersClass: 'HeavyDrone' },
  { productTypeId: 'p_drone_medium', class: 'MediumDrone', transportSlotUsage: 1, countersClass: 'LightDrone' },
  { productTypeId: 'p_drone_heavy', class: 'HeavyDrone', transportSlotUsage: 20, countersClass: 'MediumDrone' },
];

export function findGroundUnitDef(productTypeId: string): GroundUnitTypeDef {
  const found = GROUND_UNIT_CATALOG.find(u => u.productTypeId === productTypeId);
  if (!found) throw new Error(`Unbekannter GroundUnitType: ${productTypeId}`);
  return found;
}
