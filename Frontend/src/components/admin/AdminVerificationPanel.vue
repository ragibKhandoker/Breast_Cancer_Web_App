<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import TempPasswordNotice from './TempPasswordNotice.vue'
import { GENDER_OPTIONS } from '../../auth/validation'
import { listUsers, getUserDetail, setVerification, resetPassword } from '../../services/adminService'
import { errorMessage, fieldErrors } from '../../services/http'

// Checking patients' and doctors' details: a queue on the left (Patients or Doctors), the selected person's
// details and the Verify / Reject decision on the right. Self-registered doctors start pending; patients cannot
// share reports with a doctor until they are verified.
const emit = defineEmits(['counts-changed'])

const STATUS = {
  PENDING: { label: 'Pending', icon: 'refresh' },
  VERIFIED: { label: 'Verified', icon: 'check-circle' },
  REJECTED: { label: 'Rejected', icon: 'alert-circle' },
}

const accounts = ref([])
const who = ref('PATIENT') // which queue: PATIENT or DOCTOR
const noun = computed(() => (who.value === 'DOCTOR' ? 'doctor' : 'patient'))
const patients = computed(() => accounts.value.filter((u) => u.role === who.value))
const pendingBy = computed(() => ({
  PATIENT: accounts.value.filter((u) => u.role === 'PATIENT' && u.verificationStatus === 'PENDING').length,
  DOCTOR: accounts.value.filter((u) => u.role === 'DOCTOR' && u.verificationStatus === 'PENDING').length,
}))
function switchQueue(role) {
  who.value = role
  selected.value = null
}
const loading = ref(true)
const listError = ref('')
const filter = ref('PENDING')
const search = ref('')

const counts = computed(() => {
  const c = { PENDING: 0, VERIFIED: 0, REJECTED: 0, ALL: patients.value.length }
  patients.value.forEach((p) => (c[p.verificationStatus] = (c[p.verificationStatus] ?? 0) + 1))
  return c
})

const shown = computed(() => {
  const q = search.value.trim().toLowerCase()
  return patients.value.filter(
    (p) =>
      (filter.value === 'ALL' || p.verificationStatus === filter.value) &&
      (!q || p.fullName.toLowerCase().includes(q) || p.email.includes(q)),
  )
})

