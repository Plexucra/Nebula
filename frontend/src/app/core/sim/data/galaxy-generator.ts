import { Rng } from '../rng';

/**
 * Prozedurale Erzeugung der Galaxie-Topologie (Gateway-Netz + sektorale
 * Handelsstationen), rein geometrisch/graphbasiert – ohne Kenntnis von
 * Spielentitäten. `world-seed.ts` übersetzt das Ergebnis in `System`-,
 * `Gateway`- und `TradePost`-artige Datensätze.
 *
 * Designvorgaben (siehe Konzeption/04_..., §1 und Mechanik/08_..., §1):
 * - Jedes Gateway hat dieselbe feste Reichweite: Zwei Systeme sind genau
 *   dann verbunden, wenn ihr geometrischer Abstand ≤ dieser einen,
 *   galaxieweit einheitlichen Reichweite liegt – keine Verbindung "zum
 *   sechstnächsten System" o. Ä., sondern eine echte Entfernungsgrenze.
 * - Dass daraus im Schnitt 3-6 Nachbarn pro System entstehen, ist eine
 *   FOLGE der Systemplatzierung (annähernd gleichmäßiger
 *   Mindestabstand zwischen benachbarten Systemen, siehe
 *   `poissonDiskPositions`), nicht das primäre Auswahlkriterium.
 * - Der gesamte Graph ist zusammenhängend (kein isoliertes System).
 * - Sektorale Handelsstationen (Konzeption/05_..., §5) werden so
 *   platziert, dass jedes System im Schnitt ca. 2, maximal 3
 *   Gateway-Sprünge von der nächsten Handelsstation entfernt liegt.
 */

const MARGIN = 0.06;
const TARGET_AVG_DEGREE = 4.5;
const MIN_DEGREE = 3;
const MAX_DEGREE = 6;
const TARGET_AVG_HUB_HOPS = 2;
const MAX_HUB_HOPS = 3;
const MAX_TRADE_HUBS = 8;

export interface GeneratedGalaxy {
  positions: { x: number; y: number }[];
  /** Adjazenzliste (Nachbar-Indizes) je Systemknoten – die Gateway-Routen. */
  neighbors: number[][];
  /** Einheitliche Gateway-Reichweite, mit der die Kanten erzeugt wurden. */
  gatewayRange: number;
  /** Index des am zentralsten gelegenen Knotens – Vorschlag für das Heimatsystem. */
  centralIndex: number;
  /** Indizes der Systeme mit sektoraler Handelsstation. */
  tradeHubIndices: number[];
}

type Point = { x: number; y: number };

function distance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

/**
 * Platziert `count` Systeme mit einem angestrebten Mindestabstand
 * zueinander (Poisson-Disk-artiges Sampling per Rejection-Verfahren –
 * für die hier relevanten Größenordnungen von ein paar Dutzend Systemen
 * reicht das simple O(n²)-Verfahren völlig aus). Das Ergebnis ist eine
 * annähernd gleichmäßige Verteilung ohne Ballungen oder große Lücken –
 * genau die Voraussetzung dafür, dass eine einzige, feste Gateway-
 * Reichweite überall eine ähnliche Nachbarnzahl erzeugt.
 */
