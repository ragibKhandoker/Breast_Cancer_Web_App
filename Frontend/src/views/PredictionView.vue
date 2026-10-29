<script setup>
import { ref, nextTick } from 'vue'
import MammogramUpload from '../components/prediction/MammogramUpload.vue'
import ResultCard from '../components/prediction/ResultCard.vue'
import ModelPerformance from '../components/prediction/ModelPerformance.vue'
import { APP_ROUTES } from '../config/appRoutes'

// The report the backend returned for the last upload. Nothing is shown until the backend has saved it.
const result = ref(null)
const resultHeading = ref(null)

async function onUploaded(report) {
  result.value = report
  await nextTick()
  resultHeading.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="photo-bg photo-bg--prediction">
    <section class="container page">
      <div v-reveal class="glass-panel panel">
        <h1>Upload Your Mammogram</h1>
        <p class="intro">
          Choose a mammogram image from your device. After you upload it, our AI model estimates the probability that
          it shows signs of malignancy, and the image and result are saved to your account so you can review them later
          or share them with your doctor.
          <RouterLink :to="APP_ROUTES.history" class="history-link">View past results</RouterLink>
        </p>

        <MammogramUpload @uploaded="onUploaded" @reset="result = null" />

        <section v-if="result" class="result-section" aria-labelledby="result-title">
          <h2 id="result-title" ref="resultHeading" class="result-title">Your result</h2>
          <ResultCard :prediction="result" />
          <p class="next">
            <RouterLink :to="`/reports/${result.id}`" class="history-link">Open the report to share it with a doctor</RouterLink>
          </p>
        </section>

        <ModelPerformance class="performance-section" :highlight-view="result?.view ?? ''" />

        <p class="disclaimer">
          For research and education only. This tool is not a medical device and does not replace a radiologist or doctor.
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.panel { max-width: 1040px; margin: 1rem auto 0; }
.intro { max-width: 68ch; margin: 0.35rem 0 0; line-height: 1.6; color: var(--purple-900); }
.history-link { color: var(--pink-600); font-weight: 600; text-decoration: underline; text-underline-offset: 3px; }
.history-link:focus-visible { outline: 3px solid var(--purple-900); outline-offset: 2px; border-radius: 4px; }
.result-section { margin-top: 2rem; scroll-margin-top: 6rem; }
.result-title { margin: 0; color: var(--purple-900); font-size: 1.3rem; scroll-margin-top: 6rem; }
.result-section :deep(.result) { margin-top: 0.75rem; }
.next { margin: 1rem 0 0; }
.performance-section { margin-top: 2rem; }
.disclaimer { margin-top: 1.5rem; color: var(--purple-900); }

@media (max-width: 600px) {
  .panel { padding: 1.25rem; }
  .page h1 { font-size: 1.5rem; }
}
</style>
