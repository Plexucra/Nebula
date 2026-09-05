package de.nebula.model;

/**
 * Portierung von {@code product.model.ts}. {@code Fuel}: Elerium-115-Kette
 * (Antriebs-/Energieversorgungs-Treibstoff, Umsetzungskonzept/01_..., §3
 * "PowerUpkeepJob") – eigene Kategorie statt BuildingMaterial, da sowohl
 * einmalig verbaut als auch laufend verbraucht. {@code Facility}: planetare
 * Anlagen (Ebene 7 im Produktionsbaum) – Katalogeintrag vorhanden, aber noch
 * nicht mit dem Gebäude-Ausbausystem verknüpft.
 */
public enum ProductCategory {
  Ship, GroundUnit, ConsumerGood, BuildingMaterial, RawResource, Fuel, Facility
}
