package de.nebula.engine;

import de.nebula.model.PopulationGrowthState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixiert {@link Formulas} gegen stille Änderungen.
 *
 * <p>Seit {@code e8b1b61} ("eine einzige Regelquelle") ist die TypeScript-Simulation
 * gelöscht – der ursprünglich in Umsetzungskonzept/13_...md als offener Punkt
 * vermerkte Parity-Test TS↔Java ist damit gegenstandslos geworden. An seine Stelle
 * tritt dieser Test: er prüft NICHT nach, was der Code gerade tut, sondern die in
 * der Konzeption AUSGESCHRIEBENEN Zahlenbeispiele und Zusicherungen. Weicht eine
 * Formel künftig davon ab, ist entweder die Änderung falsch oder die Konzeption
 * muss mitgezogen werden – beides soll auffallen, statt still zu passieren.</p>
 */
class FormulasTest {

  private static final double EPS = 1e-9;

  /**
   * Umsetzungskonzept/17_...md, Teil A – ausgeschriebene Tabelle für
   * {@code 90 × (T+1) × (1 + 0,12·T)} mit den Katalogwerten von b_infrastructure
   * (baseCostPerLevel 90, baseHoursPerLevel 0,8).
   */
  @Test
  void infrastructureUpgradeCostMatchesDocumentedTable() {
    assertEquals(335, Formulas.infrastructureUpgradeCost(90, 2), EPS);
    assertEquals(864, Formulas.infrastructureUpgradeCost(90, 5), EPS);
    assertEquals(2178, Formulas.infrastructureUpgradeCost(90, 10), EPS);
    assertEquals(6426, Formulas.infrastructureUpgradeCost(90, 20), EPS);
    assertEquals(2.4, Formulas.infrastructureUpgradeHours(0.8, 2), EPS);
  }

  /**
   * Umsetzungskonzept/17_...md, Teil A: die Infrastrukturkurve muss STEILER sein
   * als die normale Gebäudekurve ({@code 1 + 0,12·T} statt {@code 1 + 0,08·Stufe}) –
   * genau das macht dicht besiedelte Planeten für alle teurer (Mechanik/07, §2).
   */
  @Test
  void infrastructureCurveIsSteeperThanOrdinaryBuildingCurve() {
    for (int level = 1; level <= 20; level++) {
      assertTrue(Formulas.infrastructureUpgradeCost(90, level) > Formulas.buildingUpgradeCost(90, level),
          "Infrastruktur muss ab Stufe 1 teurer sein als ein gewöhnliches Gebäude, Stufe " + level);
    }
    assertEquals(Formulas.infrastructureUpgradeCost(90, 0), Formulas.buildingUpgradeCost(90, 0), EPS);
  }

  /**
   * Umsetzungskonzept/17_...md, Teil C: Kapazität = {@code 20 000 × 2^(Stufe−1)},
   * Stufe 20 ≈ 10,5 Mrd. Credits und Baustoffe verdoppeln sich mit, die BAUZEIT
   * bleibt bewusst polynomial – der Engpass soll die Investition sein, nicht das Warten.
   */
  @Test
  void housingDoublesPerLevelExceptBuildTime() {
    assertEquals(0, Formulas.housingCapacity(20_000, 0), EPS);
    assertEquals(20_000, Formulas.housingCapacity(20_000, 1), EPS);
    assertEquals(40_000, Formulas.housingCapacity(20_000, 2), EPS);
    assertEquals(10_485_760_000L, Formulas.housingCapacity(20_000, 20), EPS);

    assertEquals(2 * Formulas.housingUpgradeCost(120, 3), Formulas.housingUpgradeCost(120, 4), EPS);
    assertEquals(2 * Formulas.housingMaterialQuantity(2, 3), Formulas.housingMaterialQuantity(2, 4), EPS);
    // Bauzeit polynomial (linear in der Stufe), NICHT verdoppelnd.
    assertEquals(5, Formulas.buildingUpgradeHours(1, 4), EPS);
  }

