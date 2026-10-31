<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import AppIcon from '../common/AppIcon.vue'

// Drawn over the mammogram preview to show the AI at work. Purely visual: the points and the sweep do not mark
// real findings (the result and heatmap come later, on the report). Screen readers get the upload status from
// MammogramUpload instead, so this is aria-hidden.
//   ready    corner brackets and "Ready to scan"
//   scanning grid, a sweeping laser line, pulsing sample points and changing step text
//   done     brackets turn green with "Scan complete"
const props = defineProps({
  state: { type: String, default: 'ready' }, // ready | scanning | done
})

const STEPS = ['Detecting breast region', 'Mapping tissue density', 'Analysing tissue patterns', 'Estimating cancer risk']
const step = ref(0)
let timer = null
watch(() => props.state, (state) => {
  clearInterval(timer)
  step.value = 0
  if (state === 'scanning') timer = setInterval(() => (step.value = (step.value + 1) % STEPS.length), 1600)
}, { immediate: true })
onBeforeUnmount(() => clearInterval(timer))

// Where the sample points sit, as % of the image; spread over where breast tissue usually is.
const POINTS = [
  { x: 38, y: 34, d: 0 }, { x: 55, y: 48, d: 0.5 }, { x: 46, y: 62, d: 1 }, { x: 30, y: 52, d: 1.4 },
  { x: 62, y: 66, d: 1.9 }, { x: 40, y: 76, d: 2.4 }, { x: 24, y: 40, d: 2.8 },
]
</script>

<template>
  <div class="scan" :class="state" aria-hidden="true">
    <span class="corner tl"></span><span class="corner tr"></span><span class="corner bl"></span><span class="corner br"></span>

    <template v-if="state === 'scanning'">
      <div class="grid"></div>
      <div class="tint"></div>
      <div class="beam"><span class="beam-line"></span></div>
      <span v-for="(p, i) in POINTS" :key="i" class="point" :style="{ left: `${p.x}%`, top: `${p.y}%`, animationDelay: `${p.d}s` }"></span>
      <div class="label">
        <span class="dot"></span>
        <span class="label-text">
          <strong>AI scanning</strong>
          <Transition name="step" mode="out-in"><span :key="step">{{ STEPS[step] }}…</span></Transition>
        </span>
      </div>
    </template>

    <div v-else class="tag">
      <AppIcon :name="state === 'done' ? 'check-circle' : 'microscope'" :size="14" />
      {{ state === 'done' ? 'Scan complete' : 'Ready to scan' }}
    </div>
  </div>
</template>

<style scoped>
.scan { position: absolute; inset: 0; pointer-events: none; overflow: hidden; --c: #ff4f9a; }
.scan.done { --c: #4ade80; }

/* Corner brackets framing the image */
.corner { position: absolute; width: 22px; height: 22px; border: 0 solid var(--c); opacity: 0.9; transition: border-color 0.4s; }
.tl { top: 8px; left: 8px; border-top-width: 3px; border-left-width: 3px; border-top-left-radius: 6px; }
.tr { top: 8px; right: 8px; border-top-width: 3px; border-right-width: 3px; border-top-right-radius: 6px; }
.bl { bottom: 8px; left: 8px; border-bottom-width: 3px; border-left-width: 3px; border-bottom-left-radius: 6px; }
.br { bottom: 8px; right: 8px; border-bottom-width: 3px; border-right-width: 3px; border-bottom-right-radius: 6px; }
.scanning .corner { animation: bracket 1.6s ease-in-out infinite; }
@keyframes bracket { 50% { opacity: 0.35; } }

/* Fine grid and a soft colour wash */
.grid {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(255, 79, 154, 0.14) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 79, 154, 0.14) 1px, transparent 1px);
  background-size: 24px 24px;
  animation: grid-in 0.6s ease-out both;
}
@keyframes grid-in { from { opacity: 0; } }
.tint { position: absolute; inset: 0; background: radial-gradient(ellipse at 45% 55%, rgba(216, 27, 106, 0.16), transparent 70%); }

