<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import { authState } from '../../auth/authStore'
import { iconFor, timeAgo } from '../../services/notificationService'
import { notificationState, refreshNotifications, markRead, markAllRead, clearNotifications } from '../../notifications/notificationStore'

// The bell in the header: how many notifications are unread, and a drop-down with the latest, newest first.
// It checks for new ones every 30 seconds, when the page becomes visible again and on every page change.
const POLL_MS = 30000
const router = useRouter()
const route = useRoute()
const open = ref(false)
const root = ref(null)
const now = ref(Date.now())

// On an administrator's temporary password the server refuses everything else, so the bell waits.
const active = computed(() => !!authState.user && !authState.user.mustChangePassword)
const badge = computed(() => (notificationState.unread > 99 ? '99+' : String(notificationState.unread)))

function refresh() {
  if (!active.value) return
  now.value = Date.now()
  refreshNotifications()
}

let timer = null
function onVisible() {
  if (document.visibilityState === 'visible') refresh()
}
function onDocClick(event) {
  if (open.value && root.value && !root.value.contains(event.target)) open.value = false
}
function onKey(event) {
  if (event.key === 'Escape' && open.value) {
    open.value = false
    root.value?.querySelector('.bell')?.focus()
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, POLL_MS)
  document.addEventListener('visibilitychange', onVisible)
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  // The bell goes away on sign-out: forget this user's notifications.
  clearNotifications()
  document.removeEventListener('visibilitychange', onVisible)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
})
watch(() => route.fullPath, () => {
  open.value = false
  refresh()
})
watch(() => authState.user?.id, (id) => (id ? refresh() : clearNotifications()))
watch(active, (on) => on && refresh())

function toggle() {
  open.value = !open.value
  if (open.value) refresh()
}

async function openItem(item) {
  await markRead(item)
  open.value = false
  if (item.link?.startsWith('/')) router.push(item.link)
}
</script>

<template>
  <div v-if="authState.user" ref="root" class="notify">
    <button
      type="button"
      class="bell"
      :class="{ ringing: notificationState.unread > 0, on: open }"
      :aria-expanded="open"
      aria-haspopup="true"
      aria-controls="notification-panel"
      :aria-label="notificationState.unread ? `Notifications, ${notificationState.unread} unread` : 'Notifications'"
      @click.stop="toggle"
    >
      <AppIcon name="bell" :size="21" />
      <span v-if="notificationState.unread" class="count" aria-hidden="true">{{ badge }}</span>
    </button>

    <Transition name="drop">
      <section v-if="open" id="notification-panel" class="panel" aria-label="Notifications">
        <header class="panel-head">
          <div>
            <h2>Notifications</h2>
            <p>{{ notificationState.unread ? `${notificationState.unread} unread` : 'You are all caught up' }}</p>
          </div>
          <button v-if="notificationState.unread" type="button" class="mark-all" @click="markAllRead">Mark all as read</button>
        </header>

        <p v-if="!notificationState.loaded && !notificationState.error" class="state">Loading…</p>
        <p v-else-if="notificationState.error && !notificationState.items.length" class="state error">{{ notificationState.error }}</p>
        <div v-else-if="!notificationState.items.length" class="state empty">
          <span class="empty-icon"><AppIcon name="bell" :size="26" /></span>
          No notifications yet. Updates to your account, messages and appointments will appear here.
        </div>
        <ul v-else class="items">
          <li v-for="item in notificationState.items" :key="item.id">
            <button type="button" class="item" :class="{ unread: !item.read }" @click="openItem(item)">
              <span class="item-icon" :data-type="item.type"><AppIcon :name="iconFor(item.type)" :size="17" /></span>
              <span class="item-text">
                <strong>{{ item.title }}</strong>
                <span v-if="item.message" class="msg">{{ item.message }}</span>
                <time :datetime="item.createdAt" :title="new Date(item.createdAt).toLocaleString()">{{ timeAgo(item.createdAt, now) }}</time>
              </span>
              <span v-if="!item.read" class="dot" aria-label="Unread"></span>
            </button>
          </li>
        </ul>
      </section>
    </Transition>
  </div>
