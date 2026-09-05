package de.nebula.model;

/**
 * Siehe Mechanik/05_..., §3-4: Soldaten besitzen keine eigene Kampfwirkung,
 * sondern kommandieren/aktivieren autonome Drohnen aus der Ferne. Bei
 * {@code p_soldier}: activeCount = Soldaten, die aktuell Drohnen
 * kommandieren; reserveCount = Reserve-Soldaten ohne passende Drohne. Bei
 * einem Drohnen-Eintrag: activeCount = aktive (kampffähige) Drohnen;
 * reserveCount = Reserve-Drohnen ohne ausreichend Soldaten zur
 * Fernsteuerung. Wird nach jeder Rekrutierung/Produktion neu verteilt
 * (proportional über alle drei Drohnenklassen, siehe {@code recalcCrewing}).
 */
public class GroundForceUnitStack {
  public String unitProductTypeId;
  public int activeCount;
  public int reserveCount;
}