/* The laser sweep: a bright line with a fading trail, down and back up */
.beam { position: absolute; left: 0; right: 0; top: 0; height: 30%; animation: sweep 2.6s ease-in-out infinite alternate; }
.beam::before {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent, rgba(255, 79, 154, 0.28));
}
.beam-line {
  position: absolute; left: 0; right: 0; bottom: 0; height: 2px; background: #ffd1e6;
  box-shadow: 0 0 6px 2px rgba(255, 79, 154, 0.95), 0 0 18px 6px rgba(216, 27, 106, 0.55);
}
@keyframes sweep { from { transform: translateY(-100%); } to { transform: translateY(333%); } }

/* Sample points: a dot with an expanding ring */
.point {
  position: absolute; width: 8px; height: 8px; margin: -4px 0 0 -4px; border-radius: 50%; background: #ffd1e6;
  box-shadow: 0 0 8px 2px rgba(255, 79, 154, 0.9); opacity: 0; animation: point 3.2s ease-out infinite;
}
.point::after {
  content: ''; position: absolute; inset: -8px; border-radius: 50%; border: 1.5px solid rgba(255, 79, 154, 0.8);
  animation: ring 3.2s ease-out infinite; animation-delay: inherit;
}
@keyframes point { 0% { opacity: 0; transform: scale(0.4); } 15%, 60% { opacity: 1; transform: scale(1); } 100% { opacity: 0; } }
@keyframes ring { 0% { transform: scale(0.3); opacity: 1; } 70%, 100% { transform: scale(1.8); opacity: 0; } }

/* Status label */
.label {
  position: absolute; left: 50%; bottom: 14px; transform: translateX(-50%); display: flex; align-items: center; gap: 0.55rem;
  padding: 0.45rem 0.85rem; border-radius: 999px; white-space: nowrap; line-height: 1.2;
  background: rgba(29, 21, 48, 0.82); border: 1px solid rgba(255, 79, 154, 0.45); backdrop-filter: blur(6px);
  color: #fff; font-size: 0.76rem; box-shadow: 0 6px 18px rgba(0, 0, 0, 0.35);
}
.label-text { display: grid; }
.label-text strong { font-size: 0.7rem; letter-spacing: 0.12em; text-transform: uppercase; color: #ff9cc6; }
.dot { width: 9px; height: 9px; border-radius: 50%; background: var(--c); box-shadow: 0 0 0 0 rgba(255, 79, 154, 0.7); animation: live 1.2s infinite; }
@keyframes live { 70% { box-shadow: 0 0 0 8px rgba(255, 79, 154, 0); } 100% { box-shadow: 0 0 0 0 rgba(255, 79, 154, 0); } }
.step-enter-active, .step-leave-active { transition: opacity 0.25s, transform 0.25s; }
.step-enter-from { opacity: 0; transform: translateY(4px); }
.step-leave-to { opacity: 0; transform: translateY(-4px); }

/* Ready / done tag */
.tag {
  position: absolute; top: 12px; left: 50%; transform: translateX(-50%); display: inline-flex; align-items: center; gap: 0.35rem;
  padding: 0.28rem 0.7rem; border-radius: 999px; line-height: 1.2; white-space: nowrap;
  background: rgba(29, 21, 48, 0.78); border: 1px solid color-mix(in srgb, var(--c) 55%, transparent);
  color: #fff; font-size: 0.72rem; font-weight: 600;
}
.tag :deep(svg) { color: var(--c); }
.done .tag { animation: pop 0.4s ease-out; }
@keyframes pop { from { transform: translateX(-50%) scale(0.8); opacity: 0; } }

@media (prefers-reduced-motion: reduce) {
  .beam, .point, .point::after, .corner, .dot, .grid { animation: none; }
  .beam { top: 50%; transform: none; }
  .point { opacity: 1; }
}
</style>
