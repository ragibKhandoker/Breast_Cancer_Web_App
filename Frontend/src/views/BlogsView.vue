<script setup>
import { ref, computed } from 'vue'
import PageHeader from '../components/common/PageHeader.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { site } from '../config/siteSettings'
import { NEWS, NEWS_CATEGORIES } from '../content/news'

// Blogs: recent breast cancer news reports, research and awareness events from trusted outlets, each with a photo and
// a link to the original article, plus the contact details. The radiologists photo sits behind the page, lightly blurred.
const category = ref('All')
const sorted = [...NEWS].sort((a, b) => b.date.localeCompare(a.date))
const shown = computed(() => (category.value === 'All' ? sorted : sorted.filter((n) => n.category === category.value)))
const day = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
</script>

<template>
  <div class="contact-page">
    <PageHeader
      eyebrow="Blogs"
      title="Breast cancer blogs"
      subtitle="News reports, research and awareness events from trusted outlets, and how to reach us."
    />

    <section class="container section">
      <div class="contact-strip">
        <span class="strip-title"><AppIcon name="message" :size="18" /> Get in touch</span>
        <a v-if="site.contact.email" :href="`mailto:${site.contact.email}`" class="strip-item">
          <AppIcon name="mail" :size="16" /> {{ site.contact.email }}
        </a>
        <a v-if="site.contact.phone" :href="`tel:${site.contact.phone.replace(/\s+/g, '')}`" class="strip-item">
          <AppIcon name="phone" :size="16" /> {{ site.contact.phone }}
        </a>
        <span v-if="site.contact.address" class="strip-item"><AppIcon name="map-pin" :size="16" /> {{ site.contact.address }}</span>
      </div>

      <div class="news-head">
        <h2>Latest blogs</h2>
        <nav class="filters" aria-label="News categories">
          <button v-for="c in NEWS_CATEGORIES" :key="c" type="button" :class="{ on: category === c }" :aria-pressed="category === c"
            @click="category = c">{{ c }}</button>
        </nav>
      </div>

      <div class="news-grid">
        <article v-for="n in shown" :key="n.url" class="news-card">
          <a :href="n.url" target="_blank" rel="noopener noreferrer" class="media" tabindex="-1" aria-hidden="true">
            <img :src="n.image" :alt="n.imageAlt" class="cover" decoding="async" />
            <span class="chip" :class="n.category.toLowerCase()">{{ n.category }}</span>
          </a>
          <div class="body">
            <div class="meta-row">
              <span class="outlet">{{ n.outlet }}</span>
              <span class="when"><AppIcon name="calendar" :size="14" /> {{ day(n.date) }}</span>
            </div>
            <h3><a :href="n.url" target="_blank" rel="noopener noreferrer">{{ n.title }}</a></h3>
            <p>{{ n.summary }}</p>
            <a :href="n.url" target="_blank" rel="noopener noreferrer" class="read">
              Read full story <AppIcon name="arrow-right" :size="15" />
            </a>
          </div>
        </article>
      </div>

      <p class="note">
        <AppIcon name="alert-circle" :size="18" />
        <span>Blogs are for general information. For your own health, talk to a doctor. In an emergency, go to the nearest hospital.</span>
      </p>
    </section>
  </div>
</template>

<style scoped>
/* The radiologists photo fills the page behind everything, with a soft wash and only a slight blur. */
.contact-page { position: relative; isolation: isolate; min-height: 100vh; }
.contact-page::before {
  content: ''; position: fixed; inset: -8px; z-index: -1; pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 246, 250, 0.7) 0%, rgba(255, 246, 250, 0.55) 40%, rgba(246, 240, 255, 0.7) 100%),
    url('../assets/images/contact-radiologists.webp') center 35% / cover no-repeat;
  filter: blur(1.5px);
}
.contact-page :deep(.page-header) { background: transparent; }
.contact-page :deep(.page-header .inner) {
  padding: 1.6rem 2rem; border-radius: 24px; background: rgba(255, 255, 255, 0.85); box-shadow: 0 16px 40px rgba(42, 22, 80, 0.1);
}
.section { padding-top: 1.5rem; padding-bottom: 3.5rem; display: grid; gap: 1.4rem; }

