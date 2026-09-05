package de.nebula.state;

import de.nebula.model.Colony;
import de.nebula.model.Player;
import de.nebula.model.Transaction;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 1:1-Portierung der "Hilfsfunktionen"-Sektion aus
 * {@code simulated-game-api.service.ts} (Lookup/Autorisierung). Bewusste
 * Anpassung ans Server-Modell: die TS-Methoden hängen am EINEN aktiven
 * {@code this.player()} dieses Browser-Tabs; hier gibt es pro WebSocket-
 * Verbindung einen eigenen {@code playerId} (siehe {@code ConnectionRegistry}),
 * deshalb nehmen die entsprechenden Methoden ihn als Parameter statt ihn
 * implizit von einem Singleton-Zustand zu lesen.
 */
public final class GameQueries {
  private GameQueries() {
  }

  public static Player requirePlayer(GameState state, String playerId) {
    if (playerId != null) {
      for (Player p : state.players) {
        if (p.id.equals(playerId)) return p;
      }
    }
    throw new CommandException("Kein aktiver Kommandant.");
  }

  /** Besitzer-ID einer Colony (Spieler oder NPC) – finanzielle Aktionen hängen am Besitzer, nicht am Menschen. */
  public static String requireColonyOwner(GameState state, String colonyId) {
    for (Colony c : state.colonies) {
      if (c.id.equals(colonyId)) return c.ownerId;
    }
    throw new CommandException("Unbekannte Kolonie.");
  }

  /**
   * Autorisierungsgrenze für UI-ausgelöste, kolonie-gebundene Befehle: seit
   * mehrere echte Kommandanten dieselbe Galaxie teilen, reicht "Kolonie
   * existiert" allein nicht mehr – ohne diese Prüfung könnte jeder
   * eingeloggte Kommandant über die ID einer fremden Kolonie dort Gebäude
   * ausbauen, Produktion einreihen usw. NUR für vom aktiven Menschen direkt
   * ausgelöste Befehle gedacht – eine künftige NPC-KI ruft für ihre eigenen
   * Kolonien bewusst ungeprüfte {@code ...Core}-Varianten auf (siehe TS
   * {@code queueBuildingCore}), da dort kein "eingeloggter Kommandant" existiert.
   */
  public static Colony requireOwnColony(GameState state, String playerId, String colonyId) {
    Colony colony = null;
    for (Colony c : state.colonies) {
      if (c.id.equals(colonyId)) {
        colony = c;
        break;
      }
    }
    if (colony == null) throw new CommandException("Unbekannte Kolonie.");
    Player player = requirePlayer(state, playerId);
    if (!colony.ownerId.equals(player.id)) throw new CommandException("Diese Kolonie gehört einem anderen Kommandanten.");
    return colony;
  }

  /** Löst JEDEN Besitzer auf (nicht nur den aktuell anfragenden Kommandanten) – z. B. für Verkäufernamen am gemeinsamen Systemmarkt. */
  public static String ownerDisplayName(GameState state, String ownerId) {
    for (Player p : state.players) if (p.id.equals(ownerId)) return p.name;
    return "Unbekannt";
  }

  public static Wallet findWallet(GameState state, WalletOwnerType ownerType, String ownerId) {
    for (Wallet w : state.wallets) {
      if (w.ownerType == ownerType && w.ownerId.equals(ownerId)) return w;
    }
    return null;
  }

  public static String popWalletIdForColony(GameState state, String colonyId) {
    Wallet w = findWallet(state, WalletOwnerType.Population, colonyId);
    return w != null ? w.id : null;
  }

  public static String homeworldPopulationWalletId(GameState state, String playerId) {
    Player player = null;
    for (Player p : state.players) if (p.id.equals(playerId)) player = p;
    if (player == null) return null;
    return popWalletIdForColony(state, player.homeworldColonyId);
  }

  /** Letzte 200 Transaktionen des angemeldeten Kommandanten (als Spieler-Wallet-Besitzer), neueste zuerst. */
  public static List<Transaction> transactionsForPlayer(GameState state, String playerId) {
    Set<String> walletIds = new LinkedHashSet<>();
    for (Wallet w : state.wallets) {
      if (w.ownerType == WalletOwnerType.Player && w.ownerId.equals(playerId)) walletIds.add(w.id);
    }
    List<Transaction> matching = state.transactions.stream()
        .filter(t -> (t.fromWalletId != null && walletIds.contains(t.fromWalletId)) || (t.toWalletId != null && walletIds.contains(t.toWalletId)))
        .toList();
    int from = Math.max(0, matching.size() - 200);
    List<Transaction> last200 = matching.subList(from, matching.size());
    List<Transaction> reversed = new java.util.ArrayList<>(last200);
    java.util.Collections.reverse(reversed);
    return reversed;
  }

  public static int getBuildingLevel(GameState state, String colonyId, String buildingTypeId) {
    for (var b : state.buildings) {
      if (b.colonyId.equals(colonyId) && b.typeId.equals(buildingTypeId)) return b.level;
    }
    return 0;
  }
}
