<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { heatmapPixels, isValidHeatmap, strongest } from '../../prediction/heatmap'

// The scan of one report with an optional heatmap overlay, zoom (buttons, Ctrl + scroll, double click, + / -
// keys) and pan (drag, arrow keys). heatmapState is 'loading', 'ready', 'unavailable' or 'error'.
const props = defineProps({
  src: { type: String, required: true },
  alt: { type: String, required: true },
  heatmap: { type: Object, default: null },
  heatmapState: { type: String, default: 'loading' },
})

const MIN_SCALE = 1
const MAX_SCALE = 8
const STEP = 1.5
const PAN_STEP = 60
// The overlay is drawn at this many pixels per heatmap cell and stretched over the scan by the browser.
const CELL_PIXELS = 16

const overlay = ref('original')
const opacity = ref(0.6)
const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const imageFailed = ref(false)
const viewport = ref(null)
const canvas = ref(null)

const heatmapReady = computed(() => props.heatmapState === 'ready' && isValidHeatmap(props.heatmap))
const nothingFlagged = computed(() => heatmapReady.value && strongest(props.heatmap) <= 0)
const showHeatmap = computed(() => overlay.value === 'heatmap' && heatmapReady.value)
const zoomPercent = computed(() => Math.round(scale.value * 100))
const stageStyle = computed(() => ({ transform: `translate(${tx.value}px, ${ty.value}px) scale(${scale.value})` }))

function draw() {
  const el = canvas.value
  if (!el || !heatmapReady.value) return
  const { rows, cols } = props.heatmap
  el.width = cols * CELL_PIXELS
  el.height = rows * CELL_PIXELS
  const ctx = el.getContext?.('2d')
  if (!ctx) return
  ctx.putImageData(new ImageData(heatmapPixels(props.heatmap, el.width, el.height), el.width, el.height), 0, 0)
}

watch([() => props.heatmap, showHeatmap], () => nextTick(draw))

// A new report starts at the original image, unzoomed.
watch(
  () => props.src,
  () => {
    overlay.value = 'original'
    imageFailed.value = false
    reset()
  },
)

function size() {
  const el = viewport.value
  return { width: el?.clientWidth ?? 0, height: el?.clientHeight ?? 0 }
}

/** Keeps the scaled image covering the viewport, so it cannot be dragged out of sight. */
function clamp() {
  const { width, height } = size()
  tx.value = Math.min(0, Math.max(width * (1 - scale.value), tx.value))
  ty.value = Math.min(0, Math.max(height * (1 - scale.value), ty.value))
}

/** Zooms to `next`, keeping the point (x, y) of the viewport where it is (the centre by default). */
function zoomTo(next, x, y) {
  const { width, height } = size()
  const cx = x ?? width / 2
  const cy = y ?? height / 2
  const target = Math.min(MAX_SCALE, Math.max(MIN_SCALE, next))
  const ratio = target / scale.value
  tx.value = cx - (cx - tx.value) * ratio
  ty.value = cy - (cy - ty.value) * ratio
  scale.value = target
  clamp()
}

const zoomIn = () => zoomTo(scale.value * STEP)
const zoomOut = () => zoomTo(scale.value / STEP)

function reset() {
  scale.value = 1
  tx.value = 0
  ty.value = 0
}

function pan(dx, dy) {
  tx.value += dx
  ty.value += dy
  clamp()
}

function localPoint(event) {
  const rect = viewport.value.getBoundingClientRect()
  return [event.clientX - rect.left, event.clientY - rect.top]
}

// Plain scrolling keeps scrolling the page; Ctrl + scroll (and trackpad pinch, which the browser reports that
// way) zooms the scan.
function onWheel(event) {
  if (!event.ctrlKey && !event.metaKey) return
  event.preventDefault()
  zoomTo(scale.value * (event.deltaY < 0 ? 1.15 : 1 / 1.15), ...localPoint(event))
}

function onDoubleClick(event) {
  if (scale.value > MIN_SCALE) reset()
  else zoomTo(2, ...localPoint(event))
}

let drag = null
function onPointerDown(event) {
  if (scale.value <= MIN_SCALE || event.button !== 0) return
  drag = { id: event.pointerId, x: event.clientX, y: event.clientY }
  viewport.value.setPointerCapture?.(event.pointerId)
}
function onPointerMove(event) {
  if (!drag || drag.id !== event.pointerId) return
  pan(event.clientX - drag.x, event.clientY - drag.y)
  drag.x = event.clientX
  drag.y = event.clientY
}
function onPointerUp(event) {
  if (drag?.id === event.pointerId) drag = null
}

const KEYS = {
  '+': zoomIn,
  '=': zoomIn,
  '-': zoomOut,
  _: zoomOut,
  0: reset,
  ArrowLeft: () => pan(PAN_STEP, 0),
  ArrowRight: () => pan(-PAN_STEP, 0),
  ArrowUp: () => pan(0, PAN_STEP),
  ArrowDown: () => pan(0, -PAN_STEP),
}
function onKeydown(event) {
  const action = KEYS[event.key]
  if (!action) return
  // Arrow keys scroll the page as usual until the scan is zoomed in.
  if (event.key.startsWith('Arrow') && scale.value <= MIN_SCALE) return
  event.preventDefault()
  action()
}

const heatmapNote = computed(() => {
  switch (props.heatmapState) {
    case 'loading':
      return 'Preparing the heatmap…'
    case 'unavailable':
      return 'A heatmap is not available for this report: the AI model on the server cannot produce one.'
    case 'error':
      return 'The heatmap could not be loaded. Reload the page to try again.'
    default:
      return heatmapReady.value ? '' : 'The heatmap for this report is not valid and is not shown.'
  }
})
</script>

