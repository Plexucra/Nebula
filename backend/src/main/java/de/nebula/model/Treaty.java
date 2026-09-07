package de.nebula.model;

/**
 * Ein förmlicher, beidseitig angenommener Friedens- oder Handelsvertrag
 * zwischen zwei Kommandanten (Umsetzungskonzept/21_...md) – zusätzlich zum
 * einfachen Kriegs-/Friedenszustand aus {@link DiplomaticRelation}. Höchstens
 * EIN aktiver Vertrag je {@link TreatyType} und ungeordnetem
 * Kommandanten-Paar ({@code playerAId}/{@code playerBId} kanonisch sortiert,
 * analog {@link DiplomaticRelation}).
 *
 * <p>Eine Kündigung löscht den Vertrag NICHT sofort, sondern setzt
 * {@code terminationEffectiveAt} – bis zu diesem Zeitpunkt bleibt er voll
 * gültig (siehe {@code TreatyCommands.terminateTreaty}). Wird er vorher
 * erneut angeboten und angenommen, hebt das eine laufende Kündigung wieder
 * auf.</p>
 */
public class Treaty {
  public String id;
  public String playerAId;
  public String playerBId;
  public TreatyType type;
  /** Zeitpunkt des Vertragsschlusses (bzw. der letzten erneuten Annahme). */
  public long since;
  /** {@code null} = ungekündigt. Gesetzt = Kündigungsfrist läuft, Vertrag bis dahin unverändert gültig. */
  public Long terminationEffectiveAt;
}