  /** Umsetzungskonzept/17_...md, Teil B: Baustoffbedarf = {@code ceil(base × Stufe^1,3)}. */
  @Test
  void buildingMaterialQuantityFollowsDocumentedExponent() {
    assertEquals(2, Formulas.buildingMaterialQuantity(2, 1), EPS);
    assertEquals(Math.ceil(2 * Math.pow(7, 1.3)), Formulas.buildingMaterialQuantity(2, 7), EPS);
    // Die im e2e beobachteten Werte der Infrastruktur-Stufe 7 (p_stahl base 2 → 26,
    // p_leitermetall/p_leiterbuendel base 1 → 13).
    assertEquals(26, Formulas.buildingMaterialQuantity(2, 7), EPS);
    assertEquals(13, Formulas.buildingMaterialQuantity(1, 7), EPS);
  }

  /**
   * Formulas#SPECIALIZATION_THRESHOLD_BASE: kalibriert auf EINE Spielwoche –
   * 168 Spielstunden exklusive Produktion ergeben kumuliert Stufe 10 (+200 % Tempo),
   * bis Stufe 50 (+1000 %) kumuliert ≈ 3895 Spielstunden.
   */
  @Test
  void specializationIsCalibratedToOneGameWeekForLevelTen() {
    double cumulativeToTen = 0;
    for (int level = 0; level < 10; level++) cumulativeToTen += Formulas.specializationThresholdHours(level);
    assertEquals(168, cumulativeToTen, 1e-6);

    double cumulativeToFifty = 0;
    for (int level = 0; level < 50; level++) cumulativeToFifty += Formulas.specializationThresholdHours(level);
    assertEquals(3895, cumulativeToFifty, 1);

    assertEquals(3.0, Formulas.specializationSpeedFactor(10), EPS);
    assertEquals(11.0, Formulas.specializationSpeedFactor(50), EPS);
  }

  /**
   * Formulas#resourceConcentrationFactor: jedes Vorkommen bleibt förderbar, ein
   * Spitzenvorkommen (100) liefert aber MEHR ALS DAS SIEBENFACHE eines
   * Spurenvorkommens (1).
   */
  @Test
  void resourceConcentrationKeepsEveryDepositViableButRewardsRichOnes() {
    double trace = Formulas.resourceConcentrationFactor(1);
    double peak = Formulas.resourceConcentrationFactor(100);
    assertTrue(trace > 0, "Auch ein Spurenvorkommen muss förderbar bleiben");
    assertTrue(peak / trace > 7, "Spitzenvorkommen muss mehr als das Siebenfache liefern, war " + (peak / trace));
    // Clamp an beiden Enden (Fördergüte ist als 0-100 definiert).
    assertEquals(Formulas.resourceConcentrationFactor(0), Formulas.resourceConcentrationFactor(-20), EPS);
    assertEquals(peak, Formulas.resourceConcentrationFactor(500), EPS);
  }

  /**
   * Umsetzungskonzept/19_...md: Bevölkerung kann die Produktion NUR BREMSEN, nie
   * beschleunigen (die frühere workforceFactor-Regel mit Tempo-Multiplikator bis ×5
   * wurde bewusst abgeschafft).
   */
  @Test
  void workforceCanOnlySlowProductionDownNeverSpeedItUp() {
    double hoursWithBonuses = 10;
    double workHours = 100;
    // Genug Arbeitskräfte (100 h / 10 h = 10 gebunden): keine Verlängerung.
    assertEquals(hoursWithBonuses, Formulas.productionHoursWithWorkforce(hoursWithBonuses, workHours, 10), EPS);
    // Überschuss beschleunigt NICHT.
    assertEquals(hoursWithBonuses, Formulas.productionHoursWithWorkforce(hoursWithBonuses, workHours, 10_000), EPS);
    // Zu wenige Arbeitskräfte: anteilig langsamer.
    assertEquals(20, Formulas.productionHoursWithWorkforce(hoursWithBonuses, workHours, 5), EPS);
    // Keine Bevölkerung darf nicht durch Null teilen.
    assertTrue(Double.isFinite(Formulas.productionHoursWithWorkforce(hoursWithBonuses, workHours, 0)));

    assertEquals(10, Formulas.workersBoundPerHour(hoursWithBonuses, workHours), EPS);
    assertEquals(0, Formulas.workersBoundPerHour(0, workHours), EPS);
  }

