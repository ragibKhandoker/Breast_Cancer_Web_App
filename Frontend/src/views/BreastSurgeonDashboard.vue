<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import SurgeonAvatar from '../components/appointments/SurgeonAvatar.vue'
import AppointmentStatusBadge from '../components/appointments/AppointmentStatusBadge.vue'
import SurgeonAppointmentManagement from '../components/appointments/SurgeonAppointmentManagement.vue'
import AvailabilityEditor from '../components/appointments/AvailabilityEditor.vue'
import AvailabilityCalendar from '../components/appointments/AvailabilityCalendar.vue'
import { getMyProfile, updateAvailability } from '../services/surgeonService'
import { listSurgeonAppointments } from '../services/appointmentService'
import { errorMessage, fieldErrors } from '../services/http'
import { onSessionEnd } from '../auth/authStore'
import { APP_ROUTES } from '../config/appRoutes'
import { formatDate, formatTime, todayIso } from '../appointments/format'

// The approved Breast Surgeon's workspace: their own appointments and availability. The server only ever
// returns this surgeon's appointments, and refuses actions on anyone else's.
const route = useRoute()
const router = useRouter()
const profile = ref(null)
const noProfile = ref(false)
const appointments = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    profile.value = await getMyProfile()
    appointments.value = await listSurgeonAppointments()
    resetAvailability()
  } catch (err) {
    if (err.response?.status === 404) noProfile.value = true
    else error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const tab = computed(() => (route.query.tab === 'availability' ? 'availability' : 'appointments'))
function openTab(id) {
  router.replace({ query: id === 'appointments' ? {} : { tab: id } })
}

const stats = computed(() => {
  const s = { total: appointments.value.length, PENDING: 0, CONFIRMED: 0, COMPLETED: 0, CANCELLED: 0 }
  appointments.value.forEach((a) => s[a.status]++)
  return s
})
const STAT_CARDS = [
  { key: 'total', label: 'Total appointments', icon: 'calendar' },
  { key: 'PENDING', label: 'Pending requests', icon: 'refresh' },
  { key: 'CONFIRMED', label: 'Confirmed', icon: 'check-circle' },
  { key: 'COMPLETED', label: 'Completed', icon: 'heart' },
  { key: 'CANCELLED', label: 'Cancelled', icon: 'close' },
]

const upcoming = computed(() => {
  const today = todayIso()
  return appointments.value.filter((a) => a.status === 'CONFIRMED' && a.date >= today).slice(0, 4)
})

function onChanged(updated) {
  appointments.value = appointments.value.map((a) => (a.id === updated.id ? updated : a))
}

// ---- Availability ----
const availability = ref({ availableDays: [], timeSlots: [] })
const accepting = ref(true)
const savingAvailability = ref(false)
const availabilityErrors = ref({})
const availabilityMessage = ref('')
const availabilityError = ref('')
const calendar = ref(null)
function resetAvailability() {
  availability.value = { availableDays: [...profile.value.availableDays], timeSlots: [...profile.value.timeSlots] }
  accepting.value = profile.value.acceptingAppointments
}
async function saveAvailability() {
  savingAvailability.value = true
  availabilityErrors.value = {}
  availabilityMessage.value = ''
  availabilityError.value = ''
  try {
    profile.value = await updateAvailability({ ...availability.value, acceptingAppointments: accepting.value })
    resetAvailability()
    availabilityMessage.value = 'Availability saved. Patients see the new slots straight away; booked appointments are unchanged.'
    calendar.value?.reload()
  } catch (err) {
    availabilityErrors.value = fieldErrors(err)
    availabilityError.value = errorMessage(err)
  } finally {
    savingAvailability.value = false
  }
}

const STATUS_PILL = { PENDING: 'Awaiting verification', APPROVED: 'Verified & listed', REJECTED: 'Changes needed' }

const stop = onSessionEnd(() => (appointments.value = []))
onBeforeUnmount(stop)
onMounted(load)
</script>

<template>
  <section class="container page surgeon-dashboard">
    <p v-if="loading" class="muted">Loading your dashboard…</p>
    <p v-else-if="error" class="alert error" role="alert">{{ error }}</p>

    <div v-else-if="noProfile" class="card start">
      <span class="start-icon"><AppIcon name="heart" :size="32" /></span>
      <h1>Become a listed Breast Surgeon</h1>
      <p class="muted">Create your Breast Surgeon profile to receive appointment requests from patients. An administrator verifies your registration before you appear in Find a Breast Surgeon.</p>
      <RouterLink :to="APP_ROUTES.surgeonProfile" class="btn btn-primary">Create my profile</RouterLink>
    </div>

    <template v-else>
      <div class="card hero">
        <SurgeonAvatar :photo-url="profile.photoUrl" :name="profile.fullName" :size="80" />
        <div class="who">
          <p class="eyebrow">Breast Surgeon dashboard</p>
          <h1>{{ profile.fullName }}</h1>
          <p class="sub">
            <span class="pill" :class="profile.status.toLowerCase()">{{ STATUS_PILL[profile.status] }}</span>
            <span class="pill" :class="profile.acceptingAppointments ? 'open' : 'closed'">{{ profile.acceptingAppointments ? 'Taking bookings' : 'Bookings paused' }}</span>
            {{ profile.hospitalName }}
          </p>
        </div>
        <RouterLink :to="APP_ROUTES.surgeonProfile" class="btn btn-outline btn-sm">Manage profile</RouterLink>
      </div>

      <p v-if="profile.status !== 'APPROVED'" class="alert info">
        Patients cannot book you until an administrator approves your profile.
        <RouterLink :to="APP_ROUTES.surgeonProfile"><strong>See its status</strong></RouterLink>
      </p>

      <div class="stats">
        <div v-for="s in STAT_CARDS" :key="s.key" class="card stat" :class="s.key.toLowerCase()" :data-stat="s.key">
          <span class="stat-icon"><AppIcon :name="s.icon" :size="20" /></span>
          <strong>{{ stats[s.key] }}</strong>
          <span>{{ s.label }}</span>
        </div>
      </div>

      <div class="card upcoming">
        <h2>Upcoming consultations</h2>
        <p v-if="!upcoming.length" class="muted">No confirmed consultations coming up.</p>
        <ul v-else>
          <li v-for="a in upcoming" :key="a.id">
            <span class="date">{{ formatDate(a.date) }}</span>
            <span class="time">{{ formatTime(a.time) }}</span>
            <span class="name">{{ a.patient.fullName }}</span>
            <AppointmentStatusBadge :status="a.status" />
          </li>
        </ul>
      </div>

      <div class="tabs" role="tablist" aria-label="Dashboard sections">
        <button type="button" role="tab" :aria-selected="tab === 'appointments'" :class="{ on: tab === 'appointments' }" @click="openTab('appointments')">
          <AppIcon name="users" :size="16" /> Appointments
          <span v-if="stats.PENDING" class="badge-count">{{ stats.PENDING }}</span>
        </button>
        <button type="button" role="tab" :aria-selected="tab === 'availability'" :class="{ on: tab === 'availability' }" @click="openTab('availability')">
          <AppIcon name="calendar" :size="16" /> Availability
        </button>
      </div>

      <div v-if="tab === 'appointments'" class="card panel" role="tabpanel">
        <SurgeonAppointmentManagement :appointments="appointments" :initial-view="stats.PENDING ? 'REQUESTS' : 'UPCOMING'" @changed="onChanged" />
      </div>

      <div v-else class="card panel" role="tabpanel">
        <div class="availability-grid">
          <div>
            <h2>Weekly availability</h2>
            <p class="muted small">Days and times you see patients. Changes do not move appointments already booked.</p>
            <AvailabilityEditor v-model="availability" :errors="availabilityErrors" />
            <label class="accept-toggle">
              <input v-model="accepting" type="checkbox" />
              <span class="track" aria-hidden="true"><span class="knob"></span></span>
              <span><strong>{{ accepting ? 'Taking new appointments' : 'New bookings paused' }}</strong><br /><span class="muted">Pause when you are away; you stay listed.</span></span>
            </label>
            <p v-if="availabilityMessage" class="alert success" role="status">{{ availabilityMessage }}</p>
            <p v-if="availabilityError" class="alert error" role="alert">{{ availabilityError }}</p>
            <div class="save-row">
              <button type="button" class="btn btn-outline btn-sm" :disabled="savingAvailability" @click="resetAvailability">Undo changes</button>
              <button type="button" class="btn btn-primary btn-sm" :disabled="savingAvailability" @click="saveAvailability">
                <span v-if="savingAvailability" class="spinner" aria-hidden="true"></span>
                {{ savingAvailability ? 'Saving…' : 'Save availability' }}
              </button>
            </div>
          </div>
          <div>
            <h2>What patients see</h2>
            <p class="muted small">Your next four weeks, with booked slots crossed out.</p>
            <AvailabilityCalendar v-if="profile.status === 'APPROVED'" ref="calendar" :model-value="null" :surgeon-id="profile.id" />
            <p v-else class="alert info">The calendar appears here once your profile is approved.</p>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.start { max-width: 640px; margin: 1rem auto; padding: 2.5rem; text-align: center; display: grid; justify-items: center; gap: 0.6rem; border-radius: 26px; }
.start-icon { display: grid; place-items: center; width: 70px; height: 70px; border-radius: 22px; background: var(--pink-50); color: var(--pink-600); }
.start p { margin: 0 0 0.6rem; line-height: 1.6; }
.hero {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 1.2rem; padding: 1.5rem 1.7rem; border: 0; color: #fff;
  background: radial-gradient(circle at 92% 0%, rgba(255, 255, 255, 0.2), transparent 40%), linear-gradient(120deg, var(--purple-950) 0%, #4a2a8a 55%, #b5179e 100%);
  box-shadow: 0 18px 40px rgba(42, 22, 80, 0.25);
}
.eyebrow { margin: 0; font-size: 0.76rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.1em; color: rgba(255, 255, 255, 0.75); }
.hero h1 { margin: 0.15rem 0 0.35rem; color: #fff; font-size: 1.6rem; }
.sub { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; color: rgba(255, 255, 255, 0.88); font-size: 0.9rem; }
.pill { padding: 0.15rem 0.65rem; border-radius: 999px; font-size: 0.75rem; font-weight: 600; background: #fff; }
.pill.approved, .pill.open { color: #1b5e20; }
.pill.pending { color: #8a5300; }
.pill.rejected, .pill.closed { color: var(--high); }
.hero .btn-outline { background: transparent; border-color: rgba(255, 255, 255, 0.7); color: #fff; }
.hero .btn-outline:hover { background: #fff; color: var(--purple-900); }
.alert.info { margin-top: 1rem; }
.stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0.8rem; margin-top: 1.2rem; }
.stat { display: grid; gap: 0.15rem; padding: 1rem 1.1rem; border-radius: 18px; }
.stat strong { font-size: 1.8rem; color: var(--purple-900); line-height: 1.1; }
.stat > span:last-child { font-size: 0.82rem; color: var(--muted); }
.stat-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: var(--pink-50); color: var(--pink-600); margin-bottom: 0.4rem; }
.stat.pending .stat-icon { background: #fff7e6; color: #8a5300; }
.stat.confirmed .stat-icon { background: #e8f5e9; color: #2e7d32; }
.stat.completed .stat-icon { background: #f1ebff; color: #4a2a8a; }
.stat.cancelled .stat-icon { background: #f3f1f6; color: #5f5873; }
.upcoming { margin-top: 1.2rem; border-radius: 20px; }
.upcoming h2, .panel h2 { margin: 0 0 0.6rem; font-size: 1.1rem; color: var(--purple-900); }
.upcoming ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.4rem; }
.upcoming li { display: grid; grid-template-columns: 150px 90px minmax(0, 1fr) auto; align-items: center; gap: 0.8rem; padding: 0.55rem 0.8rem; border-radius: 12px; background: #f8f6fa; font-size: 0.9rem; }
.upcoming .date { font-weight: 600; color: var(--purple-900); }
.upcoming .time { color: var(--pink-600); font-weight: 600; }
.upcoming .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tabs { display: flex; gap: 0.5rem; margin: 1.5rem 0 0.8rem; }
.tabs button {
  display: inline-flex; align-items: center; gap: 0.45rem; padding: 0.6rem 1.1rem; border-radius: 14px; border: 1.5px solid var(--border);
  background: #fff; color: var(--purple-900); font: 600 0.92rem 'Poppins', sans-serif; cursor: pointer;
}
.tabs button.on { background: linear-gradient(120deg, var(--pink-600), #b5179e); border-color: transparent; color: #fff; box-shadow: 0 8px 18px rgba(216, 27, 106, 0.25); }
.badge-count { min-width: 22px; padding: 0 0.4rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-size: 0.72rem; line-height: 22px; text-align: center; }
.tabs button.on .badge-count { background: #fff; color: var(--pink-600); }
.panel { border-radius: 22px; padding: 1.4rem; }
.availability-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr); gap: 2rem; }
.small { margin: -0.3rem 0 1rem; font-size: 0.86rem; }
.accept-toggle { display: flex; align-items: center; gap: 0.8rem; margin: 0.6rem 0 0.8rem; padding: 0.8rem 1rem; border-radius: 14px; border: 1.5px solid var(--border); cursor: pointer; }
.accept-toggle input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.track { flex: none; position: relative; width: 44px; height: 24px; border-radius: 999px; background: #d9d2e3; transition: background-color 0.2s; }
.knob { position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2); transition: transform 0.2s; }
.accept-toggle input:checked + .track { background: #2e9e5b; }
.accept-toggle input:checked + .track .knob { transform: translateX(20px); }
.accept-toggle:has(input:focus-visible) { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.accept-toggle .muted { font-size: 0.82rem; }
.save-row { display: flex; justify-content: flex-end; gap: 0.5rem; }
@media (max-width: 1000px) {
  .stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .availability-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .hero { grid-template-columns: auto minmax(0, 1fr); }
  .hero .btn { grid-column: 1 / -1; justify-content: center; }
  .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .upcoming li { grid-template-columns: 1fr auto; }
}
</style>
