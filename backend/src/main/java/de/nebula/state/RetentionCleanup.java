package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;

/**
 * Automatisches Aufräumen von Benachrichtigungen und Nachrichten nach Ablauf
 * ihrer Aufbewahrungsfrist (Umsetzungskonzept/15_...md, Auftrag 2). Einträge
 * mit gesetztem {@code keep}-Kennzeichen ("Beibehalten") sind davon
 * ausgenommen und bleiben unbegrenzt erhalten.
 *
 * <p>Die Fristen zählen in SPIELZEIT, nicht in Realzeit – sie stehen als
 * benannte Konstanten in Spielstunden in {@link GameConstants} und werden
 * hier über {@link Clock#hoursToMs(double)} in die Zeitbasis der Zeitstempel
 * umgerechnet. Damit skaliert die Aufbewahrung automatisch mit, falls die
 * Zeitkompression ({@code REAL_MS_PER_GAME_HOUR}) je geändert wird.</p>
 */
public final class RetentionCleanup {
  private RetentionCleanup() {
  }

  public static void purgeExpired(GameState state, long t) {
    long notificationMaxAge = (long) Clock.hoursToMs(GameConstants.NOTIFICATION_RETENTION_GAME_HOURS);
    long messageMaxAge = (long) Clock.hoursToMs(GameConstants.MESSAGE_RETENTION_GAME_HOURS);
    state.notifications.removeIf(n -> !n.keep && t - n.createdAt > notificationMaxAge);
    state.messages.removeIf(m -> !m.keep && t - m.sentAt > messageMaxAge);
  }
}
