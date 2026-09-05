package de.nebula.model;

/** Bewegungsziel für {@code moveFleetWithinSystem} – siehe {@link FleetLocationType}. */
public sealed interface FleetSystemTarget {
  record System() implements FleetSystemTarget {
  }

  record PlanetOrbit(String planetId) implements FleetSystemTarget {
  }

  record ColonyOrbit(String colonyId) implements FleetSystemTarget {
  }
}
