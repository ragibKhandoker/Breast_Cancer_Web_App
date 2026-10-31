<script setup>
import { ref, computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import SurgeonAvatar from './SurgeonAvatar.vue'
import AppointmentStatusBadge from './AppointmentStatusBadge.vue'
import { STATUS, isOpen, formatDate, formatTime, formatDateTime, formatFee } from '../../appointments/format'

// A patient's Breast Surgeon appointments, filterable by status. Emits cancel(appointment) and
// reschedule(appointment); the page opens the dialogs and talks to the server.
const props = defineProps({ appointments: { type: Array, required: true } })
defineEmits(['cancel', 'reschedule'])

const filter = ref('ALL')
const FILTERS = [{ id: 'ALL', label: 'All' }, ...Object.entries(STATUS).map(([id, s]) => ({ id, label: s.label }))]
const counts = computed(() => {
  const c = { ALL: props.appointments.length }
  props.appointments.forEach((a) => (c[a.status] = (c[a.status] ?? 0) + 1))
  return c
})
const shown = computed(() => props.appointments.filter((a) => filter.value === 'ALL' || a.status === filter.value))
</script>

<template>
  <div class="appointment-history">
    <div class="filters" role="group" aria-label="Filter by status">
      <button
        v-for="f in FILTERS"
        :key="f.id"
        type="button"
        :class="{ on: filter === f.id }"
        :aria-pressed="filter === f.id"
        :data-filter="f.id"
        @click="filter = f.id"
      >
        {{ f.label }} <span class="count">{{ counts[f.id] ?? 0 }}</span>
      </button>
    </div>

    <p v-if="!shown.length" class="empty muted">No {{ filter === 'ALL' ? '' : STATUS[filter].label.toLowerCase() }} appointments.</p>

    <ul v-else class="list">
      <li v-for="a in shown" :key="a.id" class="card item" :class="a.status.toLowerCase()">
        <div class="when">
          <span class="day">{{ formatDate(a.date, { weekday: false }).split(' ')[0] }}</span>
          <span class="mon">{{ formatDate(a.date, { weekday: false }).split(' ').slice(1).join(' ') }}</span>
          <span class="time"><AppIcon name="clock" :size="12" /> {{ formatTime(a.time) }}</span>
        </div>

        <div class="main">
          <div class="surgeon">
            <SurgeonAvatar :photo-url="a.surgeon.photoUrl" :name="a.surgeon.fullName" :size="48" />
            <div>
              <h3>{{ a.surgeon.fullName }}</h3>
              <p class="muted">{{ a.surgeon.specialty }}<template v-if="a.surgeon.designation"> · {{ a.surgeon.designation }}</template></p>
            </div>
            <AppointmentStatusBadge :status="a.status" class="badge" />
          </div>
          <dl class="meta-grid">
            <div><dt>Appointment ID</dt><dd class="code">{{ a.appointmentCode }}</dd></div>
            <div><dt>Hospital</dt><dd>{{ a.surgeon.hospitalName }}</dd></div>
            <div><dt>Date &amp; time</dt><dd>{{ formatDate(a.date) }}, {{ formatTime(a.time) }}</dd></div>
            <div><dt>Booked on</dt><dd>{{ formatDateTime(a.bookedAt) }}</dd></div>
            <div v-if="a.surgeon.consultationFee != null"><dt>Fee</dt><dd>{{ formatFee(a.surgeon.consultationFee) }}</dd></div>
          </dl>
          <p v-if="a.previousDate && a.status === 'PENDING'" class="info-line">
            <AppIcon name="refresh" :size="14" /> Moved from {{ formatDate(a.previousDate) }}, {{ formatTime(a.previousTime) }} — waiting for the surgeon to confirm.
          </p>
          <p v-if="a.status === 'CANCELLED'" class="info-line cancelled-line">
            <AppIcon name="close" :size="14" />
            {{ a.cancelledBy === 'SURGEON' ? 'Cancelled by the surgeon' : 'You cancelled this' }}<template v-if="a.cancelReason">: “{{ a.cancelReason }}”</template>
          </p>
          <div v-if="isOpen(a)" class="actions">
            <button type="button" class="btn btn-outline btn-sm" @click="$emit('reschedule', a)">
              <AppIcon name="calendar" :size="15" /> Reschedule
            </button>
            <button type="button" class="btn btn-sm cancel-btn" @click="$emit('cancel', a)">
              <AppIcon name="close" :size="15" /> Cancel
            </button>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 0.45rem; margin-bottom: 1rem; }
.filters button {
  display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.45rem 0.95rem; border-radius: 999px; border: 1.5px solid var(--border);
  background: #fff; color: var(--purple-900); font: 500 0.88rem 'Poppins', sans-serif; cursor: pointer;
}
.filters button:hover { border-color: var(--pink-300); }
.filters button.on { background: var(--purple-900); border-color: var(--purple-900); color: #fff; }
.count { min-width: 20px; padding: 0 0.35rem; border-radius: 999px; background: var(--pink-50); color: var(--pink-600); font-size: 0.72rem; font-weight: 700; text-align: center; }
.filters button.on .count { background: rgba(255, 255, 255, 0.2); color: #fff; }
.empty { padding: 2rem; text-align: center; border: 2px dashed var(--border); border-radius: 18px; }
.list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.9rem; }
.item { display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 1.2rem; padding: 1.2rem; border-radius: 20px; border-left: 5px solid #f8d9a0; }
.item.confirmed { border-left-color: #66bb6a; }
.item.cancelled { border-left-color: #cfc8da; opacity: 0.9; }
.item.completed { border-left-color: #9575cd; }
.when { display: grid; align-content: center; justify-items: center; gap: 0.1rem; padding: 0.7rem 0.4rem; border-radius: 16px; background: var(--pink-50); color: var(--purple-900); text-align: center; }
.when .day { font-size: 1.8rem; font-weight: 700; line-height: 1; color: var(--pink-600); }
.when .mon { font-size: 0.8rem; font-weight: 600; }
.when .time { display: inline-flex; align-items: center; gap: 0.2rem; margin-top: 0.3rem; font-size: 0.74rem; color: var(--muted); }
.surgeon { display: flex; align-items: center; gap: 0.8rem; }
.surgeon h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.surgeon p { margin: 0.1rem 0 0; font-size: 0.85rem; }
.badge { margin-left: auto; }
.meta-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.6rem 1rem; margin: 0.9rem 0 0; }
.meta-grid dt { font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.meta-grid dd { margin: 0.1rem 0 0; font-size: 0.9rem; color: var(--text); overflow-wrap: anywhere; }
.code { font-family: ui-monospace, Consolas, monospace; font-weight: 600; color: var(--purple-900) !important; }
.info-line { display: flex; align-items: center; gap: 0.4rem; margin: 0.8rem 0 0; padding: 0.5rem 0.7rem; border-radius: 10px; background: #fff7e6; color: #8a5300; font-size: 0.84rem; }
.info-line :deep(svg) { flex: none; }
.cancelled-line { background: #f3f1f6; color: #5f5873; }
.actions { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.9rem; }
.cancel-btn { background: #fff; border-color: #f5b5b0; color: var(--high); }
.cancel-btn:hover { background: #fdecea; box-shadow: none; }
@media (max-width: 600px) {
  .item { grid-template-columns: 1fr; }
  .when { grid-auto-flow: column; justify-content: start; gap: 0.5rem; }
  .when .time { margin: 0; }
  .surgeon { flex-wrap: wrap; }
}
</style>