  /**
   * Formulas#buildingLevelSpeedFactor: linear zur Stufe, Stufe 0 blockiert die
   * Produktion vollständig.
   */
  @Test
  void buildingLevelSpeedFactorIsLinearAndZeroBlocks() {
    assertEquals(0, Formulas.buildingLevelSpeedFactor(0), EPS);
    assertEquals(0, Formulas.buildingLevelSpeedFactor(-3), EPS);
    assertEquals(1, Formulas.buildingLevelSpeedFactor(1), EPS);
    assertEquals(10, Formulas.buildingLevelSpeedFactor(10), EPS);
  }

  /**
   * Umsetzungskonzept/19_...md: "es gibt keine absolute Sicherheit" – der Deckel
   * liegt bei 99 %, und der Loyalitätssockel ersetzt nie eine ganze Garnison
   * (max. 30 % bei 100 % Loyalität, Mechanik/11_..., §3/§8).
   */
  @Test
  void securityIsCappedBelowAbsoluteAndLoyaltyOnlyProvidesAFloor() {
    assertEquals(99, Formulas.MAX_SECURITY_PCT, EPS);
    assertEquals(99, Formulas.securityPct(1_000_000, 120, 100), EPS);
    // Ohne jede Garnison trägt nur die Loyalität, gedeckelt bei 30 %.
    assertEquals(30, Formulas.securityPct(0, 120, 100), EPS);
    assertEquals(0, Formulas.securityPct(0, 120, 0), EPS);
    // Referenzwert hat einen Sockel von 5, damit Kleinstkolonien nicht durch Null teilen.
    assertEquals(Formulas.securityPct(5, 1, 0), Formulas.securityPct(5, 100, 0), EPS);
  }

  /**
   * Umsetzungskonzept/17_...md, Teil C: Totband zwischen 30 % und 50 %
   * Lebensstandard – darunter schrumpft die Bevölkerung, darin hält sie,
   * darüber wächst sie; oberhalb der Wohnkapazität schrumpft sie ebenfalls.
   */
  /** Volle Nahrungsdeckung – der Normalfall, in dem der Nahrungsdeckel nichts tut. */
  private static final double GEDECKT = 1.0;

  @Test
  void populationDeadBandHoldsBetweenThirtyAndFiftyPercent() {
    double capacity = 20_000;
    double population = 120;

    assertEquals(PopulationGrowthState.Shrinking, Formulas.populationGrowthState(population, capacity, 29, GEDECKT));
    assertEquals(PopulationGrowthState.Holding, Formulas.populationGrowthState(population, capacity, 30, GEDECKT));
    assertEquals(PopulationGrowthState.Holding, Formulas.populationGrowthState(population, capacity, 49, GEDECKT));
    assertEquals(PopulationGrowthState.Growing, Formulas.populationGrowthState(population, capacity, 50, GEDECKT));
    assertEquals(PopulationGrowthState.Overcrowded, Formulas.populationGrowthState(capacity, capacity, 100, GEDECKT));

    assertEquals(0, Formulas.populationGrowthDelta(population, capacity, 40, 100, GEDECKT), EPS);
    assertTrue(Formulas.populationGrowthDelta(population, capacity, 10, 100, GEDECKT) < 0);
    assertTrue(Formulas.populationGrowthDelta(population, capacity, 100, 100, GEDECKT) > 0);
    assertTrue(Formulas.populationGrowthDelta(capacity + 100, capacity, 100, 100, GEDECKT) < 0,
        "Oberhalb der Wohnkapazität muss der logistische Klammerterm von selbst negativ werden");
  }

