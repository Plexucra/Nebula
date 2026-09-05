package de.nebula.model;

/**
 * Eine Blockade macht die sie bildende Flotte angreifbar – OHNE aktive
 * Blockade ist {@code engageBattle} gesperrt. Höchstens EINE Blockade je
 * Anker (je System für {@code Gateway}, je Planet für {@code PlanetOrbit})
 * und höchstens eine je Flotte – erzwungen in {@code formBlockade}. Wird
 * automatisch aufgehoben, wenn die blockierende Flotte den Ort verlässt
 * oder im Kampf vollständig vernichtet wird.
 */
public class Blockade {
  public String id;
  public String systemId;
  public BlockadeAnchorKind anchorKind;
  /** Nur bei {@code anchorKind == PlanetOrbit} gesetzt. */
  public String planetId;
  public String fleetId;
  public String ownerId;
  public long startedAt;
}
