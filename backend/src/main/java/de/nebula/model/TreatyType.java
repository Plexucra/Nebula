package de.nebula.model;

/**
 * Art eines {@link Treaty} bzw. {@link TreatyOffer} – siehe
 * Umsetzungskonzept/21_...md. {@code Peace} blockiert einseitige
 * Kriegserklärungen zwischen den Parteien, {@code Trade} erlaubt planetaren
 * Handel zwischen ihnen. Unabhängig voneinander abschließbar.
 */
public enum TreatyType {
  Peace, Trade
}