  /**
   * Umsetzungskonzept/34_...md, §J 5: eine Kolonie wächst nicht über das hinaus,
   * was sie ernähren kann – und zwar SOFORT, nicht erst wenn der geglättete
   * Lebensstandard nachgezogen hat. Bester Lebensstandard, freier Wohnraum,
   * volle Sicherheit: solange die Nahrungsdeckung unter 100 % liegt, kommt
   * niemand hinzu. Die Bevölkerung stirbt davon aber auch nicht – das
   * Schrumpfen bleibt Sache des Lebensstandards.
   */
  @Test
  void growthStopsWhileFoodIsShortEvenAtFullLivingStandard() {
    double capacity = 20_000;
    double population = 6_000;
    double knapp = 0.99;

    assertEquals(PopulationGrowthState.FoodLimited,
        Formulas.populationGrowthState(population, capacity, 100, knapp));
    assertEquals(0, Formulas.populationGrowthDelta(population, capacity, 100, 100, knapp), EPS,
        "Ohne volle Nahrungsdeckung darf die Kolonie nicht wachsen");
    assertTrue(Formulas.populationGrowthDelta(population, capacity, 100, 100, GEDECKT) > 0,
        "Mit voller Deckung wächst dieselbe Kolonie wieder");

    // Echter Mangel schrumpft weiterhin über den Lebensstandard, nicht über den Deckel.
    assertTrue(Formulas.populationGrowthDelta(population, capacity, 10, 100, knapp) < 0);
    assertEquals(PopulationGrowthState.Shrinking, Formulas.populationGrowthState(population, capacity, 10, knapp));
  }

  /**
   * Umsetzungskonzept/17_...md, Teil C: die Umstellung auf LOGISTISCHES Wachstum
   * war nötig, weil die frühere Formel (proportional zum FREIEN Wohnraum) eine
   * 120-Einwohner-Kolonie mit Kapazität 20 000 um ~358 Einwohner je Spielstunde
   * hätte wachsen lassen. Das Wachstum muss daher proportional zur BEVÖLKERUNG
   * sein und deutlich unter diesem Wert liegen.
   */
  @Test
  void populationGrowthIsLogisticNotProportionalToFreeHousing() {
    double capacity = 20_000;
    double delta = Formulas.populationGrowthDelta(120, capacity, 100, 100, GEDECKT);
    assertTrue(delta < 2, "120 Einwohner dürfen nicht sprunghaft wachsen, war " + delta);
    // Proportional zur Bevölkerung: die zehnfache Bevölkerung wächst (fern der
    // Kapazitätsgrenze) annähernd zehnmal so schnell.
    double deltaTenfold = Formulas.populationGrowthDelta(1_200, capacity, 100, 100, GEDECKT);
    assertEquals(10, deltaTenfold / delta, 0.6);
  }

  /**
   * Mechanik/04_..., §2-3: Schaden und Haltbarkeit leiten sich AUSSCHLIESSLICH aus
   * dem Produktionsaufwand ab, und das Verhältnis 0,2:1 bedeutet, dass eine Seite
   * gegen eine gleich starke Seite pro Tick rund 20 % ihres Werts verliert.
   */
  @Test
  void combatFactorsCostTwentyPercentOfValuePerTickAtNeutralCounter() {
    double productionAspect = Formulas.productionAspect(4500, 360);
    assertEquals(4500 * 360, productionAspect, EPS);

    int quantity = 100;
    double sideValue = quantity * productionAspect;
    double damage = sideValue * Formulas.COMBAT_DAMAGE_FACTOR * Formulas.counterMultiplier(false, false);
    double durability = productionAspect * Formulas.COMBAT_DURABILITY_FACTOR;
    assertEquals(20, Math.floor(damage / durability), EPS, "Neutraler Konter: 20 % Verluste je Tick");
  }

  /** Mechanik/03_..., §2 und 04_..., §4: ×2 im Vorteil, ×0,5 im Nachteil, sonst ×1. */
  @Test
  void counterMultiplierIsAsymmetricAndAdvantageWins() {
    assertEquals(2, Formulas.counterMultiplier(true, false), EPS);
    assertEquals(0.5, Formulas.counterMultiplier(false, true), EPS);
    assertEquals(1, Formulas.counterMultiplier(false, false), EPS);
    // Kontern sich beide gegenseitig, gewinnt der Vorteil des Angreifers.
    assertEquals(2, Formulas.counterMultiplier(true, true), EPS);
  }

