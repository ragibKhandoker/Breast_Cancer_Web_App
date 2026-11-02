<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import KnowledgeEditor from '../knowledge/KnowledgeEditor.vue'
import KnowledgeSection from '../knowledge/KnowledgeSection.vue'
import { blankContent, toEditable, toPayload } from '../knowledge/knowledgeEditing'
import {
  adminListModules, adminCreateModule, adminUpdateModule, adminSetPublished, adminListSubmissions, adminReviewSubmission, contentOf,
} from '../../services/knowledgeService'
import { errorMessage, fieldErrors } from '../../services/http'

// Knowledge Hub content: edit modules (timelines, classification tables, diagrams, references), publish or
// unpublish them, and review content proposed by Breast Surgeons.
const view = ref('modules')
const modules = ref([])
const submissions = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const busy = ref('')

const editing = ref(null) // { slug | null, content }
const errors = ref({})
const saving = ref(false)

const filter = ref('PENDING')
const preview = ref('')
const notes = ref({})
const pending = computed(() => submissions.value.filter((s) => s.status === 'PENDING').length)
const shownSubmissions = computed(() => submissions.value.filter((s) => !filter.value || s.status === filter.value))
const titleOf = (slug) => modules.value.find((m) => m.slug === slug)?.title ?? slug
const when = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—')

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[modules.value, submissions.value] = await Promise.all([adminListModules(), adminListSubmissions()])
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

