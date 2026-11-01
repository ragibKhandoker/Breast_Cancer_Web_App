<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import PinkRibbon from '../common/PinkRibbon.vue'
import { formatFee, formatTime, initials, DAYS } from '../../appointments/format'
import { photoSrc } from '../../services/surgeonService'

// One approved Breast Surgeon in the Find a Breast Surgeon listing, laid out like a clinic's "Meet our specialist"
// poster: a banner with the photo, consultation days and hours, then the key facts and the two actions.
const props = defineProps({ surgeon: { type: Object, required: true } })

const years = computed(() => props.surgeon.experienceYears ?? 0)
const weekDays = computed(() => DAYS.map((d) => ({ ...d, on: (props.surgeon.availableDays ?? []).includes(d.id) })))
const languages = computed(() => (props.surgeon.languages ?? []).join(', '))
const hours = computed(() => {
  const slots = [...(props.surgeon.timeSlots ?? [])].sort()
  if (!slots.length) return ''
  return slots.length === 1 ? formatTime(slots[0]) : `${formatTime(slots[0])} – ${formatTime(slots.at(-1))}`
})

const photoFailed = ref(false)
watch(() => props.surgeon.photoUrl, () => (photoFailed.value = false))
const photo = computed(() => (photoFailed.value ? '' : photoSrc(props.surgeon.photoUrl)))
</script>

<template>
  <article class="surgeon-card">
    <!-- Poster banner -->
    <div class="banner">
      <div class="poster">
        <p class="brand"><PinkRibbon :size="14" /> BreastCare</p>
        <p class="script">Meet our specialist</p>
        <p class="poster-name">{{ surgeon.fullName }}</p>
        <span class="poster-tag">{{ surgeon.specialty }}</span>
        <div class="hours">
          <span class="hours-title"><AppIcon name="calendar" :size="12" /> Consultation days</span>
          <span class="days" :aria-label="`Consultation days: ${weekDays.filter((d) => d.on).map((d) => d.label).join(', ')}`">
            <span v-for="d in weekDays" :key="d.id" :class="{ on: d.on }" aria-hidden="true">{{ d.short.slice(0, 2) }}</span>
          </span>
          <span v-if="hours" class="time"><AppIcon name="clock" :size="12" /> {{ hours }}</span>
        </div>
      </div>
      <div class="portrait">
        <img v-if="photo" :src="photo" :alt="`Photo of ${surgeon.fullName}`" @error="photoFailed = true" />
        <span v-else class="initials" aria-hidden="true">{{ initials(surgeon.fullName) }}</span>
      </div>
      <span class="verified"><AppIcon name="shield-check" :size="12" /> Verified</span>
    </div>

    <!-- Details -->
    <div class="body">
      <p class="eyebrow">{{ surgeon.designation }}</p>
      <h3>{{ surgeon.fullName }}</h3>
      <p class="hospital">
        <AppIcon name="building" :size="15" />
        <span>{{ surgeon.hospitalName }}<small v-if="surgeon.hospitalAddress">{{ surgeon.hospitalAddress }}</small></span>
      </p>

      <ul class="chips">
        <li><AppIcon name="award" :size="13" /> {{ years }} {{ years === 1 ? 'year' : 'years' }} in breast surgery</li>
        <li><AppIcon name="wallet" :size="13" /> {{ formatFee(surgeon.consultationFee) }}</li>
        <li v-if="languages"><AppIcon name="languages" :size="13" /> {{ languages }}</li>
        <li :class="surgeon.acceptingAppointments ? 'open' : 'closed'">{{ surgeon.acceptingAppointments ? 'Taking bookings' : 'Bookings paused' }}</li>
      </ul>

      <p v-if="surgeon.qualifications" class="line"><AppIcon name="book" :size="15" /> {{ surgeon.qualifications }}</p>
      <p v-if="surgeon.registrationNumber" class="line"><AppIcon name="shield-check" :size="15" /> BMDC: {{ surgeon.registrationNumber }}</p>

      <div class="actions">
        <RouterLink :to="`/breast-surgeons/${surgeon.id}`" class="view">
          View Full Profile <AppIcon name="arrow-right" :size="16" class="arrow" />
        </RouterLink>
        <RouterLink
          v-if="surgeon.acceptingAppointments"
          :to="`/breast-surgeons/${surgeon.id}/book`"
          class="btn btn-primary btn-sm book"
        >
          <AppIcon name="calendar" :size="15" /> Book Appointment
        </RouterLink>
      </div>
    </div>
  </article>
</template>

<style scoped>
.surgeon-card {
  display: flex; flex-direction: column; overflow: hidden; border-radius: 22px;
  background: #1b1530; color: #fff; border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: 0 16px 36px rgba(27, 21, 48, 0.28);
  transition: transform 0.2s, box-shadow 0.2s;
}
.surgeon-card:hover { transform: translateY(-4px); box-shadow: 0 26px 52px rgba(216, 27, 106, 0.28); }

