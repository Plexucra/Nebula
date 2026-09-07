package de.nebula.model;

/**
 * Bei der Registrierung gewählte Spielerart (Umsetzungskonzept/20_...md).
 * {@code Normal}: die von uns festgelegten Standard-Startbedingungen.
 * {@code Npc}: von der NPC-Bot-Armee genutzt, siehe {@code Player.campId} –
 * das Heimatsystem wird beim eigenen Lager platziert statt maximal isoliert.
 * {@code Test}: Aufhänger für Sonderausstattung bei gezielten Tests (z. B.
 * Kampfsystem) – aktuell identisch zu {@code Normal}.
 */
public enum PlayerRole {
  Normal, Npc, Test
}
