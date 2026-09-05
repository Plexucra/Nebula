package de.nebula.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/util/graph.ts}: einfache
 * BFS-Hilfsfunktionen über das Gateway-Routennetz (Umsetzungskonzept/13_...md,
 * Phase 7 – Flottenbewegung).
 */
public final class Graph {
  private Graph() {
  }

  public record Route(String a, String b) {
  }

  private static Map<String, List<String>> adjacency(List<Route> routes) {
    Map<String, List<String>> map = new LinkedHashMap<>();
    for (Route r : routes) {
      map.computeIfAbsent(r.a(), k -> new ArrayList<>()).add(r.b());
      map.computeIfAbsent(r.b(), k -> new ArrayList<>()).add(r.a());
    }
    return map;
  }

  /** Kürzeste Sprunganzahl (Gateway-Hops) von {@code from} zu jedem erreichbaren Knoten. */
  public static Map<String, Integer> bfsHops(List<Route> routes, String from) {
    Map<String, List<String>> adj = adjacency(routes);
    Map<String, Integer> dist = new LinkedHashMap<>();
    dist.put(from, 0);
    Deque<String> queue = new ArrayDeque<>();
    queue.add(from);
    while (!queue.isEmpty()) {
      String cur = queue.poll();
      for (String nb : adj.getOrDefault(cur, List.of())) {
        if (!dist.containsKey(nb)) {
          dist.put(nb, dist.get(cur) + 1);
          queue.add(nb);
        }
      }
    }
    return dist;
  }

  /**
   * Kürzester Pfad von {@code from} zu {@code to} als geordnete Liste der
   * Zwischen-/Zielsysteme (OHNE {@code from} selbst, MIT {@code to} als
   * letztem Eintrag) – Grundlage für hop-für-hop abgearbeitete Flüge
   * ({@code Fleet.pendingHops}). {@code null}, wenn {@code to} von
   * {@code from} aus nicht erreichbar ist.
   */
  public static List<String> bfsPath(List<Route> routes, String from, String to) {
    Map<String, List<String>> adj = adjacency(routes);
    Map<String, String> prev = new LinkedHashMap<>();
    Set<String> visited = new LinkedHashSet<>();
    visited.add(from);
    Deque<String> queue = new ArrayDeque<>();
    queue.add(from);
    while (!queue.isEmpty()) {
      String cur = queue.poll();
      if (cur.equals(to)) break;
      for (String nb : adj.getOrDefault(cur, List.of())) {
        if (!visited.contains(nb)) {
          visited.add(nb);
          prev.put(nb, cur);
          queue.add(nb);
        }
      }
    }
    if (!visited.contains(to)) return null;
    List<String> path = new ArrayList<>();
    String cur = to;
    while (!cur.equals(from)) {
      path.add(0, cur);
      cur = prev.get(cur);
    }
    return path;
  }
}
