package de.nebula.npcbot.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Bewusste Duplizierung von {@code de.nebula.ws.ServerMessage} aus dem
 * Backend, siehe {@link ClientMessage}. Anders als das Original bleibt
 * {@code payload} hier als {@link JsonNode} typisiert statt {@code Object} –
 * der Bot navigiert Antworten generisch per Feldname statt sie in
 * feste Modellklassen zu deserialisieren (keine Modulabhängigkeit zu
 * {@code de.nebula.model}).
 */
public class ServerMessage {
  public String type;
  public String requestId;
  public JsonNode payload;
}
