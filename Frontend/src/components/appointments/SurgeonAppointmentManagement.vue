<script setup>
import { ref, computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import AppointmentStatusBadge from './AppointmentStatusBadge.vue'
import CancelAppointmentDialog from './CancelAppointmentDialog.vue'
import { confirmAppointment, completeAppointment, cancelAppointment } from '../../services/appointmentService'
import { errorMessage } from '../../services/http'
import { formatDate, formatTime, formatDateTime, todayIso, initials } from '../../appointments/format'

// The surgeon's own appointments: accept or reject requests, mark consultations completed, cancel.
// Emits changed(updated appointment) after every successful action so the dashboard can update its counts.
const props = defineProps({
  appointments: { type: Array, required: true },
  initialView: { type: String, default: 'REQUESTS' },
})
const emit = defineEmits(['changed'])

const today = todayIso()
const VIEWS = [
  { id: 'REQUESTS', label: 'Requests', test: (a) => a.status === 'PENDING' },
  { id: 'UPCOMING', label: 'Upcoming', test: (a) => a.status === 'CONFIRMED' },
  { id: 'HISTORY', label: 'History', test: (a) => a.status === 'COMPLETED' || a.status === 'CANCELLED' },
  { id: 'ALL', label: 'All', test: () => true },
]
const view = ref(props.initialView)
const search = ref('')
const viewOf = (id) => VIEWS.find((v) => v.id === id)
const count = (id) => props.appointments.filter(viewOf(id).test).length

const shown = computed(() => {
  const q = search.value.trim().toLowerCase()
  const list = props.appointments.filter(viewOf(view.value).test).filter(
    (a) => !q || a.patient.fullName.toLowerCase().includes(q) || a.appointmentCode.toLowerCase().includes(q),
  )
  // History newest first; everything else soonest first.
  return view.value === 'HISTORY' ? [...list].reverse() : list
})

const expanded = ref(null)
const busy = ref('')
const messages = ref({})

async function act(appointment, action) {
  busy.value = appointment.id
  messages.value = { ...messages.value, [appointment.id]: '' }
  try {
    const updated = action === 'confirm' ? await confirmAppointment(appointment.id) : await completeAppointment(appointment.id)
    emit('changed', updated)
  } catch (err) {
    messages.value = { ...messages.value, [appointment.id]: errorMessage(err) }
  } finally {
    busy.value = ''
  }
}

// Rejecting a request and cancelling a confirmed appointment share one dialog.
const cancelling = ref(null)
const cancelError = ref('')
const cancelBusy = ref(false)
async function doCancel(reason) {
  cancelBusy.value = true
  cancelError.value = ''
  try {
    emit('changed', await cancelAppointment(cancelling.value.id, reason))
    cancelling.value = null
  } catch (err) {
    cancelError.value = errorMessage(err)
  } finally {
    cancelBusy.value = false
  }
}
</script>

<template>
  <div class="surgeon-appointments">
    <div class="toolbar">
      <div class="views" role="group" aria-label="Show">
        <button v-for="v in VIEWS" :key="v.id" type="button" :class="{ on: view === v.id }" :aria-pressed="view === v.id" :data-view="v.id" @click="view = v.id">
          {{ v.label }} <span class="count">{{ count(v.id) }}</span>
        </button>
      </div>
      <label class="search">
        <AppIcon name="search" :size="16" />
        <input v-model="search" type="search" placeholder="Patient or appointment ID" aria-label="Search appointments" />
      </label>
    </div>

    <p v-if="!shown.length" class="empty muted">
      {{ view === 'REQUESTS' ? 'No new requests. New bookings will appear here.' : 'Nothing to show here.' }}
    </p>

    <ul v-else class="rows">
      <li v-for="a in shown" :key="a.id" class="row card" :class="{ open: expanded === a.id }">
        <div class="row-main">
          <span class="initials" aria-hidden="true">{{ initials(a.patient.fullName) }}</span>
          <div class="who">
            <strong>{{ a.patient.fullName }}</strong>
            <span class="muted code">{{ a.appointmentCode }}</span>
          </div>
          <div class="slot">
            <span><AppIcon name="calendar" :size="14" /> {{ formatDate(a.date) }}</span>
            <span><AppIcon name="clock" :size="14" /> {{ formatTime(a.time) }}<em v-if="a.date === today" class="today">Today</em></span>
          </div>
          <AppointmentStatusBadge :status="a.status" />
          <button type="button" class="toggle" :aria-expanded="expanded === a.id" :aria-label="`Details for ${a.patient.fullName}`" @click="expanded = expanded === a.id ? null : a.id">
            <AppIcon name="arrow-right" :size="18" />
          </button>
        </div>

        <div v-if="expanded === a.id" class="details">
          <dl>
            <div><dt>Email</dt><dd>{{ a.patient.email }}</dd></div>
            <div><dt>Phone</dt><dd>{{ a.patient.phone || 'Not given' }}</dd></div>
            <div><dt>Booked</dt><dd>{{ formatDateTime(a.bookedAt) }}</dd></div>
            <div v-if="a.previousDate"><dt>Moved from</dt><dd>{{ formatDate(a.previousDate) }}, {{ formatTime(a.previousTime) }}</dd></div>
            <div class="wide"><dt>Reason for visit</dt><dd>{{ a.reason || 'No reason given' }}</dd></div>
            <div v-if="a.status === 'CANCELLED'" class="wide">
              <dt>Cancelled by {{ a.cancelledBy === 'SURGEON' ? 'you' : 'the patient' }}</dt><dd>{{ a.cancelReason || 'No reason given' }}</dd>
            </div>
          </dl>
        </div>

        <div v-if="a.status === 'PENDING' || a.status === 'CONFIRMED'" class="row-actions">
          <template v-if="a.status === 'PENDING'">
            <button type="button" class="btn btn-primary btn-sm" :disabled="busy === a.id" @click="act(a, 'confirm')">
              <AppIcon name="check-circle" :size="15" /> Accept
            </button>
            <button type="button" class="btn btn-sm reject" :disabled="busy === a.id" @click="cancelling = a">
              <AppIcon name="close" :size="15" /> Reject
            </button>
          </template>
          <template v-else>
            <button type="button" class="btn btn-primary btn-sm" :disabled="busy === a.id || a.date > today"
              :title="a.date > today ? 'Available on the appointment day' : ''" @click="act(a, 'complete')">
              <AppIcon name="heart" :size="15" /> Mark completed
            </button>
            <button type="button" class="btn btn-sm reject" :disabled="busy === a.id" @click="cancelling = a">Cancel</button>
          </template>
          <p v-if="messages[a.id]" class="alert error inline" role="alert">{{ messages[a.id] }}</p>
        </div>
      </li>
    </ul>

    <CancelAppointmentDialog
      v-if="cancelling"
      :appointment="cancelling"
      :who="cancelling.patient.fullName"
      :title="cancelling.status === 'PENDING' ? 'Reject this request?' : 'Cancel this appointment?'"
      :confirm-label="cancelling.status === 'PENDING' ? 'Reject request' : 'Cancel appointment'"
      :busy="cancelBusy"
      :error="cancelError"
      @confirm="doCancel"
      @close="cancelling = null; cancelError = ''"
    />
  </div>
</template>

<style scoped>
.toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.7rem; margin-bottom: 1rem; }
.views { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.views button {
  display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.45rem 0.9rem; border-radius: 999px; border: 1.5px solid var(--border);
  background: #fff; color: var(--purple-900); font: 500 0.88rem 'Poppins', sans-serif; cursor: pointer;
}
.views button.on { background: var(--purple-900); border-color: var(--purple-900); color: #fff; }
.count { min-width: 20px; padding: 0 0.35rem; border-radius: 999px; background: var(--pink-50); color: var(--pink-600); font-size: 0.72rem; font-weight: 700; text-align: center; }
.views button.on .count { background: rgba(255, 255, 255, 0.2); color: #fff; }
.search { display: flex; align-items: center; gap: 0.4rem; padding: 0 0.8rem; border: 1.5px solid var(--border); border-radius: 999px; background: #fff; color: var(--muted); }
.search input { border: 0; outline: none; padding: 0.5rem 0; font: inherit; min-width: 210px; background: transparent; }
.search:focus-within { border-color: var(--pink-600); }
.empty { padding: 2rem; text-align: center; border: 2px dashed var(--border); border-radius: 18px; }
.rows { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.6rem; }
.row { padding: 0.9rem 1.1rem; border-radius: 16px; }
.row-main { display: grid; grid-template-columns: auto minmax(0, 1.2fr) minmax(0, 1.3fr) auto auto; align-items: center; gap: 0.9rem; }
.initials { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 50%; background: #f1ebff; color: #4a2a8a; font-weight: 700; }
.who { display: grid; min-width: 0; }
.who strong { color: var(--purple-900); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.code { font-family: ui-monospace, Consolas, monospace; font-size: 0.78rem; }
.slot { display: grid; gap: 0.15rem; font-size: 0.86rem; color: var(--text); }
.slot span { display: inline-flex; align-items: center; gap: 0.35rem; }
.slot :deep(svg) { color: var(--pink-600); }
.today { margin-left: 0.4rem; padding: 0 0.4rem; border-radius: 6px; background: var(--pink-600); color: #fff; font-size: 0.68rem; font-style: normal; font-weight: 700; }
.toggle { display: grid; place-items: center; width: 34px; height: 34px; border: 0; border-radius: 50%; background: var(--pink-50); color: var(--purple-900); cursor: pointer; transition: transform 0.2s; }
.row.open .toggle { transform: rotate(90deg); }
.details { margin-top: 0.8rem; padding-top: 0.8rem; border-top: 1px dashed var(--border); }
.details dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.6rem 1rem; margin: 0; }
.details .wide { grid-column: 1 / -1; }
.details dt { font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.details dd { margin: 0.1rem 0 0; font-size: 0.9rem; overflow-wrap: anywhere; }
.row-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-top: 0.8rem; }
.reject { background: #fff; border-color: #f5b5b0; color: var(--high); }
.reject:hover { background: #fdecea; box-shadow: none; }
.inline { margin: 0; padding: 0.4rem 0.7rem; font-size: 0.85rem; }
@media (max-width: 760px) {
  .row-main { grid-template-columns: auto minmax(0, 1fr) auto; }
  .slot { grid-column: 2 / 3; grid-row: 2; }
  .row-main > .status-badge { grid-column: 3; grid-row: 2; }
}
</style>
