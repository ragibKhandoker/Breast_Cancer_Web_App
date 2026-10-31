<script setup>
import { ref, computed, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { getOverview } from '../../services/adminService'
import { errorMessage } from '../../services/http'

const emit = defineEmits(['open-tab'])

const data = ref(null)
const loading = ref(true)
const error = ref('')
const refreshedAt = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getOverview()
    refreshedAt.value = new Date()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

const tiles = computed(() => {
  if (!data.value) return []
  const { users, predictions, messages } = data.value
  return [
    { icon: 'users', label: 'Users', value: users.total, note: `+${users.newLast7Days} this week` },
    { icon: 'microscope', label: 'Analyses', value: predictions.total, note: `+${predictions.last7Days} this week` },
    { icon: 'file-text', label: 'Shared with doctors', value: predictions.shared, note: 'reports patients shared' },
    { icon: 'mail', label: 'Unread messages', value: messages.unread, note: `${messages.total} in total`, tab: messages.unread ? 'messages' : null },
  ]
})

// Work waiting for the administrator, each linking to the tab where it is done.
const todo = computed(() => {
  const s = data.value?.support
  if (!s) return []
  return [
    { icon: 'users', label: 'Patients to verify', value: s.pendingVerification, note: `${s.verified} verified · ${s.rejected} need correction`, tab: 'verification' },
    { icon: 'shield-check', label: 'Password requests', value: s.openPasswordRequests, note: 'from “Forgot password?”', tab: 'passwords' },
    { icon: 'mail', label: 'Unread messages', value: data.value.messages.unread, note: 'from the Contact page', tab: 'messages' },
  ]
})

const roleRows = computed(() => {
  if (!data.value) return []
  const { users } = data.value
  return [
    { label: 'Patients', value: users.patients },
    { label: 'Doctors', value: users.doctors },
    { label: 'Administrators', value: users.admins },
    { label: 'Disabled accounts', value: users.disabled },
  ]
})

// Benign / malignant share of all analyses, for the split bar.
const split = computed(() => {
  const p = data.value?.predictions
  const total = (p?.benign ?? 0) + (p?.malignant ?? 0)
  return total ? { benign: Math.round((p.benign / total) * 100), malignant: 100 - Math.round((p.benign / total) * 100) } : null
})

// Uploads-per-day chart: one series, bars scaled to the busiest day (at least 1 so empty weeks stay flat).
const days = computed(() => data.value?.uploadsLast14Days ?? [])
const maxDay = computed(() => Math.max(1, ...days.value.map((d) => d.count)))
const totalFortnight = computed(() => days.value.reduce((sum, d) => sum + d.count, 0))
const hovered = ref(null)

function dayLabel(iso, opts) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, opts)
}

function uptime(seconds) {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return d ? `${d}d ${h}h` : h ? `${h}h ${m}m` : `${m}m`
}

onMounted(load)
</script>