function edit(module) {
  message.value = ''
  errors.value = {}
  editing.value = module ? { slug: module.slug, content: toEditable(contentOf(module)) } : { slug: null, content: blankContent() }
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function save() {
  saving.value = true
  errors.value = {}
  try {
    const payload = toPayload(editing.value.content)
    const saved = editing.value.slug ? await adminUpdateModule(editing.value.slug, payload) : await adminCreateModule(payload)
    message.value = editing.value.slug ? 'Module saved.' : 'Module created as a draft. Publish it when ready.'
    editing.value = null
    await load()
    return saved
  } catch (err) {
    errors.value = fieldErrors(err)
    if (!Object.keys(errors.value).length) errors.value = { module: errorMessage(err) }
  } finally {
    saving.value = false
  }
}

async function togglePublished(m) {
  busy.value = m.slug
  message.value = ''
  try {
    await adminSetPublished(m.slug, !m.published)
    message.value = m.published ? `“${m.title}” is hidden from visitors.` : `“${m.title}” is now public.`
    await load()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    busy.value = ''
  }
}

async function review(s, status) {
  busy.value = s.id
  message.value = ''
  try {
    await adminReviewSubmission(s.id, status, notes.value[s.id] ?? '')
    message.value = status === 'APPROVED' ? 'Approved and added to the hub.' : 'Sent back to the surgeon.'
    await load()
  } catch (err) {
    const fe = fieldErrors(err)
    message.value = ''
    error.value = fe.note ?? Object.values(fe)[0] ?? errorMessage(err)
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="kh-admin">
    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <p v-if="message" class="alert success" role="status">{{ message }}</p>

    <div v-if="editing" class="card">
      <div class="bar">
        <h3>{{ editing.slug ? `Edit: ${editing.content.title}` : 'New module' }}</h3>
        <div class="actions">
          <button type="button" class="btn btn-outline btn-sm" :disabled="saving" @click="editing = null">Cancel</button>
          <button type="button" class="btn btn-primary btn-sm save" :disabled="saving" @click="save">{{ saving ? 'Saving…' : 'Save' }}</button>
        </div>
      </div>
      <KnowledgeEditor v-model="editing.content" :modules="modules" :slug-locked="!!editing.slug" allow-images :errors="errors" />
      <div class="bar end">
        <button type="button" class="btn btn-primary btn-sm save" :disabled="saving" @click="save">{{ saving ? 'Saving…' : 'Save' }}</button>
      </div>
    </div>

    <template v-else>
      <div class="switch" role="tablist">
        <button type="button" role="tab" :aria-selected="view === 'modules'" :class="{ on: view === 'modules' }" @click="view = 'modules'">
          <AppIcon name="book" :size="15" /> Modules
        </button>
        <button type="button" role="tab" :aria-selected="view === 'submissions'" :class="{ on: view === 'submissions' }" @click="view = 'submissions'">
          <AppIcon name="users" :size="15" /> Surgeon submissions <span v-if="pending" class="count">{{ pending }}</span>
        </button>
      </div>

      <p v-if="loading" class="muted">Loading…</p>

      <div v-else-if="view === 'modules'" class="card list">
        <div class="bar">
          <span class="muted small">{{ modules.length }} modules · {{ modules.filter((m) => m.published).length }} public</span>
          <button type="button" class="btn btn-primary btn-sm new" @click="edit(null)"><AppIcon name="file-text" :size="14" /> New module</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>#</th><th>Module</th><th>Category</th><th>Status</th><th>Updated</th><th></th></tr></thead>
            <tbody>
              <tr v-for="m in modules" :key="m.slug">
                <td>{{ m.order }}</td>
                <td><strong>{{ m.title }}</strong><br /><span class="muted mono">/knowledge/{{ m.slug }}</span></td>
                <td>{{ m.category }}</td>
                <td><span class="pill" :class="m.published ? 'pub' : 'draft'">{{ m.published ? 'Published' : 'Draft' }}</span></td>
                <td class="nowrap">{{ when(m.updatedAt) }}</td>
                <td class="row-actions">
                  <button type="button" class="btn btn-outline btn-sm" @click="edit(m)">Edit</button>
                  <button type="button" class="btn btn-outline btn-sm" :disabled="busy === m.slug" @click="togglePublished(m)">
                    {{ m.published ? 'Unpublish' : 'Publish' }}
                  </button>
                  <a v-if="m.published" :href="`/knowledge/${m.slug}`" target="_blank" rel="noopener" class="btn btn-outline btn-sm">View</a>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-else class="subs">
        <div class="filters">
          <button v-for="f in [['PENDING', 'Pending'], ['APPROVED', 'Approved'], ['REJECTED', 'Returned'], ['', 'All']]" :key="f[0]"
            type="button" :class="{ on: filter === f[0] }" @click="filter = f[0]">{{ f[1] }}</button>
        </div>
        <p v-if="!shownSubmissions.length" class="muted">Nothing here.</p>
        <div v-for="s in shownSubmissions" :key="s.id" class="card sub">
          <div class="bar">
            <div>
              <strong>{{ s.content.title }}</strong>
              <p class="muted small">
                {{ s.authorName }} · {{ s.targetSlug ? `Edit of “${titleOf(s.targetSlug)}”` : 'New module' }} · {{ when(s.createdAt) }}
              </p>
            </div>
            <span class="pill" :class="s.status.toLowerCase()">{{ s.status === 'REJECTED' ? 'Returned' : s.status.toLowerCase() }}</span>
          </div>
          <p v-if="s.reviewNote" class="small note-text">Note: {{ s.reviewNote }}</p>
          <button type="button" class="btn btn-outline btn-sm" @click="preview = preview === s.id ? '' : s.id">
            {{ preview === s.id ? 'Hide preview' : 'Preview' }}
          </button>
          <div v-if="preview === s.id" class="preview">
            <p class="pv-desc">{{ s.content.description }}</p>
            <KnowledgeSection v-for="sec in s.content.sections" :key="sec.id" :section="sec" />
            <h4>References</h4>
            <ul><li v-for="r in s.content.references" :key="r.url"><a :href="r.url" target="_blank" rel="noopener noreferrer">{{ r.title }}</a></li></ul>
          </div>
          <div v-if="s.status === 'PENDING'" class="decide">
            <textarea v-model="notes[s.id]" rows="2" maxlength="1000" placeholder="Note for the surgeon (needed to return it)"></textarea>
            <div class="actions">
              <button type="button" class="btn btn-outline btn-sm" :disabled="busy === s.id" @click="review(s, 'REJECTED')">Return</button>
              <button type="button" class="btn btn-primary btn-sm approve" :disabled="busy === s.id" @click="review(s, 'APPROVED')">Approve</button>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.kh-admin { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; }
.bar { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.6rem; margin-bottom: 0.8rem; height: auto; overflow: visible; background: none; border-radius: 0; }
.bar.end { justify-content: flex-end; margin: 0.8rem 0 0; }
.bar h3 { margin: 0; color: var(--purple-900); font-size: 1.05rem; }
.bar p { margin: 0.15rem 0 0; }
.actions { display: flex; gap: 0.5rem; }
.save, .new, .approve { background: #d81b6a; color: #fff; }
.small { font-size: 0.85rem; }
.switch, .filters { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.switch button, .filters button {
  display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.5rem 1rem; border-radius: 12px; border: 1.5px solid var(--border);
  background: var(--surface); color: var(--purple-900); font: 600 0.88rem 'Poppins', sans-serif; cursor: pointer;
}
.switch button.on, .filters button.on { background: #d81b6a; border-color: #d81b6a; color: #fff; }
.count { min-width: 20px; padding: 0 0.35rem; border-radius: 999px; background: #fff; color: #d81b6a; font-size: 0.72rem; line-height: 20px; text-align: center; }
.list { padding: 1rem; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.86rem; }
th { text-align: left; padding: 0.55rem; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); border-bottom: 1px solid var(--border); }
td { padding: 0.6rem 0.55rem; border-bottom: 1px solid var(--border); color: var(--text); vertical-align: middle; }
td strong { color: var(--purple-900); }
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 0.76rem; }
.nowrap { white-space: nowrap; }
.row-actions { display: flex; gap: 0.35rem; justify-content: flex-end; flex-wrap: wrap; }
.pill { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.74rem; font-weight: 600; text-transform: capitalize; white-space: nowrap; }
.pill.pub, .pill.approved { background: #e8f5e9; color: #1b5e20; }
.pill.draft, .pill.pending { background: #fff4e5; color: #8a4b00; }
.pill.rejected { background: #fdecea; color: #8e1c1c; }
.subs { display: grid; gap: 0.8rem; }
.sub { display: grid; gap: 0.6rem; padding: 1rem 1.1rem; }
.sub .bar { margin: 0; }
.sub strong { color: var(--purple-900); }
.sub > .btn { justify-self: start; }
.note-text { margin: 0; color: var(--text); }
.preview { display: grid; gap: 1.4rem; padding: 1.2rem; border-radius: 14px; background: #fff; color: #3d3557; }
.preview h4 { margin: 0; color: #2a1650; }
.preview ul { margin: 0; padding-left: 1.2rem; }
.preview a { color: #b0155a; text-decoration: underline; }
.pv-desc { margin: 0; color: #3d3557; font-style: italic; }
.decide { display: grid; gap: 0.5rem; }
.decide textarea { width: 100%; padding: 0.55rem 0.7rem; border: 1.5px solid #f3d4e3; border-radius: 10px; background: #fff; color: #2a1650; font: inherit; }
.decide .actions { justify-content: flex-end; }
</style>
