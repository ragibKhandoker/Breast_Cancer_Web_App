<script setup>
import { computed } from 'vue'
import { authState } from '../auth/authStore'
import { ROLES } from '../auth/roles'
import ProfileView from './ProfileView.vue'
import BreastSurgeonProfileManagement from './BreastSurgeonProfileManagement.vue'

// /profile: a doctor sets up their doctor profile here; patients and administrators keep their account profile.
const isDoctor = computed(() => authState.user?.role === ROLES.DOCTOR)
</script>

<template>
  <BreastSurgeonProfileManagement v-if="isDoctor" />
  <ProfileView v-else />
</template>
