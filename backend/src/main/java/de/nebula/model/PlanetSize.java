package de.nebula.model;

/** Enum-Konstanten bewusst exakt wie die TS-String-Literale geschrieben (Java erlaubt Umlaute/ß in Bezeichnern) – Jackson serialisiert per Default über {@code name()}, damit bleibt das JSON 1:1 kompatibel zum Frontend-Vertrag. */
public enum PlanetSize {
  Klein, Mittel, Groß, Riesig
}
