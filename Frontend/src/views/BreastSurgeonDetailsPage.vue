<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import SurgeonAvatar from '../components/appointments/SurgeonAvatar.vue'
import AvailabilityCalendar from '../components/appointments/AvailabilityCalendar.vue'
import { getSurgeon } from '../services/surgeonService'
import { errorMessage } from '../services/http'
import { authState } from '../auth/authStore'
import { ROLES } from '../auth/roles'
import { APP_ROUTES } from '../config/appRoutes'
import { DAYS, formatFee, formatTime } from '../appointments/format'

// An approved Breast Surgeon's full public profile, with their live calendar. Picking a slot here carries it
// over to the booking page.
const route = useRoute()
const router = useRouter()
const surgeon = ref(null)
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const choice = ref(null)

onMounted(async () => {
  try {
    surgeon.value = await getSurgeon(route.params.id)
  } catch (err) {
    notFound.value = err.response?.status === 404
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
})

const user = computed(() => authState.user)
const canBook = computed(() => !user.value || user.value.role === ROLES.PATIENT)

function book() {
  router.push({ path: `/breast-surgeons/${surgeon.value.id}/book`, query: choice.value ?? {} })
}
</script>

<template>
  <section class="container page details-page">
    <RouterLink :to="APP_ROUTES.surgeons" class="back"><AppIcon name="arrow-right" :size="16" class="flip" /> All Breast Surgeons</RouterLink>

    <p v-if="loading" class="muted loading"><span class="spinner dark" aria-hidden="true"></span> Loading profile…</p>
    <div v-else-if="error" class="card empty">
      <h1>{{ notFound ? 'Breast Surgeon not found' : 'Could not load this profile' }}</h1>
      <p class="muted">{{ notFound ? 'This profile is not available. It may not be approved yet, or it has been removed.' : error }}</p>
      <RouterLink :to="APP_ROUTES.surgeons" class="btn btn-primary btn-sm">Find a Breast Surgeon</RouterLink>
    </div>

    <template v-else>
      <div class="card hero">
        <SurgeonAvatar :photo-url="surgeon.photoUrl" :name="surgeon.fullName" :size="128" />
        <div class="who">
          <span class="verified"><AppIcon name="shield-check" :size="14" /> Verified Breast Surgeon</span>
          <h1>{{ surgeon.fullName }}</h1>
          <p class="designation">{{ surgeon.designation }}</p>
          <p class="qual">{{ surgeon.qualifications }}</p>
        </div>
        <div class="book-box">
          <p class="fee-label">Consultation fee</p>
          <p class="fee">{{ formatFee(surgeon.consultationFee) }}</p>
          <button v-if="canBook && surgeon.acceptingAppointments" type="button" class="btn btn-primary book-btn" @click="book">
            <AppIcon name="calendar" :size="18" /> Book Appointment
          </button>
          <p v-else-if="!surgeon.acceptingAppointments" class="paused">Not taking new appointments right now.</p>
          <p v-else class="paused">Only patient accounts can book appointments.</p>
        </div>
      </div>

      <div class="layout">
        <div class="main-col">
          <article class="card section">
            <h2>About</h2>
            <p class="bio">{{ surgeon.biography }}</p>
          </article>

          <article class="card section">
            <h2>Available appointments</h2>
            <p class="muted small">Choose a date and time, then press Book Appointment.</p>
            <AvailabilityCalendar v-model="choice" :surgeon-id="surgeon.id" />
            <div v-if="choice && canBook && surgeon.acceptingAppointments" class="chosen">
              <span><AppIcon name="check-circle" :size="16" /> {{ new Date(`${choice.date}T00:00`).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }) }} at {{ formatTime(choice.time) }}</span>
              <button type="button" class="btn btn-primary btn-sm" @click="book">Book this slot</button>
            </div>
          </article>
        </div>

        <aside class="side-col">
          <div class="card section facts">
            <h2>At a glance</h2>
            <ul>
              <li><AppIcon name="heart" :size="18" /><div><span>Specialty</span><strong>{{ surgeon.specialty }}</strong></div></li>
              <li><AppIcon name="award" :size="18" /><div><span>Breast surgery experience</span><strong>{{ surgeon.experienceYears }} years</strong></div></li>
              <li><AppIcon name="file-text" :size="18" /><div><span>Registration no.</span><strong>{{ surgeon.registrationNumber }}</strong></div></li>
              <li v-if="surgeon.languages?.length"><AppIcon name="languages" :size="18" /><div><span>Speaks</span><strong>{{ surgeon.languages.join(', ') }}</strong></div></li>
            </ul>
          </div>
          <div class="card section facts">
            <h2>Hospital / chamber</h2>
            <ul>
              <li><AppIcon name="building" :size="18" /><div><span>Name</span><strong>{{ surgeon.hospitalName }}</strong></div></li>
              <li><AppIcon name="map-pin" :size="18" /><div><span>Address</span><strong>{{ surgeon.hospitalAddress }}</strong></div></li>
            </ul>
          </div>
          <div class="card section">
            <h2>Weekly schedule</h2>
            <div class="week">
              <span v-for="d in DAYS" :key="d.id" :class="{ on: surgeon.availableDays.includes(d.id) }">{{ d.short }}</span>
            </div>
            <div class="times">
              <span v-for="t in surgeon.timeSlots" :key="t">{{ formatTime(t) }}</span>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.back { display: inline-flex; align-items: center; gap: 0.4rem; margin-bottom: 1rem; color: var(--pink-600); font-weight: 600; font-size: 0.92rem; }
