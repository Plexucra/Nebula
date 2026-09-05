package de.nebula.model;

/**
 * Einseitiges Friedensangebot innerhalb eines laufenden Kriegs – wird erst
 * mit {@code respondToPeaceOffer(accept: true)} wirksam. Ein abgelehntes
 * oder zurückgezogenes Angebot wird gelöscht, nicht als Status gespeichert.
 */
public class PeaceOffer {
  public String id;
  public String fromPlayerId;
  public String toPlayerId;
  public long createdAt;
}
