<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/common/AppIcon.vue'
import AdminOverviewPanel from '../components/admin/AdminOverviewPanel.vue'
import AdminVerificationPanel from '../components/admin/AdminVerificationPanel.vue'
import AdminPasswordPanel from '../components/admin/AdminPasswordPanel.vue'
import AdminUsersPanel from '../components/admin/AdminUsersPanel.vue'
import AdminMessagesPanel from '../components/admin/AdminMessagesPanel.vue'
import AdminSettingsPanel from '../components/admin/AdminSettingsPanel.vue'
import AdminSurgeonPanel from '../components/admin/AdminSurgeonPanel.vue'
import { authState } from '../auth/authStore'
import { getOverview, listMessages, listSurgeonProfiles } from '../services/adminService'
import { APP_ROUTES } from '../config/appRoutes'

// The admin portal. The open section lives in the URL (?tab=users), so reloads and links keep it.
// `count` names the badge shown next to the section.
const TABS = [
  { id: 'overview', label: 'Overview', icon: 'calendar', component: AdminOverviewPanel, group: 'Dashboard',
    title: 'Site overview', subtitle: 'How the website is doing right now.' },
  { id: 'verification', label: 'Patient verification', icon: 'check-circle', component: AdminVerificationPanel, group: 'Patients', count: 'pending',
    title: 'Patient verification', subtitle: 'Check patients’ details and verify them, or ask for a correction.' },
  { id: 'passwords', label: 'Password help', icon: 'shield-check', component: AdminPasswordPanel, group: 'Patients', count: 'passwords',
    title: 'Password help', subtitle: 'Reset passwords for users who are locked out.' },
  { id: 'surgeons', label: 'Breast surgeons', icon: 'heart', component: AdminSurgeonPanel, group: 'Doctors', count: 'surgeons',
    title: 'Breast Surgeon verification', subtitle: 'Check credentials before a Breast Surgeon is listed and can take appointments.' },
  { id: 'users', label: 'Users & roles', icon: 'users', component: AdminUsersPanel, group: 'Accounts',
    title: 'Users & roles', subtitle: 'Create accounts, change roles, disable or reset access.' },
  { id: 'messages', label: 'Messages', icon: 'mail', component: AdminMessagesPanel, group: 'Website', count: 'unread',
    title: 'Messages', subtitle: 'What visitors sent through the Contact page.' },
  { id: 'settings', label: 'Website settings', icon: 'file-text', component: AdminSettingsPanel, group: 'Website',
    title: 'Website settings', subtitle: 'Contact details, social links and the announcement bar.' },
]
const GROUPS = [...new Set(TABS.map((t) => t.group))]

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

// Badge counts. They are a convenience: each section shows its own errors.
const counts = ref({ unread: 0, pending: 0, passwords: 0, surgeons: 0 })
async function refreshCounts() {
  const [messages, overview, surgeons] = await Promise.allSettled([listMessages('NEW'), getOverview(), listSurgeonProfiles('PENDING')])
  if (messages.status === 'fulfilled') counts.value.unread = messages.value.length
  if (surgeons.status === 'fulfilled') counts.value.surgeons = surgeons.value.length
  const support = overview.status === 'fulfilled' ? overview.value?.support : null
  if (support) {
    counts.value.pending = support.pendingVerification
    counts.value.passwords = support.openPasswordRequests
  }
}
const waiting = computed(() => counts.value.pending + counts.value.passwords + counts.value.unread + counts.value.surgeons)

function onKeydown(event, index) {
  const step = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 }[event.key]
  if (!step) return
  event.preventDefault()
  const next = TABS[(index + step + TABS.length) % TABS.length]
  open(next.id)
  document.getElementById(`tab-${next.id}`)?.focus()
}

onMounted(refreshCounts)
</script>

