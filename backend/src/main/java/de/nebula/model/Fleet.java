package de.nebula.model;

import java.util.List;

public class Fleet {
  public String id;
  public String ownerId;
  public String name;
  public FleetLocationType locationType;
  /** Nur bei {@code locationType == ColonyOrbit} gesetzt. */
  public String locationColonyId;
  /** Bei {@code ColonyOrbit} und {@code PlanetOrbit} der umkreiste Planet, bei {@code System} {@code null}. */
  public String locationPlanetId;
  /** Aktuelles bzw. (während InTransit) Ausgangssystem des GERADE LAUFENDEN Sprungs. */
  public String systemId;
  public FleetStatus status;
  public List<FleetShipGroup> ships;
  /** Geladene Fracht – begrenzt durch Summe aus ShipTypeDef.cargoMassKg/cargoVolumeM3 aller Schiffe der Flotte. */
  public List<FleetCargoEntry> cargo;
  /** Ziel des GERADE LAUFENDEN, einzelnen Gateway-Sprungs – nur während InTransit gesetzt. */
  public String destinationSystemId;
  public Long departedAt;
  public Long arrivesAt;
  /**
   * Bei einer mehrsprungigen Reise die noch folgenden Zielsysteme NACH
   * destinationSystemId, in Flugreihenfolge (letzter Eintrag = eigentliches
   * Endziel). Jeder Sprung ist ein eigenes, ereignisbasiertes Ankunfts-Tick
   * – nach Ankunft am aktuellen destinationSystemId wird automatisch der
   * nächste Eintrag als neuer Sprung gestartet, sofern nicht per
   * cancelFleetMove abgebrochen (dann bleibt die Flotte am gerade
   * erreichten System stehen). Das macht einen Flug JEDERZEIT unterwegs
   * abbrechbar. Leer, wenn der aktuelle Sprung der letzte ist.
   */
  public List<String> pendingHops;
}
