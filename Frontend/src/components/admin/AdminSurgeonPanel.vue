<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import SurgeonAvatar from '../appointments/SurgeonAvatar.vue'
import { listSurgeonProfiles, reviewSurgeon, surgeonDocumentUrl } from '../../services/adminService'
import { errorMessage, fieldErrors } from '../../services/http'
import { dayLabels, formatFee, formatTime, formatDateTime } from '../../appointments/format'

// Checking Breast Surgeons' credentials before they are listed. Only APPROVED profiles appear in Find a
// Breast Surgeon and can take bookings; the server also refuses approval without a verification document.
const emit = defineEmits(['counts-changed'])

const STATUS = {
  PENDING: { label: 'Pending', icon: 'refresh' },
  APPROVED: { label: 'Approved', icon: 'check-circle' },
  REJECTED: { label: 'Rejected', icon: 'alert-circle' },
}

const profiles = ref([])
const loading = ref(true)
const listError = ref('')
const filter = ref('PENDING')
const search = ref('')

const counts = computed(() => {
  const c = { PENDING: 0, APPROVED: 0, REJECTED: 0, ALL: profiles.value.length }
  profiles.value.forEach((p) => c[p.status]++)
  return c
})
const shown = computed(() => {
  const q = search.value.trim().toLowerCase()
  return profiles.value.filter(
    (p) => (filter.value === 'ALL' || p.status === filter.value)
      && (!q || p.fullName.toLowerCase().includes(q) || p.registrationNumber.toLowerCase().includes(q)),
  )
})

