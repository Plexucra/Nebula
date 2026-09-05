package de.nebula.npcbot.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ein einzelner WebSocket-Client gegen {@code /game}, per Hand mit
 * {@link HttpClient#newWebSocketBuilder()} gebaut (kein Quarkus, kein
 * generiertes Client-Gerüst – siehe Umsetzungskonzept/14_...md, Teil 2).
 * Übernimmt aus {@code backend/e2e/full-playthrough.mjs} das Grundmuster
 * "requestId-Korrelation per Future + Push-Cache für unaufgeforderte
 * Server-Nachrichten", hier als wiederverwendbare Klasse statt Ad-hoc-Skript.
 */
public class GameConnection implements WebSocket.Listener {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final Map<String, JsonNode> pushCache = new ConcurrentHashMap<>();
  private final StringBuilder textBuffer = new StringBuilder();
  private final AtomicLong requestCounter = new AtomicLong();
  private final String url;
  private WebSocket webSocket;

  public GameConnection(String url) {
    this.url = url;
  }

  public void connect() throws Exception {
    webSocket = HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(URI.create(url), this)
        .get(15, TimeUnit.SECONDS);
  }

  public void close() {
    if (webSocket != null) webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
  }

  /** Blockierender Befehlsaufruf: sendet {@link ClientMessage}, wartet auf die per requestId korrelierte Antwort. */
  public JsonNode call(String type, Map<String, Object> payloadFields) {
    String requestId = "req" + requestCounter.incrementAndGet();
    CompletableFuture<JsonNode> future = new CompletableFuture<>();
    pending.put(requestId, future);
    try {
      ObjectNode envelope = mapper.createObjectNode();
      envelope.put("type", type);
      envelope.put("requestId", requestId);
      envelope.set("payload", mapper.valueToTree(payloadFields));
      webSocket.sendText(mapper.writeValueAsString(envelope), true).get(15, TimeUnit.SECONDS);
      return future.get(20, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      pending.remove(requestId);
      if (e.getCause() instanceof CommandException ce) throw ce;
      throw new RuntimeException("Befehl '" + type + "' fehlgeschlagen", e.getCause());
    } catch (Exception e) {
      pending.remove(requestId);
      throw new RuntimeException("Befehl '" + type + "' fehlgeschlagen", e);
    }
  }

  public JsonNode call(String type) {
    return call(type, Map.of());
  }

  /** Letzter per Push empfangener Stand eines Kanals (z. B. {@code "players"}) – {@code null}, falls noch nichts empfangen. */
  public JsonNode latestPush(String channel) {
    return pushCache.get(channel);
  }

  private void handleMessage(String raw) {
    JsonNode node;
    try {
      node = mapper.readTree(raw);
    } catch (Exception e) {
      System.err.println("[ws] Konnte Nachricht nicht parsen: " + raw);
      return;
    }
    String type = node.path("type").asText(null);
    JsonNode requestIdNode = node.get("requestId");
    String requestId = requestIdNode != null && !requestIdNode.isNull() ? requestIdNode.asText() : null;
    JsonNode payload = node.has("payload") ? node.get("payload") : MissingNode.getInstance();

    if (requestId != null) {
      CompletableFuture<JsonNode> future = pending.remove(requestId);
      if (future != null) {
        if ("Error".equals(type)) {
          future.completeExceptionally(new CommandException(payload.path("message").asText("Unbekannter Fehler")));
        } else {
          future.complete(payload);
        }
        return;
      }
    }
    if (type != null) pushCache.put(type, payload);
  }

  @Override
  public void onOpen(WebSocket webSocket) {
    webSocket.request(1);
  }

  @Override
  public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
    textBuffer.append(data);
    webSocket.request(1);
    if (last) {
      String full = textBuffer.toString();
      textBuffer.setLength(0);
      handleMessage(full);
    }
    return null;
  }

  @Override
  public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
    RuntimeException closed = new RuntimeException("Verbindung geschlossen: " + statusCode + " " + reason);
    pending.values().forEach(f -> f.completeExceptionally(closed));
    pending.clear();
    return null;
  }

  @Override
  public void onError(WebSocket webSocket, Throwable error) {
    pending.values().forEach(f -> f.completeExceptionally(error));
    pending.clear();
  }
}
