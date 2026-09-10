package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.model.Colony;
import de.nebula.model.PlayerRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Siegbedingung: eine Partei besitzt als EINZIGE noch Kolonien
 * ({@link VictoryCommands}). Geprüft wird ohne Umweg über Landung und
 * Bodengefecht – der Sieg hängt allein am Kolonienbesitz, wie er nach einer
 * Eroberung ({@code ColonyConquest}) aussieht.
 */
class VictoryTest {

  private static GameState twoPlayers(IdGenerator ids) {
    GameState state = new GameState();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Erster", "Ersterheim", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, "Zweiter", "Zweiterheim", ids), ids);
    return state;
  }

  @Test
  void keinSiegSolangeBeideKolonienHaben() {
    IdGenerator ids = new IdGenerator();
    GameState state = twoPlayers(ids);
    VictoryCommands.evaluate(state, ids);
    VictoryCommands.evaluate(state, ids);
    assertNull(state.victory);
  }

  @Test
  void alleinigerKommandantEinerFrischenGalaxieGewinntNicht() {
    IdGenerator ids = new IdGenerator();
    GameState state = new GameState();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Allein", "Alleinheim", ids), ids);
    for (int i = 0; i < 5; i++) VictoryCommands.evaluate(state, ids);
    assertNull(state.victory, "Ohne zweite Partei gibt es niemanden zu besiegen");
  }

  @Test
  void werAlsEinzigerNochKolonienHatGewinnt() {
    IdGenerator ids = new IdGenerator();
    GameState state = twoPlayers(ids);
    // Erster Durchlauf: beide Parteien haben Kolonien – das merkt sich der Zustand.
    VictoryCommands.evaluate(state, ids);
    VictoryCommands.evaluate(state, ids);
    assertNull(state.victory);

    // Der Zweite verliert seine letzte Kolonie an den Ersten (wie nach einer Eroberung).
    String loserId = state.players.get(1).id;
    String winnerId = state.players.get(0).id;
    for (Colony c : state.colonies) if (c.ownerId.equals(loserId)) c.ownerId = winnerId;

    VictoryCommands.evaluate(state, ids);
    assertNotNull(state.victory);
    assertEquals("Erster", state.victory.partyName);
    assertTrue(state.victory.members.contains("Erster"));
    assertTrue(state.victory.defeated.contains("Zweiter"));
    assertTrue(state.notifications.stream()
        .anyMatch(n -> n.code == Notifications.CODE_VICTORY && loserId.equals(n.playerId)),
        "Auch der Verlierer erfährt vom Ausgang");
  }

  @Test
  void einLagerGewinntGemeinsam() {
    IdGenerator ids = new IdGenerator();
    GameState state = twoPlayers(ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, "Dritter", "Dritterheim", ids), ids);
    state.players.get(0).role = PlayerRole.Npc;
    state.players.get(0).campId = "NORD";
    state.players.get(2).role = PlayerRole.Npc;
    state.players.get(2).campId = "NORD";
    VictoryCommands.evaluate(state, ids);
    VictoryCommands.evaluate(state, ids);
    assertNull(state.victory);

    String loserId = state.players.get(1).id;
    state.colonies.removeIf(c -> c.ownerId.equals(loserId));

    VictoryCommands.evaluate(state, ids);
    assertNotNull(state.victory);
    assertTrue(state.victory.camp);
    assertEquals("NORD", state.victory.partyName);
    assertEquals(2, state.victory.members.size());
  }

  @Test
  void einmalEntschiedenBleibtEntschieden() {
    IdGenerator ids = new IdGenerator();
    GameState state = twoPlayers(ids);
    VictoryCommands.evaluate(state, ids);
    VictoryCommands.evaluate(state, ids);
    String loserId = state.players.get(1).id;
    state.colonies.removeIf(c -> c.ownerId.equals(loserId));
    VictoryCommands.evaluate(state, ids);
    long declaredAt = state.victory.declaredAt;

    // Der Verlierer gründet neu – der Sieg bleibt trotzdem stehen.
    Colony revived = new Colony();
    revived.id = ids.next("col");
    revived.ownerId = loserId;
    revived.name = "Neuanfang";
    revived.planetId = state.planets.get(0).id;
    revived.systemId = state.systems.get(0).id;
    state.colonies.add(revived);
    VictoryCommands.evaluate(state, ids);
    assertEquals(declaredAt, state.victory.declaredAt);
  }
}
