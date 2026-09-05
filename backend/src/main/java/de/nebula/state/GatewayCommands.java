package de.nebula.state;

import de.nebula.engine.Graph;
import de.nebula.model.Colony;
import de.nebula.model.Gateway;
import de.nebula.model.GatewayWeightEntry;
import de.nebula.model.Player;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.StarSystem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 1:1-Portierung der "Gateway/Galaxie"-Sektion aus {@code simulated-game-api.service.ts}. */
public final class GatewayCommands {
  private GatewayCommands() {
  }

  public static Gateway gateway(GameState state, String systemId) {
    return state.gateways.stream().filter(g -> g.systemId.equals(systemId)).findFirst().orElse(null);
  }

  public static List<GatewayWeightEntry> gatewayWeights(GameState state, String playerId, String systemId) {
    Player player = state.players.stream().filter(p -> p.id.equals(playerId)).findFirst().orElse(null);
    if (player == null) return List.of();
    double weight = 0;
    for (Colony c : state.colonies) {
      if (!c.systemId.equals(systemId) || !c.ownerId.equals(player.id)) continue;
      double pop = 0;
      for (Population p : state.populations) if (p.colonyId.equals(c.id)) pop = p.currentCount;
      double loyalty = 0;
      for (PlanetStats s : state.planetStats) if (s.colonyId.equals(c.id)) loyalty = s.loyaltyPct;
      weight += pop * (loyalty / 100);
    }
    if (weight <= 0) return List.of();
    GatewayWeightEntry entry = new GatewayWeightEntry();
    entry.playerId = player.id;
    entry.playerName = player.name;
    entry.weight = Math.round(weight);
    return List.of(entry);
  }

  /** Netzwerktopologie ist öffentlich bekannt (Gateways von Anfang an offen) – ALLE Systeme, unabhängig vom Besuchsstatus. */
  public static List<StarSystem> visibleSystems(GameState state) {
    return List.copyOf(state.systems);
  }

  public static StarSystem system(GameState state, String id) {
    return state.systems.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null);
  }

  public static List<Graph.Route> gatewayRoutes(GameState state) {
    List<Graph.Route> routes = new ArrayList<>();
    for (Gateway g : state.gateways) {
      for (String target : g.reachableSystemIds) routes.add(new Graph.Route(g.systemId, target));
    }
    return routes;
  }

  public static List<Graph.Route> galaxyRoutes(GameState state) {
    Set<String> seen = new LinkedHashSet<>();
    List<Graph.Route> routes = new ArrayList<>();
    for (Gateway gateway : state.gateways) {
      for (String targetId : gateway.reachableSystemIds) {
        String a = gateway.systemId, b = targetId;
        String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
        if (!seen.add(key)) continue;
        routes.add(new Graph.Route(gateway.systemId, targetId));
      }
    }
    return routes;
  }

  /** Siehe TS {@code GameApi.hasVisitedSystem} – gesetzt durch {@code FleetCommands.processFleetArrivals}. */
  public static boolean hasVisitedSystem(GameState state, String playerId, String systemId) {
    Set<String> known = state.knownSystemIdsByPlayer.get(playerId);
    return known != null && known.contains(systemId);
  }
}
