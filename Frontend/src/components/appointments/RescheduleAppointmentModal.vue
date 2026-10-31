<script setup>
import { ref, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import AvailabilityCalendar from './AvailabilityCalendar.vue'
import { reschedule as rescheduleRules } from '../../appointments/rules'
import { rescheduleAppointment } from '../../services/appointmentService'
import { errorMessage, fieldErrors } from '../../services/http'
import { formatDate, formatTime } from '../../appointments/format'

// Pick a new free slot with the same Breast Surgeon. The server re-checks the slot (it may have just been
// taken) and puts the appointment back to Pending for the surgeon to confirm. Emits done(updated) and close.
const props = defineProps({ appointment: { type: Object, required: true } })
const emit = defineEmits(['done', 'close'])

const choice = ref(null)
const busy = ref(false)
const error = ref('')
const calendar = ref(null)
const dialog = ref(null)
onMounted(() => dialog.value?.focus())

async function submit() {
  if (!choice.value) return
  busy.value = true
  error.value = ''
  try {
    emit('done', await rescheduleAppointment(props.appointment.id, choice.value))
  } catch (err) {
    const fields = fieldErrors(err)
    error.value = fields.date || fields.time || errorMessage(err)
    // Someone may have taken the slot: show the calendar as it is now.
    if (err.response?.status === 409) {
      choice.value = null
      calendar.value?.reload()
    }
  } finally {
    busy.value = false
  }
}

function close() {
  if (!busy.value) emit('close')
}
</script>

<template>
  <div class="overlay" @click.self="close" @keydown.esc="close">
    <div ref="dialog" class="modal card" role="dialog" aria-modal="true" aria-labelledby="reschedule-title" tabindex="-1">
      <header>
        <div>
          <h2 id="reschedule-title">Reschedule appointment</h2>
          <p class="muted">
            {{ appointment.surgeon.fullName }} · currently {{ formatDate(appointment.date) }} at {{ formatTime(appointment.time) }}
          </p>
        </div>
        <button type="button" class="close" aria-label="Close" :disabled="busy" @click="close"><AppIcon name="close" :size="20" /></button>
      </header>

      <AvailabilityCalendar
        ref="calendar"
        v-model="choice"
        :surgeon-id="appointment.surgeon.id"
        :current="{ date: appointment.date, time: appointment.time }"
      />

      <p class="rule"><AppIcon name="alert-circle" :size="15" /> {{ rescheduleRules }}</p>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>

      <footer>
        <p class="summary">
          <template v-if="choice">New time: <strong>{{ formatDate(choice.date) }} at {{ formatTime(choice.time) }}</strong></template>
          <template v-else>Choose a new date and time above.</template>
        </p>
        <div class="buttons">
          <button type="button" class="btn btn-outline" :disabled="busy" @click="close">Cancel</button>
          <button type="button" class="btn btn-primary" :disabled="!choice || busy" @click="submit">
            <span v-if="busy" class="spinner" aria-hidden="true"></span>
            {{ busy ? 'Sending…' : 'Request new time' }}
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.overlay { position: fixed; inset: 0; z-index: 50; display: grid; place-items: center; padding: 1rem; background: rgba(38, 26, 63, 0.45); backdrop-filter: blur(3px); overflow-y: auto; }
.modal { width: 100%; max-width: 820px; padding: 1.5rem; border-radius: 24px; box-shadow: 0 30px 60px rgba(38, 26, 63, 0.3); outline: none; }
header { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
h2 { margin: 0; color: var(--purple-900); font-size: 1.3rem; }
header p { margin: 0.2rem 0 0; }
.close { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border: 0; border-radius: 50%; background: var(--pink-50); color: var(--purple-900); cursor: pointer; }
.close:hover { background: var(--pink-100); }
.rule { display: flex; align-items: center; gap: 0.4rem; margin: 1rem 0 0; font-size: 0.84rem; color: var(--muted); }
.rule :deep(svg) { flex: none; color: var(--pink-600); }
footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.8rem; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border); }
.summary { margin: 0; color: var(--purple-900); }
.buttons { display: flex; gap: 0.6rem; }
@media (max-width: 520px) { .buttons { width: 100%; } .buttons .btn { flex: 1; justify-content: center; } }
</style>
