<script setup>
import { reactive, ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import AvailabilityEditor from './AvailabilityEditor.vue'
import { SPECIALTY } from '../../appointments/format'
import { validateEmail } from '../../auth/validation'
import { photoSrc } from '../../services/surgeonService'

// Create or edit a Breast Surgeon profile, in two steps: Basic Information (who you are and how to reach you)
// and Extra Information (credentials, hospital, availability, documents). The specialty is fixed to Breast
// Surgery. The picture is chosen here and uploaded (or removed) by the page once the profile is saved; with
// `withFiles` (creating) the verification documents are chosen here too.
// Emits submit({ profile, photo, removePhoto, documents }).
const props = defineProps({
  initial: { type: Object, default: null },
  defaults: { type: Object, default: () => ({}) },
  withFiles: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  serverErrors: { type: Object, default: () => ({}) },
  submitLabel: { type: String, default: 'Save profile' },
})
const emit = defineEmits(['submit', 'cancel'])

const DEPARTMENT = 'Surgery'
const GENDERS = [
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'OTHER', label: 'Other' },
]
const COMMON_LANGUAGES = ['Bangla', 'English', 'Hindi', 'Urdu', 'Arabic']
const MAX_LANGUAGES = 10

const STEPS = [
  { id: 'basic', label: 'Basic Information', icon: 'user' },
  { id: 'extra', label: 'Extra Information', icon: 'award' },
]
// Which step each field (and each server error key) is on, so a failed save opens the right one.
const STEP_OF = {
  photo: 'basic', firstName: 'basic', lastName: 'basic', fullName: 'basic', specialty: 'basic', consultationFee: 'basic',
  phone: 'basic', email: 'basic', dateOfBirth: 'basic', gender: 'basic', registrationNumber: 'basic', languages: 'basic',
  biography: 'basic',
  designation: 'extra', qualifications: 'extra', experienceYears: 'extra', hospitalName: 'extra', hospitalAddress: 'extra',
  availableDays: 'extra', timeSlots: 'extra', documents: 'extra',
}

const TEXT_FIELDS = ['email', 'phone', 'designation', 'qualifications', 'experienceYears', 'hospitalName',
  'hospitalAddress', 'consultationFee', 'biography', 'registrationNumber', 'gender', 'dateOfBirth']

// The API keeps one full name; the form shows it as first and last name (the last word is the last name).
function splitName(fullName) {
  const words = String(fullName ?? '').trim().split(/\s+/).filter(Boolean)
  if (words.length < 2) return { firstName: words[0] ?? '', lastName: '' }
  return { firstName: words.slice(0, -1).join(' '), lastName: words[words.length - 1] }
}

function fromProfile(p) {
  const f = splitName(p?.fullName ?? props.defaults.fullName)
  TEXT_FIELDS.forEach((k) => (f[k] = p?.[k] ?? props.defaults[k] ?? ''))
  return f
}

const fields = reactive(fromProfile(props.initial))
const languages = ref([...(props.initial?.languages ?? [])])
const availability = ref({
  availableDays: props.initial?.availableDays ?? ['SATURDAY', 'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY'],
  timeSlots: props.initial?.timeSlots ?? ['17:00', '17:30', '18:00', '18:30', '19:00'],
})
watch(() => props.initial, (p) => {
  if (!p) return
  Object.assign(fields, fromProfile(p))
  languages.value = [...(p.languages ?? [])]
  availability.value = { availableDays: p.availableDays, timeSlots: p.timeSlots }
})

const step = ref('basic')
const profileCode = computed(() => (props.initial?.id ? `#BS-${props.initial.id.slice(-6).toUpperCase()}` : 'Given after saving'))
const today = new Date()
const isoDay = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const latestBirthDate = isoDay(new Date(today.getFullYear() - 21, today.getMonth(), today.getDate()))
const earliestBirthDate = isoDay(new Date(today.getFullYear() - 100, today.getMonth(), today.getDate()))

// ---- Validation: the same rules as the API (BreastSurgeonProfileRequest and BreastSurgeonService) ----
const errors = reactive({})
const allErrors = computed(() => {
  const merged = { ...errors, ...props.serverErrors }
  // The server checks the full name; show that under First Name.
  if (merged.fullName && !merged.firstName) merged.firstName = merged.fullName
  return merged
})
watch(() => props.serverErrors, (server) => {
  const first = Object.keys(server ?? {}).find((k) => STEP_OF[k])
  if (first) step.value = STEP_OF[first]
})

