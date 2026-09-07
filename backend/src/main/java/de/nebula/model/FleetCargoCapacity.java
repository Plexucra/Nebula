package de.nebula.model;

/**
 * Frachtkapazität, Auslastung und maximal ladbare Stückzahl einer Flotte –
 * berechnet vom Backend, damit die Oberfläche die Kapazitätsregel aus
 * {@code FleetCommands.loadCargo} nicht ein zweites Mal nachbildet
 * (Umsetzungskonzept/15_...md, Auftrag 3).
 */
public class FleetCargoCapacity {
  public double capacityMassKg;
  public double capacityVolumeM3;
  public double usedMassKg;
  public double usedVolumeM3;
  /** Maximal ladbare Stückzahl des angefragten Produkts (0, wenn kein Produkt angefragt oder nicht gelandet). */
  public double maxLoadableQuantity;
}