async function load() {
  loading.value = true
  listError.value = ''
  try {
    profiles.value = await listSurgeonProfiles()
    if (selected.value) selected.value = profiles.value.find((p) => p.id === selected.value.id) ?? null
  } catch (err) {
    listError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const selected = ref(null)
const decision = reactive({ note: '', saving: false, error: '', message: '' })
function select(p) {
  selected.value = p
  Object.assign(decision, { note: '', error: '', message: '' })
}

async function decide(status) {
  decision.saving = true
  decision.error = ''
  decision.message = ''
  try {
    const updated = await reviewSurgeon(selected.value.id, status, decision.note.trim())
    profiles.value = profiles.value.map((p) => (p.id === updated.id ? updated : p))
    selected.value = updated
    decision.message = { APPROVED: 'Approved. The surgeon is now listed and can take bookings.', REJECTED: 'Rejected. The surgeon sees your note on their profile page.', PENDING: 'Moved back to pending.' }[status]
    decision.note = ''
    emit('counts-changed')
  } catch (err) {
    decision.error = fieldErrors(err).note || errorMessage(err)
  } finally {
    decision.saving = false
  }
}

onMounted(load)
</script>

<template>
  <div class="surgeon-review">
    <div class="queue card">
      <div class="filters" role="group" aria-label="Filter by status">
        <button v-for="key in ['PENDING', 'APPROVED', 'REJECTED', 'ALL']" :key="key" type="button" :class="{ on: filter === key }" :aria-pressed="filter === key" @click="filter = key">
          {{ key === 'ALL' ? 'All' : STATUS[key].label }} <span class="count">{{ counts[key] }}</span>
        </button>
      </div>
      <label class="search"><AppIcon name="search" :size="15" /><input v-model="search" type="search" placeholder="Name or registration no." aria-label="Search surgeons" /></label>
      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="listError" class="alert error">{{ listError }}</p>
      <p v-else-if="!shown.length" class="muted empty">No {{ filter === 'ALL' ? '' : STATUS[filter].label.toLowerCase() }} Breast Surgeon profiles.</p>
      <ul v-else class="list">
        <li v-for="p in shown" :key="p.id">
          <button type="button" :class="{ on: selected?.id === p.id }" @click="select(p)">
            <SurgeonAvatar :photo-url="p.photoUrl" :name="p.fullName" :size="40" />
            <span class="who"><strong>{{ p.fullName }}</strong><span class="muted">{{ p.registrationNumber }} · {{ p.documents.length }} doc{{ p.documents.length === 1 ? '' : 's' }}</span></span>
            <span class="tag" :class="p.status.toLowerCase()">{{ STATUS[p.status].label }}</span>
          </button>
        </li>
      </ul>
    </div>

    <div class="detail card">
      <p v-if="!selected" class="muted placeholder"><AppIcon name="heart" :size="28" /> Choose a profile to check the surgeon’s credentials.</p>
      <template v-else>
        <div class="top">
          <SurgeonAvatar :photo-url="selected.photoUrl" :name="selected.fullName" :size="76" />
          <div>
            <h3>{{ selected.fullName }}</h3>
            <p class="pink">{{ selected.designation }} · {{ selected.specialty }}</p>
            <p class="muted">Submitted {{ formatDateTime(selected.submittedAt) }}</p>
          </div>
          <span class="tag big" :class="selected.status.toLowerCase()">{{ STATUS[selected.status].label }}</span>
        </div>

        <dl class="facts">
          <div><dt>Registration no.</dt><dd>{{ selected.registrationNumber }}</dd></div>
          <div><dt>Qualifications</dt><dd>{{ selected.qualifications }}</dd></div>
          <div><dt>Breast surgery experience</dt><dd>{{ selected.experienceYears }} years</dd></div>
          <div><dt>Fee</dt><dd>{{ formatFee(selected.consultationFee) }}</dd></div>
          <div><dt>Hospital</dt><dd>{{ selected.hospitalName }}<br /><span class="muted">{{ selected.hospitalAddress }}</span></dd></div>
          <div><dt>Contact</dt><dd>{{ selected.email }}<br />{{ selected.phone }}</dd></div>
          <div><dt>Schedule</dt><dd>{{ dayLabels(selected.availableDays) }}<br /><span class="muted">{{ selected.timeSlots.map(formatTime).join(', ') }}</span></dd></div>
          <div class="wide"><dt>Biography</dt><dd class="bio">{{ selected.biography }}</dd></div>
        </dl>

        <h4>Verification documents</h4>
        <ul v-if="selected.documents.length" class="docs">
          <li v-for="d in selected.documents" :key="d.fileId">
            <AppIcon name="file-text" :size="16" />
            <a :href="surgeonDocumentUrl(selected.id, d.fileId)">{{ d.filename }}</a>
            <span class="muted">{{ Math.max(1, Math.round(d.size / 1024)) }} KB · {{ formatDateTime(d.uploadedAt) }}</span>
          </li>
        </ul>
        <p v-else class="alert error">No documents uploaded. The profile cannot be approved until the surgeon adds one.</p>

        <p v-if="selected.reviewNote" class="prev-note"><strong>Last note:</strong> {{ selected.reviewNote }} <span class="muted">— {{ selected.reviewedBy }}</span></p>

        <div class="form-field">
          <label for="surgeon-note">Note to the surgeon <span class="muted">(required to reject)</span></label>
          <textarea id="surgeon-note" v-model="decision.note" rows="3" maxlength="500" placeholder="For example: the registration certificate is unreadable, please upload a clearer copy."></textarea>
        </div>
        <p v-if="decision.error" class="alert error" role="alert">{{ decision.error }}</p>
        <p v-if="decision.message" class="alert success" role="status">{{ decision.message }}</p>
        <div class="decide">
          <button type="button" class="btn btn-primary btn-sm" :disabled="decision.saving || selected.status === 'APPROVED'" @click="decide('APPROVED')">
            <AppIcon name="check-circle" :size="15" /> Approve
          </button>
          <button type="button" class="btn btn-sm reject" :disabled="decision.saving" @click="decide('REJECTED')">
            <AppIcon name="alert-circle" :size="15" /> Reject
          </button>
          <button v-if="selected.status !== 'PENDING'" type="button" class="btn btn-outline btn-sm" :disabled="decision.saving" @click="decide('PENDING')">Back to pending</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.surgeon-review { display: grid; grid-template-columns: 330px minmax(0, 1fr); gap: 1rem; align-items: start; }
.queue, .detail { border-radius: 20px; }
.filters { display: flex; flex-wrap: wrap; gap: 0.3rem; }
.filters button { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.3rem 0.7rem; border-radius: 999px; border: 1.5px solid var(--border); background: #fff; color: var(--purple-900); font: 500 0.8rem 'Poppins', sans-serif; cursor: pointer; }
.filters button.on { background: var(--purple-900); border-color: var(--purple-900); color: #fff; }
.count { font-size: 0.7rem; font-weight: 700; opacity: 0.8; }
.search { display: flex; align-items: center; gap: 0.4rem; margin: 0.7rem 0; padding: 0 0.7rem; border: 1.5px solid var(--border); border-radius: 12px; color: var(--muted); }
.search input { flex: 1; min-width: 0; border: 0; outline: none; padding: 0.5rem 0; font: inherit; background: transparent; }
.empty { padding: 1rem 0; }
.list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.3rem; max-height: 560px; overflow-y: auto; }
.list button { display: flex; align-items: center; gap: 0.6rem; width: 100%; padding: 0.55rem; border: 1.5px solid transparent; border-radius: 14px; background: none; text-align: left; font: inherit; cursor: pointer; }
.list button:hover { background: var(--pink-50); }
.list button.on { border-color: var(--pink-300); background: var(--pink-50); }
.who { flex: 1; min-width: 0; display: grid; }
.who strong { color: var(--purple-900); font-size: 0.9rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.who .muted { font-size: 0.75rem; }
.tag { padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.7rem; font-weight: 700; }
.tag.big { font-size: 0.8rem; padding: 0.25rem 0.75rem; margin-left: auto; align-self: flex-start; }
.tag.pending { background: #fff7e6; color: #8a5300; }
.tag.approved { background: #e8f5e9; color: #1b5e20; }
.tag.rejected { background: #fdecea; color: #8e1c1c; }
.placeholder { display: grid; justify-items: center; gap: 0.6rem; padding: 3rem 1rem; text-align: center; }
.placeholder :deep(svg) { color: var(--pink-300); }
.top { display: flex; gap: 1rem; align-items: center; }
.top h3 { margin: 0; color: var(--purple-900); font-size: 1.2rem; }
.top p { margin: 0.1rem 0 0; font-size: 0.88rem; }
.pink { color: var(--pink-600); font-weight: 600; }
.facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.8rem 1.2rem; margin: 1.2rem 0; padding: 1rem; border-radius: 14px; background: #fbf8fc; }
.facts .wide { grid-column: 1 / -1; }
.facts dt { font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.facts dd { margin: 0.15rem 0 0; font-size: 0.9rem; overflow-wrap: anywhere; }
.bio { white-space: pre-line; line-height: 1.55; }
h4 { margin: 0 0 0.5rem; color: var(--purple-900); }
.docs { list-style: none; margin: 0 0 1rem; padding: 0; display: grid; gap: 0.35rem; }
.docs li { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; padding: 0.5rem 0.7rem; border-radius: 10px; background: #f6f0ff; font-size: 0.88rem; }
.docs li :deep(svg) { color: #6b3fb5; }
.docs a { color: #4a2a8a; font-weight: 600; text-decoration: underline; }
.prev-note { padding: 0.6rem 0.8rem; border-radius: 10px; background: #fff7e6; font-size: 0.88rem; }
textarea { width: 100%; padding: 0.7rem 0.9rem; border: 1.5px solid var(--border); border-radius: 12px; font: 400 0.92rem 'Poppins', sans-serif; color: var(--text); resize: vertical; }
textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.decide { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.6rem; }
.reject { background: #fff; border-color: #f5b5b0; color: var(--high); }
.reject:hover { background: #fdecea; box-shadow: none; }
@media (max-width: 960px) { .surgeon-review { grid-template-columns: 1fr; } .facts { grid-template-columns: 1fr; } }
</style>
