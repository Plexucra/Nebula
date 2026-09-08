package de.nebula.model;

/**
 * Truppenkapazität einer Flotte, Gegenstück zu {@link FleetCargoCapacity} für
 * Soldaten (Umsetzungskonzept/28_...md). Die Grenze ist eine SPIELREGEL, die
 * {@code TroopTransportCommands.embarkSoldiers} durchsetzt – das Frontend
 * liest sie hier fertig ab, statt sie aus dem Schiffskatalog nachzurechnen
 * (Umsetzungskonzept/15_...md, Auftrag 3).
 */
public class FleetTroopCapacity {
  /** Plätze insgesamt: Summe von {@code ShipTypeDef.troopCapacity} über alle Schiffe der Flotte. */
  public double capacitySoldiers;
  /** Bereits eingeschiffte Soldaten. */
  public double soldiersAboard;
  /** Was jetzt noch zusteigen kann – zusätzlich begrenzt durch die Garnison der Kolonie, bei der die Flotte liegt. */
  public double maxEmbarkableQuantity;
}
