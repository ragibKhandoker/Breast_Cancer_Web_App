<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { getSlots } from '../../services/surgeonService'
import { errorMessage } from '../../services/http'
import { DAYS, formatDate, formatTime, todayIso } from '../../appointments/format'

// Interactive calendar of a Breast Surgeon's free slots: pick a day in the month grid, then a time.
// v-model is { date, time } or null. `current` marks the slot an appointment already holds (for rescheduling).
// The server re-checks the slot when booking, so a slot taken in the meantime is refused there.
const props = defineProps({
  surgeonId: { type: String, required: true },
  modelValue: { type: Object, default: null },
  days: { type: Number, default: 28 },
  current: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'loaded'])

const calendar = ref([])
const loading = ref(true)
const error = ref('')
const activeDate = ref(props.modelValue?.date ?? '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    calendar.value = await getSlots(props.surgeonId, props.days)
    emit('loaded', calendar.value)
    if (!byDate.value[activeDate.value]) {
      activeDate.value = calendar.value.find((d) => d.slots.some((s) => s.available))?.date ?? ''
    }
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}
defineExpose({ reload: load })

const byDate = computed(() => Object.fromEntries(calendar.value.map((d) => [d.date, d])))
const freeCount = (date) => byDate.value[date]?.slots.filter((s) => s.available).length ?? 0

// Every day from today, padded so the grid starts on a Monday.
const cells = computed(() => {
  const start = new Date(`${todayIso()}T00:00`)
  const list = []
  const pad = (start.getDay() + 6) % 7
  for (let i = 0; i < pad; i++) list.push(null)
  for (let i = 0; i < props.days; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    const iso = todayIso(d)
    list.push({ iso, day: d.getDate(), month: d.toLocaleDateString('en-GB', { month: 'short' }), offered: !!byDate.value[iso], free: freeCount(iso) })
  }
  return list
})

const activeDay = computed(() => byDate.value[activeDate.value] ?? null)
const groups = computed(() => {
  const slots = activeDay.value?.slots ?? []
  const part = (t) => (Number(t.slice(0, 2)) < 12 ? 'Morning' : Number(t.slice(0, 2)) < 17 ? 'Afternoon' : 'Evening')
  return ['Morning', 'Afternoon', 'Evening']
    .map((name) => ({ name, slots: slots.filter((s) => part(s.time) === name) }))
    .filter((g) => g.slots.length)
})

function pickDate(cell) {
  if (!cell?.offered) return
  activeDate.value = cell.iso
  if (props.modelValue?.date !== cell.iso) emit('update:modelValue', null)
}

function pickTime(slot) {
  if (!slot.available) return
  emit('update:modelValue', { date: activeDate.value, time: slot.time })
}

const isCurrent = (time) => props.current?.date === activeDate.value && props.current?.time === time
const isChosen = (time) => props.modelValue?.date === activeDate.value && props.modelValue?.time === time

watch(() => props.surgeonId, load)
onMounted(load)
</script>

<template>
  <div class="availability-calendar">
    <div v-if="loading" class="state"><span class="dot-spinner" aria-hidden="true"></span> Loading available dates…</div>
    <div v-else-if="error" class="alert error" role="alert">
      {{ error }} <button type="button" class="link-btn" @click="load">Try again</button>
    </div>
    <div v-else-if="!calendar.length" class="state empty">
      <AppIcon name="calendar" :size="22" />
      No consultation days in the next {{ days }} days.
    </div>
    <div v-else class="layout">
      <div class="month" aria-label="Choose a date">
        <div class="weekdays" aria-hidden="true">
          <span v-for="d in DAYS" :key="d.id">{{ d.short }}</span>
        </div>
        <div class="grid" role="group" aria-label="Dates">
          <template v-for="(cell, i) in cells" :key="cell?.iso ?? `pad-${i}`">
            <span v-if="!cell" class="pad" aria-hidden="true"></span>
            <button
              v-else
              type="button"
              class="day"
              :class="{ offered: cell.offered, full: cell.offered && !cell.free, active: cell.iso === activeDate }"
              :disabled="!cell.offered"
              :aria-pressed="cell.iso === activeDate"
              :aria-label="`${formatDate(cell.iso)}: ${!cell.offered ? 'no consultations' : cell.free ? `${cell.free} free slots` : 'fully booked'}`"
              :data-date="cell.iso"
              @click="pickDate(cell)"
            >
              <span class="num">{{ cell.day }}</span>
              <span v-if="cell.day === 1 || i === cells.findIndex(Boolean)" class="mon">{{ cell.month }}</span>
              <span v-if="cell.offered" class="pip" aria-hidden="true"></span>
            </button>
          </template>
        </div>
        <p class="legend">
          <span><i class="sw free"></i> Slots free</span>
          <span><i class="sw full"></i> Fully booked</span>
          <span><i class="sw off"></i> No consultations</span>
        </p>
      </div>

      <div class="times">
        <h4>{{ activeDay ? formatDate(activeDay.date) : 'Choose a date' }}</h4>
        <p v-if="!activeDay" class="muted small">Pick a highlighted day to see its time slots.</p>
        <p v-else-if="!freeCount(activeDay.date)" class="muted small">Every slot on this day is taken. Try another day.</p>
        <div v-for="group in groups" :key="group.name" class="group">
          <p class="group-name">{{ group.name }}</p>
          <div class="slots">
            <button
              v-for="slot in group.slots"
              :key="slot.time"
              type="button"
              class="slot"
              :class="{ chosen: isChosen(slot.time), current: isCurrent(slot.time) }"
              :disabled="!slot.available"
              :aria-pressed="isChosen(slot.time)"
              :data-time="slot.time"
              @click="pickTime(slot)"
            >
              <AppIcon name="clock" :size="14" />
              {{ formatTime(slot.time) }}
              <span v-if="isCurrent(slot.time)" class="slot-note">current</span>
              <span v-else-if="!slot.available" class="slot-note">booked</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.state { display: flex; align-items: center; gap: 0.6rem; padding: 1.2rem; border-radius: 16px; background: var(--pink-50); color: var(--muted); }
