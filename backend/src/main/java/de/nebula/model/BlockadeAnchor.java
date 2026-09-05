package de.nebula.model;

/**
 * Argument für {@code formBlockade} – Pendant zum TS-Discriminated-Union
 * {@code BlockadeAnchor}. Java-Sealed-Interface + Records statt Union-Typ;
 * im WebSocket-JSON wird das über das {@code kind}-Feld unterschieden
 * (siehe {@code de.nebula.ws}-Paket für die Jackson-Konfiguration).
 */
public sealed interface BlockadeAnchor {
  record Gateway() implements BlockadeAnchor {
  }

  record PlanetOrbit(String planetId) implements BlockadeAnchor {
  }
}