<template>
  <div class="overview">
    <div class="toolbar">
      <p class="muted small">
        {{ refreshedAt ? `Updated ${refreshedAt.toLocaleTimeString()}` : '' }}
      </p>
      <button type="button" class="btn btn-outline btn-sm" :disabled="loading" @click="load">
        {{ loading ? 'Refreshing…' : 'Refresh' }}
      </button>
    </div>

    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <p v-else-if="loading && !data" class="muted">Loading site overview…</p>

    <template v-if="data">
      <section v-if="todo.length" class="attention" aria-labelledby="todo-title">
        <h3 id="todo-title">Needs your attention</h3>
        <div class="todo-grid">
          <button v-for="item in todo" :key="item.tab" type="button" class="todo" :class="{ calm: !item.value }" @click="emit('open-tab', item.tab)">
            <span class="todo-icon"><AppIcon :name="item.icon" :size="20" /></span>
            <span class="todo-text">
              <strong>{{ item.value }}</strong>
              <span class="todo-label">{{ item.label }}</span>
              <span class="todo-note">{{ item.note }}</span>
            </span>
            <AppIcon name="arrow-right" :size="18" class="todo-go" />
          </button>
        </div>
      </section>

      <div class="tiles">
        <component
          :is="tile.tab ? 'button' : 'div'"
          v-for="tile in tiles"
          :key="tile.label"
          :type="tile.tab ? 'button' : undefined"
          class="card tile"
          :class="{ clickable: tile.tab }"
          @click="tile.tab && emit('open-tab', tile.tab)"
        >
          <span class="tile-icon"><AppIcon :name="tile.icon" :size="20" /></span>
          <span class="tile-label">{{ tile.label }}</span>
          <strong class="tile-value">{{ tile.value.toLocaleString() }}</strong>
          <span class="tile-note">{{ tile.note }}</span>
        </component>
      </div>

      <div class="grid-2">
        <!-- Uploads per day -->
        <section class="card chart-card" aria-labelledby="chart-title">
          <header class="card-head">
            <h3 id="chart-title">Analyses per day</h3>
            <span class="muted small">Last 14 days · {{ totalFortnight }} total</span>
          </header>
          <div class="chart" role="img" :aria-label="`Analyses per day over the last 14 days, ${totalFortnight} in total. A table follows.`">
            <div class="chart-grid" aria-hidden="true">
              <span class="grid-label">{{ maxDay }}</span>
              <span class="grid-label">0</span>
            </div>
            <div class="bars">
              <div
                v-for="(day, i) in days"
                :key="day.date"
                class="bar-slot"
                @mouseenter="hovered = i"
                @mouseleave="hovered = null"
                @focus="hovered = i"
                @blur="hovered = null"
                tabindex="0"
              >
                <div class="bar" :class="{ empty: !day.count, active: hovered === i }" :style="{ height: `${(day.count / maxDay) * 100}%` }"></div>
                <div v-if="hovered === i" class="tooltip" role="status">
                  <strong>{{ day.count }}</strong> {{ day.count === 1 ? 'analysis' : 'analyses' }}
                  <span>{{ dayLabel(day.date, { weekday: 'short', day: 'numeric', month: 'short' }) }}</span>
                </div>
              </div>
            </div>
            <div class="x-axis" aria-hidden="true">
              <span v-for="(day, i) in days" :key="day.date">{{ i % 2 === 1 ? '' : dayLabel(day.date, { day: 'numeric', month: 'short' }) }}</span>
            </div>
          </div>
          <table class="sr-only">
            <caption>Analyses per day, last 14 days</caption>
            <thead><tr><th scope="col">Date</th><th scope="col">Analyses</th></tr></thead>
            <tbody><tr v-for="day in days" :key="day.date"><td>{{ day.date }}</td><td>{{ day.count }}</td></tr></tbody>
          </table>
        </section>

        <!-- Breakdown -->
        <section class="card" aria-labelledby="breakdown-title">
          <header class="card-head"><h3 id="breakdown-title">Breakdown</h3></header>
          <ul class="rows">
            <li v-for="row in roleRows" :key="row.label"><span>{{ row.label }}</span><strong>{{ row.value }}</strong></li>
          </ul>
          <h4>Analysis results</h4>
          <template v-if="split">
            <div class="split" aria-hidden="true">
              <span class="benign" :style="{ width: split.benign + '%' }"></span>
              <span class="malignant" :style="{ width: split.malignant + '%' }"></span>
            </div>
            <ul class="rows">
              <li><span><i class="key benign"></i>Leaning benign</span><strong>{{ data.predictions.benign }} · {{ split.benign }}%</strong></li>
              <li><span><i class="key malignant"></i>Leaning malignant</span><strong>{{ data.predictions.malignant }} · {{ split.malignant }}%</strong></li>
              <li v-if="data.predictions.demoMode"><span>Made by the untrained demo model</span><strong>{{ data.predictions.demoMode }}</strong></li>
              <li v-if="data.predictions.withoutOwner"><span>From before accounts (no owner)</span><strong>{{ data.predictions.withoutOwner }}</strong></li>
            </ul>
          </template>
          <p v-else class="muted small">No analyses yet.</p>
        </section>
      </div>

      <div class="grid-2">
        <!-- System -->
        <section class="card" aria-labelledby="system-title">
          <header class="card-head">
            <h3 id="system-title">System status</h3>
            <span class="status-ok"><AppIcon name="shield-check" :size="16" /> Online</span>
          </header>
          <ul class="rows">
            <li><span>Database</span><strong>{{ data.system.databaseName }} · {{ data.system.databaseLatencyMs }} ms</strong></li>
            <li><span>AI model</span><strong class="wrap">{{ data.system.model }}</strong></li>
            <li><span>Server uptime</span><strong>{{ uptime(data.system.uptimeSeconds) }}</strong></li>
            <li><span>Started</span><strong>{{ new Date(data.system.startedAt).toLocaleString() }}</strong></li>
            <li><span>Spring Boot / Java</span><strong>{{ data.system.springBootVersion }} / {{ data.system.javaVersion }}</strong></li>
          </ul>
        </section>

        <!-- Privacy + shortcuts -->
        <section class="card note-card" aria-labelledby="privacy-title">
          <header class="card-head"><h3 id="privacy-title">Patient privacy</h3></header>
          <p class="small">
            These figures are totals only. Individual scans and reports are visible to the patient and to the doctors
            they choose, never to administrators.
          </p>
          <div class="shortcuts">
            <button type="button" class="btn btn-outline btn-sm" @click="emit('open-tab', 'users')">Manage users</button>
            <button type="button" class="btn btn-outline btn-sm" @click="emit('open-tab', 'messages')">Read messages</button>
            <button type="button" class="btn btn-outline btn-sm" @click="emit('open-tab', 'settings')">Website settings</button>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.small { font-size: 0.86rem; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-bottom: 1rem; }
