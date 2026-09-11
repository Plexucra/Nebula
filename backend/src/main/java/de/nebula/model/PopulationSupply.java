package de.nebula.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Versorgungslage einer Kolonie für die Oberfläche (Umsetzungskonzept/36 und
 * 38): Vorrat je Konsumgut, Tagesbedarf, Reichweite, Deckung des letzten
 * Tages, das stehende Gebot der Bevölkerung und der günstigste kaufbare
 * Brief, dazu Arbeiter und Akademiker mit Staffel, Deckel und Budget.
 */
public class PopulationSupply {
  /** Wozu ein Gut gerade nachgefragt wird. */
  public enum Group {
    /** Pflichtgut der Arbeiter auf der aktuellen Wohnstufe – zählt in ihren Lebensstandard. */
    Essential,
    /** Wachstumsgut der nächsten Wohnstufe – wird mitgekauft, deckelt das Wachstum, zählt nicht in den Lebensstandard. */
    Growth,
    /** Nur Akademiker (oder ihr Startterm) fragen es nach. */
    Academic
  }

  public static class Good {
    public String productTypeId;
    public String name;
    public Group group;
    /** Vorrat der Bevölkerung in ganzen Stücken. */
    public double stock;
    /** Bedarf je Spieltag bei der aktuellen Bevölkerung (Arbeiter plus Akademiker). */
    public double dailyNeed;
    /** Reichweite des Vorrats in Spieltagen (0 = leer). */
    public double daysLeft;
    /** Deckung des letzten Kolonietags (0..1,5), siehe {@code Economy.consumeFromStock}; {@code null} vor dem ersten Kolonietag. */
    public Double coverage;
    /** Ob am eigenen Handelsposten gerade eine kaufbare Verkaufsorder liegt. */
    public boolean orderAvailable;
    /** Günstigster kaufbarer Brief am eigenen Posten (eigener Kommandant oder Vertragspartner); {@code null} ohne Order. */
    public Double askPrice;
    /** Limit des stehenden Gebots der Bevölkerung; {@code null}, wenn gerade keines steht (Vorrat voll oder kein Budget). */
    public Double bidPrice;
    /** Restmenge des Gebots. */
    public double bidQuantity;
  }

  public List<Good> goods = new ArrayList<>();
  /** Spielzeit des nächsten Kolonietags (Gebote erneuern, Verbrauch, Wachstum), 0 wenn keiner geplant ist. */
  public long nextPurchaseAt;
  /** Vorratsziel in Tagesbedarfen. */
  public double targetDays;

  // --- Arbeiter und Akademiker (Umsetzungskonzept/38, Teil D) ---------------
  public double workers;
  public double academics;
  /** Wohnstufe der Güterstaffel und ihre Einwohnergrenze. */
  public int consumerStage;
  public double consumerStageCap;
  /** Wachstumsgut der nächsten Stufe; {@code null} auf der letzten Stufe. */
  public String growthGoodId;
  /** true = das Wachstumsgut war am letzten Kolonietag voll gedeckt, der Wohnraum begrenzt. */
  public boolean growthGoodCovered;
  /** Bezahlte Plätze des Forschungszentrums (0 ohne Zentrum). */
  public double researchCapacity;
  /** Deckel der Akademiker: Kapazität der höchsten Zentrumsstufe, deren Güter alle gedeckt sind. */
  public double academicCap;
  public int researchLevel;
  public double academicStandardOfLivingPct;

  // --- Kaufkraft (Umsetzungskonzept/38, Teil C) -----------------------------
  /** Geglättetes Einkommen des Bevölkerungs-Wallets je Spieltag. */
  public double dailyIncome;
  /** Tagesbudget der Gebote am letzten Kolonietag. */
  public double dailyBudget;
}
