package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.GameNotification;
import de.nebula.model.NotificationType;

/** 1:1-Portierung von {@code notify} aus {@code simulated-game-api.service.ts}. */
public final class Notifications {
  private Notifications() {
  }

  /** Problem-Code: Produktionswarteschlange mangels Vorprodukten angehalten (siehe TS {@code NOTIFICATION_CODE_QUEUE_STOPPED}). */
  public static final int CODE_QUEUE_STOPPED = 503;

  public static void notify(GameState state, IdGenerator ids, NotificationType type, int code, String message,
                             String colonyId, String link) {
    GameNotification n = new GameNotification();
    n.id = ids.next("ntf");
    n.code = code;
    n.type = type;
    n.message = message;
    n.colonyId = colonyId;
    n.createdAt = Clock.now();
    n.read = false;
    n.link = link;
    state.notifications.add(0, n);
  }
}
