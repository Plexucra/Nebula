package de.nebula.model;

/**
 * Einseitiges Angebot für einen neuen Friedens- oder Handelsvertrag – wird
 * erst mit {@code respondToTreatyOffer(accept: true)} wirksam (siehe
 * {@link Treaty}). Ein abgelehntes oder zurückgezogenes Angebot wird
 * gelöscht, nicht als Status gespeichert (analog {@link PeaceOffer}).
 */
public class TreatyOffer {
  public String id;
  public String fromPlayerId;
  public String toPlayerId;
  public TreatyType type;
  public long createdAt;
}
