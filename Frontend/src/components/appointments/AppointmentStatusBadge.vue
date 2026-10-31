<script setup>
import { computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { STATUS } from '../../appointments/format'

const props = defineProps({ status: { type: String, required: true } })
const meta = computed(() => STATUS[props.status] ?? { label: props.status, icon: 'alert-circle' })
</script>

<template>
  <span class="status-badge" :class="status.toLowerCase()">
    <AppIcon :name="meta.icon" :size="13" :stroke-width="2.4" />
    {{ meta.label }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.2rem 0.7rem; border-radius: 999px;
  font-size: 0.78rem; font-weight: 600; white-space: nowrap; border: 1px solid transparent;
}
.pending { background: #fff7e6; color: #8a5300; border-color: #f8d9a0; }
.confirmed { background: #e8f5e9; color: #1b5e20; border-color: #a5d6a7; }
.cancelled { background: #f3f1f6; color: #5f5873; border-color: #dcd6e6; }
.completed { background: #f1ebff; color: #4a2a8a; border-color: #cdbaf3; }
</style>
