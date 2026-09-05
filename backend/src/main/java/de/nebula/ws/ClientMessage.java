package de.nebula.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Umschlag für JEDE Client→Server-Nachricht über die eine WebSocket-Verbindung
 * (Umsetzungskonzept/13_...md, §4 – "generischer Envelope statt 90
 * Einzeltypen"). {@code type} entspricht 1:1 einer {@code GameApi}-Methode
 * (z. B. {@code "queueProduction"}); {@code payload} sind deren Argumente als
 * JSON-Objekt (Feldnamen wie die TS-Methodensignatur). {@code requestId}
 * korreliert die Antwort ({@link ServerMessage} mit Typ {@code "Ack"} bzw.
 * {@code "Error"}) mit dem ursprünglichen Aufruf, analog zu einem Promise im
 * Frontend.
 */
public class ClientMessage {
  public String type;
  public String requestId;
  public JsonNode payload;
}
