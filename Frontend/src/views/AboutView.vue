<script setup>
import PageHeader from '../components/common/PageHeader.vue'
import AppIcon from '../components/common/AppIcon.vue'
import radiologistPhoto from '../assets/images/radiologist-mammogram.jpg'
import { APP_ROUTES } from '../config/appRoutes'

const goals = [
  'Make mammogram screening and early detection easier to understand for patients and families.',
  'Give a fast, AI-based second look at a mammogram image as a research aid.',
  'Keep every result reviewable later in one place, with the original image.',
  'Be open about what the tool can and cannot do, and always point people to a doctor.',
]

const stack = [
  { icon: 'heart', name: 'Vue 3', text: 'The website you are using: pages, upload, results and history.' },
  { icon: 'shield-check', name: 'Spring Boot', text: 'The Java backend that receives images and serves the API and website.' },
  { icon: 'microscope', name: 'EfficientNet-B0 + ONNX Runtime', text: 'A neural network pretrained on ImageNet, fine-tuned on mammograms in Python and run inside the backend.' },
  { icon: 'file-text', name: 'MongoDB Atlas', text: 'Cloud database that stores results, uploaded images (GridFS) and contact messages.' },
]
</script>

<template>
  <PageHeader
    eyebrow="About BreastCare"
    title="Helping people take the first step toward early detection"
    subtitle="BreastCare is a team project that combines breast cancer awareness with an AI tool for analysing mammogram images."
  />

  <section class="container section overview">
    <img
      v-reveal
      :src="radiologistPhoto"
      alt="A radiologist reviewing mammogram images on diagnostic monitors"
      class="photo"
      width="800"
      height="452"
      loading="lazy"
    />
    <div v-reveal="120">
      <h2>Project overview</h2>
      <p>
        You upload a mammogram image, and our backend runs a deep-learning model on it to estimate how likely the
        image is to show a malignant finding. The result, together with the image, is saved so you can review it later.
      </p>
      <p>
        Alongside the analysis tool, the site explains how the process works and answers common questions, so the
        result is easier to put in context.
      </p>
    </div>
  </section>

  <section class="container section problem">
    <div v-reveal class="card-block">
      <h2>The problem</h2>
      <p>
        Breast cancer is one of the most common cancers among women worldwide, and finding it early makes treatment
        far more effective. Yet many people delay screening because they do not know what a mammogram involves, or
        they wait a long time to have images reviewed.
      </p>
    </div>
    <div v-reveal="120" class="card-block">
      <h2>Our motivation</h2>
      <p>
        We wanted to see how modern image models can support, not replace, the people who read mammograms, and to
        build something that also teaches the public why early detection matters.
      </p>
    </div>
  </section>

  <section class="container section">
    <h2 v-reveal class="center">Goals</h2>
    <ul class="goals">
      <li v-for="(goal, i) in goals" :key="goal" v-reveal="i * 80">
        <AppIcon name="shield-check" :size="22" class="check" />
        <span>{{ goal }}</span>
      </li>
    </ul>
  </section>

  <section class="container section">
    <h2 v-reveal class="center">Technology</h2>
    <div class="stack">
      <div v-for="(item, i) in stack" :key="item.name" v-reveal="i * 100" class="tech">
        <span class="tech-icon"><AppIcon :name="item.icon" :size="24" /></span>
        <h3>{{ item.name }}</h3>
        <p>{{ item.text }}</p>
      </div>
    </div>
  </section>

  <section class="container section">
    <div v-reveal class="notice">
      <h2>Important</h2>
      <p>
        BreastCare is for research and education only. It is not a medical device, its results can be wrong, and it
        does not replace a diagnosis by a qualified doctor.
      </p>
      <div class="actions">
        <RouterLink :to="APP_ROUTES.analysis" class="btn btn-primary">Start Analysis <AppIcon name="arrow-right" :size="18" /></RouterLink>
        <RouterLink to="/how-it-works" class="btn btn-outline">How It Works</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.section { padding-top: 2.5rem; padding-bottom: 1rem; }
h2 { color: var(--purple-900); font-size: clamp(1.4rem, 3vw, 1.8rem); margin: 0 0 0.9rem; }
.center { text-align: center; }
p { line-height: 1.75; margin: 0 0 1rem; }

.overview { display: grid; grid-template-columns: 1fr 1fr; gap: 2.5rem; align-items: center; }
.photo { width: 100%; height: auto; border-radius: 22px; box-shadow: var(--shadow); }

.problem { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; }
.card-block { padding: 1.75rem; background: #fff; border: 1px solid var(--border); border-radius: 20px; }

.goals { list-style: none; margin: 0 auto; padding: 0; max-width: 760px; display: grid; gap: 0.85rem; }
.goals li {
  display: flex; gap: 0.8rem; align-items: flex-start; padding: 1rem 1.25rem;
  background: #fff; border: 1px solid var(--border); border-radius: 14px; line-height: 1.6;
}
.check { flex: none; color: var(--pink-600); margin-top: 2px; }

.stack { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 1.25rem; }
.tech { padding: 1.5rem; background: #fff; border: 1px solid var(--border); border-radius: 18px; }
.tech-icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px; background: var(--pink-100); color: var(--pink-600); }
.tech h3 { margin: 0.9rem 0 0.4rem; color: var(--purple-900); font-size: 1.05rem; }
.tech p { margin: 0; font-size: 0.93rem; line-height: 1.6; }

.notice { padding: 2rem; border-radius: 22px; background: var(--pink-50); border: 1px solid var(--pink-300); text-align: center; }
.notice p { max-width: 640px; margin: 0 auto 1.5rem; }
.actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 1rem; }

@media (max-width: 820px) {
  .overview, .problem { grid-template-columns: 1fr; }
}
</style>
