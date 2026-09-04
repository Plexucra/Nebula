/**
 * Platzhalter-Formeln für Werte, die in der Konzeption bewusst als "offene
 * Zahlenfrage" markiert sind (z. B. Mechanik/01_..., Mechanik/11_...).
 * Linear/einfach gehalten und an dieser einzigen Stelle austauschbar –
 * genau der Teil, an dem laut Auftrag konkret weitergearbeitet werden soll.
 */

export function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

/** Bebauungspunkte, die ein Gebäude auf Ziel-Level `level` belegt. */
export function buildPointsUsed(pointsPerLevel: number, level: number): number {
  return pointsPerLevel * level;
}

/** Kosten für den Ausbau von `fromLevel` auf `fromLevel + 1`. */
export function buildingUpgradeCost(baseCostPerLevel: number, fromLevel: number): number {
  return Math.round(baseCostPerLevel * (fromLevel + 1) * (1 + fromLevel * 0.08));
}

export function buildingUpgradeHours(baseHoursPerLevel: number, fromLevel: number): number {
  return baseHoursPerLevel * (fromLevel + 1);
}

/** Überbebauungsmalus: >1 sobald Summe aller Bebauungspunkte auf dem
 *  Planeten dessen buildCapacity übersteigt (Konzeption/01_..., §2). */
export function overbuildFactor(totalBuildPointsUsed: number, buildCapacity: number): number {
  if (totalBuildPointsUsed <= buildCapacity) return 1;
  const overshoot = totalBuildPointsUsed / buildCapacity;
  return clamp(overshoot, 1, 3);
}

/** Workforce-Verfügbarkeit als Geschwindigkeitsfaktor, grob an
 *  Bevölkerungsgröße gekoppelt (Konzeption/06_..., §1: mehr Bevölkerung
 *  ermöglicht mehr gleichzeitig nutzbare Arbeit). */
export function workforceFactor(population: number): number {
  return clamp(population / 400, 0.35, 5);
}

/**
 * Produktionsanlagen-Tempo (Industriekomplex/Werft/Ausbildungszentrum):
 * linear zur Stufe – Stufe 2 ist doppelt so schnell wie Stufe 1, Stufe 10
 * zehnmal so schnell. Stufe 0 (kein Gebäude) blockiert Produktion komplett
 * (siehe `computeProductionHours`, `Math.max(speed, 0.05)`-Untergrenze
 * verhindert dort nur eine Division durch 0, keine tatsächliche Produktion
 * ohne Gebäude).
 */
export function buildingLevelSpeedFactor(level: number): number {
  return level <= 0 ? 0 : level;
}

/** Tempobonus aus Produktspezialisierung: +10%/Stufe, siehe `specializationThresholdHours` für die Stufen-Kalibrierung. */
export function specializationSpeedFactor(level: number): number {
  return 1 + level * 0.1;
}

/**
 * XP-Schwelle (in Spezialisierungs-Produktionsstunden, siehe
 * `SimulatedGameApiService.registerProduced` – XP = tatsächlich verbrauchte
 * Produktionszeit, nicht Stückzahl) für den Aufstieg von `level` auf
 * `level + 1`. Linear wachsend (Kosten pro Stufe steigen gleichmäßig, ohne
 * explosionsartig zu werden), kalibriert auf EINE SPIELWOCHE (nicht real –
 * die Realzeit-Stauchung über `REAL_MS_PER_GAME_HOUR` ist hier bewusst
 * irrelevant): wer ein einzelnes Produkt eine Spielwoche lang (7 × 24 = 168
 * Spielstunden) ununterbrochen exklusiv produziert, erreicht kumuliert
 * Stufe 10 = +100% Tempo (Faktor 2×, siehe `specializationSpeedFactor`) –
 * kumulierte Schwelle bis Stufe 10 ist BASE × (1+2+…+10) = BASE × 55 = 168,
 * also BASE = 168/55 ≈ 3,055. Bei gleicher Formel wären es bis Stufe 50
 * (+500%) kumuliert BASE × 1275 ≈ 3895 Spielstunden ≈ 162 Spieltage ≈ 23
 * Spielwochen (≈ 5,4 Spielmonate) – deutlich länger als linear 5× die erste
 * Woche, weil jede weitere Stufe selbst mehr kostet. In Realzeit entspricht
 * das bei `REAL_MS_PER_GAME_HOUR` (2500 ms/Spielstunde) durchgehender
 * Produktion ca. 7 Realminuten bis Stufe 10 bzw. ca. 2,7 Realstunden bis
 * Stufe 50.
 */
const SPECIALIZATION_THRESHOLD_BASE = (7 * 24) / 55;
export function specializationThresholdHours(level: number): number {
  return SPECIALIZATION_THRESHOLD_BASE * (level + 1);
}

/**
 * Ausbeutefaktor für Rohstoffe aus der Fördergüte (0-100, siehe
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §2):
 * Ausstoß = ... × (0,20 + 1,30 × Fördergüte / 100). Ersetzt die frühere
 * freihändige Konzentrationsskala (0,2-4) durch die dort empfohlene
 * Formel – jedes Vorkommen bleibt grundsätzlich förderbar, ein
 * Spitzenvorkommen (100) liefert aber mehr als das Siebenfache eines
 * Spurenvorkommens (1).
 */
export function resourceConcentrationFactor(foerdergute: number): number {
  return 0.2 + 1.3 * clamp(foerdergute, 0, 100) / 100;
}

/** Vier Planetenwerte, siehe Konzeption/07_..., §5 und
 *  Umsetzungskonzept/08_..., §5. */
