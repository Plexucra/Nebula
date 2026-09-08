package de.nebula.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShipTypeDef {
  /** entspricht einem ProductType.id mit category=Ship */
  public String productTypeId;
  @JsonProperty("class")
  public ShipClass shipClass;
  /** Frachtkapazität in kg – Fracht darf weder diese noch cargoVolumeM3 überschreiten (Summe über alle Schiffe der Flotte). */
  public double cargoMassKg;
  public double cargoVolumeM3;
  public double carrierSlotUsage;
  /**
   * Soldaten, die dieses Schiff aufnimmt (Umsetzungskonzept/28_...md). Nur der
   * Mannschaftstransporter hat einen Wert &gt; 0; er nimmt AUSSCHLIESSLICH
   * Soldaten auf, keine Drohnen und keine Waren. Drohnen sind Maschinen und
   * reisen als gewöhnliche Fracht im Frachter ({@code cargoMassKg} /
   * {@code cargoVolumeM3}) – die Anforderungen an Lebenserhaltung und an
   * Laderaum sind zu verschieden, um sie in einem Schiff zu mischen. Eine
   * Landung braucht deshalb beides: Transporter für die Soldaten, Frachter für
   * ihre Drohnen.
   */
  public double troopCapacity;
  /**
   * Klasse, die von dieser Klasse gekontert wird (×2 Schaden), siehe
   * Mechanik/03_..., §2 und Mechanik/04_..., §4. Keine eigenen
   * Angriffs-/Hüllenwerte: Schaden und Haltbarkeit leiten sich
   * ausschließlich aus dem Produktionsaufwand
   * (workHoursPerUnit × baseProductionHours) ab.
   */
  public ShipClass countersClass;
}
