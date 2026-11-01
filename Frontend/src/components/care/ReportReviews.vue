<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { myDoctors, requestReview, reportReviews, writeReview, REVIEW_STATUS } from '../../services/careService'
import { errorMessage, fieldErrors } from '../../services/http'
import { APP_ROUTES } from '../../config/appRoutes'

// On a report: the owner asks one of their doctors for a review and reads the answers; a doctor who was asked
// writes (or updates) their review here.
const props = defineProps({
  reportId: { type: String, required: true },
  isOwner: { type: Boolean, default: false },
  isDoctor: { type: Boolean, default: false },
})
const emit = defineEmits(['shared'])

const reviews = ref([])
const doctors = ref([])
const loading = ref(true)
const error = ref('')
const doctorId = ref('')
const message = ref('')
const sending = ref(false)
const note = ref('')
const saving = ref(false)
const saved = ref('')

const asked = computed(() => new Set(reviews.value.filter((r) => r.status === 'REQUESTED').map((r) => r.doctorId)))
const askable = computed(() => doctors.value.filter((d) => !asked.value.has(d.id)))
const mine = computed(() => reviews.value.find((r) => r.status !== 'WITHDRAWN'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[reviews.value, doctors.value] = await Promise.all([reportReviews(props.reportId), props.isOwner ? myDoctors() : []])
    if (props.isDoctor && mine.value) note.value = mine.value.doctorNote ?? ''
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function ask() {
  sending.value = true
  error.value = ''
  try {
    await requestReview(props.reportId, doctorId.value, message.value)
    doctorId.value = ''
    message.value = ''
    emit('shared')
    await load()
  } catch (err) {
    error.value = fieldErrors(err).message ?? errorMessage(err)
  } finally {
    sending.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  saved.value = ''
  try {
    await writeReview(mine.value.id, note.value)
    saved.value = 'Review saved. The patient has been told.'
    await load()
  } catch (err) {
    error.value = fieldErrors(err).note ?? errorMessage(err)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-if="isOwner || (isDoctor && mine)" class="card reviews">
    <h2><AppIcon name="users" :size="18" /> Doctor's review</h2>
    <p v-if="loading" class="muted small">Loading…</p>

    <template v-else-if="isOwner">
      <ul v-if="reviews.length" class="list">
        <li v-for="r in reviews" :key="r.id">
          <div class="head">
            <strong>{{ r.doctorName }}</strong>
            <span class="pill" :class="r.status.toLowerCase()">{{ REVIEW_STATUS[r.status] }}</span>
          </div>
          <p v-if="r.doctorNote" class="note">{{ r.doctorNote }}</p>
        </li>
      </ul>
      <form v-if="askable.length" class="ask" @submit.prevent="ask">
        <label class="small" for="review-doctor">Ask your doctor to review this report</label>
        <select id="review-doctor" v-model="doctorId" :disabled="sending" required>
          <option value="" disabled>Choose your doctor</option>
          <option v-for="d in askable" :key="d.id" :value="d.id">{{ d.fullName }}{{ d.hospitalName ? ` · ${d.hospitalName}` : '' }}</option>
        </select>
        <textarea v-model="message" rows="2" maxlength="500" placeholder="Message (optional)" :disabled="sending"></textarea>
        <button type="submit" class="btn btn-primary btn-sm send" :disabled="sending || !doctorId">{{ sending ? 'Sending…' : 'Send request' }}</button>
      </form>
      <p v-else-if="!doctors.length" class="muted small">
        You can ask for a review after a Breast Surgeon confirms your appointment.
        <RouterLink :to="APP_ROUTES.surgeons">Find a surgeon</RouterLink>
      </p>
    </template>

    <form v-else class="ask" @submit.prevent="save">
      <p class="small"><strong>{{ mine.patientName }}</strong> asked for your review<span v-if="mine.message">: “{{ mine.message }}”</span></p>
      <label class="small" for="review-note">Your review for the patient</label>
      <textarea id="review-note" v-model="note" rows="5" maxlength="2000" :disabled="saving" required></textarea>
      <button type="submit" class="btn btn-primary btn-sm send" :disabled="saving || note.trim().length < 2">
        {{ saving ? 'Saving…' : mine.status === 'REVIEWED' ? 'Update review' : 'Send review' }}
      </button>
      <p v-if="saved" class="ok small" role="status">{{ saved }}</p>
    </form>

    <p v-if="error" class="error small" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.reviews { margin-top: 1rem; display: grid; gap: 0.7rem; }
.reviews h2 { display: flex; align-items: center; gap: 0.4rem; margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.small { font-size: 0.86rem; margin: 0; }
.list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.6rem; }
.list li { padding: 0.7rem 0.8rem; border: 1px solid var(--border); border-radius: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
.head strong { color: var(--purple-900); }
.note { margin: 0.5rem 0 0; white-space: pre-line; color: var(--text); line-height: 1.6; }
.pill { padding: 0.12rem 0.55rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; white-space: nowrap; }
.pill.requested { background: #fff4e5; color: #8a4b00; }
.pill.reviewed { background: #e8f5e9; color: #1b5e20; }
.pill.withdrawn { background: #f3f1f6; color: #5f5873; }
.ask { display: grid; gap: 0.45rem; }
.ask label { color: var(--purple-900); font-weight: 600; }
.ask select, .ask textarea { width: 100%; padding: 0.5rem 0.65rem; border: 1.5px solid #f3d4e3; border-radius: 10px; background: #fff; color: #2a1650; font: inherit; }
.send { justify-self: start; background: #d81b6a; color: #fff; }
.ok { color: var(--low); }
.reviews a { color: var(--pink-600); font-weight: 600; text-decoration: underline; }
</style>
