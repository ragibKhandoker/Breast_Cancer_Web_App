<script setup>
import StepList from '../common/StepList.vue'
import AppIcon from '../common/AppIcon.vue'
import { HOW_IT_WORKS_STEPS } from '../../content/howItWorks'

// The first four steps cover upload to result; the full page adds history and the doctor's review.
const previewSteps = HOW_IT_WORKS_STEPS.slice(0, 4)
</script>

<template>
  <section class="container preview">
    <div v-reveal class="heading">
      <span class="pill">How It Works</span>
      <h2>From upload to result in four steps</h2>
    </div>
    <StepList :steps="previewSteps" />
    <div class="more">
      <RouterLink to="/how-it-works" class="btn btn-outline">See the full process <AppIcon name="arrow-right" :size="18" /></RouterLink>
    </div>
  </section>
</template>

<style scoped>
.preview { padding-bottom: 3rem; }
.heading {
  width: fit-content; margin: 0 auto 1.75rem; padding: 0.9rem 1.75rem; border-radius: 22px; text-align: center;
  background: rgba(255, 255, 255, 0.85); backdrop-filter: blur(8px);
}
.pill { display: inline-block; padding: 0.25rem 0.85rem; border-radius: 999px; background: var(--pink-100); color: var(--pink-600); font-weight: 600; font-size: 0.85rem; }
h2 { margin: 0.5rem 0 0; color: var(--purple-900); font-size: clamp(1.4rem, 3vw, 1.8rem); }
.more { display: flex; justify-content: center; margin-top: 1.5rem; }
</style>
