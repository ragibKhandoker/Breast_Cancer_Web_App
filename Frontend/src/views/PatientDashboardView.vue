<script setup>
import { isMalignant } from '../prediction/verdict'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { authState, onSessionEnd } from '../auth/authStore'
import { listPredictions, imageUrl, errorMessage } from '../services/predictionService'
import { APP_ROUTES } from '../config/appRoutes'
import { myReviewRequests, REVIEW_STATUS } from '../services/careService'

const recent = ref([])
const reviews = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')

async function load() {
  try {
    myReviewRequests().then((r) => (reviews.value = r.filter((x) => x.status !== 'WITHDRAWN').slice(0, 5))).catch(() => {})
    const all = await listPredictions()
    total.value = all.length
    recent.value = all.slice(0, 3)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const percent = (p) => Math.round(p.malignantProbability * 1000) / 10

const stop = onSessionEnd(() => {
  recent.value = []
  reviews.value = []
})
onBeforeUnmount(stop)
onMounted(load)
</script>

<template>
  <section class="container page">
    <h1>Hello, {{ authState.user?.fullName }}</h1>
    <p class="muted">Your mammogram analyses are private to you. You choose which doctors can see each report.</p>

    <div class="actions">
      <RouterLink :to="APP_ROUTES.analysis" class="card action">
        <strong>New analysis</strong>
        <span class="muted">Upload a PNG or JPG mammogram</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.history" class="card action">
        <strong>My history</strong>
        <span class="muted">{{ loading ? 'Loading…' : `${total} saved ${total === 1 ? 'analysis' : 'analyses'}` }}</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.messages" class="card action">
        <strong>Messages</strong>
        <span class="muted">Talk to the BreastCare administrators</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.surgeons" class="card action">
        <strong>Find a Breast Surgeon</strong>
        <span class="muted">Book a consultation with a verified Breast Surgeon</span>
      </RouterLink>
      <RouterLink :to="APP_ROUTES.myAppointments" class="card action">
        <strong>My appointments</strong>
        <span class="muted">See, reschedule or cancel your Breast Surgeon appointments</span>
      </RouterLink>
    </div>

    <template v-if="reviews.length">
      <h2>Doctor reviews</h2>
      <ul class="reviews">
        <li v-for="r in reviews" :key="r.id" class="card review">
          <div>
            <strong>{{ r.doctorName }}</strong>
            <span class="muted small"> · asked {{ new Date(r.createdAt).toLocaleDateString() }}</span>
            <p v-if="r.doctorNote" class="note">{{ r.doctorNote }}</p>
          </div>
          <span class="pill" :class="r.status.toLowerCase()">{{ REVIEW_STATUS[r.status] }}</span>
          <RouterLink :to="`/reports/${r.predictionId}`" class="open">Open report</RouterLink>
        </li>
      </ul>
    </template>

    <h2>Recent reports</h2>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!recent.length" class="muted">No analyses yet. Start with a new analysis above.</p>
    <div v-else class="grid">
      <RouterLink v-for="p in recent" :key="p.id" :to="`/reports/${p.id}`" class="card history-item">
        <img :src="imageUrl(p.id)" :alt="p.originalFilename" class="thumb" />
        <p class="filename">{{ p.originalFilename }}</p>
        <p :class="isMalignant(p) ? 'high' : 'low'">{{ isMalignant(p) ? 'Malignant' : 'Benign' }} · {{ percent(p) }}% malignancy</p>
        <p class="meta">{{ new Date(p.createdAt).toLocaleString() }}</p>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
h2 { color: var(--purple-900); margin: 2rem 0 0; font-size: 1.3rem; }
.actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; margin-top: 1.25rem; }
.action { display: grid; gap: 0.25rem; transition: transform 0.15s, border-color 0.15s; }
.action strong { color: var(--pink-600); font-size: 1.1rem; }
.action:hover { transform: translateY(-2px); border-color: var(--pink-300); }
.reviews { list-style: none; margin: 0.8rem 0 0; padding: 0; display: grid; gap: 0.6rem; }
.review { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 0.8rem; padding: 0.9rem 1.1rem; }
.review strong { color: var(--purple-900); }
.small { font-size: 0.86rem; }
.note { margin: 0.35rem 0 0; color: var(--text); white-space: pre-line; display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.pill { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.76rem; font-weight: 600; white-space: nowrap; }
.pill.requested { background: #fff4e5; color: #8a4b00; }
.pill.reviewed { background: #e8f5e9; color: #1b5e20; }
.open { color: var(--pink-600); font-weight: 600; text-decoration: underline; white-space: nowrap; }
@media (max-width: 600px) { .review { grid-template-columns: 1fr; } }
</style>
