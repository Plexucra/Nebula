package de.nebula.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Versorgungslage einer Kolonie für die Oberfläche (Umsetzungskonzept/36):
 * Vorrat je Grundkonsumgut, Tagesbedarf, Reichweite und die Deckung des
 * letzten Tages, dazu der Termin des nächsten Tageseinkaufs.
 */
public class PopulationSupply {
  public static class Good {
    public String productTypeId;
    public String name;
    /** Vorrat der Bevölkerung in ganzen Stücken. */
    public double stock;
    /** Bedarf je Spieltag bei der aktuellen Bevölkerung. */
    public double dailyNeed;
    /** Reichweite des Vorrats in Spieltagen (0 = leer). */
    public double daysLeft;
    /** Deckung des letzten Kolonietags (0..1,5), siehe {@code Economy.consumeFromStock}; {@code null} vor dem ersten Kolonietag. */
    public Double coverage;
    /** Ob am eigenen Handelsposten gerade eine kaufbare Order liegt. */
    public boolean orderAvailable;
  }

  public List<Good> goods = new ArrayList<>();
  /** Spielzeit des nächsten Tageseinkaufs, 0 wenn keiner geplant ist. */
  public long nextPurchaseAt;
  /** Vorratsziel in Tagesbedarfen. */
  public double targetDays;
  /** Unter so vielen Tagen Vorrat löst eine neue Order einen Notkauf aus. */
  public double emergencyBelowDays;
}