function checkBasic() {
  const t = (k) => String(fields[k] ?? '').trim()
  if (!t('firstName')) errors.firstName = 'First name is required'
  if (!t('lastName')) errors.lastName = 'Last name is required'
  const fullName = `${t('firstName')} ${t('lastName')}`.trim()
  if (!errors.firstName && !errors.lastName && fullName.length > 100) errors.firstName = 'Full name must be 100 characters or fewer'
  const fee = Number(fields.consultationFee)
  if (fields.consultationFee === '' || !Number.isInteger(fee) || fee < 0 || fee > 1000000) errors.consultationFee = 'Enter the fee in taka, from 0 to 1,000,000'
  if (!t('phone')) errors.phone = 'Phone number is required'
  else if (!/^\+?[0-9 ()-]{7,20}$/.test(t('phone'))) errors.phone = 'Enter a valid phone number, like +8801712345678'
  errors.email = validateEmail(t('email')) || undefined
  if (t('dateOfBirth') && (t('dateOfBirth') > latestBirthDate || t('dateOfBirth') < earliestBirthDate)) {
    errors.dateOfBirth = 'Enter a date of birth for an age between 21 and 100'
  }
  if (!t('registrationNumber')) errors.registrationNumber = 'Medical registration number is required'
  else if (!/^[A-Za-z0-9/ -]{3,40}$/.test(t('registrationNumber'))) errors.registrationNumber = 'Use 3 to 40 letters, numbers, spaces, / or -'
  const bio = t('biography')
  if (!bio) errors.biography = 'A short biography is required'
  else if (bio.length < 30 || bio.length > 2000) errors.biography = 'Biography must be 30 to 2000 characters'
}

function checkExtra() {
  const t = (k) => String(fields[k] ?? '').trim()
  const len = (k, min, max, label) => {
    if (!t(k)) errors[k] = `${label} is required`
    else if (t(k).length < min || t(k).length > max) errors[k] = `${label} must be ${min} to ${max} characters`
  }
  len('designation', 2, 100, 'Designation')
  len('qualifications', 2, 300, 'Degrees and qualifications')
  const years = Number(fields.experienceYears)
  if (fields.experienceYears === '' || !Number.isInteger(years) || years < 0 || years > 60) errors.experienceYears = 'Enter whole years from 0 to 60'
  len('hospitalName', 2, 150, 'Hospital or chamber name')
  len('hospitalAddress', 2, 300, 'Hospital address')
  if (!availability.value.availableDays?.length) errors.availableDays = 'Choose at least one consultation day'
  if (!availability.value.timeSlots?.length) errors.timeSlots = 'Add at least one time slot'
  if (props.withFiles && !documents.value.length) errors.documents = 'Upload at least one verification document (registration certificate or degree)'
}

/** Checks one step (or both); keeps the other step's errors only when checking both. Returns the first bad step. */
function validate(which) {
  Object.keys(errors).forEach((k) => {
    if (!which || STEP_OF[k] === which) delete errors[k]
  })
  if (!which || which === 'basic') checkBasic()
  if (!which || which === 'extra') checkExtra()
  Object.keys(errors).forEach((k) => errors[k] === undefined && delete errors[k])
  const bad = Object.keys(errors).map((k) => STEP_OF[k])
  return bad.includes('basic') ? 'basic' : bad.includes('extra') ? 'extra' : null
}

const form = ref(null)
function focusFirstError() {
  nextTick(() => form.value?.querySelector(`[data-step="${step.value}"] [aria-invalid="true"]`)?.focus())
}

function next() {
  if (validate('basic')) return focusFirstError()
  step.value = 'extra'
  nextTick(() => form.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }))
}

