<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { listAuditLogs, getAuditStats, getAuditEvent, verifyAuditChain, anchorAuditEvent, getBlockchainProof } from '../../services/adminService'
import { errorMessage } from '../../services/http'

// The audit log: who did what and when, each event sealed with a SHA-256 hash chained to the one before it,
// and (when a test blockchain is configured) anchored on chain. Read-only: events cannot be edited here.
const EVENT_TYPES = [
  'SCAN_CREATED', 'SCAN_DELETED', 'SCAN_SHARED', 'SCAN_UNSHARED', 'REPORT_DOWNLOADED', 'REVIEW_UPDATED',
  'ADMIN_ROLE_CHANGED', 'ACCOUNT_CREATED_BY_ADMIN', 'ACCOUNT_DISABLED', 'ACCOUNT_ENABLED', 'PASSWORD_RESET_BY_ADMIN',
  'PASSWORD_CHANGED', 'USER_LOGIN', 'USER_LOGIN_FAILED', 'USER_LOGOUT', 'SITE_SETTINGS_UPDATED', 'BACKUP_CREATED',
  'BACKUP_DELETED', 'BACKUP_RESTORE_TESTED', 'KNOWLEDGE_MODULE_SAVED', 'KNOWLEDGE_MODULE_PUBLISHED',
  'KNOWLEDGE_MODULE_UNPUBLISHED', 'KNOWLEDGE_SUBMISSION_REVIEWED',
]
const CHAIN_STATES = ['NOT_CONFIGURED', 'PENDING', 'ANCHORED', 'FAILED']
const CARDS = [
  { key: 'total', label: 'Total events', icon: 'file-text' },
  { key: 'SCAN_CREATED', label: 'Scans created', icon: 'microscope' },
  { key: 'REPORT_DOWNLOADED', label: 'Report downloads', icon: 'upload' },
  { key: 'REVIEW_UPDATED', label: 'Reviews', icon: 'check-circle' },
  { key: 'ADMIN_ROLE_CHANGED', label: 'Role changes', icon: 'users' },
]
const PAGE_SIZE = 20

const stats = ref(null)
const filters = reactive({ eventType: '', actorId: '', from: '', to: '', blockchainStatus: '' })
const page = ref(0)
const result = ref({ items: [], total: 0 })
const loading = ref(true)
const error = ref('')
const chain = ref(null)
const checkingChain = ref(false)

const pages = computed(() => Math.max(1, Math.ceil(result.value.total / PAGE_SIZE)))
const label = (type) => type.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase())
const short = (value, n = 10) => (value ? (value.length > n ? `${value.slice(0, n)}…` : value) : '—')
const when = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' }) : '—')
const cardValue = (key) => (key === 'total' ? stats.value?.total : stats.value?.byType?.[key]) ?? 0

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [list, s] = await Promise.all([listAuditLogs(filters, page.value, PAGE_SIZE), getAuditStats()])
    result.value = list
    stats.value = s
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

function apply() {
  page.value = 0
  selected.value = null
  load()
}
function clear() {
  Object.assign(filters, { eventType: '', actorId: '', from: '', to: '', blockchainStatus: '' })
  apply()
}
function go(delta) {
  page.value = Math.min(pages.value - 1, Math.max(0, page.value + delta))
  load()
}

async function checkChain() {
  checkingChain.value = true
  try {
    chain.value = await verifyAuditChain()
  } catch (err) {
    chain.value = { status: 'ERROR', problem: errorMessage(err) }
  } finally {
    checkingChain.value = false
  }
}

// ---- Selected event ----
const selected = ref(null)
const detail = ref(null)
const detailError = ref('')
const proof = ref(null)
const busy = ref(false)

async function open(event) {
  selected.value = event
  detail.value = null
  proof.value = null
  detailError.value = ''
  try {
    detail.value = await getAuditEvent(event.id)
  } catch (err) {
    detailError.value = errorMessage(err)
  }
}

async function anchor() {
  busy.value = true
  detailError.value = ''
  try {
    const updated = await anchorAuditEvent(selected.value.id)
    detail.value = { ...detail.value, event: updated }
    result.value.items = result.value.items.map((e) => (e.id === updated.id ? updated : e))
  } catch (err) {
    detailError.value = errorMessage(err)
  } finally {
    busy.value = false
  }
}

