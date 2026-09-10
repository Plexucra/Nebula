package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Player;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automatisches Aufräumen von Benachrichtigungen und Nachrichten nach Ablauf
 * ihrer Aufbewahrungsfrist sowie Löschung dauerhaft inaktiver Kommandanten
 * (Umsetzungskonzept/15_...md, Auftrag 2). Einträge mit gesetztem
 * {@code keep}-Kennzeichen ("Beibehalten") sind vom Aufräumen ausgenommen und
 * bleiben unbegrenzt erhalten.
 *
 * <h2>REALZEIT-AUSNAHME</h2>
 * <p>Diese Klasse ist – zusammen mit
 * {@code Economy.warnAboutSupplyGaps} – die einzige Stelle im Backend, die
 * NICHT in Spielstunden rechnet. Alle Fristen hier sind bereits
 * Realzeit-Millisekunden aus {@link GameConstants} und dürfen NIEMALS durch
 * {@code Clock.hoursToMs} laufen.</p>
 *
 * <p>Grund: Aufbewahrung und Inaktivität messen, wann ein MENSCH wieder an den
 * Rechner kommt. Der wird nicht schneller, wenn die Spieluhr schneller läuft.
 * Vorher hingen beide Fristen an der Spielzeit – bei
 * {@code gameSpeedMultiplier = 4} war eine Nachricht damit nach 105
 * Realsekunden gelöscht, eine Benachrichtigung nach 30. Das Nachrichten- und
 * Benachrichtigungssystem war dadurch praktisch funktionslos: Der Empfänger
 * bekam Post nie zu Gesicht, und der einzige Link auf einen Kampfbericht war
 * verschwunden, bevor der Kampf zu Ende war.</p>
 */
public final class RetentionCleanup {
  private RetentionCleanup() {
  }

  /**
   * Ereignis {@code RETENTION_CLEANUP} (stündlich in Spielzeit). {@code t} ist
   * die Spielzeit. Meldungen und Nachrichten tragen Spielzeit-Stempel und
   * werden deshalb gegen {@code t} gemessen: die Frist ist in echten Tagen
   * gemeint, und solange der Server läuft, laufen beide Uhren gleich schnell.
   * Steht die Spieluhr, weil der Server aus ist, verlängert das die Frist um
   * genau diese Zeit – in der niemand etwas hätte lesen können.
   */
  public static void purgeExpired(GameState state, IdGenerator ids, long t) {
    // REALZEIT-AUSNAHME: bereits Realzeit-Millisekunden, NICHT über Clock.hoursToMs umrechnen.
    state.notifications.removeIf(n -> !n.keep && t - n.createdAt > GameConstants.NOTIFICATION_RETENTION_REAL_MS);
    state.messages.removeIf(m -> !m.keep && t - m.sentAt > GameConstants.MESSAGE_RETENTION_REAL_MS);
    deleteInactivePlayers(state, ids, t);
  }

  /**
   * Löscht Kommandanten, die sich seit
   * {@code inactivePlayerDeletionRealDays} echten Tagen nicht mehr angemeldet
   * haben, restlos aus der Galaxie: Kolonien, Flotten, Gebäude, Lager, Truppen,
   * Orders, Verträge, Wallets, Nachrichten. Die Planeten selbst bleiben stehen
   * und sind danach wieder unbesiedelt – aus Sicht der Mitspieler verschwindet
   * das Reich also spurlos.
   *
   * <p>REALZEIT-AUSNAHME: {@code lastSeenAt} ist ein Stempel der REALUHR
   * (Anmeldung bzw. Registrierung) und wird gegen {@code Clock.realNow()}
   * gemessen – nie gegen die Spielzeit {@code t}, die gegen sie verschoben
   * sein kann (siehe {@code Clock}).</p>
   *
   * <p>NPC-Kommandanten ({@code PlayerRole.Npc}) sind ausgenommen – ihre Bots
   * melden sich zwar an, aber ein abgeschalteter Testlauf soll seine Lager
   * nicht nach einem Monat selbst entsorgen, sondern beim Neustart des Spiels
   * per Reset verschwinden.</p>
   */
  private static void deleteInactivePlayers(GameState state, IdGenerator ids, long t) {
    long realNow = Clock.realNow();
    List<Player> expired = state.players.stream()
        .filter(p -> p.role != de.nebula.model.PlayerRole.Npc)
        .filter(p -> inactiveMs(p, t, realNow) > GameConstants.INACTIVE_PLAYER_DELETION_REAL_MS)
        .toList();
    for (Player player : expired) deletePlayer(state, ids, player);
  }

  /** Ältere Spielstände ohne {@code lastSeenAt} zählen ab ihrer Erstellung – die steht in Spielzeit. */
  private static long inactiveMs(Player player, long gameNow, long realNow) {
    return player.lastSeenAt > 0 ? realNow - player.lastSeenAt : gameNow - player.createdAt;
  }

