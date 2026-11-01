<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../components/common/AppIcon.vue'
import ModuleIllustration from '../components/knowledge/ModuleIllustration.vue'
import { listModules, getGlossary, getFaqs } from '../services/knowledgeService'
import { useLearningProgress } from '../composables/useLearningProgress'
import { errorMessage } from '../services/http'
import { authState } from '../auth/authStore'

// Breast Cancer Knowledge Hub home: every published module as a card, with search, categories, progress,
// "Continue learning", bookmarks, the glossary and the FAQ. Public; progress is saved per account when signed in.
// Shortcuts to sections people often look for.
const QUICK = [
  { label: 'Breast self-examination', to: '/knowledge/symptoms#self-exam' },
  { label: 'BI-RADS guide', to: '/knowledge/mammography-birads#birads' },
  { label: 'TNM staging', to: '/knowledge/stages#tnm' },
  { label: 'Molecular subtypes', to: '/knowledge/classification#molecular' },
]
const modules = ref([])
const glossary = ref([])
const faqs = ref([])
const loading = ref(true)
const error = ref('')
const query = ref('')
const category = ref('All')
const glossaryQuery = ref('')

const { progress, loadProgress, toggleBookmark } = useLearningProgress()

const categories = computed(() => ['All', ...new Set(modules.value.map((m) => m.category))])
const normal = (s) => (s ?? '').toLowerCase()
const filtered = computed(() => {
  const q = normal(query.value).trim()
  return modules.value.filter((m) => {
    if (category.value !== 'All' && m.category !== category.value) return false
    if (!q) return true
    return [m.title, m.description, m.category, ...(m.sectionTitles ?? [])].some((t) => normal(t).includes(q))
  })
})
const glossaryMatches = computed(() => {
  const q = normal(query.value).trim()
  return q.length < 2 ? [] : glossary.value.filter((g) => normal(g.term).includes(q)).slice(0, 4)
})
const shownGlossary = computed(() => {
  const q = normal(glossaryQuery.value).trim()
  return q ? glossary.value.filter((g) => normal(g.term).includes(q) || normal(g.definition).includes(q)) : glossary.value
})

const signedIn = computed(() => !!authState.user)
const done = computed(() => modules.value.filter((m) => progress.completed.includes(m.slug)).length)
const percent = computed(() => (modules.value.length ? Math.round((done.value / modules.value.length) * 100) : 0))
const lastModule = computed(() => modules.value.find((m) => m.slug === progress.lastSlug))
const nextModule = computed(() => modules.value.find((m) => !progress.completed.includes(m.slug)))
const bookmarked = computed(() => modules.value.filter((m) => progress.bookmarks.includes(m.slug)))

