import { Id } from '../models';

export interface Route { a: Id; b: Id }

function adjacency(routes: Route[]): Map<Id, Id[]> {
  const map = new Map<Id, Id[]>();
  for (const r of routes) {
    (map.get(r.a) ?? map.set(r.a, []).get(r.a)!).push(r.b);
    (map.get(r.b) ?? map.set(r.b, []).get(r.b)!).push(r.a);
  }
  return map;
}

/** Kürzeste Sprunganzahl (Gateway-Hops) von `from` zu jedem erreichbaren Knoten. */
export function bfsHops(routes: Route[], from: Id): Map<Id, number> {
  const adj = adjacency(routes);
  const dist = new Map<Id, number>([[from, 0]]);
  const queue = [from];
  let head = 0;
  while (head < queue.length) {
    const cur = queue[head++];
    for (const nb of adj.get(cur) ?? []) {
      if (!dist.has(nb)) {
        dist.set(nb, dist.get(cur)! + 1);
        queue.push(nb);
      }
    }
  }
  return dist;
}

/**
 * Kürzester Pfad von `from` zu `to` als geordnete Liste der Zwischen-/Zielsysteme
 * (OHNE `from` selbst, MIT `to` als letztem Eintrag) – Grundlage für
 * hop-für-hop abgearbeitete Flüge (`Fleet.pendingHops`, siehe
 * `simulated-game-api.service.ts`, `moveFleet`). `null`, wenn `to` von
 * `from` aus nicht erreichbar ist; leeres Array kann nicht vorkommen, da
 * `to === from` von den Aufrufern vorher ausgeschlossen wird.
 */
export function bfsPath(routes: Route[], from: Id, to: Id): Id[] | null {
  const adj = adjacency(routes);
  const prev = new Map<Id, Id>();
  const visited = new Set<Id>([from]);
  const queue = [from];
  let head = 0;
  while (head < queue.length) {
    const cur = queue[head++];
    if (cur === to) break;
    for (const nb of adj.get(cur) ?? []) {
      if (!visited.has(nb)) {
        visited.add(nb);
        prev.set(nb, cur);
        queue.push(nb);
      }
    }
  }
  if (!visited.has(to)) return null;
  const path: Id[] = [];
  let cur = to;
  while (cur !== from) {
    path.unshift(cur);
    cur = prev.get(cur)!;
  }
  return path;
}

/** Nächstgelegener Knoten aus `candidates` (Sprunganzahl), oder null wenn keiner erreichbar ist. */
export function nearestByHops(routes: Route[], from: Id, candidates: Id[]): { id: Id; hops: number } | null {
  const dist = bfsHops(routes, from);
  let best: { id: Id; hops: number } | null = null;
  for (const c of candidates) {
    const d = dist.get(c);
    if (d === undefined) continue;
    if (!best || d < best.hops) best = { id: c, hops: d };
  }
  return best;
}
