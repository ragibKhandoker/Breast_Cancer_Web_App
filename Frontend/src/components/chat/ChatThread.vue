<script setup>
import { ref, watch, nextTick, computed } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { timeAgo } from '../../services/notificationService'

// A conversation between a user and the administrators: bubbles (mine on the right) and a box to write.
// `mine(message)` says which side a message is on. Emits send(text, done); the parent saves it, adds it to
// `messages` and calls done() to empty the box.
const props = defineProps({
  messages: { type: Array, required: true },
  mine: { type: Function, required: true },
  sending: { type: Boolean, default: false },
  error: { type: String, default: '' },
  placeholder: { type: String, default: 'Write a message…' },
  emptyText: { type: String, default: 'No messages yet.' },
})
const emit = defineEmits(['send'])

const draft = ref('')
const list = ref(null)
const now = ref(Date.now())
const left = computed(() => 2000 - draft.value.length)

function scrollToEnd() {
  nextTick(() => {
    if (list.value) list.value.scrollTop = list.value.scrollHeight
  })
}
watch(() => props.messages.length, () => {
  now.value = Date.now()
  scrollToEnd()
}, { immediate: true })

// The parent calls done() once the message is saved; if sending fails the text stays in the box. (Clearing on
// any new message would lose a half-written one when a reply arrives.)
function send() {
  const text = draft.value.trim()
  if (!text || props.sending) return
  emit('send', text, () => (draft.value = ''))
}
function onKey(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}
const fullTime = (iso) => new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
</script>

<template>
  <div class="thread">
    <ol ref="list" class="bubbles" aria-live="polite">
      <li v-if="!messages.length" class="empty"><AppIcon name="message" :size="26" /> {{ emptyText }}</li>
      <li v-for="m in messages" :key="m.id" class="bubble-row" :class="{ mine: mine(m) }">
        <div class="bubble">
          <span class="who">{{ m.senderName }}</span>
          <p>{{ m.body }}</p>
          <time :datetime="m.createdAt" :title="fullTime(m.createdAt)">{{ timeAgo(m.createdAt, now) }}</time>
        </div>
      </li>
    </ol>
    <form class="composer" novalidate @submit.prevent="send">
      <textarea v-model="draft" rows="2" maxlength="2000" :placeholder="placeholder" aria-label="Message" @keydown="onKey"></textarea>
      <button type="submit" class="btn btn-primary send" :disabled="sending || !draft.trim()" aria-label="Send message">
        <span v-if="sending" class="spinner" aria-hidden="true"></span>
        <AppIcon v-else name="send" :size="18" />
      </button>
    </form>
    <p class="foot">
      <span v-if="error" class="error" role="alert">{{ error }}</span>
      <span v-else class="muted">Enter to send · Shift+Enter for a new line</span>
      <span v-if="left < 200" class="muted">{{ left }} left</span>
    </p>
  </div>
</template>

<style scoped>
.thread { display: flex; flex-direction: column; min-height: 0; height: 100%; }
.bubbles { flex: 1; min-height: 240px; max-height: 56vh; overflow-y: auto; list-style: none; margin: 0; padding: 1rem; display: flex; flex-direction: column; gap: 0.6rem; background: #fbf8fd; border-radius: 16px; }
.empty { margin: auto; display: grid; justify-items: center; gap: 0.5rem; color: var(--muted); font-size: 0.9rem; text-align: center; }
.empty :deep(svg) { color: var(--pink-300); }
.bubble-row { display: flex; }
.bubble-row.mine { justify-content: flex-end; }
.bubble { max-width: min(78%, 520px); padding: 0.6rem 0.85rem; border-radius: 16px 16px 16px 4px; background: #fff; border: 1px solid var(--border); box-shadow: 0 2px 6px rgba(42, 22, 80, 0.05); }
.mine .bubble { border-radius: 16px 16px 4px 16px; background: linear-gradient(135deg, var(--pink-600), #b5179e); border-color: transparent; color: #fff; }
.who { display: block; font-size: 0.72rem; font-weight: 700; color: var(--pink-600); }
.mine .who { color: rgba(255, 255, 255, 0.85); }
.bubble p { margin: 0.15rem 0 0; white-space: pre-line; overflow-wrap: anywhere; line-height: 1.45; font-size: 0.92rem; }
.bubble time { display: block; margin-top: 0.25rem; font-size: 0.68rem; color: var(--muted); text-align: right; }
.mine time { color: rgba(255, 255, 255, 0.75); }
.composer { display: flex; gap: 0.5rem; margin-top: 0.7rem; }
.composer textarea {
  flex: 1; min-width: 0; padding: 0.7rem 0.85rem; border: 1.5px solid var(--border); border-radius: 14px; resize: none;
  font: 400 0.95rem 'Poppins', sans-serif; color: var(--text);
}
.composer textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.12); }
.send { flex: none; width: 52px; justify-content: center; padding: 0; border-radius: 14px; }
.foot { display: flex; justify-content: space-between; gap: 0.5rem; margin: 0.35rem 0 0; font-size: 0.75rem; }
.error { color: var(--high); }
</style>
