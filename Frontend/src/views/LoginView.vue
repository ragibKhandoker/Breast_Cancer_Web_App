<script setup>
import { reactive, ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PasswordField from '../components/auth/PasswordField.vue'
import PinkRibbon from '../components/common/PinkRibbon.vue'
import AppIcon from '../components/common/AppIcon.vue'
import ToastNotice from '../components/common/ToastNotice.vue'
import { signIn, signOut } from '../auth/authStore'
import { ROLES, safeRedirect, passwordPageFor } from '../auth/roles'
import { validateEmail } from '../auth/validation'
import { errorMessage, fieldErrors } from '../services/http'
import { APP_ROUTES } from '../config/appRoutes'
import { checkPasswordHelpStatus } from '../services/authService'
import { rememberedHelpCode, keepShownPassword, shownPassword, forgetHelpCode } from '../auth/passwordHelpCodes'

const route = useRoute()
const router = useRouter()

// The Register page passes the new email in history state, so it never appears in the URL.
const fields = reactive({ email: history.state?.email ?? '', password: '' })
const errors = reactive({ email: '', password: '' })
const submitting = ref(false)
const failure = ref('')

// "Log in as administrator": only administrator accounts get through, and they land in the admin pages.
// Kept in the URL (?as=admin) so the admin login can be bookmarked.
const asAdmin = ref(route.query.as === 'admin')
watch(asAdmin, (on) => {
  failure.value = ''
  router.replace({ query: { ...route.query, as: on ? 'admin' : undefined } })
})

function adminHome(user) {
  // The site owner opens their own admin account page; other administrators the portal.
  const target = route.query.redirect && safeRedirect(route.query.redirect, user.role, router.resolve)
  if (target && target !== APP_ROUTES.adminDashboard) return target
  return user.owner ? APP_ROUTES.adminAccount : APP_ROUTES.adminDashboard
}

// Why the user is here, shown as a notification over the page (not inside the form).
const notice = computed(() => {
  if (route.query.registered === 'doctor') {
    return { tone: 'success', title: 'Account created', text: 'Please wait until an admin approves your account.' }
  }
  if (route.query.registered) return { tone: 'success', title: 'Account created', text: 'Your account has been created. Please log in.' }
  if (route.query.expired) return { tone: 'warning', title: 'Session ended', text: 'Your session has ended. Please log in again.' }
  if (route.query.redirect) return { tone: 'info', title: 'Login required', text: 'Please log in to continue.' }
  return null
})
const noticeOpen = ref(!!notice.value)

// The photo panel: what the site offers, and what an administrator gets.
const HIGHLIGHTS = [
  { icon: 'microscope', title: 'AI mammogram analysis', text: 'An estimate with a heatmap in seconds.' },
  { icon: 'calendar', title: 'Book a Breast Surgeon', text: 'Verified surgeons, live free slots.' },
  { icon: 'shield-check', title: 'Private by design', text: 'Only you and the doctors you choose see your reports.' },
]
const ADMIN_HIGHLIGHTS = [
  { icon: 'users', title: 'Verify patients and doctors', text: 'Check details before anyone can share reports.' },
  { icon: 'award', title: 'Approve Breast Surgeons', text: 'Review credentials before they go live.' },
  { icon: 'shield-check', title: 'Password help', text: 'Answer locked-out users with a temporary password.' },
]
const highlights = computed(() => (asAdmin.value ? ADMIN_HIGHLIGHTS : HIGHLIGHTS))

function validate() {
  errors.email = validateEmail(fields.email)
  errors.password = fields.password ? '' : 'Password is required.'
  return !errors.email && !errors.password
}

// ---- A "Forgot password?" request sent from this browser ----
// The Login page checks it by itself: while it waits, a small note says so; once an administrator approves it,
// the temporary password appears here (only on this browser) and fills the form, so the user just logs in.
const reset = reactive({ state: '', password: '', copied: false })
let resetTimer = null

async function checkReset() {
  const saved = rememberedHelpCode()
  if (!saved) return
  const kept = shownPassword()
  if (kept) return showResetPassword(kept, saved.email)
  try {
    const result = await checkPasswordHelpStatus(saved.code)
    if (result.temporaryPassword) {
      keepShownPassword(result.temporaryPassword)
      showResetPassword(result.temporaryPassword, saved.email)
    } else if (result.state === 'WAITING' || result.state === 'REPLIED') {
      reset.state = 'waiting'
    } else {
      // Shown before, expired, or closed: nothing more to show here.
      reset.state = result.state === 'PASSWORD_SHOWN' ? 'shown' : 'closed'
      forgetHelpCode()
    }
  } catch {
    // Server unreachable: try again on the next check.
  }
}

function showResetPassword(password, email) {
  reset.state = 'ready'
  reset.password = password
  if (email && !fields.email) fields.email = email
  fields.password = password
  clearInterval(resetTimer)
}

async function copyResetPassword() {
  try {
    await navigator.clipboard.writeText(reset.password)
    reset.copied = true
    setTimeout(() => (reset.copied = false), 2000)
  } catch {
    // It is on screen.
  }
}

onMounted(() => {
  checkReset()
  // While waiting, look again every 20 seconds so the password appears without a reload.
  resetTimer = setInterval(() => reset.state === 'waiting' && checkReset(), 20000)
})
onBeforeUnmount(() => clearInterval(resetTimer))

async function submit() {
  if (submitting.value) return
  failure.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    const user = await signIn({ email: fields.email.trim(), password: fields.password })
    if (reset.state === 'ready') forgetHelpCode()
    if (asAdmin.value && user.role !== ROLES.ADMIN) {
      await signOut()
      fields.password = ''
      failure.value = 'This is not an administrator account. Untick “Log in as administrator” to log in normally.'
      return
    }
    // After an administrator's reset the user logs in with a temporary password and is sent to choose their own.
    if (user.mustChangePassword) await router.replace({ path: passwordPageFor(user.role), hash: '#change-password' })
    else if (asAdmin.value) await router.replace(adminHome(user))
    else await router.replace(safeRedirect(route.query.redirect, user.role, router.resolve))
  } catch (err) {
    fields.password = ''
    const status = err.response?.status
    const code = err.response?.data?.code
    if (code === 'temporary_password_expired' || code === 'temporary_password_used') {
      failure.value = errorMessage(err)
      // That temporary password is finished: stop offering it, and point to a new request.
      if (reset.state === 'ready') {
        forgetHelpCode()
        Object.assign(reset, { state: 'shown', password: '' })
      }
    } else if (status === 401) failure.value = 'Invalid email or password.'
    else if (status === 400) Object.assign(errors, fieldErrors(err))
    else failure.value = errorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="login-page" :class="{ admin: asAdmin }">
    <ToastNotice v-if="notice" v-model:open="noticeOpen" :tone="notice.tone" :title="notice.title" :message="notice.text" />
    <div class="container">
      <div v-reveal class="shell">
        <!-- Photo panel: the photo on top, a deep purple area below with what the site offers. -->
        <aside class="visual" aria-hidden="true">
          <div class="visual-frame"><div class="visual-photo"></div></div>
          <div class="visual-content">
            <span class="pill"><PinkRibbon :size="16" /> {{ asAdmin ? 'Administrator area' : 'BreastCare' }}</span>
            <h2>{{ asAdmin ? 'Keep the community safe and verified.' : 'Your breast health, in caring hands.' }}</h2>
            <ul class="highlights">
              <li v-for="item in highlights" :key="item.title">
                <span class="hl-icon"><AppIcon :name="item.icon" :size="19" /></span>
                <span>
                  <strong>{{ item.title }}</strong>
                  <span class="hl-text">{{ item.text }}</span>
                </span>
              </li>
            </ul>
            <p class="trust"><AppIcon name="shield-check" :size="14" /> Secure sign-in · Encrypted session cookie</p>
          </div>
        </aside>

        <div class="form-side">
          <header class="form-head">
            <span v-if="asAdmin" class="admin-mark" aria-hidden="true"><AppIcon name="shield-check" :size="24" /></span>
            <span v-else class="brand-mark" aria-hidden="true"><PinkRibbon :size="26" /></span>
            <h1>{{ asAdmin ? 'Admin login' : 'Welcome back' }}</h1>
            <p class="muted">
              {{ asAdmin ? 'For site administrators. You will go straight to the admin pages.' : 'Log in to see your reports, appointments and profile.' }}
            </p>
          </header>

          <!-- A password reset requested from this browser -->
          <div v-if="reset.state === 'ready'" class="reset-card ready" role="status">
            <p class="reset-title"><AppIcon name="check-circle" :size="18" /> Your password reset was approved</p>
            <p class="reset-text">Your temporary password (shown only once, keep it until you have logged in):</p>
            <div class="reset-pw">
              <code>{{ reset.password }}</code>
              <button type="button" class="btn btn-outline btn-sm" @click="copyResetPassword"><AppIcon name="copy" :size="14" /> {{ reset.copied ? 'Copied' : 'Copy' }}</button>
            </div>
            <p class="reset-text">It is already filled in below. Press <strong>Log In</strong>, then choose your own password.</p>
          </div>
          <div v-else-if="reset.state === 'waiting'" class="reset-card waiting" role="status">
            <p class="reset-title"><AppIcon name="refresh" :size="17" /> Password reset requested</p>
            <p class="reset-text">Waiting for an administrator to approve it. Your temporary password will appear right here; this page checks by itself.</p>
          </div>
          <div v-else-if="reset.state === 'shown'" class="reset-card closed" role="status">
            <p class="reset-text">A temporary password works only once, and yours has already been shown or used. Need to log in again without your password? <RouterLink :to="APP_ROUTES.forgotPassword">Ask the administrator again</RouterLink>.</p>
          </div>
          <div v-else-if="reset.state === 'closed'" class="reset-card closed" role="status">
            <p class="reset-text">Your earlier password request is closed. Still locked out? <RouterLink :to="APP_ROUTES.forgotPassword">Ask again</RouterLink>.</p>
          </div>


          <form class="form-stack" novalidate @submit.prevent="submit">
            <div class="form-field">
              <label for="login-email">Email address</label>
              <div class="input-icon">
                <AppIcon name="mail" :size="18" class="lead" />
                <input
                  id="login-email"
                  v-model="fields.email"
                  type="email"
                  autocomplete="email"
                  inputmode="email"
                  maxlength="254"
                  placeholder="name@example.com"
                  required
                  :aria-invalid="!!errors.email"
                  aria-describedby="login-email-error"
                  @blur="errors.email = validateEmail(fields.email)"
                />
              </div>
              <p id="login-email-error" class="field-error" aria-live="polite">{{ errors.email }}</p>
            </div>

            <div class="password-block">
              <PasswordField id="login-password" v-model="fields.password" :error="errors.password" />
              <RouterLink :to="APP_ROUTES.forgotPassword" class="forgot">Forgot password?</RouterLink>
            </div>

            <label class="admin-toggle" :class="{ on: asAdmin }">
              <input v-model="asAdmin" type="checkbox" />
              <span class="box" aria-hidden="true"><AppIcon name="check-circle" :size="14" /></span>
              <span class="toggle-text">
                <strong>Log in as administrator</strong>
                <span>Only administrator accounts can use this.</span>
              </span>
              <AppIcon name="shield-check" :size="20" class="toggle-icon" />
            </label>

            <div v-if="failure" class="alert error" role="alert">{{ failure }}</div>

            <button type="submit" class="btn btn-primary submit" :disabled="submitting" :aria-busy="submitting">
              <span v-if="submitting" class="spinner" aria-hidden="true"></span>
              {{ submitting ? 'Logging in…' : asAdmin ? 'Log in to admin' : 'Log In' }}
              <AppIcon v-if="!submitting" name="arrow-right" :size="18" />
            </button>
          </form>

          <p class="tab-note"><AppIcon name="users" :size="14" /> Each browser tab keeps its own login: you can use a different account in another tab.</p>
          <p v-if="asAdmin" class="switch">Administrator accounts are created by the site owner.</p>
          <template v-else>
            <div class="divider"><span>New to BreastCare?</span></div>
            <p class="switch-text">Create an account</p>
            <div class="join">
              <RouterLink :to="APP_ROUTES.register" class="join-card">
                <AppIcon name="heart" :size="18" />
                <span><strong>As a patient</strong><small>Analyse scans, book surgeons</small></span>
              </RouterLink>
              <RouterLink :to="{ path: APP_ROUTES.register, query: { as: 'doctor' } }" class="join-card">
                <AppIcon name="award" :size="18" />
                <span><strong>As a doctor</strong><small>Set up your doctor profile</small></span>
              </RouterLink>
            </div>
          </template>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* Password reset card */
.reset-card { margin: -0.4rem 0 1.1rem; padding: 0.9rem 1rem; border-radius: 16px; border: 1px solid var(--border); background: #fff; }
.reset-card.ready { background: #ecfdf3; border-color: #a7f3d0; }
.reset-card.waiting { background: var(--pink-50); border-color: var(--pink-300); }
.reset-card.closed { background: #f7f5fa; }
.reset-title { display: flex; align-items: center; gap: 0.4rem; margin: 0 0 0.3rem; font-weight: 700; color: var(--purple-900); font-size: 0.92rem; }
.ready .reset-title { color: #14532d; }
.waiting .reset-title :deep(svg) { color: var(--pink-600); animation: spin-slow 2.4s linear infinite; }
@keyframes spin-slow { to { transform: rotate(360deg); } }
.reset-text { margin: 0; font-size: 0.85rem; line-height: 1.5; color: var(--text); }
.reset-text a { color: var(--pink-600); font-weight: 600; }
.reset-pw { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin: 0.5rem 0; }
.reset-pw code { padding: 0.45rem 0.75rem; border-radius: 10px; background: #fff; border: 1px solid #a7f3d0; color: #14532d; font: 700 1.1rem ui-monospace, Consolas, monospace; letter-spacing: 0.05em; }
@media (prefers-reduced-motion: reduce) { .waiting .reset-title :deep(svg) { animation: none; } }

/* Page background: the photo, softened and washed in pink (purple for the admin login). */
.login-page { position: relative; isolation: isolate; padding: 3rem 0 4rem; min-height: calc(100vh - 76px); }
.login-page::before {
  content: ''; position: fixed; inset: -24px; z-index: -2; pointer-events: none;
  background: url('../assets/images/doctor-patient-bg.jpg') center 30% / cover no-repeat;
  filter: blur(12px) saturate(1.1);
  transform: translateZ(0);
}
.login-page::after {
  content: ''; position: fixed; inset: 0; z-index: -1; pointer-events: none;
  background:
    radial-gradient(circle at 85% 15%, rgba(255, 255, 255, 0.55), transparent 45%),
    linear-gradient(135deg, rgba(253, 227, 239, 0.9) 0%, rgba(255, 244, 249, 0.82) 50%, rgba(232, 222, 255, 0.88) 100%);
  transition: background 0.3s;
}
.login-page.admin::after {
  background: linear-gradient(135deg, rgba(232, 222, 255, 0.92) 0%, rgba(246, 240, 255, 0.86) 50%, rgba(253, 227, 239, 0.88) 100%);
}

.shell {
  display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  max-width: 1000px; margin: 0 auto; border-radius: 28px; overflow: hidden;
  background: rgba(255, 255, 255, 0.94); border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 30px 80px rgba(42, 22, 80, 0.22), 0 4px 14px rgba(216, 27, 106, 0.08);
  transition: box-shadow 0.3s;
}
.admin .shell { box-shadow: 0 0 0 4px rgba(107, 63, 181, 0.12), 0 30px 80px rgba(42, 22, 80, 0.26); }

/* Photo panel */
.visual {
  position: relative; display: flex; flex-direction: column; overflow: hidden; color: #fff;
  background:
    radial-gradient(circle at 85% 90%, rgba(216, 27, 106, 0.45), transparent 55%),
    linear-gradient(180deg, #3a2266 0%, var(--purple-950) 100%);
}
.visual-frame { position: relative; aspect-ratio: 16 / 10; overflow: hidden; }
.visual-photo {
  position: absolute; inset: 0;
  background: url('../assets/images/doctor-patient-bg.jpg') center / cover no-repeat;
  animation: drift 18s ease-in-out infinite alternate;
}
@keyframes drift { from { transform: scale(1); } to { transform: scale(1.06); } }
.visual-frame::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(58, 34, 102, 0) 50%, #3a2266 100%); }
.visual-content { position: relative; z-index: 1; display: flex; flex-direction: column; flex: 1; padding: 0.25rem 2.25rem 2rem; }
.pill {
  display: inline-flex; align-items: center; gap: 0.4rem; width: fit-content; padding: 0.35rem 0.85rem;
  border-radius: 999px; background: rgba(255, 255, 255, 0.92); color: var(--pink-600); font-weight: 600; font-size: 0.82rem;
}
.visual h2 { margin: 0.85rem 0 1.2rem; font-size: clamp(1.45rem, 2.4vw, 1.9rem); line-height: 1.2; }
.highlights { list-style: none; margin: 0; padding: 0; display: grid; gap: 1rem; }
.highlights li { display: flex; gap: 0.75rem; align-items: flex-start; }
.hl-icon {
  flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px;
  background: rgba(255, 255, 255, 0.16); border: 1px solid rgba(255, 255, 255, 0.25); color: var(--pink-300);
}
.highlights strong { display: block; font-size: 0.95rem; }
.hl-text { display: block; font-size: 0.84rem; color: rgba(255, 255, 255, 0.78); line-height: 1.45; }
.trust { display: flex; align-items: center; gap: 0.4rem; margin: auto 0 0; padding-top: 1.5rem; font-size: 0.78rem; color: rgba(255, 255, 255, 0.62); }

/* Form side */
.form-side { padding: 2.5rem 2.5rem 2rem; display: flex; flex-direction: column; justify-content: center; }
.brand-mark, .admin-mark {
  display: grid; place-items: center; width: 50px; height: 50px; border-radius: 16px;
}
.brand-mark { background: var(--pink-50); border: 1px solid var(--border); }
.admin-mark { color: #fff; background: linear-gradient(135deg, var(--purple-900), #6b3fb5 60%, #b5179e); box-shadow: 0 10px 22px rgba(42, 22, 80, 0.28); }
.form-head h1 { margin: 0.9rem 0 0; font-size: 1.85rem; }
.form-head .muted { margin: 0.35rem 0 1.4rem; font-size: 0.94rem; line-height: 1.5; }

.input-icon { position: relative; }
.input-icon .lead { position: absolute; top: 50%; left: 0.95rem; transform: translateY(-50%); color: var(--pink-300); pointer-events: none; transition: color 0.15s; }
.input-icon input { padding-left: 2.75rem; }
.input-icon:focus-within .lead { color: var(--pink-600); }
.form-field input::placeholder { color: #b3aac4; }

.password-block { display: grid; }
.forgot { justify-self: end; margin-top: -0.55rem; margin-bottom: 0.3rem; color: var(--pink-600); font-size: 0.86rem; font-weight: 500; }
.forgot:hover { text-decoration: underline; }

/* "Log in as administrator", drawn as a card so it is easy to spot and tap. */
.admin-toggle {
  position: relative; display: flex; align-items: center; gap: 0.75rem; margin: 0.3rem 0 0.2rem; padding: 0.7rem 0.9rem;
  border: 1.5px solid var(--border); border-radius: 14px; background: #fffafc; cursor: pointer; user-select: none;
  transition: border-color 0.2s, background-color 0.2s;
}
.admin-toggle:hover { border-color: var(--pink-300); }
.admin-toggle input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.box {
  flex: none; display: grid; place-items: center; width: 22px; height: 22px; border: 2px solid var(--pink-300); border-radius: 7px;
  background: #fff; color: transparent; transition: background-color 0.15s, border-color 0.15s, color 0.15s;
}
.toggle-text { flex: 1; display: grid; line-height: 1.3; }
.toggle-text strong { color: var(--purple-900); font-size: 0.92rem; }
.toggle-text span { color: var(--muted); font-size: 0.79rem; }
.toggle-icon { flex: none; color: var(--pink-300); }
.admin-toggle.on { border-color: #6b3fb5; background: #f6f0ff; }
.admin-toggle.on .box { background: #6b3fb5; border-color: #6b3fb5; color: #fff; }
.admin-toggle.on .toggle-icon { color: #6b3fb5; }
.admin-toggle:has(input:focus-visible) { outline: 3px solid var(--pink-300); outline-offset: 2px; }

.submit {
  justify-content: center; gap: 0.5rem; width: 100%; margin-top: 0.6rem; padding: 0.85rem 1.6rem;
  background: linear-gradient(135deg, var(--pink-600), #b5179e); border-color: transparent;
  box-shadow: 0 10px 24px rgba(216, 27, 106, 0.28); transition: transform 0.15s, box-shadow 0.15s;
}
.submit:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 14px 30px rgba(216, 27, 106, 0.36); }
.submit:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 3px; }
.admin .submit { background: linear-gradient(120deg, var(--purple-900), #6b3fb5); box-shadow: 0 10px 24px rgba(42, 22, 80, 0.3); }

.tab-note { display: flex; align-items: center; gap: 0.35rem; margin: 0.9rem 0 0; font-size: 0.78rem; color: var(--muted); }

/* New here */
.divider { display: flex; align-items: center; gap: 0.75rem; margin: 1.6rem 0 0.2rem; color: var(--muted); font-size: 0.85rem; }
.divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--border); }
.switch-text { margin: 0 0 0.7rem; text-align: center; color: var(--purple-900); font-weight: 600; font-size: 0.92rem; }
.join { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.join-card {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.7rem 0.8rem; border-radius: 14px;
  border: 1.5px solid var(--border); background: #fff; color: var(--pink-600); transition: border-color 0.15s, transform 0.15s, box-shadow 0.15s;
}
.join-card:hover { border-color: var(--pink-600); transform: translateY(-1px); box-shadow: 0 8px 18px rgba(216, 27, 106, 0.12); }
.join-card:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.join-card :deep(svg) { flex: none; }
.join-card span { display: grid; line-height: 1.25; }
.join-card strong { color: var(--purple-900); font-size: 0.88rem; }
.join-card small { color: var(--muted); font-size: 0.74rem; }
.switch { margin: 1.4rem 0 0; padding-top: 1.1rem; border-top: 1px solid var(--border); text-align: center; color: var(--muted); font-size: 0.9rem; }

@media (max-width: 880px) {
  .shell { grid-template-columns: 1fr; max-width: 520px; }
  .visual-frame { aspect-ratio: 16 / 7; }
  .visual-content { padding: 0 1.5rem 1.4rem; }
  .visual h2 { margin-bottom: 0; }
  .highlights, .trust { display: none; }
}
@media (max-width: 520px) {
  .login-page { padding: 1.25rem 0 2.5rem; }
  .shell { border-radius: 22px; }
  .visual h2 { font-size: 1.3rem; }
  .form-side { padding: 1.75rem 1.25rem 1.5rem; }
  .form-head h1 { font-size: 1.55rem; }
  .join { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) {
  .visual-photo { animation: none; }
  .submit, .join-card { transition: none; }
}
</style>
