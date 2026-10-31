<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import TempPasswordNotice from './TempPasswordNotice.vue'
import { listPasswordRequests, setPasswordRequestStatus, resetPassword, replyToPasswordRequest, sendPasswordToRequest } from '../../services/adminService'
import { errorMessage, fieldErrors } from '../../services/http'

// "Forgot password?" requests from the Login page. The site sends no email. The visitor has a secret request code
// that opens a status page; here the administrator replies to it and, after checking who the person is (for
// example by phone, matching the reference), sends a temporary password that the status page shows once.
// "Reset & tell by phone" is still there for requests without a status page.
const emit = defineEmits(['counts-changed'])

const FILTERS = [
  { id: 'OPEN', label: 'Open' },
  { id: 'RESOLVED', label: 'Resolved' },
  { id: 'DISMISSED', label: 'Dismissed' },
  { id: '', label: 'All' },
]

const filter = ref('OPEN')
const requests = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref('')
const rowError = reactive({})
const issued = reactive({}) // request id -> temporary password

async function load() {
  loading.value = true
  error.value = ''
  try {
    requests.value = await listPasswordRequests(filter.value || undefined)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}
watch(filter, load)

const ago = (iso) => {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  if (minutes < 1440) return `${Math.round(minutes / 60)} h ago`
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
const openCount = computed(() => requests.value.filter((r) => r.status === 'OPEN').length)

async function doReset(req) {
  if (!confirm(`Reset the password for ${req.email}? Their current password stops working and they are signed out everywhere.`)) return
  busyId.value = req.id
  rowError[req.id] = ''
  try {
    const result = await resetPassword(req.userId)
    issued[req.id] = result.temporaryPassword
    Object.assign(req, { status: 'RESOLVED', handledAt: new Date().toISOString() })
    emit('counts-changed')
  } catch (err) {
    rowError[req.id] = errorMessage(err)
  } finally {
    busyId.value = ''
  }
}

// One open form per request: 'reply' or 'send'.
const form = reactive({ id: '', mode: '', message: '', confirmed: false, error: '' })
function openForm(req, mode) {
  Object.assign(form, { id: req.id, mode, message: mode === 'reply' ? req.reply ?? '' : '', confirmed: false, error: '' })
}
function closeForm() {
  Object.assign(form, { id: '', mode: '', message: '', confirmed: false, error: '' })
}

async function sendReply(req) {
  form.error = ''
  if (!form.message.trim()) {
    form.error = 'Write a message for the user.'
    return
  }
  busyId.value = req.id
  try {
    Object.assign(req, await replyToPasswordRequest(req.id, form.message.trim()))
    closeForm()
  } catch (err) {
    form.error = fieldErrors(err).message || errorMessage(err)
  } finally {
    busyId.value = ''
  }
}

async function sendPassword(req) {
  form.error = ''
  if (!form.confirmed) {
    form.error = 'Tick the box once you have confirmed who this is.'
    return
  }
  busyId.value = req.id
  try {
    Object.assign(req, await sendPasswordToRequest(req.id, { message: form.message.trim(), identityConfirmed: true }))
    closeForm()
    emit('counts-changed')
  } catch (err) {
    form.error = fieldErrors(err).identityConfirmed || fieldErrors(err).message || errorMessage(err)
  } finally {
    busyId.value = ''
  }
}

const fmt = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '')

async function setStatus(req, status) {
  busyId.value = req.id
  rowError[req.id] = ''
  try {
    Object.assign(req, await setPasswordRequestStatus(req.id, status))
    emit('counts-changed')
  } catch (err) {
    rowError[req.id] = errorMessage(err)
  } finally {
    busyId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="pw-layout">
    <section class="card">
      <header class="head">
        <div>
          <h3>Password help requests</h3>
          <p class="muted small">Sent from “Forgot password?” on the Login page.</p>
        </div>
        <div class="chips" role="group" aria-label="Filter requests">
          <button v-for="f in FILTERS" :key="f.id" type="button" class="chip" :class="{ on: filter === f.id }"
            :aria-pressed="filter === f.id" @click="filter = f.id">{{ f.label }}</button>
        </div>
      </header>

      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="error" class="error">{{ error }}</p>
      <div v-else-if="!requests.length" class="empty">
        <AppIcon name="shield-check" :size="30" />
        <p>{{ filter === 'OPEN' ? 'No one is waiting for a password reset.' : 'No requests here.' }}</p>
      </div>
      <ul v-else class="requests">
        <li v-for="req in requests" :key="req.id" class="request" :data-status="req.status">
          <div class="req-main">
            <span class="req-icon"><AppIcon name="shield-check" :size="18" /></span>
            <div class="min0">
              <strong>{{ req.fullName }}</strong>
              <span class="email">{{ req.email }}</span>
              <span v-if="req.reference" class="ref" title="The visitor sees this too: ask them to read it out">Reference {{ req.reference }}</span>
              <p v-if="req.message" class="msg">“{{ req.message }}”</p>
              <span class="meta">
                Requested {{ ago(req.createdAt) }}
                <template v-if="req.status !== 'OPEN' && req.handledAt">
                  · {{ req.status === 'RESOLVED' ? 'Reset' : 'Dismissed' }} {{ ago(req.handledAt) }}<template v-if="req.handledBy"> by {{ req.handledBy }}</template>
                </template>
              </span>
            </div>
            <span class="pill" :data-status="req.status">{{ req.status.charAt(0) + req.status.slice(1).toLowerCase() }}</span>
          </div>

          <div v-if="req.reply" class="reply">
            <span><AppIcon name="message" :size="14" /> Your reply · {{ fmt(req.repliedAt) }}<template v-if="req.repliedBy"> · {{ req.repliedBy }}</template></span>
            <p>{{ req.reply }}</p>
          </div>
          <p v-if="req.passwordSent" class="delivery" :class="{ shown: req.passwordShownAt }">
            <AppIcon :name="req.passwordShownAt ? 'check-circle' : 'refresh'" :size="15" />
            {{ req.passwordShownAt ? `Temporary password seen by the user on ${fmt(req.passwordShownAt)}` : 'Temporary password sent to the status page; waiting for the user to open it' }}
          </p>

          <div v-if="req.status === 'OPEN'" class="actions">
            <template v-if="req.hasStatusPage">
              <button type="button" class="btn btn-primary btn-sm" :disabled="busyId === req.id" @click="openForm(req, 'send')">
                <AppIcon name="send" :size="15" /> Send temporary password
              </button>
              <button type="button" class="btn btn-outline btn-sm" :disabled="busyId === req.id" @click="openForm(req, 'reply')">
                <AppIcon name="message" :size="15" /> {{ req.reply ? 'Edit reply' : 'Reply' }}
              </button>
            </template>
            <button type="button" class="btn btn-outline btn-sm" :disabled="busyId === req.id" @click="doReset(req)">
              {{ busyId === req.id ? 'Working…' : req.hasStatusPage ? 'Reset & tell by phone' : 'Reset password' }}
            </button>
            <button type="button" class="link" :disabled="busyId === req.id" @click="setStatus(req, 'DISMISSED')">Dismiss</button>
          </div>

          <form v-if="form.id === req.id" class="req-form" novalidate @submit.prevent="form.mode === 'send' ? sendPassword(req) : sendReply(req)">
            <label :for="`req-msg-${req.id}`">{{ form.mode === 'send' ? 'Message with the password (optional)' : 'Message for the user' }}</label>
            <textarea :id="`req-msg-${req.id}`" v-model="form.message" rows="3" maxlength="1000"
              :placeholder="form.mode === 'send' ? 'e.g. Confirmed by phone. Log in and choose your own password.' : 'e.g. We will call you on the number on your profile within an hour.'"></textarea>
            <label v-if="form.mode === 'send'" class="confirm">
              <input v-model="form.confirmed" type="checkbox" />
              <span>I confirmed this is really <strong>{{ req.fullName }}</strong> (for example by phone) and they read out reference <strong>{{ req.reference }}</strong>.</span>
            </label>
            <p v-if="form.mode === 'send'" class="muted small">The password is reset now and shown once on this request's status page. You will not see it.</p>
            <p v-if="form.error" class="error">{{ form.error }}</p>
            <div class="form-actions">
              <button type="submit" class="btn btn-primary btn-sm" :disabled="busyId === req.id">
                {{ busyId === req.id ? 'Sending…' : form.mode === 'send' ? 'Reset & send password' : 'Send reply' }}
              </button>
              <button type="button" class="btn btn-outline btn-sm" @click="closeForm">Cancel</button>
            </div>
          </form>
          <div v-else-if="req.status === 'DISMISSED'" class="actions">
            <button type="button" class="link" :disabled="busyId === req.id" @click="setStatus(req, 'OPEN')">Reopen</button>
          </div>
          <p v-if="rowError[req.id]" class="error">{{ rowError[req.id] }}</p>
          <TempPasswordNotice v-if="issued[req.id]" :email="req.email" :password="issued[req.id]" @close="issued[req.id] = ''" />
        </li>
      </ul>
    </section>

    <aside class="card guide">
      <h3>How it works</h3>
      <ol>
        <li><strong>The user asks.</strong> They get a secret request code and a reference, and a status page.</li>
        <li><strong>Reply</strong> to tell them what happens next; it appears on their status page.</li>
        <li><strong>Check it is really them</strong>, for example by calling the phone number on their profile and asking for the reference.</li>
        <li><strong>Send a temporary password.</strong> It goes only to that request's status page and is shown once; you never see it.</li>
        <li><strong>They choose their own.</strong> After logging in with it, they must set a new password before anything else.</li>
      </ol>
      <p class="muted small">Requests from emails without an account are never stored, so the form cannot reveal who is registered.</p>
      <p v-if="filter === 'OPEN' && openCount" class="waiting">{{ openCount }} waiting</p>
    </aside>
  </div>
</template>

<style scoped>
.pw-layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 1rem; align-items: start; }
h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.small { font-size: 0.85rem; margin: 0.15rem 0 0; }
.min0 { min-width: 0; flex: 1; }
.head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
.chips { display: flex; flex-wrap: wrap; gap: 0.35rem; }
.chip { padding: 0.35rem 0.8rem; border: 1px solid var(--border); border-radius: 999px; background: #fff; color: var(--muted); font: 600 0.82rem 'Poppins', sans-serif; cursor: pointer; }
.chip.on { background: var(--purple-900); border-color: var(--purple-900); color: #fff; }

.requests { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
.request { padding: 1rem; border: 1px solid var(--border); border-radius: 16px; background: #fff; transition: box-shadow 0.2s; }
.request[data-status='OPEN'] { border-left: 4px solid var(--pink-600); }
.request:hover { box-shadow: 0 8px 22px rgba(42, 22, 80, 0.08); }
.req-main { display: flex; gap: 0.8rem; align-items: flex-start; }
.req-icon { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; background: var(--pink-50); color: var(--pink-600); }
.req-main strong { display: block; color: var(--purple-900); }
.email { display: block; color: var(--muted); font-size: 0.85rem; overflow-wrap: anywhere; }
.msg { margin: 0.4rem 0 0; padding: 0.5rem 0.7rem; border-radius: 10px; background: #fbf8fd; color: var(--text); font-size: 0.88rem; overflow-wrap: anywhere; }
.meta { display: block; margin-top: 0.35rem; color: var(--muted); font-size: 0.78rem; }
.pill { flex: none; padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; background: #fff4e5; color: #9a3412; }
.pill[data-status='RESOLVED'] { background: #e8f5e9; color: #1b5e20; }
.pill[data-status='DISMISSED'] { background: #f1f1f4; color: #55536b; }
.ref { display: inline-block; margin-top: 0.3rem; padding: 0.1rem 0.5rem; border-radius: 6px; background: var(--pink-50); color: var(--pink-600); font: 700 0.78rem ui-monospace, Consolas, monospace; }
.reply { margin: 0.6rem 0 0 calc(40px + 0.8rem); padding: 0.55rem 0.75rem; border-radius: 10px; background: #f7f3fb; font-size: 0.86rem; }
.reply span { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.74rem; font-weight: 600; color: var(--purple-900); }
.reply p { margin: 0.25rem 0 0; white-space: pre-line; }
.delivery { display: flex; align-items: center; gap: 0.4rem; margin: 0.5rem 0 0 calc(40px + 0.8rem); font-size: 0.82rem; color: #9a3412; }
.delivery.shown { color: var(--low); }
.req-form { display: grid; gap: 0.45rem; margin: 0.8rem 0 0 calc(40px + 0.8rem); padding: 0.85rem; border-radius: 14px; background: #fbf8fd; border: 1px solid var(--border); }
.req-form label { font-weight: 600; font-size: 0.85rem; color: var(--purple-900); }
.req-form textarea { width: 100%; padding: 0.6rem 0.75rem; border: 1.5px solid var(--border); border-radius: 10px; font: 400 0.9rem 'Poppins', sans-serif; resize: vertical; }
.req-form textarea:focus { outline: none; border-color: var(--pink-600); }
.confirm { display: flex; gap: 0.55rem; align-items: flex-start; font-weight: 400 !important; line-height: 1.45; cursor: pointer; }
.confirm input { margin-top: 0.2rem; accent-color: var(--pink-600); }
.form-actions { display: flex; gap: 0.5rem; }
.actions { display: flex; flex-wrap: wrap; gap: 0.5rem; margin: 0.75rem 0 0 calc(40px + 0.8rem); }
.link { padding: 0; border: 0; background: none; color: var(--pink-600); font: 600 0.85rem 'Poppins', sans-serif; cursor: pointer; text-decoration: underline; }
.empty { display: grid; justify-items: center; padding: 2.5rem 1rem; color: var(--muted); text-align: center; }
.empty :deep(svg) { color: #22c55e; }
.empty p { margin: 0.5rem 0 0; }

.guide ol { margin: 0.75rem 0 0.75rem; padding-left: 1.2rem; display: grid; gap: 0.6rem; font-size: 0.9rem; line-height: 1.5; }
.guide strong { color: var(--purple-900); }
.waiting { margin: 0.5rem 0 0; display: inline-block; padding: 0.25rem 0.75rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-weight: 600; font-size: 0.82rem; }

@media (max-width: 1000px) { .pw-layout { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .actions, .reply, .delivery, .req-form { margin-left: 0; } }
</style>
