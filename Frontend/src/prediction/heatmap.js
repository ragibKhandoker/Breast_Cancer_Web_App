// Turns a report's heatmap (from GET /api/predictions/{id}/heatmap) into pixels for a canvas laid over the scan.
// Each value is one region's malignant-minus-benign logit (see Backend model/Heatmap.java): only positive
// regions pushed the result towards malignant, so only they are coloured, scaled to the strongest one in this
// image. Weak regions stay transparent so the scan shows through.

// Below this share of the strongest region nothing is drawn.
export const MIN_INTENSITY = 0.15

// Blue -> cyan -> green -> yellow -> red, the usual heatmap scale.
const STOPS = [
  [0, [0, 0, 255]],
  [0.25, [0, 255, 255]],
  [0.5, [0, 255, 0]],
  [0.75, [255, 255, 0]],
  [1, [255, 0, 0]],
]

/** The largest positive value, or 0 when no region pushed the result towards malignant. */
export function strongest(heatmap) {
  return heatmap.values.reduce((max, v) => (v > max ? v : max), 0)
}

/** True when the heatmap has the shape the backend promises. */
export function isValidHeatmap(heatmap) {
  return (
    !!heatmap &&
    Number.isInteger(heatmap.rows) &&
    Number.isInteger(heatmap.cols) &&
    heatmap.rows > 0 &&
    heatmap.cols > 0 &&
    Array.isArray(heatmap.values) &&
    heatmap.values.length === heatmap.rows * heatmap.cols &&
    heatmap.values.every(Number.isFinite)
  )
}

/** [r, g, b] for an intensity in [0, 1]. */
export function colourAt(t) {
  const x = Math.min(1, Math.max(0, t))
  for (let i = 1; i < STOPS.length; i++) {
    const [end, to] = STOPS[i]
    if (x <= end) {
      const [start, from] = STOPS[i - 1]
      const f = (x - start) / (end - start)
      return from.map((c, k) => Math.round(c + (to[k] - c) * f))
    }
  }
  return STOPS[STOPS.length - 1][1]
}

/**
 * RGBA pixels (width x height) for the heatmap, smoothed between region centres (bilinear) so the overlay has no
 * blocky grid. The canvas is then stretched over the whole scan, because the model looked at the whole image.
 */
export function heatmapPixels(heatmap, width, height) {
  const { rows, cols, values } = heatmap
  const max = strongest(heatmap)
  const out = new Uint8ClampedArray(width * height * 4)
  if (max <= 0) return out
  const at = (r, c) => Math.max(0, values[r * cols + c]) / max

  for (let y = 0; y < height; y++) {
    const gy = Math.min(rows - 1, Math.max(0, ((y + 0.5) / height) * rows - 0.5))
    const r0 = Math.floor(gy)
    const r1 = Math.min(rows - 1, r0 + 1)
    const fy = gy - r0
    for (let x = 0; x < width; x++) {
      const gx = Math.min(cols - 1, Math.max(0, ((x + 0.5) / width) * cols - 0.5))
      const c0 = Math.floor(gx)
      const c1 = Math.min(cols - 1, c0 + 1)
      const fx = gx - c0
      const t =
        at(r0, c0) * (1 - fx) * (1 - fy) + at(r0, c1) * fx * (1 - fy) + at(r1, c0) * (1 - fx) * fy + at(r1, c1) * fx * fy
      if (t < MIN_INTENSITY) continue
      const [red, green, blue] = colourAt(t)
      const i = (y * width + x) * 4
      out[i] = red
      out[i + 1] = green
      out[i + 2] = blue
      out[i + 3] = Math.round(255 * (0.35 + 0.5 * t))
    }
  }
  return out
}
