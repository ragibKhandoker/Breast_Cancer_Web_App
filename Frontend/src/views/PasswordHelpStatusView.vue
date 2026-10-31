<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import PinkRibbon from '../components/common/PinkRibbon.vue'
import AppIcon from '../components/common/AppIcon.vue'
import { checkPasswordHelpStatus } from '../services/authService'
import { errorMessage } from '../services/http'
import { APP_ROUTES } from '../config/appRoutes'
import { rememberedHelpCode, rememberHelpCode } from '../auth/passwordHelpCodes'

// The status of a "Forgot password?" request, opened with its secret code (no login). Shows the administrator's
// reply and, once they have checked who the visitor is, the temporary password: the server gives it out only
// once, so it is kept on this page until the visitor leaves.
const code = ref(rememberedHelpCode()?.code ?? '')
const status = ref(null)
const checking = ref(false)
const failure = ref('')
const password = ref('') // kept after the server stops sending it
const copied = ref(false)

const STATES = {
  WAITING: { icon: 'refresh', tone: 'info', title: 'Waiting for an administrator', text: 'Your request is in the queue. This page checks again every 30 seconds.' },
  REPLIED: { icon: 'message', tone: 'info', title: 'The administrator replied', text: 'Read their message below. Your temporary password will appear here once they have confirmed it is you.' },
  PASSWORD_READY: { icon: 'check-circle', tone: 'success', title: 'Your temporary password is ready', text: 'Copy it now: it is shown only once. Log in with it, then choose your own password.' },
  PASSWORD_SHOWN: { icon: 'shield-check', tone: 'muted', title: 'Temporary password already shown', text: 'It was shown once on this page. If you did not get it, send a new request.' },
  PASSWORD_EXPIRED: { icon: 'alert-circle', tone: 'warn', title: 'The temporary password expired', text: 'It was not opened within 24 hours. Send a new request.' },
  RESOLVED: { icon: 'shield-check', tone: 'muted', title: 'Request handled', text: 'An administrator has reset the password for this account. They will contact you, or send a new request.' },
  CLOSED: { icon: 'close', tone: 'muted', title: 'Request closed', text: 'An administrator closed this request. Send a new one if you still need help.' },
}
const view = computed(() => (password.value ? STATES.PASSWORD_READY : STATES[status.value?.state] ?? STATES.WAITING))
const finished = computed(() => ['PASSWORD_SHOWN', 'PASSWORD_EXPIRED', 'RESOLVED', 'CLOSED'].includes(status.value?.state))

async function check() {
  failure.value = ''
  if (!code.value.trim()) {
    failure.value = 'Enter the request code you were given.'
    return
  }
  checking.value = true
  try {
    const result = await checkPasswordHelpStatus(code.value.trim())
    status.value = result
    if (result.temporaryPassword) password.value = result.temporaryPassword
    rememberHelpCode({ code: code.value.trim(), reference: result.reference })
  } catch (err) {
    failure.value = errorMessage(err)
  } finally {
    checking.value = false
  }
}

async function copyPassword() {
  try {
    await navigator.clipboard.writeText(password.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    // Selectable on screen.
  }
}

const fmt = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '')