async function loadProof() {
  busy.value = true
  detailError.value = ''
  try {
    proof.value = await getBlockchainProof(selected.value.id)
  } catch (err) {
    detailError.value = errorMessage(err)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="audit">
    <!-- Overview -->
    <div class="cards">
      <div v-for="c in CARDS" :key="c.key" class="card stat">
        <span class="stat-icon"><AppIcon :name="c.icon" :size="18" /></span>
        <span class="stat-value">{{ cardValue(c.key) }}</span>
        <span class="stat-label">{{ c.label }}</span>
      </div>
    </div>

    <div class="card chain-bar">
      <div>
        <strong>Hash chain</strong>
        <span class="muted small">
          Blockchain anchoring: {{ stats?.blockchain?.enabled ? `on (${stats.blockchain.network})` : 'not configured' }}
        </span>
      </div>
      <div class="chain-actions">
        <span v-if="chain" class="pill" :class="chain.status === 'VERIFIED' ? 'ok' : 'bad'">
          {{ chain.status === 'VERIFIED' ? `Verified · ${chain.checked} events` : chain.problem || chain.status }}
        </span>
        <button type="button" class="btn btn-outline btn-sm" :disabled="checkingChain" @click="checkChain">
          <AppIcon name="shield-check" :size="15" /> {{ checkingChain ? 'Checking…' : 'Verify whole chain' }}
        </button>
      </div>
    </div>

    <!-- Filters -->
    <form class="card filters" @submit.prevent="apply">
      <label>
        <span>Event type</span>
        <select v-model="filters.eventType">
          <option value="">All events</option>
          <option v-for="t in EVENT_TYPES" :key="t" :value="t">{{ label(t) }}</option>
        </select>
      </label>
      <label>
        <span>User ID</span>
        <input v-model.trim="filters.actorId" placeholder="Any user" maxlength="80" />
      </label>
      <label>
        <span>From</span>
        <input v-model="filters.from" type="date" />
      </label>
      <label>
        <span>To</span>
        <input v-model="filters.to" type="date" />
      </label>
      <label>
        <span>Blockchain</span>
        <select v-model="filters.blockchainStatus">
          <option value="">Any status</option>
          <option v-for="s in CHAIN_STATES" :key="s" :value="s">{{ label(s) }}</option>
        </select>
      </label>
      <div class="filter-actions">
        <button type="submit" class="btn btn-primary btn-sm">Apply</button>
        <button type="button" class="btn btn-outline btn-sm" @click="clear">Clear</button>
      </div>
    </form>

    <div class="layout" :class="{ open: selected }">
      <!-- Table -->
      <div class="card table-card">
        <p v-if="error" class="alert error">{{ error }}</p>
        <p v-else-if="loading" class="muted">Loading…</p>
        <p v-else-if="!result.items.length" class="muted empty">No events match.</p>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr><th>#</th><th>Event</th><th>User</th><th>Target</th><th>Time</th><th>Hash</th><th>Blockchain</th></tr>
            </thead>
            <tbody>
              <tr v-for="e in result.items" :key="e.id" :class="{ on: selected?.id === e.id }" tabindex="0"
                @click="open(e)" @keydown.enter="open(e)">
                <td class="mono">{{ e.sequence }}</td>
                <td><strong>{{ label(e.eventType) }}</strong></td>
                <td><span class="mono">{{ short(e.actorId, 8) }}</span><small v-if="e.actorRole">{{ e.actorRole }}</small></td>
                <td><span class="mono">{{ short(e.targetId, 8) }}</span><small v-if="e.targetType">{{ e.targetType }}</small></td>
                <td class="nowrap">{{ when(e.timestamp) }}</td>
                <td>
                  <span class="hash" :class="e.hashVerified ? 'ok' : 'bad'" :title="e.hashVerified ? 'Hash verified' : 'Hash does not match: the event was changed'">
                    <AppIcon :name="e.hashVerified ? 'check-circle' : 'alert-circle'" :size="13" /> {{ short(e.eventHash, 8) }}
                  </span>
                </td>
                <td><span class="state" :data-state="e.blockchainStatus">{{ label(e.blockchainStatus) }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="result.total" class="pager">
          <span class="muted small">{{ result.total }} event{{ result.total === 1 ? '' : 's' }} · page {{ page + 1 }} of {{ pages }}</span>
          <div>
            <button type="button" class="btn btn-outline btn-sm" :disabled="page === 0" @click="go(-1)">Previous</button>
            <button type="button" class="btn btn-outline btn-sm" :disabled="page >= pages - 1" @click="go(1)">Next</button>
          </div>
        </div>
      </div>

      <!-- Details -->
      <aside v-if="selected" class="card detail" aria-label="Event details">
        <div class="detail-head">
          <h3>{{ label(selected.eventType) }} <span class="muted">#{{ selected.sequence }}</span></h3>
          <button type="button" class="close" aria-label="Close details" @click="selected = null">×</button>
        </div>
        <p v-if="detailError" class="alert error">{{ detailError }}</p>
        <p v-if="!detail && !detailError" class="muted">Loading…</p>
        <template v-if="detail">
          <div class="verdict" :class="detail.verification.status === 'VERIFIED' && detail.verification.chainStatus === 'LINKED' ? 'ok' : 'bad'">
            <AppIcon :name="detail.verification.status === 'VERIFIED' ? 'check-circle' : 'alert-circle'" :size="18" />
            <span>
              <strong>Hash {{ detail.verification.status === 'VERIFIED' ? 'verified' : 'does not match' }}</strong>
              · chain {{ detail.verification.chainStatus === 'LINKED' ? 'linked' : 'broken' }}
              <small>{{ detail.verification.chainDetail }}</small>
            </span>
          </div>
          <dl>
            <dt>Event ID</dt><dd class="mono">{{ detail.event.id }}</dd>
            <dt>Time</dt><dd>{{ when(detail.event.timestamp) }}</dd>
            <dt>User</dt><dd class="mono">{{ detail.event.actorId || '—' }} <small>{{ detail.event.actorRole }}</small></dd>
            <dt>Target</dt><dd class="mono">{{ detail.event.targetType || '—' }} {{ detail.event.targetId || '' }}</dd>
            <template v-for="(value, key) in detail.event.metadata" :key="key">
              <dt>{{ key }}</dt><dd class="mono">{{ value }}</dd>
            </template>
            <dt>Event hash</dt><dd class="mono break">{{ detail.event.eventHash }}</dd>
            <dt>Previous hash</dt><dd class="mono break">{{ detail.event.previousHash }}</dd>
            <dt>Blockchain</dt>
            <dd>
              <span class="state" :data-state="detail.event.blockchainStatus">{{ label(detail.event.blockchainStatus) }}</span>
              <small v-if="detail.event.blockchainError" class="error">{{ detail.event.blockchainError }}</small>
            </dd>
            <template v-if="detail.event.blockchainReference">
              <dt>Transaction</dt><dd class="mono break">{{ detail.event.blockchainReference }}</dd>
              <dt>Network</dt><dd>{{ detail.event.blockchainNetwork }}</dd>
            </template>
          </dl>
          <div v-if="stats?.blockchain?.enabled" class="detail-actions">
            <button v-if="detail.event.blockchainStatus !== 'ANCHORED'" type="button" class="btn btn-primary btn-sm" :disabled="busy" @click="anchor">
              {{ busy ? 'Working…' : detail.event.blockchainStatus === 'FAILED' ? 'Retry anchoring' : 'Anchor on blockchain' }}
            </button>
            <button v-if="detail.event.blockchainReference" type="button" class="btn btn-outline btn-sm" :disabled="busy" @click="loadProof">Check on-chain proof</button>
          </div>
          <div v-if="proof" class="verdict" :class="proof.matches ? 'ok' : 'bad'">
            <AppIcon :name="proof.matches ? 'check-circle' : 'alert-circle'" :size="18" />
            <span>
              <strong>{{ proof.matches ? 'On-chain proof matches' : 'On-chain proof does not match' }}</strong>
              <small>{{ proof.found ? `Block ${proof.blockNumber ?? '—'} on ${proof.network}` : 'Transaction not found on the network.' }}</small>
            </span>
          </div>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.audit { display: grid; gap: 1rem; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.75rem; }
.stat { display: grid; gap: 0.15rem; padding: 1rem; }
.stat-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: var(--pink-100); color: var(--pink-600); margin-bottom: 0.3rem; }
.stat-value { font-size: 1.5rem; font-weight: 700; color: var(--purple-900); }
.stat-label { font-size: 0.82rem; color: var(--muted); }

.chain-bar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.75rem; padding: 0.9rem 1.1rem; }
.chain-bar > div:first-child { display: grid; gap: 0.1rem; }
.chain-bar strong { color: var(--purple-900); }
.chain-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; }
.small { font-size: 0.84rem; }
.pill { padding: 0.25rem 0.7rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
.pill.ok, .verdict.ok { background: rgba(46, 125, 50, 0.2); color: var(--low); }
.pill.bad, .verdict.bad { background: rgba(198, 40, 40, 0.2); color: var(--high); }

.filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.7rem; align-items: end; padding: 1rem; }
.filters label { display: grid; gap: 0.3rem; font-size: 0.8rem; font-weight: 600; color: var(--muted); min-width: 0; }
.filters input, .filters select {
  width: 100%; min-width: 0; padding: 0.5rem 0.6rem; border: 1.5px solid var(--border); border-radius: 10px;
  background: #fff; color: #241445; font: 400 0.88rem 'Poppins', sans-serif;
}
.filter-actions { display: flex; gap: 0.4rem; }

