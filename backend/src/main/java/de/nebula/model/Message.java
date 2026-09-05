package de.nebula.model;

/**
 * Ingame-Nachrichtensystem (Umsetzungskonzept/14_...md) – ausschließlich
 * Spieler-zu-Spieler ("E-Mail"), AUSDRÜCKLICH keine Gruppen-/
 * Broadcast-Nachrichten. Vollständig neues Feature ohne TS-Vorlage, analog
 * zu {@link GameNotification} gehalten: eine flache Liste, je Kommandant
 * server-/clientseitig auf seine eigenen Nachrichten gefiltert.
 */
public class Message {
  public String id;
  public String fromPlayerId;
  public String toPlayerId;
  public String subject;
  public String body;
  public long sentAt;
  /** Nur vom Empfänger ({@code toPlayerId}) gesetzt. */
  public boolean read;
}
