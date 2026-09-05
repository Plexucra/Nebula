package de.nebula.state;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Verbindungen je Kommandant (Umsetzungskonzept/13_...md, "Mehrere
 * Verbindungen pro Kommandant"): anders als ein 1-Login-1-Session-Modell
 * kann ein Kommandant gleichzeitig vom Handy UND vom PC eingeloggt sein –
 * beide Verbindungen bekommen dieselben Pushes.
 */
@ApplicationScoped
public class ConnectionRegistry {
  private final Map<String, Set<WebSocketConnection>> byPlayerId = new ConcurrentHashMap<>();
  private final Map<String, String> playerIdByConnection = new ConcurrentHashMap<>();

  public void login(String playerId, WebSocketConnection connection) {
    logout(connection);
    byPlayerId.computeIfAbsent(playerId, id -> new CopyOnWriteArraySet<>()).add(connection);
    playerIdByConnection.put(connection.id(), playerId);
  }

  public void logout(WebSocketConnection connection) {
    String previousPlayerId = playerIdByConnection.remove(connection.id());
    if (previousPlayerId != null) {
      Set<WebSocketConnection> connections = byPlayerId.get(previousPlayerId);
      if (connections != null) {
        connections.remove(connection);
      }
    }
  }

  public String playerIdOf(WebSocketConnection connection) {
    return playerIdByConnection.get(connection.id());
  }

  public Set<WebSocketConnection> connectionsOf(String playerId) {
    return byPlayerId.getOrDefault(playerId, Set.of());
  }
}
