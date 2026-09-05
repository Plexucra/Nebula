package de.nebula.model;

/**
 * Betriebszustand des Energienetzes (b_powergrid) – Umsetzungskonzept/01_...,
 * §3 "PowerUpkeepJob": verbraucht laufend Stabilisiertes Elerium aus dem
 * Kolonielager; reicht der Bestand nicht, sinkt {@code coverageRatio} unter 1
 * und mindert anteilig die vom Energienetz beigesteuerte Wohnkapazität
 * (Blackout, geglättet statt hartem Ein/Aus).
 */
public class ColonyPowerState {
  public String colonyId;
  public double coverageRatio;
}
