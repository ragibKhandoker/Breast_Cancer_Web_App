<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { authState, onSessionEnd } from '../../auth/authStore'
import { APP_ROUTES } from '../../config/appRoutes'
import { sendChat, chatError, MAX_MESSAGE_CHARS } from '../../services/chatService'

// The breast health assistant: a button in the corner of every page that opens a chat panel. Answers come from
// POST /api/chat, which only answers breast cancer and breast health questions. The conversation lives only in
// this component: it is not saved in the browser and is cleared when the user signs out.
const SUGGESTIONS = [
  'What are the early signs of breast cancer?',
  'How do I do a breast self-exam?',
  'When should I start getting mammograms?',
  'What does the heatmap on my report mean?',
]

const open = ref(false)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const error = ref('')
const list = ref(null)
const input = ref(null)

const signedIn = computed(() => !!authState.user)
const canSend = computed(() => !sending.value && draft.value.trim().length > 0 && draft.value.length <= MAX_MESSAGE_CHARS)

async function scrollToEnd() {
  await nextTick()
  if (list.value) list.value.scrollTop = list.value.scrollHeight
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await scrollToEnd()
    input.value?.focus()
  }
}

async function send(text = draft.value) {
  const question = text.trim()
  if (!question || sending.value || question.length > MAX_MESSAGE_CHARS) return
  error.value = ''
  messages.value.push({ role: 'user', content: question })
  draft.value = ''
  sending.value = true
  scrollToEnd()
  try {
    const { reply, onTopic } = await sendChat([...messages.value])
    messages.value.push({ role: 'assistant', content: reply, offTopic: !onTopic })
  } catch (err) {
    // Take the question back so the conversation stays user/assistant pairs, and let the user resend it.
    messages.value.pop()
    draft.value = question
    error.value = chatError(err)
  } finally {
    sending.value = false
    scrollToEnd()
    nextTick(() => input.value?.focus())
  }
}

function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

function newChat() {
  messages.value = []
  error.value = ''
  draft.value = ''
}

const stop = onSessionEnd(() => {
  newChat()
  open.value = false
})
onBeforeUnmount(stop)
</script>

<template>
  <div class="chat">
    <section v-if="open" id="breast-health-chat" class="panel" role="dialog" aria-label="Breast health assistant">
      <header class="head">
        <div>
          <h2>BreastCare Assistant</h2>
          <p>Breast cancer and breast health questions only</p>
        </div>
        <div class="head-actions">
          <button v-if="messages.length" type="button" class="text-btn" @click="newChat">New chat</button>
          <button type="button" class="close" aria-label="Close chat" @click="toggle">×</button>
        </div>
      </header>

      <template v-if="signedIn">
        <div ref="list" class="messages" aria-live="polite">
          <div v-if="!messages.length" class="welcome">
            <p>Hi! Ask me anything about breast cancer or breast health: symptoms, screening, self-exams, risk factors, treatment, or how to use BreastCare.</p>
            <div class="suggestions">
              <button v-for="s in SUGGESTIONS" :key="s" type="button" :disabled="sending" @click="send(s)">{{ s }}</button>
            </div>
          </div>
          <p
            v-for="(m, i) in messages"
            :key="i"
            class="bubble"
            :class="[m.role, { 'off-topic': m.offTopic }]"
          >{{ m.content }}</p>
          <p v-if="sending" class="bubble assistant typing">Typing…</p>
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <form class="composer" @submit.prevent="send()">
          <label for="chat-input" class="sr-only">Your question</label>
          <textarea
            id="chat-input"
            ref="input"
            v-model="draft"
            rows="2"
            :maxlength="MAX_MESSAGE_CHARS"
            placeholder="Ask about breast health…"
            :disabled="sending"
            @keydown="onKeydown"
          ></textarea>
          <button type="submit" class="btn btn-primary btn-sm" :disabled="!canSend">Send</button>
        </form>
        <p class="note">General information only, not medical advice. For symptoms or worries, see a doctor.</p>
      </template>

      <div v-else class="signed-out">
        <p>Please sign in to chat with the breast health assistant.</p>
        <RouterLink :to="APP_ROUTES.login" class="btn btn-primary btn-sm" @click="open = false">Sign in</RouterLink>
      </div>
    </section>

    <button
      type="button"
      class="launcher"
      :aria-expanded="open"
      aria-controls="breast-health-chat"
      :aria-label="open ? 'Close breast health assistant' : 'Open breast health assistant'"
      @click="toggle"
    >
      <svg v-if="!open" viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
      <span v-else aria-hidden="true" class="launcher-x">×</span>
    </button>
  </div>
