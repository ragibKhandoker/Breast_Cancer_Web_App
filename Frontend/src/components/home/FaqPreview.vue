<script setup>
import FaqItem from '../common/FaqItem.vue'
import AppIcon from '../common/AppIcon.vue'
import { FAQS } from '../../content/faqs'

const previewFaqs = FAQS.slice(0, 4)
</script>

<template>
  <section class="container preview">
    <div v-reveal class="heading">
      <span class="pill">FAQ</span>
      <h2>Common questions</h2>
    </div>
    <div class="list">
      <FaqItem v-for="faq in previewFaqs" :key="faq.question" :question="faq.question" :answer="faq.answer" />
    </div>
    <div class="more">
      <RouterLink to="/faq" class="btn btn-outline">View all questions <AppIcon name="arrow-right" :size="18" /></RouterLink>
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
.list { max-width: 820px; margin: 0 auto; display: grid; gap: 0.85rem; }
.more { display: flex; justify-content: center; margin-top: 1.5rem; }
</style>
