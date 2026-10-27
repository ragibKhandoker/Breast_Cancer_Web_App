<script setup>
import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PasswordField from '../components/auth/PasswordField.vue'
import PinkRibbon from '../components/common/PinkRibbon.vue'
import { signIn } from '../auth/authStore'
import { safeRedirect } from '../auth/roles'
import { validateEmail } from '../auth/validation'
import { errorMessage, fieldErrors } from '../services/http'
import { APP_ROUTES } from '../config/appRoutes'

const route = useRoute()
const router = useRouter()

// The Register page passes the new email in history state, so it never appears in the URL.
const fields = reactive({ email: history.state?.email ?? '', password: '' })
const errors = reactive({ email: '', password: '' })
const submitting = ref(false)
const failure = ref('')

const notice = computed(() => {
  if (route.query.registered) return 'Your account has been created. Please log in.'
  if (route.query.expired) return 'Your session has ended. Please log in again.'
  if (route.query.redirect) return 'Please log in to continue.'
  return ''
})

function validate() {
  errors.email = validateEmail(fields.email)
  errors.password = fields.password ? '' : 'Password is required.'
  return !errors.email && !errors.password
}

async function submit() {
  if (submitting.value) return
  failure.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    const user = await signIn({ email: fields.email.trim(), password: fields.password })
    await router.replace(safeRedirect(route.query.redirect, user.role, router.resolve))
  } catch (err) {
    fields.password = ''
    const status = err.response?.status
    if (status === 401) failure.value = 'Invalid email or password.'
    else if (status === 400) Object.assign(errors, fieldErrors(err))
    else failure.value = errorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="container page auth-page">
    <div v-reveal class="card auth-card">
      <PinkRibbon :size="40" />
      <h1>Welcome back</h1>
      <p class="muted">Log in to analyse mammograms and see your reports.</p>

      <div v-if="notice" class="alert info" role="status">{{ notice }}</div>

      <form class="form-stack" novalidate @submit.prevent="submit">
        <div class="form-field">
          <label for="login-email">Email</label>
          <input
            id="login-email"
            v-model="fields.email"
            type="email"
            autocomplete="email"
            inputmode="email"
            maxlength="254"
            required
            :aria-invalid="!!errors.email"
            aria-describedby="login-email-error"
            @blur="errors.email = validateEmail(fields.email)"
          />
          <p id="login-email-error" class="field-error" aria-live="polite">{{ errors.email }}</p>
        </div>

        <PasswordField id="login-password" v-model="fields.password" :error="errors.password" />

        <div v-if="failure" class="alert error" role="alert">{{ failure }}</div>

        <button type="submit" class="btn btn-primary submit" :disabled="submitting" :aria-busy="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>
          {{ submitting ? 'Logging in…' : 'Log In' }}
        </button>
      </form>

      <p class="switch">
        New here? <RouterLink :to="APP_ROUTES.register">Create an account</RouterLink>
      </p>
    </div>
  </section>
</template>

<style scoped>
.auth-page { display: flex; justify-content: center; }
.auth-card { width: 100%; max-width: 460px; padding: 2rem; }
.auth-card h1 { margin-top: 0.75rem; }
.auth-card > .muted { margin: 0 0 1.25rem; }
.submit { justify-content: center; margin-top: 0.5rem; }
.switch { margin: 1.25rem 0 0; text-align: center; color: var(--muted); }
.switch a { color: var(--pink-600); font-weight: 600; }
@media (max-width: 520px) { .auth-card { padding: 1.5rem 1.25rem; } }
</style>
