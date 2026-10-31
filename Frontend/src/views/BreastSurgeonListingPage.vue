<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import PageHeader from '../components/common/PageHeader.vue'
import AppIcon from '../components/common/AppIcon.vue'
import SurgeonCard from '../components/appointments/SurgeonCard.vue'
import { listSurgeons } from '../services/surgeonService'
import { errorMessage } from '../services/http'
import { authState } from '../auth/authStore'
import { ROLES } from '../auth/roles'
import { APP_ROUTES } from '../config/appRoutes'

// Find a Breast Surgeon: approved Breast Surgeons only (the server decides who is listed). Searching and
// filtering also happen on the server; the hospital list comes from the unfiltered listing.
const surgeons = ref([])
const hospitals = ref([])
const loading = ref(true)
const error = ref('')
const filters = reactive({ q: '', minExperience: '', hospital: '' })
const EXPERIENCE = [
  { value: '', label: 'Any experience' },
  { value: 5, label: '5+ years' },
  { value: 10, label: '10+ years' },
  { value: 15, label: '15+ years' },
  { value: 20, label: '20+ years' },
]

let requestNo = 0
async function load() {
  const mine = ++requestNo
  loading.value = true
  error.value = ''
  try {
    const result = await listSurgeons({ ...filters, q: filters.q.trim() })
    if (mine !== requestNo) return
    surgeons.value = result
    if (!filters.q.trim() && !filters.minExperience && !filters.hospital) {
      hospitals.value = [...new Set(result.map((s) => s.hospitalName))].sort((a, b) => a.localeCompare(b))
    }
  } catch (err) {
    if (mine === requestNo) error.value = errorMessage(err)
  } finally {
    if (mine === requestNo) loading.value = false
  }
}

// Typing waits a moment before searching; the drop-downs search straight away.
let timer = null
watch(() => filters.q, () => {
  clearTimeout(timer)
  timer = setTimeout(load, 300)
})
watch(() => [filters.minExperience, filters.hospital], load)

const filtered = computed(() => !!(filters.q.trim() || filters.minExperience || filters.hospital))
function clear() {
  Object.assign(filters, { q: '', minExperience: '', hospital: '' })
}

// Doctors set up their own Breast Surgeon profile from here; visitors are invited to join as a doctor.
const user = computed(() => authState.user)
const showJoin = computed(() => !user.value || user.value.role === ROLES.DOCTOR)

onMounted(load)
</script>

<template>
  <div class="surgeon-listing">
    <PageHeader
      eyebrow="Breast Surgery consultations"
      title="Find a Breast Surgeon"
      subtitle="Book a consultation with a verified Breast Surgeon. Every surgeon listed here has had their registration and credentials checked by our team."
    >
      <div class="trust">
        <span><AppIcon name="shield-check" :size="16" /> Credentials verified</span>
        <span><AppIcon name="heart" :size="16" /> Breast Surgery specialists only</span>
        <span><AppIcon name="calendar" :size="16" /> Live availability</span>
      </div>
    </PageHeader>

    <section class="container page">
      <div v-if="showJoin" class="card join-banner">
        <span class="join-icon"><AppIcon name="user" :size="24" /></span>
        <div class="join-text">
          <strong>Are you a Breast Surgeon?</strong>
          <span class="muted">
            {{ user ? 'Set up your profile with your photo, fees, languages and weekly schedule. Patients can book you once an administrator verifies it.'
              : 'Register with a doctor account, then set up your profile so patients can book you.' }}
          </span>
        </div>
        <RouterLink v-if="user" :to="APP_ROUTES.surgeonProfile" class="btn btn-primary btn-sm">Set up my profile</RouterLink>
        <RouterLink v-else :to="{ path: APP_ROUTES.register, query: { as: 'doctor' } }" class="btn btn-outline btn-sm">Join as a doctor</RouterLink>
      </div>

      <form class="card filters" role="search" @submit.prevent="load">
        <label class="search">
          <AppIcon name="search" :size="18" />
          <input v-model="filters.q" type="search" placeholder="Search by surgeon’s name" aria-label="Search by Breast Surgeon’s name" />
        </label>
        <select v-model="filters.minExperience" aria-label="Filter by experience">
          <option v-for="e in EXPERIENCE" :key="e.label" :value="e.value">{{ e.label }}</option>
        </select>
        <select v-model="filters.hospital" aria-label="Filter by hospital">
          <option value="">All hospitals</option>
          <option v-for="h in hospitals" :key="h" :value="h">{{ h }}</option>
        </select>
        <button v-if="filtered" type="button" class="btn btn-outline btn-sm" @click="clear">Clear</button>
      </form>

      <p class="result-line muted" aria-live="polite">
        <template v-if="!loading && !error">
          {{ surgeons.length }} Breast Surgeon{{ surgeons.length === 1 ? '' : 's' }}{{ filtered ? ' match your search' : ' available' }}
        </template>
      </p>

      <div v-if="loading && !surgeons.length" class="cards">
        <div v-for="n in 3" :key="n" class="card skeleton" aria-hidden="true">
          <span class="sk-circle"></span><span class="sk-line w70"></span><span class="sk-line w50"></span><span class="sk-line"></span><span class="sk-line w80"></span>
        </div>
      </div>
      <div v-else-if="error" class="alert error" role="alert">
        {{ error }} <button type="button" class="btn btn-outline btn-sm" @click="load">Try again</button>
      </div>
      <div v-else-if="!surgeons.length" class="card empty">
        <span class="empty-icon"><AppIcon name="users" :size="30" /></span>
        <h2>{{ filtered ? 'No Breast Surgeons match your search' : 'No Breast Surgeons available yet' }}</h2>
        <p class="muted">
          {{ filtered ? 'Try a different name, or clear the filters.' : 'Verified Breast Surgeons will appear here as soon as their profiles are approved. Please check back soon.' }}
        </p>
        <button v-if="filtered" type="button" class="btn btn-primary btn-sm" @click="clear">Clear filters</button>
      </div>
      <div v-else class="cards" :class="{ dim: loading }">
        <SurgeonCard v-for="s in surgeons" :key="s.id" :surgeon="s" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.join-banner { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.2rem; border-left: 5px solid var(--pink-600); }
