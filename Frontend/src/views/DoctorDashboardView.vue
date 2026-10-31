<script setup>
import { isMalignant } from '../prediction/verdict'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { authState, onSessionEnd } from '../auth/authStore'
import { listSharedReports, imageUrl, errorMessage } from '../services/predictionService'
import { APP_ROUTES } from '../config/appRoutes'

const shared = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  try {
    shared.value = await listSharedReports()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const percent = (p) => Math.round(p.malignantProbability * 1000) / 10

const stop = onSessionEnd(() => (shared.value = []))
onBeforeUnmount(stop)
onMounted(load)
</script>

<template>
  <section class="container page">
    <h1>Welcome, {{ authState.user?.fullName }}</h1>
    <p class="muted">Reports your patients have chosen to share with you. You only see what they share.</p>

    <div v-if="authState.user?.verificationStatus === 'PENDING'" class="alert info verify-note" role="status">
      <strong>Your doctor account is awaiting verification.</strong>
      Set up your <RouterLink :to="APP_ROUTES.profile">doctor profile</RouterLink> with your registration documents;
      patients can share reports with you and book you once an administrator verifies it.
    </div>
    <div v-else-if="authState.user?.verificationStatus === 'REJECTED'" class="alert error verify-note" role="status">
      An administrator could not verify your doctor account. See their note in
      <RouterLink :to="APP_ROUTES.accountSettings">account settings</RouterLink>.
    </div>

    <div class="surgeon-links">
      <RouterLink :to="APP_ROUTES.surgeonDashboard" class="card surgeon-link">
        <strong>Breast Surgeon dashboard</strong>
        <span class="muted">Appointment requests, upcoming consultations and your availability</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.surgeonProfile" class="card surgeon-link">
        <strong>My doctor profile</strong>
        <span class="muted">Photo, fees, languages, hospital and schedule: the profile patients book you through</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.messages" class="card surgeon-link">
        <strong>Messages</strong>
        <span class="muted">Talk to the BreastCare administrators</span>
      </RouterLink>
    </div>

    <h2 class="shared-title">Shared reports</h2>

    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!shared.length" class="muted">No patient has shared a report with you yet.</p>
    <div v-else class="grid">
      <RouterLink v-for="{ report, patient } in shared" :key="report.id" :to="`/reports/${report.id}`" class="card history-item">
        <img :src="imageUrl(report.id)" :alt="report.originalFilename" class="thumb" />
        <p class="filename">{{ patient.fullName }}</p>
        <p :class="isMalignant(report) ? 'high' : 'low'">
          {{ isMalignant(report) ? 'Malignant' : 'Benign' }} · {{ percent(report) }}% malignancy
          <span v-if="report.demoMode" class="tag">demo</span>
        </p>
        <p class="meta">{{ new Date(report.createdAt).toLocaleString() }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.surgeon-links { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; margin-top: 1.25rem; }
.surgeon-link { display: grid; gap: 0.25rem; transition: transform 0.15s, border-color 0.15s; }
.surgeon-link strong { color: var(--pink-600); font-size: 1.1rem; }
.surgeon-link:hover { transform: translateY(-2px); border-color: var(--pink-300); }
.verify-note { margin-top: 1rem; }
.verify-note a { color: var(--pink-600); font-weight: 600; text-decoration: underline; }
.shared-title { color: var(--purple-900); margin: 2rem 0 0; font-size: 1.3rem; }
</style>
