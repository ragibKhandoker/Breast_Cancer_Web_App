<script setup>
import PageHeader from '../components/common/PageHeader.vue'
import FaqItem from '../components/common/FaqItem.vue'
import { FAQS } from '../content/faqs'
</script>

<template>
  <div class="photo-bg photo-bg--faq light-boxes faq-page">
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
        Still have a question? <RouterLink to="/blogs">Contact us</RouterLink>.
      </p>
    </section>
  </div>
</template>

<style scoped>
/* The photo shows through: the header has no background of its own here, and white question cards sit on top. */
.faq-page :deep(.page-header) { background: transparent; }
.faq-page :deep(.page-header .inner) {
  padding: 1.6rem 2rem; border-radius: 24px; background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(6px); box-shadow: 0 16px 40px rgba(42, 22, 80, 0.1);
}
.faq-page :deep(.faq-item) { box-shadow: 0 8px 24px rgba(42, 22, 80, 0.08); }
.section { padding-top: 2rem; padding-bottom: 3rem; }
.list { max-width: 820px; margin: 0 auto; display: grid; gap: 0.85rem; }
.more {
  width: fit-content; margin: 2rem auto 0; padding: 0.7rem 1.4rem; border-radius: 999px; text-align: center;
  background: rgba(255, 255, 255, 0.9); color: var(--text); box-shadow: 0 8px 24px rgba(42, 22, 80, 0.08);
}
.more a { color: var(--pink-600); font-weight: 600; }
.more a:hover { text-decoration: underline; }
</style>
