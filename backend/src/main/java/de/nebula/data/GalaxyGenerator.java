package de.nebula.data;

import de.nebula.engine.Rng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/galaxy-generator.ts}.
 * Prozedurale Erzeugung der Galaxie-Topologie (Gateway-Netz + sektorale
 * Handelsstationen), rein geometrisch/graphbasiert – ohne Kenntnis von
 * Spielentitäten. {@code WorldSeed} übersetzt das Ergebnis in
 * {@code StarSystem}-, {@code Gateway}- und Handelsstations-Datensätze.
 *
 * <p>Designvorgaben (siehe Konzeption/04_..., §1 und Mechanik/08_..., §1):
 * jedes Gateway hat dieselbe feste Reichweite; zwei Systeme sind genau dann
 * verbunden, wenn ihr geometrischer Abstand ≤ dieser einen, galaxieweit
 * einheitlichen Reichweite liegt. Dass daraus im Schnitt 3-6 Nachbarn pro
 * System entstehen ist eine FOLGE der Systemplatzierung (annähernd
 * gleichmäßiger Mindestabstand, siehe {@link #poissonDiskPositions}), nicht
 * das primäre Auswahlkriterium. Der gesamte Graph ist zusammenhängend.
 * Sektorale Handelsstationen (Konzeption/05_..., §5) werden so platziert,
 * dass jedes System im Schnitt ca. 2, maximal 3 Gateway-Sprünge von der
 * nächsten Handelsstation entfernt liegt.</p>
 */
public final class GalaxyGenerator {
  private GalaxyGenerator() {
  }

  private static final double MARGIN = 0.06;
  private static final double TARGET_AVG_DEGREE = 4.5;
  private static final int MIN_DEGREE = 3;
  private static final int MAX_DEGREE = 6;
  private static final double TARGET_AVG_HUB_HOPS = 2;
  private static final int MAX_HUB_HOPS = 3;
  private static final int MAX_TRADE_HUBS = 8;

  public record Point(double x, double y) {
  }

  public record GeneratedGalaxy(List<Point> positions, List<List<Integer>> neighbors, double gatewayRange,
                                 int centralIndex, List<Integer> tradeHubIndices) {
  }

  private static double distance(Point a, Point b) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }

  /**
   * Platziert {@code count} Systeme mit einem angestrebten Mindestabstand
   * zueinander (Poisson-Disk-artiges Sampling per Rejection-Verfahren – für
   * die hier relevanten Größenordnungen von ein paar Dutzend/Hundert
   * Systemen reicht das simple O(n²)-Verfahren völlig aus).
   */
  private static List<Point> poissonDiskPositions(int count, Rng rng) {
    double usableSide = 1 - 2 * MARGIN;
    double minDist = 0.82 * Math.sqrt((usableSide * usableSide) / count);
    int maxOuterAttempts = 4;
    int attemptsPerPoint = 250;

    List<Point> best = null;
    int bestFallbacks = Integer.MAX_VALUE;

    for (int outer = 0; outer < maxOuterAttempts; outer++) {
      List<Point> positions = new ArrayList<>();
      int fallbacks = 0;
      for (int i = 0; i < count; i++) {
        Point placed = null;
        Point bestCandidate = null;
        double bestCandidateMinDist = -1;
        for (int attempt = 0; attempt < attemptsPerPoint; attempt++) {
          Point candidate = new Point(MARGIN + rng.next() * usableSide, MARGIN + rng.next() * usableSide);
          double nearest = Double.POSITIVE_INFINITY;
          for (Point p : positions) {
            nearest = Math.min(nearest, distance(candidate, p));
          }
          if (nearest >= minDist) {
            placed = candidate;
            break;
          }
          if (nearest > bestCandidateMinDist) {
            bestCandidateMinDist = nearest;
            bestCandidate = candidate;
          }
        }
        if (placed != null) {
          positions.add(placed);
        } else {
          positions.add(bestCandidate != null ? bestCandidate : new Point(0.5, 0.5));
          fallbacks++;
        }
      }
      if (best == null || fallbacks < bestFallbacks) {
        best = positions;
        bestFallbacks = fallbacks;
      }
      if (fallbacks == 0) break;
      minDist *= 0.85;
    }

    return best;
  }

  /** Anzahl Systeme, die bei gegebener Reichweite {@code r} erreichbar wären, im Schnitt über alle Systeme. */
  private static double averageDegreeAtRange(List<Point> positions, double r) {
    int n = positions.size();
    double total = 0;
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        if (distance(positions.get(i), positions.get(j)) <= r) total += 2;
      }
    }
    return total / n;
  }

  /** Bestimmt die einheitliche Gateway-Reichweite per Bisektion auf die Ziel-Durchschnittsnachbarnzahl. */
  private static double pickGatewayRange(List<Point> positions) {
    double lo = 0;
    double hi = Math.sqrt(2); // größtmögliche Distanz im Einheitsquadrat
    for (int iter = 0; iter < 40; iter++) {
      double mid = (lo + hi) / 2;
      if (averageDegreeAtRange(positions, mid) < TARGET_AVG_DEGREE) lo = mid; else hi = mid;
    }
    return (lo + hi) / 2;
  }

  private static List<Set<Integer>> buildAdjacency(List<Point> positions, double range) {
    int n = positions.size();
    List<Set<Integer>> adjacency = new ArrayList<>();
    for (int i = 0; i < n; i++) adjacency.add(new LinkedHashSet<>());

    List<List<Integer>> neighborsByDistance = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Integer> order = new ArrayList<>();
      for (int j = 0; j < n; j++) if (j != i) order.add(j);
      final int fi = i;
      order.sort((a, b) -> Double.compare(distance(positions.get(fi), positions.get(a)), distance(positions.get(fi), positions.get(b))));
      neighborsByDistance.add(order);
    }

    // 1) Kernregel: Gateway-Reichweite ist für alle Systeme identisch.
    for (int i = 0; i < n; i++) {
      for (int j : neighborsByDistance.get(i)) {
        if (j <= i) continue;
        if (distance(positions.get(i), positions.get(j)) <= range) {
          adjacency.get(i).add(j);
          adjacency.get(j).add(i);
        }
      }
    }

    // 2) Obergrenze durchsetzen (seltener Ausreißer in dichteren Regionen):
    // nur die nächsten MAX_DEGREE Verbindungen innerhalb der Reichweite behalten.
    for (int i = 0; i < n; i++) {
      if (adjacency.get(i).size() <= MAX_DEGREE) continue;
      List<Integer> kept = new ArrayList<>();
      for (int j : neighborsByDistance.get(i)) {
        if (adjacency.get(i).contains(j)) kept.add(j);
        if (kept.size() >= MAX_DEGREE) break;
      }
      Set<Integer> keepSet = new LinkedHashSet<>(kept);
      for (int j : new ArrayList<>(adjacency.get(i))) {
        if (!keepSet.contains(j)) {
          adjacency.get(i).remove(j);
          adjacency.get(j).remove(i);
        }
      }
    }

    // 3) Untergrenze durchsetzen (seltener Ausreißer in spärlicheren Regionen):
    // fehlende Verbindungen zu den nächstgelegenen Systemen ergänzen, auch
    // wenn das im Einzelfall die reguläre Reichweite geringfügig überschreitet.
    for (int i = 0; i < n; i++) {
      for (int j : neighborsByDistance.get(i)) {
        if (adjacency.get(i).size() >= MIN_DEGREE) break;
        if (adjacency.get(j).size() >= MAX_DEGREE) continue;
        adjacency.get(i).add(j);
        adjacency.get(j).add(i);
      }
    }

    // 4) Zusammenhang des Gesamtgraphen erzwingen.
    ensureConnected(n, adjacency, positions);

    return adjacency;
  }

  private static void ensureConnected(int n, List<Set<Integer>> adjacency, List<Point> positions) {
    for (int guard = 0; guard < n; guard++) {
      int[] comp = new int[n];
      Arrays.fill(comp, -1);
      int compId = 0;
      for (int start = 0; start < n; start++) {
        if (comp[start] != -1) continue;
        List<Integer> queue = new ArrayList<>();
        queue.add(start);
        comp[start] = compId;
        int head = 0;
        while (head < queue.size()) {
          int cur = queue.get(queue.size() - 1);
          queue.remove(queue.size() - 1);
          for (int nb : adjacency.get(cur)) {
            if (comp[nb] == -1) {
              comp[nb] = compId;
              queue.add(nb);
            }
          }
        }
        compId++;
      }
      Set<Integer> distinctComponents = new LinkedHashSet<>();
      for (int c : comp) distinctComponents.add(c);
      if (distinctComponents.size() <= 1) return;

      Integer bestI = null;
      Integer bestJ = null;
      double bestD = Double.POSITIVE_INFINITY;
      for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
          if (comp[i] == comp[j]) continue;
          double d = distance(positions.get(i), positions.get(j));
          if (d < bestD) {
            bestD = d;
            bestI = i;
            bestJ = j;
          }
        }
      }
      if (bestI != null) {
        adjacency.get(bestI).add(bestJ);
        adjacency.get(bestJ).add(bestI);
      }
    }
  }

  private static List<List<Integer>> bfsAllPairs(List<List<Integer>> neighbors) {
    int n = neighbors.size();
    List<List<Integer>> result = new ArrayList<>();
    for (int start = 0; start < n; start++) {
      int[] dist = new int[n];
      Arrays.fill(dist, Integer.MAX_VALUE);
      dist[start] = 0;
      List<Integer> queue = new ArrayList<>();
      queue.add(start);
      int head = 0;
      while (head < queue.size()) {
        int cur = queue.get(head++);
        for (int nb : neighbors.get(cur)) {
          if (dist[nb] == Integer.MAX_VALUE) {
            dist[nb] = dist[cur] + 1;
            queue.add(nb);
          }
        }
      }
      List<Integer> distList = new ArrayList<>();
      for (int d : dist) distList.add(d);
      result.add(distList);
    }
    return result;
  }

  private static List<Integer> placeTradeHubs(List<List<Integer>> distances) {
    int n = distances.size();
    int center = 0;
    int bestEcc = Integer.MAX_VALUE;
    for (int i = 0; i < n; i++) {
      int ecc = distances.get(i).stream().mapToInt(Integer::intValue).max().orElse(0);
      if (ecc < bestEcc) {
        bestEcc = ecc;
        center = i;
      }
    }

    List<Integer> hubs = new ArrayList<>();
    hubs.add(center);

    while (hubs.size() < MAX_TRADE_HUBS) {
      double[] avgMax = tradeHubMetrics(distances, hubs, n);
      if (avgMax[0] <= TARGET_AVG_HUB_HOPS && avgMax[1] <= MAX_HUB_HOPS) break;
      int farthest = -1;
      int farthestDist = -1;
      for (int i = 0; i < n; i++) {
        int d = distToNearestHub(distances, hubs, i);
        if (d > farthestDist) {
          farthestDist = d;
          farthest = i;
        }
      }
      if (farthest == -1 || farthestDist == 0) break;
      hubs.add(farthest);
    }

    return hubs;
  }

  private static int distToNearestHub(List<List<Integer>> distances, List<Integer> hubs, int node) {
    int min = Integer.MAX_VALUE;
    for (int h : hubs) min = Math.min(min, distances.get(node).get(h));
    return min;
  }

  private static double[] tradeHubMetrics(List<List<Integer>> distances, List<Integer> hubs, int n) {
    long sum = 0;
    int max = 0;
    for (int i = 0; i < n; i++) {
      int d = distToNearestHub(distances, hubs, i);
      sum += d;
      max = Math.max(max, d);
    }
    return new double[]{(double) sum / n, max};
  }

  public static GeneratedGalaxy generateGalaxy(int systemCount, Rng rng) {
    List<Point> positions = poissonDiskPositions(systemCount, rng);
    double gatewayRange = pickGatewayRange(positions);
    List<Set<Integer>> adjacency = buildAdjacency(positions, gatewayRange);
    List<List<Integer>> neighborLists = new ArrayList<>();
    for (Set<Integer> set : adjacency) neighborLists.add(new ArrayList<>(set));
    List<List<Integer>> distances = bfsAllPairs(neighborLists);
    List<Integer> tradeHubIndices = placeTradeHubs(distances);

    int centralIndex = 0;
    double bestCentrality = Double.POSITIVE_INFINITY;
    Point center = new Point(0.5, 0.5);
    for (int i = 0; i < systemCount; i++) {
      double centrality = distance(positions.get(i), center);
      if (centrality < bestCentrality) {
        bestCentrality = centrality;
        centralIndex = i;
      }
    }

    return new GeneratedGalaxy(positions, neighborLists, gatewayRange, centralIndex, tradeHubIndices);
  }
}
