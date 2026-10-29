<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import AdminOverviewPanel from '../components/admin/AdminOverviewPanel.vue'
import AdminUsersPanel from '../components/admin/AdminUsersPanel.vue'
import AdminMessagesPanel from '../components/admin/AdminMessagesPanel.vue'
import AdminSettingsPanel from '../components/admin/AdminSettingsPanel.vue'
import { authState } from '../auth/authStore'
import { listMessages } from '../services/adminService'
import { APP_ROUTES } from '../config/appRoutes'

// The administrator's console. The open tab lives in the URL (?tab=users), so reloads and links keep it.
const TABS = [
  { id: 'overview', label: 'Overview', icon: 'calendar', component: AdminOverviewPanel },
  { id: 'users', label: 'Users', icon: 'users', component: AdminUsersPanel },
  { id: 'messages', label: 'Messages', icon: 'mail', component: AdminMessagesPanel },
  { id: 'settings', label: 'Website settings', icon: 'file-text', component: AdminSettingsPanel },
]

const route = useRoute()
const router = useRouter()
const active = computed(() => TABS.find((t) => t.id === route.query.tab) ?? TABS[0])

function open(id) {
  router.replace({ query: id === 'overview' ? {} : { tab: id } })
}

const user = computed(() => authState.user)
const initials = computed(() =>
  (user.value?.fullName ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join(''),
)
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
})

const unread = ref(0)
async function refreshUnread() {
  try {
    unread.value = (await listMessages('NEW')).length
  } catch {
    // The badge is a convenience; the Messages tab shows any error.
  }
}

function onKeydown(event, index) {
  const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key]
  if (!step) return
  const next = TABS[(index + step + TABS.length) % TABS.length]
  open(next.id)
  document.getElementById(`tab-${next.id}`)?.focus()
}

onMounted(refreshUnread)
</script>

<template>
  <section class="container page console">
    <!-- Admin profile -->
    <div class="card admin-card">
      <div class="avatar" aria-hidden="true">{{ initials }}</div>
      <div class="who">
        <p class="eyebrow">{{ greeting }}</p>
        <h1>{{ user?.fullName }}</h1>
        <p class="muted">
          <span class="badge"><AppIcon name="shield-check" :size="14" /> Administrator</span>
          {{ user?.email }}
        </p>
      </div>
      <RouterLink :to="APP_ROUTES.profile" class="btn btn-outline btn-sm">Edit my profile</RouterLink>
    </div>

    <!-- Tabs -->
    <div class="tabs" role="tablist" aria-label="Admin console">
      <button
        v-for="(tab, i) in TABS"
        :id="`tab-${tab.id}`"
        :key="tab.id"
        type="button"
        role="tab"
        :aria-selected="active.id === tab.id"
        :aria-controls="`panel-${tab.id}`"
        :tabindex="active.id === tab.id ? 0 : -1"
        :class="{ on: active.id === tab.id }"
        @click="open(tab.id)"
        @keydown="onKeydown($event, i)"
      >
        <AppIcon :name="tab.icon" :size="17" />
        {{ tab.label }}
        <span v-if="tab.id === 'messages' && unread" class="badge-count" :aria-label="`${unread} unread`">{{ unread }}</span>
      </button>
    </div>

    <div :id="`panel-${active.id}`" role="tabpanel" :aria-labelledby="`tab-${active.id}`" class="panel">
      <KeepAlive>
        <component :is="active.component" :key="active.id" @open-tab="open" @unread-changed="refreshUnread" />
      </KeepAlive>
    </div>
  </section>
</template>

<style scoped>
.admin-card {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 1.25rem; padding: 1.4rem 1.6rem;
  background:
    radial-gradient(circle at 95% 10%, rgba(181, 23, 158, 0.12), transparent 40%),
    linear-gradient(120deg, #fff 0%, var(--pink-50) 100%);
}
.avatar {
  display: grid; place-items: center; width: 72px; height: 72px; border-radius: 50%;
  background: linear-gradient(135deg, var(--purple-900), #6b3fb5); color: #fff; font-size: 1.6rem; font-weight: 700;
  box-shadow: 0 8px 20px rgba(42, 22, 80, 0.25);
}
.eyebrow { margin: 0; font-size: 0.82rem; font-weight: 600; color: var(--pink-600); text-transform: uppercase; letter-spacing: 0.04em; }
.who h1 { margin: 0.1rem 0 0.2rem; font-size: 1.6rem; overflow-wrap: anywhere; }
.who .muted { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; overflow-wrap: anywhere; }
.badge { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.15rem 0.6rem; border-radius: 999px; background: var(--purple-900); color: #fff; font-size: 0.76rem; font-weight: 600; }

.tabs { display: flex; gap: 0.35rem; margin: 1.25rem 0 1rem; padding: 0.35rem; border-radius: 16px; background: #fff; border: 1px solid var(--border); overflow-x: auto; }
.tabs button {
  display: inline-flex; align-items: center; gap: 0.45rem; flex: none; padding: 0.6rem 1.05rem; border: 0; border-radius: 12px;
  background: none; color: var(--muted); font: 600 0.92rem 'Poppins', sans-serif; cursor: pointer; transition: background-color 0.15s, color 0.15s;
}
.tabs button:hover { color: var(--pink-600); background: var(--pink-50); }
.tabs button.on { background: var(--pink-600); color: #fff; box-shadow: 0 6px 16px rgba(216, 27, 106, 0.25); }
.tabs button:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 1px; }
.badge-count { min-width: 20px; padding: 0 0.4rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-size: 0.72rem; line-height: 20px; text-align: center; }
.tabs button.on .badge-count { background: #fff; color: var(--pink-600); }

@media (max-width: 640px) {
  .admin-card { grid-template-columns: auto minmax(0, 1fr); padding: 1.2rem; }
  .admin-card .btn { grid-column: 1 / -1; justify-content: center; }
  .avatar { width: 58px; height: 58px; font-size: 1.3rem; }
  .who h1 { font-size: 1.3rem; }
  .tabs button { padding: 0.55rem 0.75rem; font-size: 0.86rem; }
}
</style>
