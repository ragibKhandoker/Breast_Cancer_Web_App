<script setup>
import { computed } from 'vue'

const props = defineProps({ prediction: { type: Object, required: true } })

const percent = computed(() => Math.round(props.prediction.malignantProbability * 1000) / 10)
const isMalignant = computed(() => props.prediction.label === 'malignant')
</script>

<template>
  <div class="card result">
    <p v-if="prediction.demoMode" class="warning">
      Demo mode: the model has not been trained on mammograms yet, so this number is meaningless. Run
      <code>train.py</code> and then <code>export_onnx.py</code> in ML-Service.
    </p>
    <p class="label">Estimated malignancy probability</p>
    <p class="percent" :class="isMalignant ? 'high' : 'low'">{{ percent }}%</p>
    <div class="bar">
      <div class="bar-fill" :class="isMalignant ? 'high' : 'low'" :style="{ width: percent + '%' }"></div>
    </div>
    <p class="verdict">
      Model leans <strong>{{ isMalignant ? 'malignant' : 'benign' }}</strong>
    </p>
    <p class="meta">Model {{ prediction.modelVersion }} · {{ new Date(prediction.createdAt).toLocaleString() }}</p>
  </div>
</template>