.toolbar p { margin: 0; }

.attention {
  margin-bottom: 1rem; padding: 1.25rem; border-radius: 20px; color: #fff;
  background: radial-gradient(circle at 90% 0%, rgba(255, 255, 255, 0.18), transparent 45%), linear-gradient(120deg, var(--purple-900), #6b3fb5 60%, #b5179e);
  box-shadow: 0 16px 36px rgba(42, 22, 80, 0.22);
}
.attention h3 { margin: 0 0 0.85rem; font-size: 1rem; color: #fff; letter-spacing: 0.02em; }
.todo-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.75rem; }
.todo {
  display: flex; align-items: center; gap: 0.8rem; padding: 0.9rem 1rem; border: 1px solid rgba(255, 255, 255, 0.25); border-radius: 16px;
  background: rgba(255, 255, 255, 0.12); color: #fff; text-align: left; font: inherit; cursor: pointer; transition: background-color 0.15s, transform 0.15s;
}
.todo:hover { background: rgba(255, 255, 255, 0.2); transform: translateY(-2px); }
.todo:focus-visible { outline: 3px solid #fff; outline-offset: 2px; }
.todo-icon { flex: none; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 13px; background: #fff; color: var(--pink-600); }
.todo.calm .todo-icon { color: #16a34a; }
.todo-text { flex: 1; display: grid; min-width: 0; line-height: 1.25; }
.todo-text strong { font-size: 1.5rem; }
.todo-label { font-weight: 600; font-size: 0.9rem; }
.todo-note { font-size: 0.76rem; opacity: 0.8; }
.todo-go { flex: none; opacity: 0.7; }
@media (max-width: 860px) { .todo-grid { grid-template-columns: 1fr; } }

.tiles { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; }
.tile { display: grid; gap: 0.15rem; text-align: left; font: inherit; color: inherit; }
.tile.clickable { cursor: pointer; transition: transform 0.15s, border-color 0.15s; }
.tile.clickable:hover { transform: translateY(-2px); border-color: var(--pink-300); }
.tile-icon { display: grid; place-items: center; width: 38px; height: 38px; margin-bottom: 0.4rem; border-radius: 12px; background: var(--pink-50); color: var(--pink-600); }
.tile-label { font-size: 0.85rem; color: var(--muted); }
.tile-value { font-size: 1.9rem; color: var(--purple-900); line-height: 1.15; }
.tile-note { font-size: 0.8rem; color: var(--muted); }

.grid-2 { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); gap: 1rem; margin-top: 1rem; align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; margin-bottom: 0.75rem; }
.card-head h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
h4 { margin: 1rem 0 0.5rem; font-size: 0.9rem; color: var(--purple-900); }

/* Bar chart: thin bars with rounded tops on a recessive baseline; hover shows the exact count. */
.chart { position: relative; padding-left: 1.75rem; }
.chart-grid { position: absolute; inset: 0 0 1.5rem 0; display: flex; flex-direction: column; justify-content: space-between; pointer-events: none; }
.grid-label { font-size: 0.72rem; color: var(--muted); line-height: 1; }
.bars { display: grid; grid-template-columns: repeat(14, 1fr); gap: 2px; align-items: end; height: 180px; border-bottom: 1px solid var(--border); }
.bar-slot { position: relative; height: 100%; display: flex; align-items: flex-end; justify-content: center; cursor: default; outline: none; }
.bar-slot:focus-visible { box-shadow: inset 0 0 0 2px var(--pink-300); border-radius: 6px; }
.bar { width: min(70%, 22px); min-height: 0; border-radius: 4px 4px 0 0; background: var(--pink-600); transition: height 0.5s cubic-bezier(0.22, 1, 0.36, 1), background-color 0.15s; }
.bar.empty { height: 2px !important; background: var(--pink-100); }
.bar.active { background: #a3124f; }
.tooltip {
  position: absolute; bottom: calc(100% + 6px); left: 50%; transform: translateX(-50%); z-index: 2;
  display: grid; padding: 0.4rem 0.6rem; border-radius: 8px; background: var(--purple-950); color: #fff;
  font-size: 0.78rem; white-space: nowrap; box-shadow: 0 6px 16px rgba(42, 22, 80, 0.25); pointer-events: none;
}
.bars .bar-slot:first-child .tooltip { left: 0; transform: none; }
.bars .bar-slot:last-child .tooltip { left: auto; right: 0; transform: none; }
.tooltip span { color: rgba(255, 255, 255, 0.75); }
.x-axis { display: grid; grid-template-columns: repeat(14, 1fr); gap: 2px; margin-top: 0.4rem; font-size: 0.7rem; color: var(--muted); text-align: center; white-space: nowrap; }

.rows { list-style: none; margin: 0; padding: 0; }
.rows li { display: flex; justify-content: space-between; gap: 1rem; padding: 0.45rem 0; border-bottom: 1px solid var(--border); font-size: 0.9rem; }
.rows li:last-child { border-bottom: 0; }
.rows strong { color: var(--purple-900); text-align: right; }
.rows .wrap { overflow-wrap: anywhere; }
.split { display: flex; gap: 2px; height: 10px; border-radius: 5px; overflow: hidden; background: var(--pink-100); }
.split span { display: block; height: 100%; }
.benign { background: var(--low); }
.malignant { background: var(--high); }
.key { display: inline-block; width: 10px; height: 10px; margin-right: 0.45rem; border-radius: 3px; vertical-align: -1px; }

.status-ok { display: inline-flex; align-items: center; gap: 0.3rem; color: var(--low); font-size: 0.85rem; font-weight: 600; }
.note-card .small { margin: 0 0 1rem; line-height: 1.55; }
.shortcuts { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

@media (max-width: 1000px) { .tiles { grid-template-columns: repeat(2, minmax(0, 1fr)); } .grid-2 { grid-template-columns: 1fr; } }
@media (max-width: 480px) {
  .tiles { gap: 0.6rem; }
  .tile { padding: 0.9rem; }
  .tile-value { font-size: 1.5rem; }
  .tile-note { font-size: 0.74rem; }
  .x-axis { font-size: 0.6rem; }
}
</style>
