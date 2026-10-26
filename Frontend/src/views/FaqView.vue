<script setup>
import PageHeader from '../components/common/PageHeader.vue'
import FaqItem from '../components/common/FaqItem.vue'
import { FAQS } from '../content/faqs'
</script>

<template>
  <PageHeader
    eyebrow="FAQ"
    title="Frequently asked questions"
    subtitle="Answers about the analysis, your data, and what BreastCare can and cannot do."
  />

  <section class="container section">
    <div class="list">
      <FaqItem v-for="faq in FAQS" :key="faq.question" :question="faq.question" :answer="faq.answer" />
    </div>

    <p class="more">
      Still have a question? <RouterLink to="/contact">Send us a message</RouterLink>.
    </p>
  </section>
</template>

<style scoped>
.section { padding-top: 2rem; padding-bottom: 1rem; }
.list { max-width: 820px; margin: 0 auto; display: grid; gap: 0.85rem; }
.more { text-align: center; margin-top: 2rem; color: var(--text); }
.more a { color: var(--pink-600); font-weight: 600; }
.more a:hover { text-decoration: underline; }
</style>
