<script setup>
import { computed, ref, watch } from 'vue'
import { initials } from '../../appointments/format'
import { photoSrc } from '../../services/surgeonService'

// The surgeon's profile picture, or their initials when there is none (or it fails to load).
const props = defineProps({
  photoUrl: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: Number, default: 72 },
})

const failed = ref(false)
watch(() => props.photoUrl, () => (failed.value = false))
const src = computed(() => (failed.value ? '' : photoSrc(props.photoUrl)))
</script>

<template>
  <span class="surgeon-avatar" :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${size * 0.34}px` }">
    <img v-if="src" :src="src" :alt="`Photo of ${name}`" @error="failed = true" />
    <span v-else aria-hidden="true">{{ initials(name) }}</span>
  </span>
</template>

<style scoped>
.surgeon-avatar {
  flex: none; display: grid; place-items: center; overflow: hidden; border-radius: 50%;
  background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; font-weight: 700;
  box-shadow: 0 0 0 4px #fff, 0 8px 20px rgba(216, 27, 106, 0.22);
}
.surgeon-avatar img { width: 100%; height: 100%; object-fit: cover; }
</style>
