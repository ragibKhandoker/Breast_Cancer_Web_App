<script setup>
import AppIcon from '../common/AppIcon.vue'
import PinkRibbon from '../common/PinkRibbon.vue'
import radiologistPhoto from '../../assets/images/radiologist-mammogram.jpg'
</script>

<template>
  <section class="container about">
    <figure v-reveal class="frame">
      <img
        :src="radiologistPhoto"
        alt="A radiologist reviewing mammogram images on diagnostic monitors"
        width="800"
        height="452"
        loading="lazy"
      />
    </figure>

    <div v-reveal="150" class="copy">
      <span class="pill">About Our Platform</span>
      <h2>Better Awareness. Better Care.</h2>
      <p>
        We are committed to raising awareness about breast cancer, providing reliable information, and offering
        advanced AI tools for early detection and support.
      </p>
      <RouterLink to="/about" class="btn btn-primary">Explore More <AppIcon name="arrow-right" :size="18" /></RouterLink>
    </div>

    <PinkRibbon class="ribbon" :size="120" aria-hidden="true" />
  </section>
</template>

<style scoped>
.about { display: grid; grid-template-columns: 1.15fr 1.2fr auto; align-items: center; gap: 2.5rem; padding-top: 3.5rem; padding-bottom: 3rem; }

.frame {
  margin: 0; padding: 10px; border: 2px dashed var(--pink-300); border-radius: 26px;
  background: rgba(255, 255, 255, 0.85); box-shadow: var(--shadow);
}
.frame img {
  width: 100%; height: auto; aspect-ratio: 16 / 10; object-fit: cover; object-position: 40% center;
  border-radius: 18px; transition: transform 0.6s ease;
}
.frame:hover img { transform: scale(1.02); }

/* Frosted panel so the text stays readable over the Home page photo. */
.copy {
  padding: 1.75rem 2rem; border-radius: 22px;
  background: rgba(255, 255, 255, 0.82); backdrop-filter: blur(8px); box-shadow: var(--shadow);
}
.pill { display: inline-block; padding: 0.3rem 0.9rem; border-radius: 999px; background: var(--pink-100); color: var(--pink-600); font-weight: 600; font-size: 0.9rem; }
h2 { color: var(--purple-900); font-size: clamp(1.6rem, 3vw, 2.1rem); margin: 0.75rem 0; }
.copy p { line-height: 1.6; margin: 0 0 1.5rem; max-width: 470px; }
.ribbon { transform: rotate(12deg); opacity: 0.95; }

@media (max-width: 900px) {
  .about { grid-template-columns: 1fr; text-align: center; }
  .copy p { margin-left: auto; margin-right: auto; }
  .ribbon { display: none; }
}
</style>
