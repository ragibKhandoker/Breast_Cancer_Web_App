<script setup>
import { onMounted, onBeforeUnmount, watch } from 'vue'
import AppIcon from './AppIcon.vue'

// A notification that floats over the page (top right, under the header), outside any form. It closes by itself
// after `duration` ms (0 keeps it open) or with its × button. v-model:open shows and hides it.
const open = defineModel('open', { type: Boolean, default: false })
const props = defineProps({
  title: { type: String, default: '' },
  message: { type: String, required: true },
  tone: { type: String, default: 'success' }, // success | info | warning
  duration: { type: Number, default: 6000 },
})

const ICONS = { success: 'check-circle', info: 'alert-circle', warning: 'alert-circle' }

let timer = null
function schedule() {
  clearTimeout(timer)
  if (open.value && props.duration > 0) timer = setTimeout(() => (open.value = false), props.duration)
}
watch(open, schedule)
onMounted(schedule)
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="open" class="toast" :class="tone" :role="tone === 'warning' ? 'alert' : 'status'" aria-live="polite">
        <span class="toast-icon"><AppIcon :name="ICONS[tone] ?? 'check-circle'" :size="20" /></span>
        <div class="toast-body">
          <strong v-if="title">{{ title }}</strong>
          <p>{{ message }}</p>
        </div>
        <button type="button" class="toast-close" aria-label="Close notification" @click="open = false">
          <AppIcon name="close" :size="16" />
        </button>
        <span v-if="duration > 0" class="toast-timer" :style="{ animationDuration: `${duration}ms` }" aria-hidden="true"></span>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.toast {
  position: fixed; top: 92px; right: 24px; z-index: 60; display: flex; align-items: flex-start; gap: 0.75rem;
  width: min(380px, calc(100vw - 32px)); padding: 0.95rem 1rem 1.05rem; overflow: hidden; border-radius: 16px;
  background: #fff; border: 1px solid var(--border); border-left: 5px solid var(--low);
  box-shadow: 0 18px 45px rgba(42, 22, 80, 0.18), 0 2px 8px rgba(42, 22, 80, 0.06);
}
.toast.info { border-left-color: var(--pink-600); }
.toast.warning { border-left-color: #ef8a17; }
.toast-icon { flex: none; display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: #e8f5e9; color: var(--low); }
.info .toast-icon { background: var(--pink-50); color: var(--pink-600); }
.warning .toast-icon { background: var(--warn-bg); color: var(--warn-text); }
.toast-body { flex: 1; min-width: 0; }
.toast-body strong { display: block; color: var(--purple-900); font-size: 0.95rem; }
.toast-body p { margin: 0.15rem 0 0; color: var(--text); font-size: 0.88rem; line-height: 1.45; }
.toast-close {
  flex: none; display: grid; place-items: center; width: 28px; height: 28px; margin: -0.2rem -0.3rem 0 0;
  border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer;
}
.toast-close:hover { background: #f6f1f8; color: var(--purple-900); }
.toast-close:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 1px; }
/* Shrinking bar along the bottom: how long until it closes. */
.toast-timer { position: absolute; left: 0; bottom: 0; height: 3px; width: 100%; background: currentColor; color: var(--low); opacity: 0.35; transform-origin: left; animation: shrink linear forwards; }
.info .toast-timer { color: var(--pink-600); }
.warning .toast-timer { color: #ef8a17; }
@keyframes shrink { from { transform: scaleX(1); } to { transform: scaleX(0); } }

.toast-enter-active, .toast-leave-active { transition: opacity 0.25s, transform 0.25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(24px); }

@media (max-width: 560px) { .toast { top: 84px; right: 16px; } }
@media (prefers-reduced-motion: reduce) {
  .toast-enter-active, .toast-leave-active { transition: none; }
  .toast-timer { animation: none; display: none; }
}
</style>
