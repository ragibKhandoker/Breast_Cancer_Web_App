<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import AppIcon from '../components/common/AppIcon.vue'
import AppointmentHistory from '../components/appointments/AppointmentHistory.vue'
import CancelAppointmentDialog from '../components/appointments/CancelAppointmentDialog.vue'
import RescheduleAppointmentModal from '../components/appointments/RescheduleAppointmentModal.vue'
import { listMyAppointments, cancelAppointment } from '../services/appointmentService'
import { errorMessage } from '../services/http'
import { onSessionEnd } from '../auth/authStore'
import { APP_ROUTES } from '../config/appRoutes'
import { formatDate, formatTime, todayIso } from '../appointments/format'

// My Breast Surgeon Appointments: the signed-in patient's own appointments only (the server returns no others).
const appointments = ref([])
const loading = ref(true)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    appointments.value = await listMyAppointments()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const next = computed(() => {
  const today = todayIso()
  return [...appointments.value]
    .filter((a) => a.status === 'CONFIRMED' && a.date >= today)
    .sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))[0]
})
const pendingCount = computed(() => appointments.value.filter((a) => a.status === 'PENDING').length)

// ---- Cancel ----
const cancelling = ref(null)
const cancelBusy = ref(false)
const cancelError = ref('')
async function doCancel(reason) {
  cancelBusy.value = true
  cancelError.value = ''
  try {
    await cancelAppointment(cancelling.value.id, reason)
    notice.value = `Appointment ${cancelling.value.appointmentCode} has been cancelled.`
    cancelling.value = null
    await load()
  } catch (err) {
    cancelError.value = errorMessage(err)
  } finally {
    cancelBusy.value = false
  }
}

// ---- Reschedule ----
const rescheduling = ref(null)
async function rescheduled(updated) {
  rescheduling.value = null
  notice.value = `Requested ${formatDate(updated.date)} at ${formatTime(updated.time)} for ${updated.appointmentCode}. It is Pending until the surgeon confirms.`
  await load()
}

const stop = onSessionEnd(() => (appointments.value = []))
onBeforeUnmount(stop)
onMounted(load)
</script>

<template>
  <section class="container page my-appointments">
    <div class="head">
      <div>
        <h1>My Breast Surgeon Appointments</h1>
        <p class="muted">Your consultations with verified Breast Surgeons. Only you can see this list.</p>
      </div>
      <RouterLink :to="APP_ROUTES.surgeons" class="btn btn-primary"><AppIcon name="search" :size="17" /> Book a new appointment</RouterLink>
    </div>

    <div v-if="next || pendingCount" class="highlights">
      <div v-if="next" class="card highlight next">
        <span class="hl-icon"><AppIcon name="calendar" :size="22" /></span>
        <div>
          <p class="hl-label">Next confirmed appointment</p>
          <p class="hl-main">{{ formatDate(next.date) }} at {{ formatTime(next.time) }}</p>
          <p class="muted">{{ next.surgeon.fullName }} · {{ next.surgeon.hospitalName }}</p>
        </div>
      </div>
      <div v-if="pendingCount" class="card highlight waiting">
        <span class="hl-icon"><AppIcon name="refresh" :size="22" /></span>
        <div>
          <p class="hl-label">Waiting for the surgeon</p>
          <p class="hl-main">{{ pendingCount }} pending request{{ pendingCount === 1 ? '' : 's' }}</p>
        </div>
      </div>
    </div>

    <p v-if="notice" class="alert success" role="status">{{ notice }}</p>

    <p v-if="loading && !appointments.length" class="muted"><span class="spinner dark" aria-hidden="true"></span> Loading your appointments…</p>
    <div v-else-if="error" class="alert error" role="alert">{{ error }} <button type="button" class="btn btn-outline btn-sm" @click="load">Try again</button></div>
    <div v-else-if="!appointments.length" class="card empty">
      <span class="empty-icon"><AppIcon name="calendar" :size="30" /></span>
      <h2>No appointments yet</h2>
      <p class="muted">Find a verified Breast Surgeon and book a consultation in a few clicks.</p>
      <RouterLink :to="APP_ROUTES.surgeons" class="btn btn-primary btn-sm">Find a Breast Surgeon</RouterLink>
    </div>
    <AppointmentHistory v-else :appointments="appointments" @cancel="(a) => { cancelling = a; cancelError = ''; notice = '' }" @reschedule="(a) => { rescheduling = a; notice = '' }" />

    <CancelAppointmentDialog
      v-if="cancelling"
      :appointment="cancelling"
      :busy="cancelBusy"
      :error="cancelError"
      @confirm="doCancel"
      @close="cancelling = null"
    />
    <RescheduleAppointmentModal v-if="rescheduling" :appointment="rescheduling" @done="rescheduled" @close="rescheduling = null" />
  </section>
</template>

<style scoped>
.head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 1rem; margin-bottom: 1.2rem; }
.head p { margin: 0.2rem 0 0; }
.highlights { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 1rem; margin-bottom: 1.2rem; }
.highlight { display: flex; gap: 0.9rem; align-items: center; border-radius: 20px; }
.highlight p { margin: 0; }
.hl-icon { flex: none; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px; }
.next .hl-icon { background: #e8f5e9; color: #2e7d32; }
.waiting .hl-icon { background: #fff7e6; color: #8a5300; }
.hl-label { font-size: 0.76rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.07em; color: var(--muted); }
.hl-main { font-size: 1.1rem; font-weight: 700; color: var(--purple-900); }
.spinner.dark { display: inline-block; vertical-align: middle; border-color: var(--pink-100); border-top-color: var(--pink-600); }
.empty { display: grid; justify-items: center; gap: 0.4rem; padding: 3rem 1.5rem; text-align: center; }
.empty h2 { margin: 0.4rem 0 0; color: var(--purple-900); font-size: 1.25rem; }
.empty p { margin: 0 0 0.6rem; }
.empty-icon { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 20px; background: var(--pink-50); color: var(--pink-600); }
</style>
