<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { iconFor, timeAgo } from '../../services/notificationService'
import { notificationState, refreshNotifications, markRead, markAllRead } from '../../notifications/notificationStore'

// The latest notifications as a card on a page (the admin account page), newest first. It shares its data with
// the bell in the header and refreshes every 30 seconds.
const props = defineProps({ limit: { type: Number, default: 6 } })
const router = useRouter()
const now = ref(Date.now())

let timer = null
function refresh() {
  now.value = Date.now()
  refreshNotifications()
}
onMounted(() => {
  refresh()
  timer = setInterval(refresh, 30000)
})
onBeforeUnmount(() => clearInterval(timer))

async function open(item) {
  await markRead(item)
  if (item.link?.startsWith('/')) router.push(item.link)
}
</script>

<template>
  <section class="card recent" aria-labelledby="recent-title">
    <header class="head">
      <span class="head-icon"><AppIcon name="bell" :size="20" /></span>
      <div class="head-text">
        <h2 id="recent-title">Recent notifications</h2>
        <p class="muted">{{ notificationState.unread ? `${notificationState.unread} unread · newest first` : 'All caught up · newest first' }}</p>
      </div>
      <button v-if="notificationState.unread" type="button" class="btn btn-outline btn-sm" @click="markAllRead">Mark all as read</button>
    </header>

    <p v-if="!notificationState.loaded" class="muted state">Loading…</p>
    <p v-else-if="!notificationState.items.length" class="muted state">No notifications yet.</p>
    <ol v-else class="timeline">
      <li v-for="item in notificationState.items.slice(0, props.limit)" :key="item.id" :class="{ unread: !item.read }">
        <button type="button" class="entry" @click="open(item)">
          <span class="entry-icon"><AppIcon :name="iconFor(item.type)" :size="16" /></span>
          <span class="entry-text">
            <span class="entry-top">
              <strong>{{ item.title }}</strong>
              <time :datetime="item.createdAt" :title="new Date(item.createdAt).toLocaleString()">{{ timeAgo(item.createdAt, now) }}</time>
            </span>
            <span v-if="item.message" class="msg">{{ item.message }}</span>
          </span>
          <span v-if="!item.read" class="new">New</span>
        </button>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.recent { border-radius: 22px; padding: 1.4rem; }
.head { display: flex; align-items: center; gap: 0.8rem; margin-bottom: 0.9rem; }
.head-icon { flex: none; display: grid; place-items: center; width: 42px; height: 42px; border-radius: 13px; background: var(--pink-50); color: var(--pink-600); }
.head-text { flex: 1; min-width: 0; }
.head h2 { margin: 0; font-size: 1.1rem; color: var(--purple-900); }
.head p { margin: 0.1rem 0 0; font-size: 0.82rem; }
.state { margin: 0.5rem 0 0; }
.timeline { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.35rem; }
.entry {
  display: flex; align-items: flex-start; gap: 0.75rem; width: 100%; padding: 0.7rem 0.8rem; border: 1px solid transparent;
  border-radius: 14px; background: transparent; text-align: left; font: inherit; color: var(--text); cursor: pointer;
}
.entry:hover { background: #fbf6fa; border-color: var(--border); }
.entry:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 1px; }
.unread .entry { background: var(--pink-50); }
.entry-icon { flex: none; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; background: #f3eef8; color: var(--purple-900); }
.unread .entry-icon { background: #fff; color: var(--pink-600); }
.entry-text { flex: 1; min-width: 0; display: grid; gap: 0.15rem; }
.entry-top { display: flex; align-items: baseline; justify-content: space-between; gap: 0.6rem; }
.entry-top strong { font-size: 0.9rem; color: var(--purple-900); }
time { flex: none; font-size: 0.74rem; color: var(--pink-600); font-weight: 500; }
.msg { font-size: 0.82rem; color: var(--muted); line-height: 1.45; }
.new { flex: none; align-self: center; padding: 0.1rem 0.5rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-size: 0.68rem; font-weight: 700; }
@media (max-width: 560px) { .head { flex-wrap: wrap; } .entry-top { flex-direction: column; gap: 0; } }
</style>
