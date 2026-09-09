package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.BattleStatus;
import de.nebula.model.DiplomaticRelation;
import de.nebula.model.DiplomaticStatus;
import de.nebula.model.NotificationType;
import de.nebula.model.PeaceOffer;
import de.nebula.model.Player;
import de.nebula.model.Treaty;

import java.util.List;

/**
 * 1:1-Portierung der "Diplomatie"-Sektion aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 9) – vereinfacht auf Krieg/Frieden
 * zwischen genau zwei Kommandanten (siehe TS-Klassendoku über
 * {@code engageBattle} für die bewusst nicht umgesetzten Teile).
 */
public final class DiplomacyCommands {
  private DiplomacyCommands() {
  }

  private static final double WAR_MIN_DURATION_HOURS = 24;

  /** Kanonische, sortierte Paar-Reihenfolge für {@link DiplomaticRelation} – EIN Eintrag je Paar. */
  private static String[] relationKey(String a, String b) {
    return a.compareTo(b) < 0 ? new String[]{a, b} : new String[]{b, a};
  }

  private static DiplomaticRelation findRelation(GameState state, String a, String b) {
    String[] key = relationKey(a, b);
    for (DiplomaticRelation r : state.diplomaticRelations) {
      if (r.playerAId.equals(key[0]) && r.playerBId.equals(key[1])) return r;
    }
    return null;
  }

  public static DiplomaticStatus diplomaticStatus(GameState state, String playerId, String otherPlayerId) {
    if (playerId == null || playerId.equals(otherPlayerId)) return DiplomaticStatus.Peace;
    DiplomaticRelation r = findRelation(state, playerId, otherPlayerId);
    return r != null ? r.status : DiplomaticStatus.Peace;
  }

  public static List<DiplomaticRelation> activeWars(GameState state, String playerId) {
    return state.diplomaticRelations.stream()
        .filter(r -> r.status == DiplomaticStatus.War && (r.playerAId.equals(playerId) || r.playerBId.equals(playerId)))
        .toList();
  }

  public static List<PeaceOffer> incomingPeaceOffers(GameState state, String playerId) {
    return state.peaceOffers.stream().filter(o -> o.toPlayerId.equals(playerId)).toList();
  }

  public static List<PeaceOffer> outgoingPeaceOffers(GameState state, String playerId) {
    return state.peaceOffers.stream().filter(o -> o.fromPlayerId.equals(playerId)).toList();
  }

