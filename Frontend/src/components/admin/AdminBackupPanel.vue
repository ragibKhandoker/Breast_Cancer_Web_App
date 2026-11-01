<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { getBackupStatus, listBackups, createBackup, verifyBackup, runRestoreTest } from '../../services/adminService'
import { errorMessage } from '../../services/http'

// Encrypted database backups. Files stay on the server (never downloadable here); a restore test copies a backup
// into a separate *_restore_test database, checks it, and drops it. The live database is never overwritten.
const status = ref(null)
const backups = ref([])
const loading = ref(true)
const error = ref('')
const working = ref('')
const message = ref(null)
const report = ref(null)

const when = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—')
const size = (bytes) => (bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`)
const retentionText = computed(() => {
  const s = status.value
  if (!s) return ''
  const r = s.retention
  if (!s.retentionEnabled) return 'Off: old backups are kept until deleted by hand'
  if (!s.retentionValid) return 'Settings invalid: nothing is deleted'
  return `${r.daily} daily · ${r.weekly} weekly · ${r.monthly} monthly`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[status.value, backups.value] = await Promise.all([getBackupStatus(), listBackups()])
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function run(kind, fn) {
  working.value = kind
  message.value = null
  try {
    await fn()
  } catch (err) {
    message.value = { ok: false, text: errorMessage(err) }
  } finally {
    working.value = ''
  }
}

const create = () => run('create', async () => {
  const b = await createBackup()
  message.value = { ok: true, text: `Backup ${b.file} created (${b.documents} records).` }
  await load()
})
const verify = (file) => run(`verify:${file}`, async () => {
  await verifyBackup(file)
  message.value = { ok: true, text: `${file} passed its integrity check.` }
})
const restoreTest = (file) => run(`restore:${file}`, async () => {
  report.value = await runRestoreTest(file)
  await load()
})

onMounted(load)
</script>

<template>
  <div class="backups">
    <p v-if="error" class="alert error">{{ error }}</p>
    <p v-else-if="loading" class="muted">Loading…</p>
    <template v-else-if="status">
      <div class="cards">
        <div class="card stat">
          <span class="stat-icon"><AppIcon name="clock" :size="18" /></span>
          <span class="stat-label">Last backup</span>
          <span class="stat-value">{{ status.lastBackup ? when(status.lastBackup.createdAt) : 'None yet' }}</span>
        </div>
        <div class="card stat">
          <span class="stat-icon"><AppIcon name="file-text" :size="18" /></span>
          <span class="stat-label">Backups stored</span>
          <span class="stat-value">{{ status.backupCount }}</span>
        </div>
        <div class="card stat">
          <span class="stat-icon"><AppIcon name="refresh" :size="18" /></span>
          <span class="stat-label">Retention</span>
          <span class="stat-value small">{{ retentionText }}</span>
        </div>
        <div class="card stat">
          <span class="stat-icon"><AppIcon name="shield-check" :size="18" /></span>
          <span class="stat-label">Last restore test</span>
          <span class="stat-value small">
            <template v-if="status.lastRestoreTest">
              <span :class="status.lastRestoreTest.outcome === 'PASSED' ? 'ok' : 'bad'">{{ status.lastRestoreTest.outcome }}</span>
              · {{ when(status.lastRestoreTest.at) }}
            </template>
            <template v-else>Never run</template>
          </span>
        </div>
      </div>

      <div class="card actions-card">
        <div>
          <strong>Encryption: {{ status.encryptionConfigured ? 'AES-256 + HMAC-SHA256' : 'not set up' }}</strong>
          <span class="muted small">
            {{ status.encryptionConfigured ? 'Files stay on the server, outside the website.' : 'Set BACKUP_ENCRYPTION_KEY on the server first.' }}
          </span>
        </div>
        <button type="button" class="btn btn-primary btn-sm" :disabled="!status.encryptionConfigured || !!working" @click="create">
          <AppIcon name="upload" :size="15" /> {{ working === 'create' ? 'Backing up…' : 'Create backup' }}
        </button>
      </div>

      <p v-if="message" class="alert" :class="message.ok ? 'success' : 'error'" role="status">{{ message.text }}</p>

      <div v-if="report" class="card report" :class="report.passed ? 'ok' : 'bad'">
        <h3><AppIcon :name="report.passed ? 'check-circle' : 'alert-circle'" :size="18" /> Restore test {{ report.passed ? 'passed' : 'failed' }}</h3>
        <p class="small">{{ report.file }} → {{ report.targetDatabase }} {{ report.testDatabaseKept ? '(kept)' : '(dropped after the check)' }}</p>
        <ul class="counts">
          <li v-for="(n, c) in report.restored" :key="c"><span>{{ c }}</span><strong>{{ n }} / {{ report.expected[c] }}</strong></li>
        </ul>
        <p class="small">Audit events checked: {{ report.auditEventsChecked }} · changed: {{ report.auditEventsTampered }}</p>
        <ul v-if="report.problems.length" class="problems"><li v-for="p in report.problems" :key="p">{{ p }}</li></ul>
      </div>

      <div class="card list-card">
        <p v-if="!backups.length" class="muted empty">No backups yet.</p>
        <div v-else class="table-wrap">
          <table>
            <thead><tr><th>File</th><th>Created</th><th>Size</th><th>Records</th><th>SHA-256</th><th></th></tr></thead>
            <tbody>
              <tr v-for="b in backups" :key="b.file">
                <td class="mono">{{ b.file }}</td>
                <td class="nowrap">{{ when(b.createdAt) }}</td>
                <td>{{ size(b.sizeBytes) }}</td>
                <td>{{ b.documents }}</td>
                <td class="mono" :title="b.sha256">{{ b.sha256 ? `${b.sha256.slice(0, 12)}…` : '—' }}</td>
                <td class="row-actions">
                  <button type="button" class="btn btn-outline btn-sm" :disabled="!!working" @click="verify(b.file)">
                    {{ working === `verify:${b.file}` ? 'Checking…' : 'Verify' }}
                  </button>
                  <button v-if="status.restoreEnabled" type="button" class="btn btn-outline btn-sm" :disabled="!!working" @click="restoreTest(b.file)">
                    {{ working === `restore:${b.file}` ? 'Testing…' : 'Restore test' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!status.restoreEnabled" class="muted small note">Restore tests are off on this server. Run them on a test server (see docs/F20).</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.backups { display: grid; gap: 1rem; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.75rem; }
.stat { display: grid; gap: 0.2rem; padding: 1rem; }
.stat-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: var(--pink-100); color: var(--pink-600); margin-bottom: 0.3rem; }
.stat-label { font-size: 0.8rem; color: var(--muted); }
.stat-value { font-size: 1.1rem; font-weight: 700; color: var(--purple-900); }
.stat-value.small, .small { font-size: 0.86rem; }
.ok { color: var(--low); }
.bad { color: var(--high); }
.actions-card { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.75rem; padding: 1rem 1.1rem; }
.actions-card > div { display: grid; gap: 0.15rem; }
.actions-card strong { color: var(--purple-900); }
.report { display: grid; gap: 0.5rem; border-left: 4px solid currentColor; }
.report h3 { display: flex; align-items: center; gap: 0.4rem; margin: 0; font-size: 1rem; }
.report p { margin: 0; color: var(--text); }
.counts { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 0.3rem 1rem; margin: 0; padding: 0; list-style: none; font-size: 0.84rem; color: var(--text); }
.counts li { display: flex; justify-content: space-between; gap: 0.5rem; }
.problems { margin: 0; padding-left: 1.2rem; color: var(--high); font-size: 0.86rem; }
.list-card { padding: 0.5rem; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.84rem; }
th { text-align: left; padding: 0.6rem 0.55rem; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); border-bottom: 1px solid var(--border); }
td { padding: 0.55rem; border-bottom: 1px solid var(--border); color: var(--text); vertical-align: middle; }
.mono { font-family: ui-monospace, 'Cascadia Code', Consolas, monospace; font-size: 0.8rem; }
.nowrap { white-space: nowrap; }
.row-actions { display: flex; gap: 0.4rem; justify-content: flex-end; }
.empty, .note { padding: 1rem; margin: 0; }
</style>