// ---- Known languages ----
const languageDraft = ref('')
function toggleLanguage(name) {
  delete errors.languages
  const at = languages.value.findIndex((l) => l.toLowerCase() === name.toLowerCase())
  if (at >= 0) languages.value.splice(at, 1)
  else if (languages.value.length >= MAX_LANGUAGES) errors.languages = `List at most ${MAX_LANGUAGES} languages`
  else languages.value.push(name)
}
function addLanguage() {
  const name = languageDraft.value.trim().replace(/\s+/g, ' ')
  if (!name) return
  if (!/^[\p{L} ()-]{2,30}$/u.test(name)) {
    errors.languages = 'Use 2 to 30 letters for a language'
    return
  }
  if (!languages.value.some((l) => l.toLowerCase() === name.toLowerCase())) toggleLanguage(name)
  languageDraft.value = ''
}
const extraLanguages = computed(() => languages.value.filter((l) => !COMMON_LANGUAGES.some((c) => c.toLowerCase() === l.toLowerCase())))
const hasLanguage = (name) => languages.value.some((l) => l.toLowerCase() === name.toLowerCase())

// ---- Picture and documents ----
const photo = ref(null)
const photoPreview = ref('')
const removePhoto = ref(false)
const currentPhoto = computed(() => {
  if (photoPreview.value) return photoPreview.value
  if (removePhoto.value) return ''
  return photoSrc(props.initial?.photoUrl)
})
const hasPhoto = computed(() => !!photo.value || (!removePhoto.value && !!props.initial?.photoUrl))

function clearPreview() {
  if (photoPreview.value) URL.revokeObjectURL?.(photoPreview.value)
  photoPreview.value = ''
}
function pickPhoto(event) {
  const file = event.target.files?.[0]
  delete errors.photo
  event.target.value = ''
  if (!file) return
  if (!/^image\/(png|jpeg|webp)$/.test(file.type)) errors.photo = 'Choose a PNG, JPG or WebP picture'
  else if (file.size > 5 * 1024 * 1024) errors.photo = 'The picture must be 5 MB or smaller'
  else {
    photo.value = file
    removePhoto.value = false
    clearPreview()
    photoPreview.value = typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : ''
  }
}
function dropPhoto() {
  delete errors.photo
  photo.value = null
  clearPreview()
  removePhoto.value = !!props.initial?.photoUrl
}
onBeforeUnmount(clearPreview)

const documents = ref([])
function pickDocuments(event) {
  delete errors.documents
  for (const file of event.target.files ?? []) {
    if (!/^(application\/pdf|image\/(png|jpeg))$/.test(file.type)) errors.documents = `${file.name}: upload a PDF, PNG or JPG`
    else if (file.size > 10 * 1024 * 1024) errors.documents = `${file.name} is larger than 10 MB`
    else if (documents.value.length >= 5) errors.documents = 'At most 5 documents'
    else documents.value.push(file)
  }
  event.target.value = ''
}

function submit() {
  const bad = validate()
  if (bad) {
    step.value = bad
    return focusFirstError()
  }
  const t = (k) => (typeof fields[k] === 'string' ? fields[k].trim() : fields[k])
  const profile = {
    specialty: SPECIALTY,
    ...availability.value,
    fullName: `${t('firstName')} ${t('lastName')}`.trim(),
    email: t('email'),
    phone: t('phone'),
    designation: t('designation'),
    qualifications: t('qualifications'),
    experienceYears: Number(fields.experienceYears),
    hospitalName: t('hospitalName'),
    hospitalAddress: t('hospitalAddress'),
    consultationFee: Number(fields.consultationFee),
    biography: t('biography'),
    registrationNumber: t('registrationNumber'),
    gender: t('gender') || null,
    dateOfBirth: t('dateOfBirth') || null,
    languages: [...languages.value],
  }
  emit('submit', { profile, photo: photo.value, removePhoto: removePhoto.value, documents: [...documents.value] })
}

const inv = (k) => (allErrors.value[k] ? 'true' : 'false')
const stepHasErrors = (id) => Object.keys(allErrors.value).some((k) => STEP_OF[k] === id)
</script>