export function infrastructurePct(builtCapacity: number, population: number): number {
  if (population <= 0) return 100;
  return clamp((builtCapacity / population) * 100, 0, 400);
}

/**
 * Garnisonsbasierte Sicherheit plus ein loyalitätsproportionaler Sockel
 * (Konzeption/Mechanik/11_..., §3/§8: "hohe Loyalität → weniger notwendige
 * Truppen"): Eine wirklich loyale Bevölkerung hält notfalls selbst Ordnung,
 * auch ganz ohne stationierte Drohnen. Der Sockel ersetzt keine Garnison
 * vollständig (max. 30 % bei 100 % Loyalität) – für hohe Sicherheitswerte
 * bleibt eine aktive Garnison nötig.
 */
export function securityPct(garrisonStrength: number, population: number, loyaltyPct: number): number {
  const reference = Math.max(population * 0.05, 5);
  const garrisonSecurity = (garrisonStrength / reference) * 100;
  const loyaltyFloor = loyaltyPct * 0.3;
  return clamp(Math.max(garrisonSecurity, loyaltyFloor), 0, 400);
}

export function loyaltyDelta(opts: {
  isHomeworld: boolean;
  standardOfLivingPct: number;
  securityPct: number;
}): number {
  const base = opts.isHomeworld ? 0.35 : 0.12;
  let condition = 0;
  if (opts.standardOfLivingPct < 60) condition -= 0.2;
  else if (opts.standardOfLivingPct > 100) condition += 0.05;
  if (opts.securityPct < 40) condition -= 0.15;
  return base + condition;
}

/**
 * Kombinierter "Zufriedenheits"-Faktor aus Lebensstandard und Sicherheit,
 * der die Wachstumsgeschwindigkeit relativ zur Referenzgeschwindigkeit
 * (1,0 = 100 %) skaliert. Eigene Funktion statt Inline-Berechnung in
 * `populationGrowthDelta`, damit die UI (Kolonie-Detail, Tab
 * "Bevölkerung") exakt denselben Wert anzeigen kann, der auch das
 * Wachstum steuert – keine zweite, potenziell abweichende Kopie der
 * Formel.
 */
export function growthConditionFactor(standardOfLivingPct: number, securityPct: number): number {
  const livingFactor = clamp(standardOfLivingPct / 100, 0.15, 1.6);
  const securityFactor = clamp(securityPct / 100, 0.5, 1.2);
  return livingFactor * securityFactor;
}

export function populationGrowthDelta(opts: {
  population: number;
  capacity: number;
  standardOfLivingPct: number;
  securityPct: number;
}): number {
  const room = opts.capacity - opts.population;
  if (room <= 0) return opts.population * -0.002; // leichte Schrumpfung bei Überbevölkerung
  return room * 0.018 * growthConditionFactor(opts.standardOfLivingPct, opts.securityPct);
}

export const CREDITS_PER_NEW_INHABITANT = 8;

/**
 * Produktionsaufwand als militärischer Basiswert (Mechanik/04_..., §2):
 * "Kein zusätzlicher abstrakter Kampfkraftwert" — Schaden UND Haltbarkeit
 * einer Einheit im Kampf leiten sich ausschließlich hieraus ab. Wird
 * bereits jetzt für die Sicherheits-Kennzahl (stationierte Bodentruppen)
 * verwendet und ist die vorgesehene Grundlage für das spätere Kampfsystem.
 */
export function productionAspect(baseWorkforceRequired: number, baseProductionHours: number): number {
  return baseWorkforceRequired * baseProductionHours;
}

/**
 * Globale Kampf-Faktoren (Mechanik/04_..., §2-3 – Zahlenwert selbst als
 * "offene Zahlenfrage" markiert, hier festgelegt): Gruppenschaden =
 * Anzahl × Produktionsaufwand je Einheit × `COMBAT_DAMAGE_FACTOR`,
 * Haltbarkeit = Produktionsaufwand je Einheit × `COMBAT_DURABILITY_FACTOR`.
 * Verhältnis 0,2:1 erfüllt die dort genannte Vorgabe "bei neutralem Konter
 * (×1) verliert eine Seite gegen eine gleich starke Seite pro Tick rund 20%
 * ihres Werts" – nach 5 Ticks (§Kampftick, `COMBAT_TICK_HOURS`) ist eine
 * gleich starke, neutrale Streitmacht bei Dauerbeschuss vollständig
 * vernichtet.
 */
export const COMBAT_DAMAGE_FACTOR = 0.2;
export const COMBAT_DURABILITY_FACTOR = 1;

/** Dauer eines Kampf-Ticks in Spielstunden (Mechanik/04_..., §6). */
export const COMBAT_TICK_HOURS = 8;

/**
 * Konter-Multiplikator (Mechanik/03_..., §2 und 04_..., §4): ×2 im
 * Vorteil (die eigene Klasse kontert die des Ziels), ×0,5 im Nachteil
 * (wird selbst gekontert), sonst ×1. Symmetrisch aus Angreifer- UND
 * Verteidiger-`countersClass` ableitbar, hier bewusst nur EINE Richtung
 * abgefragt (`attackerCountersDefender`) – der Aufrufer prüft die jeweils
 * andere Richtung für den Fall des Nachteils separat (siehe
 * `SimulatedGameApiService.resolveBattleTick`).
 */
export function counterMultiplier(attackerCountersDefenderClass: boolean, defenderCountersAttackerClass: boolean): number {
  if (attackerCountersDefenderClass) return 2;
  if (defenderCountersAttackerClass) return 0.5;
  return 1;
}
