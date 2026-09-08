package de.nebula.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GroundUnitTypeDef {
  public String productTypeId;
  /** TS-Feldname {@code class} – in Java reserviertes Wort, daher umbenannt; JSON-Vertrag bleibt über die Annotation exakt gleich. */
  @JsonProperty("class")
  public GroundUnitClass unitClass;
  /**
   * Drohnenklasse, die gekontert wird (×2 Schaden), analog zu den
   * Schiffsklassen (Mechanik/05_..., §3). {@code null} bei Soldaten, die
   * keine eigene unmittelbare Kampfwirkung besitzen.
   */
  public GroundUnitClass countersClass;
}
