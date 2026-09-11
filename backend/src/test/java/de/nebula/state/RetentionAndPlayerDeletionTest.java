package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Clock;
import de.nebula.engine.GameConstants;
import de.nebula.model.Colony;
import de.nebula.model.NotificationType;
import de.nebula.model.Player;
import de.nebula.model.PlayerRole;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REALZEIT-AUSNAHME: Aufbewahrungsfristen und die Löschung inaktiver
 * Kommandanten zählen in ECHTER Zeit, nicht in Spielzeit.
 *
 * <p>Genau das ist der Punkt dieser Tests. Vorher hingen beide Fristen an
 * {@code Clock.hoursToMs} und damit am Tempo-Regler – bei
 * {@code gameSpeedMultiplier = 4} war eine Nachricht nach 105 Realsekunden
 * gelöscht. Die Tests würden das sofort wieder bemerken: Sie prüfen die Fristen
 * gegen echte Millisekunden, ohne den Regler zu kennen.</p>
 */
class RetentionAndPlayerDeletionTest {

  private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

  private record Arena(GameState state, IdGenerator ids, Player player, Colony home) {
  }

  /**
   * Räumt zum Zeitpunkt {@code t} auf und hält dabei alle Kommandanten als
   * "gerade gesehen". Sonst würde beim Zeitsprung in die Zukunft die
   * Inaktivitäts-Löschung mitgreifen und die Aussage der Aufbewahrungstests
   * verfälschen – die Löschung hat ihre eigenen Tests weiter unten.
   */
  private static void purgeWithActivePlayers(GameState state, long t) {
    for (Player p : state.players) p.lastSeenAt = t;
    RetentionCleanup.purgeExpired(state, new IdGenerator(), t);
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Testkommandant", "Testheim", ids), ids);
    Colony home = state.colonies.stream().filter(c -> c.isHomeworld).findFirst().orElseThrow();
    Player player = state.players.get(0);
    player.lastSeenAt = Clock.now();
    return new Arena(state, ids, player, home);
  }

  // --- Aufbewahrung ----------------------------------------------------------

  @Test
  void notificationsSurviveFarLongerThanTheOldGameTimeDeadline() {
    Arena a = newArena();
    long now = Clock.now();
    Notifications.notify(a.state(), a.ids(), NotificationType.Info, Notifications.CODE_BUILDING_DONE,
        "Testmeldung", a.home().id, null);

    // Die ALTE Regel waren 48 SPIELstunden. Nach so viel Realzeit muss die
    // Meldung heute noch stehen – sonst hängt die Frist wieder an der Spieluhr.
    long oldDeadline = (long) Clock.hoursToMs(48);
    purgeWithActivePlayers(a.state(), now + oldDeadline + 1000);
    assertEquals(1, a.state().notifications.size(),
        "Eine Benachrichtigung darf nicht nach 48 SPIELstunden verschwinden – die Frist zählt in Realzeit.");

    // Kurz vor der echten Frist: steht noch.
    purgeWithActivePlayers(a.state(), now + GameConstants.NOTIFICATION_RETENTION_REAL_MS - ONE_DAY_MS);
    assertEquals(1, a.state().notifications.size());

    // Kurz danach: weg.
    purgeWithActivePlayers(a.state(), now + GameConstants.NOTIFICATION_RETENTION_REAL_MS + 1000);
    assertTrue(a.state().notifications.isEmpty(), "Nach Ablauf der echten Frist wird aufgeräumt.");
  }

  @Test
  void keptNotificationsAreNeverPurged() {
    Arena a = newArena();
    long now = Clock.now();
    Notifications.notify(a.state(), a.ids(), NotificationType.Info, Notifications.CODE_BUILDING_DONE,
        "Bleibt", a.home().id, null);
    a.state().notifications.get(0).keep = true;

    purgeWithActivePlayers(a.state(), now + 10 * GameConstants.NOTIFICATION_RETENTION_REAL_MS);
    assertEquals(1, a.state().notifications.size(), "\"Beibehalten\" schützt unbegrenzt.");
  }

