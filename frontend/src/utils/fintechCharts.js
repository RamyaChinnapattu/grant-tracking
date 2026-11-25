/**
 * Pure geometry and normalization helpers for FinTech SVG charts and meters.
 */

export const FINTECH_CHART_COLORS = {
  indigo: "#5146D8",
  blue: "#3478F6",
  teal: "#12B8A6",
  emerald: "#16A879",
  amber: "#F59E0B",
  rose: "#E1495B",
  slate: "#94A3B8",
  navy: "#101B35",
};

/**
 * Computes SVG stroke-dasharray / stroke-dashoffset slices for a DonutChart.
 *
 * @param {Array<{label: string, value: number, color?: string, hint?: string}>} segments
 * @param {number} radius
 * @returns {{ total: number, circumference: number, slices: Array }}
 */
export function buildDonutSlices(segments = [], radius = 64) {
  const circumference = 2 * Math.PI * radius;
  const clean = (Array.isArray(segments) ? segments : [])
    .map((item, idx) => ({
      ...item,
      value: Math.max(0, Number(item?.value) || 0),
      color:
        item?.color ||
        Object.values(FINTECH_CHART_COLORS)[
          idx % Object.values(FINTECH_CHART_COLORS).length
        ],
    }))
    .filter((item) => item.value > 0);

  const total = clean.reduce((sum, item) => sum + item.value, 0);
  if (total <= 0) {
    return { total: 0, circumference, slices: [] };
  }

  let cumulativeOffset = 0;
  const slices = clean.map((item) => {
    const ratio = item.value / total;
    const arcLength = ratio * circumference;
    const slice = {
      ...item,
      ratio,
      percentage: Math.round(ratio * 1000) / 10,
      arcLength,
      dashArray: `${arcLength} ${circumference}`,
      dashOffset: cumulativeOffset === 0 ? 0 : -cumulativeOffset,
    };
    cumulativeOffset += arcLength;
    return slice;
  });

  return { total, circumference, slices };
}

/**
 * Safely computes clamped percentage between 0 and 100.
 */
export function clampPercent(value, max = 100) {
  const num = Number(value) || 0;
  const denom = Number(max) || 0;
  if (denom <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((num / denom) * 100)));
}
