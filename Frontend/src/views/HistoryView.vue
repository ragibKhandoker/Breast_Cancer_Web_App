<script setup>
import { ref, onMounted } from 'vue'
import { listPredictions, deletePrediction, imageUrl, errorMessage } from '../services/predictionService'

const predictions = ref([])
const error = ref('')
const loading = ref(true)

async function load() {
  try {
    predictions.value = await listPredictions()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function remove(id) {
  if (!confirm('Delete this record and its image?')) return
  try {
    await deletePrediction(id)
    predictions.value = predictions.value.filter((p) => p.id !== id)
  } catch (err) {
    error.value = errorMessage(err)
  }
}

onMounted(load)
</script>

<template>
  <section class="container page">
    <h1>Prediction History</h1>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!predictions.length" class="muted">No analyses yet.</p>

    <div v-else class="grid">
      <div v-for="(p, i) in predictions" :key="p.id" v-reveal="(i % 4) * 100" class="card history-item">
        <img :src="imageUrl(p.id)" :alt="p.originalFilename" class="thumb" />
        <p class="filename">{{ p.originalFilename }}</p>
        <p :class="p.label === 'malignant' ? 'high' : 'low'">
          {{ Math.round(p.malignantProbability * 1000) / 10 }}% malignancy
          <span v-if="p.demoMode" class="tag">demo</span>
        </p>
        <p class="meta">{{ new Date(p.createdAt).toLocaleString() }}</p>
        <div class="item-actions">
          <RouterLink :to="`/reports/${p.id}`" class="report-link">View &amp; share</RouterLink>
          <button class="link-danger" @click="remove(p.id)">Delete</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.item-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 0.5rem; }
.item-actions .link-danger { margin: 0; }
.report-link { color: var(--pink-600); font-weight: 600; }
</style>