async function load() {
  loading.value = true
  listError.value = ''
  try {
    accounts.value = (await listUsers()).filter((u) => u.role === 'PATIENT' || u.role === 'DOCTOR')
  } catch (err) {
    listError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

// ---- Selected patient ----
const selected = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const decision = reactive({ note: '', error: '', saving: false, message: '', ok: true })
const reset = reactive({ saving: false, password: '', error: '' })

async function select(patient) {
  selected.value = { ...patient }
  detailError.value = ''
  Object.assign(decision, { note: '', error: '', message: '' })
  Object.assign(reset, { password: '', error: '' })
  detailLoading.value = true
  try {
    selected.value = await getUserDetail(patient.id)
  } catch (err) {
    detailError.value = errorMessage(err)
  } finally {
    detailLoading.value = false
  }
}

function age(dob) {
  if (!dob) return ''
  const birth = new Date(`${dob}T00:00`)
  const now = new Date()
  let years = now.getFullYear() - birth.getFullYear()
  if (now < new Date(now.getFullYear(), birth.getMonth(), birth.getDate())) years--
  return `${years} years`
}
const fmtDate = (iso) => (iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—')
const fmtDateTime = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : 'Never')

const details = computed(() => {
  const d = selected.value
  if (!d || detailLoading.value) return []
  if (d.role === 'DOCTOR') {
    return [
      { label: 'Phone', value: d.phone, icon: 'phone' },
      { label: 'Specialization', value: d.specialization, icon: 'award' },
      { label: 'Hospital', value: d.hospital, icon: 'building' },
      { label: 'Last login', value: fmtDateTime(d.lastLoginAt), icon: 'shield-check' },
    ]
  }
  return [
    { label: 'Phone', value: d.phone, icon: 'phone' },
    { label: 'Date of birth', value: d.dateOfBirth && `${fmtDate(d.dateOfBirth)} · ${age(d.dateOfBirth)}`, icon: 'calendar' },
    { label: 'Gender', value: GENDER_OPTIONS.find((o) => o.value === d.gender)?.label, icon: 'users' },
    { label: 'Address', value: d.address, icon: 'map-pin' },
    { label: 'Analyses', value: String(d.predictionCount ?? 0), icon: 'microscope' },
    { label: 'Last login', value: fmtDateTime(d.lastLoginAt), icon: 'shield-check' },
  ]
})
const missing = computed(() => details.value.filter((d) => !d.value).map((d) => d.label.toLowerCase()))

async function decide(status) {
  decision.error = ''
  decision.message = ''
  if (status === 'REJECTED' && !decision.note.trim()) {
    decision.error = `Tell the ${noun.value} what needs to be corrected.`
    return
  }
  decision.saving = true
  try {
    selected.value = await setVerification(selected.value.id, status, decision.note.trim())
    const row = accounts.value.find((p) => p.id === selected.value.id)
    if (row) row.verificationStatus = selected.value.verificationStatus
    decision.note = ''
    decision.ok = true
    decision.message = {
      VERIFIED: who.value === 'DOCTOR' ? 'Doctor verified. Patients can now share reports with them.' : 'Patient verified.',
      REJECTED: `Sent back to the ${noun.value} for correction.`,
      PENDING: 'Moved back to pending.',
    }[status]
    emit('counts-changed')
  } catch (err) {
    decision.ok = false
    decision.error = fieldErrors(err).note ?? ''
    decision.message = decision.error ? '' : errorMessage(err)
  } finally {
    decision.saving = false
  }
}

async function doReset() {
  if (!confirm(`Reset the password for ${selected.value.email}? Their current password stops working and they are signed out everywhere.`)) return
  reset.saving = true
  reset.error = ''
  try {
    const result = await resetPassword(selected.value.id)
    reset.password = result.temporaryPassword
    selected.value = { ...selected.value, mustChangePassword: true }
    emit('counts-changed')
  } catch (err) {
    reset.error = errorMessage(err)
  } finally {
    reset.saving = false
  }
}

const initials = (name) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('')

onMounted(load)
</script>

<template>
  <div class="verify-layout">
    <!-- Queue -->
    <section class="card queue" aria-labelledby="queue-title">
      <header class="queue-head">
        <h3 id="queue-title">{{ who === 'DOCTOR' ? 'Doctors' : 'Patients' }}</h3>
        <input v-model="search" type="search" placeholder="Search name or email" :aria-label="`Search ${noun}s`" />
      </header>
      <div class="queue-tabs" role="tablist" aria-label="Whose details to check">
        <button v-for="r in ['PATIENT', 'DOCTOR']" :key="r" type="button" role="tab" class="queue-tab"
          :class="{ on: who === r }" :aria-selected="who === r" @click="switchQueue(r)">
          {{ r === 'DOCTOR' ? 'Doctors' : 'Patients' }}
          <span v-if="pendingBy[r]" class="n">{{ pendingBy[r] }}</span>
        </button>
      </div>
      <div class="chips" role="group" aria-label="Filter by verification">
        <button v-for="key in ['PENDING', 'VERIFIED', 'REJECTED', 'ALL']" :key="key" type="button" class="chip"
          :class="{ on: filter === key }" :aria-pressed="filter === key" @click="filter = key">
          {{ key === 'ALL' ? 'All' : STATUS[key].label }} <span class="n">{{ counts[key] ?? 0 }}</span>
        </button>
      </div>

      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="listError" class="error">{{ listError }}</p>
      <p v-else-if="!shown.length" class="empty">
        <AppIcon name="check-circle" :size="28" />
        {{ filter === 'PENDING' && !search ? `No ${noun}s are waiting. All caught up!` : `No ${noun}s match.` }}
      </p>
      <ul v-else class="people">
        <li v-for="p in shown" :key="p.id">
          <button type="button" class="person" :class="{ on: selected?.id === p.id }" @click="select(p)">
            <span class="mini-avatar" aria-hidden="true">{{ initials(p.fullName) }}</span>
            <span class="who">
              <strong>{{ p.fullName }}</strong>
              <span>{{ p.email }}</span>
            </span>
            <span class="status" :data-status="p.verificationStatus">{{ STATUS[p.verificationStatus]?.label }}</span>
          </button>
        </li>
      </ul>
    </section>

    <!-- Detail -->
    <section class="card detail" aria-live="polite">
      <div v-if="!selected" class="placeholder">
        <span class="ph-icon"><AppIcon name="users" :size="30" /></span>
        <h3>Select a {{ noun }}</h3>
        <p class="muted">Check their details, then verify them or ask for a correction.</p>
      </div>

      <template v-else>
        <header class="detail-head">
          <span class="big-avatar" aria-hidden="true">{{ initials(selected.fullName) }}</span>
          <div class="min0">
            <h3>{{ selected.fullName }}</h3>
            <p class="muted">{{ selected.email }} · joined {{ fmtDate(selected.createdAt) }}</p>
            <div class="tags">
              <span class="status" :data-status="selected.verificationStatus">
                <AppIcon :name="STATUS[selected.verificationStatus]?.icon ?? 'refresh'" :size="13" /> {{ STATUS[selected.verificationStatus]?.label }}
              </span>
              <span v-if="selected.disabled" class="tag off">Account disabled</span>
              <span v-if="selected.mustChangePassword" class="tag warn">Temporary password</span>
            </div>
          </div>
        </header>

        <p v-if="detailLoading" class="muted">Loading details…</p>
        <p v-else-if="detailError" class="error">{{ detailError }}</p>
        <template v-else>
          <dl class="facts">
            <div v-for="d in details" :key="d.label" class="fact" :class="{ empty: !d.value }">
              <dt><AppIcon :name="d.icon" :size="15" /> {{ d.label }}</dt>
              <dd>{{ d.value || 'Not provided' }}</dd>
            </div>
          </dl>
          <p v-if="missing.length" class="missing">
            <AppIcon name="alert-circle" :size="16" /> Missing: {{ missing.join(', ') }}
          </p>

          <p v-if="selected.verifiedAt" class="history">
            {{ STATUS[selected.verificationStatus]?.label }} by {{ selected.verifiedBy }} on {{ fmtDateTime(selected.verifiedAt) }}
            <template v-if="selected.verificationNote"> — “{{ selected.verificationNote }}”</template>
          </p>

          <div class="decide">
            <label for="verify-note">Note to the {{ noun }} <span class="opt">needed when rejecting</span></label>
            <textarea id="verify-note" v-model="decision.note" rows="2" maxlength="300"
              :placeholder="who === 'DOCTOR' ? 'e.g. Please add your BMDC registration certificate to your doctor profile.' : 'e.g. Please add your full date of birth.'" :aria-invalid="!!decision.error"></textarea>
            <p class="field-error" aria-live="polite">{{ decision.error }}</p>
            <div class="decide-actions">
              <button type="button" class="btn btn-sm approve" :disabled="decision.saving || selected.verificationStatus === 'VERIFIED'" @click="decide('VERIFIED')">
                <AppIcon name="check-circle" :size="16" /> Verify
              </button>
              <button type="button" class="btn btn-sm reject" :disabled="decision.saving" @click="decide('REJECTED')">
                <AppIcon name="alert-circle" :size="16" /> Ask for correction
              </button>
              <button v-if="selected.verificationStatus !== 'PENDING'" type="button" class="link" :disabled="decision.saving" @click="decide('PENDING')">
                Move back to pending
              </button>
            </div>
            <div v-if="decision.message" class="alert" :class="decision.ok ? 'success' : 'error'" role="status">{{ decision.message }}</div>
          </div>

          <div class="password-help">
            <div>
              <h4>Password help</h4>
              <p class="muted small">Locked out? Give the {{ noun }} a temporary password.</p>
            </div>
            <button type="button" class="btn btn-outline btn-sm" :disabled="reset.saving" @click="doReset">
              {{ reset.saving ? 'Resetting…' : 'Reset password' }}
            </button>
          </div>
          <p v-if="reset.error" class="error">{{ reset.error }}</p>
          <TempPasswordNotice v-if="reset.password" :email="selected.email" :password="reset.password" @close="reset.password = ''" />
        </template>
      </template>
    </section>
  </div>
</template>

<style scoped>
.queue-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 0.3rem; padding: 0.25rem; margin-bottom: 0.7rem; border-radius: 12px; background: #f6f1f8; }
.queue-tab {
  display: inline-flex; align-items: center; justify-content: center; gap: 0.4rem; padding: 0.45rem; border: 0; border-radius: 9px;
  background: transparent; color: var(--muted); font: 600 0.88rem 'Poppins', sans-serif; cursor: pointer;
}
.queue-tab.on { background: #fff; color: var(--pink-600); box-shadow: 0 2px 8px rgba(42, 22, 80, 0.08); }
.queue-tab .n { padding: 0 0.45rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-size: 0.72rem; }
.queue-tab:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.verify-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr); gap: 1rem; align-items: start; }
h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.small { font-size: 0.85rem; margin: 0.1rem 0 0; }
.min0 { min-width: 0; }

.queue-head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.6rem; }
.queue-head input { flex: 1; min-width: 160px; max-width: 240px; padding: 0.45rem 0.7rem; border: 1.5px solid var(--border); border-radius: 10px; font: inherit; font-size: 0.88rem; }
.queue-head input:focus { outline: none; border-color: var(--pink-600); }
.chips { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0.9rem 0; }
.chip { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.35rem 0.75rem; border: 1px solid var(--border); border-radius: 999px; background: #fff; color: var(--muted); font: 600 0.82rem 'Poppins', sans-serif; cursor: pointer; }
.chip .n { padding: 0 0.4rem; border-radius: 999px; background: var(--pink-50); color: var(--pink-600); font-size: 0.72rem; }
.chip.on { background: var(--purple-900); border-color: var(--purple-900); color: #fff; }
.chip.on .n { background: rgba(255, 255, 255, 0.2); color: #fff; }

.people { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.35rem; max-height: 560px; overflow-y: auto; }
.person { display: flex; align-items: center; gap: 0.7rem; width: 100%; padding: 0.6rem 0.7rem; border: 1px solid transparent; border-radius: 14px; background: none; text-align: left; font: inherit; cursor: pointer; transition: background-color 0.15s, border-color 0.15s; }
.person:hover { background: var(--pink-50); }
.person.on { background: var(--pink-50); border-color: var(--pink-300); }
.person:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 1px; }
.mini-avatar, .big-avatar { flex: none; display: grid; place-items: center; border-radius: 50%; background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; font-weight: 700; }
.mini-avatar { width: 38px; height: 38px; font-size: 0.85rem; }
.who { flex: 1; min-width: 0; display: grid; }
.who strong { color: var(--purple-900); font-size: 0.92rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.who span { color: var(--muted); font-size: 0.8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.status { flex: none; display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; background: #fff4e5; color: #9a3412; }
.status[data-status='VERIFIED'] { background: #e8f5e9; color: #1b5e20; }
.status[data-status='REJECTED'] { background: #fdecea; color: #8e1c1c; }
.empty { display: grid; justify-items: center; gap: 0.4rem; padding: 2rem 1rem; color: var(--muted); text-align: center; }
.empty :deep(svg) { color: #22c55e; }

.placeholder { display: grid; justify-items: center; text-align: center; padding: 3rem 1rem; }
.ph-icon { display: grid; place-items: center; width: 64px; height: 64px; margin-bottom: 0.75rem; border-radius: 20px; background: var(--pink-50); color: var(--pink-600); }
.placeholder p { margin: 0.3rem 0 0; }

.detail-head { display: flex; gap: 1rem; align-items: center; padding-bottom: 1rem; border-bottom: 1px solid var(--border); }
.big-avatar { width: 60px; height: 60px; font-size: 1.3rem; box-shadow: 0 8px 18px rgba(216, 27, 106, 0.25); }
.detail-head h3 { font-size: 1.25rem; overflow-wrap: anywhere; }
.detail-head .muted { margin: 0.1rem 0 0.4rem; font-size: 0.86rem; overflow-wrap: anywhere; }
.tags { display: flex; flex-wrap: wrap; gap: 0.35rem; }
.tag { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; }
.tag.off { background: #fdecea; color: #8e1c1c; }
.tag.warn { background: #ede9fe; color: #5b21b6; }

.facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.6rem; margin: 1rem 0 0; }
.fact { padding: 0.65rem 0.8rem; border-radius: 12px; background: #fbf8fd; border: 1px solid var(--border); }
.fact dt { display: flex; align-items: center; gap: 0.35rem; font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
.fact dd { margin: 0.2rem 0 0; color: var(--purple-900); font-weight: 500; overflow-wrap: anywhere; }
.fact.empty dd { color: #b45309; font-style: italic; font-weight: 400; }
.missing { display: flex; align-items: center; gap: 0.4rem; margin: 0.75rem 0 0; font-size: 0.85rem; color: #b45309; }
.history { margin: 0.75rem 0 0; font-size: 0.84rem; color: var(--muted); overflow-wrap: anywhere; }

.decide { margin-top: 1.1rem; padding-top: 1rem; border-top: 1px solid var(--border); display: grid; gap: 0.35rem; }
.decide label { font-weight: 600; color: var(--purple-900); font-size: 0.92rem; }
.opt { font-weight: 400; font-size: 0.78rem; color: var(--muted); }
.decide textarea { width: 100%; padding: 0.7rem 0.9rem; border: 1.5px solid var(--border); border-radius: 12px; resize: vertical; font: 400 0.95rem 'Poppins', sans-serif; }
.decide textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.decide textarea[aria-invalid='true'] { border-color: var(--high); }
.decide-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
.decide-actions .btn { display: inline-flex; align-items: center; gap: 0.35rem; }
.approve { background: #16a34a; border-color: #16a34a; color: #fff; }
.reject { background: #fff; border-color: #dc2626; color: #dc2626; }
.link { padding: 0; border: 0; background: none; color: var(--muted); font: 600 0.85rem 'Poppins', sans-serif; text-decoration: underline; cursor: pointer; }

.password-help { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-top: 1.1rem; padding: 0.9rem 1rem; border-radius: 14px; background: #f6f0ff; border: 1px solid #e4d8fb; }
.password-help h4 { margin: 0; color: var(--purple-900); font-size: 0.95rem; }

@media (max-width: 1000px) { .verify-layout { grid-template-columns: 1fr; } .people { max-height: 340px; } }
@media (max-width: 520px) { .facts { grid-template-columns: 1fr; } .password-help { flex-direction: column; align-items: stretch; } }
</style>