/* Banner */
.banner {
  position: relative; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); min-height: 230px;
  background:
    radial-gradient(circle at 78% 30%, rgba(255, 255, 255, 0.28) 0, transparent 42%),
    radial-gradient(circle at 0% 100%, rgba(216, 27, 106, 0.55) 0, transparent 55%),
    linear-gradient(135deg, #fdf2f7 0%, #fbe3ee 48%, #f3d6f0 100%);
  color: #2a1650;
}
.banner::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 6px;
  background: linear-gradient(90deg, var(--pink-600), #b5179e, #6b3fb5);
}
.poster { display: flex; flex-direction: column; gap: 0.25rem; padding: 1rem 0.5rem 1.1rem 1.1rem; min-width: 0; }
.brand { display: flex; align-items: center; gap: 0.35rem; margin: 0; font-size: 0.78rem; font-weight: 700; letter-spacing: 0.04em; color: var(--pink-600); }
.script { margin: 0.35rem 0 0; font: italic 600 1rem 'Georgia', serif; color: #6b3fb5; }
.poster-name { margin: 0; font-size: 1.12rem; font-weight: 800; line-height: 1.2; color: #2a1650; overflow-wrap: anywhere; }
.poster-tag {
  width: fit-content; margin-top: 0.15rem; padding: 0.12rem 0.55rem; border-radius: 6px;
  background: var(--pink-600); color: #fff; font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em;
}
.hours {
  display: grid; gap: 0.35rem; width: fit-content; max-width: 100%; margin-top: auto; padding: 0.5rem 0.6rem;
  border-radius: 12px; background: #fff; box-shadow: 0 6px 16px rgba(42, 22, 80, 0.12);
}
.hours-title, .time { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.7rem; font-weight: 700; color: #2a1650; }
.hours :deep(svg) { color: var(--pink-600); }
.days { display: flex; flex-wrap: wrap; gap: 0.2rem; }
.days span {
  display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%;
  background: #f1edf5; color: #a29bb5; font-size: 0.6rem; font-weight: 700;
}
.days span.on { background: var(--pink-600); color: #fff; }

.portrait { position: relative; display: flex; align-items: flex-end; justify-content: center; padding: 1.2rem 0.9rem 0; }
.portrait img {
  width: 100%; max-width: 210px; height: 100%; max-height: 220px; object-fit: cover; object-position: top;
  border-radius: 110px 110px 14px 14px; border: 4px solid #fff; border-bottom: 0;
  box-shadow: 0 12px 30px rgba(42, 22, 80, 0.25);
}
.initials {
  display: grid; place-items: center; align-self: center; width: 130px; height: 130px; margin-bottom: 1rem; border-radius: 50%;
  background: linear-gradient(135deg, var(--pink-600), #6b3fb5); color: #fff; font-size: 2.6rem; font-weight: 800;
  border: 4px solid #fff;
}
.verified {
  position: absolute; top: 10px; right: 10px; display: inline-flex; align-items: center; gap: 0.25rem;
  padding: 0.18rem 0.55rem; border-radius: 999px; background: #e8f5ec; color: #1b7a3d;
  font-size: 0.68rem; font-weight: 700; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

/* Body */
.body { display: flex; flex-direction: column; flex: 1; padding: 1.15rem 1.3rem 1.1rem; }
.eyebrow { margin: 0; font-size: 0.74rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #f7a8c8; line-height: 1.5; }
.body h3 { margin: 0.3rem 0 0.35rem; font-size: 1.35rem; font-weight: 800; line-height: 1.2; color: #fff; overflow-wrap: anywhere; }
.hospital { display: flex; align-items: flex-start; gap: 0.45rem; margin: 0; color: #d9d3e8; font-size: 0.92rem; }
.hospital span { display: grid; min-width: 0; overflow-wrap: anywhere; }
.hospital small { color: #a9a1c0; font-size: 0.8rem; }
.body :deep(svg) { flex: none; }
.hospital :deep(svg), .line :deep(svg) { margin-top: 0.15rem; color: #f7a8c8; }

.chips { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0.85rem 0 0.7rem; padding: 0; list-style: none; }
.chips li {
  display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.25rem 0.65rem; border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.18); background: rgba(255, 255, 255, 0.06); color: #ece8f5;
  font-size: 0.76rem; font-weight: 600;
}
.chips li.open { border-color: rgba(74, 222, 128, 0.5); color: #86efac; }
.chips li.closed { border-color: rgba(251, 191, 36, 0.5); color: #fcd34d; }
.line { display: flex; align-items: flex-start; gap: 0.45rem; margin: 0.3rem 0 0; color: #d9d3e8; font-size: 0.86rem; line-height: 1.45; overflow-wrap: anywhere; }

.actions {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.75rem;
  margin-top: auto; padding-top: 1rem; border-top: 1px solid rgba(255, 255, 255, 0.1);
}
.body .line:last-of-type { margin-bottom: 1rem; }
.view { display: inline-flex; align-items: center; gap: 0.4rem; color: #f7a8c8; font-weight: 700; text-decoration: none; }
.view .arrow { transform: rotate(-45deg); transition: transform 0.2s; }
.view:hover { color: #fff; }
.view:hover .arrow { transform: rotate(-45deg) translateX(3px); }
.book { gap: 0.35rem; font-weight: 600; }

@media (max-width: 420px) {
  .banner { grid-template-columns: 1fr; }
  .portrait { order: -1; padding-top: 1rem; }
  .portrait img { max-height: 180px; }
}
</style>
