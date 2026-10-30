<script setup>
import { computed } from 'vue'

// Where a report stands: the scan's processing and whether a doctor has been asked to look at it. The site has
// no "reviewed" step yet, so sharing is the furthest a report can get: "Awaiting doctor review".
const props = defineProps({
  report: { type: Object, required: true },
  // The owner sees who it is shared with; a doctor opening the report only knows it was shared with them.
  isOwner: { type: Boolean, default: false },
  shares: { type: Array, default: () => [] },
})

const scan = computed(() => {
  if (props.report.demoMode) {
    return { tone: 'warn', label: 'Analysed with a demo model', detail: 'This result is not valid. Do not use it.' }
  }
  if (props.report.lowConfidence || props.report.label === 'uncertain') {
    return { tone: 'warn', label: 'Analysis complete: low confidence', detail: 'The AI could not decide clearly.' }
  }
  return { tone: 'ok', label: 'Analysis complete', detail: `Analysed ${new Date(props.report.createdAt).toLocaleString()}` }
})

const review = computed(() => {
  if (!props.isOwner) {
    return { tone: 'pending', label: 'Shared with you for review', detail: 'The patient chose to share this report with you.' }
  }
  if (!props.shares.length) {
    return {
      tone: 'idle',
      label: 'Not shared with a doctor',
      detail: 'Share this report with a doctor below so they can review it.',
    }
  }
  return {
    tone: 'pending',
    label: 'Awaiting doctor review',
    detail: `Shared with ${props.shares.map((d) => d.fullName).join(', ')}.`,
  }
})
</script>

<template>
  <div class="card status">
    <h2>Status</h2>
    <dl>
      <div class="row">
        <dt>Scan</dt>
        <dd>
          <span class="badge" :class="`badge--${scan.tone}`">{{ scan.label }}</span>
          <span class="detail">{{ scan.detail }}</span>
        </dd>
      </div>
      <div class="row">
        <dt>Doctor review</dt>
        <dd>
          <span class="badge" :class="`badge--${review.tone}`">{{ review.label }}</span>
          <span class="detail">{{ review.detail }}</span>
        </dd>
      </div>
    </dl>
  </div>
</template>

<style scoped>
.status h2 { margin: 0 0 0.75rem; font-size: 1.1rem; color: var(--purple-900); }
dl { display: grid; gap: 0.75rem; margin: 0; }
.row { display: grid; grid-template-columns: 7.5rem 1fr; gap: 0.75rem; align-items: start; }
dt { font-weight: 600; color: var(--muted); font-size: 0.9rem; padding-top: 0.2rem; }
dd { display: grid; gap: 0.25rem; margin: 0; }
.badge { width: fit-content; padding: 0.2rem 0.7rem; border-radius: 999px; font-size: 0.85rem; font-weight: 600; }
.badge--ok { background: #edf7ee; color: var(--low); }
.badge--warn { background: var(--warn-bg); color: var(--warn-text); }
.badge--pending { background: #ede7fb; color: #5b3fa8; }
.badge--idle { background: #f1eff5; color: var(--muted); }
.detail { font-size: 0.85rem; color: var(--text); overflow-wrap: anywhere; }
@media (max-width: 480px) { .row { grid-template-columns: 1fr; gap: 0.3rem; } }
</style>
