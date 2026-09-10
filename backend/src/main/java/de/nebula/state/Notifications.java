package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.GameNotification;
import de.nebula.model.NotificationType;

/**
 * Benachrichtigungssystem (Umsetzungskonzept/10_...md, §5).
 *
 * <p>ALLE Codes stehen hier – vorher lagen sie über {@code DiplomacyCommands},
 * {@code BattleCommands}, {@code GroundBattleCommands} und {@code EconomyTick}
 * verstreut, sodass sich niemand versehentlich doppelt vergebene Nummern
 * ausschließen konnte und das Frontend keine Liste hatte, an der es die
 * Beschriftung eines Links festmachen konnte.</p>
 *
 * <p>Code-Konvention nach Typ gestaffelt: 1xx Info, 4xx Warnung, 5xx Problem.</p>
 */
public final class Notifications {
  private Notifications() {
  }

  // --- Info (1xx) ---------------------------------------------------------
  public static final int CODE_PEACE_OFFERED = 101;
  public static final int CODE_PEACE_ACCEPTED = 102;
  /** Bauauftrag (Gebäude) fertiggestellt. */
  public static final int CODE_BUILDING_DONE = 103;
  /** Schiff aus der Werft fertiggestellt. */
  public static final int CODE_SHIP_DONE = 104;
  /** Flotte hat ihr Ziel erreicht. */
  public static final int CODE_FLEET_ARRIVED = 105;
  /** Verkaufsorder vollständig abverkauft. */
  public static final int CODE_SELL_ORDER_SOLD_OUT = 106;
  /** Energieversorgung wieder hergestellt – Gegenstück zu {@link #CODE_BLACKOUT}. */
  public static final int CODE_POWER_RESTORED = 107;
  public static final int CODE_TREATY_OFFERED = 111;
  public static final int CODE_TREATY_ACCEPTED = 112;
  public static final int CODE_TREATY_REJECTED = 113;
  public static final int CODE_TREATY_TERMINATION_REQUESTED = 114;
  public static final int CODE_TREATY_ENDED = 115;
  /** Neue Kolonie gegründet. */
  public static final int CODE_COLONY_FOUNDED = 120;
  /** Der Krieg ist entschieden – eine Partei besitzt als Einzige noch Kolonien ({@code VictoryCommands}). */
  public static final int CODE_VICTORY = 121;

  // --- Warnung (4xx) ------------------------------------------------------
  public static final int CODE_WAR_DECLARED = 401;
  public static final int CODE_BATTLE_STARTED = 402;
  public static final int CODE_BATTLE_ENDED = 403;
  public static final int CODE_GROUND_BATTLE_STARTED = 405;
  public static final int CODE_GROUND_BATTLE_ENDED = 406;
  public static final int CODE_GROUND_SIEGE_BEGUN = 407;
  /** Landung von der planetaren Abwehr abgefangen. */
  public static final int CODE_LANDING_INTERCEPTED = 404;
  /** Bevölkerung schrumpft anhaltend. */
  public static final int CODE_POPULATION_SHRINKING = 408;
  /** Guthaben des Kommandanten läuft leer (laufende Kosten übersteigen die Einnahmen). */
  public static final int CODE_TREASURY_LOW = 409;

  // --- Problem (5xx) ------------------------------------------------------
  /** Produktionswarteschlange mangels Vorprodukten angehalten. */
  public static final int CODE_QUEUE_STOPPED = 503;
  /** Grundbedarfsgut nicht am Systemmarkt zu bekommen. */
  public static final int CODE_SUPPLY_GAP = 505;
  /** Energieausfall: die Infrastruktur bekommt kein Elerium mehr. */
  public static final int CODE_BLACKOUT = 506;
  /** Guthaben aufgebraucht. */
  public static final int CODE_TREASURY_EMPTY = 507;
  /** Die Heimatwelt ist gefallen (Umsetzungskonzept/34_...md, §J 9) – der Kommandant bleibt im Spiel. */
  public static final int CODE_HOMEWORLD_LOST = 508;

