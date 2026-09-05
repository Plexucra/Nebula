package de.nebula.model;

import java.util.List;
import java.util.Map;

/**
 * Ein einzelner Kampf-Tick für den Kampfbericht: {@code attackerShipsBefore}/
 * {@code defenderShipsBefore} sind die zu Tickbeginn noch kampffähigen (und
 * damit an diesem Tick TEILNEHMENDEN) Schiffe je Seite – Mechanik/04_...,
 * §1: "Alle zu Tickbeginn kampffähigen Einheiten verursachen ihren Schaden
 * auch dann noch, wenn sie im selben Tick zerstört werden." {@code Losses}
 * sind die in GENAU diesem Tick daraus resultierenden Verluste.
 */
public class BattleTickResult {
  public int tick;
  public long atTime;
  public List<FleetShipGroup> attackerShipsBefore;
  public List<FleetShipGroup> defenderShipsBefore;
  public Map<String, Integer> attackerLosses;
  public Map<String, Integer> defenderLosses;
}
