package de.nebula.model;

/**
 * Betriebszustand der Infrastruktur (Umsetzungskonzept/17_...md, Teil A): sie
 * verbraucht Stabilisiertes Elerium aus Speicher und Lager, verbucht einmal je
 * Spieltag im Kolonietag ({@code Economy.consumePower}). Reicht der Bestand
 * nicht, sinkt {@code coverageRatio} unter 1 (Blackout); die offene Menge
 * bleibt als {@code shortfall} stehen und wird beim nächsten Elerium-Zugang
 * sofort nachgeholt ({@code PowerGrid.settleShortfall}) – die Versorgung
 * kehrt also mit dem Nachschub zurück, nicht erst am nächsten Tag.
 */
public class ColonyPowerState {
  public String colonyId;
  public double coverageRatio;
  /** Am laufenden Kolonietag fällige Zellen. */
  public double dueToday;
  /** Davon noch nicht gedeckt. */
  public double shortfall;
}
