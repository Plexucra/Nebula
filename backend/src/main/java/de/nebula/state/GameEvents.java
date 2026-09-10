package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.ScheduledEvent;
import org.jboss.logging.Logger;

/**
 * Der Ereignisplaner. Ersetzt die frühere Tick-Schleife, die jede Sekunde
 * ALLE Bauten, Flotten, Gefechte, Aufträge, Verträge und Spezialisierungen
 * abgelaufen ist, um zu prüfen, ob etwas fällig ist. Jetzt wird jede
 * Fälligkeit dort geplant, wo sie entsteht, und der Takt arbeitet nur noch
 * die fälligen Ereignisse in Zeitreihenfolge ab ({@link #runDue}).
 *
 * <p><b>Ein Ereignis je Typ und Ziel.</b> {@link #schedule} ersetzt ein
 * bestehendes Ereignis desselben Schlüssels – ein Vorgang hat immer genau eine
 * nächste Fälligkeit. Wer einen Vorgang abbricht, ruft {@link #cancel}; und
 * weil das leicht vergessen wird, prüft jeder Behandler beim Feuern, ob das
 * Objekt die Fälligkeit noch genau so trägt ({@code completesAt == at}).
 * Ein veraltetes Ereignis wird still verworfen.</p>
 *
 * <p><b>Die Ereigniszeit ist die Uhr des Behandlers.</b> Was ein Behandler als
 * "jetzt" braucht (Abflug des nächsten Sprungs, nächste Gefechtsrunde),
 * nimmt er aus {@code event.at}, nicht aus {@code Clock.now()}. So driften
 * Folgetermine nicht mit der Abarbeitungslatenz, und Tests spulen mit
 * {@code runDue(state, ids, fälligkeit)} exakt bis zu einem Ereignis.</p>
 *
 * <p>Die Wirtschaft ist ein wiederkehrendes Ereignis JE KOLONIE – der
 * Kolonietag ({@link GameEventType#COLONY_DAY}, {@link Economy#colonyDay}):
 * einmal je Spieltag zur Tageszeit der Kolonie (Gründungszeit + n Tage), mit
 * Tageseinkauf und Vorrat statt eines Sekundentakts (Umsetzungskonzept/36).
 * Die Kolonien verteilen sich damit von selbst über den Tag.</p>
 */
public final class GameEvents {
  private GameEvents() {
  }

  private static final Logger LOG = Logger.getLogger(GameEvents.class);

  /** Ziel-Kennung der wiederkehrenden Aufgaben – sie haben kein Objekt. */
  private static final String NO_TARGET = "";
  /** Bleibt eine wiederkehrende Aufgabe weiter als das zurück, wird nicht nachgeholt, sondern neu aufgesetzt. */
  private static final long MAX_CATCH_UP_MS = 30_000;

  private static String key(String type, String targetId) {
    return type + ":" + targetId;
  }

  /**
   * Plant {@code type} für {@code targetId} zu {@code at} (Spielzeit) und ersetzt
   * eine bestehende Planung desselben Schlüssels. Fortschrittsgarantie: Wer aus
   * einer Ereignisbehandlung heraus etwas auf die eigene Zeit oder früher plant
   * (ein Dauerauftrag mit einer Fertigung unter einer Millisekunde), bekommt
   * eine Millisekunde später – sonst käme {@link #runDue} nie zum Ende.
   */
  public static void schedule(GameState state, GameEventType type, String targetId, long at) {
    Long eventTime = Clock.currentEventTime();
    if (eventTime != null && at <= eventTime) at = eventTime + 1;
    String key = key(type.name(), targetId);
    ScheduledEvent previous = state.eventByKey.remove(key);
    if (previous != null) state.events.remove(previous);
    ScheduledEvent event = new ScheduledEvent(at, state.eventSeq.incrementAndGet(), type.name(), targetId);
    state.events.add(event);
    state.eventByKey.put(key, event);
  }

