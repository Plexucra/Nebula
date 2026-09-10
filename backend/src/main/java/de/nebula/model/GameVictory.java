package de.nebula.model;

import java.util.List;

/**
 * Der entschiedene Krieg: EINE Partei besitzt noch Kolonien, alle anderen
 * keine mehr (Nutzervorgabe: "gewonnen hat eine Partei, wenn sie alle
 * feindlichen Kolonien vernichtet hat" – im Spiel geschieht das durch
 * Eroberung, siehe {@code ColonyConquest}).
 *
 * <p>Eine <b>Partei</b> ist das Lager ({@code Player.campId}) bei NPCs und der
 * Kommandant selbst bei allen anderen – zwei Lager, die gemeinsam angreifen,
 * gewinnen also gemeinsam.</p>
 *
 * <p>Der Zustand wird EINMAL festgeschrieben und danach nicht mehr verändert:
 * Wer gewonnen hat, hat gewonnen, auch wenn danach jemand neu registriert.
 * Das Spiel läuft weiter – es gibt keinen Endbildschirm, der die Galaxie
 * anhält (Umsetzungskonzept/13: der Weltzustand lebt weiter, solange der
 * Server läuft).</p>
 */
public class GameVictory {
  /** Kennung der siegreichen Partei: Lagername ({@code NORD}) oder {@code player:<id>}. */
  public String partyId;
  /** Anzeigename: Lagername bzw. Kommandantenname. */
  public String partyName;
  /** {@code true}, wenn die Partei ein Lager ist (mehrere Kommandanten). */
  public boolean camp;
  /** Kommandanten der siegreichen Partei (Anzeigenamen). */
  public List<String> members;
  /** Unterlegene Parteien (Anzeigenamen) – wer beim Sieg keine Kolonie mehr hatte. */
  public List<String> defeated;
  /** Spielzeitstempel der Entscheidung ({@code Clock.now()}). */
  public long declaredAt;
  /** Kolonien der Siegerpartei im Augenblick der Entscheidung. */
  public int colonies;
}
