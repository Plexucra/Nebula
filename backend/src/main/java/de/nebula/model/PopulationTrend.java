package de.nebula.model;

import java.util.List;

/**
 * Bevölkerungsverlauf einer Kolonie samt fertiger Einordnung, in welcher
 * Wachstumsphase sie steckt und was gerade bremst (Umsetzungskonzept/18_...md).
 *
 * <p>Die Einordnung entsteht im Backend, nicht in der Oberfläche: sie ist eine
 * Aussage über die Spielregeln (Totband, Wohnraumgrenze, Versorgung) und
 * gehört damit auf dieselbe Seite wie die Regeln selbst – siehe
 * Umsetzungskonzept/15_...md, Auftrag 3.</p>
 */
public class PopulationTrend {
  /** Älteste zuerst; das Fenster ist begrenzt und "reicht nicht unendlich zurück". */
  public List<PopulationSample> samples;
  public Phase phase;
  /** Was das Wachstum aktuell begrenzt – nur gesetzt, wenn es tatsächlich bremst. */
  public LimitingFactor limitingFactor;
  /** Länge des abgedeckten Zeitfensters in Spielstunden (für die Achsenbeschriftung). */
  public double windowGameHours;

  public enum Phase {
    /** Zu wenige Messpunkte für eine belastbare Aussage. */
    TooFewSamples,
    /** Die Zuwächse werden von Messung zu Messung größer. */
    Accelerating,
    /** Gleichmäßiges Wachstum. */
    Steady,
    /** Wachstum flacht ab – die Kurve läuft auf eine Grenze zu. */
    Slowing,
    /** Kein nennenswerter Zuwachs mehr. */
    Plateau,
    /** Die Bevölkerung geht zurück. */
    Shrinking
  }

  public enum LimitingFactor {
    /** Die Versorgung mit Konsumgütern hält nicht mit. */
    Supply,
    /** Der Wohnraum ist nahezu ausgeschöpft. */
    Housing
  }
}
