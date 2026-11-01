<script setup>
import { ref, onMounted } from 'vue'
import KnowledgeEditor from './KnowledgeEditor.vue'
import { blankContent, toEditable, toPayload } from './knowledgeEditing'
import { listModules, getModule, mySubmissions, submitContent, contentOf } from '../../services/knowledgeService'
import { errorMessage, fieldErrors } from '../../services/http'

// Verified Breast Surgeons propose Knowledge Hub content. Nothing goes public until an administrator approves it.
defineProps({ approved: { type: Boolean, default: false } })

const modules = ref([])
const mine = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const target = ref('')
const draft = ref(null)
const errors = ref({})
const sending = ref(false)

const when = (iso) => (iso ? new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' }) : '')
const STATUS = { PENDING: 'Waiting for review', APPROVED: 'Approved', REJECTED: 'Returned' }

async function load() {
  loading.value = true
  try {
    ;[modules.value, mine.value] = await Promise.all([listModules(), mySubmissions()])
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function start() {
  message.value = ''
  errors.value = {}
  try {
    draft.value = target.value ? toEditable(contentOf(await getModule(target.value))) : blankContent()
  } catch (err) {
    error.value = errorMessage(err)
  }
}

async function send() {
  sending.value = true
  errors.value = {}
  try {
    await submitContent(target.value, toPayload(draft.value))
    draft.value = null
    message.value = 'Sent for review.'
    await load()
  } catch (err) {
    errors.value = fieldErrors(err)
    if (!Object.keys(errors.value).length) errors.value = { content: errorMessage(err) }
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="sk">
    <h2>Knowledge Hub</h2>
    <p v-if="!approved" class="alert info">You can suggest content once your profile is verified.</p>
    <template v-else>
      <p class="muted small">Suggest an edit or a new lesson. An administrator checks it before it goes public.</p>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>
      <p v-if="message" class="alert success" role="status">{{ message }}</p>

      <div v-if="!draft" class="start-row">
        <select v-model="target" aria-label="What to suggest">
          <option value="">A new lesson</option>
          <option v-for="m in modules" :key="m.slug" :value="m.slug">Edit: {{ m.title }}</option>
        </select>
        <button type="button" class="btn btn-primary btn-sm go" @click="start">Start</button>
      </div>

      <div v-else class="draft">
        <KnowledgeEditor v-model="draft" :modules="modules" :slug-locked="!!target" :errors="errors" />
        <div class="actions">
          <button type="button" class="btn btn-outline btn-sm" :disabled="sending" @click="draft = null">Cancel</button>
          <button type="button" class="btn btn-primary btn-sm go" :disabled="sending" @click="send">{{ sending ? 'Sending…' : 'Send for review' }}</button>
        </div>
      </div>

      <h3>My suggestions</h3>
      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="!mine.length" class="muted small">None yet.</p>
      <ul v-else class="mine">
        <li v-for="s in mine" :key="s.id">
          <div>
            <strong>{{ s.content.title }}</strong>
            <span class="muted small"> · {{ s.targetSlug ? 'Edit' : 'New lesson' }} · {{ when(s.createdAt) }}</span>
            <p v-if="s.reviewNote" class="small note">Admin note: {{ s.reviewNote }}</p>
          </div>
          <span class="pill" :class="s.status.toLowerCase()">{{ STATUS[s.status] }}</span>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.sk { display: grid; gap: 0.8rem; }
h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
h3 { margin: 0.6rem 0 0; font-size: 1rem; color: var(--purple-900); }
.small { font-size: 0.86rem; margin: 0; }
.start-row { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.start-row select { min-width: 260px; padding: 0.5rem 0.7rem; border: 1.5px solid #f3d4e3; border-radius: 10px; background: #fff; color: #2a1650; font: inherit; }
.go { background: #d81b6a; color: #fff; }
.draft { display: grid; gap: 0.8rem; }
.actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
.mine { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.45rem; }
.mine li { display: flex; justify-content: space-between; align-items: center; gap: 0.8rem; padding: 0.65rem 0.85rem; border: 1px solid var(--border); border-radius: 12px; }
.mine strong { color: var(--purple-900); }
.note { color: var(--text); margin-top: 0.25rem; }
.pill { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; white-space: nowrap; }
.pill.approved { background: #e8f5e9; color: #1b5e20; }
.pill.pending { background: #fff4e5; color: #8a4b00; }
.pill.rejected { background: #fdecea; color: #8e1c1c; }
</style>
