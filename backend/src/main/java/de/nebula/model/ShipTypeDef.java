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
   * Slots, die dieses Schiff selbst AUFNIMMT – Gegenstück zu
   * {@link #carrierSlotUsage}. Nur das Trägerschiff hat einen Wert &gt; 0.
   *
   * <p>Ein Slot entspricht genau der Masse einer Korvette (42 000 t): jede
   * {@code carrierSlotUsage} im Katalog ist exakt {@code massKg / 42 000 t},
   * das Slotsystem ist also ein Massensystem. Die 400 Slots des Trägers sind
   * deshalb keine freie Zahl, sondern die Zusicherung "nimmt vier Kreuzer auf"
   * (4 × 100 Slots = 16,8 Mio. t Zuladung) – der Träger wiegt mit 12,6 Mio. t
   * selbst drei Kreuzer.</p>
   */
  public double carrierSlotCapacity;
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
  /**
   * Eleriumkapseln, die dieses Schiff für EINEN Gateway-Sprung verbraucht –
   * abgeleitet aus seiner Masse ({@code massKg / Korvettenmasse ×
   * GameConstants.JUMP_FUEL_PER_CORVETTE_MASS_PER_HOP}, siehe
   * {@code ShipCatalog}), nicht im Katalog gepflegt. Steht hier statt als
   * Formel im Client, damit Verbrauchsanzeige und Abrechnung dieselbe Regel
   * benutzen (Umsetzungskonzept/15_...md, "EINE Regelquelle").
   */
  public double jumpFuelPerHop;
  /**
   * Fassungsvermögen des Treibstofftanks dieses Schiffs in Kapseln:
   * {@link #jumpFuelPerHop} × {@code GameConstants.JUMP_FUEL_TANK_RANGE_HOPS}.
   * Die Reichweite ist damit für jedes Schiff gleich.
   */
  public double fuelTankCapacity;
}
