<script setup>
import { reactive, ref, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PasswordField from '../components/auth/PasswordField.vue'
import PinkRibbon from '../components/common/PinkRibbon.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { register } from '../services/authService'
import { errorMessage, fieldErrors } from '../services/http'
import {
  PASSWORD_RULES,
  validateConfirm,
  validateEmail,
  validateFullName,
  validatePassword,
} from '../auth/validation'
import { APP_ROUTES } from '../config/appRoutes'

const router = useRouter()
const route = useRoute()

// Register as a patient or as a doctor. /register?as=doctor opens with Doctor chosen.
const ACCOUNT_TYPES = [
  { value: 'PATIENT', label: 'Register as a Patient', icon: 'heart', text: 'Upload mammograms and keep your reports.' },
  { value: 'DOCTOR', label: 'Register as a Doctor', icon: 'award', text: 'Set up your doctor profile and see shared reports.' },
]
const accountType = ref(route.query.as === 'doctor' ? 'DOCTOR' : 'PATIENT')
const isDoctor = computed(() => accountType.value === 'DOCTOR')

const fields = reactive({ fullName: '', email: '', password: '', confirm: '' })
const errors = reactive({ fullName: '', email: '', password: '', confirm: '' })
const touched = reactive({ fullName: false, email: false, password: false, confirm: false })
const status = ref('idle') // idle | submitting | success | error
const failure = ref('')
const formEl = ref(null)

const submitting = computed(() => status.value === 'submitting')
const ruleStatus = computed(() => PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.test(fields.password) })))

// 0-4 rules met, shown as a four-segment meter under the password.
const STRENGTH_LABELS = ['Too weak', 'Weak', 'Fair', 'Good', 'Strong']
const strength = computed(() => ruleStatus.value.filter((rule) => rule.met).length)

const PATIENT_BENEFITS = [
  { icon: 'microscope', title: 'AI-assisted analysis', text: 'Upload a mammogram and get an estimate in seconds.' },
  { icon: 'shield-check', title: 'Private by design', text: 'Only you, and the doctors you choose, see your reports.' },
  { icon: 'users', title: 'Share with your doctor', text: 'Send any report to your doctor with one click.' },
]
const DOCTOR_BENEFITS = [
  { icon: 'user', title: 'Your doctor profile', text: 'Photo, fees, languages, hospital and weekly schedule in one place.' },
  { icon: 'calendar', title: 'Appointments', text: 'Patients book your free slots once your profile is approved.' },
  { icon: 'shield-check', title: 'Verified by our team', text: 'An administrator checks your registration before patients can reach you.' },
]
const BENEFITS = computed(() => (isDoctor.value ? DOCTOR_BENEFITS : PATIENT_BENEFITS))

const validators = {
  fullName: () => validateFullName(fields.fullName),
  email: () => validateEmail(fields.email),
  password: () => validatePassword(fields.password),
  confirm: () => validateConfirm(fields.password, fields.confirm),
}

function check(name) {
  errors[name] = validators[name]()
  return !errors[name]
}

function onBlur(name) {
  touched[name] = true
  check(name)
}

function onInput(name) {
  if (touched[name]) check(name)
  if (name === 'password' && touched.confirm) check('confirm')
}

async function submit() {
  if (submitting.value) return
  Object.keys(touched).forEach((key) => (touched[key] = true))
  const valid = Object.keys(validators).map(check).every(Boolean)
  if (!valid) {
    await nextTick()
    formEl.value?.querySelector('[aria-invalid="true"]')?.focus()
    return
  }

  status.value = 'submitting'
  failure.value = ''
  try {
    const user = await register({
      fullName: fields.fullName.trim(), email: fields.email.trim(), password: fields.password, accountType: accountType.value,
    })
    status.value = 'success'
    await router.push({ path: APP_ROUTES.login, query: { registered: isDoctor.value ? 'doctor' : '1' }, state: { email: user.email } })
  } catch (err) {
    status.value = 'error'
    // Field problems (including "email already exists") show under their field; anything else above the button.
    const matched = Object.entries(fieldErrors(err)).filter(([key]) => key in errors)
    matched.forEach(([key, message]) => (errors[key] = message))
    failure.value = matched.length ? '' : errorMessage(err)
    if (matched.length) {
      await nextTick()
      formEl.value?.querySelector('[aria-invalid="true"]')?.focus()
    }
  }
}
</script>

