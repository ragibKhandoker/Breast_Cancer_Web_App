<script setup>
import { ref, computed, onMounted } from 'vue'
import { getModelInfo, errorMessage } from '../../services/predictionService'

// How well the running model did on test images it never saw while being built (ML-Service/compare_ensemble.py
// or evaluate.py). The numbers come from the server's model files, so they always describe the model that
// produced the user's result. metrics.tests holds one report per test set.
const props = defineProps({ highlightView: { type: String, default: '' } })

const info = ref(null)
const error = ref('')
const loading = ref(true)

const tests = computed(() => info.value?.metrics?.tests ?? [])
const evaluatedAt = computed(() => info.value?.metrics?.evaluated_at)

const pct = (value) => (value == null ? '–' : `${(value * 100).toFixed(1)}%`)
const range = (pair) => (pair ? `${pct(pair[0])} – ${pct(pair[1])}` : '')
const auc = (value) => (value == null ? '–' : value.toFixed(3))

function tiles(test) {
  const o = test.overall
  const c = o.confusion_matrix
  return [
    { key: 'recall', name: 'Sensitivity (recall)', value: pct(o.recall), ci: range(o.ci95?.recall),
      plain: `Flagged ${c.true_positive} of ${o.malignant} positive images; missed ${c.false_negative}.` },
    { key: 'specificity', name: 'Specificity', value: pct(o.specificity), ci: range(o.ci95?.specificity),
      plain: `Cleared ${c.true_negative} of ${o.benign} negative images; wrongly flagged ${c.false_positive}.` },
    { key: 'accuracy', name: 'Accuracy', value: pct(o.accuracy), ci: range(o.ci95?.accuracy),
      plain: `Right on ${c.true_positive + c.true_negative} of ${o.images} test images.` },
    { key: 'precision', name: 'Precision', value: pct(o.precision), ci: '',
      plain: 'Of the images it flagged, the share that really were positive.' },
    { key: 'f1', name: 'F1 score', value: pct(o.f1), ci: '', plain: 'Balance of precision and sensitivity.' },
    { key: 'roc_auc', name: 'ROC-AUC', value: auc(o.roc_auc), ci: o.ci95?.roc_auc ? o.ci95.roc_auc.map(auc).join(' – ') : '',
      plain: '0.5 is guessing, 1.0 is perfect: how well it ranks positive above negative.' },
  ]
}

onMounted(async () => {
  try {
    info.value = await getModelInfo()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="card performance" aria-labelledby="model-performance-title">
    <h2 id="model-performance-title">How accurate is this model?</h2>
    <p v-if="loading" class="muted">Loading model results…</p>
    <p v-else-if="error" class="error">Could not load the model's results. {{ error }}</p>
    <p v-else-if="!info.available" class="warning">No model is installed on the server yet, so mammograms cannot be analysed.</p>
    <p v-else-if="!info.trained || !tests.length" class="warning">
      The model on this server has not been trained and tested yet (demo mode). Its results are meaningless and no
      accuracy can be shown.
    </p>
    <template v-else>
      <p class="intro">
        Every upload is first checked to be a mammogram; photos, drawings and other scans are refused. Three AI models
        then estimate the malignancy percentage together, including the winning model of the RSNA Screening
        Mammography Breast Cancer Detection competition. The result is <em>Malignant</em> when that is the larger
        percentage, otherwise <em>Benign</em>. Below is how often that was right on mammograms from a hospital none of
        the models was trained on, <strong>from patients the combination never saw</strong>. Ranges are 95%
        confidence intervals.
      </p>

      <article v-for="test in tests" :key="test.key ?? test.name" class="test" :aria-labelledby="`test-${test.key ?? 'main'}`">
        <h3 :id="`test-${test.key ?? 'main'}`">{{ test.name }}</h3>
        <p class="test-meta">
          {{ test.overall.images }} mammograms from {{ test.patients }} patients · {{ test.labels }}
          <span v-if="test.note"> · {{ test.note }}</span>
        </p>
        <ul class="tiles">
          <li v-for="tile in tiles(test)" :key="tile.key" class="tile">
            <span class="tile-name">{{ tile.name }}</span>
            <span class="tile-value">{{ tile.value }}</span>
            <span v-if="tile.ci" class="tile-ci">95% CI {{ tile.ci }}</span>
            <span class="tile-plain">{{ tile.plain }}</span>
          </li>
        </ul>
        <div class="table-wrap">
          <table>
            <caption>By mammogram view</caption>
            <thead>
              <tr><th scope="col">View</th><th scope="col">Images</th><th scope="col">Sensitivity</th><th scope="col">Specificity</th><th scope="col">Accuracy</th><th scope="col">ROC-AUC</th></tr>
            </thead>
            <tbody>
              <tr v-for="[view, m] in Object.entries(test.by_view ?? {})" :key="view" :class="{ highlight: view === props.highlightView }">
                <th scope="row">{{ view }}<span v-if="view === props.highlightView" class="you"> (your image)</span></th>
                <td>{{ m.images }}</td>
                <td>{{ pct(m.recall) }}</td>
                <td>{{ pct(m.specificity) }}</td>
                <td>{{ pct(m.accuracy) }}</td>
                <td>{{ auc(m.roc_auc) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>

      <p class="limits">
        <strong>Limits:</strong> this is a research tool, not a medical device. It misses some cancers and raises
        false alarms, as the numbers above show, and results on images from other hospitals or machines can be lower.
        Use it only alongside a radiologist or doctor.
      </p>
      <p class="meta">
        Model {{ info.version }} · decision threshold {{ pct(info.threshold) }}
        <span v-if="evaluatedAt"> · tested {{ new Date(evaluatedAt).toLocaleDateString() }}</span>
      </p>
    </template>
  </section>
</template>

<style scoped>
.performance { background: rgba(255, 255, 255, 0.92); }
h2 { margin: 0 0 0.5rem; font-size: 1.2rem; color: var(--purple-900); }
h3 { margin: 0; font-size: 1.02rem; color: var(--purple-900); }
.intro { margin: 0 0 1rem; line-height: 1.55; }
.test { padding: 1rem 0 0.5rem; border-top: 1px solid var(--border); }
.test-meta { margin: 0.25rem 0 0.75rem; font-size: 0.85rem; color: var(--muted); line-height: 1.45; }
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr)); gap: 0.75rem; margin: 0 0 1rem; padding: 0; list-style: none; }
.tile { display: grid; gap: 0.15rem; padding: 0.85rem 0.9rem; border: 1px solid var(--border); border-radius: 12px; background: #fff; }
.tile-name { font-size: 0.82rem; font-weight: 600; color: var(--muted); }
.tile-value { font-size: 1.5rem; font-weight: 700; color: var(--purple-900); }
.tile-ci { font-size: 0.78rem; color: var(--muted); }
.tile-plain { margin-top: 0.25rem; font-size: 0.82rem; line-height: 1.4; color: var(--text); }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
caption { text-align: left; font-weight: 600; color: var(--purple-900); margin-bottom: 0.4rem; }
th, td { padding: 0.5rem 0.6rem; border-bottom: 1px solid var(--border); text-align: left; white-space: nowrap; }
thead th { color: var(--muted); font-size: 0.8rem; }
tr.highlight { background: var(--pink-50); }
.you { font-weight: 400; color: var(--pink-600); }
.limits { margin: 1rem 0 0; font-size: 0.88rem; line-height: 1.55; }
</style>