onMounted(async () => {
  loadProgress()
  try {
    const [m, g, f] = await Promise.all([listModules(), getGlossary(), getFaqs()])
    modules.value = m
    glossary.value = g
    faqs.value = f
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="kh">
    <header class="kh-hero">
      <div class="container hero-inner">
        <span class="eyebrow"><AppIcon name="book" :size="15" /> Learning center</span>
        <h1>Breast Cancer Knowledge Hub</h1>
        <p class="subtitle">
          Everything you need to know about breast cancer — from understanding its history and classifications to
          diagnosis, treatment, and recovery.
        </p>
        <label class="search">
          <AppIcon name="search" :size="18" />
          <span class="sr-only">Search the Knowledge Hub</span>
          <input v-model="query" type="search" placeholder="Search a topic, e.g. HER2, BI-RADS, lumpectomy" />
        </label>
        <nav class="quick-links" aria-label="Popular topics">
          <span>Popular:</span>
          <router-link v-for="q in QUICK" :key="q.to" :to="q.to">{{ q.label }}</router-link>
        </nav>
        <ul v-if="glossaryMatches.length" class="quick-terms" aria-label="Glossary matches">
          <li v-for="g in glossaryMatches" :key="g.term"><strong>{{ g.term }}:</strong> {{ g.definition }}</li>
        </ul>
      </div>
    </header>

    <div class="container kh-body">
      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-else-if="loading" class="muted center">Loading…</p>

      <template v-else>
        <section class="continue" aria-labelledby="continue-title">
          <div class="progress-box">
            <h2 id="continue-title">Your progress</h2>
            <div class="bar" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100" aria-label="Modules completed">
              <span :style="{ width: `${percent}%` }"></span>
            </div>
            <p class="muted small">{{ done }} of {{ modules.length }} modules completed</p>
            <p v-if="!signedIn" class="muted small">Saved in this browser. <router-link to="/login" class="link">Sign in</router-link> to keep it.</p>
            <p v-if="progress.error" class="small error">{{ progress.error }}</p>
          </div>
          <div class="resume">
            <template v-if="lastModule">
              <span class="small muted">Continue learning</span>
              <router-link :to="`/knowledge/${lastModule.slug}`" class="resume-link">
                {{ lastModule.title }} <AppIcon name="arrow-right" :size="16" />
              </router-link>
            </template>
            <template v-else-if="nextModule">
              <span class="small muted">Start here</span>
              <router-link :to="`/knowledge/${nextModule.slug}`" class="resume-link">
                {{ nextModule.title }} <AppIcon name="arrow-right" :size="16" />
              </router-link>
            </template>
            <div v-if="bookmarked.length" class="bookmarks">
              <span class="small muted">Bookmarks:</span>
              <router-link v-for="m in bookmarked" :key="m.slug" :to="`/knowledge/${m.slug}`" class="bm-chip">{{ m.title }}</router-link>
            </div>
          </div>
        </section>

        <nav class="categories" aria-label="Categories">
          <button v-for="c in categories" :key="c" type="button" :class="{ active: category === c }" :aria-pressed="category === c" @click="category = c">
            {{ c }}
          </button>
        </nav>

        <p v-if="!filtered.length" class="muted center">No module matches “{{ query }}”.</p>
        <div class="grid-modules">
          <article v-for="m in filtered" :key="m.slug" class="module-card">
            <div class="art">
              <ModuleIllustration :name="m.illustration" :alt="m.title" />
              <span v-if="progress.completed.includes(m.slug)" class="done-badge"><AppIcon name="check-circle" :size="14" /> Done</span>
            </div>
            <div class="card-body">
              <div class="card-top">
                <span class="chip">{{ m.category }}</span>
                <button type="button" class="bookmark" :class="{ on: progress.bookmarks.includes(m.slug) }"
                  :aria-pressed="progress.bookmarks.includes(m.slug)" :aria-label="`Bookmark ${m.title}`" @click="toggleBookmark(m.slug)">
                  <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>
                </button>
              </div>
              <h3>{{ m.title }}</h3>
              <p>{{ m.description }}</p>
              <div class="card-foot">
                <span class="time"><AppIcon name="clock" :size="14" /> {{ m.readingMinutes }} min read</span>
                <router-link :to="`/knowledge/${m.slug}`" class="btn btn-primary btn-sm">Explore More</router-link>
              </div>
            </div>
          </article>
        </div>

        <section class="glossary" aria-labelledby="glossary-title">
          <div class="section-head">
            <h2 id="glossary-title">Medical glossary</h2>
            <input v-model="glossaryQuery" type="search" class="small-search" placeholder="Find a term" aria-label="Find a glossary term" />
          </div>
          <dl class="terms">
            <div v-for="g in shownGlossary" :key="g.term" class="term">
              <dt>{{ g.term }}</dt>
              <dd>{{ g.definition }}</dd>
            </div>
          </dl>
          <p v-if="!shownGlossary.length" class="muted">No term found.</p>
        </section>

        <section class="faqs" aria-labelledby="faq-title">
          <h2 id="faq-title">Frequently asked questions</h2>
          <details v-for="f in faqs" :key="f.question" class="faq">
            <summary>{{ f.question }}</summary>
            <p>{{ f.answer }}</p>
          </details>
        </section>

        <p class="disclaimer-note" role="note">
          This hub is for education only. It does not replace advice from your doctor. Photos: Unsplash (National Cancer Institute and others).
        </p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.kh { background: #fff; min-height: 70vh; padding-bottom: 3rem; }
.kh-hero {
  background: radial-gradient(circle at 85% 15%, #fde3ef 0, transparent 45%), linear-gradient(180deg, #fff4f9 0%, #fff 100%);
  padding: 3.2rem 0 2.4rem; border-bottom: 1px solid #f8e3ed;
}
.hero-inner { max-width: 860px; text-align: center; display: grid; justify-items: center; gap: 0.9rem; }
.eyebrow { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.3rem 0.9rem; border-radius: 999px; background: #fde3ef; color: #a3134f; font-weight: 600; font-size: 0.85rem; }
h1 { margin: 0; color: #2a1650; font-size: clamp(2rem, 4.5vw, 2.9rem); line-height: 1.15; }
.subtitle { margin: 0; max-width: 680px; color: #3d3557; font-size: 1.08rem; line-height: 1.65; }
.search {
  display: flex; align-items: center; gap: 0.6rem; width: min(620px, 100%); padding: 0.75rem 1rem; margin-top: 0.4rem;
  background: #fff; border: 2px solid #f3d4e3; border-radius: 14px; color: #d81b6a; box-shadow: 0 10px 30px rgba(216, 27, 106, 0.08);
}
.search:focus-within { border-color: #e84a8a; }
.search input { flex: 1; border: 0; outline: 0; font: inherit; color: #2a1650; background: transparent; min-width: 0; }
.quick-links { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 0.45rem; font-size: 0.88rem; }
.quick-links span { color: #6f6787; }
.quick-links a { padding: 0.3rem 0.8rem; border-radius: 999px; background: #fff; border: 1.5px solid #f3d4e3; color: #a3134f; font-weight: 600; }
.quick-links a:hover { border-color: #d81b6a; background: #fff0f6; }
.quick-terms { list-style: none; margin: 0; padding: 0.75rem 1rem; width: min(620px, 100%); text-align: left; display: grid; gap: 0.35rem; background: #faf6ff; border: 1px solid #e3dbf5; border-radius: 12px; font-size: 0.9rem; color: #3d3557; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

.kh-body { display: grid; gap: 1.6rem; padding-top: 1.8rem; }
.center { text-align: center; }
.small { font-size: 0.85rem; }
h2 { margin: 0; color: #2a1650; font-size: 1.3rem; }

.continue { display: grid; grid-template-columns: minmax(220px, 1fr) 2fr; gap: 1rem; }
.progress-box, .resume { padding: 1.1rem 1.2rem; background: #fff; border: 1px solid #f3d4e3; border-radius: 16px; box-shadow: 0 8px 24px rgba(216, 27, 106, 0.06); }
.progress-box { display: grid; gap: 0.55rem; }
.bar { height: 10px; background: #fde3ef; border-radius: 999px; overflow: hidden; }
.bar span { display: block; height: 100%; background: linear-gradient(90deg, #e84a8a, #6b3fb5); border-radius: inherit; transition: width 0.4s ease; }
.progress-box p { margin: 0; }
.resume { display: grid; gap: 0.35rem; align-content: center; }
.resume-link { display: inline-flex; align-items: center; gap: 0.4rem; color: #d81b6a; font-weight: 700; font-size: 1.05rem; }
.resume-link:hover, .link { text-decoration: underline; }
.link { color: #d81b6a; font-weight: 600; }
.bookmarks { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; margin-top: 0.4rem; }
.bm-chip { padding: 0.2rem 0.7rem; border-radius: 999px; background: #faf6ff; border: 1px solid #e3dbf5; color: #4b2d8a; font-size: 0.82rem; font-weight: 500; }

.categories { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.categories button { padding: 0.45rem 1rem; border-radius: 999px; border: 1.5px solid #f3d4e3; background: #fff; color: #2a1650; font: 500 0.9rem 'Poppins', sans-serif; cursor: pointer; }
.categories button:hover { border-color: #e84a8a; }
.categories button.active { background: #d81b6a; border-color: #d81b6a; color: #fff; }

.grid-modules { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1.1rem; }
.module-card { display: grid; grid-template-rows: auto 1fr; background: #fff; border: 1px solid #f3d4e3; border-radius: 18px; overflow: hidden; box-shadow: 0 10px 28px rgba(216, 27, 106, 0.07); transition: transform 0.2s, box-shadow 0.2s; }
.module-card:hover { transform: translateY(-3px); box-shadow: 0 16px 34px rgba(216, 27, 106, 0.13); }
.art { position: relative; padding: 0.8rem 0.8rem 0; }
.done-badge { position: absolute; top: 1.2rem; left: 1.2rem; display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.2rem 0.6rem; border-radius: 999px; background: #e8f5e9; color: #1b5e20; font-size: 0.75rem; font-weight: 600; }
.card-body { display: grid; grid-template-rows: auto auto 1fr auto; gap: 0.5rem; padding: 0.9rem 1.1rem 1.1rem; }
.card-top { display: flex; justify-content: space-between; align-items: center; }
.chip { padding: 0.15rem 0.65rem; border-radius: 999px; background: #fde3ef; color: #a3134f; font-size: 0.75rem; font-weight: 600; }
.bookmark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; border: 1px solid #f3d4e3; background: #fff; cursor: pointer; }
.bookmark path { fill: none; stroke: #d81b6a; stroke-width: 2; stroke-linejoin: round; }
.bookmark.on path { fill: #d81b6a; }
.bookmark:focus-visible { outline: 3px solid #f7a8c8; outline-offset: 2px; }
.module-card h3 { margin: 0; color: #2a1650; font-size: 1.08rem; }
.module-card p { margin: 0; color: #4a4263; font-size: 0.92rem; line-height: 1.6; }
.card-foot { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; padding-top: 0.4rem; }
.time { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.82rem; color: #6f6787; white-space: nowrap; }
.card-foot .btn { white-space: nowrap; }

.section-head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; margin-bottom: 0.8rem; }
.small-search { padding: 0.5rem 0.8rem; border: 1.5px solid #f3d4e3; border-radius: 10px; font: inherit; color: #2a1650; background: #fff; }
.small-search:focus { outline: 0; border-color: #e84a8a; }
.terms { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 0.6rem; margin: 0; max-height: 460px; overflow-y: auto; padding-right: 0.25rem; }
.term { padding: 0.75rem 0.9rem; background: #fff; border: 1px solid #f3d4e3; border-radius: 12px; }
.term dt { font-weight: 600; color: #a3134f; }
.term dd { margin: 0.2rem 0 0; color: #3d3557; font-size: 0.9rem; line-height: 1.55; }

.faqs { display: grid; gap: 0.55rem; }
.faqs h2 { margin-bottom: 0.3rem; }
.faq { background: #fff; border: 1px solid #f3d4e3; border-radius: 14px; padding: 0 1.1rem; }
.faq[open] { border-color: #f7a8c8; box-shadow: 0 8px 22px rgba(216, 27, 106, 0.08); }
.faq summary { cursor: pointer; padding: 0.95rem 0; font-weight: 600; color: #2a1650; }
.faq p { margin: 0 0 1rem; color: #3d3557; line-height: 1.65; }
.disclaimer-note { margin: 0; padding: 0.8rem 1rem; border-radius: 12px; background: #fff4e5; color: #6b3a00; text-align: center; font-size: 0.9rem; }

@media (max-width: 720px) {
  .continue { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .module-card, .bar span { transition: none; }
  .module-card:hover { transform: none; }
}
</style>
