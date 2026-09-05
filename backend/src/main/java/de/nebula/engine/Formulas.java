package de.nebula.engine;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/engine/formulas.ts}.
 * Platzhalter-Formeln für Werte, die in der Konzeption bewusst als "offene
 * Zahlenfrage" markiert sind (z. B. Mechanik/01_..., Mechanik/11_...).
 * Linear/einfach gehalten und an dieser einzigen Stelle austauschbar.
 *
 * <p><b>WICHTIG für die Migration (siehe Umsetzungskonzept/13_...md):</b>
 * diese Klasse MUSS bitweise dieselben Ergebnisse liefern wie das
 * TS-Original – jede künftige Änderung an einer der beiden Seiten ohne die
 * jeweils andere ist ein Bug. Bis zu einem automatisierten Parity-Test
 * (offener Punkt im Migrationsplan) gilt: bei Unstimmigkeiten ist die
 * TS-Version die Referenz (dort seit Session-Beginn live erprobt).</p>
 */
public final class Formulas {
  private Formulas() {
  }

  public static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  /** Bebauungspunkte, die ein Gebäude auf Ziel-Level {@code level} belegt. */
  public static double buildPointsUsed(double pointsPerLevel, int level) {
    return pointsPerLevel * level;
  }

  /** Kosten für den Ausbau von {@code fromLevel} auf {@code fromLevel + 1}. */
  public static double buildingUpgradeCost(double baseCostPerLevel, int fromLevel) {
    return Math.round(baseCostPerLevel * (fromLevel + 1) * (1 + fromLevel * 0.08));
  }

  public static double buildingUpgradeHours(double baseHoursPerLevel, int fromLevel) {
    return baseHoursPerLevel * (fromLevel + 1);
  }

  /** Überbebauungsmalus: {@code > 1}, sobald die Summe aller Bebauungspunkte auf dem Planeten dessen buildCapacity übersteigt (Konzeption/01_..., §2). */
  public static double overbuildFactor(double totalBuildPointsUsed, double buildCapacity) {
    if (totalBuildPointsUsed <= buildCapacity) return 1;
    double overshoot = totalBuildPointsUsed / buildCapacity;
    return clamp(overshoot, 1, 3);
  }

  /**
   * Workforce-Verfügbarkeit als Geschwindigkeitsfaktor, grob an
   * Bevölkerungsgröße gekoppelt (Konzeption/06_..., §1: mehr Bevölkerung
   * ermöglicht mehr gleichzeitig nutzbare Arbeit). Erreicht sein Maximum (5)
   * bereits ab ~2000 Einwohnern – siehe Umsetzungskonzept/12_...md für die
   * Analyse, warum das der dominante Geschwindigkeitshebel im Spiel ist.
   */
  public static double workforceFactor(double population) {
    return clamp(population / 400, 0.35, 5);
  }

  /**
   * Produktionsanlagen-Tempo (Industriekomplex/Werft/Ausbildungszentrum):
   * linear zur Stufe – Stufe 2 ist doppelt so schnell wie Stufe 1, Stufe 10
   * zehnmal so schnell. Stufe 0 (kein Gebäude) blockiert Produktion komplett.
   */
  public static double buildingLevelSpeedFactor(int level) {
    return level <= 0 ? 0 : level;
  }

  /** Tempobonus aus Produktspezialisierung: +10%/Stufe, siehe {@link #specializationThresholdHours}. */
  public static double specializationSpeedFactor(int level) {
    return 1 + level * 0.1;
  }

  /**
   * XP-Schwelle (in Spezialisierungs-Produktionsstunden – XP = tatsächlich
   * verbrauchte Produktionszeit, nicht Stückzahl) für den Aufstieg von
   * {@code level} auf {@code level + 1}. Linear wachsend, kalibriert auf EINE
   * SPIELWOCHE (nicht real): wer ein einzelnes Produkt eine Spielwoche lang
   * (168 Spielstunden) ununterbrochen exklusiv produziert, erreicht kumuliert
   * Stufe 10 = +100% Tempo. Kumulierte Schwelle bis Stufe 10 ist
   * BASE × (1+2+…+10) = BASE × 55 = 168, also BASE = 168/55 ≈ 3,055. Bis
   * Stufe 50 (+500%) kumuliert BASE × 1275 ≈ 3895 Spielstunden ≈ 23
   * Spielwochen.
   */
  public static final double SPECIALIZATION_THRESHOLD_BASE = (7 * 24) / 55.0;

  public static double specializationThresholdHours(int level) {
    return SPECIALIZATION_THRESHOLD_BASE * (level + 1);
  }

  /**
   * Ausbeutefaktor für Rohstoffe aus der Fördergüte (0-100):
   * Ausstoß = ... × (0,20 + 1,30 × Fördergüte / 100). Jedes Vorkommen bleibt
   * grundsätzlich förderbar, ein Spitzenvorkommen (100) liefert aber mehr als
   * das Siebenfache eines Spurenvorkommens (1).
   */
  public static double resourceConcentrationFactor(double foerdergute) {
    return 0.2 + 1.3 * clamp(foerdergute, 0, 100) / 100;
  }

