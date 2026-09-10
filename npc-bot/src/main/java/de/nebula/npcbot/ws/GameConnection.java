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
  /** Spieluhr des Servers aus der letzten Nachricht plus der Wanduhr-Zeitpunkt ihres Eintreffens. */
  private volatile long lastGameNow;
  private volatile long lastGameNowReceivedAt;

  public GameConnection(String url) {
    this.url = url;
  }

  public void connect() throws Exception {
    URI target = URI.create(url);
    requireLocalOrLan(target);
    webSocket = HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(target, this)
        .get(15, TimeUnit.SECONDS);
  }

  /**
   * Vorgabe: <b>Die NPC-Bots gehen nicht ins Internet.</b> Sie sprechen
   * ausschließlich mit dem Spielserver, und der steht auf demselben Rechner
   * oder im selben LAN (Umsetzungskonzept/14, Teil 3 – "NICHT für
   * Internet-Veröffentlichung gedacht"). Diese Prüfung macht das verbindlich,
   * statt es der Aufrufzeile zu überlassen: eine öffentliche Adresse in
   * {@code --server} wird abgewiesen, nicht stillschweigend kontaktiert.
   *
   * <p>Es ist die EINZIGE Netzwerkverbindung, die der Bot überhaupt aufbaut –
   * es gibt keinen weiteren HTTP-Aufruf im gesamten Modul.</p>
   */
  static void requireLocalOrLan(URI uri) throws java.net.UnknownHostException {
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
    if (!scheme.equals("ws") && !scheme.equals("wss")) {
      throw new IllegalArgumentException("Nur ws:// bzw. wss:// erlaubt, nicht: " + uri);
    }
    String host = uri.getHost();
    if (host == null) throw new IllegalArgumentException("Adresse ohne Rechnernamen: " + uri);
    for (java.net.InetAddress address : java.net.InetAddress.getAllByName(host)) {
      boolean local = address.isLoopbackAddress() || address.isSiteLocalAddress()
          || address.isLinkLocalAddress() || address.isAnyLocalAddress();
      if (!local) {
        throw new IllegalArgumentException("Der Bot verbindet sich nur mit einem Server im eigenen Netz. "
            + "Adresse " + address.getHostAddress() + " (" + host + ") liegt außerhalb – Verbindung abgelehnt.");
      }
    }
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

  /**
   * Die SPIELUHR des Servers, fortgeschrieben seit der letzten Nachricht. Jeder
   * Zeitstempel, den der Server liefert ({@code endsAt}, {@code arrivesAt}, ...),
   * steht in dieser Zeit – sie kann gegen die Wanduhr verschoben sein
   * ({@code Clock} im Backend). Restzeiten deshalb nie gegen
   * {@code System.currentTimeMillis()} rechnen, sondern hiergegen.
   */
  public long gameNow() {
    if (lastGameNowReceivedAt == 0) return System.currentTimeMillis();
    return lastGameNow + (System.currentTimeMillis() - lastGameNowReceivedAt);
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
    JsonNode gameNow = node.get("gameNow");
    if (gameNow != null && gameNow.isNumber()) {
      lastGameNow = gameNow.asLong();
      lastGameNowReceivedAt = System.currentTimeMillis();
    }
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
