<script setup>
import { isMalignant } from '../prediction/verdict'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { authState, onSessionEnd } from '../auth/authStore'
import { listPredictions, imageUrl, errorMessage } from '../services/predictionService'
import { APP_ROUTES } from '../config/appRoutes'

const recent = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')

async function load() {
  try {
    const all = await listPredictions()
    total.value = all.length
    recent.value = all.slice(0, 3)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const percent = (p) => Math.round(p.malignantProbability * 1000) / 10

const stop = onSessionEnd(() => (recent.value = []))
onBeforeUnmount(stop)
onMounted(load)
</script>

<template>
  <section class="container page">
    <h1>Hello, {{ authState.user?.fullName }}</h1>
    <p class="muted">Your mammogram analyses are private to you. You choose which doctors can see each report.</p>

    <div class="actions">
      <RouterLink :to="APP_ROUTES.analysis" class="card action">
        <strong>New analysis</strong>
        <span class="muted">Upload a PNG or JPG mammogram</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.history" class="card action">
        <strong>My history</strong>
        <span class="muted">{{ loading ? 'Loading…' : `${total} saved ${total === 1 ? 'analysis' : 'analyses'}` }}</span>
      </RouterLink>
    </div>

    <h2>Recent reports</h2>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!recent.length" class="muted">No analyses yet. Start with a new analysis above.</p>
    <div v-else class="grid">
      <RouterLink v-for="p in recent" :key="p.id" :to="`/reports/${p.id}`" class="card history-item">
        <img :src="imageUrl(p.id)" :alt="p.originalFilename" class="thumb" />
        <p class="filename">{{ p.originalFilename }}</p>
        <p :class="isMalignant(p) ? 'high' : 'low'">{{ isMalignant(p) ? 'Malignant' : 'Benign' }} · {{ percent(p) }}% malignancy</p>
        <p class="meta">{{ new Date(p.createdAt).toLocaleString() }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
h2 { color: var(--purple-900); margin: 2rem 0 0; font-size: 1.3rem; }
.actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; margin-top: 1.25rem; }
.action { display: grid; gap: 0.25rem; transition: transform 0.15s, border-color 0.15s; }
.action strong { color: var(--pink-600); font-size: 1.1rem; }
.action:hover { transform: translateY(-2px); border-color: var(--pink-300); }
</style>