.join-icon { flex: none; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px; background: var(--pink-50); color: var(--pink-600); }
.join-text { flex: 1; display: grid; gap: 0.15rem; }
.join-text strong { color: var(--purple-900); }
.join-text .muted { font-size: 0.9rem; }
@media (max-width: 640px) { .join-banner { flex-wrap: wrap; } .join-banner .btn { width: 100%; justify-content: center; } }
.trust { display: flex; flex-wrap: wrap; justify-content: center; gap: 0.6rem; margin-top: 1.4rem; }
.trust span { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.35rem 0.85rem; border-radius: 999px; background: #fff; color: var(--purple-900); font-size: 0.84rem; font-weight: 500; box-shadow: var(--shadow); }
.trust :deep(svg) { color: var(--pink-600); }
.page { padding-top: 0; }
.filters { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) auto; gap: 0.7rem; align-items: center; margin-top: -1.6rem; padding: 0.9rem; border-radius: 20px; }
.search { display: flex; align-items: center; gap: 0.5rem; padding: 0 0.9rem; border: 1.5px solid var(--border); border-radius: 14px; color: var(--muted); }
.search:focus-within, .filters select:focus { border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.12); outline: none; }
.search input { flex: 1; min-width: 0; padding: 0.75rem 0; border: 0; outline: none; font: inherit; background: transparent; color: var(--text); }
.filters select { padding: 0.75rem 0.9rem; border: 1.5px solid var(--border); border-radius: 14px; background: #fff; font: inherit; color: var(--text); }
.result-line { min-height: 1.4rem; margin: 1.1rem 0 0.8rem; font-size: 0.9rem; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 1.1rem; transition: opacity 0.2s; }
.cards.dim { opacity: 0.55; }
.empty { display: grid; justify-items: center; gap: 0.4rem; padding: 3rem 1.5rem; text-align: center; }
.empty h2 { margin: 0.4rem 0 0; color: var(--purple-900); font-size: 1.25rem; }
.empty p { margin: 0 0 0.6rem; max-width: 460px; }
.empty-icon { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; background: var(--pink-50); color: var(--pink-600); }
.skeleton { display: grid; gap: 0.7rem; padding: 1.4rem; }
.sk-circle, .sk-line { display: block; border-radius: 999px; background: linear-gradient(90deg, #f6eef3 25%, #fdf6fa 50%, #f6eef3 75%); background-size: 200% 100%; animation: shimmer 1.3s infinite; }
.sk-circle { width: 72px; height: 72px; }
.sk-line { height: 12px; }
.w50 { width: 50%; } .w70 { width: 70%; } .w80 { width: 80%; }
@keyframes shimmer { to { background-position: -200% 0; } }
@media (max-width: 760px) {
  .filters { grid-template-columns: 1fr 1fr; }
  .search { grid-column: 1 / -1; }
}
</style>