  /** Einseitig – tritt sofort in Kraft. Löscht ein eventuell zwischen beiden noch offenes Friedensangebot (hinfällig). */
  public static void declareWar(GameState state, IdGenerator ids, String playerId, String otherPlayerId) {
    Player me = GameQueries.requirePlayer(state, playerId);
    if (otherPlayerId.equals(me.id)) throw new CommandException("Krieg gegen sich selbst ist nicht möglich.");
    Player other = state.players.stream().filter(p -> p.id.equals(otherPlayerId)).findFirst().orElse(null);
    if (other == null) throw new CommandException("Unbekannter Kommandant.");
    DiplomaticRelation existing = findRelation(state, me.id, otherPlayerId);
    if (existing != null && existing.status == DiplomaticStatus.War) {
      throw new CommandException("Sie befinden sich bereits im Krieg mit diesem Kommandanten.");
    }
    Treaty peaceTreaty = TreatyCommands.findActivePeaceTreaty(state, me.id, otherPlayerId);
    if (peaceTreaty != null) {
      if (peaceTreaty.terminationEffectiveAt == null) {
        throw new CommandException("Mit diesem Kommandanten besteht ein Friedensvertrag – kündigen Sie ihn zuerst über die Diplomatie.");
      }
      long remainingHours = (long) Math.ceil(Clock.msToHours(peaceTreaty.terminationEffectiveAt - Clock.now()));
      throw new CommandException("Der gekündigte Friedensvertrag mit diesem Kommandanten läuft noch " + Math.max(1, remainingHours) + " Spielstunden – erst danach ist eine Kriegserklärung möglich.");
    }
    long t = Clock.now();
    String[] key = relationKey(me.id, otherPlayerId);
    if (existing != null) {
      existing.status = DiplomaticStatus.War;
      existing.since = t;
    } else {
      DiplomaticRelation r = new DiplomaticRelation();
      r.id = ids.next("dip");
      r.playerAId = key[0];
      r.playerBId = key[1];
      r.status = DiplomaticStatus.War;
      r.since = t;
      state.diplomaticRelations.add(r);
    }
    state.peaceOffers.removeIf(o ->
        (o.fromPlayerId.equals(me.id) && o.toPlayerId.equals(otherPlayerId)) || (o.fromPlayerId.equals(otherPlayerId) && o.toPlayerId.equals(me.id)));
    // Krieg beendet automatisch einen ggf. noch laufenden Handelsvertrag (und dessen Angebote/Kündigungsfrist) –
    // ein Friedensvertrag kann hier nicht mehr bestehen, siehe Sperre oben.
    TreatyCommands.endAllImmediately(state, me.id, otherPlayerId);
    Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_WAR_DECLARED,
        me.name + " hat Ihnen den Krieg erklärt.", other.id, null);
  }

  /**
   * Legt ein einseitiges Friedensangebot ab – wirksam erst nach
   * {@code respondToPeaceOffer(accept: true)} durch den Empfänger. Gesperrt
   * während eines laufenden Gefechts zwischen den beiden und vor Ablauf von
   * {@code WAR_MIN_DURATION_HOURS} seit Kriegsbeginn.
   */
  public static void offerPeace(GameState state, IdGenerator ids, String playerId, String otherPlayerId) {
    Player me = GameQueries.requirePlayer(state, playerId);
    Player other = state.players.stream().filter(p -> p.id.equals(otherPlayerId)).findFirst().orElse(null);
    if (other == null) throw new CommandException("Unbekannter Kommandant.");
    DiplomaticRelation relation = findRelation(state, me.id, otherPlayerId);
    if (relation == null || relation.status != DiplomaticStatus.War) throw new CommandException("Sie befinden sich nicht im Krieg mit diesem Kommandanten.");
    if (Clock.now() - relation.since < Clock.hoursToMs(WAR_MIN_DURATION_HOURS)) {
      throw new CommandException("Ein Friedensangebot ist erst " + (int) WAR_MIN_DURATION_HOURS + " Spielstunden nach Kriegsbeginn möglich.");
    }
    boolean hasActiveBattle = state.battles.stream().anyMatch(b -> b.status == BattleStatus.Active
        && ((b.attackerId.equals(me.id) && b.defenderId.equals(otherPlayerId)) || (b.attackerId.equals(otherPlayerId) && b.defenderId.equals(me.id))));
    if (hasActiveBattle) throw new CommandException("Während eines laufenden Gefechts ist kein Friedensangebot möglich.");
    if (state.peaceOffers.stream().anyMatch(o -> o.fromPlayerId.equals(me.id) && o.toPlayerId.equals(otherPlayerId))) {
      throw new CommandException("Es liegt bereits ein Friedensangebot an diesen Kommandanten vor.");
    }
    PeaceOffer offer = new PeaceOffer();
    offer.id = ids.next("pof");
    offer.fromPlayerId = me.id;
    offer.toPlayerId = otherPlayerId;
    offer.createdAt = Clock.now();
    state.peaceOffers.add(offer);
    Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_PEACE_OFFERED,
        me.name + " bietet Ihnen Frieden an.", other.id, null);
  }

  /** Nur der Empfänger darf antworten. Ablehnen löscht das Angebot ersatzlos, der Krieg läuft weiter. */
  public static void respondToPeaceOffer(GameState state, IdGenerator ids, String playerId, String offerId, boolean accept) {
    Player me = GameQueries.requirePlayer(state, playerId);
    PeaceOffer offer = state.peaceOffers.stream().filter(o -> o.id.equals(offerId)).findFirst().orElse(null);
    if (offer == null) throw new CommandException("Unbekanntes Friedensangebot.");
    if (!offer.toPlayerId.equals(me.id)) throw new CommandException("Dieses Friedensangebot richtet sich nicht an Sie.");
    state.peaceOffers.remove(offer);
    if (accept) {
      String[] key = relationKey(offer.fromPlayerId, offer.toPlayerId);
      long t = Clock.now();
      for (DiplomaticRelation r : state.diplomaticRelations) {
        if (r.playerAId.equals(key[0]) && r.playerBId.equals(key[1])) {
          r.status = DiplomaticStatus.Peace;
          r.since = t;
        }
      }
      Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_PEACE_ACCEPTED,
          me.name + " hat Ihr Friedensangebot angenommen.", offer.fromPlayerId, null);
    }
  }
}
