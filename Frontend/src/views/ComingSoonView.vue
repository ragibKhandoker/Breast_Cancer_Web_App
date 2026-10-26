<script setup>
import PinkRibbon from '../components/common/PinkRibbon.vue'
</script>

<template>
  <section class="container page coming-soon">
    <PinkRibbon :size="60" />
    <h1>{{ $route.meta.title }}</h1>
    <p class="muted">{{ $route.meta.description ?? 'This page is coming in a later sprint.' }}</p>
    <RouterLink to="/" class="btn btn-outline">Back to Home</RouterLink>
  </section>
</template>

<style scoped>
.coming-soon { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 0.5rem; padding-top: 4rem; min-height: 50vh; }
</style>
