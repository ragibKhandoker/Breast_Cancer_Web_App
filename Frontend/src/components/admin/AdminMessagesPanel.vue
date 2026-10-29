<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { listMessages, setMessageStatus, deleteMessage } from '../../services/adminService'
import { errorMessage } from '../../services/http'

const emit = defineEmits(['unread-changed'])

const filter = ref('') // '' all | 'NEW' | 'READ'
const messages = ref([])
const loading = ref(true)
const error = ref('')
const openId = ref('')
const busyId = ref('')

const unread = computed(() => messages.value.filter((m) => m.status === 'NEW').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    messages.value = await listMessages(filter.value || undefined)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

/** Opening an unread message marks it as read. */
async function toggleOpen(message) {
  openId.value = openId.value === message.id ? '' : message.id
  if (openId.value && message.status === 'NEW') await setStatus(message, 'READ')
}

async function setStatus(message, status) {
  busyId.value = message.id
  try {
    Object.assign(message, await setMessageStatus(message.id, status))
    emit('unread-changed')
    // Under a status filter, a message that no longer matches leaves the list.
    if (filter.value && filter.value !== message.status) messages.value = messages.value.filter((m) => m.id !== message.id)
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    busyId.value = ''
  }
}

async function remove(message) {
  if (!confirm(`Delete the message "${message.subject}" from ${message.email}? This cannot be undone.`)) return
  busyId.value = message.id
  try {
    await deleteMessage(message.id)
    messages.value = messages.value.filter((m) => m.id !== message.id)
    emit('unread-changed')
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    busyId.value = ''
  }
}

const replyLink = (m) => `mailto:${m.email}?subject=${encodeURIComponent(`Re: ${m.subject}`)}`

watch(filter, load)
onMounted(load)
</script>

<template>
  <section class="card inbox">
    <header class="head">
      <h3>Contact messages <span v-if="!filter && unread" class="count">{{ unread }} unread</span></h3>
      <div class="segmented" role="group" aria-label="Show messages">
        <button v-for="option in [['', 'All'], ['NEW', 'Unread'], ['READ', 'Read']]" :key="option[0]" type="button"
          :aria-pressed="filter === option[0]" :class="{ on: filter === option[0] }" @click="filter = option[0]">
          {{ option[1] }}
        </button>
      </div>
    </header>

    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <p v-if="loading" class="muted">Loading…</p>
    <div v-else-if="!messages.length" class="empty">
      <AppIcon name="mail" :size="28" />
      <p class="muted">{{ filter === 'NEW' ? 'No unread messages. All caught up!' : 'No messages yet.' }}</p>
    </div>

    <ul v-else class="list">
      <li v-for="m in messages" :key="m.id" :class="{ unread: m.status === 'NEW', open: openId === m.id }">
        <button type="button" class="summary" :aria-expanded="openId === m.id" @click="toggleOpen(m)">
          <span class="dot" :aria-label="m.status === 'NEW' ? 'Unread' : 'Read'"></span>
          <span class="who">
            <strong>{{ m.name }}</strong>
            <span class="email">{{ m.email }}</span>
          </span>
          <span class="subject">{{ m.subject }}</span>
          <span class="date">{{ new Date(m.createdAt).toLocaleString() }}</span>
        </button>
        <div v-if="openId === m.id" class="body">
          <p class="text">{{ m.message }}</p>
          <div class="actions">
            <a :href="replyLink(m)" class="btn btn-primary btn-sm">Reply by email</a>
            <button type="button" class="btn btn-outline btn-sm" :disabled="busyId === m.id"
              @click="setStatus(m, m.status === 'NEW' ? 'READ' : 'NEW')">
              Mark as {{ m.status === 'NEW' ? 'read' : 'unread' }}
            </button>
            <button type="button" class="link-danger" :disabled="busyId === m.id" @click="remove(m)">Delete</button>
          </div>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.head { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem; }
.count { margin-left: 0.35rem; padding: 0.1rem 0.55rem; border-radius: 999px; background: var(--pink-600); color: #fff; font-size: 0.76rem; }
.segmented { display: inline-flex; padding: 3px; border-radius: 999px; background: var(--pink-50); border: 1px solid var(--border); }
.segmented button { padding: 0.35rem 0.9rem; border: 0; border-radius: 999px; background: none; color: var(--muted); font: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer; }
.segmented button.on { background: #fff; color: var(--pink-600); box-shadow: 0 2px 6px rgba(216, 27, 106, 0.15); }
.empty { display: grid; justify-items: center; gap: 0.25rem; padding: 2rem 0; color: var(--pink-300); }
.list { list-style: none; margin: 0; padding: 0; }
.list li { border-bottom: 1px solid var(--border); }
.list li.open { background: var(--pink-50); border-radius: 12px; }
.summary {
  display: grid; grid-template-columns: 12px minmax(0, 1.1fr) minmax(0, 2fr) auto; align-items: center; gap: 0.9rem;
  width: 100%; padding: 0.8rem 0.6rem; border: 0; background: none; font: inherit; color: inherit; text-align: left; cursor: pointer;
}
.summary:focus-visible { outline: 3px solid var(--pink-300); border-radius: 10px; }
.dot { width: 9px; height: 9px; border-radius: 50%; background: transparent; border: 1.5px solid var(--border); }
.unread .dot { background: var(--pink-600); border-color: var(--pink-600); }
.who strong { display: block; color: var(--purple-900); font-weight: 500; }
.unread .who strong, .unread .subject { font-weight: 700; }
.email { display: block; font-size: 0.8rem; color: var(--muted); overflow-wrap: anywhere; }
.subject { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text); }
.date { font-size: 0.8rem; color: var(--muted); white-space: nowrap; }
.body { padding: 0 0.6rem 1rem 2.1rem; }
.text { margin: 0 0 0.9rem; white-space: pre-wrap; line-height: 1.6; overflow-wrap: anywhere; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.6rem; }
.actions .link-danger { margin: 0 0 0 auto; }
@media (max-width: 720px) {
  .summary { grid-template-columns: 12px minmax(0, 1fr); row-gap: 0.2rem; }
  .subject, .date { grid-column: 2; }
  .body { padding-left: 1.6rem; }
}
</style>
