package de.nebula.model;

/**
 * Bebauungsplätze einer Kolonie (Umsetzungskonzept/17_...md, Teil A): jede
 * Infrastruktur-Stufe liefert {@code SLOTS_PER_INFRASTRUCTURE_LEVEL} Plätze,
 * jede Stufe jedes anderen Gebäudes (inklusive laufender Ausbauten) belegt
 * genau einen. DIE strategische Größe der Bebauung.
 */
public class BuildSlots {
  public int total;
  public int used;
  public int free;
  public int infrastructureLevel;
  /** Summe der Infrastruktur-Stufen ALLER Kolonien auf diesem Planeten (inkl. laufender Ausbauten). */
  public int planetInfrastructureTotal;
  /** Planetweite Obergrenze für Infrastruktur, abhängig von der Planetengröße. */
  public int planetInfrastructureMax;
}
