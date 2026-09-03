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
