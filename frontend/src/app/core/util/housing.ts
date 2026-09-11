/**
 * Belegung des Wohnraums in Prozent aus dem Planetenwert `infrastructurePct`.
 *
 * Der Server liefert `infrastructurePct` = Wohnraum ÷ Einwohner × 100,
 * gedeckelt bei 400 (`Formulas.infrastructurePct`). Als „Wohnraum 400 %"
 * angezeigt, war das wenig sprechend (Gesamttest 11.9.2026) – die
 * Bevölkerungsseite sagt es richtig herum: „15,7 % belegt". Beim Deckel ist
 * nur bekannt, dass höchstens ein Viertel belegt ist; dann `null`.
 */
export function housingOccupancyPct(infrastructurePct: number): number | null {
  if (infrastructurePct >= 400) return null;
  if (infrastructurePct <= 0) return 100;
  return Math.min(100, 10000 / infrastructurePct);
}
