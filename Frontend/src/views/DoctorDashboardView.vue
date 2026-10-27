<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { authState, onSessionEnd } from '../auth/authStore'
import { listSharedReports, imageUrl, errorMessage } from '../services/predictionService'

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

    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!shared.length" class="muted">No patient has shared a report with you yet.</p>
    <div v-else class="grid">
      <RouterLink v-for="{ report, patient } in shared" :key="report.id" :to="`/reports/${report.id}`" class="card history-item">
        <img :src="imageUrl(report.id)" :alt="report.originalFilename" class="thumb" />
        <p class="filename">{{ patient.fullName }}</p>
        <p :class="report.label === 'malignant' ? 'high' : 'low'">
          {{ percent(report) }}% malignancy
          <span v-if="report.demoMode" class="tag">demo</span>
        </p>
        <p class="meta">{{ new Date(report.createdAt).toLocaleString() }}</p>
      </RouterLink>
    </div>
  </section>
</template>