  public static void cancel(GameState state, GameEventType type, String targetId) {
    ScheduledEvent previous = state.eventByKey.remove(key(type.name(), targetId));
    if (previous != null) state.events.remove(previous);
  }

  /** Nächste geplante Fälligkeit für Typ und Ziel, oder {@code null} – für Tests und Anzeigen. */
  public static Long scheduledAt(GameState state, GameEventType type, String targetId) {
    ScheduledEvent e = state.eventByKey.get(key(type.name(), targetId));
    return e == null ? null : e.at;
  }

  /**
   * Arbeitet alle Ereignisse ab, die bis {@code t} (Spielzeit) fällig sind, in
   * Zeitreihenfolge. Aufrufer hält die Sperre auf {@code state}.
   */
  public static void runDue(GameState state, IdGenerator ids, long t) {
    ensureRecurring(state, t);
    long fired = 0;
    while (!state.events.isEmpty()) {
      ScheduledEvent next = state.events.first();
      if (next.at > t) break;
      if (++fired > MAX_EVENTS_PER_RUN) {
        LOG.errorf("Mehr als %d Ereignisse in einem Durchlauf bis %d – Abbruch, nächstes wäre %s", MAX_EVENTS_PER_RUN, t, next);
        break;
      }
      state.events.pollFirst();
      state.eventByKey.remove(key(next.type, next.targetId), next);
      Clock.enterEventTime(next.at);
      try {
        fire(state, ids, next);
      } catch (RuntimeException e) {
        // Ein einzelnes defektes Ereignis darf nicht die ganze Galaxie anhalten.
        LOG.errorf(e, "Ereignis %s fehlgeschlagen", next);
      } finally {
        Clock.leaveEventTime();
      }
    }
  }

  /** Notbremse gegen ein Ereignis, das sich selbst ohne Zeitfortschritt neu plant – sollte dank {@link #schedule} nie greifen. */
  private static final long MAX_EVENTS_PER_RUN = 5_000_000;

  /**
   * Feuert das geplante Ereignis für Typ und Ziel sofort, mit SEINER Fälligkeit
   * als Ereigniszeit – ohne die Zeit bis dahin (und damit die Wirtschaft) laufen
   * zu lassen. Für Tests, die einen Vorgang isoliert zu Ende bringen wollen.
   *
   * @return false, wenn nichts geplant war
   */
  public static boolean fireNow(GameState state, IdGenerator ids, GameEventType type, String targetId) {
    ScheduledEvent event = state.eventByKey.remove(key(type.name(), targetId));
    if (event == null) return false;
    state.events.remove(event);
    Clock.enterEventTime(event.at);
    try {
      fire(state, ids, event);
    } finally {
      Clock.leaveEventTime();
    }
    return true;
  }

  /**
   * Sorgt dafür, dass die galaxieweiten wiederkehrenden Aufgaben geplant sind –
   * beim ersten Takt einer Galaxie und nach einem Reset. Die Statistik schreibt
   * sofort ihre erste Momentaufnahme, das Aufräumen läuft nach einer Stunde,
   * der Ausgleichsfonds nach einem vollen Spieltag. Die Kolonietage plant die
   * Gründung ({@link Economy#startColonyRhythm}).
   */
  private static void ensureRecurring(GameState state, long t) {
    if (scheduledAt(state, GameEventType.STATS_SNAPSHOT, NO_TARGET) == null) {
      schedule(state, GameEventType.STATS_SNAPSHOT, NO_TARGET, t);
    }
    if (scheduledAt(state, GameEventType.RETENTION_CLEANUP, NO_TARGET) == null) {
      schedule(state, GameEventType.RETENTION_CLEANUP, NO_TARGET, t + (long) Clock.hoursToMs(1));
    }
    if (scheduledAt(state, GameEventType.WEALTH_REDISTRIBUTION, NO_TARGET) == null) {
      schedule(state, GameEventType.WEALTH_REDISTRIBUTION, NO_TARGET, t + (long) GameConstants.GAME_DAY_MS);
    }
  }

