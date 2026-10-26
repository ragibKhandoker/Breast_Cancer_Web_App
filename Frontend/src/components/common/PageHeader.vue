<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>

<template>
  <header class="page-header">
    <div class="container inner">
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      <slot />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  background:
    radial-gradient(circle at 85% 20%, rgba(251, 211, 230, 0.9) 0, transparent 45%),
    linear-gradient(180deg, #fde7f1 0%, var(--bg) 100%);
  padding: 3.5rem 0 3rem;
}
.inner { max-width: 820px; text-align: center; }
.eyebrow {
  display: inline-block; padding: 0.3rem 0.9rem; border-radius: 999px;
  background: var(--pink-100); color: var(--pink-600); font-weight: 600; font-size: 0.85rem;
}
h1 { margin: 0.8rem 0 0; color: var(--purple-900); font-size: clamp(2rem, 4.5vw, 2.9rem); line-height: 1.15; }
.subtitle { margin: 1rem auto 0; max-width: 640px; color: var(--text); font-size: 1.1rem; line-height: 1.6; }
</style>
