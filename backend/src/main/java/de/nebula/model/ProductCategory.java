package de.nebula.model;

/**
 * Portierung von {@code product.model.ts}. {@code EnergyModule} (bis
 * Umsetzungskonzept/20_...md {@code Fuel}): Elerium-115-Kette
 * (Antriebs-/Energieversorgungs-Treibstoff, Umsetzungskonzept/01_..., §3
 * "PowerUpkeepJob") – eigene Kategorie statt BuildingMaterial, da sowohl
 * einmalig verbaut als auch laufend verbraucht. {@code ShipModule} (seit
 * Umsetzungskonzept/20_...md aus {@code BuildingMaterial} herausgelöst): die
 * je Schiffstyp einmal produzierten Baugruppen, ausschließlich Eingang von
 * {@code Ship}-Rezepten. Die frühere Kategorie {@code Facility} (planetare
 * Anlagen als Produkte) wurde mit Umsetzungskonzept/17_...md entfernt – sie
 * war nie an das Gebäudesystem angebunden; Gebäude kosten jetzt stattdessen
 * Baustoffe.
 */
public enum ProductCategory {
  Ship, GroundUnit, ConsumerGood, BuildingMaterial, ShipModule, RawResource, EnergyModule
}
