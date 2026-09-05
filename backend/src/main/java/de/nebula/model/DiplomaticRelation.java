package de.nebula.model;

/**
 * Genau eine Beziehung je ungeordnetem Kommandanten-Paar ({@code playerAId}/
 * {@code playerBId} beim Anlegen kanonisch sortiert, siehe
 * {@code relationKey}) – ohne Eintrag gilt implizit {@code Peace}. {@code War}
 * entsteht einseitig, {@code Peace} erst nach beidseitiger Zustimmung
 * (siehe {@link PeaceOffer}).
 */
public class DiplomaticRelation {
  public String id;
  public String playerAId;
  public String playerBId;
  public DiplomaticStatus status;
  /** Zeitpunkt des letzten Statuswechsels (Kriegserklärung bzw. Friedensschluss). */
  public long since;
}
