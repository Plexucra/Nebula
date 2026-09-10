package de.nebula.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Population {
  public String colonyId;
  public double currentCount;
  /** Geglättete Wachstumsrate in Einwohnern JE SPIELSTUNDE, gesetzt vom Tageseinkauf ({@code Economy.colonyDay}). */
  public double growthRatePerInterval;
  /**
   * Vorrat der Bevölkerung je Grundkonsumgut in GANZEN Stücken
   * (Umsetzungskonzept/36): getrennt vom Kolonielager, damit der Kommandant
   * nicht versehentlich das Essen seiner Bevölkerung verkauft. Gefüllt vom
   * Tageseinkauf, geleert vom täglichen Verbrauch.
   */
  public Map<String, Double> stock = new LinkedHashMap<>();
}
