package de.nebula.model;

import java.util.List;

/**
 * Ein Bodentruppenverband. Er steht ENTWEDER in einer Kolonie ({@link #colonyId}
 * gesetzt, {@link #fleetId} null) ODER an Bord einer Flotte ({@link #fleetId}
 * gesetzt, {@link #colonyId} null) – nie beides, nie keines von beidem.
 *
 * <p>An Bord befinden sich ausschließlich SOLDATEN
 * (Umsetzungskonzept/28_...md): Drohnen sind Maschinen und reisen als
 * gewöhnliche Fracht im Frachter, sie liegen dafür im Warenlager der Kolonie
 * statt im Verband. Nur der Mannschaftstransporter hat mit
 * {@code ShipTypeDef.troopCapacity} überhaupt Platz für Soldaten.</p>
 */
public class GroundForceGroup {
  public String id;
  public String ownerId;
  /** Kolonie, in der der Verband steht – {@code null}, wenn er an Bord ist. */
  public String colonyId;
  /** Flotte, an deren Bord der Verband ist – {@code null}, wenn er in einer Kolonie steht. */
  public String fleetId;
  public List<GroundForceUnitStack> units;
}
