package de.nebula.model;

/**
 * Der Energiespeicher einer Kolonie (Umsetzungskonzept/32_...md): kommt mit der
 * Infrastruktur, ohne eigenes Gebäude. Stabilisiertes Elerium, das die Kolonie
 * erreicht (Produktion, Entladung, Zukauf), fließt zuerst hierher, bis die
 * Vorhaltemenge erreicht ist; erst der Rest landet im Lager. Nur die
 * Infrastruktur zieht aus dem Speicher – Produktionsketten sehen ihn nicht.
 */
public class EnergyStorage {
  public String colonyId;
  /** Aktuell vorgehaltenes Stabilisiertes Elerium. */
  public double stored;
  /**
   * Konfigurierte Vorhaltemenge; {@code null} = automatisch (Verbrauch der
   * Infrastruktur über {@code GameConstants.ENERGY_RESERVE_DEFAULT_GAME_HOURS},
   * wächst also mit jeder Infrastrukturstufe mit).
   */
  public Double reserveTarget;
}
