<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import AppIcon from '../components/common/AppIcon.vue'
import ChatThread from '../components/chat/ChatThread.vue'
import { getMyMessages, sendMyMessage, markMyMessagesRead } from '../services/messageService'
import { errorMessage, fieldErrors } from '../services/http'

// A patient's or doctor's conversation with the site's administrators. New replies show up within 15 seconds.
const messages = ref([])
const loading = ref(true)
const loadError = ref('')
const sending = ref(false)
const sendError = ref('')

async function load({ quiet = false } = {}) {
  if (!quiet) loading.value = true
  try {
    const data = await getMyMessages()
    messages.value = data.messages ?? []
    loadError.value = ''
    if (data.unread) await markMyMessagesRead()
  } catch (err) {
    if (!quiet) loadError.value = errorMessage(err)
  } finally {
    loading.value = false
  }
}

async function send(text, done) {
  sending.value = true
  sendError.value = ''
  try {
    const saved = await sendMyMessage(text)
    messages.value = [...messages.value, saved]
    done()
  } catch (err) {
    sendError.value = fieldErrors(err).body || errorMessage(err)
  } finally {
    sending.value = false
  }
}

let timer = null
onMounted(() => {
  load()
  timer = setInterval(() => load({ quiet: true }), 15000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <section class="container page messages-page">
    <header class="head">
      <span class="head-icon"><AppIcon name="message" :size="24" /></span>
      <div>
        <h1>Messages</h1>
        <p class="muted">Talk to the BreastCare administrators: account questions, verification, password help.</p>
      </div>
    </header>

    <div class="card chat-card">
      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="loadError" class="alert error" role="alert">{{ loadError }}</p>
      <ChatThread
        v-else
        :messages="messages"
        :mine="(m) => !m.fromAdmin"
        :sending="sending"
        :error="sendError"
        placeholder="Write to the administrators…"
        empty-text="No messages yet. Ask the administrators anything about your account."
        @send="send"
      />
    </div>
    <p class="note muted"><AppIcon name="shield-check" :size="14" /> Never send your password here. Administrators will never ask for it.</p>
  </section>
</template>

<style scoped>
.messages-page { max-width: 820px; }
.head { display: flex; gap: 0.9rem; align-items: center; margin-bottom: 1rem; }
.head-icon { flex: none; display: grid; place-items: center; width: 50px; height: 50px; border-radius: 16px; background: var(--pink-50); color: var(--pink-600); }
.head h1 { margin: 0; }
.head p { margin: 0.2rem 0 0; }
.chat-card { padding: 1rem; border-radius: 22px; }
.note { display: flex; align-items: center; gap: 0.35rem; margin: 0.75rem 0 0; font-size: 0.8rem; }
</style>
