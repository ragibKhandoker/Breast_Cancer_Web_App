<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import ChatThread from '../chat/ChatThread.vue'
import { listConversations, getConversation, sendToUser, markConversationRead } from '../../services/messageService'
import { listUsers } from '../../services/adminService'
import { timeAgo } from '../../services/notificationService'
import { errorMessage, fieldErrors } from '../../services/http'

// The administrators' inbox: one conversation per patient or doctor, the most recent first, and a way to start
// one with anyone. Refreshes every 15 seconds. ?user=<id> opens that user's conversation.
const emit = defineEmits(['counts-changed'])
const route = useRoute()

const conversations = ref([])
const loading = ref(true)
const listError = ref('')
const search = ref('')
const selected = ref(null) // { userId, fullName, email, role }
const thread = ref([])
const threadLoading = ref(false)
const sending = ref(false)
const sendError = ref('')
const now = ref(Date.now())

// Starting a new conversation
const people = ref([])
const picking = ref(false)
const pickSearch = ref('')

const shown = computed(() => {
  const q = search.value.trim().toLowerCase()
  return conversations.value.filter((c) => !q || c.fullName.toLowerCase().includes(q) || c.email.includes(q))
})
const pickable = computed(() => {
  const q = pickSearch.value.trim().toLowerCase()
  return people.value
    .filter((u) => u.role !== 'ADMIN' && (!q || u.fullName.toLowerCase().includes(q) || u.email.includes(q)))
    .slice(0, 8)
})

