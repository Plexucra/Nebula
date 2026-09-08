package de.nebula.npcbot;

import com.fasterxml.jackson.databind.JsonNode;
import de.nebula.npcbot.ws.CommandException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static de.nebula.npcbot.Json.text;

/**
 * Lagerdiplomatie: Krieg gegen JEDEN Kommandanten des gegnerischen Lagers,
 * Friedens- UND Handelsvertrag mit JEDEM Kommandanten des eigenen Lagers.
 *
 * <p>Der Friedensvertrag schließt eine versehentliche Kriegserklärung im
 * eigenen Lager aus (Umsetzungskonzept/21 §C), der Handelsvertrag öffnet den
 * planetaren Handel untereinander. Beides ist die Vorbedingung für die vom
 * Nutzer beschriebene Durchflugregel an Blockaden – die im Backend derzeit
 * nicht existiert (Bewegungen prüfen keine Blockaden, siehe
 * {@code FleetCommands.moveFleet}); der Vertragsstand wird trotzdem gepflegt
 * und im Monitoring ausgewiesen, damit die Regel bei Einführung sofort
 * greift.</p>
 *
 * <p>Friedensangebote des Gegners werden abgelehnt: das Lagerziel ist der
 * Sieg, nicht der Waffenstillstand. Menschliche Spieler (kein NPC-Präfix)
 * bleiben unbehelligt – gegen sie wird weder Krieg erklärt noch ein Vertrag
 * angeboten.</p>
 */
final class Diplomacy {
  private final Bot bot;
  private final Set<String> warDeclared = new HashSet<>();
  private final Set<String> treatyOffered = new HashSet<>();
  private int treatiesWithCamp;

  Diplomacy(Bot bot) {
    this.bot = bot;
  }

  int treatiesWithCamp() {
    return treatiesWithCamp;
  }

  void tick() {
    World w = bot.world;
    for (JsonNode p : w.players()) {
      String id = text(p, "id");
      String name = text(p, "name");
      if (id == null || id.equals(bot.playerId)) continue;
      if (bot.isEnemyCampName(name)) declareWar(id, name);
      else if (bot.isOwnCampName(name)) ensureTreaties(id, name);
    }
    answerOffers();
  }

  private void declareWar(String id, String name) {
    if (warDeclared.contains(id)) return;
    try {
      bot.call("declareWar", Map.of("otherPlayerId", id));
      bot.monitor.event("WAR_DECLARED", "Krieg erklärt an " + name, "other", name);
    } catch (CommandException e) {
      // "bereits im Krieg" ist nach einem Neustart der Normalfall.
    }
    warDeclared.add(id);
  }

  private void ensureTreaties(String id, String name) {
    Set<String> active = new HashSet<>();
    for (JsonNode t : bot.world.treaties()) {
      String other = Json.eq(text(t, "playerAId"), bot.playerId) ? text(t, "playerBId") : text(t, "playerAId");
      if (Json.eq(other, id)) active.add(text(t, "type"));
    }
    for (String type : new String[]{"Peace", "Trade"}) {
      String key = id + ":" + type;
      if (active.contains(type) || treatyOffered.contains(key)) continue;
      try {
        bot.call("offerTreaty", Map.of("otherPlayerId", id, "type", type));
        bot.monitor.event("TREATY_OFFERED", type + "-Vertrag angeboten an " + name, "other", name, "type", type);
      } catch (CommandException e) {
        // liegt bereits vor / besteht bereits – beides ist der gewünschte Zustand
      }
      treatyOffered.add(key);
    }
  }

  private void answerOffers() {
    World w = bot.world;
    for (JsonNode o : w.incomingTreatyOffers()) {
      JsonNode from = w.player(text(o, "fromPlayerId"));
      String fromName = from == null ? "?" : text(from, "name");
      boolean accept = bot.isOwnCampName(fromName);
      try {
        bot.call("respondToTreatyOffer", Map.of("offerId", text(o, "id"), "accept", accept));
        bot.monitor.event(accept ? "TREATY_ACCEPTED" : "TREATY_REJECTED",
            text(o, "type") + "-Vertrag von " + fromName + (accept ? " angenommen" : " abgelehnt"), "other", fromName, "type", text(o, "type"));
      } catch (CommandException e) {
        bot.monitor.log("Vertragsantwort abgelehnt: " + e.getMessage());
      }
    }
    w.invalidate("incomingTreatyOffers", "treaties");
    for (JsonNode o : w.incomingPeaceOffers()) {
      JsonNode from = w.player(text(o, "fromPlayerId"));
      String fromName = from == null ? "?" : text(from, "name");
      try {
        bot.call("respondToPeaceOffer", Map.of("offerId", text(o, "id"), "accept", false));
        bot.monitor.event("PEACE_REJECTED", "Friedensangebot von " + fromName + " abgelehnt – Lagerziel ist der Sieg", "other", fromName);
      } catch (CommandException e) {
        bot.monitor.log("Friedensantwort abgelehnt: " + e.getMessage());
      }
    }
    w.invalidate("incomingPeaceOffers");
    int n = 0;
    for (JsonNode t : w.treaties()) if (Json.isNull(t.path("terminationEffectiveAt"))) n++;
    treatiesWithCamp = n;
  }
}
