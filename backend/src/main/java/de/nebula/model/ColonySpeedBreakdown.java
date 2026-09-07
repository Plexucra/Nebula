package de.nebula.model;

import java.util.List;
import java.util.Map;

/**
 * Fertig ausgerechnete Aufschlüsselung ALLER Faktoren, die in die
 * Produktionsgeschwindigkeit einer Kolonie eingehen – Datengrundlage der
 * Transparenz-Panels ("Produktionstempo dieser Kolonie") im Bebauungs-/
 * Produktions-Tab.
 *
 * <p>Diese Werte wurden früher im Frontend aus einer zweiten Kopie der
 * Formeln ({@code engine/formulas.ts}) nachgerechnet. Seit
 * Umsetzungskonzept/15_...md, Auftrag 3 liefert sie ausschließlich das
 * Backend: die Regel lebt damit nur noch an EINER Stelle
 * ({@code engine/Formulas.java} / {@code ChainPlanner.computeProductionHours}),
 * ohne dass die Anzeige verloren geht – der Spieler muss jeden Faktor sehen
 * können, der seine strategische Entscheidung beeinflusst.</p>
 */
public class ColonySpeedBreakdown {
  public double population;
  public double workforceFactor;
  public int industryLevel;
  /** Tempofaktor des Industriekomplexes (linear zur Stufe, siehe {@code Formulas.buildingLevelSpeedFactor}). */
  public double buildingSpeedFactor;
  public boolean blackout;
  /** Zufriedenheit = derselbe Faktor, der das Bevölkerungswachstum steuert, in Prozent. */
  public double satisfactionPct;
  /** Aktiver Zustand der Bevölkerungsentwicklung samt Schwellen (Umsetzungskonzept/17_...md, Teil C). */
  public PopulationGrowthState growthState;
  public double growthPerHour;
  public double shrinkBelowPct;
  public double growthFromPct;
  public double housingCapacity;
  /** Bebauungsplätze der Kolonie – DIE strategische Größe der Bebauung. */
  public BuildSlots buildSlots;
  /** Elerium-Bedarf der Infrastruktur je Spielstunde und aktuelle Deckung. */
  public double infrastructureEleriumPerHour;
  public double powerCoverage;
  public List<BuildingUpgradePreview> buildingUpgrades;
  /** productTypeId -> Tempobonus der Spezialisierung in Prozent (nur Produkte mit Spezialisierung in dieser Kolonie). */
  public Map<String, Double> specializationSpeedBonusPctByProduct;
  /** productTypeId -> Fördergüte-Ausbeutefaktor (nur Rohstoffe/Tier 0 mit Rohstoffprofil). */
  public Map<String, Double> concentrationFactorByProduct;

  /** Vorschau für den nächsten Ausbauschritt EINES Gebäudetyps auf dieser Kolonie. */
  public static class BuildingUpgradePreview {
    public String typeId;
    public int currentLevel;
    public double upgradeCost;
    public double upgradeHours;
    /** Produktionstempo der Anlage auf der AKTUELLEN Stufe in Prozent (Stufe 1 = 100%). */
    public double productionSpeedPct;
    /** Produktionstempo nach dem nächsten Ausbauschritt in Prozent – für die "nächste Stufe:"-Vorschau. */
    public double nextProductionSpeedPct;
    /** Baustoffe des nächsten Ausbauschritts mit Lagerabgleich – Kosten müssen VOR dem Klick sichtbar sein. */
    public List<MaterialRequirement> materials;
    /** true = dieser Ausbau belegt einen Bebauungsplatz (alle Gebäude außer Infrastruktur). */
    public boolean needsSlot;
    /** true = Credits, Baustoffe und (falls nötig) ein freier Platz bzw. planetweite Kapazität sind vorhanden. */
    public boolean affordable;
    /** Grund, falls nicht {@code affordable} – derselbe Text, den {@code queueBuilding} als Fehler liefern würde. */
    public String blockedReason;
  }
}
