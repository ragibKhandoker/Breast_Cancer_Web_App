<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import { myPatients, patientScans, runDetectionFor } from '../../services/careService'
import { imageUrl, uploadFailure } from '../../services/predictionService'
import { ACCEPT, newUploadId, validateMammogramFile } from '../../prediction/mammogramFile'
import { isMalignant } from '../../prediction/verdict'
import { errorMessage } from '../../services/http'

// The doctor's patients: anyone whose appointment this doctor confirmed. For each, the reports the doctor may
// open, and a mammogram analysis the doctor can run for them (saved in the patient's own history).
const patients = ref([])
const loading = ref(true)
const error = ref('')
const open = ref('')
const scans = ref({})
const running = ref('')
const progress = ref(0)
const runError = ref({})
const router = useRouter()

const percent = (p) => Math.round(p.malignantProbability * 1000) / 10
const day = (iso) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { dateStyle: 'medium' }) : '')

async function load() {
  try {
    patients.value = await myPatients()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function toggle(p) {
  if (open.value === p.id) {
    open.value = ''
    return
  }
  open.value = p.id
  try {
    scans.value = { ...scans.value, [p.id]: await patientScans(p.id) }
  } catch (err) {
    runError.value = { ...runError.value, [p.id]: errorMessage(err) }
  }
}

async function run(p, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  runError.value = { ...runError.value, [p.id]: '' }
  const check = await validateMammogramFile(file)
  if (check.error) {
    runError.value = { ...runError.value, [p.id]: check.error }
    return
  }
  running.value = p.id
  progress.value = 0
  try {
    const report = await runDetectionFor(p.id, file, { uploadId: newUploadId(), onProgress: (v) => (progress.value = v) })
    router.push(`/reports/${report.id}`)
  } catch (err) {
    // 403 here means "not your patient" (not "patients only"), so show the server's own words.
    const message = err.response?.status === 403 ? errorMessage(err) : uploadFailure(err).message
    runError.value = { ...runError.value, [p.id]: message }
  } finally {
    running.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="care">
    <h2>My patients</h2>
    <p class="muted small">Patients appear here once you confirm their appointment.</p>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!patients.length" class="muted">No patients yet.</p>
    <ul v-else class="patients">
      <li v-for="p in patients" :key="p.id" class="card patient">
        <div class="row">
          <span class="avatar" aria-hidden="true"><AppIcon name="user" :size="20" /></span>
          <div class="who">
            <strong>{{ p.fullName }}</strong>
            <span class="muted small">Last visit {{ day(p.lastVisitDate) }} · {{ p.scans }} report{{ p.scans === 1 ? '' : 's' }}</span>
          </div>
          <span v-if="p.openRequests" class="badge">{{ p.openRequests }} to review</span>
          <div class="actions">
            <label class="btn btn-primary btn-sm run" :class="{ busy: running === p.id }">
              <AppIcon name="microscope" :size="15" />
              {{ running === p.id ? `Analysing… ${progress}%` : 'Run detection' }}
              <input type="file" :accept="ACCEPT" :disabled="!!running" :aria-label="`Upload a mammogram for ${p.fullName}`" @change="run(p, $event)" />
            </label>
            <button type="button" class="btn btn-outline btn-sm" :aria-expanded="open === p.id" @click="toggle(p)">
              {{ open === p.id ? 'Hide reports' : 'Reports' }}
            </button>
          </div>
        </div>
        <p v-if="runError[p.id]" class="error small">{{ runError[p.id] }}</p>
        <div v-if="open === p.id" class="scans">
          <p v-if="!scans[p.id]" class="muted small">Loading…</p>
          <p v-else-if="!scans[p.id].length" class="muted small">No reports yet. Run a detection, or ask the patient to share one.</p>
          <RouterLink v-for="s in scans[p.id] || []" :key="s.id" :to="`/reports/${s.id}`" class="scan">
            <img :src="imageUrl(s.id)" alt="" />
            <span>
              <strong :class="isMalignant(s) ? 'high' : 'low'">{{ isMalignant(s) ? 'Malignant' : 'Benign' }} · {{ percent(s) }}%</strong>
              <span class="muted small">{{ new Date(s.createdAt).toLocaleDateString() }}{{ s.createdByName ? ` · run by ${s.createdByName}` : '' }}</span>
            </span>
          </RouterLink>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.care h2 { color: var(--purple-900); margin: 2rem 0 0.2rem; font-size: 1.3rem; }
.small { font-size: 0.86rem; margin: 0; }
.patients { list-style: none; margin: 0.8rem 0 0; padding: 0; display: grid; gap: 0.7rem; }
.patient { display: grid; gap: 0.6rem; padding: 1rem 1.1rem; }
.row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.8rem; }
.avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; background: var(--pink-100); color: var(--pink-600); }
.who { display: grid; gap: 0.1rem; flex: 1; min-width: 180px; }
.who strong { color: var(--purple-900); }
.badge { padding: 0.15rem 0.6rem; border-radius: 999px; background: #fff4e5; color: #8a4b00; font-size: 0.78rem; font-weight: 600; }
.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.run { position: relative; overflow: hidden; cursor: pointer; background: #d81b6a; color: #fff; }
.run input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.run.busy { opacity: 0.8; cursor: progress; }
.scans { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.5rem; }
.scan { display: flex; gap: 0.6rem; align-items: center; padding: 0.5rem; border: 1px solid var(--border); border-radius: 12px; color: var(--text); }
.scan:hover { border-color: var(--pink-300); }
.scan img { width: 52px; height: 52px; object-fit: cover; border-radius: 8px; background: #000; }
.scan > span { display: grid; gap: 0.1rem; }
</style>