.contact-strip {
  display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem 1.2rem; padding: 0.9rem 1.3rem; border-radius: 18px;
  background: rgba(255, 255, 255, 0.92); border: 1px solid #f3d4e3; box-shadow: 0 10px 28px rgba(42, 22, 80, 0.08);
}
.strip-title { display: inline-flex; align-items: center; gap: 0.4rem; font-weight: 700; color: #2a1650; }
.strip-title :deep(svg) { color: #d81b6a; }
.strip-item { display: inline-flex; align-items: center; gap: 0.35rem; color: #3d3557; font-size: 0.92rem; overflow-wrap: anywhere; }
.strip-item :deep(svg) { flex: none; color: #d81b6a; }
a.strip-item:hover { color: #d81b6a; text-decoration: underline; }

.news-head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.8rem; }
.news-head h2 {
  margin: 0; padding: 0.35rem 1rem; border-radius: 999px; background: rgba(255, 255, 255, 0.9); color: #2a1650; font-size: 1.35rem;
}
.filters { display: flex; flex-wrap: wrap; gap: 0.45rem; }
.filters button {
  padding: 0.42rem 0.95rem; border-radius: 999px; border: 1.5px solid #f3d4e3; background: rgba(255, 255, 255, 0.95);
  color: #2a1650; font: 500 0.88rem 'Poppins', sans-serif; cursor: pointer;
}
.filters button.on { background: #d81b6a; border-color: #d81b6a; color: #fff; }
.filters button:focus-visible { outline: 3px solid #f7a8c8; outline-offset: 2px; }

/* Every card the same size: one photo height, titles up to three lines, summaries up to four, link at the bottom. */
.news-grid { --gap: 1.25rem; display: flex; flex-wrap: wrap; justify-content: flex-start; gap: var(--gap); }
/* Three equal cards a row, always starting from the left (one card on its own sits on the left, not in the middle). */
.news-card {
  flex: 0 0 calc((100% - 2 * var(--gap)) / 3);
  display: flex; flex-direction: column; overflow: hidden; border-radius: 22px;
  background: #fff; border: 1px solid #f3d4e3; box-shadow: 0 12px 30px rgba(42, 22, 80, 0.1);
  transition: transform 0.25s, box-shadow 0.25s;
}
.news-card:hover { transform: translateY(-4px); box-shadow: 0 20px 40px rgba(42, 22, 80, 0.18); }
.media { position: relative; display: block; height: 210px; overflow: hidden; background: #f3d4e3; }
.media::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(42, 22, 80, 0) 55%, rgba(42, 22, 80, 0.35) 100%); }
.cover { display: block; width: 100%; height: 100%; object-fit: cover; transition: transform 0.5s ease; }
.news-card:hover .cover { transform: scale(1.05); }
.chip {
  position: absolute; z-index: 1; top: 0.85rem; left: 0.85rem; padding: 0.25rem 0.75rem; border-radius: 999px;
  font-size: 0.75rem; font-weight: 700; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.chip.bangladesh { background: #e6f4ea; color: #1b5e20; }
.chip.research { background: #ede7fb; color: #4b2d8a; }
.chip.awareness { background: #fde3ef; color: #a3134f; }
.chip.treatment { background: #e3f0fb; color: #0d4c86; }
.body { flex: 1; display: flex; flex-direction: column; gap: 0.6rem; padding: 1.1rem 1.25rem 1.25rem; }
.meta-row { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; font-size: 0.8rem; }
.outlet { font-weight: 700; color: #d81b6a; text-transform: uppercase; letter-spacing: 0.04em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.when { flex: none; display: inline-flex; align-items: center; gap: 0.3rem; color: #6f6787; }
.news-card h3 {
  margin: 0; font-size: 1.08rem; line-height: 1.35; min-height: calc(1.08rem * 1.35 * 3);
  display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.news-card h3 a { color: #2a1650; }
.news-card h3 a:hover { color: #d81b6a; }
.news-card p {
  margin: 0; color: #4a4263; font-size: 0.92rem; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 4; line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden;
}
.read {
  margin-top: auto; align-self: flex-start; display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.5rem 1rem;
  border-radius: 999px; background: #fff0f6; color: #d81b6a; font-weight: 600; font-size: 0.88rem; transition: background 0.2s, color 0.2s;
}
.read:hover { background: #d81b6a; color: #fff; }
.read:focus-visible, .news-card h3 a:focus-visible { outline: 3px solid #f7a8c8; outline-offset: 2px; }

.note {
  display: flex; gap: 0.6rem; align-items: flex-start; margin: 0; padding: 0.9rem 1rem; border-radius: 14px;
  background: rgba(255, 255, 255, 0.92); border: 1px solid #f3d4e3; font-size: 0.88rem; color: #4a4263; line-height: 1.55;
}
.note :deep(svg) { flex: none; margin-top: 0.1rem; color: #d81b6a; }

@media (max-width: 1000px) {
  .news-card { flex-basis: calc((100% - var(--gap)) / 2); }
}
@media (max-width: 640px) {
  .news-card { flex-basis: 100%; }
  .news-card h3 { min-height: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .news-card, .cover { transition: none; }
  .news-card:hover, .news-card:hover .cover { transform: none; }
}
</style>
