<script setup>
import { reactive, ref } from 'vue'
import PinkRibbon from '../components/common/PinkRibbon.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { validateEmail } from '../auth/validation'
import { requestPasswordHelp } from '../services/authService'
import { errorMessage, fieldErrors } from '../services/http'
import { APP_ROUTES } from '../config/appRoutes'

// The site sends no email: the request goes to an administrator, who resets the password and contacts the user.
const fields = reactive({ email: '', message: '' })
const errors = reactive({ email: '', message: '' })
const sending = ref(false)
const sent = ref(false)
const failure = ref('')

async function submit() {
  if (sending.value) return
  failure.value = ''
  errors.email = validateEmail(fields.email)
  errors.message = fields.message.trim().length > 500 ? 'Message must be 500 characters or fewer.' : ''
  if (errors.email || errors.message) return
  sending.value = true
  try {
    await requestPasswordHelp({ email: fields.email.trim(), message: fields.message.trim() })
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
          If <strong>{{ fields.email.trim() }}</strong> has an account, an administrator will reset the password and
          contact you with a temporary one. You will be asked to choose your own password after logging in.
        </div>
        <RouterLink :to="APP_ROUTES.login" class="btn btn-primary submit">Back to login</RouterLink>
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
      </template>

      <p class="switch">Remembered it? <RouterLink :to="APP_ROUTES.login">Log in</RouterLink></p>
    </div>
  </section>
</template>

<style scoped>
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
