<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import ModuleIllustration from '../components/knowledge/ModuleIllustration.vue'
import KnowledgeSection from '../components/knowledge/KnowledgeSection.vue'
import YouTubeEmbed from '../components/common/YouTubeEmbed.vue'
import { getModule, listModules } from '../services/knowledgeService'
import { useLearningProgress } from '../composables/useLearningProgress'
import { errorMessage } from '../services/http'

// One Knowledge Hub lesson: table of contents, reading progress, sections, references, related lessons and
// previous/next links. Opening it records it for "Continue learning".
const route = useRoute()
const module = ref(null)
const all = ref([])
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const activeId = ref('')
const readPercent = ref(0)
const article = ref(null)

const { progress, loadProgress, markCompleted, toggleBookmark, rememberLast } = useLearningProgress()

const slug = computed(() => route.params.slug)
const index = computed(() => all.value.findIndex((m) => m.slug === slug.value))
const prev = computed(() => (index.value > 0 ? all.value[index.value - 1] : null))
const next = computed(() => (index.value >= 0 && index.value < all.value.length - 1 ? all.value[index.value + 1] : null))
const related = computed(() => (module.value?.related ?? []).map((s) => all.value.find((m) => m.slug === s)).filter(Boolean))
const completed = computed(() => progress.completed.includes(slug.value))
const bookmarked = computed(() => progress.bookmarks.includes(slug.value))

async function load() {
  loading.value = true
  error.value = ''
  notFound.value = false
  module.value = null
  try {
    const [m, list] = await Promise.all([getModule(slug.value), all.value.length ? all.value : listModules()])
    module.value = m
    all.value = list
    document.title = `${m.title} | Knowledge Hub | BreastCare`
    rememberLast(m.slug)
  } catch (err) {
    if (err.response?.status === 404) notFound.value = true
    else error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
  await nextTick()
  observeSections()
  onScroll()
  // A link to one section (/knowledge/symptoms#self-exam) opens at that section once the lesson is shown.
  if (route.hash) document.getElementById(decodeURIComponent(route.hash.slice(1)))?.scrollIntoView({ block: 'start' })
}

// Highlights the section being read in the table of contents.
let observer = null
function observeSections() {
  observer?.disconnect()
  if (!module.value || typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver((entries) => {
    const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
    if (visible[0]) activeId.value = visible[0].target.id
  }, { rootMargin: '-90px 0px -60% 0px' })
  ;[...(module.value.video ? [{ id: 'video' }] : []), ...module.value.sections].forEach((s) => {
    const el = document.getElementById(s.id)
    if (el) observer.observe(el)
  })
}

function onScroll() {
  const el = article.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const total = rect.height - window.innerHeight
  readPercent.value = total <= 0 ? 100 : Math.min(100, Math.max(0, Math.round((-rect.top / total) * 100)))
}

function goTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  loadProgress()
  load()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('scroll', onScroll)
})
watch(slug, (value, old) => {
  if (value && value !== old) {
    window.scrollTo({ top: 0 })
    load()
  }
})
</script>

