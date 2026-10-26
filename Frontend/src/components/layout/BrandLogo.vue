<script setup>
import PinkRibbon from '../common/PinkRibbon.vue'

defineProps({ dark: { type: Boolean, default: false } })
</script>

<template>
  <RouterLink to="/" class="brand" :class="{ dark }">
    <PinkRibbon :size="30" />
    <span>
      <span class="name">Breast<span class="accent">Care</span></span>
      <span class="tagline">Detect Early • Live Healthier</span>
    </span>
  </RouterLink>
</template>

<style scoped>
.brand { display: flex; align-items: center; gap: 0.6rem; }
.name { display: block; font-size: 1.35rem; font-weight: 700; color: var(--purple-900); line-height: 1.1; }
.accent { color: var(--pink-600); }
.tagline { display: block; font-size: 0.72rem; color: var(--muted); }
.dark .name { color: #fff; }
.dark .accent { color: var(--pink-500); }
.dark .tagline { color: #cfc6e6; }
</style>
