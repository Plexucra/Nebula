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
  /** Kosten für den Ausbau von {@code fromLevel} auf {@code fromLevel + 1}. */
  public static double buildingUpgradeCost(double baseCostPerLevel, int fromLevel) {
    return Math.round(baseCostPerLevel * (fromLevel + 1) * (1 + fromLevel * 0.08));
  }

  public static double buildingUpgradeHours(double baseHoursPerLevel, int fromLevel) {
    return baseHoursPerLevel * (fromLevel + 1);
  }

  /**
   * Kosten der nächsten Infrastruktur-Stufe (Umsetzungskonzept/17_...md, Teil A):
   * {@code T} = Summe der Infrastruktur-Stufen ALLER Kolonien des Planeten –
   * ein dicht besiedelter Planet wird für alle teurer (Mechanik/07 §2,
   * "planetweiter Malus, Summe aller Spieler"). Deutlich steiler als die
   * normale Gebäudekurve ({@code 1 + 0,12·T} statt {@code 1 + 0,08·Stufe}).
   */
  public static double infrastructureUpgradeCost(double baseCostPerLevel, int planetTotal) {
    return Math.round(baseCostPerLevel * (planetTotal + 1) * (1 + planetTotal * GameConstants.INFRASTRUCTURE_COST_GROWTH_PER_LEVEL));
  }

  public static double infrastructureUpgradeHours(double baseHoursPerLevel, int planetTotal) {
    return baseHoursPerLevel * (planetTotal + 1);
  }

  /** Baustoffbedarf eines Ausbaus auf {@code targetLevel}: {@code ceil(base × Stufe^1,3)} (Umsetzungskonzept/17_...md, Teil B). */
  public static double buildingMaterialQuantity(double baseQuantity, int targetLevel) {
    return Math.ceil(baseQuantity * Math.pow(targetLevel, GameConstants.BUILDING_MATERIAL_LEVEL_EXPONENT));
  }

  /**
   * Wohnkomplex (Umsetzungskonzept/17_...md, Teil C): Kapazität = {@code 20 000 × 2^(Stufe−1)}
   * (Stufe 20 ≈ 10,5 Mrd.). Credits und Baustoffe verdoppeln sich deshalb ebenfalls je Stufe –
   * eine polynomiale Kurve wäre gegenüber der verdoppelten Kapazität faktisch gratis. Die
   * BAUZEIT bleibt bewusst polynomial ({@link #buildingUpgradeHours}): der Engpass soll die
   * Investition sein, nicht das Warten.
   */
  public static double housingCapacity(double capacityPerLevel, int level) {
    if (level <= 0) return 0;
    return capacityPerLevel * Math.pow(GameConstants.HOUSING_GROWTH_FACTOR, level - 1);
  }

  public static double housingUpgradeCost(double baseCostPerLevel, int fromLevel) {
    return Math.round(baseCostPerLevel * Math.pow(GameConstants.HOUSING_GROWTH_FACTOR, fromLevel));
  }

  public static double housingMaterialQuantity(double baseQuantity, int targetLevel) {
    return Math.ceil(baseQuantity * Math.pow(GameConstants.HOUSING_GROWTH_FACTOR, targetLevel - 1));
  }

  /** Elerium-Verbrauch der Infrastruktur je Spielstunde: {@code BASE × Stufe^1,25}, leicht überlinear (Umsetzungskonzept/17_...md, Teil A). */
  public static double infrastructureEleriumPerHour(int level) {
    if (level <= 0) return 0;
    return GameConstants.ELERIUM_UPKEEP_BASE_PER_HOUR * Math.pow(level, GameConstants.ELERIUM_UPKEEP_LEVEL_EXPONENT);
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

  /** Basis-Wachstumsrate des logistischen Wachstums, siehe {@link #populationGrowthDelta}. */
  public static final double POPULATION_BASE_GROWTH_RATE_PER_HOUR = SharedConstants.populationBaseGrowthRatePerHour();

  /** Schwellen des Lebensstandard-Totbands, siehe {@link #populationGrowthState} – Werte aus {@code shared/game-constants.json}. */
  public static final double LIVING_STANDARD_SHRINK_BELOW_PCT = SharedConstants.livingStandardShrinkBelowPct();
  public static final double LIVING_STANDARD_GROWTH_FROM_PCT = SharedConstants.livingStandardGrowthFromPct();
  public static final double POPULATION_SHRINK_RATE_PER_HOUR = SharedConstants.populationShrinkRatePerHour();

  /**
   * Zustand der Bevölkerungsentwicklung (Umsetzungskonzept/17_...md, Teil C):
   * Überbevölkerung schrumpft leicht; Lebensstandard unter
   * {@link #LIVING_STANDARD_SHRINK_BELOW_PCT} schrumpft proportional zum
   * Fehlbetrag; dazwischen (Totband bis {@link #LIVING_STANDARD_GROWTH_FROM_PCT})
   * hält die Bevölkerung; darüber wächst sie wie bisher. Das Totband sorgt
   * dafür, dass sich die Bevölkerung bei konstanter Güterzufuhr von selbst
   * in dem Bereich einpendelt, den die Versorgung trägt, ohne zu oszillieren.
   */
  public static de.nebula.model.PopulationGrowthState populationGrowthState(double population, double capacity, double standardOfLivingPct) {
    if (population >= capacity) return de.nebula.model.PopulationGrowthState.Overcrowded;
    if (standardOfLivingPct < LIVING_STANDARD_SHRINK_BELOW_PCT) return de.nebula.model.PopulationGrowthState.Shrinking;
    if (standardOfLivingPct < LIVING_STANDARD_GROWTH_FROM_PCT) return de.nebula.model.PopulationGrowthState.Holding;
    return de.nebula.model.PopulationGrowthState.Growing;
  }

  /**
   * Bevölkerungsänderung je Spielstunde – LOGISTISCH: proportional zur
   * Bevölkerung selbst und gedämpft nahe der Wohnkapazität
   * ({@code BASE × Bev × (1 − Bev/Kapazität) × Zustandsfaktor}), mit dem
   * Lebensstandard-Totband darüber (siehe {@link #populationGrowthState}).
   *
   * <p>Umgestellt mit Umsetzungskonzept/17_...md, Teil C: die frühere Formel
   * war proportional zum FREIEN Wohnraum ({@code Rest × 0,018}) – mit einer
   * Wohnkapazität von 20 000 hätte eine 120-Einwohner-Kolonie damit ~358
   * Einwohner je Spielstunde zugelegt. Logistisch ist der Wohnraum wieder
   * eine echte Obergrenze statt eines Wachstumstreibers; der begrenzende
   * Faktor im Frühspiel ist damit die Nahrungsversorgung (über den
   * Lebensstandard im Totband). Überbevölkerung braucht keinen Sonderfall
   * mehr – oberhalb der Kapazität wird der Klammerterm von selbst negativ.</p>
   */
  public static double populationGrowthDelta(double population, double capacity, double standardOfLivingPct, double securityPct) {
    double logistic = POPULATION_BASE_GROWTH_RATE_PER_HOUR * population
        * (capacity > 0 ? 1 - population / capacity : -1) * growthConditionFactor(standardOfLivingPct, securityPct);
    return switch (populationGrowthState(population, capacity, standardOfLivingPct)) {
      case Overcrowded -> logistic; // Klammerterm ist hier negativ
      case Shrinking -> -population * POPULATION_SHRINK_RATE_PER_HOUR
          * (LIVING_STANDARD_SHRINK_BELOW_PCT - standardOfLivingPct) / LIVING_STANDARD_SHRINK_BELOW_PCT;
      case Holding -> 0;
      case Growing -> logistic;
    };
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
