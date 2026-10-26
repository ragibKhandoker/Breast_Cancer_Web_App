<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  steps: { type: Array, required: true }, // [{ icon, title, text }]
})
</script>

<template>
  <ol class="steps">
    <li v-for="(step, i) in steps" :key="step.title" v-reveal="(i % 3) * 100" class="step">
      <span class="number" aria-hidden="true">{{ i + 1 }}</span>
      <span class="icon" aria-hidden="true"><AppIcon :name="step.icon" :size="26" /></span>
      <h3>{{ step.title }}</h3>
      <p>{{ step.text }}</p>
    </li>
  </ol>
</template>

<style scoped>
.steps {
  list-style: none; margin: 0; padding: 0;
  display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.25rem;
}
.step {
  position: relative; padding: 1.75rem 1.5rem 1.5rem;
  background: #fff; border: 1px solid var(--border); border-radius: 20px; box-shadow: var(--shadow);
}
.number {
  position: absolute; top: 1.1rem; right: 1.25rem;
  font-size: 2.2rem; font-weight: 800; line-height: 1; color: var(--pink-100);
}
.icon {
  display: grid; place-items: center; width: 54px; height: 54px; border-radius: 16px;
  background: var(--pink-100); color: var(--pink-600);
}
h3 { margin: 1rem 0 0.5rem; color: var(--purple-900); font-size: 1.1rem; }
p { margin: 0; color: var(--text); line-height: 1.6; font-size: 0.95rem; }
</style>