<template>
  <section class="register-page">
    <div class="container">
      <div v-reveal class="shell">
        <!-- Photo panel: the faces stay visible; text sits on a dark fade at the bottom. -->
        <aside class="visual" aria-hidden="true">
          <div class="visual-frame"><div class="visual-photo"></div></div>
          <div class="visual-content">
            <span class="pill"><PinkRibbon :size="16" /> Join BreastCare</span>
            <h2>{{ isDoctor ? 'Help patients find the right breast surgeon.' : 'Early detection starts with you.' }}</h2>
            <ul class="benefits">
              <li v-for="item in BENEFITS" :key="item.title">
                <span class="benefit-icon"><AppIcon :name="item.icon" :size="20" /></span>
                <span>
                  <strong>{{ item.title }}</strong>
                  <span class="benefit-text">{{ item.text }}</span>
                </span>
              </li>
            </ul>
            <p class="trust">For research and education. Not a substitute for a radiologist.</p>
          </div>
        </aside>

        <div class="form-side">
          <header class="form-head">
            <h1>Create your account</h1>
            <p class="muted">It takes less than a minute. Choose how you will use BreastCare.</p>
          </header>

          <div class="account-types" role="radiogroup" aria-label="Account type">
            <label v-for="t in ACCOUNT_TYPES" :key="t.value" class="account-type" :class="{ on: accountType === t.value }">
              <input v-model="accountType" type="radio" name="account-type" :value="t.value" :disabled="submitting" />
              <span class="type-icon"><AppIcon :name="t.icon" :size="20" /></span>
              <span class="type-text">
                <strong>{{ t.label }}</strong>
                <span>{{ t.text }}</span>
              </span>
            </label>
          </div>
          <p v-if="isDoctor" class="alert info doctor-note">
            Doctor accounts are checked by an administrator. After you log in, set up your doctor profile; patients
            can share reports with you and book you once you are verified.
          </p>

          <form ref="formEl" class="form-stack" novalidate @submit.prevent="submit">
            <div class="form-field">
              <label for="register-name">Full name</label>
              <input
                id="register-name"
                v-model="fields.fullName"
                type="text"
                autocomplete="name"
                maxlength="100"
                placeholder="e.g. Ayesha Rahman"
                required
                :aria-invalid="!!errors.fullName"
                aria-describedby="register-name-error"
                @blur="onBlur('fullName')"
                @input="onInput('fullName')"
              />
              <p id="register-name-error" class="field-error" aria-live="polite">{{ errors.fullName }}</p>
            </div>

            <div class="form-field">
              <label for="register-email">Email</label>
              <input
                id="register-email"
                v-model="fields.email"
                type="email"
                autocomplete="email"
                inputmode="email"
                maxlength="254"
                placeholder="name@example.com"
                required
                :aria-invalid="!!errors.email"
                aria-describedby="register-email-error"
                @blur="onBlur('email')"
                @input="onInput('email')"
              />
              <p id="register-email-error" class="field-error" aria-live="polite">{{ errors.email }}</p>
            </div>

            <PasswordField
              id="register-password"
              v-model="fields.password"
              autocomplete="new-password"
              describedby="register-password-rules"
              :error="errors.password"
              @blur="onBlur('password')"
              @update:model-value="onInput('password')"
            />
            <div class="strength" :data-level="strength" :class="{ empty: !fields.password }">
              <div class="strength-bar" aria-hidden="true">
                <span v-for="n in 4" :key="n" :class="{ on: n <= strength }"></span>
              </div>
              <span class="strength-label">{{ fields.password ? STRENGTH_LABELS[strength] : 'Password strength' }}</span>
            </div>
            <ul id="register-password-rules" class="rules" aria-label="Password requirements">
              <li v-for="rule in ruleStatus" :key="rule.label" :class="{ met: rule.met }">
                <span class="tick" aria-hidden="true">{{ rule.met ? '✓' : '' }}</span> {{ rule.label }}
                <span class="sr-only">{{ rule.met ? '(done)' : '(not yet)' }}</span>
              </li>
            </ul>

            <PasswordField
              id="register-confirm"
              v-model="fields.confirm"
              label="Confirm password"
              autocomplete="new-password"
              :error="errors.confirm"
              @blur="onBlur('confirm')"
              @update:model-value="onInput('confirm')"
            />

            <div v-if="status === 'success'" class="alert success" role="status">Account created. Taking you to login…</div>
            <div v-else-if="failure" class="alert error" role="alert">{{ failure }}</div>

            <button type="submit" class="btn btn-primary submit" :disabled="submitting" :aria-busy="submitting">
              <span v-if="submitting" class="spinner" aria-hidden="true"></span>
              {{ submitting ? 'Creating account…' : isDoctor ? 'Create Doctor Account' : 'Create Patient Account' }}
              <AppIcon v-if="!submitting" name="arrow-right" :size="18" />
            </button>
            <p class="terms">
              By creating an account you agree to our <RouterLink to="/terms">Terms</RouterLink> and
              <RouterLink to="/privacy">Privacy Policy</RouterLink>.
            </p>
          </form>

          <p class="switch">
            Already have an account? <RouterLink :to="APP_ROUTES.login">Log in</RouterLink>
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* Page background: the same photo, softened and washed in pink so the card stands out. It is small (739px),
   so the blur also hides upscaling on large screens. */
