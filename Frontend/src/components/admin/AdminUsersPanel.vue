<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import PasswordField from '../auth/PasswordField.vue'
import TempPasswordNotice from './TempPasswordNotice.vue'
import { authState } from '../../auth/authStore'
import { ROLES, ROLE_LABELS } from '../../auth/roles'
import { validateEmail, validateFullName, validatePassword } from '../../auth/validation'
import { listUsers, createUser, changeRole, setUserDisabled, resetPassword } from '../../services/adminService'
import { errorMessage, fieldErrors } from '../../services/http'

const users = ref([])
const loading = ref(true)
const listError = ref('')
const rowMessage = reactive({}) // userId -> { text, ok }
const savingId = ref('')

// Filters
const search = ref('')
const roleFilter = ref('')
const statusFilter = ref('') // '' | 'active' | 'disabled'

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return users.value.filter(
    (u) =>
      (!q || u.fullName.toLowerCase().includes(q) || u.email.includes(q)) &&
      (!roleFilter.value || u.role === roleFilter.value) &&
      (!statusFilter.value || (statusFilter.value === 'disabled') === u.disabled),
  )
})

async function load() {
  try {
    users.value = await listUsers()
  } catch (err) {
    listError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const isSelf = (user) => user.id === authState.user?.id
// Only the owner manages the owner's account; for everyone else it is read-only here.
const isLocked = (user) => isSelf(user) || (user.owner && !authState.user?.owner)

async function updateRole(user, select) {
  const role = select.value
  if (role === user.role) return
  savingId.value = user.id
  try {
    Object.assign(user, await changeRole(user.id, role))
    rowMessage[user.id] = { text: `Now ${ROLE_LABELS[user.role]}`, ok: true }
  } catch (err) {
    select.value = user.role // show the role that is actually saved
    rowMessage[user.id] = { text: errorMessage(err), ok: false }
  } finally {
    savingId.value = ''
  }
}

async function toggleDisabled(user) {
  const disable = !user.disabled
  if (disable && !confirm(`Disable ${user.email}? They will be signed out and cannot log in until you enable the account again.`)) return
  savingId.value = user.id
  try {
    Object.assign(user, await setUserDisabled(user.id, disable))
    rowMessage[user.id] = { text: user.disabled ? 'Account disabled' : 'Account enabled', ok: true }
  } catch (err) {
    rowMessage[user.id] = { text: errorMessage(err), ok: false }
  } finally {
    savingId.value = ''
  }
}

const VERIFY_LABELS = { PENDING: 'Pending check', VERIFIED: 'Verified', REJECTED: 'Needs correction' }
const resetResult = ref(null) // { email, password }

async function doReset(user) {
  if (!confirm(`Reset the password for ${user.email}? Their current password stops working and they are signed out everywhere.`)) return
  savingId.value = user.id
  try {
    const result = await resetPassword(user.id)
    Object.assign(user, result.user)
    resetResult.value = { email: user.email, password: result.temporaryPassword }
  } catch (err) {
    rowMessage[user.id] = { text: errorMessage(err), ok: false }
  } finally {
    savingId.value = ''
  }
}

function lastSeen(iso) {
  if (!iso) return 'Never'
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes} min ago`
  if (minutes < 1440) return `${Math.round(minutes / 60)} h ago`
  return new Date(iso).toLocaleDateString()
}

// Create account
const form = reactive({ fullName: '', email: '', password: '', role: ROLES.DOCTOR })
const formErrors = reactive({ fullName: '', email: '', password: '' })
const creating = ref(false)
const createResult = ref(null) // { ok, text }

async function submitCreate() {
  formErrors.fullName = validateFullName(form.fullName)
  formErrors.email = validateEmail(form.email)
  formErrors.password = validatePassword(form.password)
  if (formErrors.fullName || formErrors.email || formErrors.password) return

  creating.value = true
  createResult.value = null
  try {
    const user = await createUser({ ...form, fullName: form.fullName.trim(), email: form.email.trim() })
    users.value.unshift(user)
    createResult.value = { ok: true, text: `Created ${ROLE_LABELS[user.role].toLowerCase()} account for ${user.email}.` }
    Object.assign(form, { fullName: '', email: '', password: '', role: ROLES.DOCTOR })
  } catch (err) {
    const errors = fieldErrors(err)
    for (const key of Object.keys(formErrors)) formErrors[key] = errors[key] ?? formErrors[key]
    createResult.value = { ok: false, text: errorMessage(err) }
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="layout">
    <section class="card">
      <header class="head">
        <h3>All users <span class="count">{{ filtered.length }}<template v-if="filtered.length !== users.length"> of {{ users.length }}</template></span></h3>
        <div class="filters">
          <input v-model="search" type="search" placeholder="Search name or email" aria-label="Search users" />
          <select v-model="roleFilter" aria-label="Filter by role">
            <option value="">All roles</option>
            <option v-for="role in Object.keys(ROLES)" :key="role" :value="role">{{ ROLE_LABELS[role] }}</option>
          </select>
          <select v-model="statusFilter" aria-label="Filter by status">
            <option value="">Any status</option>
            <option value="active">Active</option>
            <option value="disabled">Disabled</option>
          </select>
        </div>
      </header>

      <TempPasswordNotice v-if="resetResult" :email="resetResult.email" :password="resetResult.password" class="reset-notice" @close="resetResult = null" />

      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="listError" class="error">{{ listError }}</p>
      <p v-else-if="!filtered.length" class="muted">No users match these filters.</p>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr><th scope="col">User</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Last login</th><th scope="col">Joined</th></tr>
          </thead>
          <tbody>
            <tr v-for="user in filtered" :key="user.id" :class="{ off: user.disabled }">
              <td>
                <strong class="name">{{ user.fullName }}<span v-if="isSelf(user)" class="you">you</span><span v-if="user.owner" class="owner-tag">owner</span></strong>
                <span class="email">{{ user.email }}</span>
                <span v-if="user.verificationStatus" class="verify" :data-status="user.verificationStatus">{{ VERIFY_LABELS[user.verificationStatus] }}</span>
              </td>
              <td>
                <span v-if="isLocked(user)" class="muted">{{ ROLE_LABELS[user.role] }}</span>
                <select
                  v-else
                  :value="user.role"
                  :disabled="savingId === user.id"
                  :aria-label="`Role for ${user.email}`"
                  @change="updateRole(user, $event.target)"
                >
                  <option v-for="role in Object.keys(ROLES)" :key="role" :value="role">{{ ROLE_LABELS[role] }}</option>
                </select>
                <span v-if="rowMessage[user.id]" class="row-msg" :class="rowMessage[user.id].ok ? 'low' : 'high'" role="status">
                  {{ rowMessage[user.id].text }}
                </span>
              </td>
              <td>
                <span class="pill" :class="user.disabled ? 'pill-off' : 'pill-on'">{{ user.disabled ? 'Disabled' : 'Active' }}</span>
                <button
                  v-if="!isLocked(user)"
                  type="button"
                  class="link-btn"
                  :class="{ danger: !user.disabled }"
                  :disabled="savingId === user.id"
                  @click="toggleDisabled(user)"
                >
                  {{ user.disabled ? 'Enable' : 'Disable' }}
                </button>
                <button v-if="!isLocked(user)" type="button" class="link-btn reset-btn" :disabled="savingId === user.id" @click="doReset(user)">
                  Reset password
                </button>
              </td>
              <td class="meta">{{ lastSeen(user.lastLoginAt) }}</td>
              <td class="meta">{{ new Date(user.createdAt).toLocaleDateString() }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <form class="card form-stack create" novalidate @submit.prevent="submitCreate">
      <h3>Create account</h3>
      <p class="muted small">For doctors and administrators. Patients can also sign up themselves.</p>
      <div class="form-field">
        <label for="new-name">Full name</label>
        <input id="new-name" v-model="form.fullName" maxlength="100" :aria-invalid="!!formErrors.fullName" aria-describedby="new-name-error" />
        <p id="new-name-error" class="field-error">{{ formErrors.fullName }}</p>
      </div>
      <div class="form-field">
        <label for="new-email">Email</label>
        <input id="new-email" v-model="form.email" type="email" maxlength="254" autocomplete="off" :aria-invalid="!!formErrors.email" aria-describedby="new-email-error" />
        <p id="new-email-error" class="field-error">{{ formErrors.email }}</p>
      </div>
      <PasswordField id="new-password" v-model="form.password" label="Temporary password" autocomplete="new-password" :error="formErrors.password" />
      <div class="form-field">
        <label for="new-role">Role</label>
        <select id="new-role" v-model="form.role">
          <option v-for="role in Object.keys(ROLES)" :key="role" :value="role">{{ ROLE_LABELS[role] }}</option>
        </select>
      </div>
      <div v-if="createResult" class="alert" :class="createResult.ok ? 'success' : 'error'" role="status">{{ createResult.text }}</div>
      <button type="submit" class="btn btn-primary" :disabled="creating" :aria-busy="creating">
        <span v-if="creating" class="spinner" aria-hidden="true"></span>
        {{ creating ? 'Creating…' : 'Create Account' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 2.2fr) minmax(0, 1fr); gap: 1rem; align-items: start; }
h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.small { font-size: 0.86rem; margin: -0.3rem 0 0.4rem; }
.head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem; }
.count { margin-left: 0.35rem; padding: 0.1rem 0.5rem; border-radius: 999px; background: var(--pink-50); color: var(--pink-600); font-size: 0.8rem; }
.filters { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.filters input, .filters select, td select {
  padding: 0.45rem 0.6rem; border: 1.5px solid var(--border); border-radius: 10px; background: #fff; font: inherit; font-size: 0.88rem;
}
.filters input { min-width: 200px; }
.filters input:focus, .filters select:focus, td select:focus { outline: none; border-color: var(--pink-600); }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
th, td { text-align: left; padding: 0.65rem 0.5rem; border-bottom: 1px solid var(--border); vertical-align: middle; }
th { color: var(--muted); font-weight: 600; font-size: 0.82rem; }
tr.off td { background: #fbf7f9; }
.name { display: block; color: var(--purple-900); }
.email { display: block; font-size: 0.82rem; color: var(--muted); overflow-wrap: anywhere; }
.you { margin-left: 0.4rem; padding: 0 0.4rem; border-radius: 4px; background: var(--pink-100); color: var(--pink-600); font-size: 0.72rem; font-weight: 600; }
.row-msg { display: block; font-size: 0.78rem; margin-top: 0.2rem; }
.pill { display: inline-block; padding: 0.15rem 0.55rem; border-radius: 999px; font-size: 0.76rem; font-weight: 600; }
.pill-on { background: #e8f5e9; color: #1b5e20; }
.pill-off { background: #fdecea; color: #8e1c1c; }
.link-btn { display: block; margin-top: 0.25rem; padding: 0; border: 0; background: none; color: var(--pink-600); font: inherit; font-size: 0.8rem; font-weight: 600; cursor: pointer; }
.link-btn.danger { color: var(--high); }
.link-btn:disabled { opacity: 0.5; cursor: wait; }
.meta { white-space: nowrap; }
.verify { display: inline-block; margin-top: 0.2rem; padding: 0 0.45rem; border-radius: 6px; font-size: 0.7rem; font-weight: 600; background: #fff4e5; color: #9a3412; }
.verify[data-status='VERIFIED'] { background: #e8f5e9; color: #1b5e20; }
.verify[data-status='REJECTED'] { background: #fdecea; color: #8e1c1c; }
.reset-btn { color: #6b3fb5; }
.owner-tag { margin-left: 0.4rem; padding: 0 0.4rem; border-radius: 4px; background: #fef3c7; color: #92400e; font-size: 0.72rem; font-weight: 700; }
.reset-notice { margin: 0 0 0.75rem; }
.create .btn { justify-content: center; }
@media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .filters, .filters input { width: 100%; min-width: 0; } .filters select { flex: 1; } }
</style>
