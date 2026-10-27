<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { authState } from '../auth/authStore'
import { ROLES, ROLE_LABELS } from '../auth/roles'
import { validateEmail, validateFullName, validatePassword } from '../auth/validation'
import { listUsers, createUser, changeRole } from '../services/adminService'
import { errorMessage, fieldErrors } from '../services/http'
import PasswordField from '../components/auth/PasswordField.vue'

const users = ref([])
const loading = ref(true)
const listError = ref('')
const rowMessage = reactive({}) // userId -> { text, ok }
const savingId = ref('')

const counts = computed(() =>
  Object.keys(ROLES).map((role) => ({ role, count: users.value.filter((u) => u.role === role).length })),
)

async function load() {
  try {
    users.value = await listUsers()
  } catch (err) {
    listError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function updateRole(user, select) {
  const role = select.value
  if (role === user.role) return
  savingId.value = user.id
  try {
    const updated = await changeRole(user.id, role)
    Object.assign(user, updated)
    rowMessage[user.id] = { text: `Now ${ROLE_LABELS[updated.role]}`, ok: true }
  } catch (err) {
    select.value = user.role // show the role that is actually saved
    rowMessage[user.id] = { text: errorMessage(err), ok: false }
  } finally {
    savingId.value = ''
  }
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
  <section class="container page">
    <h1>Administration</h1>
    <p class="muted">Manage accounts and roles. Medical records are private to patients and the doctors they choose.</p>

    <div class="stats">
      <div v-for="item in counts" :key="item.role" class="card stat">
        <strong>{{ item.count }}</strong>
        <span class="muted">{{ ROLE_LABELS[item.role] }}{{ item.count === 1 ? '' : 's' }}</span>
      </div>
    </div>

    <div class="layout">
      <div class="card">
        <h2>Users</h2>
        <p v-if="loading" class="muted">Loading…</p>
        <p v-else-if="listError" class="error">{{ listError }}</p>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Role</th><th scope="col">Joined</th></tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.fullName }}</td>
                <td class="email">{{ user.email }}</td>
                <td>
                  <span v-if="user.id === authState.user?.id" class="muted">{{ ROLE_LABELS[user.role] }} (you)</span>
                  <template v-else>
                    <select
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
                  </template>
                </td>
                <td class="meta">{{ new Date(user.createdAt).toLocaleDateString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <form class="card form-stack" novalidate @submit.prevent="submitCreate">
        <h2>Create account</h2>
        <p class="muted small">Use this for doctors and administrators. Patients can also sign up themselves.</p>
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
  </section>
</template>

<style scoped>
h2 { margin: 0 0 0.75rem; color: var(--purple-900); font-size: 1.2rem; }
.small { font-size: 0.88rem; margin: -0.4rem 0 0.5rem; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin: 1.25rem 0; }
.stat { display: grid; gap: 0.1rem; text-align: center; }
.stat strong { font-size: 1.8rem; color: var(--pink-600); }
.layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 1.25rem; align-items: start; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
th, td { text-align: left; padding: 0.6rem 0.5rem; border-bottom: 1px solid var(--border); vertical-align: middle; }
th { color: var(--muted); font-weight: 600; }
.email { overflow-wrap: anywhere; }
td select { padding: 0.35rem 0.5rem; border: 1.5px solid var(--border); border-radius: 8px; font: inherit; background: #fff; }
.row-msg { display: block; font-size: 0.8rem; margin-top: 0.2rem; }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .stats { grid-template-columns: 1fr; } }
</style>
