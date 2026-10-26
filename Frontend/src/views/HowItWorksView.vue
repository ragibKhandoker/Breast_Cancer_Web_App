<script setup>
import PageHeader from '../components/common/PageHeader.vue'
import StepList from '../components/common/StepList.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { HOW_IT_WORKS_STEPS } from '../content/howItWorks'
import { APP_ROUTES } from '../config/appRoutes'
</script>

<template>
  <PageHeader
    eyebrow="How It Works"
    title="From upload to result in a few steps"
    subtitle="Here is exactly what happens when you analyse a mammogram on BreastCare."
  />

  <section class="container section">
    <StepList :steps="HOW_IT_WORKS_STEPS" />
  </section>

  <section class="container section">
    <div v-reveal class="cta">
      <div>
        <h2>Ready to try it?</h2>
        <p>Upload a PNG or JPG mammogram and see the model's estimate in a few seconds.</p>
      </div>
      <div class="actions">
        <RouterLink :to="APP_ROUTES.analysis" class="btn btn-primary">Start Analysis <AppIcon name="arrow-right" :size="18" /></RouterLink>
        <RouterLink to="/faq" class="btn btn-outline">Read the FAQ</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.section { padding-top: 2rem; padding-bottom: 1rem; }
.cta {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 1.5rem;
  padding: 2rem 2.25rem; border-radius: 22px; background: #fff; border: 1px solid var(--border); box-shadow: var(--shadow);
}
h2 { margin: 0 0 0.4rem; color: var(--purple-900); }
p { margin: 0; color: var(--text); }
.actions { display: flex; flex-wrap: wrap; gap: 1rem; }
</style>
