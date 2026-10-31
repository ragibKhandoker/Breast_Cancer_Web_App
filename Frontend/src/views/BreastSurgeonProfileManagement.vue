<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import SurgeonAvatar from '../components/appointments/SurgeonAvatar.vue'
import BreastSurgeonProfileForm from '../components/appointments/BreastSurgeonProfileForm.vue'
import {
  getMyProfile, createProfile, updateProfile, uploadPhoto, removePhoto, uploadDocument, removeDocument, documentUrl,
} from '../services/surgeonService'
import { errorMessage, fieldErrors } from '../services/http'
import { authState } from '../auth/authStore'
import { APP_ROUTES } from '../config/appRoutes'
import { dayLabels, formatFee, formatTime, formatDate, formatDateTime } from '../appointments/format'

// The signed-in doctor's own Breast Surgeon profile: create it, see where the review stands, edit it, and
// manage the picture and verification documents. Only doctor accounts reach this page.
const router = useRouter()
const profile = ref(null)
const loading = ref(true)
const loadError = ref('')
const editing = ref(false)
const saving = ref(false)
const serverErrors = ref({})
const formError = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    profile.value = await getMyProfile()
  } catch (err) {
    if (err.response?.status === 404) profile.value = null
    else loadError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

// A self-registered doctor waits for verification (approving this profile also verifies the account).
const accountPending = computed(() => authState.user?.verificationStatus === 'PENDING' && profile.value?.status !== 'APPROVED')
const accountRejected = computed(() => authState.user?.verificationStatus === 'REJECTED')

const defaults = computed(() =>({ fullName: authState.user?.fullName ?? '', email: authState.user?.email ?? '' }))

async function create({ profile: body, photo, documents }) {
  saving.value = true
  serverErrors.value = {}
  formError.value = ''
  try {
    let saved = await createProfile(body)
    // The profile exists now; upload the files one by one and report any that fail.
    const failures = []
    if (photo) {
      try {
        saved = await uploadPhoto(photo)
      } catch (err) {
        failures.push(`Picture: ${fieldErrors(err).photo || errorMessage(err)}`)
      }
    }
    for (const doc of documents) {
      try {
        saved = await uploadDocument(doc)
      } catch (err) {
        failures.push(`${doc.name}: ${fieldErrors(err).document || errorMessage(err)}`)
      }
    }
    profile.value = saved
    notice.value = failures.length
      ? `Profile created, but some files were not uploaded — ${failures.join('; ')}. Add them below.`
      : 'Profile submitted. An administrator will check your credentials; you will appear in Find a Breast Surgeon once approved.'
  } catch (err) {
    serverErrors.value = fieldErrors(err)
    formError.value = errorMessage(err)
  } finally {
    saving.value = false
  }
}

async function save({ profile: body, photo, removePhoto: dropPhoto }) {
  saving.value = true
  serverErrors.value = {}
  formError.value = ''
  const wasApproved = profile.value.status === 'APPROVED'
  try {
    profile.value = await updateProfile(body)
    editing.value = false
    // The details are saved; a picture change that fails is reported without losing them.
    let photoProblem = ''
    try {
      if (photo) profile.value = await uploadPhoto(photo)
      else if (dropPhoto) profile.value = await removePhoto()
    } catch (err) {
      photoProblem = ` The picture was not changed: ${fieldErrors(err).photo || errorMessage(err)}`
    }
    notice.value = (wasApproved && profile.value.status === 'PENDING'
      ? 'Saved. You changed your credentials, so your profile is back with an administrator for a quick re-check.'
      : 'Your profile has been saved.') + photoProblem
  } catch (err) {
    serverErrors.value = fieldErrors(err)
    formError.value = errorMessage(err)
  } finally {
    saving.value = false
  }
}

// ---- Picture and documents ----
const fileBusy = ref('')
const fileError = ref('')
async function changePhoto(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  fileBusy.value = 'photo'
  fileError.value = ''
  try {
    profile.value = await uploadPhoto(file)
    notice.value = 'Profile picture updated.'
  } catch (err) {
    fileError.value = fieldErrors(err).photo || errorMessage(err)
  } finally {
    fileBusy.value = ''
  }
}
async function addDocument(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  fileBusy.value = 'document'
  fileError.value = ''
  try {
    profile.value = await uploadDocument(file)
    notice.value = 'Document added. Your profile will be re-checked by an administrator.'
  } catch (err) {
    fileError.value = fieldErrors(err).document || errorMessage(err)
  } finally {
    fileBusy.value = ''
  }
}
async function deleteDocument(doc) {
  if (!confirm(`Remove ${doc.filename}? Your profile will be sent back for review.`)) return
  fileBusy.value = doc.fileId
  fileError.value = ''
  try {
    profile.value = await removeDocument(doc.fileId)
  } catch (err) {
    fileError.value = errorMessage(err)
  } finally {
    fileBusy.value = ''
  }
}

const STATUS_TEXT = {
  PENDING: { title: 'Waiting for verification', text: 'An administrator is checking your registration and documents. Patients cannot see or book you yet.', icon: 'refresh' },
  APPROVED: { title: 'Approved — you are live', text: 'Patients can find you in Find a Breast Surgeon and book your free slots.', icon: 'check-circle' },
  REJECTED: { title: 'Changes needed', text: 'An administrator could not approve your profile yet. Fix the points below and save to send it again.', icon: 'alert-circle' },
}
const status = computed(() => STATUS_TEXT[profile.value?.status])
const GENDER_LABEL = { FEMALE: 'Female', MALE: 'Male', OTHER: 'Other' }

onMounted(load)
</script>

<template>
  <section class="container page surgeon-profile-page">
    <div class="head">
      <div>
        <h1>Doctor Profile</h1>
        <nav class="crumbs" aria-label="Breadcrumb">
          <RouterLink to="/">Home</RouterLink><span aria-hidden="true">»</span>
          <RouterLink :to="APP_ROUTES.doctorDashboard">Doctor</RouterLink><span aria-hidden="true">»</span>
          <span aria-current="page">{{ profile ? (editing ? 'Edit Profile' : 'My Profile') : 'Add Profile' }}</span>
        </nav>
        <p class="muted">{{ profile ? 'What patients see, and where your verification stands.' : 'Set up your doctor profile. It goes live after an administrator verifies your credentials.' }}</p>
      </div>
      <div class="head-links">
        <RouterLink v-if="profile" :to="APP_ROUTES.surgeonDashboard" class="btn btn-outline btn-sm">Surgeon dashboard</RouterLink>
        <RouterLink :to="APP_ROUTES.accountSettings" class="btn btn-outline btn-sm">Email &amp; password</RouterLink>
        <RouterLink :to="APP_ROUTES.doctorDashboard" class="back-link">← Back to Dashboard</RouterLink>
      </div>
    </div>

    <p v-if="accountPending" class="alert info" role="status">
      Your doctor account is waiting for an administrator's verification. Patients cannot share reports with you or
      book you yet. Completing this profile with your registration documents helps us verify you faster.
    </p>
    <p v-else-if="accountRejected" class="alert error" role="status">
      An administrator could not verify your doctor account yet. See their note in
      <RouterLink :to="APP_ROUTES.accountSettings">Email &amp; password</RouterLink> and correct your details.
    </p>
    <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="loadError" class="alert error" role="alert">{{ loadError }}</p>

    <!-- No profile yet -->
    <template v-else-if="!profile">
      <p v-if="formError" class="alert error" role="alert">{{ formError }}</p>
      <BreastSurgeonProfileForm
        with-files
        :defaults="defaults"
        :submitting="saving"
        :server-errors="serverErrors"
        submit-label="Submit for verification"
        @submit="create"
        @cancel="router.push(APP_ROUTES.doctorDashboard)"
      />
    </template>

    <!-- Editing -->
    <template v-else-if="editing">
      <p v-if="formError" class="alert error" role="alert">{{ formError }}</p>
      <p class="alert info">Changing your name, designation, qualifications, experience or registration number sends the profile back for verification.</p>
      <BreastSurgeonProfileForm
        :initial="profile"
        :submitting="saving"
        :server-errors="serverErrors"
        submit-label="Save changes"
        @submit="save"
        @cancel="editing = false; serverErrors = {}; formError = ''"
      />
    </template>

    <!-- Viewing -->
    <template v-else>
      <div class="card status-card" :class="profile.status.toLowerCase()">
        <span class="status-icon"><AppIcon :name="status.icon" :size="24" /></span>
        <div>
          <h2>{{ status.title }}</h2>
          <p>{{ status.text }}</p>
          <p v-if="profile.status === 'REJECTED' && profile.reviewNote" class="review-note"><strong>Administrator’s note:</strong> {{ profile.reviewNote }}</p>
          <p v-if="profile.reviewedAt" class="meta">Reviewed {{ formatDateTime(profile.reviewedAt) }}</p>
        </div>
      </div>

      <div class="grid-view">
        <div class="card summary">
          <div class="summary-top">
            <SurgeonAvatar :photo-url="profile.photoUrl" :name="profile.fullName" :size="96" />
            <div>
              <h2>{{ profile.fullName }}</h2>
              <p class="pink">{{ profile.designation }}</p>
              <p class="muted">{{ profile.qualifications }}</p>
              <label class="btn btn-outline btn-sm file-btn" :class="{ busy: fileBusy === 'photo' }">
                <AppIcon name="image" :size="15" /> {{ fileBusy === 'photo' ? 'Uploading…' : 'Update picture' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="!!fileBusy" @change="changePhoto" />
              </label>
            </div>
          </div>
          <dl>
            <div><dt>Specialty</dt><dd>{{ profile.specialty }}</dd></div>
            <div><dt>Experience</dt><dd>{{ profile.experienceYears }} years in breast surgery</dd></div>
            <div><dt>Registration no.</dt><dd>{{ profile.registrationNumber }}</dd></div>
            <div><dt>Consultation fee</dt><dd>{{ formatFee(profile.consultationFee) }}</dd></div>
            <div><dt>Hospital / chamber</dt><dd>{{ profile.hospitalName }}</dd></div>
            <div><dt>Address</dt><dd>{{ profile.hospitalAddress }}</dd></div>
            <div><dt>Email</dt><dd>{{ profile.email }}</dd></div>
            <div><dt>Phone</dt><dd>{{ profile.phone }}</dd></div>
            <div><dt>Gender</dt><dd>{{ GENDER_LABEL[profile.gender] ?? 'Not given' }}</dd></div>
            <div><dt>Date of birth</dt><dd>{{ profile.dateOfBirth ? formatDate(profile.dateOfBirth, { weekday: false }) : 'Not given' }}</dd></div>
            <div class="wide"><dt>Known languages</dt><dd>{{ profile.languages?.length ? profile.languages.join(', ') : 'Not given' }}</dd></div>
            <div><dt>Days</dt><dd>{{ dayLabels(profile.availableDays) }}</dd></div>
            <div><dt>Time slots</dt><dd>{{ profile.timeSlots.map(formatTime).join(', ') }}</dd></div>
            <div class="wide"><dt>Biography</dt><dd class="bio">{{ profile.biography }}</dd></div>
          </dl>
          <div class="summary-actions">
            <button type="button" class="btn btn-primary btn-sm" @click="editing = true; notice = ''"><AppIcon name="file-text" :size="15" /> Edit profile</button>
            <RouterLink v-if="profile.status === 'APPROVED'" :to="`/breast-surgeons/${profile.id}`" class="btn btn-outline btn-sm">See public profile</RouterLink>
          </div>
        </div>

        <div class="card docs">
          <h2>Verification documents</h2>
          <p class="muted small">Only you and the site administrators can open these.</p>
          <ul v-if="profile.documents.length" class="doc-list">
            <li v-for="d in profile.documents" :key="d.fileId">
              <AppIcon name="file-text" :size="18" />
              <a :href="documentUrl(d.fileId)" class="doc-name">{{ d.filename }}</a>
              <span class="muted">{{ Math.max(1, Math.round(d.size / 1024)) }} KB</span>
              <button type="button" class="remove" :disabled="!!fileBusy" :aria-label="`Remove ${d.filename}`" @click="deleteDocument(d)"><AppIcon name="trash" :size="15" /></button>
            </li>
          </ul>
          <p v-else class="alert error">No documents yet. An administrator cannot approve your profile without one.</p>
          <label v-if="profile.documents.length < 5" class="btn btn-outline btn-sm file-btn">
            <AppIcon name="upload" :size="15" /> {{ fileBusy === 'document' ? 'Uploading…' : 'Add document' }}
            <input type="file" accept="application/pdf,image/png,image/jpeg" :disabled="!!fileBusy" @change="addDocument" />
          </label>
          <p class="field-error">{{ fileError }}</p>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 1rem; margin-bottom: 1.2rem; }
.head h1 { margin: 0; color: var(--purple-900); font-size: 1.7rem; }
.head p { margin: 0.35rem 0 0; max-width: 640px; }
.crumbs { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; margin-top: 0.25rem; font-size: 0.88rem; color: var(--muted); }
.crumbs a { color: var(--muted); }
.crumbs a:hover { color: var(--pink-600); }
.crumbs [aria-current] { color: var(--purple-900); font-weight: 500; }
.head-links { display: flex; flex-wrap: wrap; align-items: center; gap: 0.9rem; }
.back-link { color: var(--purple-900); font-weight: 600; font-size: 0.92rem; }
.back-link:hover { color: var(--pink-600); }
.status-card { display: flex; gap: 1rem; align-items: flex-start; border-radius: 20px; border-left: 6px solid; margin-bottom: 1.2rem; }
.status-card h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.status-card p { margin: 0.25rem 0 0; }
.status-icon { flex: none; display: grid; place-items: center; width: 46px; height: 46px; border-radius: 14px; }
.status-card.pending { border-left-color: #f0b44c; } .pending .status-icon { background: #fff7e6; color: #8a5300; }
.status-card.approved { border-left-color: #43a047; } .approved .status-icon { background: #e8f5e9; color: #2e7d32; }
.status-card.rejected { border-left-color: var(--high); } .rejected .status-icon { background: #fdecea; color: var(--high); }
.review-note { padding: 0.6rem 0.8rem; border-radius: 10px; background: #fdecea; color: #8e1c1c; }
.grid-view { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 1.2rem; align-items: start; }
.summary, .docs { border-radius: 22px; padding: 1.5rem; }
.summary-top { display: flex; gap: 1.2rem; align-items: center; margin-bottom: 1.2rem; }
.summary-top h2 { margin: 0; color: var(--purple-900); }
.summary-top p { margin: 0.15rem 0; }
.pink { color: var(--pink-600); font-weight: 600; }
.file-btn { position: relative; margin-top: 0.5rem; cursor: pointer; }
.file-btn input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.summary dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.8rem 1.2rem; margin: 0; padding-top: 1rem; border-top: 1px dashed var(--border); }
.summary .wide { grid-column: 1 / -1; }
.summary dt { font-size: 0.74rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.summary dd { margin: 0.15rem 0 0; overflow-wrap: anywhere; }
.bio { white-space: pre-line; line-height: 1.6; }
.summary-actions { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-top: 1.2rem; }
.docs h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.small { margin: 0.2rem 0 0.9rem; font-size: 0.85rem; }
.doc-list { list-style: none; margin: 0 0 0.9rem; padding: 0; display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.45rem; }
.doc-list li { min-width: 0; display: flex; align-items: center; gap: 0.5rem; padding: 0.55rem 0.7rem; border-radius: 12px; background: #f8f6fa; font-size: 0.88rem; }
.doc-list li > :deep(svg) { flex: none; color: var(--pink-600); }
.doc-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--purple-900); font-weight: 500; text-decoration: underline; }
.doc-list .muted, .doc-list .remove { flex: none; white-space: nowrap; }
.remove { display: grid; place-items: center; width: 30px; height: 30px; border: 0; border-radius: 50%; background: transparent; color: var(--muted); cursor: pointer; }
.remove:hover { background: #fdecea; color: var(--high); }
@media (max-width: 900px) { .grid-view { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .summary dl { grid-template-columns: 1fr; } .summary-top { flex-direction: column; text-align: center; } }
</style>
