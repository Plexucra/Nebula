package de.nebula.model;

/**
 * Eine laufende Koloniegründung (Umsetzungskonzept/24_...md): das
 * Kolonisationsschiff ist bereits verbraucht, die Kolonisten sind gelandet und
 * bauen einen Spieltag lang das Startlager auf. Erst bei {@code endsAt} entsteht
 * die eigentliche {@link Colony} (siehe {@code ColonyCommands.processColonizations}).
 *
 * <p>Bewusst ein eigener Zustand statt eines Feldes an der Kolonie: vor Ablauf
 * gibt es noch KEINE Kolonie – sie taucht daher weder in Übersichten noch in
 * Berechnungen auf und kann in dieser Zeit auch nicht produzieren.</p>
 */
public class Colonization {
  public String id;
  public String planetId;
  public String systemId;
  public String ownerId;
  /** Flotte, die das Schiff gestellt hat – rein informativ für die Anzeige. */
  public String fleetId;
  public String colonyName;
  public long startedAt;
  public long endsAt;
}
