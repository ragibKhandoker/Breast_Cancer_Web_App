<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ResultCard from '../components/prediction/ResultCard.vue'
import ScanViewer from '../components/report/ScanViewer.vue'
import ReportStatus from '../components/report/ReportStatus.vue'
import MedicalDisclaimer from '../components/report/MedicalDisclaimer.vue'
import { authState, onSessionEnd } from '../auth/authStore'
import {
  getPrediction,
  getHeatmap,
  deletePrediction,
  imageUrl,
  listShares,
  listDoctors,
  shareWithDoctor,
  unshareWithDoctor,
  errorMessage,
} from '../services/predictionService'
import { APP_ROUTES } from '../config/appRoutes'

const route = useRoute()
const router = useRouter()

const report = ref(null)
const loading = ref(true)
const error = ref('')
const heatmap = ref(null)
const heatmapState = ref('loading')

// Sharing (owner only)
const shares = ref([])
const doctors = ref([])
const selectedDoctor = ref('')
const shareError = ref('')
const busy = ref(false)

const isOwner = computed(() => !!report.value && report.value.ownerId === authState.user?.id)
const unsharedDoctors = computed(() => doctors.value.filter((d) => !shares.value.some((s) => s.id === d.id)))

async function load(id) {
  loading.value = true
  error.value = ''
  report.value = null
  try {
    report.value = await getPrediction(id)
    loadHeatmap(id)
    if (isOwner.value) {
      ;[shares.value, doctors.value] = await Promise.all([listShares(id), listDoctors()])
    }
  } catch (err) {
    // The backend answers 404 both for missing reports and for ones this account may not open.
    error.value = err.response?.status === 404 ? 'This report does not exist or you do not have access to it.' : errorMessage(err)
  } finally {
    loading.value = false
  }
}

// Loaded after the report so the page shows at once; older reports get their heatmap made on this request.
async function loadHeatmap(id) {
  heatmap.value = null
  heatmapState.value = 'loading'
  try {
    const data = await getHeatmap(id)
    if (route.params.id !== id) return
    heatmap.value = data
    heatmapState.value = data ? 'ready' : 'unavailable'
  } catch {
    if (route.params.id === id) heatmapState.value = 'error'
  }
}

async function share() {
  if (!selectedDoctor.value) return
  busy.value = true
  shareError.value = ''
  try {
    shares.value = await shareWithDoctor(report.value.id, selectedDoctor.value)
    selectedDoctor.value = ''
  } catch (err) {
    shareError.value = errorMessage(err)
  } finally {
    busy.value = false
  }
}

async function unshare(doctor) {
  busy.value = true
  shareError.value = ''
  try {
    await unshareWithDoctor(report.value.id, doctor.id)
    shares.value = shares.value.filter((s) => s.id !== doctor.id)
  } catch (err) {
    shareError.value = errorMessage(err)
  } finally {
    busy.value = false
  }
}

async function remove() {
  if (!confirm('Delete this report and its image? This cannot be undone.')) return
  try {
    await deletePrediction(report.value.id)
    router.replace(APP_ROUTES.history)
  } catch (err) {
    error.value = errorMessage(err)
  }
}

const stop = onSessionEnd(() => {
  report.value = null
  heatmap.value = null
  shares.value = []
})
onBeforeUnmount(stop)
watch(() => route.params.id, (id) => id && load(id), { immediate: true })
</script>

<template>
  <section class="container page">
    <p v-if="loading" class="muted">Loading…</p>
    <template v-else-if="error">
      <h1>Report</h1>
      <p class="error">{{ error }}</p>
    </template>

    <template v-else-if="report">
      <h1>{{ report.originalFilename }}</h1>
      <p class="meta">Analysed {{ new Date(report.createdAt).toLocaleString() }}</p>

      <div class="layout">
        <ScanViewer
          :src="imageUrl(report.id)"
          :alt="`Mammogram ${report.originalFilename}`"
          :heatmap="heatmap"
          :heatmap-state="heatmapState"
        />
        <div>
          <ResultCard :prediction="report" />
          <ReportStatus :report="report" :is-owner="isOwner" :shares="shares" class="status-card" />

          <div v-if="isOwner" class="card share">
            <h2>Share with a doctor</h2>
            <p class="muted small">Only the doctors you add here can open this report. You can remove them at any time.</p>

            <ul v-if="shares.length" class="share-list">
              <li v-for="doctor in shares" :key="doctor.id">
                <span>{{ doctor.fullName }}</span>
                <button class="link-danger" :disabled="busy" @click="unshare(doctor)">Remove</button>
              </li>
            </ul>
            <p v-else class="muted small">Not shared with anyone.</p>

            <form v-if="unsharedDoctors.length" class="share-form" @submit.prevent="share">
              <label for="share-doctor" class="sr-only">Doctor</label>
              <select id="share-doctor" v-model="selectedDoctor" :disabled="busy">
                <option value="" disabled>Choose a doctor…</option>
                <option v-for="doctor in unsharedDoctors" :key="doctor.id" :value="doctor.id">{{ doctor.fullName }}</option>
              </select>
              <button type="submit" class="btn btn-primary btn-sm" :disabled="busy || !selectedDoctor">Share</button>
            </form>
            <p v-else-if="!doctors.length" class="muted small">No doctor accounts exist yet.</p>
            <p v-if="shareError" class="error">{{ shareError }}</p>
          </div>

          <div class="footer-actions">
            <RouterLink v-if="isOwner" :to="APP_ROUTES.history" class="btn btn-outline btn-sm">Back to history</RouterLink>
            <RouterLink v-else :to="APP_ROUTES.doctorDashboard" class="btn btn-outline btn-sm">Back to dashboard</RouterLink>
            <button v-if="isOwner" class="link-danger" @click="remove">Delete report</button>
          </div>
        </div>
      </div>
    </template>

    <MedicalDisclaimer />
  </section>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1.5rem; margin-top: 1rem; align-items: start; }
.status-card { margin-top: 1rem; }
.layout :deep(.result) { margin-top: 0; }
.share { margin-top: 1rem; }
.share h2 { margin: 0 0 0.25rem; font-size: 1.1rem; color: var(--purple-900); }
.small { font-size: 0.88rem; }
.share-list { list-style: none; padding: 0; margin: 0.5rem 0; }
.share-list li { display: flex; justify-content: space-between; align-items: center; padding: 0.4rem 0; border-bottom: 1px solid var(--border); }
.share-list .link-danger { margin: 0; }
.share-form { display: flex; gap: 0.5rem; margin-top: 0.75rem; }
.share-form select { flex: 1; min-width: 0; padding: 0.45rem 0.6rem; border: 1.5px solid var(--border); border-radius: 10px; font: inherit; background: #fff; }
.footer-actions { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-top: 1rem; }
.footer-actions .link-danger { margin: 0; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
@media (max-width: 800px) { .layout { grid-template-columns: 1fr; } }
</style>