.layout { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; align-items: start; }
.layout.open { grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); }
.table-card { padding: 0.5rem; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.84rem; }
th { text-align: left; padding: 0.6rem 0.55rem; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); border-bottom: 1px solid var(--border); }
td { padding: 0.55rem; border-bottom: 1px solid var(--border); color: var(--text); vertical-align: top; }
td small { display: block; color: var(--muted); font-size: 0.72rem; }
tbody tr { cursor: pointer; }
tbody tr:hover, tbody tr.on { background: var(--pink-50); }
tbody tr:focus-visible { outline: 2px solid var(--pink-300); outline-offset: -2px; }
.mono { font-family: ui-monospace, 'Cascadia Code', Consolas, monospace; font-size: 0.8rem; }
.nowrap { white-space: nowrap; }
.break { overflow-wrap: anywhere; }
.hash { display: inline-flex; align-items: center; gap: 0.25rem; font-family: ui-monospace, Consolas, monospace; font-size: 0.78rem; }
.hash.ok { color: var(--low); }
.hash.bad { color: var(--high); }
.state { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.72rem; font-weight: 600; background: var(--pink-50); color: var(--muted); white-space: nowrap; }
.state[data-state='ANCHORED'] { background: rgba(46, 125, 50, 0.2); color: var(--low); }
.state[data-state='PENDING'] { background: var(--warn-bg); color: var(--warn-text); }
.state[data-state='FAILED'] { background: rgba(198, 40, 40, 0.2); color: var(--high); }
.empty { padding: 1.5rem; text-align: center; }
.pager { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.6rem; padding: 0.7rem 0.5rem 0.3rem; }
.pager div { display: flex; gap: 0.4rem; }

.detail { display: grid; gap: 0.8rem; position: sticky; top: 1rem; }
.detail-head { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
.detail h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.close { border: 0; background: none; color: var(--muted); font-size: 1.5rem; cursor: pointer; }
.verdict { display: flex; gap: 0.5rem; align-items: flex-start; padding: 0.7rem 0.8rem; border-radius: 12px; font-size: 0.86rem; }
.verdict span { display: grid; gap: 0.15rem; }
.verdict small { opacity: 0.85; }
dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.45rem 0.8rem; margin: 0; font-size: 0.84rem; }
dt { color: var(--muted); font-weight: 600; }
dd { margin: 0; color: var(--text); min-width: 0; }
dd small { color: var(--muted); }
dd .error { display: block; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }

@media (max-width: 1000px) {
  .layout.open { grid-template-columns: minmax(0, 1fr); }
  .detail { position: static; }
}
</style>
