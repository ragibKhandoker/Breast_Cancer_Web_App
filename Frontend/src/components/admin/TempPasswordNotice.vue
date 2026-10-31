<script setup>
import { ref } from 'vue'
import AppIcon from '../common/AppIcon.vue'

// Shows a temporary password once, right after an administrator resets it, with a Copy button.
const props = defineProps({
  email: { type: String, required: true },
  password: { type: String, required: true },
})
defineEmits(['close'])

const copied = ref(false)
async function copy() {
  try {
    await navigator.clipboard.writeText(props.password)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    // Clipboard blocked (e.g. plain http on another host): the password is still selectable on screen.
  }
}
</script>

<template>
  <div class="temp" role="status">
    <p class="title"><AppIcon name="check-circle" :size="18" /> Password reset for {{ email }}</p>
    <div class="code">
      <code>{{ password }}</code>
      <button type="button" class="copy" @click="copy">{{ copied ? 'Copied' : 'Copy' }}</button>
    </div>
    <p class="hint">
      Give this temporary password to the user (for example by phone). It is shown only now and is not stored, only
      its secure hash. It works for one login only, within 24 hours. Their other logins have been signed out, and after logging in they must
      choose their own password before they can do anything else.
    </p>
    <button type="button" class="done" @click="$emit('close')">Done</button>
  </div>
</template>

<style scoped>
.temp { margin-top: 0.75rem; padding: 1rem 1.1rem; border-radius: 14px; background: #ecfdf3; border: 1px solid #a7f3d0; color: #14532d; }
.title { display: flex; align-items: center; gap: 0.45rem; margin: 0 0 0.6rem; font-weight: 600; overflow-wrap: anywhere; }
.code { display: flex; align-items: center; gap: 0.5rem; padding: 0.4rem 0.4rem 0.4rem 0.9rem; border-radius: 10px; background: #fff; border: 1px dashed #34d399; }
.code code { flex: 1; font: 600 1.1rem ui-monospace, 'Cascadia Code', Consolas, monospace; letter-spacing: 0.06em; color: var(--purple-900); user-select: all; overflow-wrap: anywhere; }
.copy, .done { padding: 0.4rem 0.85rem; border: 0; border-radius: 8px; font: 600 0.85rem 'Poppins', sans-serif; cursor: pointer; }
.copy { background: #16a34a; color: #fff; }
.done { margin-top: 0.6rem; background: none; color: #166534; padding-left: 0; }
.hint { margin: 0.6rem 0 0; font-size: 0.84rem; line-height: 1.5; }
</style>
