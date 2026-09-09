package de.nebula.state;

import de.nebula.model.GameNotification;

import java.util.List;
import java.util.Set;

/**
 * 1:1-Portierung der "Benachrichtigungen"-Sektion aus
 * {@code simulated-game-api.service.ts} (Umsetzungskonzept/13_...md, Rest
 * von Phase 10). {@code _notifications} ist EINE gemeinsame Liste über die
 * ganze Galaxie (jeder Eintrag hängt an einer {@code colonyId} oder ist
 * global bei {@code null}) – gefiltert auf die Kolonien des anfragenden
 * Kommandanten, damit ein Konto nie die Benachrichtigungen eines anderen zu
 * sehen bekommt.
 */
public final class NotificationCommands {
  private NotificationCommands() {
  }

  private static List<GameNotification> forPlayer(GameState state, String playerId) {
    Set<String> myColonyIds = state.colonies.stream()
        .filter(c -> c.ownerId.equals(playerId))
        .map(c -> c.id)
        .collect(java.util.stream.Collectors.toSet());
    return state.notifications.stream()
        .filter(n -> n.playerId != null
            ? n.playerId.equals(playerId)
            : n.colonyId == null || myColonyIds.contains(n.colonyId))
        .toList();
  }

  public static List<GameNotification> notifications(GameState state, String playerId) {
    return forPlayer(state, playerId);
  }

  public static long unreadNotificationCount(GameState state, String playerId) {
    return forPlayer(state, playerId).stream().filter(n -> !n.read).count();
  }

  public static void markNotificationRead(GameState state, String id) {
    for (GameNotification n : state.notifications) if (n.id.equals(id)) n.read = true;
  }

  public static void markAllNotificationsRead(GameState state, String playerId) {
    Set<String> myIds = forPlayer(state, playerId).stream().map(n -> n.id).collect(java.util.stream.Collectors.toSet());
    for (GameNotification n : state.notifications) if (myIds.contains(n.id)) n.read = true;
  }

  /**
   * "Beibehalten" umschalten – schützt die Benachrichtigung vor dem
   * automatischen Aufräumen nach {@code NOTIFICATION_RETENTION_GAME_HOURS}
   * (siehe {@link RetentionCleanup}). Nur für eigene Benachrichtigungen
   * wirksam (gleiche Sichtbarkeitsregel wie {@link #notifications}).
   */
  public static void setNotificationKeep(GameState state, String playerId, String id, boolean keep) {
    Set<String> myIds = forPlayer(state, playerId).stream().map(n -> n.id).collect(java.util.stream.Collectors.toSet());
    if (!myIds.contains(id)) return;
    for (GameNotification n : state.notifications) if (n.id.equals(id)) n.keep = keep;
  }
}
