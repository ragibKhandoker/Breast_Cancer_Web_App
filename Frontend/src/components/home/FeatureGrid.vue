<script setup>
import AppIcon from '../common/AppIcon.vue'

const features = [
  { icon: 'microscope', title: 'Mammography Prediction', text: 'Upload an image and get instant results.', to: '/prediction' },
  { icon: 'book', title: 'Learn About Breast Cancer', text: 'Know the risks, symptoms and prevention.', to: '/learn' },
  { icon: 'calendar', title: 'Book Appointment', text: 'Consult with specialists near you.', to: '/support' },
  { icon: 'file-text', title: 'Download Reports', text: 'Get your prediction report in PDF.', to: '/history' },
]
</script>

<template>
  <section class="container features">
    <h2 v-reveal class="title"><span class="rule"></span>Explore Our Features<span class="rule"></span></h2>
    <div class="cards">
      <RouterLink v-for="(f, i) in features" :key="f.title" v-reveal="i * 120" :to="f.to" class="feature">
        <AppIcon :name="f.icon" :size="44" :stroke-width="1.8" class="icon" />
        <h3>{{ f.title }}</h3>
        <p>{{ f.text }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.features { padding-bottom: 3rem; }
.title {
  display: flex; align-items: center; justify-content: center; gap: 1rem; width: fit-content;
  margin: 0 auto 1.75rem; padding: 0.5rem 1.5rem; border-radius: 999px;
  background: rgba(255, 255, 255, 0.82); backdrop-filter: blur(8px);
  color: var(--purple-900); font-size: clamp(1.4rem, 3vw, 1.8rem);
}
.rule { width: 70px; height: 2px; background: var(--pink-500); }

.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1.25rem; }
.feature {
  display: flex; flex-direction: column; align-items: center; text-align: center;
  padding: 1.75rem 1.25rem; border: 1px solid var(--border); border-radius: 18px;
  background: linear-gradient(180deg, #fff 0%, var(--pink-50) 100%);
  /* Includes the scroll-reveal fade (v-reveal) as well as the hover lift. */
  transition: opacity 0.7s ease, transform 0.5s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.2s;
}
.feature:hover { transform: translateY(-4px); box-shadow: var(--shadow); }
.icon { color: #6b3fb5; }
h3 { color: var(--purple-900); font-size: 1.05rem; margin: 1rem 0 0.5rem; }
p { margin: 0; color: var(--text); font-size: 0.92rem; line-height: 1.5; }

@media (max-width: 900px) { .cards { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 520px) { .cards { grid-template-columns: 1fr; } .rule { width: 32px; } }
</style>
