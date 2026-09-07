package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Message;
import de.nebula.model.Player;

import java.util.Comparator;
import java.util.List;

/**
 * 1:1-Gegenstück zur "Nachrichten"-Sektion in {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/14_...md) – ein komplett neues Feature ohne TS-Vorlage,
 * beidseitig (Browser-Simulation UND Backend) parallel entworfen, damit
 * `GameApi` für beide Implementierungen exakt denselben Vertrag erfüllt.
 * Ausschließlich Spieler-zu-Spieler, AUSDRÜCKLICH keine Gruppen-/
 * Broadcast-Nachrichten.
 */
public final class MessageCommands {
  private MessageCommands() {
  }

  public static List<Message> inbox(GameState state, String playerId) {
    return state.messages.stream()
        .filter(m -> m.toPlayerId.equals(playerId))
        .sorted(Comparator.comparingLong((Message m) -> m.sentAt).reversed())
        .toList();
  }

  public static List<Message> sentMessages(GameState state, String playerId) {
    return state.messages.stream()
        .filter(m -> m.fromPlayerId.equals(playerId))
        .sorted(Comparator.comparingLong((Message m) -> m.sentAt).reversed())
        .toList();
  }

  public static long unreadMessageCount(GameState state, String playerId) {
    return state.messages.stream().filter(m -> m.toPlayerId.equals(playerId) && !m.read).count();
  }

  public static void sendMessage(GameState state, IdGenerator ids, String fromPlayerId, String toPlayerId, String subject, String body) {
    Player me = GameQueries.requirePlayer(state, fromPlayerId);
    if (toPlayerId.equals(me.id)) throw new CommandException("Eine Nachricht an sich selbst ist nicht möglich.");
    if (state.players.stream().noneMatch(p -> p.id.equals(toPlayerId))) throw new CommandException("Unbekannter Empfänger.");
    String trimmedSubject = subject == null || subject.isBlank() ? "(kein Betreff)" : subject.trim();
    String trimmedBody = body == null ? "" : body.trim();
    if (trimmedBody.isEmpty()) throw new CommandException("Die Nachricht darf nicht leer sein.");

    Message message = new Message();
    message.id = ids.next("msg");
    message.fromPlayerId = me.id;
    message.toPlayerId = toPlayerId;
    message.subject = trimmedSubject;
    message.body = trimmedBody;
    message.sentAt = Clock.now();
    message.read = false;
    message.keep = false;
    state.messages.add(message);
  }

  /** Nur der Empfänger darf seine eigene Nachricht als gelesen markieren. */
  public static void markMessageRead(GameState state, String playerId, String id) {
    for (Message m : state.messages) {
      if (m.id.equals(id) && m.toPlayerId.equals(playerId)) m.read = true;
    }
  }

  /**
   * "Beibehalten" umschalten – schützt die Nachricht vor dem automatischen
   * Aufräumen nach {@code MESSAGE_RETENTION_GAME_HOURS} (siehe
   * {@link RetentionCleanup}). Sowohl Absender als auch Empfänger dürfen das
   * setzen: beide sehen dieselbe Nachricht (Postausgang bzw. Posteingang),
   * und für beide wäre ein Verlust gleichermaßen ärgerlich.
   */
  public static void setMessageKeep(GameState state, String playerId, String id, boolean keep) {
    for (Message m : state.messages) {
      if (m.id.equals(id) && (m.toPlayerId.equals(playerId) || m.fromPlayerId.equals(playerId))) m.keep = keep;
    }
  }
}
