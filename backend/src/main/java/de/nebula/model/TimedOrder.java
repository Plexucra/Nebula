package de.nebula.model;

/**
 * Portierung von {@code common.model.ts}. Ein laufender Auftrag mit
 * Fertigstellungszeitpunkt (Order/Job-Rückgabeobjekt, Umsetzungskonzept/00_...,
 * §5). IDs sind im gesamten Modell bewusst einfache {@code String}s.
 */
public class TimedOrder {
  public String id;
  public long startedAt;
  public long completesAt;
}