.state.empty :deep(svg) { color: var(--pink-600); }
.dot-spinner { width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--pink-100); border-top-color: var(--pink-600); animation: spin 0.8s linear infinite; }
.link-btn { background: none; border: 0; color: inherit; font: inherit; font-weight: 600; text-decoration: underline; cursor: pointer; }

.layout { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); gap: 1.4rem; }
.weekdays, .grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 0.35rem; }
.weekdays span { text-align: center; font-size: 0.72rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); padding-bottom: 0.2rem; }
.day {
  position: relative; display: grid; place-items: center; aspect-ratio: 1; min-height: 40px; border-radius: 12px;
  border: 1.5px solid transparent; background: #f6f3f8; color: #b3abc4; font: 600 0.92rem 'Poppins', sans-serif; cursor: not-allowed;
}
.day.offered { background: #fff; border-color: var(--border); color: var(--purple-900); cursor: pointer; transition: border-color 0.15s, transform 0.15s; }
.day.offered:hover { border-color: var(--pink-300); transform: translateY(-1px); }
.day.full { color: var(--muted); background: #fbf7fa; }
.day.active { background: linear-gradient(135deg, var(--pink-600), #b5179e); border-color: transparent; color: #fff; box-shadow: 0 8px 18px rgba(216, 27, 106, 0.28); }
.day:focus-visible, .slot:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.mon { position: absolute; top: 3px; left: 0; right: 0; text-align: center; font-size: 0.58rem; font-weight: 600; text-transform: uppercase; opacity: 0.75; }
.pip { position: absolute; bottom: 5px; width: 6px; height: 6px; border-radius: 50%; background: #2e9e5b; }
.day.full .pip { background: #c9c1d6; }
.day.active .pip { background: #fff; }
.legend { display: flex; flex-wrap: wrap; gap: 0.9rem; margin: 0.8rem 0 0; font-size: 0.76rem; color: var(--muted); }
.legend span { display: inline-flex; align-items: center; gap: 0.35rem; }
.sw { display: inline-block; width: 10px; height: 10px; border-radius: 50%; }
.sw.free { background: #2e9e5b; }
.sw.full { background: #c9c1d6; }
.sw.off { background: #ece7f1; }

.times h4 { margin: 0 0 0.6rem; color: var(--purple-900); font-size: 1rem; }
.small { font-size: 0.86rem; margin: 0; }
.group + .group { margin-top: 0.8rem; }
.group-name { margin: 0 0 0.4rem; font-size: 0.72rem; font-weight: 700; letter-spacing: 0.1em; text-transform: uppercase; color: var(--muted); }
.slots { display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 0.45rem; }
.slot {
  display: inline-flex; align-items: center; justify-content: center; gap: 0.35rem; flex-wrap: wrap; padding: 0.55rem 0.5rem;
  border-radius: 12px; border: 1.5px solid var(--border); background: #fff; color: var(--purple-900);
  font: 600 0.86rem 'Poppins', sans-serif; cursor: pointer; transition: border-color 0.15s, background-color 0.15s;
}
.slot:hover:not(:disabled) { border-color: var(--pink-600); background: var(--pink-50); }
.slot:disabled { cursor: not-allowed; color: #b3abc4; background: #f8f6fa; text-decoration: line-through; }
.slot.current { text-decoration: none; border-style: dashed; }
.slot.chosen { background: var(--pink-600); border-color: var(--pink-600); color: #fff; }
.slot-note { font-size: 0.66rem; font-weight: 500; text-transform: uppercase; letter-spacing: 0.05em; }

@media (max-width: 720px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
