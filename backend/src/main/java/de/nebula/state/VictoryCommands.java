package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Colony;
import de.nebula.model.GameVictory;
import de.nebula.model.NotificationType;
import de.nebula.model.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Siegbedingung (Nutzervorgabe): <b>Eine Partei hat gewonnen, sobald sie
 * als EINZIGE noch Kolonien besitzt</b> – alle feindlichen Kolonien sind
 * vernichtet bzw. erobert.
 *
 * <p>Partei = Lager ({@code Player.campId}) bei NPCs, der Kommandant selbst bei
 * allen übrigen. Geprüft wird in jedem Tick ({@code GameTick}) und zwar erst,
 * wenn es überhaupt einmal MEHRERE Parteien mit Kolonien gab: sonst gewänne der
 * erste Kommandant einer frischen Galaxie im ersten Tick gegen niemanden.</p>
 *
 * <p>Kommandanten ohne Kolonie bleiben im Spiel (Umsetzungskonzept/34 §J 9) –
 * mit Flotten und der Möglichkeit, mit einem Kolonisationsschiff neu
 * anzufangen. Der Sieg wird deshalb festgeschrieben und gemeldet, aber die
 * Galaxie läuft weiter.</p>
 */
public final class VictoryCommands {
  private VictoryCommands() {
  }

  /**
   * Prüft die Lage und schreibt den Sieg fest, sobald nur noch eine Partei
   * Kolonien hat. Idempotent: ein einmal festgeschriebener Sieg bleibt stehen.
   */
  public static void evaluate(GameState state, IdGenerator ids) {
    if (state.victory != null) return;

    Map<String, List<Player>> partiesWithColonies = new LinkedHashMap<>();
    Set<String> allParties = new LinkedHashSet<>();
    for (Player p : state.players) {
      String party = partyOf(p);
      allParties.add(party);
      boolean hasColony = false;
      for (Colony c : state.colonies) if (c.ownerId.equals(p.id)) hasColony = true;
      if (hasColony) partiesWithColonies.computeIfAbsent(party, k -> new ArrayList<>()).add(p);
    }
    // Erst ab zwei Parteien ist überhaupt ein Krieg denkbar; und solange noch keine
    // zweite Partei Kolonien HATTE, ist "als Einzige Kolonien" kein Sieg, sondern
    // der Normalzustand einer jungen Galaxie.
    if (allParties.size() < 2) return;
    state.partiesEverWithColonies.addAll(partiesWithColonies.keySet());
    if (state.partiesEverWithColonies.size() < 2 || partiesWithColonies.size() != 1) return;

    Map.Entry<String, List<Player>> winner = partiesWithColonies.entrySet().iterator().next();
    GameVictory victory = new GameVictory();
    victory.partyId = winner.getKey();
    victory.camp = winner.getKey().startsWith("camp:");
    victory.partyName = displayName(winner.getKey(), winner.getValue());
    victory.members = winner.getValue().stream().map(p -> p.name).toList();
    victory.defeated = state.players.stream()
        .filter(p -> !winner.getValue().contains(p))
        .map(p -> p.name)
        .toList();
    victory.declaredAt = Clock.now();
    victory.colonies = (int) state.colonies.stream()
        .filter(c -> winner.getValue().stream().anyMatch(p -> p.id.equals(c.ownerId)))
        .count();
    state.victory = victory;

    String message = (victory.camp ? "Lager " : "") + victory.partyName
        + " hat den Krieg entschieden: alle gegnerischen Kolonien sind gefallen.";
    for (Player p : state.players) {
      Notifications.notifyPlayer(state, ids, NotificationType.Info, Notifications.CODE_VICTORY, message, p.id, "/statistiken");
    }
  }

  /** Kennung der Partei eines Kommandanten – Lager, sonst er selbst. */
  public static String partyOf(Player p) {
    return p.campId != null && !p.campId.isBlank() ? "camp:" + p.campId : "player:" + p.id;
  }

  private static String displayName(String partyId, List<Player> members) {
    if (partyId.startsWith("camp:")) return partyId.substring("camp:".length());
    return members.isEmpty() ? partyId : members.get(0).name;
  }
}
