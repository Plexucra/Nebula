package de.nebula.model;

/** Anzeige-Fassung des {@link EnergyStorage} samt der Größen, die der Client sonst nachrechnen müsste. */
public class EnergyStorageView {
  public String colonyId;
  public double stored;
  /** Wirksame Vorhaltemenge (konfiguriert oder automatisch). */
  public double reserveTarget;
  /** true = keine eigene Konfiguration, die Vorhaltemenge folgt der Infrastrukturstufe. */
  public boolean automatic;
  /** Was "automatisch" bei der aktuellen Infrastrukturstufe bedeuten würde. */
  public double defaultTarget;
  public double upkeepPerHour;
  /** Reichweite des Speichers allein, in Spielstunden ({@code null} ohne Verbrauch). */
  public Double storedCoverageGameHours;
  /** Stabilisiertes Elerium im normalen Lager – das, was Produktionsketten sehen. */
  public double warehouseStock;
}
