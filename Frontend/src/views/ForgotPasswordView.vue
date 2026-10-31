<script setup>
import { reactive, ref } from 'vue'
import PinkRibbon from '../components/common/PinkRibbon.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { validateEmail } from '../auth/validation'
import { requestPasswordHelp } from '../services/authService'
import { errorMessage, fieldErrors } from '../services/http'
import { APP_ROUTES } from '../config/appRoutes'
import { rememberHelpCode } from '../auth/passwordHelpCodes'

// The site sends no email: the request goes to an administrator. The visitor gets a secret request code that
// opens the request's status page, where the administrator's reply (and, once they have checked who the visitor
// is, a temporary password) appears.
const fields = reactive({ email: '', message: '' })
const errors = reactive({ email: '', message: '' })
const sending = ref(false)
const sent = ref(false)
const failure = ref('')
const ticket = ref(null) // { code, reference }
const copied = ref(false)

async function copyCode() {
  try {
    await navigator.clipboard.writeText(ticket.value.code)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    // The code is on screen to write down.
  }
}

async function submit() {
  if (sending.value) return
  failure.value = ''
  errors.email = validateEmail(fields.email)
  errors.message = fields.message.trim().length > 500 ? 'Message must be 500 characters or fewer.' : ''
  if (errors.email || errors.message) return
  sending.value = true
  try {
    const answer = await requestPasswordHelp({ email: fields.email.trim(), message: fields.message.trim() })
    ticket.value = answer?.code ? { code: answer.code, reference: answer.reference } : null
    if (ticket.value) rememberHelpCode(ticket.value)
    sent.value = true
  } catch (err) {
    const matched = fieldErrors(err)
    if (matched.email || matched.message) Object.assign(errors, matched)
    else failure.value = errorMessage(err)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="container page auth-page">
    <div v-reveal class="card auth-card">
      <PinkRibbon :size="40" />

      <template v-if="sent">
        <h1>Request sent</h1>
        <div class="alert success" role="status">
          If <strong>{{ fields.email.trim() }}</strong> has an account, an administrator will check your request.
        </div>
        <div v-if="ticket" class="ticket">
          <p class="ticket-label">Your request code <span>keep it secret</span></p>
          <div class="code-row">
            <code class="code">{{ ticket.code }}</code>
            <button type="button" class="btn btn-outline btn-sm" @click="copyCode"><AppIcon name="copy" :size="15" /> {{ copied ? 'Copied' : 'Copy' }}</button>
          </div>
          <p class="ticket-ref">Reference <strong>{{ ticket.reference }}</strong> — tell the administrator this if they call you.</p>
          <ul class="steps">
            <li>Open the status page with this code to see the administrator's reply.</li>
            <li>After they confirm it is you, your temporary password appears there <strong>once</strong>.</li>
            <li>Log in with it and choose your own password.</li>
          </ul>
          <p class="hint">This browser remembers the code. On another device, type it in.</p>
        </div>
        <RouterLink :to="APP_ROUTES.passwordHelpStatus" class="btn btn-primary submit">Check my request status</RouterLink>
        <RouterLink :to="APP_ROUTES.login" class="btn btn-outline submit">Back to login</RouterLink>
      </template>

      <template v-else>
        <h1>Forgot your password?</h1>
        <p class="muted">Tell us your account email. An administrator will reset your password and get in touch.</p>

        <form class="form-stack" novalidate @submit.prevent="submit">
          <div class="form-field">
            <label for="forgot-email">Email</label>
            <input id="forgot-email" v-model="fields.email" type="email" autocomplete="email" inputmode="email" maxlength="254" required
              :aria-invalid="!!errors.email" aria-describedby="forgot-email-error" @blur="errors.email = validateEmail(fields.email)" />
            <p id="forgot-email-error" class="field-error" aria-live="polite">{{ errors.email }}</p>
          </div>
          <div class="form-field">
            <label for="forgot-message">Message <span class="optional">optional</span></label>
            <textarea id="forgot-message" v-model="fields.message" rows="3" maxlength="500"
              placeholder="Anything that helps the administrator, e.g. a phone number to reach you"
              :aria-invalid="!!errors.message" aria-describedby="forgot-message-error"></textarea>
            <p id="forgot-message-error" class="field-error" aria-live="polite">{{ errors.message }}</p>
          </div>

          <div v-if="failure" class="alert error" role="alert">{{ failure }}</div>

          <button type="submit" class="btn btn-primary submit" :disabled="sending" :aria-busy="sending">
            <span v-if="sending" class="spinner" aria-hidden="true"></span>
            {{ sending ? 'Sending…' : 'Ask for a password reset' }}
          </button>
        </form>

        <p class="how"><AppIcon name="shield-check" :size="16" /> For your safety, only an administrator can reset a password.</p>
        <p class="how">Already asked? <RouterLink :to="APP_ROUTES.passwordHelpStatus">Check your request status</RouterLink></p>
      </template>

      <p class="switch">Remembered it? <RouterLink :to="APP_ROUTES.login">Log in</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
.ticket { margin: 0.75rem 0 1rem; padding: 1rem 1.1rem; border-radius: 16px; border: 1.5px dashed var(--pink-300); background: var(--pink-50); }
.ticket-label { margin: 0 0 0.45rem; font-weight: 600; color: var(--purple-900); font-size: 0.9rem; }
.ticket-label span { margin-left: 0.35rem; padding: 0.05rem 0.45rem; border-radius: 6px; background: #fff; color: var(--high); font-size: 0.7rem; }
.code-row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; }
.code { padding: 0.45rem 0.7rem; border-radius: 10px; background: #fff; border: 1px solid var(--border); color: var(--purple-900); font: 700 1.02rem/1.2 ui-monospace, Consolas, monospace; letter-spacing: 0.04em; }
.ticket-ref { margin: 0.6rem 0 0; font-size: 0.86rem; }
.ticket-ref strong { color: var(--pink-600); font-family: ui-monospace, Consolas, monospace; }
.steps { margin: 0.6rem 0 0; padding-left: 1.1rem; font-size: 0.84rem; line-height: 1.5; display: grid; gap: 0.2rem; }
.hint { margin: 0.5rem 0 0; font-size: 0.78rem; color: var(--muted); }
.submit + .submit { margin-top: 0.6rem; }
.auth-page { display: flex; justify-content: center; }
.auth-card { width: 100%; max-width: 480px; padding: 2rem; }
.auth-card h1 { margin-top: 0.75rem; }
.auth-card > .muted { margin: 0 0 1.25rem; }
.optional { font-weight: 400; font-size: 0.78rem; color: var(--muted); }
.form-field textarea {
  width: 100%; padding: 0.8rem 1rem; border: 1.5px solid var(--border); border-radius: 12px; resize: vertical;
  background: #fff; color: var(--text); font: 400 1rem 'Poppins', sans-serif;
}
.form-field textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.submit { justify-content: center; margin-top: 0.5rem; width: 100%; }
.alert { line-height: 1.55; }
.how { display: flex; align-items: center; gap: 0.4rem; margin: 1rem 0 0; font-size: 0.85rem; color: var(--muted); }
.how :deep(svg) { color: var(--pink-600); flex: none; }
.switch { margin: 1.25rem 0 0; text-align: center; color: var(--muted); }
.switch a { color: var(--pink-600); font-weight: 600; }
@media (max-width: 520px) { .auth-card { padding: 1.5rem 1.25rem; } }
</style>