<template>
  <div class="km">
    <div class="read-bar" aria-hidden="true"><span :style="{ width: `${readPercent}%` }"></span></div>

    <div class="container">
      <nav class="crumbs" aria-label="Breadcrumb">
        <router-link to="/knowledge">Knowledge Hub</router-link>
        <span aria-hidden="true">/</span>
        <span>{{ module?.category ?? 'Lesson' }}</span>
      </nav>

      <p v-if="loading" class="muted center">Loading…</p>
      <div v-else-if="notFound" class="empty">
        <h1>Lesson not found</h1>
        <router-link to="/knowledge" class="btn btn-primary">Back to the Knowledge Hub</router-link>
      </div>
      <p v-else-if="error" class="alert error">{{ error }}</p>

      <template v-else-if="module">
        <header class="lesson-head">
          <div class="head-art"><ModuleIllustration :name="module.illustration" :alt="module.title" /></div>
          <div class="head-text">
            <span class="chip">{{ module.category }}</span>
            <h1>{{ module.title }}</h1>
            <p class="lead">{{ module.description }}</p>
            <div class="head-actions">
              <span class="time"><AppIcon name="clock" :size="15" /> {{ module.readingMinutes }} min read</span>
              <button type="button" class="btn btn-outline btn-sm" :aria-pressed="bookmarked" @click="toggleBookmark(module.slug)">
                <AppIcon name="bookmark" :size="15" :filled="bookmarked" />
                {{ bookmarked ? 'Bookmarked' : 'Bookmark' }}
              </button>
              <button type="button" class="btn btn-sm" :class="completed ? 'btn-done' : 'btn-primary'" @click="markCompleted(module.slug, !completed)">
                <AppIcon name="check-circle" :size="15" /> {{ completed ? 'Completed' : 'Mark as completed' }}
              </button>
            </div>
            <p v-if="progress.error" class="small error">{{ progress.error }}</p>
          </div>
        </header>

        <div class="layout">
          <aside class="toc">
            <details open class="toc-box">
              <summary>Contents</summary>
              <ol>
                <li v-if="module.video"><a href="#video" :class="{ active: activeId === 'video' }" @click.prevent="goTo('video')">Watch the video</a></li>
                <li v-for="s in module.sections" :key="s.id">
                  <a :href="`#${s.id}`" :class="{ active: activeId === s.id }" @click.prevent="goTo(s.id)">{{ s.title }}</a>
                </li>
                <li><a href="#references" @click.prevent="goTo('references')">References</a></li>
              </ol>
              <p class="toc-progress small">{{ readPercent }}% read</p>
            </details>
          </aside>

          <article ref="article" class="content">
            <section v-if="module.video" id="video" class="video-block">
              <h2><AppIcon name="youtube" :size="22" /> Watch: {{ module.video.title }}</h2>
              <YouTubeEmbed :url="`https://www.youtube.com/watch?v=${module.video.youtubeId}`" :title="module.video.title" />
              <p class="video-note">
                Video from <strong>{{ module.video.channel || 'YouTube' }}</strong> on YouTube, for general education. It is not medical advice for you.
              </p>
            </section>
            <KnowledgeSection v-for="s in module.sections" :key="s.id" :section="s" />

            <section id="references" class="refs">
              <h2>References</h2>
              <ol>
                <li v-for="r in module.references" :key="r.url">
                  <a :href="r.url" target="_blank" rel="noopener noreferrer">{{ r.title }}</a>
                  <span v-if="r.publisher" class="muted"> · {{ r.publisher }}</span>
                </li>
              </ol>
            </section>

            <p class="disclaimer-note" role="note">For education only. Talk to your doctor about your own care.</p>

            <div class="finish">
              <button v-if="!completed" type="button" class="btn btn-primary" @click="markCompleted(module.slug, true)">
                <AppIcon name="check-circle" :size="16" /> I finished this lesson
              </button>
              <p v-else class="done-text"><AppIcon name="check-circle" :size="16" /> Lesson completed</p>
            </div>

            <section v-if="related.length" class="related">
              <h2>Related lessons</h2>
              <div class="related-grid">
                <router-link v-for="r in related" :key="r.slug" :to="`/knowledge/${r.slug}`" class="related-card">
                  <ModuleIllustration :name="r.illustration" :alt="r.title" />
                  <strong>{{ r.title }}</strong>
                  <span class="small muted">{{ r.readingMinutes }} min read</span>
                </router-link>
              </div>
            </section>

            <nav class="pager" aria-label="Lessons">
              <router-link v-if="prev" :to="`/knowledge/${prev.slug}`" class="pager-link">
                <span class="small muted">Previous</span><strong>{{ prev.title }}</strong>
              </router-link>
              <span v-else></span>
              <router-link v-if="next" :to="`/knowledge/${next.slug}`" class="pager-link next">
                <span class="small muted">Next</span><strong>{{ next.title }}</strong>
              </router-link>
            </nav>
          </article>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.km { background: #fff; padding-bottom: 3rem; min-height: 70vh; }
.read-bar { position: sticky; top: 0; z-index: 20; height: 4px; background: #fde3ef; }
.read-bar span { display: block; height: 100%; background: linear-gradient(90deg, #e84a8a, #6b3fb5); }
.crumbs { display: flex; gap: 0.5rem; padding: 1.2rem 0 0.4rem; font-size: 0.9rem; color: #6f6787; }
.crumbs a { color: #d81b6a; font-weight: 600; }
.center { text-align: center; }
.small { font-size: 0.85rem; }
.empty { display: grid; justify-items: center; gap: 1rem; padding: 3rem 0; }
.empty h1 { margin: 0; color: #2a1650; }

.lesson-head {
  display: grid; grid-template-columns: 220px 1fr; gap: 1.5rem; align-items: center; margin: 0.6rem 0 1.6rem; padding: 1.4rem;
  background: linear-gradient(135deg, #fff4f9, #fff 60%); border: 1px solid #f3d4e3; border-radius: 20px;
}
.head-text { display: grid; gap: 0.6rem; justify-items: start; }
.chip { padding: 0.15rem 0.7rem; border-radius: 999px; background: #fde3ef; color: #a3134f; font-size: 0.78rem; font-weight: 600; }
.lesson-head h1 { margin: 0; color: #2a1650; font-size: clamp(1.7rem, 3.6vw, 2.4rem); line-height: 1.2; }
.lead { margin: 0; color: #3d3557; font-size: 1.04rem; line-height: 1.65; }
.head-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; }
.time { display: inline-flex; align-items: center; gap: 0.3rem; color: #6f6787; font-size: 0.88rem; margin-right: 0.3rem; }
.btn-outline { border: 1.5px solid #f7a8c8; }
.btn-done { background: #e8f5e9; color: #1b5e20; border: 1.5px solid #a5d6a7; }
.btn { display: inline-flex; align-items: center; gap: 0.35rem; }

.layout { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 2rem; align-items: start; }
.toc { position: sticky; top: 90px; }
.toc-box { background: #fff; border: 1px solid #f3d4e3; border-radius: 16px; padding: 0.4rem 1rem 0.8rem; }
.toc-box summary { cursor: pointer; padding: 0.6rem 0; font-weight: 700; color: #2a1650; }
.toc ol { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.15rem; }
.toc a { display: block; padding: 0.35rem 0.6rem; border-radius: 8px; border-left: 3px solid transparent; color: #4a4263; font-size: 0.9rem; line-height: 1.4; }
.toc a:hover { background: #fff4f9; color: #d81b6a; }
.toc a.active { border-left-color: #d81b6a; background: #fff0f6; color: #a3134f; font-weight: 600; }
.toc-progress { margin: 0.6rem 0 0; color: #6f6787; }

.content { display: grid; gap: 2.2rem; min-width: 0; }
.video-block { display: grid; gap: 0.8rem; scroll-margin-top: 90px; }
.video-block h2 { display: flex; align-items: center; gap: 0.5rem; margin: 0; color: #2a1650; font-size: 1.3rem; }
.video-block h2 :deep(svg) { flex: none; color: #d81b6a; }
.video-note { margin: 0; font-size: 0.86rem; color: #6f6787; }
.refs h2, .related h2 { margin: 0 0 0.75rem; color: #2a1650; font-size: 1.3rem; }
.refs ol { margin: 0; padding-left: 1.3rem; display: grid; gap: 0.45rem; color: #3d3557; line-height: 1.5; }
.refs a { color: #b0155a; font-weight: 500; text-decoration: underline; text-underline-offset: 2px; overflow-wrap: anywhere; }
.disclaimer-note { margin: 0; padding: 0.8rem 1rem; border-radius: 12px; background: #fff4e5; color: #6b3a00; font-size: 0.92rem; }
.finish { display: flex; justify-content: center; }
.done-text { display: inline-flex; align-items: center; gap: 0.4rem; margin: 0; color: #1b5e20; font-weight: 600; }

.related-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 0.8rem; }
.related-card { display: grid; gap: 0.4rem; padding: 0.7rem; border: 1px solid #f3d4e3; border-radius: 14px; background: #fff; color: #2a1650; transition: box-shadow 0.2s; }
.related-card:hover { box-shadow: 0 10px 24px rgba(216, 27, 106, 0.12); }

.pager { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
.pager-link { display: grid; gap: 0.2rem; padding: 0.9rem 1.1rem; border: 1px solid #f3d4e3; border-radius: 14px; background: #fff; color: #2a1650; }
.pager-link:hover { border-color: #e84a8a; }
.pager-link.next { text-align: right; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .toc { position: static; }
  .lesson-head { grid-template-columns: 1fr; }
  .head-art { max-width: 220px; }
}
@media (max-width: 560px) {
  .pager { grid-template-columns: 1fr; }
  .pager-link.next { text-align: left; }
}
</style>
