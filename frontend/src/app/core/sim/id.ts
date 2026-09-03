let counter = 0;

/** Simpler, sortierbarer ID-Generator für die In-Memory-Simulation. */
export function nextId(prefix: string): string {
  counter += 1;
  return `${prefix}_${counter.toString(36)}`;
}