.register-page { position: relative; isolation: isolate; padding: 3rem 0 4rem; min-height: calc(100vh - 76px); }
.register-page::before {
  content: ''; position: fixed; inset: -24px; z-index: -2; pointer-events: none;
  background: url('../assets/images/register-bg.jpg') center 30% / cover no-repeat;
  filter: blur(10px) saturate(1.1);
  transform: translateZ(0);
}
.register-page::after {
  content: ''; position: fixed; inset: 0; z-index: -1; pointer-events: none;
  background:
    radial-gradient(circle at 15% 20%, rgba(255, 255, 255, 0.55), transparent 45%),
    linear-gradient(135deg, rgba(253, 227, 239, 0.88) 0%, rgba(255, 244, 249, 0.8) 50%, rgba(232, 222, 255, 0.85) 100%);
}

.shell {
  display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  max-width: 1060px; margin: 0 auto; border-radius: 28px; overflow: hidden;
  background: rgba(255, 255, 255, 0.92); border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 30px 80px rgba(42, 22, 80, 0.22), 0 4px 14px rgba(216, 27, 106, 0.08);
}

/* Photo panel: the whole photo (both faces) on top, fading into a deep purple area that holds the text. */
.visual {
  position: relative; display: flex; flex-direction: column; overflow: hidden; color: #fff;
  background:
    radial-gradient(circle at 85% 90%, rgba(216, 27, 106, 0.45), transparent 55%),
    linear-gradient(180deg, #3a2266 0%, var(--purple-950) 100%);
}
.visual-frame { position: relative; aspect-ratio: 739 / 415; overflow: hidden; }
.visual-photo {
  position: absolute; inset: 0;
  background: url('../assets/images/register-bg.jpg') center / cover no-repeat;
  animation: drift 18s ease-in-out infinite alternate;
}
@keyframes drift { from { transform: scale(1); } to { transform: scale(1.06); } }
.visual-frame::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(58, 34, 102, 0) 55%, #3a2266 100%);
}
.visual-content { position: relative; z-index: 1; display: flex; flex-direction: column; flex: 1; padding: 0.5rem 2.25rem 2.25rem; }
.pill {
  display: inline-flex; align-items: center; gap: 0.4rem; width: fit-content; padding: 0.35rem 0.85rem;
  border-radius: 999px; background: rgba(255, 255, 255, 0.92); color: var(--pink-600); font-weight: 600; font-size: 0.82rem;
}
.visual h2 { margin: 0.9rem 0 1.2rem; font-size: clamp(1.5rem, 2.6vw, 2rem); line-height: 1.2; }
.benefits { list-style: none; margin: 0; padding: 0; display: grid; gap: 1.1rem; }
.trust { margin: auto 0 0; padding-top: 1.5rem; font-size: 0.78rem; color: rgba(255, 255, 255, 0.6); }
.benefits li { display: flex; gap: 0.75rem; align-items: flex-start; }
.benefit-icon {
  flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px;
  background: rgba(255, 255, 255, 0.16); border: 1px solid rgba(255, 255, 255, 0.25); backdrop-filter: blur(6px);
  color: var(--pink-300);
}
.benefits strong { display: block; font-size: 0.95rem; }
.benefit-text { display: block; font-size: 0.85rem; color: rgba(255, 255, 255, 0.8); line-height: 1.45; }

