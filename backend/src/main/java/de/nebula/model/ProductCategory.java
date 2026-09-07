package de.nebula.model;

/**
 * Portierung von {@code product.model.ts}. {@code Fuel}: Elerium-115-Kette
 * (Antriebs-/Energieversorgungs-Treibstoff, Umsetzungskonzept/01_..., §3
 * "PowerUpkeepJob") – eigene Kategorie statt BuildingMaterial, da sowohl
 * einmalig verbaut als auch laufend verbraucht. Die frühere Kategorie
 * {@code Facility} (planetare Anlagen als Produkte) wurde mit
 * Umsetzungskonzept/17_...md entfernt – sie war nie an das Gebäudesystem
 * angebunden; Gebäude kosten jetzt stattdessen Baustoffe.
 */
public enum ProductCategory {
  Ship, GroundUnit, ConsumerGood, BuildingMaterial, RawResource, Fuel
}