.flip { transform: rotate(180deg); }
.loading { display: flex; align-items: center; gap: 0.5rem; }
.spinner.dark { border-color: var(--pink-100); border-top-color: var(--pink-600); }
.empty { text-align: center; padding: 3rem 1.5rem; }
.hero {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 1.6rem; padding: 1.8rem; border-radius: 26px;
  background: radial-gradient(circle at 100% 0%, rgba(247, 168, 200, 0.35), transparent 45%), linear-gradient(120deg, #fff 0%, #fff6fa 100%);
}
.verified { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.2rem 0.7rem; border-radius: 999px; background: #e8f5e9; color: #1b5e20; font-size: 0.78rem; font-weight: 600; }
.who h1 { margin: 0.5rem 0 0.1rem; font-size: clamp(1.5rem, 3vw, 2rem); }
.designation { margin: 0; color: var(--pink-600); font-weight: 600; }
.qual { margin: 0.4rem 0 0; color: var(--text); }
.book-box { display: grid; justify-items: center; gap: 0.2rem; min-width: 210px; padding: 1.1rem; border-radius: 20px; background: #fff; border: 1px solid var(--border); text-align: center; }
.fee-label { margin: 0; font-size: 0.78rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.08em; font-weight: 600; }
.fee { margin: 0 0 0.5rem; font-size: 1.7rem; font-weight: 700; color: var(--purple-900); }
.book-btn { width: 100%; justify-content: center; }
.paused { margin: 0; padding: 0.5rem 0.7rem; border-radius: 10px; background: var(--warn-bg); color: var(--warn-text); font-size: 0.84rem; }
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 1.2rem; margin-top: 1.2rem; align-items: start; }
.main-col, .side-col { display: grid; gap: 1.2rem; }
.section { padding: 1.4rem; border-radius: 22px; }
.section h2 { margin: 0 0 0.6rem; color: var(--purple-900); font-size: 1.12rem; }
.small { margin: -0.3rem 0 1rem; font-size: 0.88rem; }
.bio { margin: 0; line-height: 1.75; white-space: pre-line; }
.chosen { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.6rem; margin-top: 1.1rem; padding: 0.8rem 1rem; border-radius: 14px; background: #e8f5e9; color: #1b5e20; font-weight: 500; }
.chosen span { display: inline-flex; align-items: center; gap: 0.4rem; }
.facts ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.8rem; }
.facts li { display: flex; gap: 0.7rem; align-items: flex-start; }
.facts li > :deep(svg) { flex: none; margin-top: 2px; color: var(--pink-600); }
.facts span { display: block; font-size: 0.76rem; color: var(--muted); }
.facts strong { color: var(--purple-900); font-weight: 600; overflow-wrap: anywhere; }
.week { display: grid; grid-template-columns: repeat(7, 1fr); gap: 0.3rem; }
.week span { padding: 0.35rem 0; border-radius: 8px; text-align: center; font-size: 0.74rem; font-weight: 600; background: #f6f3f8; color: #b3abc4; }
.week span.on { background: var(--pink-600); color: #fff; }
.times { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-top: 0.8rem; }
.times span { padding: 0.2rem 0.6rem; border-radius: 999px; background: #f6f0ff; color: #4a2a8a; font-size: 0.78rem; font-weight: 600; }
@media (max-width: 900px) {
  .hero { grid-template-columns: auto minmax(0, 1fr); }
  .book-box { grid-column: 1 / -1; }
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 520px) {
  .hero { grid-template-columns: 1fr; justify-items: center; text-align: center; padding: 1.4rem; }
}
</style>
