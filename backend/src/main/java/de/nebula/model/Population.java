package de.nebula.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Population {
  public String colonyId;
  /** GESAMTE Bevölkerung – Arbeiter plus Akademiker (Umsetzungskonzept/38_...md, Teil D). */
  public double currentCount;
  /**
   * Akademiker: kein Teil der Arbeitskraft, mit eigenem Bedarf und eigenem
   * Lebensstandard, nur in bezahlten Plätzen eines Forschungszentrums. Arbeiter
   * = {@code currentCount − academics}.
   */
  public double academics;
  /** Geglättete Wachstumsrate in Einwohnern JE SPIELSTUNDE, gesetzt vom Kolonietag ({@code Economy.colonyDay}). */
  public double growthRatePerInterval;
  /**
   * Vorrat der Bevölkerung je Konsumgut in GANZEN Stücken
   * (Umsetzungskonzept/36): getrennt vom Kolonielager, damit der Kommandant
   * nicht versehentlich das Essen seiner Bevölkerung verkauft. Gefüllt von
   * den Kauforders der Bevölkerung (Umsetzungskonzept/38), geleert vom
   * täglichen Verbrauch.
   */
  public Map<String, Double> stock = new LinkedHashMap<>();

  /** Arbeiter – die Arbeitskraft der Kolonie. */
  public double workers() {
    return Math.max(0, currentCount - academics);
  }
}