function poissonDiskPositions(count: number, rng: Rng): { positions: Point[]; minDist: number } {
  const usableSide = 1 - 2 * MARGIN;
  let minDist = 0.82 * Math.sqrt((usableSide * usableSide) / count);
  const maxOuterAttempts = 4;
  const attemptsPerPoint = 250;

  let best: { positions: Point[]; fallbacks: number; minDist: number } | null = null;

  for (let outer = 0; outer < maxOuterAttempts; outer++) {
    const positions: Point[] = [];
    let fallbacks = 0;
    for (let i = 0; i < count; i++) {
      let placed: Point | null = null;
      let bestCandidate: Point | null = null;
      let bestCandidateMinDist = -1;
      for (let attempt = 0; attempt < attemptsPerPoint; attempt++) {
        const candidate: Point = {
          x: MARGIN + rng() * usableSide,
          y: MARGIN + rng() * usableSide,
        };
        const nearest = positions.reduce((min, p) => Math.min(min, distance(candidate, p)), Infinity);
        if (nearest >= minDist) {
          placed = candidate;
          break;
        }
        if (nearest > bestCandidateMinDist) {
          bestCandidateMinDist = nearest;
          bestCandidate = candidate;
        }
      }
      if (placed) {
        positions.push(placed);
      } else {
        // kein Kandidat hat den Mindestabstand voll erfüllt – bestmöglichen nehmen.
        positions.push(bestCandidate ?? { x: 0.5, y: 0.5 });
        fallbacks++;
      }
    }
    if (!best || fallbacks < best.fallbacks) best = { positions, fallbacks, minDist };
    if (fallbacks === 0) break;
    minDist *= 0.85;
  }

  return { positions: best!.positions, minDist: best!.minDist };
}

/** Anzahl Systeme, die bei gegebener Reichweite `r` erreichbar wären, im Schnitt über alle Systeme. */
function averageDegreeAtRange(positions: Point[], r: number): number {
  const n = positions.length;
  let total = 0;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      if (distance(positions[i], positions[j]) <= r) total += 2;
    }
  }
  return total / n;
}

/** Bestimmt die einheitliche Gateway-Reichweite per Bisektion auf die Ziel-Durchschnittsnachbarnzahl. */
function pickGatewayRange(positions: Point[]): number {
  let lo = 0;
  let hi = Math.SQRT2; // größtmögliche Distanz im Einheitsquadrat
  for (let iter = 0; iter < 40; iter++) {
    const mid = (lo + hi) / 2;
    if (averageDegreeAtRange(positions, mid) < TARGET_AVG_DEGREE) lo = mid; else hi = mid;
  }
  return (lo + hi) / 2;
}

function buildAdjacency(positions: Point[], range: number): number[][] {
  const n = positions.length;
  const adjacency: Set<number>[] = Array.from({ length: n }, () => new Set<number>());
  const distTo = (i: number, j: number) => distance(positions[i], positions[j]);
  const neighborsByDistance = positions.map((p, i) =>
    positions.map((_, j) => j).filter(j => j !== i).sort((a, b) => distTo(i, a) - distTo(i, b)),
  );

  // 1) Kernregel: Gateway-Reichweite ist für alle Systeme identisch.
  for (let i = 0; i < n; i++) {
    for (const j of neighborsByDistance[i]) {
      if (j <= i) continue;
      if (distTo(i, j) <= range) {
        adjacency[i].add(j);
        adjacency[j].add(i);
      }
    }
  }

  // 2) Obergrenze durchsetzen (seltener Ausreißer in dichteren Regionen):
  // nur die nächsten MAX_DEGREE Verbindungen innerhalb der Reichweite behalten.
  for (let i = 0; i < n; i++) {
    if (adjacency[i].size <= MAX_DEGREE) continue;
    const kept = neighborsByDistance[i].filter(j => adjacency[i].has(j)).slice(0, MAX_DEGREE);
    const keepSet = new Set(kept);
    for (const j of [...adjacency[i]]) {
      if (!keepSet.has(j)) {
        adjacency[i].delete(j);
        adjacency[j].delete(i);
      }
    }
  }

  // 3) Untergrenze durchsetzen (seltener Ausreißer in spärlicheren Regionen):
  // fehlende Verbindungen zu den nächstgelegenen Systemen ergänzen, auch
  // wenn das im Einzelfall die reguläre Reichweite geringfügig überschreitet.
  for (const i of [...Array(n).keys()]) {
    for (const j of neighborsByDistance[i]) {
      if (adjacency[i].size >= MIN_DEGREE) break;
      if (adjacency[j].size >= MAX_DEGREE) continue;
      adjacency[i].add(j);
      adjacency[j].add(i);
    }
  }

  // 4) Zusammenhang des Gesamtgraphen erzwingen.
  ensureConnected(n, adjacency, positions);

  return adjacency.map(set => [...set]);
}

