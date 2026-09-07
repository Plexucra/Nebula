import { ProductType } from '../models';

/** Explizite Icons für die bekanntesten/prominentesten Produkte (Rohstoffe, Schiffe, Bodeneinheiten). */
const PRODUCT_ICON: Record<string, string> = {
  p_ferrometall: '⛏️', p_leichtmetall: '⛏️', p_refraktaer: '⛏️', p_leitmetall: '⛏️', p_edelmetall: '⛏️',
  p_seltenerden: '⛏️', p_technometall: '⛏️', p_silikat: '🪨', p_kohlenstoff: '🪨', p_salz: '🧂',
  p_radionuklid: '☢️', p_eis: '🧊', p_atmosphaere: '💨', p_edelgas: '💨', p_kohlenwasserstoff: '🛢️',
  p_isotopentraeger: '⚛️', p_elerium: '☢️',
  p_corvette: '🛩️', p_destroyer: '🚢', p_cruiser: '🛳️', p_freighter: '📦', p_carrier: '🛸', p_trooptransport: '🚐',
  p_soldier: '💂', p_drone_light: '🤖', p_drone_medium: '🤖', p_drone_heavy: '🤖',
};

/**
 * Schlüsselwort-Fallback für die übrigen ~190 Produkte des erweiterten
 * Produktionsbaums (Nebula_Planetentypen_..., §10): ein Icon pro Produkt
 * von Hand zu pflegen ist bei dieser Katalogtiefe nicht praktikabel –
 * grobe visuelle Wiedererkennung nach Wortbestandteil/Kategorie genügt.
 */
function keywordIcon(p: ProductType): string {
  const n = p.name.toLowerCase();
  if (n.includes('waffe') || n.includes('gefechtskopf') || n.includes('initiator')) return '⚔️';
  if (n.includes('schild')) return '🛡️';
  if (n.includes('triebwerk') || n.includes('manöver') || n.includes('antrieb')) return '🚀';
  if (n.includes('sensor') || n.includes('navigation') || n.includes('kommunikation')) return '📡';
  if (n.includes('chip') || n.includes('wafer') || n.includes('halbleiter')) return '🧩';
  if (n.includes('reaktor') || n.includes('kraftwerk') || n.includes('energie')) return '⚡';
  if (n.includes('elerium')) return '☢️';
  if (n.includes('rumpf') || n.includes('struktur') || n.includes('panzer') || n.includes('hitzeschild')) return '🔩';
  if (n.includes('habitat') || n.includes('besatzung') || n.includes('lebenserhaltung') || n.includes('truppenunterbring')) return '🫀';
  if (n.includes('hangar') || n.includes('fracht') || n.includes('lager')) return '📦';
  if (n.includes('werft')) return '🏗️';
  if (n.includes('medizin') || n.includes('pharma') || n.includes('hygiene')) return '💊';
  if (n.includes('nahrung') || n.includes('wasser') || n.includes('trinkwasser')) return '🥫';
  if (n.includes('kleidung') || n.includes('textil')) return '🧥';
  if (n.includes('elektronik') || n.includes('unterhaltung')) return '📺';
  if (n.includes('paket')) return '🎁';
  if (p.category === 'Ship') return '🛸';
  if (p.category === 'GroundUnit') return '🤖';
  if (p.category === 'RawResource') return '⛏️';
  if (p.category === 'EnergyModule') return '🔋';
  if (p.category === 'ShipModule') return '🔩';
  return '⚙️';
}

export function iconForProduct(p: ProductType): string {
  return PRODUCT_ICON[p.id] ?? keywordIcon(p);
}