  /**
   * Beschriftung des Links einer Benachrichtigung. Vorher stand im Frontend
   * fest "Kampfbericht öffnen →" – auch an einer Versorgungswarnung, die auf
   * eine Kolonie zeigt.
   */
  public static String linkLabel(int code) {
    return switch (code) {
      case CODE_BATTLE_STARTED, CODE_BATTLE_ENDED -> "Kampfbericht öffnen";
      case CODE_GROUND_BATTLE_STARTED, CODE_GROUND_BATTLE_ENDED, CODE_GROUND_SIEGE_BEGUN -> "Bodenkampfbericht öffnen";
      case CODE_WAR_DECLARED, CODE_PEACE_OFFERED, CODE_PEACE_ACCEPTED, CODE_TREATY_OFFERED, CODE_TREATY_ACCEPTED,
           CODE_TREATY_REJECTED, CODE_TREATY_TERMINATION_REQUESTED, CODE_TREATY_ENDED -> "Diplomatie öffnen";
      case CODE_FLEET_ARRIVED -> "Flotte öffnen";
      case CODE_SELL_ORDER_SOLD_OUT -> "Handel öffnen";
      case CODE_TREASURY_LOW, CODE_TREASURY_EMPTY -> "Konto öffnen";
      case CODE_VICTORY -> "Statistiken öffnen";
      case CODE_HOMEWORLD_LOST -> "Flotten öffnen";
      default -> "Kolonie öffnen";
    };
  }

  /** Routen-Pfad zu einer Kolonie – MUSS zu {@code app.routes.ts} passen ({@code planeten/:id}). */
  public static String colonyLink(String colonyId) {
    return "/planeten/" + colonyId;
  }

  /** Meldung an eine KOLONIE – sichtbar für deren jeweiligen Eigentümer. */
  public static void notify(GameState state, IdGenerator ids, NotificationType type, int code, String message,
                             String colonyId, String link) {
    add(state, ids, type, code, message, colonyId, null, link);
  }

  /**
   * Meldung an einen KOMMANDANTEN (Umsetzungskonzept/34_...md, §J 9). Für
   * alles, was nicht an einem Ort hängt: Diplomatie, Gefechte, Verlust der
   * Heimatwelt. Vorher wurde dafür die {@code homeworldColonyId} des Spielers
   * als Adresse missbraucht – mit dem Fall der Heimatwelt bekam der Eroberer
   * die Post des Verlierers.
   */
  public static void notifyPlayer(GameState state, IdGenerator ids, NotificationType type, int code, String message,
                                   String playerId, String link) {
    add(state, ids, type, code, message, null, playerId, link);
  }

  private static void add(GameState state, IdGenerator ids, NotificationType type, int code, String message,
                          String colonyId, String playerId, String link) {
    GameNotification n = new GameNotification();
    n.id = ids.next("ntf");
    n.code = code;
    n.type = type;
    n.message = message;
    n.colonyId = colonyId;
    n.playerId = playerId;
    n.createdAt = Clock.now();
    n.read = false;
    n.link = link;
    n.linkLabel = link == null ? null : linkLabel(code);
    state.notifications.add(0, n);
  }

  /**
   * Meldet nur, wenn sich der Zustand GEÄNDERT hat (Flankenauslösung). Ohne das
   * würde eine Dauerlage wie ein Blackout bei jedem Tick eine neue Meldung
   * erzeugen und die Glocke unbrauchbar machen – genau der Effekt, den die
   * Versorgungswarnung vorher hatte.
   *
   * @param stateKey eindeutiger Schlüssel der beobachteten Lage, z. B. {@code "blackout:col_1"}
   * @param active   liegt die Lage gerade vor?
   * @return true, wenn dieser Aufruf die Flanke war (Zustand hat gewechselt)
   */
  public static boolean edgeTriggered(GameState state, String stateKey, boolean active) {
    Boolean previous = state.notificationEdgeState.put(stateKey, active);
    return previous == null ? active : previous != active;
  }
}