<template>
  <section class="container page console">
    <!-- Portal header with the signed-in administrator -->
    <div class="card admin-card">
      <div class="avatar" aria-hidden="true">{{ initials }}</div>
      <div class="who">
        <p class="eyebrow">{{ greeting }} · Admin portal</p>
        <h1>{{ user?.fullName }}</h1>
        <p class="sub">
          <span class="badge"><AppIcon name="shield-check" :size="14" /> Administrator</span>
          {{ user?.email }}
        </p>
      </div>
      <div class="header-side">
        <span class="waiting" :class="{ calm: !waiting }">
          <AppIcon :name="waiting ? 'alert-circle' : 'check-circle'" :size="16" />
          {{ waiting ? `${waiting} item${waiting === 1 ? '' : 's'} need attention` : 'All caught up' }}
        </span>
        <RouterLink v-if="user?.owner" :to="APP_ROUTES.adminAccount" class="btn btn-outline btn-sm">My admin account</RouterLink>
        <RouterLink v-else :to="APP_ROUTES.profile" class="btn btn-outline btn-sm">Edit my profile</RouterLink>
      </div>
    </div>

    <div class="portal">
      <!-- Sidebar navigation -->
      <nav class="sidebar card" aria-label="Admin portal">
        <div role="tablist" aria-label="Admin portal sections" aria-orientation="vertical" class="tabs">
          <template v-for="group in GROUPS" :key="group">
            <p class="group" aria-hidden="true">{{ group }}</p>
            <template v-for="tab in TABS" :key="tab.id">
              <button
                v-if="tab.group === group"
                :id="`tab-${tab.id}`"
                type="button"
                role="tab"
                :aria-selected="active.id === tab.id"
                :aria-controls="`panel-${tab.id}`"
                :tabindex="active.id === tab.id ? 0 : -1"
                :class="{ on: active.id === tab.id }"
                @click="open(tab.id)"
                @keydown="onKeydown($event, TABS.indexOf(tab))"
              >
                <span class="tab-icon"><AppIcon :name="tab.icon" :size="17" /></span>
                <span class="tab-label">{{ tab.label }}</span>
                <span v-if="tab.count && counts[tab.count]" class="badge-count" :aria-label="`${counts[tab.count]} waiting`">{{ counts[tab.count] }}</span>
              </button>
            </template>
          </template>
        </div>
        <div class="side-note">
          <AppIcon name="heart" :size="16" />
          <span>Only administrators can see this portal.</span>
        </div>
      </nav>

      <!-- Active section -->
      <div class="content">
        <header class="content-head">
          <h2>{{ active.title }}</h2>
          <p class="muted">{{ active.subtitle }}</p>
        </header>
        <div :id="`panel-${active.id}`" role="tabpanel" :aria-labelledby="`tab-${active.id}`" class="panel">
          <KeepAlive>
            <component :is="active.component" :key="active.id" @open-tab="open" @unread-changed="refreshCounts" @counts-changed="refreshCounts" />
          </KeepAlive>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.admin-card {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 1.25rem; padding: 1.5rem 1.75rem;
  color: #fff; border: 0;
  background:
    radial-gradient(circle at 92% 0%, rgba(255, 255, 255, 0.2), transparent 40%),
    radial-gradient(circle at 0% 120%, rgba(216, 27, 106, 0.55), transparent 50%),
    linear-gradient(120deg, var(--purple-950) 0%, #4a2a8a 55%, #b5179e 100%);
  box-shadow: 0 18px 40px rgba(42, 22, 80, 0.25);
}
.avatar {
  display: grid; place-items: center; width: 74px; height: 74px; border-radius: 50%;
  background: #fff; color: var(--purple-900); font-size: 1.6rem; font-weight: 700;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.25), 0 10px 24px rgba(0, 0, 0, 0.25);
}
.eyebrow { margin: 0; font-size: 0.78rem; font-weight: 600; color: rgba(255, 255, 255, 0.75); text-transform: uppercase; letter-spacing: 0.1em; }
.who h1 { margin: 0.15rem 0 0.25rem; font-size: 1.65rem; color: #fff; overflow-wrap: anywhere; }
.sub { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; color: rgba(255, 255, 255, 0.85); overflow-wrap: anywhere; }
.badge { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.15rem 0.65rem; border-radius: 999px; background: #fff; color: var(--purple-900); font-size: 0.76rem; font-weight: 600; }
.header-side { display: grid; justify-items: end; gap: 0.6rem; }
.waiting { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.35rem 0.85rem; border-radius: 999px; background: rgba(255, 255, 255, 0.16); border: 1px solid rgba(255, 255, 255, 0.3); font-size: 0.84rem; font-weight: 600; }
.waiting.calm { background: rgba(34, 197, 94, 0.25); border-color: rgba(134, 239, 172, 0.5); }
.admin-card .btn-outline { background: transparent; border-color: rgba(255, 255, 255, 0.7); color: #fff; }
.admin-card .btn-outline:hover { background: #fff; color: var(--purple-900); }

.portal { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 1.25rem; margin-top: 1.25rem; align-items: start; }
.sidebar { position: sticky; top: 90px; padding: 0.9rem; }
.tabs { display: grid; gap: 0.2rem; }
.group { margin: 0.75rem 0.6rem 0.3rem; font-size: 0.7rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: var(--muted); }
.group:first-child { margin-top: 0.2rem; }
.tabs button {
  display: flex; align-items: center; gap: 0.65rem; width: 100%; padding: 0.55rem 0.65rem; border: 0; border-radius: 12px;
  background: none; color: var(--purple-900); font: 500 0.92rem 'Poppins', sans-serif; text-align: left; cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}
.tab-icon { flex: none; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; background: var(--pink-50); color: var(--pink-600); transition: background-color 0.15s, color 0.15s; }
.tab-label { flex: 1; }
.tabs button:hover { background: var(--pink-50); }
.tabs button.on { background: linear-gradient(120deg, var(--pink-600), #b5179e); color: #fff; font-weight: 600; box-shadow: 0 8px 18px rgba(216, 27, 106, 0.25); }
.tabs button.on .tab-icon { background: rgba(255, 255, 255, 0.2); color: #fff; }
.tabs button:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 1px; }
.badge-count { min-width: 22px; padding: 0 0.45rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-size: 0.72rem; font-weight: 700; line-height: 22px; text-align: center; }
.tabs button.on .badge-count { background: #fff; color: var(--pink-600); }
.side-note { display: flex; align-items: center; gap: 0.45rem; margin-top: 1rem; padding: 0.7rem 0.75rem; border-radius: 12px; background: #f6f0ff; color: #5b3d99; font-size: 0.78rem; line-height: 1.4; }
.side-note :deep(svg) { flex: none; color: var(--pink-600); }

.content-head { margin-bottom: 1rem; }
.content-head h2 { margin: 0; font-size: 1.4rem; color: var(--purple-900); }
.content-head p { margin: 0.15rem 0 0; }

@media (max-width: 960px) {
  .portal { grid-template-columns: 1fr; }
  .sidebar { position: static; padding: 0.5rem; }
  .tabs { display: flex; overflow-x: auto; gap: 0.3rem; }
  .group, .side-note { display: none; }
  .tabs button { flex: none; width: auto; }
}
@media (max-width: 640px) {
  .admin-card { grid-template-columns: auto minmax(0, 1fr); padding: 1.2rem; }
  .header-side { grid-column: 1 / -1; justify-items: stretch; }
  .header-side .btn { justify-content: center; }
  .avatar { width: 58px; height: 58px; font-size: 1.3rem; }
  .who h1 { font-size: 1.3rem; }
  .tab-icon { width: 28px; height: 28px; }
}
</style>