<template>
  <div class="viewer card">
    <div class="toolbar">
      <div class="segmented" role="group" aria-label="Image overlay">
        <button type="button" :aria-pressed="overlay === 'original'" @click="overlay = 'original'">Original</button>
        <button type="button" :aria-pressed="overlay === 'heatmap'" :disabled="!heatmapReady" @click="overlay = 'heatmap'">
          Heatmap
        </button>
      </div>
      <div class="zoom" role="group" aria-label="Zoom">
        <button type="button" class="icon-btn" aria-label="Zoom out" :disabled="scale <= 1" @click="zoomOut">−</button>
        <span class="zoom-level" aria-live="polite">{{ zoomPercent }}%</span>
        <button type="button" class="icon-btn" aria-label="Zoom in" :disabled="scale >= 8" @click="zoomIn">+</button>
        <button type="button" class="reset" :disabled="scale === 1" @click="reset">Reset</button>
      </div>
    </div>

    <div class="frame">
      <p v-if="imageFailed" class="image-error">The scan image could not be loaded.</p>
      <div
        v-else
        ref="viewport"
        class="viewport"
        :class="{ zoomed: scale > 1 }"
        tabindex="0"
        role="img"
        :aria-label="`${alt}${showHeatmap ? ', with heatmap overlay' : ''}. Zoom ${zoomPercent}%.`"
        @wheel="onWheel"
        @dblclick="onDoubleClick"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @keydown="onKeydown"
      >
        <div class="stage" :style="stageStyle">
          <img :src="src" alt="" draggable="false" @error="imageFailed = true" />
          <canvas v-show="showHeatmap" ref="canvas" class="overlay" :style="{ opacity }" aria-hidden="true"></canvas>
        </div>
      </div>
    </div>

    <p class="hint">Drag to move when zoomed · Ctrl + scroll or double-click to zoom · keys + − 0 and arrows</p>

    <div v-if="showHeatmap" class="heatmap-panel">
      <label class="opacity">
        <span>Heatmap opacity</span>
        <input v-model.number="opacity" type="range" min="0.1" max="1" step="0.05" />
        <span class="value">{{ Math.round(opacity * 100) }}%</span>
      </label>
      <div class="legend">
        <span class="legend-bar" aria-hidden="true"></span>
        <span class="legend-labels"><span>Weaker</span><span>Stronger</span></span>
      </div>
      <p v-if="nothingFlagged" class="small">No region of this image pushed the AI towards "malignant".</p>
      <p class="small">
        Coloured areas are where the AI found the most evidence for "malignant", compared within this image only. Even a
        benign result usually has some coloured areas. The heatmap shows what the AI looked at, not where a tumour is.
        It comes from the ResNet50 part of the AI (model {{ heatmap.modelVersion }}).
      </p>
    </div>
    <p v-else-if="heatmapNote" class="small muted">{{ heatmapNote }}</p>
  </div>
</template>

<style scoped>
.viewer { display: grid; gap: 0.75rem; padding: 1rem; }
.toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.6rem; }
.segmented { display: inline-flex; padding: 3px; border-radius: 999px; background: var(--pink-100); }
.segmented button {
  padding: 0.4rem 1rem; border: 0; border-radius: 999px; background: transparent; color: var(--purple-900);
  font: 600 0.9rem 'Poppins', sans-serif; cursor: pointer;
}
.segmented button[aria-pressed='true'] { background: #fff; color: var(--pink-600); box-shadow: 0 2px 6px rgba(216, 27, 106, 0.18); }
.segmented button:disabled { opacity: 0.45; cursor: not-allowed; }
.zoom { display: inline-flex; align-items: center; gap: 0.35rem; }
.icon-btn, .reset {
  min-width: 34px; height: 34px; padding: 0 0.6rem; border: 1.5px solid var(--border); border-radius: 10px;
  background: #fff; color: var(--purple-900); font: 600 1rem 'Poppins', sans-serif; cursor: pointer;
}
.reset { font-size: 0.85rem; }
.icon-btn:disabled, .reset:disabled { opacity: 0.45; cursor: not-allowed; }
.zoom-level { min-width: 3.2rem; text-align: center; font-weight: 600; font-variant-numeric: tabular-nums; color: var(--purple-900); }

.frame { display: flex; justify-content: center; border-radius: 12px; background: #000; overflow: hidden; }
.viewport { position: relative; max-width: 100%; overflow: hidden; line-height: 0; cursor: zoom-in; touch-action: pan-y; }
.viewport.zoomed { cursor: grab; touch-action: none; }
.viewport.zoomed:active { cursor: grabbing; }
.viewport:focus-visible { outline: 3px solid var(--pink-500); outline-offset: -3px; }
.stage { position: relative; transform-origin: 0 0; will-change: transform; }
.stage img { display: block; max-width: 100%; max-height: 70vh; user-select: none; }
.overlay { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.image-error { margin: 0; padding: 3rem 1rem; color: #fff; line-height: 1.5; }

.hint { margin: 0; font-size: 0.8rem; color: var(--muted); }
.heatmap-panel { display: grid; gap: 0.5rem; }
.opacity { display: flex; align-items: center; gap: 0.6rem; font-size: 0.88rem; font-weight: 600; color: var(--purple-900); }
.opacity input { flex: 1; accent-color: var(--pink-600); }
.opacity .value { min-width: 2.8rem; text-align: right; font-variant-numeric: tabular-nums; }
.legend { display: grid; gap: 0.2rem; }
.legend-bar { height: 10px; border-radius: 5px; background: linear-gradient(90deg, #00f, #0ff, #0f0, #ff0, #f00); }
.legend-labels { display: flex; justify-content: space-between; font-size: 0.78rem; color: var(--muted); }
.small { margin: 0; font-size: 0.85rem; line-height: 1.5; }
</style>