  /**
   * Entfernt einen Kommandanten und alles, was an ihm hängt. Bewusst als eine
   * Methode und nicht verteilt: Wer hier ein Feld vergisst, hinterlässt
   * Geisterobjekte mit einer {@code ownerId}, die es nicht mehr gibt – genau
   * das Problem, das {@code FleetCommands.removeDestroyedFleets} für Flotten
   * schon einmal lösen musste.
   */
  public static void deletePlayer(GameState state, IdGenerator ids, Player player) {
    String playerId = player.id;

    // Sets statt Listen: jedes removeIf unten fragt je Eintrag "gehört zu einer
    // seiner Kolonien?" – bei einem Reich mit vielen Kolonien und tausenden
    // Lager-/Gebäudeeinträgen wäre das mit contains auf einer Liste quadratisch.
    Set<String> colonyIds = state.colonies.stream()
        .filter(c -> c.ownerId.equals(playerId)).map(c -> c.id).collect(Collectors.toSet());

    // Kolonieabhängiges zuerst, damit nichts ohne Kolonie zurückbleibt.
    state.buildings.removeIf(b -> colonyIds.contains(b.colonyId));
    state.warehouse.removeIf(w -> colonyIds.contains(w.colonyId));
    state.specializations.removeIf(s -> colonyIds.contains(s.colonyId));
    state.productionQueue.removeIf(q -> colonyIds.contains(q.colonyId));
    state.shipyardQueue.removeIf(q -> colonyIds.contains(q.colonyId));
    state.recruitmentQueue.removeIf(q -> colonyIds.contains(q.colonyId));
    state.populations.removeIf(p -> colonyIds.contains(p.colonyId));
    state.planetStats.removeIf(s -> colonyIds.contains(s.colonyId));
    state.powerStates.removeIf(s -> colonyIds.contains(s.colonyId));
    state.energyStorages.removeIf(s -> colonyIds.contains(s.colonyId));
    // moneySupplyStates haengen am PLANETEN, nicht an der Kolonie – und ein Planet
    // kann von mehreren Kommandanten besiedelt sein (bewusste Regel, siehe TODO.md).
    // Der Eintrag bleibt deshalb stehen, wenn dort noch jemand anderes siedelt.
    Set<String> planetIds = state.colonies.stream()
        .filter(c -> c.ownerId.equals(playerId)).map(c -> c.planetId).collect(Collectors.toSet());
    state.moneySupplyStates.removeIf(m -> planetIds.contains(m.planetId)
        && state.colonies.stream().noneMatch(c -> c.planetId.equals(m.planetId) && !c.ownerId.equals(playerId)));
    for (String colonyId : colonyIds) {
      state.populationHistory.remove(colonyId);
      state.consumptionCoverage.remove(colonyId);
      state.rawStandardOfLiving.remove(colonyId);
      state.forgetColonyBookkeeping(colonyId);
    }
    state.notificationEdgeState.keySet().removeIf(k -> k.endsWith(":" + playerId));
    state.colonies.removeIf(c -> c.ownerId.equals(playerId));
    state.colonizations.removeIf(c -> c.ownerId.equals(playerId));

    // Militär und Bewegung
    Set<String> fleetIds = state.fleets.stream()
        .filter(f -> f.ownerId.equals(playerId)).map(f -> f.id).collect(Collectors.toSet());
    state.blockades.removeIf(b -> fleetIds.contains(b.fleetId) || b.ownerId.equals(playerId));
    state.battles.removeIf(b -> fleetIds.contains(b.attackerFleetId) || fleetIds.contains(b.defenderFleetId)
        || playerId.equals(b.attackerId) || playerId.equals(b.defenderId));
    state.groundBattles.removeIf(b -> playerId.equals(b.attackerId) || playerId.equals(b.defenderId));
    state.fleets.removeIf(f -> f.ownerId.equals(playerId));
    state.groundForceGroups.removeIf(g -> playerId.equals(g.ownerId));

    // Handel und Geld
    state.marketOrders.removeIf(o -> playerId.equals(o.ownerId));
    state.depot.removeIf(d -> playerId.equals(d.ownerId));
    Set<String> walletIds = state.wallets.stream()
        .filter(w -> w.ownerId.equals(playerId) || colonyIds.contains(w.ownerId)).map(w -> w.id).collect(Collectors.toSet());
    state.transactions.removeIf(tx -> walletIds.contains(tx.fromWalletId) || walletIds.contains(tx.toWalletId));
    state.wallets.removeIf(w -> walletIds.contains(w.id));

    // Politik und Kommunikation
    state.diplomaticRelations.removeIf(r -> r.playerAId.equals(playerId) || r.playerBId.equals(playerId));
    state.peaceOffers.removeIf(o -> o.fromPlayerId.equals(playerId) || o.toPlayerId.equals(playerId));
    state.treaties.removeIf(tr -> tr.playerAId.equals(playerId) || tr.playerBId.equals(playerId));
    state.treatyOffers.removeIf(o -> o.fromPlayerId.equals(playerId) || o.toPlayerId.equals(playerId));
    state.messages.removeIf(m -> m.fromPlayerId.equals(playerId) || m.toPlayerId.equals(playerId));
    // Beide Adressaten: an seine Kolonien UND die an ihn selbst gerichteten
    // (Umsetzungskonzept/34_...md, §H) – sonst bleiben unsichtbare Reste liegen.
    state.notifications.removeIf(n -> colonyIds.contains(n.colonyId) || playerId.equals(n.playerId));

    // Sichtbarkeit und der Kommandant selbst
    state.knownSystemIdsByPlayer.remove(playerId);
    state.exploredSystemIdsByPlayer.remove(playerId);
    state.players.removeIf(p -> p.id.equals(playerId));
    // Geplante Ereignisse seiner Objekte laufen ins Leere (die Behandler prüfen
    // die Existenz); nur der Sieg muss sofort neu bewertet werden – vielleicht
    // war er die letzte Gegenpartei.
    VictoryCommands.evaluate(state, ids);
  }
}