</template>

<style scoped>
.notify { position: relative; }
.bell {
  position: relative; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 50%;
  border: 1.5px solid var(--border); background: #fff; color: var(--purple-900); cursor: pointer;
  transition: border-color 0.15s, color 0.15s, box-shadow 0.15s;
}
.bell:hover, .bell.on { border-color: var(--pink-300); color: var(--pink-600); box-shadow: 0 4px 12px rgba(216, 27, 106, 0.15); }
.bell:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.bell.ringing :deep(svg) { animation: ring 2.4s ease-in-out infinite; transform-origin: 50% 4px; }
@keyframes ring { 0%, 70%, 100% { transform: rotate(0); } 75% { transform: rotate(14deg); } 80% { transform: rotate(-12deg); } 85% { transform: rotate(8deg); } 90% { transform: rotate(-5deg); } }
.count {
  position: absolute; top: -4px; right: -4px; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 999px;
  display: grid; place-items: center; background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff;
  font-size: 0.7rem; font-weight: 700; border: 2px solid #fff;
}

.panel {
  position: absolute; top: calc(100% + 10px); right: -8px; z-index: 30; width: min(380px, calc(100vw - 24px));
  border-radius: 18px; background: #fff; border: 1px solid var(--border); overflow: hidden;
  box-shadow: 0 24px 60px rgba(42, 22, 80, 0.22);
}
.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.75rem; padding: 1rem 1.1rem 0.8rem; border-bottom: 1px solid var(--border); }
.panel-head h2 { margin: 0; font-size: 1.02rem; color: var(--purple-900); }
.panel-head p { margin: 0.1rem 0 0; font-size: 0.8rem; color: var(--muted); }
.mark-all { border: 0; background: none; color: var(--pink-600); font: 600 0.8rem 'Poppins', sans-serif; cursor: pointer; padding: 0.25rem; border-radius: 6px; }
.mark-all:hover { text-decoration: underline; }
.mark-all:focus-visible { outline: 3px solid var(--pink-300); }

.items { list-style: none; margin: 0; padding: 0.35rem; max-height: min(420px, 60vh); overflow-y: auto; }
.item {
  display: flex; align-items: flex-start; gap: 0.7rem; width: 100%; padding: 0.7rem 0.75rem; border: 0; border-radius: 12px;
  background: transparent; text-align: left; cursor: pointer; font: inherit; color: var(--text);
}
.item:hover { background: #fbf6fa; }
.item:focus-visible { outline: 3px solid var(--pink-300); outline-offset: -2px; }
.item.unread { background: var(--pink-50); }
.item.unread:hover { background: var(--pink-100); }
.item-icon { flex: none; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: #f3eef8; color: var(--purple-900); }
.unread .item-icon { background: #fff; color: var(--pink-600); }
.item-text { flex: 1; min-width: 0; display: grid; gap: 0.1rem; }
.item-text strong { font-size: 0.88rem; color: var(--purple-900); }
.msg { font-size: 0.8rem; color: var(--muted); line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
time { font-size: 0.72rem; color: var(--pink-600); font-weight: 500; }
.dot { flex: none; width: 9px; height: 9px; margin-top: 0.4rem; border-radius: 50%; background: var(--pink-600); }

.state { margin: 0; padding: 1.4rem 1.2rem; text-align: center; color: var(--muted); font-size: 0.86rem; }
.state.error { color: var(--high); }
.empty { display: grid; justify-items: center; gap: 0.6rem; line-height: 1.5; }
.empty-icon { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 50%; background: var(--pink-50); color: var(--pink-300); }

.drop-enter-active, .drop-leave-active { transition: opacity 0.18s, transform 0.18s; }
.drop-enter-from, .drop-leave-to { opacity: 0; transform: translateY(-6px); }

@media (max-width: 560px) {
  .panel { position: fixed; top: 76px; left: 12px; right: 12px; width: auto; }
}
@media (prefers-reduced-motion: reduce) {
  .bell.ringing :deep(svg) { animation: none; }
}
</style>
