package de.nebula.model;

import java.util.List;

/**
 * Ein Bodentruppenverband. Er steht ENTWEDER in einer Kolonie ({@link #colonyId}
 * gesetzt) ODER an Bord einer Flotte ({@link #fleetId} gesetzt) ODER auf der
 * Oberfläche eines Planeten ({@link #planetId} gesetzt, seit
 * Umsetzungskonzept/04_...md, {@code LandingCommands.land}) – genau eines von
 * den dreien, nie mehrere, nie keines.
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
  /** Kolonie, in der der Verband steht – {@code null}, wenn er nicht dort steht. */
  public String colonyId;
  /** Flotte, an deren Bord der Verband ist – {@code null}, wenn er nicht an Bord ist. */
  public String fleetId;
  /** Planet, auf dessen Oberfläche der Verband gelandet ist – {@code null} sonst. */
  public String planetId;
  public List<GroundForceUnitStack> units;

  /**
   * Ziel eines laufenden {@code LandingCommands.moveGroundForces}-Befehls – {@code null}, solange
   * keine Verlegung läuft. Nur relevant, während {@link #planetId} gesetzt ist.
   */
  public String pendingMoveColonyId;
  /**
   * Zeitpunkt, zu dem eine laufende Verlegung abgeschlossen ist (genau ein Kampftick nach dem
   * Befehl, unabhängig von der Distanz, siehe Mechanik/05_...md §7) – {@code null} ohne laufende
   * Verlegung.
   */
  public Long moveCompletesAt;
}
