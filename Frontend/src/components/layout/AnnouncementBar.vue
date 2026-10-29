<script setup>
import { ref, computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { site } from '../../config/siteSettings'

// Site-wide notice set in the Admin Console. Closing it hides that text for the rest of the browser session;
// a new announcement shows again.
const STORAGE_KEY = 'bc-dismissed-announcement'

function readDismissed() {
  try {
    return sessionStorage.getItem(STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

const dismissed = ref(readDismissed())
const visible = computed(() => site.announcement.enabled && site.announcement.text && dismissed.value !== site.announcement.text)

function close() {
  dismissed.value = site.announcement.text
  try {
    sessionStorage.setItem(STORAGE_KEY, site.announcement.text)
  } catch {
    // Storage blocked: it stays hidden until the page reloads.
  }
}
</script>

<template>
  <div v-if="visible" class="announcement" role="region" aria-label="Announcement">
    <div class="container inner">
      <span class="dot" aria-hidden="true"></span>
      <p>{{ site.announcement.text }}</p>
      <button type="button" class="close" aria-label="Dismiss announcement" @click="close">
        <AppIcon name="close" :size="16" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.announcement { background: linear-gradient(90deg, var(--purple-900), #6b3fb5 60%, var(--pink-600)); color: #fff; }
.inner { display: flex; align-items: center; justify-content: center; gap: 0.75rem; min-height: 42px; padding-top: 0.4rem; padding-bottom: 0.4rem; }
p { margin: 0; font-size: 0.9rem; font-weight: 500; text-align: center; }
.dot { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--pink-300); box-shadow: 0 0 0 4px rgba(247, 168, 200, 0.25); }
.close {
  flex: none; display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: 50%;
  background: rgba(255, 255, 255, 0.14); color: #fff; cursor: pointer;
}
.close:hover { background: rgba(255, 255, 255, 0.25); }
.close:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
</style>