</template>

<style scoped>
.chat { position: fixed; right: 1.25rem; bottom: 1.25rem; z-index: 60; display: flex; flex-direction: column; align-items: flex-end; gap: 0.75rem; }
.launcher {
  display: grid; place-items: center; width: 58px; height: 58px; border: 0; border-radius: 50%;
  background: var(--pink-600); color: #fff; cursor: pointer; box-shadow: 0 10px 28px rgba(216, 27, 106, 0.4);
}
.launcher:focus-visible { outline: 3px solid var(--purple-900); outline-offset: 3px; }
.launcher-x { font-size: 2rem; line-height: 1; }

.panel {
  display: flex; flex-direction: column; width: min(380px, calc(100vw - 2.5rem)); height: min(560px, calc(100vh - 7rem));
  border-radius: 20px; overflow: hidden; background: #fff; border: 1px solid var(--border);
  box-shadow: 0 24px 60px rgba(42, 22, 80, 0.25);
}
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem; padding: 0.9rem 1rem; background: var(--pink-600); color: #fff; }
.head h2 { margin: 0; font-size: 1.05rem; }
.head p { margin: 0.15rem 0 0; font-size: 0.78rem; opacity: 0.9; }
.head-actions { display: flex; align-items: center; gap: 0.4rem; }
.text-btn { border: 1px solid rgba(255, 255, 255, 0.7); border-radius: 999px; background: transparent; color: #fff; padding: 0.2rem 0.7rem; font: 600 0.78rem 'Poppins', sans-serif; cursor: pointer; }
.close { border: 0; background: transparent; color: #fff; font-size: 1.6rem; line-height: 1; cursor: pointer; padding: 0 0.2rem; }

.messages { flex: 1; overflow-y: auto; padding: 1rem; display: flex; flex-direction: column; gap: 0.6rem; background: var(--pink-50); }
.welcome p { margin: 0 0 0.75rem; font-size: 0.9rem; color: var(--text); line-height: 1.5; }
.suggestions { display: grid; gap: 0.4rem; }
.suggestions button {
  text-align: left; padding: 0.5rem 0.75rem; border: 1.5px solid var(--border); border-radius: 12px; background: #fff;
  color: var(--purple-900); font: 500 0.85rem 'Poppins', sans-serif; cursor: pointer;
}
.suggestions button:hover { border-color: var(--pink-300); }
.bubble { max-width: 85%; margin: 0; padding: 0.6rem 0.8rem; border-radius: 14px; font-size: 0.9rem; line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; }
.bubble.user { align-self: flex-end; background: var(--pink-600); color: #fff; border-bottom-right-radius: 4px; }
.bubble.assistant { align-self: flex-start; background: #fff; color: var(--text); border: 1px solid var(--border); border-bottom-left-radius: 4px; }
.bubble.off-topic { background: var(--warn-bg); color: var(--warn-text); border-color: #f5c98a; }
.typing { color: var(--muted) !important; font-style: italic; }

.error { margin: 0; padding: 0.5rem 1rem; font-size: 0.85rem; background: #fdecea; }
.composer { display: flex; gap: 0.5rem; align-items: flex-end; padding: 0.75rem; border-top: 1px solid var(--border); }
.composer textarea {
  flex: 1; resize: none; padding: 0.55rem 0.7rem; border: 1.5px solid var(--border); border-radius: 12px;
  font: 400 0.9rem 'Poppins', sans-serif; color: var(--text);
}
.composer textarea:focus { outline: none; border-color: var(--pink-500); }
.note { margin: 0; padding: 0 0.9rem 0.7rem; font-size: 0.72rem; color: var(--muted); }
.signed-out { flex: 1; display: grid; place-content: center; gap: 1rem; padding: 1.5rem; text-align: center; color: var(--text); }
.signed-out p { margin: 0; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

@media (max-width: 480px) {
  .chat { right: 0.75rem; bottom: 0.75rem; }
  .panel { width: calc(100vw - 1.5rem); height: calc(100vh - 6rem); }
}
</style>