async function loadList({ quiet = false } = {}) {
  if (!quiet) loading.value = true
  try {
    conversations.value = await listConversations()
    listError.value = ''
    now.value = Date.now()
  } catch (err) {
    if (!quiet) listError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function open(person, { quiet = false } = {}) {
  selected.value = { userId: person.userId ?? person.id, fullName: person.fullName, email: person.email, role: person.role }
  picking.value = false
  if (!quiet) {
    threadLoading.value = true
    sendError.value = ''
  }
  try {
    thread.value = await getConversation(selected.value.userId)
    const row = conversations.value.find((c) => c.userId === selected.value.userId)
    if (row?.unread) {
      await markConversationRead(selected.value.userId)
      row.unread = 0
      emit('counts-changed')
    }
  } catch (err) {
    if (!quiet) sendError.value = errorMessage(err)
  } finally {
    threadLoading.value = false
  }
}

async function send(text, done) {
  sending.value = true
  sendError.value = ''
  try {
    const saved = await sendToUser(selected.value.userId, text)
    thread.value = [...thread.value, saved]
    done()
    loadList({ quiet: true })
  } catch (err) {
    sendError.value = fieldErrors(err).body || errorMessage(err)
  } finally {
    sending.value = false
  }
}

async function startNew() {
  picking.value = !picking.value
  if (picking.value && !people.value.length) {
    try {
      people.value = await listUsers()
    } catch (err) {
      listError.value = errorMessage(err)
    }
  }
}

let timer = null
onMounted(async () => {
  await loadList()
  const wanted = route.query.user
  if (wanted) {
    const row = conversations.value.find((c) => c.userId === wanted)
    if (row) open(row)
  }
  timer = setInterval(() => {
    loadList({ quiet: true })
    if (selected.value) open(selected.value, { quiet: true })
  }, 15000)
})
onBeforeUnmount(() => clearInterval(timer))

const initials = (name) => (name ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('')
</script>

<template>
  <div class="inbox">
    <section class="card list" aria-labelledby="inbox-title">
      <header class="list-head">
        <h3 id="inbox-title">Inbox</h3>
        <button type="button" class="btn btn-primary btn-sm" @click="startNew"><AppIcon name="message" :size="15" /> New message</button>
      </header>

      <div v-if="picking" class="picker">
        <input v-model="pickSearch" type="search" placeholder="Find a patient or doctor" aria-label="Find a patient or doctor" />
        <ul>
          <li v-for="u in pickable" :key="u.id">
            <button type="button" @click="open(u)">
              <span class="mini">{{ initials(u.fullName) }}</span>
              <span class="who"><strong>{{ u.fullName }}</strong><span>{{ u.email }} · {{ u.role === 'DOCTOR' ? 'Doctor' : 'Patient' }}</span></span>
            </button>
          </li>
          <li v-if="!pickable.length" class="muted none">No matching patients or doctors.</li>
        </ul>
      </div>

      <input v-model="search" type="search" class="search" placeholder="Search conversations" aria-label="Search conversations" />
      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="listError" class="error">{{ listError }}</p>
      <p v-else-if="!shown.length" class="muted empty">{{ search ? 'No conversations match.' : 'No messages yet. Start one with “New message”.' }}</p>
      <ul v-else class="convs">
        <li v-for="c in shown" :key="c.userId">
          <button type="button" class="conv" :class="{ on: selected?.userId === c.userId, unread: c.unread }" @click="open(c)">
            <span class="mini">{{ initials(c.fullName) }}</span>
            <span class="who">
              <span class="top"><strong>{{ c.fullName }}</strong><time>{{ timeAgo(c.lastAt, now) }}</time></span>
              <span class="last">{{ c.lastFromAdmin ? 'You: ' : '' }}{{ c.lastMessage }}</span>
            </span>
            <span v-if="c.unread" class="count">{{ c.unread }}</span>
          </button>
        </li>
      </ul>
    </section>

    <section class="card chat">
      <div v-if="!selected" class="placeholder">
        <span class="ph-icon"><AppIcon name="message" :size="30" /></span>
        <h3>Select a conversation</h3>
        <p class="muted">Reply to patients and doctors, or start a new message, for example to confirm a password reset.</p>
      </div>
      <template v-else>
        <header class="chat-head">
          <span class="mini big">{{ initials(selected.fullName) }}</span>
          <div class="min0">
            <h3>{{ selected.fullName }}</h3>
            <p class="muted">{{ selected.email }} · {{ selected.role === 'DOCTOR' ? 'Doctor' : 'Patient' }}</p>
          </div>
        </header>
        <p v-if="threadLoading" class="muted">Loading…</p>
        <ChatThread
          v-else
          :messages="thread"
          :mine="(m) => m.fromAdmin"
          :sending="sending"
          :error="sendError"
          :placeholder="`Write to ${selected.fullName}…`"
          empty-text="No messages yet. Write the first one."
          @send="send"
        />
      </template>
    </section>
  </div>
</template>

<style scoped>
.inbox { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(0, 2fr); gap: 1rem; align-items: start; }
h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.list-head { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem; }
.search, .picker input { width: 100%; padding: 0.55rem 0.8rem; border: 1.5px solid var(--border); border-radius: 12px; font: 400 0.9rem 'Poppins', sans-serif; }
.search:focus, .picker input:focus { outline: none; border-color: var(--pink-600); }
.picker { margin-bottom: 0.75rem; padding: 0.6rem; border-radius: 14px; background: #fbf8fd; border: 1px solid var(--border); }
.picker ul { list-style: none; margin: 0.4rem 0 0; padding: 0; display: grid; gap: 0.2rem; max-height: 260px; overflow-y: auto; }
.picker button, .conv {
  display: flex; align-items: center; gap: 0.6rem; width: 100%; padding: 0.55rem 0.6rem; border: 0; border-radius: 12px;
  background: transparent; text-align: left; font: inherit; color: var(--text); cursor: pointer;
}
.picker button:hover, .conv:hover { background: #fbf6fa; }
.none { padding: 0.4rem 0.6rem; font-size: 0.85rem; }
.convs { list-style: none; margin: 0.6rem 0 0; padding: 0; display: grid; gap: 0.2rem; max-height: 62vh; overflow-y: auto; }
.conv.on { background: var(--pink-50); }
.conv.unread strong { color: var(--pink-600); }
.mini { flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 50%; background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; font-weight: 700; font-size: 0.8rem; }
.mini.big { width: 46px; height: 46px; font-size: 0.95rem; }
.who { flex: 1; min-width: 0; display: grid; }
.who strong { font-size: 0.9rem; color: var(--purple-900); }
.who > span:not(.top), .last { font-size: 0.78rem; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top { display: flex; justify-content: space-between; gap: 0.5rem; }
.top time { flex: none; font-size: 0.7rem; color: var(--muted); }
.count { flex: none; min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; display: grid; place-items: center; background: var(--pink-600); color: #fff; font-size: 0.7rem; font-weight: 700; }
.empty { padding: 1rem 0.2rem; font-size: 0.88rem; }
.chat { min-height: 420px; display: flex; flex-direction: column; }
.chat-head { display: flex; align-items: center; gap: 0.75rem; padding-bottom: 0.8rem; margin-bottom: 0.8rem; border-bottom: 1px solid var(--border); }
.chat-head p { margin: 0.1rem 0 0; font-size: 0.82rem; }
.min0 { min-width: 0; }
.placeholder { margin: auto; display: grid; justify-items: center; gap: 0.4rem; text-align: center; max-width: 360px; padding: 2rem 1rem; }
.ph-icon { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 50%; background: var(--pink-50); color: var(--pink-600); }
.placeholder p { margin: 0; }
@media (max-width: 900px) { .inbox { grid-template-columns: 1fr; } }
</style>
