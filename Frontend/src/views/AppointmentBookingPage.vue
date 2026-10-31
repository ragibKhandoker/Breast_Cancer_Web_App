<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import SurgeonAvatar from '../components/appointments/SurgeonAvatar.vue'
import AvailabilityCalendar from '../components/appointments/AvailabilityCalendar.vue'
import { getSurgeon } from '../services/surgeonService'
import { bookAppointment } from '../services/appointmentService'
import { errorMessage, fieldErrors } from '../services/http'
import { authState } from '../auth/authStore'
import { booking as bookingRule } from '../appointments/rules'
import { formatDate, formatTime, formatFee } from '../appointments/format'
import { APP_ROUTES } from '../config/appRoutes'

// Booking in two steps: choose a slot, then review and send the request. The server makes the final check
// (approved surgeon, offered slot, still free) and answers with the new Appointment ID.
const route = useRoute()
const router = useRouter()
const surgeon = ref(null)
const loadError = ref('')
const loading = ref(true)
const step = ref(1)
const choice = ref(route.query.date && route.query.time ? { date: String(route.query.date), time: String(route.query.time) } : null)
const reason = ref('')
const submitting = ref(false)
const submitError = ref('')
const calendar = ref(null)

onMounted(async () => {
  try {
    surgeon.value = await getSurgeon(route.params.id)
    if (!surgeon.value.acceptingAppointments) loadError.value = 'This Breast Surgeon is not taking new appointments right now.'
  } catch (err) {
    loadError.value = err.response?.status === 404 ? 'This Breast Surgeon is not available for booking.' : errorMessage(err)
  } finally {
    loading.value = false
  }
})

// A slot carried over from the profile page is kept only if the calendar still shows it free.
function onCalendarLoaded(days) {
  if (!choice.value) return
  const day = days.find((d) => d.date === choice.value.date)
  if (!day?.slots.some((s) => s.time === choice.value.time && s.available)) choice.value = null
}

const patient = computed(() => authState.user)

