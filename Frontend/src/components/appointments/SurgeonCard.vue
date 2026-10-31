<script setup>
import { computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import SurgeonAvatar from './SurgeonAvatar.vue'
import { formatFee, DAYS } from '../../appointments/format'

// One approved Breast Surgeon in the Find a Breast Surgeon listing: photo, the key facts in three tiles, then hospital, degrees and languages, and the two actions.
const props = defineProps({ surgeon: { type: Object, required: true } })

const years = computed(() => props.surgeon.experienceYears ?? 0)
const weekDays = computed(() => DAYS.map((d) => ({ ...d, on: (props.surgeon.availableDays ?? []).includes(d.id) })))
const languages = computed(() => (props.surgeon.languages ?? []).join(', '))
</script>

<template>
  <article class="surgeon-card">
    <span class="verified"><AppIcon name="shield-check" :size="13" /> Verified</span>

    <div class="head">
      <SurgeonAvatar :photo-url="surgeon.photoUrl" :name="surgeon.fullName" :size="84" class="photo" />
      <h3>{{ surgeon.fullName }}</h3>
      <p class="designation">{{ surgeon.designation }}</p>
      <span class="specialty"><AppIcon name="heart" :size="12" /> {{ surgeon.specialty }}</span>
    </div>

    <dl class="stats">
      <div>
        <dt>Experience</dt>
        <dd>{{ years }} {{ years === 1 ? 'yr' : 'yrs' }}</dd>
      </div>
      <div>
        <dt>Fee</dt>
        <dd>{{ formatFee(surgeon.consultationFee) }}</dd>
      </div>
      <div>
        <dt>Status</dt>
        <dd :class="surgeon.acceptingAppointments ? 'open' : 'closed'">{{ surgeon.acceptingAppointments ? 'Open' : 'Paused' }}</dd>
      </div>
    </dl>

    <ul class="facts">
      <li><AppIcon name="award" :size="16" /><span>{{ years }} {{ years === 1 ? 'year' : 'years' }} in breast surgery</span></li>
      <li><AppIcon name="building" :size="16" /><span><strong>{{ surgeon.hospitalName }}</strong><small v-if="surgeon.hospitalAddress">{{ surgeon.hospitalAddress }}</small></span></li>
      <li v-if="surgeon.qualifications"><AppIcon name="book" :size="16" /><span>{{ surgeon.qualifications }}</span></li>
      <li v-if="languages"><AppIcon name="languages" :size="16" /><span>Speaks {{ languages }}</span></li>
      <li><AppIcon name="wallet" :size="16" /><span>{{ formatFee(surgeon.consultationFee) }} consultation</span></li>
    </ul>

    <div class="days" :aria-label="`Consultation days: ${weekDays.filter((d) => d.on).map((d) => d.label).join(', ')}`">
      <span v-for="d in weekDays" :key="d.id" :class="{ on: d.on }" aria-hidden="true">{{ d.short.slice(0, 2) }}</span>
    </div>

    <p v-if="!surgeon.acceptingAppointments" class="paused">Not taking new appointments right now</p>

    <div class="actions">
      <RouterLink :to="`/breast-surgeons/${surgeon.id}`" class="btn btn-outline btn-sm">View Profile</RouterLink>
      <RouterLink
        v-if="surgeon.acceptingAppointments"
        :to="`/breast-surgeons/${surgeon.id}/book`"
        class="btn btn-primary btn-sm"
      >
        <AppIcon name="calendar" :size="15" /> Book Appointment
      </RouterLink>
    </div>
  </article>
</template>

<style scoped>
/* Strong text colours on a plain white card, so everything reads clearly. */
.surgeon-card {
  --ink: #241445;
  --ink-soft: #4a4161;
  position: relative; display: flex; flex-direction: column; overflow: hidden;
  border-radius: 22px; background: #fff; border: 1px solid #f0d3e2;
  box-shadow: 0 10px 28px rgba(42, 22, 80, 0.08);
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.surgeon-card:hover { transform: translateY(-4px); border-color: var(--pink-300); box-shadow: 0 22px 46px rgba(216, 27, 106, 0.16); }

.verified {
  position: absolute; top: 12px; right: 12px; display: inline-flex; align-items: center; gap: 0.3rem;
  padding: 0.2rem 0.6rem; border-radius: 999px; background: #e8f5ec; color: #1b7a3d;
  font-size: 0.72rem; font-weight: 700;
}

.head { display: grid; justify-items: center; text-align: center; padding: 1.75rem 1.3rem 0; }
.photo { box-shadow: 0 0 0 5px #fff, 0 10px 24px rgba(42, 22, 80, 0.2) !important; }
.head h3 { margin: 0.7rem 0 0; font-size: 1.2rem; font-weight: 700; color: var(--ink); line-height: 1.25; overflow-wrap: anywhere; }
.designation { margin: 0.2rem 0 0.5rem; color: var(--pink-600); font-weight: 600; font-size: 0.9rem; }
.specialty {
  display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.2rem 0.7rem; border-radius: 999px;
  background: var(--pink-50); color: #b0144f; border: 1px solid #f6c3da; font-size: 0.76rem; font-weight: 700;
}

.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem; margin: 1rem 1.2rem 0; }
.stats div { padding: 0.55rem 0.3rem; border-radius: 14px; background: #faf6fc; border: 1px solid #efe6f5; text-align: center; }
.stats dt { font-size: 0.68rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--ink-soft); }
.stats dd { margin: 0.15rem 0 0; font-size: 1rem; font-weight: 800; color: var(--ink); }
.stats dd.open { color: #1b7a3d; }
.stats dd.closed { color: #a4500a; }

.facts { list-style: none; margin: 1rem 1.3rem 0; padding: 0; display: grid; gap: 0.55rem; }
.facts li { display: flex; align-items: flex-start; gap: 0.55rem; font-size: 0.9rem; color: var(--ink); line-height: 1.45; }
.facts :deep(svg) { flex: none; margin-top: 0.12rem; color: var(--pink-600); }
.facts span { min-width: 0; display: grid; overflow-wrap: anywhere; }
.facts strong { font-weight: 600; }
.facts small { color: var(--ink-soft); font-size: 0.8rem; }

.days { display: flex; justify-content: center; gap: 0.3rem; margin: 1rem 1.3rem 0; }
.days span {
  display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%;
  background: #f3eff6; color: #8d86a0; font-size: 0.7rem; font-weight: 700;
}
.days span.on { background: var(--pink-600); color: #fff; }

.paused { margin: 0.9rem 1.3rem 0; padding: 0.5rem 0.75rem; border-radius: 10px; background: var(--warn-bg); color: var(--warn-text); font-size: 0.84rem; font-weight: 600; text-align: center; }
.actions { display: flex; gap: 0.6rem; margin-top: auto; padding: 1.1rem 1.3rem 1.3rem; }
.actions .btn { flex: 1; justify-content: center; gap: 0.35rem; font-weight: 600; }
</style>