  @Test
  void messagesUseTheRealTimeDeadlineToo() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("A", "Heim A", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, state.players, "B", "Heim B", ids, PlayerRole.Normal, null),
        ids);
    String from = state.players.get(0).id;
    String to = state.players.get(1).id;
    for (Player p : state.players) p.lastSeenAt = Clock.now();

    long now = Clock.now();
    MessageCommands.sendMessage(state, ids, from, to, "Betreff", "Text");
    assertEquals(1, MessageCommands.inbox(state, to).size());

    // Alte Regel: 168 SPIELstunden – danach muss die Nachricht noch da sein.
    purgeWithActivePlayers(state, now + (long) Clock.hoursToMs(168) + 1000);
    assertEquals(1, MessageCommands.inbox(state, to).size(),
        "Der Empfänger muss seine Post auch dann noch vorfinden, wenn die Spieluhr rast.");

    purgeWithActivePlayers(state, now + GameConstants.MESSAGE_RETENTION_REAL_MS + 1000);
    assertTrue(MessageCommands.inbox(state, to).isEmpty());
  }

  /**
   * NPC-Post lebt nur Minuten (Speicher des LAN-Servers, 11.9.2026): gelesene
   * Nachrichten an einen NPC und Benachrichtigungen an ihn oder seine Kolonie
   * verschwinden nach {@code NPC_MAIL_RETENTION_REAL_MS}; ungelesene Nachrichten
   * und die Post eines Menschen bleiben.
   */
  @Test
  void npcMailIsPurgedAfterMinutesWhileHumanMailStays() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Mensch", "Heim", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, state.players, "NPC-Nord-01", "Heim NPC", ids, PlayerRole.Npc, "NORD"),
        ids);
    Player human = state.players.get(0);
    Player npc = state.players.get(1);
    for (Player p : state.players) p.lastSeenAt = Clock.now();
    long now = Clock.now();

    MessageCommands.sendMessage(state, ids, human.id, npc.id, "Status", "gelesen");
    MessageCommands.sendMessage(state, ids, human.id, npc.id, "Status", "ungelesen");
    MessageCommands.sendMessage(state, ids, npc.id, human.id, "Antwort", "an den Menschen");
    MessageCommands.markMessageRead(state, npc.id, MessageCommands.inbox(state, npc.id).get(0).id);
    MessageCommands.markMessageRead(state, human.id, MessageCommands.inbox(state, human.id).get(0).id);
    Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_BUILDING_DONE, "NPC-Meldung", npc.id, null);
    Notifications.notify(state, ids, NotificationType.Info, Notifications.CODE_BUILDING_DONE, "NPC-Kolonie", npc.homeworldColonyId, null);
    Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_BUILDING_DONE, "Menschen-Meldung", human.id, null);
    int notificationsBefore = state.notifications.size();

    purgeWithActivePlayers(state, now + GameConstants.NPC_MAIL_RETENTION_REAL_MS + 1000);

    assertEquals(1, MessageCommands.inbox(state, npc.id).size(), "nur die ungelesene NPC-Nachricht bleibt");
    assertEquals("ungelesen", MessageCommands.inbox(state, npc.id).get(0).body);
    assertEquals(1, MessageCommands.inbox(state, human.id).size(), "gelesene Post eines Menschen bleibt bis zur normalen Frist");
    assertEquals(notificationsBefore - 2, state.notifications.size(), "beide NPC-Benachrichtigungen sind weg, die des Menschen nicht");
    assertTrue(state.notifications.stream().anyMatch(n -> "Menschen-Meldung".equals(n.message)));
  }

  // --- Löschung inaktiver Kommandanten ---------------------------------------

  @Test
  void anInactivePlayerVanishesWithEverythingHeOwned() {
    Arena a = newArena();
    String playerId = a.player().id;
    long now = Clock.now();

    assertFalse(a.state().colonies.isEmpty());
    assertFalse(a.state().fleets.isEmpty());

    // Kurz vor der Frist passiert nichts.
    a.player().lastSeenAt = now - GameConstants.INACTIVE_PLAYER_DELETION_REAL_MS + ONE_DAY_MS;
    RetentionCleanup.purgeExpired(a.state(), a.ids(), now);
    assertEquals(1, a.state().players.size(), "Wer kürzlich da war, bleibt.");

    // Danach verschwindet das Reich restlos.
    a.player().lastSeenAt = now - GameConstants.INACTIVE_PLAYER_DELETION_REAL_MS - 1000;
    RetentionCleanup.purgeExpired(a.state(), a.ids(), now);

    assertTrue(a.state().players.isEmpty(), "Der Kommandant selbst ist weg.");
    assertTrue(a.state().colonies.stream().noneMatch(c -> c.ownerId.equals(playerId)), "Keine Kolonien mehr.");
    assertTrue(a.state().fleets.stream().noneMatch(f -> f.ownerId.equals(playerId)), "Keine Flotten mehr.");
    assertTrue(a.state().buildings.isEmpty(), "Keine Gebäude mehr.");
    assertTrue(a.state().warehouse.isEmpty(), "Kein Lager mehr.");
    assertTrue(a.state().populations.isEmpty(), "Keine Bevölkerung mehr.");
    assertTrue(a.state().wallets.stream().noneMatch(w -> w.ownerId.equals(playerId)), "Kein Konto mehr.");
    assertTrue(a.state().marketOrders.stream().noneMatch(o -> playerId.equals(o.ownerId)), "Keine Orders mehr.");
    assertFalse(a.state().knownSystemIdsByPlayer.containsKey(playerId), "Keine Sichtbarkeitsdaten mehr.");

    // Die Planeten selbst bleiben stehen – sie sind danach wieder unbesiedelt.
    assertFalse(a.state().planets.isEmpty(), "Die Himmelskörper bleiben, nur das Reich verschwindet.");
  }

  @Test
  void npcPlayersAreNotDeletedByInactivity() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state,
        WorldSeed.createWorldSeed("Bot", "Botheim", ids, PlayerRole.Npc, "NORD"), ids);
    Player bot = state.players.get(0);
    bot.lastSeenAt = Clock.now() - 10 * GameConstants.INACTIVE_PLAYER_DELETION_REAL_MS;

    RetentionCleanup.purgeExpired(state, ids, Clock.now());
    assertEquals(1, state.players.size(),
        "Ein abgeschalteter Bot-Testlauf soll seine Lager nicht selbst entsorgen.");
  }

  @Test
  void aPlayerWithoutLastSeenCountsFromCreation() {
    Arena a = newArena();
    a.player().lastSeenAt = 0;
    a.player().createdAt = Clock.now() - GameConstants.INACTIVE_PLAYER_DELETION_REAL_MS - 1000;

    RetentionCleanup.purgeExpired(a.state(), a.ids(), Clock.now());
    assertTrue(a.state().players.isEmpty(),
        "Ältere Spielstände ohne lastSeenAt zählen ab ihrer Erstellung – kein unsterblicher Rest.");
  }
}
