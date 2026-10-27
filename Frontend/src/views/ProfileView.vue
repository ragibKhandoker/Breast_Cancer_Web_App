<script setup>
import { reactive, ref, computed, onMounted, onBeforeUnmount } from 'vue'
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

// How much of the optional profile is filled in, shown as a progress bar.
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
        <div class="hero-banner" aria-hidden="true"></div>
        <div class="hero-body">
          <div class="avatar" aria-hidden="true">{{ initials }}</div>
          <div class="identity">
            <h1>{{ profile.fullName }}</h1>
            <p class="muted">{{ profile.email }}</p>
            <div class="badges">
              <span class="badge role" :data-role="profile.role">{{ ROLE_LABELS[profile.role] }}</span>
              <span class="badge"><AppIcon name="calendar" :size="14" /> Member since {{ memberSince }}</span>
            </div>
          </div>
          <div class="completion" :aria-label="`Profile ${completion}% complete`">
            <div class="completion-top">
              <span>Profile completeness</span>
              <strong>{{ completion }}%</strong>
            </div>
            <div class="completion-bar"><span :style="{ width: completion + '%' }"></span></div>
            <p class="muted small">{{ completion === 100 ? 'All set. Thank you!' : 'Add your details below to complete it.' }}</p>
          </div>
        </div>
      </div>

      <div class="layout">
        <!-- Personal details -->
        <form v-reveal class="card section form-stack" novalidate @submit.prevent="saveDetails">
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
            <p id="profile-address-error" class="field-error" aria-live="polite">{{ detailErrors.address }}</p>
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
          <form v-reveal="100" class="card section form-stack" novalidate @submit.prevent="saveEmail">
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
          <form v-reveal="200" class="card section form-stack" novalidate @submit.prevent="savePassword">
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
</template>

<style scoped>
.small { font-size: 0.86rem; }
.load-error { max-width: 560px; margin: 2rem auto; padding: 2rem; text-align: center; }
.load-error h1 { margin-top: 0; }

/* Summary card */
.hero { padding: 0; overflow: hidden; }
.hero-banner {
  height: 110px;
  background:
    radial-gradient(circle at 85% 20%, rgba(255, 255, 255, 0.35), transparent 40%),
    linear-gradient(120deg, var(--pink-600) 0%, #b5179e 55%, #6b3fb5 100%);
}
.hero-body {
  display: grid; grid-template-columns: auto minmax(0, 1fr) minmax(220px, 300px); gap: 1.5rem; align-items: end;
  padding: 0 1.75rem 1.5rem;
}
.avatar {
  display: grid; place-items: center; width: 108px; height: 108px; margin-top: -54px; border-radius: 50%;
  background: linear-gradient(135deg, var(--pink-100), #efe7ff); color: var(--pink-600);
  font-size: 2.3rem; font-weight: 700; border: 5px solid #fff; box-shadow: 0 10px 24px rgba(42, 22, 80, 0.18);
}
.identity h1 { margin: 0.75rem 0 0; font-size: 1.6rem; overflow-wrap: anywhere; }
.identity .muted { margin: 0.1rem 0 0.6rem; overflow-wrap: anywhere; }
.badges { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.badge {
  display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.75rem; border-radius: 999px;
  background: var(--pink-50); border: 1px solid var(--border); color: var(--muted); font-size: 0.8rem; font-weight: 500;
}
.badge.role { background: var(--pink-600); border-color: var(--pink-600); color: #fff; font-weight: 600; }
.badge.role[data-role='DOCTOR'] { background: #6b3fb5; border-color: #6b3fb5; }
.badge.role[data-role='ADMIN'] { background: var(--purple-900); border-color: var(--purple-900); }

.completion-top { display: flex; justify-content: space-between; font-size: 0.85rem; color: var(--purple-900); font-weight: 500; }
.completion-top strong { color: var(--pink-600); }
.completion-bar { height: 8px; margin: 0.4rem 0; border-radius: 4px; background: var(--pink-100); overflow: hidden; }
.completion-bar span {
  display: block; height: 100%; border-radius: 4px;
  background: linear-gradient(90deg, var(--pink-600), #b5179e); transition: width 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.completion .small { margin: 0; }

/* Sections */
.layout { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: 1.25rem; margin-top: 1.25rem; align-items: start; }
.side { display: grid; gap: 1.25rem; }
.section { padding: 1.5rem; }
.section-head { display: flex; gap: 0.85rem; align-items: flex-start; margin-bottom: 0.75rem; }
.section-icon {
  flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px;
  background: var(--pink-50); color: var(--pink-600);
}
.section-head h2 { margin: 0; font-size: 1.15rem; color: var(--purple-900); }
.section-head .muted { margin: 0.15rem 0 0; overflow-wrap: anywhere; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.optional { font-weight: 400; font-size: 0.78rem; color: var(--muted); }
.form-field textarea {
  width: 100%; padding: 0.8rem 1rem; border: 1.5px solid var(--border); border-radius: 12px; resize: vertical;
  background: #fff; color: var(--text); font: 400 1rem 'Poppins', sans-serif;
}
.form-field textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.hint { margin: -0.4rem 0 0.4rem; }
.actions { display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 0.4rem; }
.section .btn { justify-content: center; }
.side .btn { justify-self: start; }

@media (max-width: 960px) {
  .hero-body { grid-template-columns: auto minmax(0, 1fr); }
  .completion { grid-column: 1 / -1; }
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .hero-body { grid-template-columns: 1fr; justify-items: start; gap: 0.75rem; padding: 0 1.25rem 1.25rem; }
  .avatar { width: 88px; height: 88px; margin-top: -44px; font-size: 1.9rem; }
  .identity h1 { margin-top: 0; }
  .row { grid-template-columns: 1fr; gap: 0; }
  .actions { flex-direction: column-reverse; }
  .actions .btn, .side .btn { width: 100%; }
}
</style>
