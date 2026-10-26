<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { analyzeImage, errorMessage } from '../services/predictionService'
import ResultCard from '../components/prediction/ResultCard.vue'

const file = ref(null)
const previewUrl = ref('')
const result = ref(null)
const error = ref('')
const loading = ref(false)
const dragging = ref(false)

function selectFile(selected) {
  if (!selected) return
  file.value = selected
  result.value = null
  error.value = ''
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(selected)
}

function onDrop(event) {
  dragging.value = false
  selectFile(event.dataTransfer.files[0])
}

async function analyze() {
  loading.value = true
  error.value = ''
  try {
    result.value = await analyzeImage(file.value)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => previewUrl.value && URL.revokeObjectURL(previewUrl.value))
</script>

<template>
  <div class="photo-bg photo-bg--prediction">
    <section class="container page">
      <div v-reveal class="glass-panel panel">
        <h1>Mammography Prediction</h1>
        <p class="muted">
          Upload a PNG or JPG mammogram, up to 50 MB. <RouterLink to="/history" class="history-link">View past results</RouterLink>
        </p>

        <label
          class="dropzone"
          :class="{ dragging }"
          @dragover.prevent="dragging = true"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
        >
          <input type="file" accept=".png,.jpg,.jpeg" hidden @change="selectFile($event.target.files[0])" />
          <img v-if="previewUrl" :src="previewUrl" alt="Selected mammogram" class="upload-preview" />
          <span v-else>Drop an image here or click to choose</span>
        </label>

        <button class="btn btn-primary" :disabled="!file || loading" @click="analyze">
          {{ loading ? 'Analyzing…' : 'Analyze' }}
        </button>

        <p v-if="error" class="error">{{ error }}</p>
        <ResultCard v-if="result" :prediction="result" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.panel { max-width: 720px; margin: 1rem auto 0; }
.history-link { color: var(--pink-600); font-weight: 600; }

/* Blurred, see-through drop area so the photo behind stays visible but soft. */
.dropzone {
  background: rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(14px);
  border-color: rgba(216, 27, 106, 0.55);
  color: var(--purple-900);
  font-weight: 500;
}
.dropzone.dragging { background: rgba(253, 227, 239, 0.6); border-color: var(--pink-600); }
</style>
