package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.DiplomaticStatus;
import de.nebula.model.NotificationType;
import de.nebula.model.Player;
import de.nebula.model.Treaty;
import de.nebula.model.TreatyOffer;
import de.nebula.model.TreatyType;

import java.util.List;

/**
 * Förmliche Friedens- und Handelsverträge zwischen zwei Kommandanten,
 * zusätzlich zum einfachen Kriegs-/Friedenszustand aus
 * {@link DiplomacyCommands} (siehe Umsetzungskonzept/21_...md).
 *
 * <p><b>Friedensvertrag</b> ({@link TreatyType#Peace}): blockiert
 * {@code declareWar} zwischen den Parteien, solange er (inkl. laufender
 * Kündigungsfrist) gültig ist – schließt also einen Angriff aus, OHNE
 * automatisch auch Handel zu erlauben.</p>
 *
 * <p><b>Handelsvertrag</b> ({@link TreatyType#Trade}): Voraussetzung für
 * planetaren Handel (Kauf/Verkauf an einem Planetaren Handelsposten) zwischen
 * den Parteien, siehe {@link MarketCommands}. Handel an der neutralen
 * Handelsgilde-Station eines {@code isTradeHub}-Systems ist davon NICHT
 * betroffen – dort kann jeder mit jedem handeln.</p>
 *
 * <p>Beide Vertragsarten sind unabhängig voneinander abschließbar
 * (parallel, einzeln oder gar nicht), können aber nur geschlossen werden,
 * solange die Parteien nicht im Krieg stehen. Eine Kündigung beendet den
 * Vertrag nicht sofort, sondern erst nach einer Kündigungsfrist
 * ({@link GameConstants#PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS} bzw.
 * {@link GameConstants#TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS}) – bis
 * dahin gilt er unverändert weiter.</p>
 */
public final class TreatyCommands {
  private TreatyCommands() {
  }


  /** Kanonische, sortierte Paar-Reihenfolge – analog {@code DiplomacyCommands.relationKey}. */
  private static String[] key(String a, String b) {
    return a.compareTo(b) < 0 ? new String[]{a, b} : new String[]{b, a};
  }

  private static Treaty find(GameState state, String a, String b, TreatyType type) {
    String[] k = key(a, b);
    for (Treaty t : state.treaties) {
      if (t.type == type && t.playerAId.equals(k[0]) && t.playerBId.equals(k[1])) return t;
    }
    return null;
  }

  /** {@code true}, solange der Vertrag existiert und – falls gekündigt – die Kündigungsfrist noch nicht abgelaufen ist. */
  public static boolean isActive(GameState state, String a, String b, TreatyType type) {
    Treaty t = find(state, a, b, type);
    if (t == null) return false;
    return t.terminationEffectiveAt == null || Clock.now() < t.terminationEffectiveAt;
  }

  public static boolean hasPeaceTreaty(GameState state, String a, String b) {
    return isActive(state, a, b, TreatyType.Peace);
  }

  public static boolean hasTradeAgreement(GameState state, String a, String b) {
    return isActive(state, a, b, TreatyType.Trade);
  }

  /** Der aktive (inkl. gekündigt, aber noch nicht abgelaufene) Friedensvertrag zwischen beiden – für Fehlermeldungen mit Restlaufzeit. */
  public static Treaty findActivePeaceTreaty(GameState state, String a, String b) {
    Treaty t = find(state, a, b, TreatyType.Peace);
    return t != null && isActive(state, a, b, TreatyType.Peace) ? t : null;
  }

  public static List<Treaty> treatiesOf(GameState state, String playerId) {
    return state.treaties.stream().filter(t -> t.playerAId.equals(playerId) || t.playerBId.equals(playerId)).toList();
  }

  public static List<TreatyOffer> incomingTreatyOffers(GameState state, String playerId) {
    return state.treatyOffers.stream().filter(o -> o.toPlayerId.equals(playerId)).toList();
  }