let timer = null
onMounted(() => {
  if (code.value) check()
  timer = setInterval(() => {
    if (status.value && !password.value && !finished.value && !checking.value) check()
  }, 30000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <section class="container page status-page">
    <div v-reveal class="card status-card">
      <PinkRibbon :size="40" />
      <h1>Password help status</h1>
      <p class="muted">Enter the request code from “Forgot password?” to see what the administrator has done.</p>

      <form class="code-form" novalidate @submit.prevent="check">
        <label for="help-code">Request code</label>
        <div class="code-input">
          <input id="help-code" v-model="code" autocomplete="off" spellcheck="false" placeholder="ABCDE-FGHJK-MNPQR-STUVW"
            :aria-invalid="!!failure" aria-describedby="help-code-error" />
          <button type="submit" class="btn btn-primary" :disabled="checking">
            <span v-if="checking" class="spinner" aria-hidden="true"></span>
            {{ checking ? 'Checking…' : status ? 'Refresh' : 'Check' }}
          </button>
        </div>
        <p id="help-code-error" class="field-error" aria-live="polite">{{ failure }}</p>
      </form>

      <div v-if="status" class="result" :data-tone="view.tone" role="status">
        <div class="result-head">
          <span class="result-icon"><AppIcon :name="view.icon" :size="22" /></span>
          <div>
            <h2>{{ view.title }}</h2>
            <p>{{ view.text }}</p>
          </div>
        </div>
        <p class="ref">Reference <strong>{{ status.reference }}</strong> — give this to the administrator if they call you.</p>

        <div v-if="status.reply" class="reply">
          <span class="reply-from"><AppIcon name="message" :size="15" /> Administrator · {{ fmt(status.repliedAt) }}</span>
          <p>{{ status.reply }}</p>
        </div>

        <div v-if="password" class="password">
          <p class="pw-label">Temporary password <span>shown only once</span></p>
          <div class="pw-row">
            <code>{{ password }}</code>
            <button type="button" class="btn btn-outline btn-sm" @click="copyPassword"><AppIcon name="copy" :size="15" /> {{ copied ? 'Copied' : 'Copy' }}</button>
          </div>
          <p class="pw-note">Works until {{ fmt(status.passwordExpiresAt) }}. After logging in you must choose your own password; nobody else, administrators included, will know it.</p>
          <RouterLink :to="APP_ROUTES.login" class="btn btn-primary login-btn">Log in now <AppIcon name="arrow-right" :size="16" /></RouterLink>
        </div>

        <RouterLink v-else-if="finished" :to="APP_ROUTES.forgotPassword" class="btn btn-outline login-btn">Send a new request</RouterLink>
      </div>

      <p class="switch">
        <RouterLink :to="APP_ROUTES.forgotPassword">New request</RouterLink> · <RouterLink :to="APP_ROUTES.login">Log in</RouterLink>
      </p>
    </div>
  </section>
</template>

<style scoped>
.status-page { display: flex; justify-content: center; }
.status-card { width: 100%; max-width: 560px; padding: 2rem; }
.status-card h1 { margin: 0.75rem 0 0; }
.status-card > .muted { margin: 0.35rem 0 1.2rem; }
.code-form label { font-weight: 600; color: var(--purple-900); font-size: 0.92rem; }
.code-input { display: flex; gap: 0.5rem; margin-top: 0.4rem; }
.code-input input {
  flex: 1; min-width: 0; padding: 0.75rem 0.9rem; border: 1.5px solid var(--border); border-radius: 12px;
  font: 600 0.98rem ui-monospace, Consolas, monospace; letter-spacing: 0.04em; color: var(--purple-900); text-transform: uppercase;
}
.code-input input:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.code-input input[aria-invalid='true'] { border-color: var(--high); }

.result { margin-top: 0.4rem; padding: 1.1rem; border-radius: 18px; border: 1px solid var(--border); background: #fff; border-left: 5px solid var(--pink-600); }
.result[data-tone='success'] { border-left-color: var(--low); }
.result[data-tone='warn'] { border-left-color: #ef8a17; }
.result[data-tone='muted'] { border-left-color: #a8a3b8; }
.result-head { display: flex; gap: 0.8rem; align-items: flex-start; }
.result-icon { flex: none; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 13px; background: var(--pink-50); color: var(--pink-600); }
[data-tone='success'] .result-icon { background: #e8f5e9; color: var(--low); }
[data-tone='warn'] .result-icon { background: var(--warn-bg); color: var(--warn-text); }
[data-tone='muted'] .result-icon { background: #f1f0f5; color: #55536b; }
.result h2 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.result-head p { margin: 0.2rem 0 0; font-size: 0.88rem; color: var(--muted); line-height: 1.45; }
.ref { margin: 0.8rem 0 0; font-size: 0.84rem; }
.ref strong { color: var(--pink-600); font-family: ui-monospace, Consolas, monospace; }
.reply { margin-top: 0.85rem; padding: 0.8rem 0.9rem; border-radius: 14px; background: #f7f3fb; }
.reply-from { display: inline-flex; align-items: center; gap: 0.35rem; font-size: 0.76rem; font-weight: 600; color: var(--purple-900); }
.reply p { margin: 0.35rem 0 0; white-space: pre-line; line-height: 1.5; }
.password { margin-top: 0.9rem; padding: 1rem; border-radius: 14px; background: #ecfdf3; border: 1px solid #a7f3d0; }
.pw-label { margin: 0 0 0.45rem; font-weight: 600; color: #14532d; }
.pw-label span { margin-left: 0.35rem; padding: 0.05rem 0.45rem; border-radius: 6px; background: #fff; color: var(--high); font-size: 0.7rem; }
.pw-row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; }
.pw-row code { padding: 0.5rem 0.8rem; border-radius: 10px; background: #fff; border: 1px solid #a7f3d0; font: 700 1.15rem ui-monospace, Consolas, monospace; color: #14532d; letter-spacing: 0.05em; }
.pw-note { margin: 0.55rem 0 0; font-size: 0.8rem; color: #166534; line-height: 1.45; }
.login-btn { margin-top: 0.9rem; justify-content: center; width: 100%; gap: 0.4rem; }
.switch { margin: 1.25rem 0 0; text-align: center; color: var(--muted); font-size: 0.9rem; }
.switch a { color: var(--pink-600); font-weight: 600; }
@media (max-width: 520px) { .status-card { padding: 1.5rem 1.15rem; } .code-input { flex-direction: column; } }
</style>
