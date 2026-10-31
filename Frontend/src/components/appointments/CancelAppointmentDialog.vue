<script setup>
import { ref, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { formatDate, formatTime } from '../../appointments/format'

// "Are you sure?" before cancelling (or, for a surgeon, rejecting) an appointment, with an optional reason.
// The parent sends the request; emits confirm(reason) and close.
const props = defineProps({
  appointment: { type: Object, required: true },
  title: { type: String, default: 'Cancel this appointment?' },
  confirmLabel: { type: String, default: 'Yes, cancel it' },
  who: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  error: { type: String, default: '' },
})
const emit = defineEmits(['confirm', 'close'])

const reason = ref('')
const keepButton = ref(null)
onMounted(() => keepButton.value?.focus())

function close() {
  if (!props.busy) emit('close')
}
</script>

<template>
  <div class="overlay" @click.self="close" @keydown.esc="close">
    <div class="dialog card" role="alertdialog" aria-modal="true" aria-labelledby="cancel-title" aria-describedby="cancel-desc">
      <span class="icon" aria-hidden="true"><AppIcon name="alert-circle" :size="26" /></span>
      <h2 id="cancel-title">{{ title }}</h2>
      <p id="cancel-desc" class="muted">
        {{ appointment.appointmentCode }} · {{ who || appointment.surgeon.fullName }}<br />
        {{ formatDate(appointment.date) }} at {{ formatTime(appointment.time) }}
      </p>
      <p class="note">The time slot will be released so someone else can book it. This cannot be undone.</p>
      <div class="form-field">
        <label for="cancel-reason">Reason <span class="muted">(optional)</span></label>
        <textarea id="cancel-reason" v-model="reason" rows="3" maxlength="500" placeholder="Let the other side know why"></textarea>
      </div>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>
      <div class="buttons">
        <button ref="keepButton" type="button" class="btn btn-outline" :disabled="busy" @click="close">Keep it</button>
        <button type="button" class="btn btn-danger" :disabled="busy" @click="emit('confirm', reason.trim())">
          <span v-if="busy" class="spinner" aria-hidden="true"></span>
          {{ busy ? 'Working…' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay { position: fixed; inset: 0; z-index: 50; display: grid; place-items: center; padding: 1rem; background: rgba(38, 26, 63, 0.45); backdrop-filter: blur(3px); }
.dialog { width: 100%; max-width: 440px; padding: 1.6rem; border-radius: 22px; box-shadow: 0 30px 60px rgba(38, 26, 63, 0.3); }
.icon { display: grid; place-items: center; width: 50px; height: 50px; border-radius: 16px; background: #fdecea; color: var(--high); }
h2 { margin: 0.8rem 0 0.3rem; color: var(--purple-900); font-size: 1.25rem; }
#cancel-desc { margin: 0 0 0.6rem; line-height: 1.5; }
.note { margin: 0 0 0.8rem; padding: 0.6rem 0.8rem; border-radius: 10px; background: var(--warn-bg); color: var(--warn-text); font-size: 0.85rem; }
textarea { width: 100%; padding: 0.7rem 0.9rem; border: 1.5px solid var(--border); border-radius: 12px; font: 400 0.95rem 'Poppins', sans-serif; color: var(--text); resize: vertical; }
textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.buttons { display: flex; justify-content: flex-end; gap: 0.6rem; margin-top: 1rem; }
.btn-danger { background: var(--high); border-color: var(--high); color: #fff; }
.btn-danger:hover { box-shadow: 0 6px 16px rgba(198, 40, 40, 0.3); }
@media (max-width: 480px) { .buttons .btn { flex: 1; justify-content: center; } }
</style>
