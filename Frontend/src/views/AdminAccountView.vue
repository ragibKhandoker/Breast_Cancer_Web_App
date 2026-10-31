<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import AppIcon from '../components/common/AppIcon.vue'
import PasswordField from '../components/auth/PasswordField.vue'
import RecentNotifications from '../components/common/RecentNotifications.vue'
import { authState, updateCurrentUser } from '../auth/authStore'
import { validateConfirm, validateFullName, validatePassword, validatePhone } from '../auth/validation'
import { getAdminAccount } from '../services/adminService'
import { updateProfile, changePassword } from '../services/profileService'
import { errorMessage, fieldErrors } from '../services/http'
import { APP_ROUTES } from '../config/appRoutes'

// The site owner's own admin profile. The backend answers only the owner (ADMIN_EMAIL); every other
// administrator sees "access denied" here.
const account = ref(null)
const loading = ref(true)
const denied = ref(false)
const loadError = ref('')

const profile = computed(() => account.value?.profile)
const initials = computed(() =>
  (profile.value?.fullName ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join(''),
)
const fmt = (iso, withTime = false) =>
  iso ? new Date(iso).toLocaleString(undefined, withTime ? { dateStyle: 'medium', timeStyle: 'short' } : { dateStyle: 'long' }) : 'Never'

const activity = computed(() => {
  const a = account.value?.activity
  if (!a) return []
  return [
    { icon: 'check-circle', label: 'Patients verified', value: a.patientsVerified, tone: 'green' },
    { icon: 'alert-circle', label: 'Sent back for correction', value: a.patientsRejected, tone: 'amber' },
    { icon: 'shield-check', label: 'Passwords reset', value: a.passwordRequestsResolved, tone: 'pink' },
    { icon: 'close', label: 'Requests dismissed', value: a.passwordRequestsDismissed, tone: 'grey' },
  ]
})
const team = computed(() => {
  const t = account.value?.team
  if (!t) return []
  return [
    { label: 'Administrators', value: t.administrators },
    { label: 'Doctors', value: t.doctors },
    { label: 'Patients', value: t.patients },
    { label: 'Disabled accounts', value: t.disabled },
  ]
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    account.value = await getAdminAccount()
    resetDetails()
  } catch (err) {
    if (err.response?.status === 403) denied.value = true
    else loadError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

// ---- Name and phone ----
const details = reactive({ fullName: '', phone: '' })
const detailErrors = reactive({ fullName: '', phone: '' })
const detailsState = reactive({ saving: false, message: '', ok: true })
function resetDetails() {
  details.fullName = profile.value?.fullName ?? ''
  details.phone = profile.value?.phone ?? ''
  detailErrors.fullName = detailErrors.phone = ''
}
const detailsChanged = computed(
  () => !!profile.value && (details.fullName.trim() !== profile.value.fullName || details.phone.trim() !== (profile.value.phone ?? '')),
)

async function saveDetails() {
  detailsState.message = ''
  detailErrors.fullName = validateFullName(details.fullName)
  detailErrors.phone = validatePhone(details.phone)
  if (detailErrors.fullName || detailErrors.phone) return
  detailsState.saving = true
  try {
    const p = profile.value
    // The profile API replaces every field, so the ones not shown here are sent back unchanged.
    const saved = await updateProfile({
      fullName: details.fullName.trim(), phone: details.phone.trim(), dateOfBirth: p.dateOfBirth ?? '',
      gender: p.gender ?? '', address: p.address ?? '', specialization: '', hospital: '',
    })
    account.value = { ...account.value, profile: saved }
    updateCurrentUser(saved)
    resetDetails()
    Object.assign(detailsState, { ok: true, message: 'Saved.' })
  } catch (err) {
    const errors = fieldErrors(err)
    detailErrors.fullName = errors.fullName ?? ''
    detailErrors.phone = errors.phone ?? ''
    Object.assign(detailsState, { ok: false, message: errors.fullName || errors.phone ? '' : errorMessage(err) })
  } finally {
    detailsState.saving = false
  }
}

// ---- Password ----
const pw = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const pwErrors = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const pwState = reactive({ saving: false, message: '', ok: true })

async function savePassword() {
  pwState.message = ''
  pwErrors.currentPassword = pw.currentPassword ? '' : 'Enter your current password.'
  pwErrors.newPassword = validatePassword(pw.newPassword)
  pwErrors.confirm = validateConfirm(pw.newPassword, pw.confirm)
  if (pwErrors.currentPassword || pwErrors.newPassword || pwErrors.confirm) return
  pwState.saving = true
  try {
    await changePassword({ currentPassword: pw.currentPassword, newPassword: pw.newPassword })
    Object.assign(pw, { currentPassword: '', newPassword: '', confirm: '' })
    account.value = { ...account.value, passwordChangedAt: new Date().toISOString() }
    Object.assign(pwState, { ok: true, message: 'Password changed. Your other devices have been signed out.' })
  } catch (err) {
    const errors = fieldErrors(err)
    pwErrors.currentPassword = errors.currentPassword ?? ''
    pwErrors.newPassword = errors.newPassword ?? ''
    Object.assign(pwState, { ok: false, message: errors.currentPassword || errors.newPassword ? '' : errorMessage(err) })
  } finally {
    pwState.saving = false
  }
}

onMounted(() => {
  // Not the owner: say so without asking the server.
  if (authState.user && !authState.user.owner) {
    denied.value = true
    loading.value = false
    return
  }
  load()
})
</script>

<template>
  <section class="container page account-page">
    <p v-if="loading" class="muted">Loading your admin account…</p>

    <div v-else-if="denied" class="card denied" role="alert">
      <span class="lock"><AppIcon name="shield-check" :size="34" /></span>
      <h1>Owner only</h1>
      <p class="muted">This admin account page belongs to the site owner. Nobody else can open it, including other administrators.</p>
      <RouterLink :to="APP_ROUTES.adminDashboard" class="btn btn-primary btn-sm">Back to the admin portal</RouterLink>
    </div>

    <div v-else-if="loadError" class="card denied" role="alert">
      <h1>Admin account</h1>
      <p class="error">{{ loadError }}</p>
      <button type="button" class="btn btn-primary btn-sm" @click="load">Try again</button>
    </div>

    <template v-else-if="account">
      <!-- Owner card -->
      <div v-reveal class="owner-card">
        <div class="avatar-wrap">
          <div class="avatar" aria-hidden="true">{{ initials }}</div>
          <span class="crown" title="Site owner"><AppIcon name="shield-check" :size="16" /></span>
        </div>
        <div class="identity">
          <p class="eyebrow">Admin account · private</p>
          <h1>{{ profile.fullName }}</h1>
          <p class="email"><AppIcon name="mail" :size="15" /> {{ profile.email }}</p>
          <div class="badges">
            <span class="badge solid"><AppIcon name="shield-check" :size="14" /> Site owner</span>
            <span class="badge"><AppIcon name="calendar" :size="14" /> Since {{ fmt(profile.createdAt) }}</span>
            <span class="badge"><AppIcon name="refresh" :size="14" /> Last login {{ fmt(account.lastLoginAt, true) }}</span>
          </div>
        </div>
        <RouterLink :to="APP_ROUTES.adminDashboard" class="btn btn-sm portal-btn">Open admin portal <AppIcon name="arrow-right" :size="16" /></RouterLink>
      </div>

      <!-- Activity -->
      <section v-reveal="80" class="stats" aria-label="Your activity">
        <div v-for="item in activity" :key="item.label" class="stat" :data-tone="item.tone">
          <span class="stat-icon"><AppIcon :name="item.icon" :size="20" /></span>
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </section>

      <!-- Latest notifications, newest first (the same as the bell in the header) -->
      <RecentNotifications v-reveal="120" class="recent-block" :limit="6" />

      <div class="layout">
        <div class="main">
          <!-- Details -->
          <form v-reveal="120" class="card section form-stack" novalidate @submit.prevent="saveDetails">
            <header class="section-head">
              <span class="section-icon"><AppIcon name="users" :size="20" /></span>
              <div>
                <h2>Account details</h2>
                <p class="muted small">Only you can see and change these.</p>
              </div>
            </header>
            <div class="row">
              <div class="form-field">
                <label for="acct-name">Full name</label>
                <input id="acct-name" v-model="details.fullName" autocomplete="name" maxlength="100" :aria-invalid="!!detailErrors.fullName" />
                <p class="field-error" aria-live="polite">{{ detailErrors.fullName }}</p>
              </div>
              <div class="form-field">
                <label for="acct-phone">Phone <span class="optional">optional</span></label>
                <input id="acct-phone" v-model="details.phone" type="tel" autocomplete="tel" maxlength="20" placeholder="+8801712345678" :aria-invalid="!!detailErrors.phone" />
                <p class="field-error" aria-live="polite">{{ detailErrors.phone }}</p>
              </div>
            </div>
            <div class="form-field">
              <label for="acct-email">Email</label>
              <input id="acct-email" :value="profile.email" readonly aria-describedby="acct-email-note" />
              <p id="acct-email-note" class="muted small locked"><AppIcon name="shield-check" :size="14" /> Fixed: the owner is recognised by ADMIN_EMAIL in Backend/.env.</p>
            </div>
            <div v-if="detailsState.message" class="alert" :class="detailsState.ok ? 'success' : 'error'" role="status">{{ detailsState.message }}</div>
            <div class="actions">
              <button type="button" class="btn btn-outline btn-sm" :disabled="!detailsChanged || detailsState.saving" @click="resetDetails">Discard</button>
              <button type="submit" class="btn btn-primary btn-sm" :disabled="!detailsChanged || detailsState.saving">
                {{ detailsState.saving ? 'Saving…' : 'Save changes' }}
              </button>
            </div>
          </form>

          <!-- Password -->
          <form v-reveal="160" class="card section form-stack" novalidate @submit.prevent="savePassword">
            <header class="section-head">
              <span class="section-icon"><AppIcon name="shield-check" :size="20" /></span>
              <div>
                <h2>Password &amp; security</h2>
                <p class="muted small">Last changed: {{ account.passwordChangedAt ? fmt(account.passwordChangedAt, true) : 'never (still the setup password)' }}</p>
              </div>
            </header>
            <PasswordField id="acct-current" v-model="pw.currentPassword" label="Current password" :error="pwErrors.currentPassword" />
            <div class="row">
              <PasswordField id="acct-new" v-model="pw.newPassword" label="New password" autocomplete="new-password" :error="pwErrors.newPassword" />
              <PasswordField id="acct-confirm" v-model="pw.confirm" label="Confirm new password" autocomplete="new-password" :error="pwErrors.confirm" />
            </div>
            <p class="muted small">8–72 characters with an upper-case letter, a lower-case letter and a number.</p>
            <div v-if="pwState.message" class="alert" :class="pwState.ok ? 'success' : 'error'" role="status">{{ pwState.message }}</div>
            <div class="actions">
              <button type="submit" class="btn btn-primary btn-sm" :disabled="pwState.saving">{{ pwState.saving ? 'Changing…' : 'Change password' }}</button>
            </div>
          </form>
        </div>

        <aside class="side">
          <section v-reveal="140" class="card section">
            <header class="section-head compact"><h2>Accounts you look after</h2></header>
            <ul class="team">
              <li v-for="row in team" :key="row.label"><span>{{ row.label }}</span><strong>{{ row.value }}</strong></li>
            </ul>
          </section>
          <section v-reveal="180" class="card section protect">
            <header class="section-head compact"><h2>How your account is protected</h2></header>
            <ul class="checks">
              <li><AppIcon name="check-circle" :size="16" /> Only you can open this page.</li>
              <li><AppIcon name="check-circle" :size="16" /> Other admins cannot view, disable, demote or reset your account.</li>
              <li><AppIcon name="check-circle" :size="16" /> Changing your password signs out your other devices.</li>
            </ul>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.small { font-size: 0.85rem; }
.denied { max-width: 520px; margin: 2rem auto; padding: 2.25rem; text-align: center; display: grid; justify-items: center; gap: 0.25rem; }
.denied h1 { margin: 0.5rem 0 0; }
.denied p { margin: 0.25rem 0 1rem; }
.lock { display: grid; place-items: center; width: 72px; height: 72px; border-radius: 22px; background: linear-gradient(135deg, var(--purple-900), #6b3fb5); color: #fff; }

.owner-card {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 1.5rem; align-items: center; padding: 2rem; border-radius: 26px; color: #fff;
  background:
    radial-gradient(circle at 90% -10%, rgba(255, 255, 255, 0.22), transparent 45%),
    radial-gradient(circle at -5% 120%, rgba(216, 27, 106, 0.6), transparent 50%),
    linear-gradient(120deg, var(--purple-950) 0%, #3f2378 50%, #8e1a8f 100%);
  box-shadow: 0 22px 50px rgba(42, 22, 80, 0.3);
}
.avatar-wrap { position: relative; }
.avatar {
  display: grid; place-items: center; width: 104px; height: 104px; border-radius: 50%; background: #fff; color: var(--purple-900);
  font-size: 2.2rem; font-weight: 700; box-shadow: 0 0 0 5px rgba(255, 255, 255, 0.22), 0 14px 30px rgba(0, 0, 0, 0.3);
}
.crown {
  position: absolute; right: -2px; bottom: 4px; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%;
  background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #fff; border: 3px solid #fff;
}
.eyebrow { margin: 0; font-size: 0.75rem; font-weight: 600; letter-spacing: 0.14em; text-transform: uppercase; color: rgba(255, 255, 255, 0.72); }
.identity h1 { margin: 0.2rem 0; font-size: 1.9rem; color: #fff; overflow-wrap: anywhere; }
.email { display: flex; align-items: center; gap: 0.4rem; margin: 0 0 0.85rem; color: rgba(255, 255, 255, 0.88); overflow-wrap: anywhere; }
.badges { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.8rem; border-radius: 999px; background: rgba(255, 255, 255, 0.14); border: 1px solid rgba(255, 255, 255, 0.3); font-size: 0.8rem; }
.badge.solid { background: linear-gradient(135deg, #fbbf24, #f59e0b); border-color: transparent; color: #3b2300; font-weight: 700; }
.portal-btn { display: inline-flex; align-items: center; gap: 0.4rem; background: #fff; border-color: #fff; color: var(--purple-900); }

.stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; margin-top: 1.25rem; }
.recent-block { margin-top: 1.25rem; }
.stat { display: grid; gap: 0.1rem; padding: 1.1rem 1.2rem; border-radius: 18px; background: #fff; border: 1px solid var(--border); box-shadow: var(--shadow); }
.stat-icon { display: grid; place-items: center; width: 40px; height: 40px; margin-bottom: 0.4rem; border-radius: 12px; background: var(--pink-50); color: var(--pink-600); }
.stat[data-tone='green'] .stat-icon { background: #e8f5e9; color: #16a34a; }
.stat[data-tone='amber'] .stat-icon { background: #fff4e5; color: #c2410c; }
.stat[data-tone='grey'] .stat-icon { background: #f1f1f4; color: #55536b; }
.stat strong { font-size: 1.8rem; color: var(--purple-900); line-height: 1.1; }
.stat span:last-child { font-size: 0.85rem; color: var(--muted); }

.layout { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); gap: 1.25rem; margin-top: 1.25rem; align-items: start; }
.main, .side { display: grid; gap: 1.25rem; }
.section { padding: 1.6rem; border-radius: 22px; }
.section-head { display: flex; gap: 0.85rem; align-items: flex-start; margin-bottom: 1rem; padding-bottom: 0.9rem; border-bottom: 1px solid var(--border); }
.section-head.compact { margin-bottom: 0.6rem; }
.section-head h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.section-head .muted { margin: 0.15rem 0 0; }
.section-icon { flex: none; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 13px; background: linear-gradient(135deg, var(--purple-900), #6b3fb5); color: #fff; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.optional { font-weight: 400; font-size: 0.78rem; color: var(--muted); }
.form-field input[readonly] { background: #f6f4fa; color: var(--muted); cursor: not-allowed; }
.locked { display: flex; align-items: center; gap: 0.35rem; margin: 0.35rem 0 0.5rem; }
.actions { display: flex; justify-content: flex-end; gap: 0.6rem; margin-top: 0.25rem; }

.team { list-style: none; margin: 0; padding: 0; display: grid; }
.team li { display: flex; justify-content: space-between; padding: 0.6rem 0; border-bottom: 1px dashed var(--border); }
.team li:last-child { border-bottom: 0; }
.team strong { color: var(--purple-900); }
.checks { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.6rem; font-size: 0.9rem; line-height: 1.45; }
.checks li { display: flex; gap: 0.5rem; align-items: flex-start; }
.checks :deep(svg) { flex: none; margin-top: 0.15rem; color: #16a34a; }
.protect { background: linear-gradient(160deg, #fff 0%, #f3fbf5 100%); }

@media (max-width: 960px) {
  .owner-card { grid-template-columns: auto minmax(0, 1fr); }
  .portal-btn { grid-column: 1 / -1; justify-content: center; }
  .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .owner-card { grid-template-columns: 1fr; justify-items: center; text-align: center; padding: 1.5rem 1.25rem; }
  .email, .badges { justify-content: center; }
  .avatar { width: 84px; height: 84px; font-size: 1.8rem; }
  .identity h1 { font-size: 1.5rem; }
  .row { grid-template-columns: 1fr; gap: 0; }
  .section { padding: 1.25rem; }
}
</style>
