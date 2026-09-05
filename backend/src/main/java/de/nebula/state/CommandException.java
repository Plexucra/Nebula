package de.nebula.state;

/**
 * Fachliche Ablehnung eines Befehls (Pendant zum geworfenen {@code Error} in
 * der TS-Simulation, z. B. "Unbekannte Kolonie.") – wird im WebSocket-Layer
 * (siehe {@code GameSocket}) 1:1 als {@code ServerMessage} vom Typ
 * {@code "Error"} an den Client durchgereicht.
 */
public class CommandException extends RuntimeException {
  public CommandException(String message) {
    super(message);
  }
}