  public static List<TreatyOffer> outgoingTreatyOffers(GameState state, String playerId) {
    return state.treatyOffers.stream().filter(o -> o.fromPlayerId.equals(playerId)).toList();
  }

  /** Legt ein einseitiges Vertragsangebot ab – wirksam erst nach {@code respondToTreatyOffer(accept: true)} durch den Empfänger. */
  public static void offerTreaty(GameState state, IdGenerator ids, String playerId, String otherPlayerId, TreatyType type) {
    Player me = GameQueries.requirePlayer(state, playerId);
    if (otherPlayerId.equals(me.id)) throw new CommandException("Ein Vertrag mit sich selbst ist nicht möglich.");
    Player other = state.players.stream().filter(p -> p.id.equals(otherPlayerId)).findFirst().orElse(null);
    if (other == null) throw new CommandException("Unbekannter Kommandant.");
    if (DiplomacyCommands.diplomaticStatus(state, me.id, otherPlayerId) == DiplomaticStatus.War) {
      throw new CommandException("Im Krieg können keine Verträge geschlossen werden.");
    }
    if (isActive(state, me.id, otherPlayerId, type)) {
      throw new CommandException("Es besteht bereits ein " + label(type) + " mit diesem Kommandanten.");
    }
    if (state.treatyOffers.stream().anyMatch(o -> o.type == type && o.fromPlayerId.equals(me.id) && o.toPlayerId.equals(otherPlayerId))) {
      throw new CommandException("Es liegt bereits ein Angebot für einen " + label(type) + " an diesen Kommandanten vor.");
    }
    TreatyOffer offer = new TreatyOffer();
    offer.id = ids.next("trof");
    offer.fromPlayerId = me.id;
    offer.toPlayerId = otherPlayerId;
    offer.type = type;
    offer.createdAt = Clock.now();
    state.treatyOffers.add(offer);
    Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_TREATY_OFFERED,
        me.name + " bietet Ihnen einen " + label(type) + " an.", other.id, "/diplomatie");
  }

  /** Nur der Empfänger darf antworten. Ablehnen löscht das Angebot ersatzlos. */
  public static void respondToTreatyOffer(GameState state, IdGenerator ids, String playerId, String offerId, boolean accept) {
    Player me = GameQueries.requirePlayer(state, playerId);
    TreatyOffer offer = state.treatyOffers.stream().filter(o -> o.id.equals(offerId)).findFirst().orElse(null);
    if (offer == null) throw new CommandException("Unbekanntes Vertragsangebot.");
    if (!offer.toPlayerId.equals(me.id)) throw new CommandException("Dieses Angebot richtet sich nicht an Sie.");
    state.treatyOffers.remove(offer);
    Player sender = state.players.stream().filter(p -> p.id.equals(offer.fromPlayerId)).findFirst().orElse(null);
    if (!accept) {
      if (sender != null) {
        Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_TREATY_REJECTED,
            me.name + " hat Ihr Angebot für einen " + label(offer.type) + " abgelehnt.", sender.id, "/diplomatie");
      }
      return;
    }
    if (DiplomacyCommands.diplomaticStatus(state, offer.fromPlayerId, offer.toPlayerId) == DiplomaticStatus.War) {
      throw new CommandException("Im Krieg kann dieses Angebot nicht mehr angenommen werden.");
    }
    String[] k = key(offer.fromPlayerId, offer.toPlayerId);
    Treaty existing = find(state, offer.fromPlayerId, offer.toPlayerId, offer.type);
    long t = Clock.now();
    if (existing != null) {
      // Erneute Annahme (z. B. nach eigener Kündigung) hebt eine laufende Kündigungsfrist wieder auf.
      existing.terminationEffectiveAt = null;
      existing.since = t;
      GameEvents.cancel(state, GameEventType.TREATY_ENDED, existing.id);
    } else {
      Treaty treaty = new Treaty();
      treaty.id = ids.next("trty");
      treaty.playerAId = k[0];
      treaty.playerBId = k[1];
      treaty.type = offer.type;
      treaty.since = t;
      state.treaties.add(treaty);
    }
    if (sender != null) {
      Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_TREATY_ACCEPTED,
          me.name + " hat Ihr Angebot für einen " + label(offer.type) + " angenommen.", sender.id, "/diplomatie");
    }
  }

  /** Kündigt einen bestehenden Vertrag – er bleibt bis zum Ende der Kündigungsfrist unverändert gültig (siehe Klassendoku). */
  public static void terminateTreaty(GameState state, IdGenerator ids, String playerId, String otherPlayerId, TreatyType type) {
    Player me = GameQueries.requirePlayer(state, playerId);
    Treaty treaty = find(state, me.id, otherPlayerId, type);
    if (treaty == null || !isActive(state, me.id, otherPlayerId, type)) {
      throw new CommandException("Kein " + label(type) + " mit diesem Kommandanten.");
    }
    if (treaty.terminationEffectiveAt != null) throw new CommandException("Dieser Vertrag ist bereits gekündigt.");
    double noticeHours = type == TreatyType.Peace
        ? GameConstants.PEACE_TREATY_TERMINATION_NOTICE_GAME_HOURS
        : GameConstants.TRADE_AGREEMENT_TERMINATION_NOTICE_GAME_HOURS;
    treaty.terminationEffectiveAt = Clock.now() + (long) Clock.hoursToMs(noticeHours);
    GameEvents.schedule(state, GameEventType.TREATY_ENDED, treaty.id, treaty.terminationEffectiveAt);
    Player other = state.players.stream().filter(p -> p.id.equals(otherPlayerId)).findFirst().orElse(null);
    if (other != null) {
      Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_TREATY_TERMINATION_REQUESTED,
          me.name + " hat den " + label(type) + " gekündigt.", other.id, "/diplomatie");
    }
  }

  /** Sofortige, kündigungsfristlose Beendigung ALLER Verträge/Angebote zwischen beiden – bei Kriegserklärung (siehe {@code DiplomacyCommands.declareWar}). */
  static void endAllImmediately(GameState state, String a, String b) {
    state.treatyOffers.removeIf(o ->
        (o.fromPlayerId.equals(a) && o.toPlayerId.equals(b)) || (o.fromPlayerId.equals(b) && o.toPlayerId.equals(a)));
    String[] k = key(a, b);
    for (Treaty t : state.treaties) {
      if (t.playerAId.equals(k[0]) && t.playerBId.equals(k[1])) GameEvents.cancel(state, GameEventType.TREATY_ENDED, t.id);
    }
    state.treaties.removeIf(t -> t.playerAId.equals(k[0]) && t.playerBId.equals(k[1]));
  }

  /** Ereignis {@code TREATY_ENDED}: die Kündigungsfrist ist abgelaufen. Veraltet, wenn der Vertrag erneut angenommen oder anders beendet wurde. */
  static void endTreaty(GameState state, IdGenerator ids, String treatyId, long at) {
    Treaty treaty = state.treaties.stream().filter(t -> t.id.equals(treatyId)).findFirst().orElse(null);
    if (treaty == null || treaty.terminationEffectiveAt == null || treaty.terminationEffectiveAt != at) return;
    state.treaties.remove(treaty);
    notifyEnded(state, ids, treaty);
  }

  private static void notifyEnded(GameState state, IdGenerator ids, Treaty tr) {
    for (String pid : new String[]{tr.playerAId, tr.playerBId}) {
      Player p = state.players.stream().filter(x -> x.id.equals(pid)).findFirst().orElse(null);
      if (p == null) continue;
      String otherId = tr.playerAId.equals(pid) ? tr.playerBId : tr.playerAId;
      Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_TREATY_ENDED,
          "Der " + label(tr.type) + " mit " + GameQueries.ownerDisplayName(state, otherId) + " ist ausgelaufen.",
          p.id, "/diplomatie");
    }
  }

  private static String label(TreatyType type) {
    return type == TreatyType.Peace ? "Friedensvertrag" : "Handelsvertrag";
  }
}