  /** Vier Planetenwerte, siehe Konzeption/07_..., §5 und Umsetzungskonzept/08_..., §5. */
  public static double infrastructurePct(double builtCapacity, double population) {
    if (population <= 0) return 100;
    return clamp((builtCapacity / population) * 100, 0, 400);
  }

  /**
   * Garnisonsbasierte Sicherheit plus ein loyalitätsproportionaler Sockel
   * (Mechanik/11_..., §3/§8: "hohe Loyalität → weniger notwendige Truppen").
   * Der Sockel ersetzt keine Garnison vollständig (max. 30% bei 100% Loyalität).
   */
  public static double securityPct(double garrisonStrength, double population, double loyaltyPct) {
    double reference = Math.max(population * 0.05, 5);
    double garrisonSecurity = (garrisonStrength / reference) * 100;
    double loyaltyFloor = loyaltyPct * 0.3;
    return clamp(Math.max(garrisonSecurity, loyaltyFloor), 0, 400);
  }

  public static double loyaltyDelta(boolean isHomeworld, double standardOfLivingPct, double securityPct) {
    double base = isHomeworld ? 0.35 : 0.12;
    double condition = 0;
    if (standardOfLivingPct < 60) condition -= 0.2;
    else if (standardOfLivingPct > 100) condition += 0.05;
    if (securityPct < 40) condition -= 0.15;
    return base + condition;
  }

  /**
   * Kombinierter "Zufriedenheits"-Faktor aus Lebensstandard und Sicherheit,
   * der die Wachstumsgeschwindigkeit relativ zur Referenzgeschwindigkeit
   * (1,0 = 100%) skaliert. Eigene Methode statt Inline-Berechnung in
   * {@link #populationGrowthDelta}, damit die UI exakt denselben Wert
   * anzeigen kann, der auch das Wachstum steuert.
   */
  public static double growthConditionFactor(double standardOfLivingPct, double securityPct) {
    double livingFactor = clamp(standardOfLivingPct / 100, 0.15, 1.6);
    double securityFactor = clamp(securityPct / 100, 0.5, 1.2);
    return livingFactor * securityFactor;
  }

  public static double populationGrowthDelta(double population, double capacity, double standardOfLivingPct, double securityPct) {
    double room = capacity - population;
    if (room <= 0) return population * -0.002; // leichte Schrumpfung bei Überbevölkerung
    return room * 0.018 * growthConditionFactor(standardOfLivingPct, securityPct);
  }

  public static final double CREDITS_PER_NEW_INHABITANT = 8;

  /** Ab welchem {@code coverageRatio} eine Kolonie als "im Blackout" gilt (Konzeption/Spieldesign/06_..., "Energieversorgung"). */
  public static final double BLACKOUT_THRESHOLD = 0.999;

  /** Produktionstempo im Blackout: nur noch 10% Geschwindigkeit (siehe {@code ChainPlanner.computeProductionHours}). */
  public static final double BLACKOUT_PRODUCTION_FACTOR = 0.1;

  /** Sicherheit/Lebensstandard im Blackout: halbiert (siehe künftiges {@code recalcCoreStats}). */
  public static final double BLACKOUT_STAT_FACTOR = 0.5;

  /**
   * Produktionsaufwand als militärischer Basiswert (Mechanik/04_..., §2):
   * "Kein zusätzlicher abstrakter Kampfkraftwert" — Schaden UND Haltbarkeit
   * einer Einheit im Kampf leiten sich ausschließlich hieraus ab.
   */
  public static double productionAspect(double baseWorkforceRequired, double baseProductionHours) {
    return baseWorkforceRequired * baseProductionHours;
  }

  /**
   * Globale Kampf-Faktoren (Mechanik/04_..., §2-3): Gruppenschaden = Anzahl ×
   * Produktionsaufwand je Einheit × {@link #COMBAT_DAMAGE_FACTOR}, Haltbarkeit
   * = Produktionsaufwand je Einheit × {@link #COMBAT_DURABILITY_FACTOR}.
   * Verhältnis 0,2:1 erfüllt die Vorgabe "bei neutralem Konter (×1) verliert
   * eine Seite gegen eine gleich starke Seite pro Tick rund 20% ihres Werts".
   */
  public static final double COMBAT_DAMAGE_FACTOR = 0.2;
  public static final double COMBAT_DURABILITY_FACTOR = 1;

  /** Dauer eines Kampf-Ticks in Spielstunden (Mechanik/04_..., §6). */
  public static final int COMBAT_TICK_HOURS = 8;

  /**
   * Konter-Multiplikator (Mechanik/03_..., §2 und 04_..., §4): ×2 im Vorteil,
   * ×0,5 im Nachteil, sonst ×1. Der Aufrufer prüft beide Richtungen separat
   * (siehe GameEngine.resolveBattleTick, sobald portiert).
   */
  public static double counterMultiplier(boolean attackerCountersDefenderClass, boolean defenderCountersAttackerClass) {
    if (attackerCountersDefenderClass) return 2;
    if (defenderCountersAttackerClass) return 0.5;
    return 1;
  }
}
