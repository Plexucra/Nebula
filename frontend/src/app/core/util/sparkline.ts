export interface SparklineGeometry {
  path: string;
  lastX: number;
  lastY: number;
}

/** Reines Geometrie-Utility für kleine Trendlinien (keine Chart-Library nötig). */
export function sparklineGeometry(values: number[], width = 100, height = 32, pad = 3): SparklineGeometry {
  if (values.length === 0) return { path: '', lastX: width - pad, lastY: height / 2 };
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const n = values.length;
  const step = n > 1 ? (width - pad * 2) / (n - 1) : 0;
  const points = values.map((v, i) => ({
    x: pad + i * step,
    y: pad + (1 - (v - min) / range) * (height - pad * 2),
  }));
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ');
  const last = points[points.length - 1];
  return { path, lastX: last.x, lastY: last.y };
}

export type TrendDirection = 'up' | 'down' | 'flat';

/** Vergleicht den letzten Wert mit einem Punkt ein paar Messungen zuvor. */
export function trendDirection(values: number[], lookback = 6): TrendDirection {
  if (values.length < 2) return 'flat';
  const from = values[Math.max(0, values.length - 1 - lookback)];
  const to = values[values.length - 1];
  const diff = to - from;
  if (Math.abs(diff) < Math.max(0.01, Math.abs(from) * 0.001)) return 'flat';
  return diff > 0 ? 'up' : 'down';
}
