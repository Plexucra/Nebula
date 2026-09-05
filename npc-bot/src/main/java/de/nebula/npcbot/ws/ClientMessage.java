package de.nebula.npcbot.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Bewusste Duplizierung von {@code de.nebula.ws.ClientMessage} aus dem
 * Backend (siehe Umsetzungskonzept/14_...md, Teil 2 – "keine Modulabhängigkeit
 * zwischen backend und npc-bot, zwei eigenständige Deployment-Einheiten").
 * Wire-Format 1:1 identisch: {@code type} = Name eines {@code GameApi}-Befehls,
 * {@code requestId} korreliert die Antwort, {@code payload} sind dessen Argumente.
 */
public class ClientMessage {
  public String type;
  public String requestId;
  public JsonNode payload;
}
