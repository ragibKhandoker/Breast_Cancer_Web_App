<script setup>
import { ref, computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { DAYS, formatTime } from '../../appointments/format'

// Weekly consultation days and the time slots offered on each of them. v-model: { availableDays, timeSlots }.
// Used by the profile form and the surgeon dashboard. The server checks and sorts the values again.
const props = defineProps({
  modelValue: { type: Object, required: true },
  errors: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue'])

const MAX_SLOTS = 24
const days = computed(() => props.modelValue.availableDays ?? [])
const slots = computed(() => props.modelValue.timeSlots ?? [])

function update(patch) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}

function toggleDay(id) {
  const next = days.value.includes(id) ? days.value.filter((d) => d !== id) : [...days.value, id]
  update({ availableDays: DAYS.map((d) => d.id).filter((d) => next.includes(d)) })
}

const newSlot = ref('')
const slotError = ref('')
function addSlot(value = newSlot.value) {
  slotError.value = ''
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(value)) {
    slotError.value = 'Choose a time first.'
    return
  }
  if (slots.value.includes(value)) {
    slotError.value = `${formatTime(value)} is already in the list.`
    return
  }
  if (slots.value.length >= MAX_SLOTS) {
    slotError.value = `At most ${MAX_SLOTS} slots a day.`
    return
  }
  update({ timeSlots: [...slots.value, value].sort() })
  newSlot.value = ''
}

function removeSlot(value) {
  update({ timeSlots: slots.value.filter((s) => s !== value) })
}

// Quick ways to fill a typical chamber session.
const PRESETS = [
  { label: 'Morning (9–12, hourly)', times: ['09:00', '10:00', '11:00'] },
  { label: 'Evening (5–8, every 30 min)', times: ['17:00', '17:30', '18:00', '18:30', '19:00', '19:30'] },
]
function applyPreset(times) {
  update({ timeSlots: [...new Set([...slots.value, ...times])].sort().slice(0, MAX_SLOTS) })
}
</script>

<template>
  <div class="availability-editor">
    <fieldset class="block">
      <legend>Available days</legend>
      <div class="days">
        <label v-for="d in DAYS" :key="d.id" class="day-chip" :class="{ on: days.includes(d.id) }">
          <input type="checkbox" :checked="days.includes(d.id)" :value="d.id" @change="toggleDay(d.id)" />
          <span class="full">{{ d.label }}</span><span class="short">{{ d.short }}</span>
        </label>
      </div>
      <p class="field-error">{{ errors.availableDays }}</p>
    </fieldset>

    <fieldset class="block">
      <legend>Time slots <span class="muted">(each consultation day)</span></legend>
      <div class="slot-list">
        <span v-for="s in slots" :key="s" class="slot-chip">
          <AppIcon name="clock" :size="13" /> {{ formatTime(s) }}
          <button type="button" :aria-label="`Remove ${formatTime(s)}`" @click="removeSlot(s)"><AppIcon name="close" :size="13" /></button>
        </span>
        <span v-if="!slots.length" class="muted none">No time slots yet.</span>
      </div>
      <div class="add-row">
        <input v-model="newSlot" type="time" step="900" aria-label="New time slot" @keydown.enter.prevent="addSlot()" />
        <button type="button" class="btn btn-outline btn-sm" @click="addSlot()">Add slot</button>
        <button v-for="p in PRESETS" :key="p.label" type="button" class="preset" @click="applyPreset(p.times)">+ {{ p.label }}</button>
      </div>
      <p class="field-error">{{ slotError || errors.timeSlots }}</p>
    </fieldset>
  </div>
</template>

<style scoped>
.block { border: 0; padding: 0; margin: 0 0 0.6rem; min-width: 0; }
legend { padding: 0; margin-bottom: 0.5rem; font-weight: 600; color: var(--purple-900); font-size: 0.95rem; }
legend .muted { font-weight: 400; font-size: 0.82rem; }
.days { display: flex; flex-wrap: wrap; gap: 0.45rem; }
.day-chip {
  position: relative; padding: 0.45rem 0.9rem; border-radius: 999px; border: 1.5px solid var(--border); background: #fff;
  color: var(--purple-900); font-size: 0.86rem; font-weight: 500; cursor: pointer; user-select: none; transition: all 0.15s;
}
.day-chip input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.day-chip:hover { border-color: var(--pink-300); }
.day-chip.on { background: var(--pink-600); border-color: var(--pink-600); color: #fff; }
.day-chip:has(input:focus-visible) { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.short { display: none; }
.slot-list { display: flex; flex-wrap: wrap; gap: 0.4rem; min-height: 2.1rem; align-items: center; }
.slot-chip {
  display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.4rem 0.3rem 0.7rem; border-radius: 999px;
  background: #f6f0ff; color: #4a2a8a; font-size: 0.84rem; font-weight: 600;
}
.slot-chip button { display: grid; place-items: center; width: 22px; height: 22px; border: 0; border-radius: 50%; background: rgba(74, 42, 138, 0.1); color: inherit; cursor: pointer; }
.slot-chip button:hover { background: rgba(198, 40, 40, 0.15); color: var(--high); }
.none { font-size: 0.86rem; }
.add-row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin-top: 0.7rem; }
.add-row input { padding: 0.5rem 0.7rem; border: 1.5px solid var(--border); border-radius: 10px; font: inherit; color: var(--text); }
.add-row input:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
.preset { border: 1px dashed var(--pink-300); background: var(--pink-50); color: var(--pink-600); border-radius: 999px; padding: 0.35rem 0.8rem; font: 500 0.8rem 'Poppins', sans-serif; cursor: pointer; }
.preset:hover { background: #fff; }
@media (max-width: 560px) {
  .full { display: none; }
  .short { display: inline; }
}
</style>
