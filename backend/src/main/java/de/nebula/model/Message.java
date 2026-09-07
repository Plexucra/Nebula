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
  /**
   * "Beibehalten": schützt die Nachricht vor der automatischen Löschung nach
   * {@code GameConstants.MESSAGE_RETENTION_GAME_HOURS} (siehe
   * {@code RetentionCleanup}). Standard {@code false}; sowohl Absender als
   * auch Empfänger dürfen ihn setzen, da beide dieselbe Nachricht in
   * Postausgang bzw. Posteingang sehen.
   */
  public boolean keep;
}
