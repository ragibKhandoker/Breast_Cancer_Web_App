<script setup>
import { reactive, ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import PasswordField from '../components/auth/PasswordField.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { onSessionEnd, updateCurrentUser } from '../auth/authStore'
import { ROLE_LABELS } from '../auth/roles'
import {
  GENDER_OPTIONS,
  localIsoDate,
  validateConfirm,
  validateDateOfBirth,
  validateEmail,
  validateFullName,
  validatePassword,
  validatePhone,
} from '../auth/validation'
import { getProfile, updateProfile, changeEmail, changePassword } from '../services/profileService'
import { errorMessage, fieldErrors } from '../services/http'

const profile = ref(null)
const loading = ref(true)
const loadError = ref('')

const isDoctor = computed(() => profile.value?.role === 'DOCTOR')
const initials = computed(() =>
  (profile.value?.fullName ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join(''),
)
const memberSince = computed(() =>
  profile.value ? new Date(profile.value.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'long' }) : '',
)

// The optional details shown on the summary card; a missing one jumps to its input when clicked.
const facts = computed(() => {
  if (!profile.value) return []
  const p = profile.value
  const list = [
    { key: 'phone', icon: 'phone', label: 'Phone', value: p.phone, input: 'profile-phone' },
    {
      key: 'dateOfBirth', icon: 'calendar', label: 'Date of birth', input: 'profile-dob',
      value: p.dateOfBirth && new Date(`${p.dateOfBirth}T00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }),
    },
    { key: 'gender', icon: 'users', label: 'Gender', value: GENDER_OPTIONS.find((o) => o.value === p.gender)?.label, input: 'profile-gender' },
    { key: 'address', icon: 'map-pin', label: 'Address', value: p.address, input: 'profile-address' },
  ]
  if (isDoctor.value) {
    list.push(
      { key: 'specialization', icon: 'microscope', label: 'Specialization', value: p.specialization, input: 'profile-specialization' },
      { key: 'hospital', icon: 'heart', label: 'Hospital', value: p.hospital, input: 'profile-hospital' },
    )
  }
  return list
})

function focusField(id) {
  const el = document.getElementById(id)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el?.focus({ preventScroll: true })
}

// An administrator's check of a patient's or doctor's details; administrators have none.
const VERIFICATION = {
  VERIFIED: { label: 'Verified', icon: 'check-circle' },
  PENDING: { label: 'Awaiting verification', icon: 'refresh' },
  REJECTED: { label: 'Needs correction', icon: 'alert-circle' },
}
const verification = computed(() => VERIFICATION[profile.value?.verificationStatus] ?? null)

// Circumference of the completion ring (r = 42).
const RING = 2 * Math.PI * 42

// How much of the optional profile is filled in, shown as a progress ring.
const completion = computed(() => {
  if (!profile.value) return 0
  const keys = ['phone', 'dateOfBirth', 'gender', 'address', ...(isDoctor.value ? ['specialization', 'hospital'] : [])]
  return Math.round((keys.filter((key) => profile.value[key]).length / keys.length) * 100)
})

/** Applies a profile returned by the server to the page and to the signed-in user shown in the header. */
function applyProfile(data) {
  profile.value = data
  updateCurrentUser(data)
  resetDetails()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    applyProfile(await getProfile())
    // After an administrator's password reset, take the user straight to choosing their own.
    if (profile.value.mustChangePassword || location.hash === '#change-password') {
      nextTick(() => focusField('profile-current-password'))
    }
  } catch (err) {
    // A 404 here means the backend is running an older build without the profile API.
    loadError.value = err.response?.status === 404
      ? 'The profile service is not available. Restart the backend so it runs the latest version, then try again.'
      : errorMessage(err)
  } finally {
    loading.value = false
  }
}

/** Puts a server's per-field messages under the matching inputs; returns true if any matched. */
function showFieldErrors(err, errors, rename = {}) {
  const matched = Object.entries(fieldErrors(err))
    .map(([key, message]) => [rename[key] ?? key, message])
    .filter(([key]) => key in errors)
  matched.forEach(([key, message]) => (errors[key] = message))
  return matched.length > 0
}

// ---- Personal details ----
const DETAIL_KEYS = ['fullName', 'phone', 'dateOfBirth', 'gender', 'address', 'specialization', 'hospital']
const details = reactive(Object.fromEntries(DETAIL_KEYS.map((key) => [key, ''])))
const detailErrors = reactive(Object.fromEntries(DETAIL_KEYS.map((key) => [key, ''])))
const detailsState = reactive({ saving: false, message: '', ok: true })

function resetDetails() {
  for (const key of DETAIL_KEYS) {
    details[key] = profile.value?.[key] ?? ''
    detailErrors[key] = ''
  }
}

const detailsChanged = computed(
  () => !!profile.value && DETAIL_KEYS.some((key) => details[key].trim() !== (profile.value[key] ?? '')),
)

function validateDetails() {
  detailErrors.fullName = validateFullName(details.fullName)
  detailErrors.phone = validatePhone(details.phone)
  detailErrors.dateOfBirth = validateDateOfBirth(details.dateOfBirth)
  detailErrors.address = details.address.trim().length > 300 ? 'Address must be 300 characters or fewer.' : ''
  return !detailErrors.fullName && !detailErrors.phone && !detailErrors.dateOfBirth && !detailErrors.address
}

async function saveDetails() {
  detailsState.message = ''
  if (!validateDetails()) return
  detailsState.saving = true
  try {
    const payload = Object.fromEntries(DETAIL_KEYS.map((key) => [key, details[key].trim()]))
    applyProfile(await updateProfile(payload))
    Object.assign(detailsState, { ok: true, message: 'Your profile has been saved.' })
  } catch (err) {
    const matched = showFieldErrors(err, detailErrors)
    Object.assign(detailsState, { ok: false, message: matched ? 'Please correct the highlighted fields.' : errorMessage(err) })
  } finally {
    detailsState.saving = false
  }
}

// ---- Email ----
const emailForm = reactive({ email: '', currentPassword: '' })
const emailErrors = reactive({ email: '', currentPassword: '' })
const emailState = reactive({ saving: false, message: '', ok: true })

async function saveEmail() {
  emailState.message = ''
  emailErrors.email = validateEmail(emailForm.email)
  emailErrors.currentPassword = emailForm.currentPassword ? '' : 'Enter your current password.'
  if (!emailErrors.email && emailForm.email.trim().toLowerCase() === profile.value.email) {
    emailErrors.email = 'This is already your email.'
  }
  if (emailErrors.email || emailErrors.currentPassword) return
  emailState.saving = true
  try {
    applyProfile(await changeEmail({ email: emailForm.email.trim(), currentPassword: emailForm.currentPassword }))
    Object.assign(emailForm, { email: '', currentPassword: '' })
    Object.assign(emailState, { ok: true, message: `Your email is now ${profile.value.email}. Use it next time you log in.` })
  } catch (err) {
    emailForm.currentPassword = ''
    const matched = showFieldErrors(err, emailErrors)
    Object.assign(emailState, { ok: false, message: matched ? '' : errorMessage(err) })
  } finally {
    emailState.saving = false
  }
}

// ---- Password ----
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const passwordErrors = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const passwordState = reactive({ saving: false, message: '', ok: true })

async function savePassword() {
  passwordState.message = ''
  passwordErrors.currentPassword = passwordForm.currentPassword ? '' : 'Enter your current password.'
  passwordErrors.newPassword = validatePassword(passwordForm.newPassword)
  passwordErrors.confirm = validateConfirm(passwordForm.newPassword, passwordForm.confirm)
  if (passwordErrors.currentPassword || passwordErrors.newPassword || passwordErrors.confirm) return
  passwordState.saving = true
  try {
    applyProfile(await changePassword({ currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword }))
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirm: '' })
    Object.assign(passwordState, { ok: true, message: 'Password changed. You have been signed out on your other devices.' })
  } catch (err) {
    const matched = showFieldErrors(err, passwordErrors)
    Object.assign(passwordState, { ok: false, message: matched ? '' : errorMessage(err) })
  } finally {
    passwordState.saving = false
  }
}

const stop = onSessionEnd(() => (profile.value = null))
onBeforeUnmount(stop)
onMounted(load)
</script>

<template>
  <div class="photo-bg photo-bg--profile">
  <section class="container page profile-page">
    <p v-if="loading" class="muted">Loading your profile…</p>
    <div v-else-if="loadError" class="card load-error" role="alert">
      <h1>My profile</h1>
      <p class="error">{{ loadError }}</p>
      <button type="button" class="btn btn-primary btn-sm" @click="load">Try again</button>
    </div>

    <template v-else-if="profile">
      <!-- Summary -->
      <div v-reveal class="card hero">
        <div class="hero-body">
          <div class="avatar-ring">
            <div class="avatar" aria-hidden="true">{{ initials }}</div>
            <span class="online" title="Signed in"></span>
          </div>
          <div class="identity">
            <p class="eyebrow">Welcome back</p>
            <h1>{{ profile.fullName }}</h1>
            <p class="email"><AppIcon name="mail" :size="15" /> {{ profile.email }}</p>
            <div class="badges">
              <span class="badge role" :data-role="profile.role"><AppIcon name="shield-check" :size="14" /> {{ ROLE_LABELS[profile.role] }}</span>
              <span class="badge"><AppIcon name="calendar" :size="14" /> Member since {{ memberSince }}</span>
              <span v-if="verification" class="badge verify" :data-status="profile.verificationStatus">
                <AppIcon :name="verification.icon" :size="14" /> {{ verification.label }}
              </span>
            </div>
          </div>
          <div class="completion" :aria-label="`Profile ${completion}% complete`">
            <div class="ring-wrap">
              <svg class="ring" viewBox="0 0 100 100" aria-hidden="true">
                <circle class="ring-track" cx="50" cy="50" r="42" />
                <circle class="ring-value" cx="50" cy="50" r="42"
                  :stroke-dasharray="RING" :stroke-dashoffset="RING * (1 - completion / 100)" />
              </svg>
              <div class="ring-label"><strong>{{ completion }}%</strong><span>complete</span></div>
            </div>
            <div class="completion-text">
              <span class="completion-title">Profile completeness</span>
              <p>{{ completion === 100 ? 'All set. Thank you!' : 'Add your details below to complete it.' }}</p>
            </div>
          </div>
        </div>

        <ul class="facts">
          <li v-for="fact in facts" :key="fact.key">
            <span class="fact-icon" :class="{ filled: fact.value }"><AppIcon :name="fact.icon" :size="17" /></span>
            <div class="fact-text">
              <span class="fact-label">{{ fact.label }}</span>
              <span v-if="fact.value" class="fact-value" :title="fact.value">{{ fact.value }}</span>
              <button v-else type="button" class="fact-add" @click="focusField(fact.input)">+ Add {{ fact.label.toLowerCase() }}</button>
            </div>
          </li>
        </ul>
      </div>

      <div v-if="profile.mustChangePassword" class="alert error notice" role="alert">
        <AppIcon name="shield-check" :size="20" />
        <span>
          You are signed in with a temporary password from an administrator. Choose a new password of your own below
          to continue: enter the temporary password as your current password. Nobody else, administrators included,
          will know your new password.
        </span>
      </div>
      <div v-if="profile.verificationStatus === 'REJECTED'" class="alert error notice" role="status">
        <AppIcon name="alert-circle" :size="20" />
        <span>
          <strong>Your details need a correction.</strong> {{ profile.verificationNote }}
          Update them below and an administrator will check them again.
        </span>
      </div>
      <div v-else-if="profile.verificationStatus === 'PENDING'" class="alert info notice" role="status">
        <AppIcon name="refresh" :size="20" />
        <span>An administrator will check your details soon. Keeping them complete and correct speeds this up.</span>
      </div>

      <div class="layout" :class="{ solo: profile.mustChangePassword }">
        <!-- Personal details (hidden until a temporary password is replaced) -->
        <form v-if="!profile.mustChangePassword" v-reveal class="card section form-stack" novalidate @submit.prevent="saveDetails">
          <header class="section-head">
            <span class="section-icon"><AppIcon name="users" :size="20" /></span>
            <div>
              <h2>Personal information</h2>
              <p class="muted small">Only you can see these details{{ isDoctor ? ', except your name, specialization and hospital' : '' }}.</p>
            </div>
          </header>

          <div class="form-field">
            <label for="profile-name">Full name</label>
            <input id="profile-name" v-model="details.fullName" autocomplete="name" maxlength="100"
              :aria-invalid="!!detailErrors.fullName" aria-describedby="profile-name-error" @blur="detailErrors.fullName = validateFullName(details.fullName)" />
            <p id="profile-name-error" class="field-error" aria-live="polite">{{ detailErrors.fullName }}</p>
          </div>

          <div class="row">
            <div class="form-field">
              <label for="profile-phone">Phone <span class="optional">optional</span></label>
              <input id="profile-phone" v-model="details.phone" type="tel" autocomplete="tel" maxlength="20" placeholder="+8801712345678"
                :aria-invalid="!!detailErrors.phone" aria-describedby="profile-phone-error" @blur="detailErrors.phone = validatePhone(details.phone)" />
              <p id="profile-phone-error" class="field-error" aria-live="polite">{{ detailErrors.phone }}</p>
            </div>
            <div class="form-field">
              <label for="profile-dob">Date of birth <span class="optional">optional</span></label>
              <input id="profile-dob" v-model="details.dateOfBirth" type="date" autocomplete="bday" min="1900-01-01" :max="localIsoDate()"
                :aria-invalid="!!detailErrors.dateOfBirth" aria-describedby="profile-dob-error" @blur="detailErrors.dateOfBirth = validateDateOfBirth(details.dateOfBirth)" />
              <p id="profile-dob-error" class="field-error" aria-live="polite">{{ detailErrors.dateOfBirth }}</p>
            </div>
          </div>

          <div class="form-field">
            <label for="profile-gender">Gender <span class="optional">optional</span></label>
            <select id="profile-gender" v-model="details.gender">
              <option value="">Not specified</option>
              <option v-for="option in GENDER_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <p class="field-error"></p>
          </div>

          <div class="form-field">
            <label for="profile-address">Address <span class="optional">optional</span></label>
            <textarea id="profile-address" v-model="details.address" rows="2" maxlength="300" autocomplete="street-address"
              :aria-invalid="!!detailErrors.address" aria-describedby="profile-address-error"></textarea>
            <div class="field-foot">
              <p id="profile-address-error" class="field-error" aria-live="polite">{{ detailErrors.address }}</p>
              <span class="counter" :class="{ over: details.address.length > 280 }">{{ details.address.length }}/300</span>
            </div>
          </div>

          <div v-if="isDoctor" class="row">
            <div class="form-field">
              <label for="profile-specialization">Specialization</label>
              <input id="profile-specialization" v-model="details.specialization" maxlength="100" placeholder="e.g. Radiology"
                :aria-invalid="!!detailErrors.specialization" />
              <p class="field-error" aria-live="polite">{{ detailErrors.specialization }}</p>
            </div>
            <div class="form-field">
              <label for="profile-hospital">Hospital / clinic</label>
              <input id="profile-hospital" v-model="details.hospital" maxlength="150" :aria-invalid="!!detailErrors.hospital" />
              <p class="field-error" aria-live="polite">{{ detailErrors.hospital }}</p>
            </div>
          </div>

          <div v-if="detailsState.message" class="alert" :class="detailsState.ok ? 'success' : 'error'" role="status">{{ detailsState.message }}</div>

          <div class="actions">
            <button type="button" class="btn btn-outline btn-sm" :disabled="!detailsChanged || detailsState.saving" @click="resetDetails">
              Discard changes
            </button>
            <button type="submit" class="btn btn-primary btn-sm" :disabled="!detailsChanged || detailsState.saving" :aria-busy="detailsState.saving">
              <span v-if="detailsState.saving" class="spinner" aria-hidden="true"></span>
              {{ detailsState.saving ? 'Saving…' : 'Save changes' }}
            </button>
          </div>
        </form>

        <div class="side">
          <!-- Email -->
          <form v-if="!profile.mustChangePassword" v-reveal="100" class="card section form-stack" novalidate @submit.prevent="saveEmail">
            <header class="section-head">
              <span class="section-icon"><AppIcon name="mail" :size="20" /></span>
              <div>
                <h2>Email address</h2>
                <p class="muted small">Currently <strong>{{ profile.email }}</strong></p>
              </div>
            </header>
            <div class="form-field">
              <label for="profile-new-email">New email</label>
              <input id="profile-new-email" v-model="emailForm.email" type="email" autocomplete="email" maxlength="254"
                :aria-invalid="!!emailErrors.email" aria-describedby="profile-new-email-error" />
              <p id="profile-new-email-error" class="field-error" aria-live="polite">{{ emailErrors.email }}</p>
            </div>
            <PasswordField id="profile-email-password" v-model="emailForm.currentPassword" label="Current password" :error="emailErrors.currentPassword" />
            <div v-if="emailState.message" class="alert" :class="emailState.ok ? 'success' : 'error'" role="status">{{ emailState.message }}</div>
            <button type="submit" class="btn btn-primary btn-sm" :disabled="emailState.saving" :aria-busy="emailState.saving">
              <span v-if="emailState.saving" class="spinner" aria-hidden="true"></span>
              {{ emailState.saving ? 'Updating…' : 'Update email' }}
            </button>
          </form>

          <!-- Password -->
          <form id="change-password" v-reveal="200" class="card section form-stack" :class="{ urgent: profile.mustChangePassword }"
            novalidate @submit.prevent="savePassword">
            <header class="section-head">
              <span class="section-icon"><AppIcon name="shield-check" :size="20" /></span>
              <div>
                <h2>Change password</h2>
                <p class="muted small">Other devices will be signed out.</p>
              </div>
            </header>
            <PasswordField id="profile-current-password" v-model="passwordForm.currentPassword" label="Current password" :error="passwordErrors.currentPassword" />
            <PasswordField id="profile-new-password" v-model="passwordForm.newPassword" label="New password" autocomplete="new-password" :error="passwordErrors.newPassword" />
            <PasswordField id="profile-confirm-password" v-model="passwordForm.confirm" label="Confirm new password" autocomplete="new-password" :error="passwordErrors.confirm" />
            <p class="muted small hint">8–72 characters with an upper-case letter, a lower-case letter and a number.</p>
            <div v-if="passwordState.message" class="alert" :class="passwordState.ok ? 'success' : 'error'" role="status">{{ passwordState.message }}</div>
            <button type="submit" class="btn btn-primary btn-sm" :disabled="passwordState.saving" :aria-busy="passwordState.saving">
              <span v-if="passwordState.saving" class="spinner" aria-hidden="true"></span>
              {{ passwordState.saving ? 'Changing…' : 'Change password' }}
            </button>
          </form>
        </div>
      </div>
    </template>
  </section>
  </div>
</template>

<style scoped>
.layout.solo { grid-template-columns: minmax(0, 560px); justify-content: center; }
.small { font-size: 0.86rem; }

/* Frosted cards over the radiologist photo: the photo shows through softly and the forms stay readable. */
.profile-page .card {
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(14px) saturate(1.15);
  -webkit-backdrop-filter: blur(14px) saturate(1.15);
  border: 1px solid rgba(255, 255, 255, 0.75);
  border-radius: 22px;
  box-shadow: 0 18px 45px rgba(42, 22, 80, 0.14);
}
.load-error { max-width: 560px; margin: 2rem auto; padding: 2rem; text-align: center; }
.load-error h1 { margin-top: 0; }

/* Summary card: the radiologist photo under a deep pink wash carries the identity in white,
   with a strip of quick facts underneath. */
.hero { padding: 0; overflow: hidden; }
.hero-body {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 1.75rem; align-items: center;
  padding: 2rem 2rem 1.85rem; color: #fff;
  background:
    radial-gradient(circle at 8% 120%, rgba(255, 255, 255, 0.22), transparent 45%),
    linear-gradient(110deg, rgba(150, 12, 80, 0.95) 0%, rgba(196, 22, 120, 0.86) 42%, rgba(107, 63, 181, 0.7) 100%),
    url('../assets/images/profile-radiologist-bg.jpg') center 38% / cover no-repeat;
}

.avatar-ring {
  position: relative; padding: 5px; border-radius: 50%;
  background: conic-gradient(from 200deg, #ffd1e6, #fff, #e3d4ff, #ffd1e6);
  box-shadow: 0 14px 34px rgba(42, 10, 50, 0.35);
}
.avatar {
  display: grid; place-items: center; width: 112px; height: 112px; border-radius: 50%;
  background: linear-gradient(135deg, #fff 0%, var(--pink-50) 55%, #efe7ff 100%); color: var(--pink-600);
  font-size: 2.4rem; font-weight: 700; letter-spacing: 0.02em; border: 4px solid rgba(255, 255, 255, 0.9);
}
.online {
  position: absolute; right: 10px; bottom: 10px; width: 18px; height: 18px; border-radius: 50%;
  background: #22c55e; border: 3px solid #fff; box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.25);
}

.eyebrow {
  margin: 0; font-size: 0.75rem; font-weight: 600; letter-spacing: 0.16em; text-transform: uppercase;
  color: rgba(255, 255, 255, 0.75);
}
.identity h1 {
  margin: 0.2rem 0 0.2rem; font-size: 1.95rem; line-height: 1.2; color: #fff;
  text-shadow: 0 2px 14px rgba(60, 0, 40, 0.35); overflow-wrap: anywhere;
}
.email { display: flex; align-items: center; gap: 0.4rem; margin: 0 0 0.9rem; color: rgba(255, 255, 255, 0.88); overflow-wrap: anywhere; }
.badges { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.badge {
  display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.85rem; border-radius: 999px;
  background: rgba(255, 255, 255, 0.16); border: 1px solid rgba(255, 255, 255, 0.35); color: #fff;
  font-size: 0.8rem; font-weight: 500; backdrop-filter: blur(6px);
}
.badge.role { background: #fff; border-color: #fff; color: var(--pink-600); font-weight: 600; }
.badge.role[data-role='DOCTOR'] { color: #6b3fb5; }
.badge.role[data-role='ADMIN'] { color: var(--purple-900); }
.badge.verify[data-status='VERIFIED'] { background: #22c55e; border-color: #22c55e; font-weight: 600; }
.badge.verify[data-status='REJECTED'] { background: #fff4e5; border-color: #fff4e5; color: #9a3412; font-weight: 600; }

.notice { display: flex; align-items: flex-start; gap: 0.6rem; margin: 1.25rem 0 0; line-height: 1.55; }
.notice :deep(svg) { flex: none; margin-top: 0.1rem; }
.section.urgent { border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15), 0 18px 45px rgba(42, 22, 80, 0.14); }

/* Glass panel with a ring that fills as the profile is completed. */
.completion {
  display: flex; align-items: center; gap: 1rem; max-width: 330px; padding: 1rem 1.25rem 1rem 1rem;
  border-radius: 20px; background: rgba(255, 255, 255, 0.14); border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
}
.ring-wrap { position: relative; flex: none; width: 92px; height: 92px; }
.ring { width: 100%; height: 100%; transform: rotate(-90deg); }
.ring circle { fill: none; stroke-width: 9; }
.ring-track { stroke: rgba(255, 255, 255, 0.22); }
.ring-value { stroke: #fff; stroke-linecap: round; transition: stroke-dashoffset 0.9s cubic-bezier(0.22, 1, 0.36, 1); }
.ring-label { position: absolute; inset: 0; display: grid; place-content: center; justify-items: center; line-height: 1.1; }
.ring-label strong { font-size: 1.35rem; }
.ring-label span { font-size: 0.62rem; text-transform: uppercase; letter-spacing: 0.08em; opacity: 0.8; }
.completion-title { font-weight: 600; font-size: 0.95rem; }
.completion-text p { margin: 0.2rem 0 0; font-size: 0.82rem; color: rgba(255, 255, 255, 0.85); }

/* Quick facts strip */
.facts {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 0.25rem 0.5rem;
  margin: 0; padding: 1rem 1.5rem; list-style: none;
}
.facts li { display: flex; align-items: center; gap: 0.7rem; min-width: 0; padding: 0.35rem 0.25rem; }
.fact-icon {
  flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px;
  background: var(--pink-50); color: var(--pink-500); border: 1px dashed var(--pink-300);
}
.fact-icon.filled { background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; border-color: transparent; }
.fact-text { display: grid; min-width: 0; }
.fact-label { font-size: 0.7rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.fact-value { color: var(--purple-900); font-weight: 600; font-size: 0.92rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fact-add {
  justify-self: start; padding: 0; border: 0; background: none; cursor: pointer;
  color: var(--pink-600); font: 500 0.88rem 'Poppins', sans-serif;
}
.fact-add:hover { text-decoration: underline; }
.fact-add:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; border-radius: 4px; }

/* Sections */
.layout { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 1.25rem; margin-top: 1.25rem; align-items: start; }
.side { display: grid; gap: 1.25rem; }
.section { padding: 1.75rem; transition: box-shadow 0.25s ease, border-color 0.25s ease; }
/* The card being filled in lifts slightly, so the user always sees where they are. */
.section:focus-within { border-color: var(--pink-300); box-shadow: 0 22px 50px rgba(216, 27, 106, 0.18); }
.section-head {
  display: flex; gap: 0.85rem; align-items: flex-start;
  margin-bottom: 1.1rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border);
}
.section-icon {
  flex: none; display: grid; place-items: center; width: 44px; height: 44px; border-radius: 14px;
  background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff;
  box-shadow: 0 8px 18px rgba(216, 27, 106, 0.28);
}
.section-head h2 { margin: 0; font-size: 1.15rem; color: var(--purple-900); }
.section-head .muted { margin: 0.15rem 0 0; overflow-wrap: anywhere; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.optional {
  margin-left: 0.3rem; padding: 0.05rem 0.5rem; border-radius: 999px; background: var(--pink-50);
  font-weight: 500; font-size: 0.72rem; color: var(--muted); vertical-align: middle;
}
.section :deep(.form-field input),
.section .form-field select,
.section .form-field textarea {
  background: #fffafc; transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}
.section :deep(.form-field input:focus),
.section .form-field select:focus,
.section .form-field textarea:focus { background: #fff; }
.form-field textarea {
  width: 100%; padding: 0.8rem 1rem; border: 1.5px solid var(--border); border-radius: 12px; resize: vertical;
  color: var(--text); font: 400 1rem 'Poppins', sans-serif;
}
.form-field textarea:hover { border-color: var(--pink-300); }
.form-field textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.form-field textarea[aria-invalid='true'] { border-color: var(--high); }
.field-foot { display: flex; justify-content: space-between; gap: 0.75rem; }
.counter { flex: none; font-size: 0.78rem; color: var(--muted); }
.counter.over { color: var(--pink-600); font-weight: 600; }
.hint { margin: -0.4rem 0 0.4rem; }
.actions {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  margin-top: 0.4rem; padding-top: 1rem; border-top: 1px solid var(--border);
}
.section .btn { justify-content: center; }
.section .btn-primary:not(:disabled) { background: linear-gradient(120deg, var(--pink-600), #b5179e); border-color: transparent; }
.side .btn { width: 100%; margin-top: 0.25rem; }

@media (max-width: 960px) {
  .hero-body { grid-template-columns: auto minmax(0, 1fr); }
  .completion { grid-column: 1 / -1; max-width: none; }
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .hero-body { grid-template-columns: 1fr; justify-items: center; text-align: center; gap: 1rem; padding: 1.75rem 1.25rem 1.25rem; }
  .avatar { width: 88px; height: 88px; font-size: 1.9rem; }
  .identity h1 { font-size: 1.5rem; }
  .email, .badges { justify-content: center; }
  .completion { width: 100%; text-align: left; }
  .facts { grid-template-columns: 1fr 1fr; padding: 0.75rem; }
  .row { grid-template-columns: 1fr; gap: 0; }
  .actions { flex-direction: column-reverse; }
  .actions .btn { width: 100%; }
  .section { padding: 1.25rem; }
}
</style>
