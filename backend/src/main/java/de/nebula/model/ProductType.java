package de.nebula.model;

import java.util.List;

/** Portierung von {@code product.model.ts}. tier 0 = Rohstoff, steigt Richtung Endprodukt (Umsetzungskonzept/02_..., §1). */
public class ProductType {
  public String id;
  public String name;
  public ProductCategory category;
  public int tier;
  public List<RecipeInput> recipe;
  public List<ResourceWeight> resourceProfile;
  /** Basis-Produktionszeit in Spielstunden für 1 Einheit. */
  public double baseProductionHours;
  /** Benötigte Arbeitskraft (Bevölkerung) pro paralleler Produktionseinheit. */
  /**
   * Arbeitsstunden, die EIN Stück dieses Erzeugnisses insgesamt an
   * Arbeitskraft kostet (Umsetzungskonzept/19_...md). Zusammen mit der
   * tatsächlichen Fertigungsdauer ergibt sich daraus, wie viele Arbeitskräfte
   * die Fertigung je Stunde bindet – reicht die Bevölkerung dafür nicht,
   * dauert es entsprechend länger.
   */
  public double workHoursPerUnit;
  public double massKg;
  public double volumeM3;
  /** Eleriumbedarf pro Charge in Gramm – reiner Datenwert, aktuell NICHT als Voraussetzung geprüft (siehe TS-Original). */
  public double eleriumG;
  public String description;
}
