<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import { authState } from '../../auth/authStore'
import { ROLE_LABELS } from '../../auth/roles'
import { APP_ROUTES } from '../../config/appRoutes'

// The avatar in the header: opens a small menu with the profile, "Use another account" (this tab only) and
// Logout, so the header itself stays on one line.
defineProps({ loggingOut: { type: Boolean, default: false } })
const emit = defineEmits(['logout', 'another-account'])

const route = useRoute()
const open = ref(false)
const root = ref(null)
const user = computed(() => authState.user)
const initials = computed(() =>
  (user.value?.fullName ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join(''),
)
const profileLink = computed(() => (user.value?.owner ? APP_ROUTES.adminAccount : APP_ROUTES.profile))

function onDocClick(event) {
  if (open.value && root.value && !root.value.contains(event.target)) open.value = false
}
function onKey(event) {
  if (event.key === 'Escape' && open.value) {
    open.value = false
    root.value?.querySelector('.user-btn')?.focus()
  }
}
onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
})
watch(() => route.fullPath, () => (open.value = false))

function choose(action) {
  open.value = false
  emit(action)
}
</script>

<template>
  <div v-if="user" ref="root" class="user-menu">
    <button
      type="button"
      class="user-btn"
      :class="{ on: open }"
      aria-haspopup="menu"
      :aria-expanded="open"
      aria-controls="user-menu-panel"
      :aria-label="`Account menu for ${user.fullName}`"
      :title="user.email"
      @click.stop="open = !open"
    >
      {{ initials }}
    </button>

    <Transition name="drop">
      <div v-if="open" id="user-menu-panel" class="panel" role="menu">
        <div class="who">
          <span class="who-avatar" aria-hidden="true">{{ initials }}</span>
          <span class="min0">
            <strong>{{ user.fullName }}</strong>
            <span class="email">{{ user.email }}</span>
            <span class="role">{{ ROLE_LABELS[user.role] }}</span>
          </span>
        </div>
        <RouterLink :to="profileLink" class="item" role="menuitem"><AppIcon name="user" :size="17" /> My profile</RouterLink>
        <button type="button" class="item" role="menuitem" @click="choose('another-account')">
          <AppIcon name="users" :size="17" />
          <span class="item-text">Use another account<small>This tab only; your other tabs stay signed in</small></span>
        </button>
        <button type="button" class="item danger" role="menuitem" :disabled="loggingOut" @click="choose('logout')">
          <AppIcon name="arrow-right" :size="17" /> {{ loggingOut ? 'Logging out…' : 'Logout' }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.user-menu { position: relative; }
.user-btn {
  display: grid; place-items: center; width: 42px; height: 42px; border: 0; border-radius: 50%; cursor: pointer;
  background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; font: 700 0.95rem 'Poppins', sans-serif;
  box-shadow: 0 4px 12px rgba(216, 27, 106, 0.3); transition: transform 0.15s;
}
.user-btn:hover { transform: scale(1.06); }
.user-btn.on, .user-btn:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }

.panel {
  position: absolute; top: calc(100% + 10px); right: 0; z-index: 30; width: 270px; padding: 0.4rem;
  border-radius: 16px; background: #fff; border: 1px solid var(--border); box-shadow: 0 24px 60px rgba(42, 22, 80, 0.22);
}
.who { display: flex; gap: 0.65rem; align-items: center; padding: 0.6rem 0.6rem 0.75rem; margin-bottom: 0.3rem; border-bottom: 1px solid var(--border); }
.who-avatar { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; font-weight: 700; font-size: 0.85rem; }
.min0 { min-width: 0; display: grid; }
.who strong { color: var(--purple-900); font-size: 0.92rem; }
.email { color: var(--muted); font-size: 0.78rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.role { width: fit-content; margin-top: 0.2rem; padding: 0.02rem 0.45rem; border-radius: 6px; background: var(--pink-50); color: var(--pink-600); font-size: 0.7rem; font-weight: 600; }
.item {
  display: flex; align-items: center; gap: 0.6rem; width: 100%; padding: 0.6rem 0.65rem; border: 0; border-radius: 10px;
  background: transparent; color: var(--purple-900); font: 500 0.9rem 'Poppins', sans-serif; text-align: left; cursor: pointer;
}
.item:hover { background: #fbf6fa; color: var(--pink-600); }
.item:focus-visible { outline: 3px solid var(--pink-300); outline-offset: -2px; }
.item :deep(svg) { flex: none; }
.item-text { display: grid; }
.item-text small { color: var(--muted); font-size: 0.72rem; font-weight: 400; }
.danger { color: var(--high); }
.danger:hover { background: #fdecea; color: var(--high); }
.drop-enter-active, .drop-leave-active { transition: opacity 0.18s, transform 0.18s; }
.drop-enter-from, .drop-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
