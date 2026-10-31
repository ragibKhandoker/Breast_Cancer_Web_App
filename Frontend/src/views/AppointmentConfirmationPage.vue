<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import SurgeonAvatar from '../components/appointments/SurgeonAvatar.vue'
import AppointmentStatusBadge from '../components/appointments/AppointmentStatusBadge.vue'
import { getAppointment } from '../services/appointmentService'
import { errorMessage } from '../services/http'
import { formatDate, formatTime, formatFee, formatDateTime } from '../appointments/format'
import { APP_ROUTES } from '../config/appRoutes'

// Shown right after booking: the Appointment ID and what happens next. Read from the server, so reloading
// the page (or opening it later) shows the appointment as it is now.
const route = useRoute()
const appointment = ref(null)
const error = ref('')
const copied = ref(false)

onMounted(async () => {
  try {
    appointment.value = await getAppointment(route.params.id)
  } catch (err) {
    error.value = errorMessage(err)
  }
})

async function copyCode() {
  try {
    await navigator.clipboard.writeText(appointment.value.appointmentCode)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    // Clipboard blocked: the code is on screen to copy by hand.
  }
}
</script>

<template>
  <section class="container page confirmation-page">
    <p v-if="!appointment && !error" class="muted">Loading…</p>
    <div v-else-if="error" class="card"><p class="alert error" role="alert">{{ error }}</p></div>

    <div v-else class="card ticket">
      <div class="ticket-head">
        <span class="tick" aria-hidden="true"><AppIcon name="check-circle" :size="40" /></span>
        <h1>{{ appointment.status === 'PENDING' ? 'Appointment requested' : 'Your appointment' }}</h1>
        <p class="muted">
          <template v-if="appointment.status === 'PENDING'">
            We have sent your request to {{ appointment.surgeon.fullName }}. It stays <strong>Pending</strong> until the surgeon confirms it.
          </template>
          <template v-else>Current status of this appointment.</template>
        </p>
      </div>

      <div class="code-box">
        <span class="label">Appointment ID</span>
        <span class="code">{{ appointment.appointmentCode }}</span>
        <button type="button" class="btn btn-outline btn-sm" @click="copyCode">{{ copied ? 'Copied!' : 'Copy' }}</button>
      </div>

      <div class="surgeon">
        <SurgeonAvatar :photo-url="appointment.surgeon.photoUrl" :name="appointment.surgeon.fullName" :size="60" />
        <div>
          <strong>{{ appointment.surgeon.fullName }}</strong>
          <span class="muted">{{ appointment.surgeon.specialty }}<template v-if="appointment.surgeon.designation"> · {{ appointment.surgeon.designation }}</template></span>
        </div>
        <AppointmentStatusBadge :status="appointment.status" />
      </div>

      <dl class="facts">
        <div><dt><AppIcon name="calendar" :size="15" /> Date</dt><dd>{{ formatDate(appointment.date) }}</dd></div>
        <div><dt><AppIcon name="clock" :size="15" /> Time</dt><dd>{{ formatTime(appointment.time) }}</dd></div>
        <div><dt><AppIcon name="building" :size="15" /> Hospital</dt><dd>{{ appointment.surgeon.hospitalName }}<template v-if="appointment.surgeon.hospitalAddress"><br /><span class="muted">{{ appointment.surgeon.hospitalAddress }}</span></template></dd></div>
        <div><dt><AppIcon name="wallet" :size="15" /> Fee</dt><dd>{{ formatFee(appointment.surgeon.consultationFee) }}</dd></div>
        <div><dt><AppIcon name="file-text" :size="15" /> Booked on</dt><dd>{{ formatDateTime(appointment.bookedAt) }}</dd></div>
      </dl>

      <ol class="next">
        <li><strong>The surgeon reviews your request.</strong> You will see the status change to Confirmed in My Appointments.</li>
        <li><strong>Arrive 10–15 minutes early</strong> with your Appointment ID and any recent mammogram or scan reports.</li>
        <li><strong>Need to change it?</strong> You can reschedule or cancel from My Appointments while it is pending or confirmed.</li>
      </ol>

      <div class="actions">
        <RouterLink :to="APP_ROUTES.myAppointments" class="btn btn-primary">View my appointments</RouterLink>
        <RouterLink :to="APP_ROUTES.surgeons" class="btn btn-outline">Find another surgeon</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ticket { max-width: 720px; margin: 0 auto; padding: 2rem; border-radius: 26px; }
.ticket-head { text-align: center; }
.tick { display: inline-grid; place-items: center; width: 76px; height: 76px; border-radius: 50%; background: #e8f5e9; color: #2e7d32; box-shadow: 0 0 0 8px rgba(46, 125, 50, 0.08); }
.ticket-head h1 { margin: 0.9rem 0 0.4rem; }
.ticket-head p { margin: 0 auto; max-width: 520px; line-height: 1.6; }
.code-box { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 0.8rem; margin: 1.5rem 0; padding: 1rem; border-radius: 18px; border: 2px dashed var(--pink-300); background: var(--pink-50); }
.label { font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.08em; color: var(--muted); }
.code { font-family: ui-monospace, Consolas, monospace; font-size: 1.35rem; font-weight: 700; color: var(--purple-900); letter-spacing: 0.04em; }
.surgeon { display: flex; align-items: center; gap: 0.9rem; padding: 1rem 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.surgeon > div { display: grid; flex: 1; min-width: 0; }
.surgeon strong { color: var(--purple-900); }
.surgeon .muted { font-size: 0.86rem; }
.facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 1rem; margin: 1.2rem 0; }
.facts dt { display: flex; align-items: center; gap: 0.35rem; font-size: 0.76rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.facts dt :deep(svg) { color: var(--pink-600); }
.facts dd { margin: 0.2rem 0 0; color: var(--purple-900); font-weight: 500; }
.next { margin: 0; padding: 1rem 1rem 1rem 2.2rem; border-radius: 16px; background: #f6f0ff; color: var(--text); line-height: 1.6; font-size: 0.92rem; }
.next li + li { margin-top: 0.4rem; }
.actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 0.7rem; margin-top: 1.5rem; }
@media (max-width: 520px) { .ticket { padding: 1.3rem; } .actions .btn { flex: 1; justify-content: center; } }
</style>