/* Patient or doctor */
.account-types { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin-bottom: 1rem; }
.account-type {
  position: relative; display: flex; gap: 0.6rem; align-items: flex-start; padding: 0.85rem; border-radius: 14px;
  border: 1.5px solid var(--border); background: #fff; cursor: pointer; transition: border-color 0.15s, box-shadow 0.15s;
}
.account-type:hover { border-color: var(--pink-300); }
.account-type.on { border-color: var(--pink-600); background: var(--pink-50); box-shadow: 0 6px 16px rgba(216, 27, 106, 0.12); }
.account-type input { position: absolute; opacity: 0; pointer-events: none; }
.account-type:has(input:focus-visible) { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.type-icon { flex: none; display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px; background: var(--pink-50); color: var(--pink-600); }
.account-type.on .type-icon { background: var(--pink-600); color: #fff; }
.type-text { display: grid; gap: 0.15rem; }
.type-text strong { color: var(--purple-900); font-size: 0.92rem; }
.type-text span { color: var(--muted); font-size: 0.78rem; line-height: 1.4; }
.doctor-note { margin: -0.2rem 0 1rem; font-size: 0.85rem; }

/* Form side */
.form-side { padding: 2.5rem 2.5rem 2rem; }
.form-head h1 { margin: 0; font-size: 1.75rem; }
.form-head .muted { margin: 0.35rem 0 1.5rem; font-size: 0.93rem; line-height: 1.5; }
.form-field input::placeholder { color: #b3aac4; }

.strength { display: flex; align-items: center; gap: 0.75rem; margin: -0.6rem 0 0.4rem; }
.strength-bar { flex: 1; display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
.strength-bar span { height: 6px; border-radius: 3px; background: var(--pink-100); transition: background-color 0.25s; }
.strength-label { flex: none; min-width: 7.5rem; text-align: right; font-size: 0.8rem; font-weight: 600; color: var(--muted); }
.strength[data-level='1'] .on { background: var(--high); }
.strength[data-level='2'] .on { background: #ef8a17; }
.strength[data-level='3'] .on { background: #c9b20a; }
.strength[data-level='4'] .on { background: var(--low); }
.strength:not(.empty)[data-level='0'] .strength-label, .strength[data-level='1'] .strength-label { color: var(--high); }
.strength[data-level='4'] .strength-label { color: var(--low); }

.rules {
  list-style: none; margin: 0 0 0.6rem; padding: 0;
  display: grid; grid-template-columns: 1fr 1fr; gap: 0.3rem 1rem; font-size: 0.82rem; color: var(--muted);
}
.rules li { display: flex; align-items: center; gap: 0.4rem; transition: color 0.2s; }
.tick {
  display: inline-grid; place-items: center; width: 16px; height: 16px; border-radius: 50%;
  border: 1.5px solid var(--border); font-size: 0.65rem; font-weight: 700; transition: all 0.2s;
}
.rules .met { color: var(--low); }
.rules .met .tick { background: var(--low); border-color: var(--low); color: #fff; }

.submit {
  justify-content: center; width: 100%; margin-top: 0.5rem; padding: 0.85rem 1.6rem;
  background: linear-gradient(135deg, var(--pink-600), #b5179e); border-color: transparent;
  box-shadow: 0 10px 24px rgba(216, 27, 106, 0.28);
}
.submit:hover:not(:disabled) { box-shadow: 0 14px 30px rgba(216, 27, 106, 0.36); }
.submit:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 3px; }
.terms { margin: 0.75rem 0 0; text-align: center; font-size: 0.8rem; color: var(--muted); }
.terms a, .switch a { color: var(--pink-600); font-weight: 600; }
.switch { margin: 1.25rem 0 0; padding-top: 1.1rem; border-top: 1px solid var(--border); text-align: center; color: var(--muted); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

@media (max-width: 880px) {
  .shell { grid-template-columns: 1fr; max-width: 560px; }
  .visual-content { padding: 0 1.5rem 1.5rem; }
  .visual h2 { margin-bottom: 0; }
  .benefits, .trust { display: none; }
}
@media (max-width: 520px) {
  .register-page { padding: 1.25rem 0 2.5rem; }
  .shell { border-radius: 22px; }
  .visual h2 { font-size: 1.35rem; }
  .form-head h1 { font-size: 1.5rem; line-height: 1.25; }
  .form-side { padding: 1.75rem 1.25rem 1.5rem; }
  .rules { grid-template-columns: 1fr; }
  .account-types { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .visual-photo { animation: none; }
}
</style>
