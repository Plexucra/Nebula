package de.nebula.model;

/**
 * Die drei Orte, an denen eine im System angekommene ({@code Stationed})
 * Flotte innerhalb DESSELBEN Systems stehen kann (räumliche Hierarchie
 * System → Orbit → Kolonie) – Wechsel zwischen ihnen ist instant, nur
 * Gateway-Sprünge ZWISCHEN Systemen kosten Zeit:
 * <ul>
 *   <li>{@code ColonyOrbit} = angedockt an einer konkreten Kolonie
 *       ({@code locationColonyId} gesetzt) – Handel/Be-/Entladen nutzbar.</li>
 *   <li>{@code PlanetOrbit} = im Orbit eines Planeten ohne Andocken, auch bei
 *       unbesiedelten Planeten möglich ({@code locationPlanetId} gesetzt,
 *       {@code locationColonyId} NICHT) – Vorstufe für eine spätere
 *       Landungs-/Invasionsmechanik, aktuell ohne Handelszugriff.</li>
 *   <li>{@code System} = am Systemhandelsposten, keinem Planeten zugeordnet.</li>
 * </ul>
 */
public enum FleetLocationType {
  ColonyOrbit, PlanetOrbit, System
}
