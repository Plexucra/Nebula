package de.nebula.model;

/** Ein Baustoff eines Ausbauschritts mit Bedarf und aktuellem Lagerbestand – für die Kosten-Vorschau VOR dem Klick. */
public class MaterialRequirement {
  public String productTypeId;
  public double required;
  public double available;
}
