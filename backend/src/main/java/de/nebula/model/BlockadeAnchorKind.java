package de.nebula.model;

/**
 * Die zwei Blockade-Anker, die eine Flotte in einem System bilden kann
 * (Mechanik/06_..., stark vereinfacht): {@code Gateway} = ausgehend vom
 * Systemhandelsposten ("Gateway blockieren"), {@code PlanetOrbit} =
 * ausgehend vom Orbit eines konkreten Planeten ("Planet blockieren"), auch
 * bei unbesiedelten Planeten möglich.
 */
public enum BlockadeAnchorKind {
  Gateway, PlanetOrbit
}
