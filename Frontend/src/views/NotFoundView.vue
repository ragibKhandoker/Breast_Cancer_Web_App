<script setup>
import PinkRibbon from '../components/common/PinkRibbon.vue'
</script>

<template>
  <section class="container page not-found">
    <PinkRibbon :size="56" />
    <p class="code">404</p>
    <h1>Page not found</h1>
    <p class="muted">The page <code>{{ $route.fullPath }}</code> does not exist or has moved.</p>
    <div class="actions">
      <RouterLink to="/" class="btn btn-primary">Back to Home</RouterLink>
      <RouterLink to="/contact" class="btn btn-outline">Contact Us</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.not-found { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 0.5rem; min-height: 55vh; padding-top: 4rem; }
.code { margin: 0.5rem 0 0; font-size: 3.5rem; font-weight: 800; color: var(--pink-300); line-height: 1; }
.muted code { overflow-wrap: anywhere; }
.actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 1rem; margin-top: 1rem; }
</style>