async function submit() {
  submitting.value = true
  submitError.value = ''
  try {
    const appointment = await bookAppointment({ surgeonId: surgeon.value.id, ...choice.value, reason: reason.value.trim() })
    router.replace(`/appointments/${appointment.id}/confirmation`)
  } catch (err) {
    const fields = fieldErrors(err)
    submitError.value = fields.date || fields.time || fields.reason || errorMessage(err)
    if (err.response?.status === 409 || fields.date || fields.time) {
      // The slot is gone (or no longer offered): back to the calendar as it is now.
      choice.value = null
      step.value = 1
      calendar.value?.reload()
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="container page booking-page">
    <RouterLink :to="surgeon ? `/breast-surgeons/${surgeon.id}` : APP_ROUTES.surgeons" class="back">
      <AppIcon name="arrow-right" :size="16" class="flip" /> Back to profile
    </RouterLink>
    <h1>Book an appointment</h1>

    <p v-if="loading" class="muted">Loading…</p>
    <div v-else-if="loadError" class="card empty">
      <p class="alert error" role="alert">{{ loadError }}</p>
      <RouterLink :to="APP_ROUTES.surgeons" class="btn btn-primary btn-sm">Find another Breast Surgeon</RouterLink>
    </div>

    <template v-else>
      <ol class="stepper" aria-label="Booking steps">
        <li :class="{ on: step === 1, done: step > 1 }"><span>1</span> Date &amp; time</li>
        <li :class="{ on: step === 2 }"><span>2</span> Review &amp; confirm</li>
      </ol>

      <div class="layout">
        <div class="main">
          <p v-if="submitError" class="alert error" role="alert">{{ submitError }}</p>

          <div v-show="step === 1" class="card panel">
            <h2>Choose a date and time</h2>
            <AvailabilityCalendar ref="calendar" v-model="choice" :surgeon-id="surgeon.id" @loaded="onCalendarLoaded" />
            <div class="form-field reason">
              <label for="booking-reason">Reason for visit <span class="muted">(optional)</span></label>
              <textarea id="booking-reason" v-model="reason" rows="3" maxlength="500"
                placeholder="For example: a lump I noticed last month, follow-up after a mammogram…"></textarea>
              <p class="hint">{{ reason.length }}/500 · Only {{ surgeon.fullName }} sees this.</p>
            </div>
            <div class="nav">
              <button type="button" class="btn btn-primary" :disabled="!choice" @click="step = 2; submitError = ''">
                Continue <AppIcon name="arrow-right" :size="16" />
              </button>
            </div>
          </div>

          <div v-if="step === 2 && choice" class="card panel">
            <h2>Review your booking</h2>
            <dl class="summary">
              <div><dt>Breast Surgeon</dt><dd>{{ surgeon.fullName }}</dd></div>
              <div><dt>Specialty</dt><dd>{{ surgeon.specialty }}</dd></div>
              <div><dt>Date</dt><dd>{{ formatDate(choice.date) }}</dd></div>
              <div><dt>Time</dt><dd>{{ formatTime(choice.time) }}</dd></div>
              <div><dt>Hospital / chamber</dt><dd>{{ surgeon.hospitalName }}<br /><span class="muted">{{ surgeon.hospitalAddress }}</span></dd></div>
              <div><dt>Consultation fee</dt><dd>{{ formatFee(surgeon.consultationFee) }} <span class="muted">(paid at the chamber)</span></dd></div>
              <div><dt>Patient</dt><dd>{{ patient?.fullName }}<br /><span class="muted">{{ patient?.email }}</span></dd></div>
              <div v-if="reason.trim()" class="wide"><dt>Reason for visit</dt><dd>{{ reason.trim() }}</dd></div>
            </dl>
            <p class="rule"><AppIcon name="alert-circle" :size="16" /> {{ bookingRule }}</p>
            <div class="nav">
              <button type="button" class="btn btn-outline" :disabled="submitting" @click="step = 1">Change</button>
              <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
                <span v-if="submitting" class="spinner" aria-hidden="true"></span>
                {{ submitting ? 'Sending request…' : 'Confirm booking' }}
              </button>
            </div>
          </div>
        </div>

        <aside class="card side">
          <SurgeonAvatar :photo-url="surgeon.photoUrl" :name="surgeon.fullName" :size="84" />
          <h3>{{ surgeon.fullName }}</h3>
          <p class="muted">{{ surgeon.designation }}</p>
          <span class="specialty"><AppIcon name="heart" :size="12" /> {{ surgeon.specialty }}</span>
          <ul>
            <li><AppIcon name="building" :size="16" /> {{ surgeon.hospitalName }}</li>
            <li><AppIcon name="wallet" :size="16" /> {{ formatFee(surgeon.consultationFee) }}</li>
            <li v-if="choice"><AppIcon name="calendar" :size="16" /> {{ formatDate(choice.date) }}, {{ formatTime(choice.time) }}</li>
          </ul>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.back { display: inline-flex; align-items: center; gap: 0.4rem; margin-bottom: 0.8rem; color: var(--pink-600); font-weight: 600; font-size: 0.92rem; }
.flip { transform: rotate(180deg); }
.empty { display: grid; justify-items: start; gap: 0.6rem; margin-top: 1rem; }
.stepper { display: flex; gap: 0.6rem; list-style: none; margin: 1rem 0; padding: 0; }
.stepper li { display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.4rem 1rem 0.4rem 0.4rem; border-radius: 999px; background: #fff; border: 1.5px solid var(--border); color: var(--muted); font-weight: 500; font-size: 0.9rem; }
.stepper span { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--pink-50); color: var(--pink-600); font-weight: 700; font-size: 0.82rem; }
.stepper li.on { border-color: var(--pink-600); color: var(--purple-900); }
.stepper li.on span { background: var(--pink-600); color: #fff; }
.stepper li.done span { background: #2e9e5b; color: #fff; }
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 1.2rem; align-items: start; }
.panel { padding: 1.5rem; border-radius: 22px; }
.panel h2 { margin: 0 0 1rem; color: var(--purple-900); font-size: 1.15rem; }
.reason { margin-top: 1.3rem; }
.reason textarea { width: 100%; padding: 0.8rem 1rem; border: 1.5px solid var(--border); border-radius: 12px; font: 400 0.95rem 'Poppins', sans-serif; color: var(--text); resize: vertical; }
.reason textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.hint { margin: 0; font-size: 0.78rem; color: var(--muted); }
.nav { display: flex; justify-content: flex-end; gap: 0.6rem; margin-top: 1rem; }
.summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.9rem 1.2rem; margin: 0; padding: 1.1rem; border-radius: 16px; background: var(--pink-50); }
.summary .wide { grid-column: 1 / -1; }
.summary dt { font-size: 0.74rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.summary dd { margin: 0.15rem 0 0; color: var(--purple-900); font-weight: 500; overflow-wrap: anywhere; }
.rule { display: flex; gap: 0.5rem; align-items: flex-start; margin: 1rem 0 0; font-size: 0.88rem; color: var(--muted); }
.rule :deep(svg) { flex: none; color: var(--pink-600); margin-top: 2px; }
.side { position: sticky; top: 92px; display: grid; justify-items: center; gap: 0.3rem; padding: 1.5rem; border-radius: 22px; text-align: center; }
.side h3 { margin: 0.6rem 0 0; color: var(--purple-900); }
.side p { margin: 0; font-size: 0.88rem; }
.specialty { display: inline-flex; align-items: center; gap: 0.3rem; margin-top: 0.3rem; padding: 0.15rem 0.6rem; border-radius: 999px; background: var(--pink-50); color: var(--pink-600); font-size: 0.75rem; font-weight: 600; }
.side ul { list-style: none; margin: 0.9rem 0 0; padding: 0.9rem 0 0; display: grid; gap: 0.5rem; width: 100%; border-top: 1px dashed var(--border); text-align: left; }
.side li { display: flex; gap: 0.5rem; align-items: center; font-size: 0.88rem; }
.side li :deep(svg) { flex: none; color: var(--pink-600); }
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; order: -1; }
}
@media (max-width: 560px) {
  .summary { grid-template-columns: 1fr; }
  .nav .btn { flex: 1; justify-content: center; }
}
</style>