<template>
  <form ref="form" class="surgeon-form" novalidate @submit.prevent="submit">
    <!-- Steps -->
    <nav class="steps" aria-label="Profile sections">
      <button
        v-for="s in STEPS"
        :key="s.id"
        type="button"
        class="step-tab"
        :class="{ active: step === s.id, invalid: stepHasErrors(s.id) }"
        :aria-current="step === s.id ? 'step' : undefined"
        @click="step = s.id"
      >
        <span class="radio" aria-hidden="true"></span>
        <AppIcon :name="s.icon" :size="16" />
        {{ s.label }}
        <AppIcon v-if="stepHasErrors(s.id)" name="alert-circle" :size="15" class="warn" />
      </button>
    </nav>

    <div class="panel">
      <!-- Step 1: Basic Information -->
      <section v-show="step === 'basic'" class="card form-card" data-step="basic">
        <header class="card-head"><h2>Basic Information</h2></header>
        <div class="card-body">
          <div class="photo-block">
            <span class="label">Profile Image</span>
            <div class="photo-row">
              <span class="photo-preview">
                <img v-if="currentPhoto" :src="currentPhoto" alt="Profile picture" />
                <AppIcon v-else name="user" :size="40" />
              </span>
              <div class="photo-actions">
                <div class="photo-buttons">
                  <label class="btn btn-dark btn-sm file-btn">
                    <AppIcon name="image" :size="15" /> {{ hasPhoto ? 'Change Image' : 'Upload Image' }}
                    <input type="file" accept="image/png,image/jpeg,image/webp" @change="pickPhoto" />
                  </label>
                  <button v-if="hasPhoto" type="button" class="btn btn-outline btn-sm" @click="dropPhoto">
                    <AppIcon name="trash" :size="15" /> Remove
                  </button>
                </div>
                <p class="hint">Use JPEG, PNG or WebP. Best size: 400×400 pixels. Keep it under 5 MB.</p>
                <p v-if="removePhoto" class="hint pink">The picture will be removed when you save.</p>
                <p class="field-error">{{ allErrors.photo }}</p>
              </div>
            </div>
          </div>

          <div class="grid-3">
            <div class="form-field">
              <label for="bs-id">ID</label>
              <input id="bs-id" :value="profileCode" readonly class="readonly" />
            </div>
            <div class="form-field">
              <label for="bs-first-name">First Name <span class="req">*</span></label>
              <input id="bs-first-name" v-model="fields.firstName" :aria-invalid="inv('firstName')" placeholder="Dr. Nasrin" autocomplete="given-name" />
              <p class="field-error">{{ allErrors.firstName }}</p>
            </div>
            <div class="form-field">
              <label for="bs-last-name">Last Name <span class="req">*</span></label>
              <input id="bs-last-name" v-model="fields.lastName" :aria-invalid="inv('lastName')" placeholder="Akter" autocomplete="family-name" />
              <p class="field-error">{{ allErrors.lastName }}</p>
            </div>

            <div class="form-field">
              <label for="bs-department">Department</label>
              <div id="bs-department" class="fixed-field">{{ DEPARTMENT }} <span class="lock">Fixed</span></div>
            </div>
            <div class="form-field">
              <label for="bs-specialty">Specialist</label>
              <div id="bs-specialty" class="fixed-field"><AppIcon name="heart" :size="15" /> {{ SPECIALTY }} <span class="lock">Fixed</span></div>
              <p class="field-error">{{ allErrors.specialty }}</p>
            </div>
            <div class="form-field">
              <label for="bs-fee">Fees (৳) <span class="req">*</span></label>
              <input id="bs-fee" v-model="fields.consultationFee" type="number" min="0" step="50" :aria-invalid="inv('consultationFee')" placeholder="1500" />
              <p class="field-error">{{ allErrors.consultationFee }}</p>
            </div>

            <div class="form-field">
              <label for="bs-phone">Phone Number <span class="req">*</span></label>
              <input id="bs-phone" v-model="fields.phone" type="tel" :aria-invalid="inv('phone')" placeholder="+8801712345678" autocomplete="tel" />
              <p class="field-error">{{ allErrors.phone }}</p>
            </div>
            <div class="form-field">
              <label for="bs-email">Email Address <span class="req">*</span></label>
              <input id="bs-email" v-model="fields.email" type="email" :aria-invalid="inv('email')" placeholder="chamber@example.com" autocomplete="email" />
              <p class="field-error">{{ allErrors.email }}</p>
            </div>
            <div class="form-field">
              <label for="bs-dob">DOB</label>
              <input id="bs-dob" v-model="fields.dateOfBirth" type="date" :min="earliestBirthDate" :max="latestBirthDate" :aria-invalid="inv('dateOfBirth')" />
              <p class="field-error">{{ allErrors.dateOfBirth }}</p>
            </div>

            <div class="form-field">
              <label for="bs-gender">Gender</label>
              <select id="bs-gender" v-model="fields.gender" :aria-invalid="inv('gender')">
                <option value="">Select</option>
                <option v-for="g in GENDERS" :key="g.value" :value="g.value">{{ g.label }}</option>
              </select>
              <p class="field-error">{{ allErrors.gender }}</p>
            </div>
            <div class="form-field">
              <label for="bs-registration">Registration Number <span class="req">*</span></label>
              <input id="bs-registration" v-model="fields.registrationNumber" :aria-invalid="inv('registrationNumber')" placeholder="BMDC A-12345" />
              <p class="field-error">{{ allErrors.registrationNumber }}</p>
            </div>
            <div class="form-field">
              <label for="bs-language-input">Known Languages</label>
              <div class="language-input">
                <input id="bs-language-input" v-model="languageDraft" :aria-invalid="inv('languages')" placeholder="Add a language" @keydown.enter.prevent="addLanguage" />
                <button type="button" class="add-lang" aria-label="Add language" @click="addLanguage"><AppIcon name="arrow-right" :size="16" /></button>
              </div>
              <p class="field-error">{{ allErrors.languages }}</p>
            </div>
            <div class="chips span-3" role="group" aria-label="Known languages">
              <button
                v-for="l in COMMON_LANGUAGES"
                :key="l"
                type="button"
                class="chip"
                :class="{ on: hasLanguage(l) }"
                :aria-pressed="hasLanguage(l)"
                @click="toggleLanguage(l)"
              >
                <AppIcon v-if="hasLanguage(l)" name="check-circle" :size="14" /> {{ l }}
              </button>
              <button v-for="l in extraLanguages" :key="l" type="button" class="chip on" :aria-label="`Remove ${l}`" @click="toggleLanguage(l)">
                {{ l }} <AppIcon name="close" :size="13" />
              </button>
            </div>

            <div class="form-field span-3">
              <label for="bs-bio">About <span class="req">*</span></label>
              <textarea id="bs-bio" v-model="fields.biography" rows="5" :aria-invalid="inv('biography')"
                placeholder="Your training, the breast conditions you treat, and how you work with patients."></textarea>
              <p class="hint">{{ String(fields.biography).trim().length }}/2000 characters (at least 30)</p>
              <p class="field-error">{{ allErrors.biography }}</p>
            </div>
          </div>
        </div>
        <footer class="card-foot">
          <button type="button" class="btn btn-outline" :disabled="submitting" @click="emit('cancel')">Cancel</button>
          <button type="button" class="btn btn-primary next-btn" @click="next">Next <AppIcon name="arrow-right" :size="16" /></button>
        </footer>
      </section>

      <!-- Step 2: Extra Information -->
      <section v-show="step === 'extra'" class="card form-card" data-step="extra">
        <header class="card-head"><h2>Extra Information</h2></header>
        <div class="card-body">
          <h3 class="sub">Professional details <span class="muted">Checked by an administrator before your profile goes live.</span></h3>
          <div class="grid-3">
            <div class="form-field">
              <label for="bs-designation">Designation <span class="req">*</span></label>
              <input id="bs-designation" v-model="fields.designation" :aria-invalid="inv('designation')" placeholder="Consultant Breast Surgeon" />
              <p class="field-error">{{ allErrors.designation }}</p>
            </div>
            <div class="form-field span-2">
              <label for="bs-qualifications">Degrees &amp; Qualifications <span class="req">*</span></label>
              <input id="bs-qualifications" v-model="fields.qualifications" :aria-invalid="inv('qualifications')" placeholder="MBBS, FCPS (Surgery), Fellowship in Breast Surgery" />
              <p class="field-error">{{ allErrors.qualifications }}</p>
            </div>
            <div class="form-field">
              <label for="bs-experience">Experience (years) <span class="req">*</span></label>
              <input id="bs-experience" v-model="fields.experienceYears" type="number" min="0" max="60" step="1" :aria-invalid="inv('experienceYears')" />
              <p class="field-error">{{ allErrors.experienceYears }}</p>
            </div>
            <div class="form-field">
              <label for="bs-hospital">Hospital / Chamber <span class="req">*</span></label>
              <input id="bs-hospital" v-model="fields.hospitalName" :aria-invalid="inv('hospitalName')" placeholder="Dhaka Breast Centre" />
              <p class="field-error">{{ allErrors.hospitalName }}</p>
            </div>
            <div class="form-field">
              <label for="bs-address">Hospital Address <span class="req">*</span></label>
              <input id="bs-address" v-model="fields.hospitalAddress" :aria-invalid="inv('hospitalAddress')" placeholder="House 12, Road 5, Dhanmondi, Dhaka" />
              <p class="field-error">{{ allErrors.hospitalAddress }}</p>
            </div>
          </div>

          <h3 class="sub">Availability <span class="muted">Patients can book these slots on these days. You can change them any time.</span></h3>
          <AvailabilityEditor v-model="availability" :errors="allErrors" />

          <template v-if="withFiles">
            <h3 class="sub">Verification documents <span class="muted">Registration certificate, degree or fellowship certificate. Only administrators see these.</span></h3>
            <label class="dropzone" :class="{ invalid: allErrors.documents }">
              <AppIcon name="file-text" :size="26" />
              <span><strong>Choose documents</strong> — PDF, PNG or JPG, up to 10 MB each (max 5)</span>
              <input type="file" accept="application/pdf,image/png,image/jpeg" multiple @change="pickDocuments" />
            </label>
            <ul v-if="documents.length" class="doc-list">
              <li v-for="(d, i) in documents" :key="`${d.name}-${i}`">
                <AppIcon name="file-text" :size="16" /> <span class="doc-name">{{ d.name }}</span>
                <span class="muted">{{ Math.max(1, Math.round(d.size / 1024)) }} KB</span>
                <button type="button" class="remove" :aria-label="`Remove ${d.name}`" @click="documents.splice(i, 1)"><AppIcon name="close" :size="14" /></button>
              </li>
            </ul>
            <p class="field-error">{{ allErrors.documents }}</p>
          </template>
        </div>
        <footer class="card-foot">
          <button type="button" class="btn btn-outline" :disabled="submitting" @click="step = 'basic'">Back</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">
            <span v-if="submitting" class="spinner" aria-hidden="true"></span>
            {{ submitting ? 'Saving…' : submitLabel }}
          </button>
        </footer>
      </section>
    </div>
  </form>
