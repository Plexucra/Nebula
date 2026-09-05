package de.nebula.npcbot.ws;

/** Gegenstück zum {@code type: "Error"}-Umschlag des Servers – trägt dessen Fehlertext 1:1 weiter. */
public class CommandException extends RuntimeException {
  public CommandException(String message) {
    super(message);
  }
}