  /**
   * Plant eine wiederkehrende Aufgabe relativ zu ihrer FÄLLIGKEIT neu, nicht
   * zur Abarbeitung – so bleibt der Takt driftfrei, und ein verspäteter Takt
   * holt versäumte Läufe nach. Liegt die Aufgabe weiter als
   * {@link #MAX_CATCH_UP_MS} zurück, wird nicht nachgeholt: das ist kein
   * Stottern mehr, sondern eine gestellte Uhr.
   */
  private static void rescheduleRecurring(GameState state, ScheduledEvent event, long intervalMs) {
    long next = event.at + intervalMs;
    // Gegen die Wanduhr messen, nicht gegen die Ereigniszeit: die ist per Definition "jetzt".
    long now = Clock.wallGameNow();
    if (next < now - MAX_CATCH_UP_MS) {
      LOG.warnf("%s lag %d s zurück – setze neu auf jetzt statt nachzuholen", event.type, (now - next) / 1000);
      next = now;
    }
    schedule(state, GameEventType.valueOf(event.type), event.targetId, next);
  }

  private static void fire(GameState state, IdGenerator ids, ScheduledEvent event) {
    GameEventType type = GameEventType.valueOf(event.type);
    switch (type) {
      case BUILDING_COMPLETED -> BuildingCommands.completeBuilding(state, ids, event.targetId, event.at);
      case DEFENSE_ACTIVATED -> BuildingCommands.completeDefenseActivation(state, event.targetId, event.at);
      case FLEET_ARRIVED -> FleetCommands.arrive(state, ids, event.targetId, event.at);
      case BATTLE_ROUND -> BattleCommands.round(state, ids, event.targetId, event.at);
      case GROUND_BATTLE_ROUND -> GroundBattleCommands.round(state, ids, event.targetId, event.at);
      case PRODUCTION_COMPLETED -> ProductionCommands.completeIfDue(state, ids, event.targetId, event.at);
      case SHIP_COMPLETED -> ShipyardCommands.completeIfDue(state, ids, event.targetId, event.at);
      case RECRUITMENT_COMPLETED -> RecruitmentCommands.completeIfDue(state, ids, event.targetId, event.at);
      case COLONIZATION_COMPLETED -> ColonyCommands.completeColonization(state, ids, event.targetId, event.at);
      case GROUND_FORCE_MOVED -> LandingCommands.completeGroundForceMove(state, ids, event.targetId, event.at);
      case SPECIALIZATION_DECAY -> Specializations.decay(state, event.targetId, event.at);
      case TREATY_ENDED -> TreatyCommands.endTreaty(state, ids, event.targetId, event.at);
      case COLONY_DAY -> {
        // Erst den nächsten Tag planen, dann rechnen: ein Fehler im Tag darf den
        // Rhythmus der Kolonie nicht für immer abreißen lassen. Ohne Kolonie
        // (aufgelöst, eingegliedert) endet der Rhythmus hier.
        if (ColonyCommands.colony(state, event.targetId) == null) return;
        rescheduleRecurring(state, event, (long) GameConstants.GAME_DAY_MS);
        Economy.colonyDay(state, ids, event.targetId, event.at);
      }
      case WEALTH_REDISTRIBUTION -> {
        Economy.runWealthRedistribution(state, ids);
        rescheduleRecurring(state, event, (long) GameConstants.GAME_DAY_MS);
      }
      case STATS_SNAPSHOT -> {
        Economy.recordStatsSnapshot(state, event.at);
        rescheduleRecurring(state, event, (long) Clock.hoursToMs(GameConstants.STATS_SNAPSHOT_INTERVAL_GAME_HOURS));
      }
      case RETENTION_CLEANUP -> {
        RetentionCleanup.purgeExpired(state, ids, event.at);
        rescheduleRecurring(state, event, (long) Clock.hoursToMs(1));
      }
    }
  }
}