</template>

<style scoped>
.surgeon-form { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 1.4rem; align-items: start; scroll-margin-top: 96px; }

/* Step menu */
.steps { position: sticky; top: 96px; display: grid; gap: 0.7rem; }
.step-tab {
  display: flex; align-items: center; gap: 0.6rem; width: 100%; padding: 0.85rem 1rem; border-radius: 12px;
  border: 1.5px solid var(--border); background: #fff; color: var(--text); font: 500 0.95rem 'Poppins', sans-serif;
  cursor: pointer; text-align: left; transition: border-color 0.15s, box-shadow 0.15s;
}
.step-tab:hover { border-color: var(--pink-300); }
.step-tab:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.step-tab.active { border-color: var(--pink-600); color: var(--purple-900); font-weight: 600; box-shadow: var(--shadow); }
.step-tab .radio { flex: none; width: 16px; height: 16px; border-radius: 50%; border: 2px solid var(--border); }
.step-tab.active .radio { border: 5px solid var(--pink-600); }
.step-tab .warn { margin-left: auto; color: var(--high); }

/* The card */
.form-card { padding: 0; border-radius: 16px; overflow: hidden; }
.card-head { padding: 1rem 1.5rem; border-bottom: 1px solid var(--border); }
.card-head h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.card-body { padding: 1.4rem 1.5rem 0.4rem; }
.card-foot { display: flex; justify-content: flex-end; gap: 0.7rem; padding: 1rem 1.5rem; border-top: 1px solid var(--border); background: #fdfafc; }
.next-btn { gap: 0.4rem; }

.label, .form-field label { font-weight: 600; color: var(--purple-900); font-size: 0.9rem; }
.req { color: var(--high); }
.grid-3 { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.2rem 1rem; }
.span-2 { grid-column: span 2; }
.span-3 { grid-column: 1 / -1; }
.form-field input, .form-field select { padding: 0.7rem 0.9rem; font-size: 0.95rem; }
.form-field select[aria-invalid='true'] { border-color: var(--high); }
.readonly { background: #f3f0f6 !important; color: var(--muted) !important; cursor: default; }
.form-field textarea {
  width: 100%; padding: 0.8rem 1rem; border: 1.5px solid var(--border); border-radius: 12px; background: #fff;
  color: var(--text); font: 400 0.95rem 'Poppins', sans-serif; resize: vertical;
}
.form-field textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.form-field textarea[aria-invalid='true'] { border-color: var(--high); }
.hint { margin: 0; font-size: 0.78rem; color: var(--muted); }
.hint.pink { color: var(--pink-600); font-weight: 500; }
.fixed-field {
  display: flex; align-items: center; gap: 0.45rem; padding: 0.7rem 0.9rem; border-radius: 12px; border: 1.5px dashed var(--pink-300);
  background: var(--pink-50); color: var(--pink-600); font-weight: 600; font-size: 0.95rem;
}
.lock { margin-left: auto; padding: 0.05rem 0.5rem; border-radius: 6px; background: #fff; color: var(--muted); font-size: 0.7rem; font-weight: 600; }

/* Picture */
.photo-block { display: grid; gap: 0.5rem; margin-bottom: 1.1rem; }
.photo-row { display: flex; align-items: center; gap: 1.1rem; }
.photo-preview {
  flex: none; display: grid; place-items: center; width: 96px; height: 96px; border-radius: 14px; overflow: hidden;
  background: var(--pink-50); color: var(--pink-300); border: 2px dashed var(--pink-300);
}
.photo-preview img { width: 100%; height: 100%; object-fit: cover; }
.photo-actions { display: grid; gap: 0.35rem; }
.photo-buttons { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.btn-dark { background: var(--purple-900); color: #fff; border: 0; }
.btn-dark:hover { background: #3b2270; }
.file-btn { position: relative; cursor: pointer; }
.file-btn input, .dropzone input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.file-btn:has(input:focus-visible) { outline: 3px solid var(--pink-300); outline-offset: 2px; }

/* Languages */
.language-input { position: relative; }
.language-input input { padding-right: 2.8rem; }
.add-lang {
  position: absolute; top: 50%; right: 6px; transform: translateY(-50%); display: grid; place-items: center;
  width: 32px; height: 32px; border: 0; border-radius: 8px; background: var(--pink-50); color: var(--pink-600); cursor: pointer;
}
.chips { display: flex; flex-wrap: wrap; gap: 0.45rem; margin: -0.4rem 0 0.9rem; }
.chip {
  display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.35rem 0.85rem; border-radius: 999px;
  border: 1.5px solid var(--border); background: #fff; color: var(--text); font: 500 0.85rem 'Poppins', sans-serif; cursor: pointer;
}
.chip:hover { border-color: var(--pink-300); }
.chip.on { border-color: var(--pink-600); background: var(--pink-50); color: var(--pink-600); }
.chip:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }

/* Extra information */
.sub { display: grid; gap: 0.1rem; margin: 0.4rem 0 0.9rem; color: var(--purple-900); font-size: 1rem; }
.sub .muted { font-size: 0.83rem; font-weight: 400; }
.dropzone {
  position: relative; display: flex; align-items: center; gap: 0.8rem; padding: 1.1rem; border-radius: 16px;
  border: 2px dashed var(--pink-300); background: var(--pink-50); color: var(--purple-900); font-size: 0.9rem; cursor: pointer;
}
.dropzone :deep(svg) { color: var(--pink-600); flex: none; }
.dropzone.invalid { border-color: var(--high); }
.dropzone:has(input:focus-visible) { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.doc-list { list-style: none; margin: 0.7rem 0 0; padding: 0; display: grid; gap: 0.4rem; }
.doc-list li { display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0.7rem; border-radius: 10px; background: #f8f6fa; font-size: 0.88rem; }
.doc-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.remove { display: grid; place-items: center; width: 26px; height: 26px; border: 0; border-radius: 50%; background: transparent; color: var(--muted); cursor: pointer; }
.remove:hover { background: #fdecea; color: var(--high); }

@media (max-width: 1000px) {
  .grid-3 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .span-2 { grid-column: 1 / -1; }
}
@media (max-width: 820px) {
  .surgeon-form { grid-template-columns: 1fr; }
  .steps { position: static; grid-template-columns: 1fr 1fr; }
}
@media (max-width: 560px) {
  .grid-3 { grid-template-columns: 1fr; }
  .steps { grid-template-columns: 1fr; }
  .card-body, .card-head, .card-foot { padding-left: 1rem; padding-right: 1rem; }
  .photo-row { flex-direction: column; align-items: flex-start; }
  .card-foot .btn { flex: 1; justify-content: center; }
}
</style>
