package de.nebula.model;

import java.util.List;

public class Planet {
  public String id;
  public String systemId;
  public String name;
  public PlanetSize size;
  public PlanetType type;
  public List<PlanetResourceConcentration> resourceConcentration;
  /** Bahn-Index im System, rein fürs Layout der Systemkarte. */
  public int orbitIndex;
}