function ensureConnected(n: number, adjacency: Set<number>[], positions: Point[]): void {
  const componentOf = (): number[] => {
    const comp = new Array(n).fill(-1);
    let compId = 0;
    for (let start = 0; start < n; start++) {
      if (comp[start] !== -1) continue;
      const queue = [start];
      comp[start] = compId;
      while (queue.length) {
        const cur = queue.pop()!;
        for (const nb of adjacency[cur]) {
          if (comp[nb] === -1) { comp[nb] = compId; queue.push(nb); }
        }
      }
      compId++;
    }
    return comp;
  };

  for (let guard = 0; guard < n; guard++) {
    const comp = componentOf();
    if (new Set(comp).size <= 1) return;
    let best: { i: number; j: number; d: number } | null = null;
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        if (comp[i] === comp[j]) continue;
        const d = distance(positions[i], positions[j]);
        if (!best || d < best.d) best = { i, j, d };
      }
    }
    if (best) { adjacency[best.i].add(best.j); adjacency[best.j].add(best.i); }
  }
}

function bfsAllPairs(neighbors: number[][]): number[][] {
  const n = neighbors.length;
  return neighbors.map((_, start) => {
    const dist = new Array(n).fill(Infinity);
    dist[start] = 0;
    const queue = [start];
    let head = 0;
    while (head < queue.length) {
      const cur = queue[head++];
      for (const nb of neighbors[cur]) {
        if (dist[nb] === Infinity) { dist[nb] = dist[cur] + 1; queue.push(nb); }
      }
    }
    return dist;
  });
}

function placeTradeHubs(distances: number[][]): number[] {
  const n = distances.length;
  let center = 0;
  let bestEcc = Infinity;
  for (let i = 0; i < n; i++) {
    const ecc = Math.max(...distances[i]);
    if (ecc < bestEcc) { bestEcc = ecc; center = i; }
  }

  const hubs = [center];
  const distToNearestHub = (node: number): number => Math.min(...hubs.map(h => distances[node][h]));
  const metrics = (): { avg: number; max: number } => {
    const ds = distances.map((_, i) => distToNearestHub(i));
    return { avg: ds.reduce((a, b) => a + b, 0) / n, max: Math.max(...ds) };
  };

  while (hubs.length < MAX_TRADE_HUBS) {
    const { avg, max } = metrics();
    if (avg <= TARGET_AVG_HUB_HOPS && max <= MAX_HUB_HOPS) break;
    let farthest = -1;
    let farthestDist = -1;
    for (let i = 0; i < n; i++) {
      const d = distToNearestHub(i);
      if (d > farthestDist) { farthestDist = d; farthest = i; }
    }
    if (farthest === -1 || farthestDist === 0) break;
    hubs.push(farthest);
  }

  return hubs;
}

export function generateGalaxy(systemCount: number, rng: Rng): GeneratedGalaxy {
  const { positions } = poissonDiskPositions(systemCount, rng);
  const gatewayRange = pickGatewayRange(positions);
  const neighborLists = buildAdjacency(positions, gatewayRange);
  const distances = bfsAllPairs(neighborLists);
  const tradeHubIndices = placeTradeHubs(distances);

  let centralIndex = 0;
  let bestCentrality = Infinity;
  for (let i = 0; i < systemCount; i++) {
    const centrality = distance(positions[i], { x: 0.5, y: 0.5 });
    if (centrality < bestCentrality) { bestCentrality = centrality; centralIndex = i; }
  }

  return { positions, neighbors: neighborLists, gatewayRange, centralIndex, tradeHubIndices };
}
