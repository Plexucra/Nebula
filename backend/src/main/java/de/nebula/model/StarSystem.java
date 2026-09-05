package de.nebula.model;

import java.util.List;

/**
 * Portierung von {@code System} aus {@code player.model.ts} – umbenannt zu
 * {@code StarSystem}, weil {@code System} in Java mit {@link java.lang.System}
 * kollidiert. Im JSON-Vertrag zum Frontend bleibt das Feld/der Typname aus
 * TS-Sicht ohnehin nur clientseitig relevant (eigenständige Typdefinition
 * dort), diese Umbenennung hat also keine Auswirkung auf die Spielmechanik.
 */
public class StarSystem {
  public String id;
  public String name;
  /** Grobe Galaxie-Koordinaten für die Kartendarstellung. */
  public double x;
  public double y;
  public List<String> planetIds;
  public String gatewayId;
  /** true = eigenes Heimatsystem eines Spielers (narrativ, für Prototyp-Flair). */
  public boolean isHomeSystem;
  public String factionFlavor;
  /** true = sektorale Handelsstation der Handelsgilde, siehe Konzeption/05_..., §5. */
  public boolean isTradeHub;
}
