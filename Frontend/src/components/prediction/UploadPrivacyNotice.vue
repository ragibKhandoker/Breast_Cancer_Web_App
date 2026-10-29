<script setup>
import AppIcon from '../common/AppIcon.vue'

// What really happens to an uploaded mammogram in this app (see README: PredictionService, SecurityConfig).
// Keep it in step with the backend; do not add promises (encryption, anonymity, automatic deletion) it does not keep.
defineProps({ id: { type: String, default: 'upload-privacy' } })
</script>

<template>
  <section class="privacy card" :aria-labelledby="`${id}-title`">
    <h2 :id="`${id}-title`" class="privacy-title">
      <AppIcon name="shield-check" :size="20" />
      Privacy notice
    </h2>
    <ul :id="id" class="privacy-list">
      <li>Your image is sent to this website's own server, where our AI model estimates a malignancy probability. It is not sent to an outside AI service.</li>
      <li>The image, its file name and the result are saved to your account in our database, so you can open them later in your history.</li>
      <li>Only you can see them, plus any doctor you choose to share a report with. Administrators see totals only, never your images.</li>
      <li>Images are kept until you delete the report from your history, which also deletes the image.</li>
      <li>The result is for research and education. It is not a diagnosis and does not replace a radiologist or doctor.</li>
    </ul>
  </section>
</template>

<style scoped>
.privacy { background: rgba(255, 255, 255, 0.9); }
.privacy-title {
  display: flex; align-items: center; gap: 0.5rem;
  margin: 0 0 0.6rem; font-size: 1.05rem; color: var(--purple-900);
}
.privacy-title svg { color: var(--pink-600); }
.privacy-list { margin: 0; padding-left: 1.1rem; display: grid; gap: 0.45rem; font-size: 0.9rem; line-height: 1.55; color: var(--text); }
</style>
