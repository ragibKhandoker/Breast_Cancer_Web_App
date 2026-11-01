<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { getReportIntegrity, verifyReportIntegrity } from '../../services/predictionService'
import { errorMessage } from '../../services/http'

// Has this report's PDF changed since it was made? The backend recomputes its SHA-256 and checks it against the
// recorded hash and (when set up) the blockchain proof. Read-only: nothing here can change the record.
const props = defineProps({
  scanId: { type: String, required: true },
  // Bumped by the page after a download, since the first download creates and records the report.
  refreshKey: { type: Number, default: 0 },
})

const STATES = {
  VERIFIED: { label: 'Verified', tone: 'ok', icon: 'check-circle' },
  OFF_CHAIN_VERIFIED: { label: 'Verified (off-chain)', tone: 'ok', icon: 'check-circle' },
  PENDING: { label: 'Pending', tone: 'wait', icon: 'clock' },
  FAILED: { label: 'Verification failed', tone: 'bad', icon: 'alert-circle' },
  NOT_RECORDED: { label: 'Not recorded', tone: 'idle', icon: 'file-text' },
  NO_REPORT: { label: 'No report yet', tone: 'idle', icon: 'file-text' },
}

const result = ref(null)
const loading = ref(true)
const checking = ref(false)
const error = ref('')

const state = computed(() => STATES[result.value?.status] ?? STATES.NO_REPORT)
const canVerify = computed(() => result.value && !['NO_REPORT', 'NOT_RECORDED'].includes(result.value.status))
const when = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '')
const shortRef = (ref) => (ref && ref.length > 22 ? `${ref.slice(0, 12)}…${ref.slice(-8)}` : ref)

async function load() {
  loading.value = true
  error.value = ''
  try {
    result.value = await getReportIntegrity(props.scanId)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function verify() {
  checking.value = true
  error.value = ''
  try {
    result.value = await verifyReportIntegrity(props.scanId)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    checking.value = false
  }
}

watch(() => [props.scanId, props.refreshKey], load)
onMounted(load)
</script>

<template>
  <div class="card integrity">
    <div class="head">
      <h2>Report integrity</h2>
      <span v-if="result" class="badge" :class="state.tone"><AppIcon :name="state.icon" :size="14" /> {{ state.label }}</span>
    </div>
    <p v-if="loading" class="muted small">Checking…</p>
    <template v-else-if="result">
      <p class="message" role="status">{{ result.message }}</p>
      <dl v-if="result.transactionReference || result.recordedAt">
        <template v-if="result.recordedAt"><dt>Recorded</dt><dd>{{ when(result.recordedAt) }}</dd></template>
        <template v-if="result.transactionReference">
          <dt>Blockchain</dt>
          <dd><span class="mono" :title="result.transactionReference">{{ shortRef(result.transactionReference) }}</span> <small>{{ result.network }}</small></dd>
        </template>
        <template v-if="result.anchoredAt"><dt>Anchored</dt><dd>{{ when(result.anchoredAt) }}</dd></template>
        <dt>Checked</dt><dd>{{ when(result.checkedAt) }}</dd>
      </dl>
    </template>
    <p v-if="error" class="error small" role="alert">{{ error }}</p>
    <button v-if="canVerify" type="button" class="btn btn-outline btn-sm" :disabled="checking" @click="verify">
      <AppIcon name="shield-check" :size="15" /> {{ checking ? 'Verifying…' : 'Verify integrity' }}
    </button>
  </div>
</template>

<style scoped>
.integrity { display: grid; gap: 0.6rem; margin-top: 1rem; }
.head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem; }
.head h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.badge { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.2rem 0.65rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; }
.badge.ok { background: rgba(46, 125, 50, 0.2); color: var(--low); }
.badge.bad { background: rgba(198, 40, 40, 0.2); color: var(--high); }
.badge.wait { background: var(--warn-bg); color: var(--warn-text); }
.badge.idle { background: var(--pink-50); color: var(--muted); }
.message { margin: 0; color: var(--text); font-size: 0.9rem; line-height: 1.5; }
dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.3rem 0.8rem; margin: 0; font-size: 0.84rem; }
dt { color: var(--muted); font-weight: 600; }
dd { margin: 0; color: var(--text); overflow-wrap: anywhere; }
dd small { color: var(--muted); }
.mono { font-family: ui-monospace, 'Cascadia Code', Consolas, monospace; }
.small { font-size: 0.86rem; margin: 0; }
.btn { width: fit-content; gap: 0.35rem; }
</style>
