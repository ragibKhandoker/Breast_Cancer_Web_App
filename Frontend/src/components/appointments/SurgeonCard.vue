<script setup>
import AppIcon from '../common/AppIcon.vue'
import SurgeonAvatar from './SurgeonAvatar.vue'
import { formatFee } from '../../appointments/format'

// One approved Breast Surgeon in the Find a Breast Surgeon listing.
defineProps({ surgeon: { type: Object, required: true } })
</script>

<template>
  <article class="card surgeon-card">
    <div class="top">
      <SurgeonAvatar :photo-url="surgeon.photoUrl" :name="surgeon.fullName" :size="76" />
      <div class="who">
        <h3>{{ surgeon.fullName }}</h3>
        <p class="designation">{{ surgeon.designation }}</p>
        <span class="specialty"><AppIcon name="heart" :size="12" /> {{ surgeon.specialty }}</span>
      </div>
    </div>

    <p class="qualifications">{{ surgeon.qualifications }}</p>

    <ul class="facts">
      <li><AppIcon name="award" :size="16" /> {{ surgeon.experienceYears }} {{ surgeon.experienceYears === 1 ? 'year' : 'years' }} in breast surgery</li>
      <li><AppIcon name="building" :size="16" /> {{ surgeon.hospitalName }}</li>
      <li><AppIcon name="wallet" :size="16" /> {{ formatFee(surgeon.consultationFee) }} consultation</li>
    </ul>

    <p v-if="!surgeon.acceptingAppointments" class="paused">Not taking new appointments right now</p>

    <div class="actions">
      <RouterLink :to="`/breast-surgeons/${surgeon.id}`" class="btn btn-outline btn-sm">View Profile</RouterLink>
      <RouterLink
        v-if="surgeon.acceptingAppointments"
        :to="`/breast-surgeons/${surgeon.id}/book`"
        class="btn btn-primary btn-sm"
      >
        Book Appointment
      </RouterLink>
    </div>
  </article>
</template>

<style scoped>
.surgeon-card {
  display: flex; flex-direction: column; gap: 0.9rem; padding: 1.4rem; border-radius: 22px;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.surgeon-card:hover { transform: translateY(-3px); border-color: var(--pink-300); box-shadow: 0 18px 40px rgba(216, 27, 106, 0.14); }
.top { display: flex; align-items: center; gap: 1rem; }
.who { min-width: 0; }
.who h3 { margin: 0; font-size: 1.12rem; color: var(--purple-900); overflow-wrap: anywhere; }
.designation { margin: 0.1rem 0 0.35rem; color: var(--muted); font-size: 0.88rem; }
.specialty {
  display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.15rem 0.6rem; border-radius: 999px;
  background: var(--pink-50); color: var(--pink-600); border: 1px solid var(--border); font-size: 0.75rem; font-weight: 600;
}
.qualifications { margin: 0; color: var(--text); font-size: 0.9rem; line-height: 1.5; }
.facts { list-style: none; margin: 0; padding: 0.85rem 0 0; display: grid; gap: 0.45rem; border-top: 1px dashed var(--border); }
.facts li { display: flex; align-items: center; gap: 0.5rem; font-size: 0.88rem; color: var(--text); }
.facts :deep(svg) { flex: none; color: var(--pink-600); }
.paused { margin: 0; padding: 0.45rem 0.7rem; border-radius: 10px; background: var(--warn-bg); color: var(--warn-text); font-size: 0.82rem; font-weight: 500; }
.actions { display: flex; gap: 0.6rem; margin-top: auto; }
.actions .btn { flex: 1; justify-content: center; }
</style>