  /** Umsetzungskonzept/17_...md, Teil A: Elerium-Verbrauch {@code 0,005 × Stufe^1,25}, leicht überlinear. */
  @Test
  void infrastructureEleriumUpkeepIsSlightlySuperlinear() {
    assertEquals(0, Formulas.infrastructureEleriumPerHour(0), EPS);
    assertEquals(0.005, Formulas.infrastructureEleriumPerHour(1), EPS);
    assertEquals(0.005 * Math.pow(2, 1.25), Formulas.infrastructureEleriumPerHour(2), EPS);
    assertTrue(Formulas.infrastructureEleriumPerHour(2) > 2 * Formulas.infrastructureEleriumPerHour(1),
        "überlinear: Stufe 2 muss mehr als das Doppelte von Stufe 1 verbrauchen");
  }

  /** Konzeption/07_..., §5: Planetenwerte sind Prozentwerte mit definierten Grenzen. */
  @Test
  void planetValuesStayWithinTheirDefinedBounds() {
    assertEquals(100, Formulas.infrastructurePct(0, 0), EPS, "Ohne Bevölkerung gilt die Infrastruktur als ausreichend");
    assertEquals(100, Formulas.infrastructurePct(120, 120), EPS);
    assertEquals(400, Formulas.infrastructurePct(1_000_000, 120), EPS);
    assertEquals(0, Formulas.infrastructurePct(0, 120), EPS);
  }

  /**
   * Formulas#loyaltyDelta: Heimatwelt wächst schneller als Kolonien, schlechte
   * Versorgung und schlechte Sicherheit drücken beide.
   */
  @Test
  void loyaltyRewardsHomeworldAndPunishesPoorConditions() {
    assertTrue(Formulas.loyaltyDelta(true, 100, 100) > Formulas.loyaltyDelta(false, 100, 100));
    assertTrue(Formulas.loyaltyDelta(false, 50, 100) < Formulas.loyaltyDelta(false, 100, 100),
        "Lebensstandard unter 60 % muss die Loyalität drücken");
    assertTrue(Formulas.loyaltyDelta(false, 100, 30) < Formulas.loyaltyDelta(false, 100, 100),
        "Sicherheit unter 40 % muss die Loyalität drücken");
    assertTrue(Formulas.loyaltyDelta(false, 50, 30) < 0, "Beides schlecht: Loyalität muss sinken");
  }

  /** Formulas#growthConditionFactor: beidseitig geklammert, damit Extremwerte das Wachstum nicht entgleisen lassen. */
  @Test
  void growthConditionFactorIsClampedOnBothSides() {
    assertEquals(0.15 * 0.5, Formulas.growthConditionFactor(0, 0), EPS);
    assertEquals(1.6 * 1.2, Formulas.growthConditionFactor(10_000, 10_000), EPS);
    assertEquals(1.0, Formulas.growthConditionFactor(100, 100), EPS);
  }

  /** Formulas#clamp – Basis aller obigen Grenzen. */
  @Test
  void clampRespectsBothBounds() {
    assertEquals(5, Formulas.clamp(-1, 5, 10), EPS);
    assertEquals(10, Formulas.clamp(99, 5, 10), EPS);
    assertEquals(7, Formulas.clamp(7, 5, 10), EPS);
  }

  /** Blackout-Kennwerte (Konzeption/Spieldesign/06_...): Bremse, kein Totalausfall. */
  @Test
  void blackoutSlowsButDoesNotStopTheColony() {
    assertTrue(Formulas.BLACKOUT_PRODUCTION_FACTOR > 0 && Formulas.BLACKOUT_PRODUCTION_FACTOR < 1);
    assertTrue(Formulas.BLACKOUT_STAT_FACTOR > 0 && Formulas.BLACKOUT_STAT_FACTOR < 1);
    assertFalse(Formulas.BLACKOUT_THRESHOLD >= 1, "Der Blackout muss knapp VOR vollständiger Unterdeckung greifen");
  }
}
