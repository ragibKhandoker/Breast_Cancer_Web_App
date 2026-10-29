<script setup>
import { computed } from 'vue'
import { VIEWS } from '../../prediction/mammogramFile'
import { isMalignant as isMalignantReport } from '../../prediction/verdict'

// One analysis result: Benign or Malignant, whichever of the two percentages is larger.
const props = defineProps({ prediction: { type: Object, required: true } })

const malignantPercent = computed(() => Math.round(props.prediction.malignantProbability * 1000) / 10)
const benignPercent = computed(() => Math.round((100 - malignantPercent.value) * 10) / 10)
const isMalignant = computed(() => isMalignantReport(props.prediction))
const viewLabel = computed(() => VIEWS.find((v) => v.value === props.prediction.view)?.label)
</script>

<template>
  <div class="card result">
    <p v-if="prediction.demoMode" class="warning">
      Demo mode: the model running on this server has not been trained and tested on mammograms yet, so this result
      means nothing. Do not use it.
    </p>
    <p v-if="viewLabel" class="meta view">View: {{ viewLabel }}</p>

    <p class="verdict" :class="isMalignant ? 'high' : 'low'" role="status">{{ isMalignant ? 'Malignant' : 'Benign' }}</p>

    <div class="split">
      <div class="side" :class="{ larger: !isMalignant }">
        <span class="side-name">Benign</span>
        <span class="side-value low">{{ benignPercent }}%</span>
      </div>
      <div class="side" :class="{ larger: isMalignant }">
        <span class="side-name">Malignant</span>
        <span class="side-value high">{{ malignantPercent }}%</span>
      </div>
    </div>
    <div class="bar" role="img" :aria-label="`Benign ${benignPercent}%, malignant ${malignantPercent}%`">
      <div class="bar-fill high" :style="{ width: malignantPercent + '%' }"></div>
    </div>

    <p class="note">
      This is an AI estimate for research and education, not a diagnosis. Always discuss your mammogram with a doctor.
    </p>
    <p class="meta">Model {{ prediction.modelVersion }} · {{ new Date(prediction.createdAt).toLocaleString() }}</p>
  </div>
</template>

<style scoped>
.view { margin: 0 0 0.5rem; font-weight: 600; color: var(--purple-900); }
.verdict { margin: 0 0 0.75rem; font-size: 2rem; font-weight: 700; }
.split { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-bottom: 0.6rem; }
.side { display: grid; gap: 0.1rem; padding: 0.7rem 0.9rem; border: 1.5px solid var(--border); border-radius: 12px; }
.side.larger { border-color: currentColor; background: var(--pink-50); }
.side-name { font-size: 0.85rem; font-weight: 600; color: var(--muted); }
.side-value { font-size: 1.5rem; font-weight: 700; }
.bar { background: #c8e6c9; }
.bar-fill { border-radius: 5px 0 0 5px; }
.note { margin: 0.75rem 0 0; font-size: 0.88rem; color: var(--text); }
</style>
