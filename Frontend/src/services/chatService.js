import { http } from './http'

// Limits the backend enforces (ChatService.java); the widget trims older messages to stay within them.
export const MAX_MESSAGES = 20
export const MAX_MESSAGE_CHARS = 2000
export const MAX_TOTAL_CHARS = 20000

/**
 * The part of the conversation to send: the newest messages that fit the backend's limits, always starting with a
 * user message and ending with the new question. Older messages are dropped in user/assistant pairs.
 */
export function conversationToSend(messages) {
  let start = Math.max(0, messages.length - (MAX_MESSAGES - 1))
  if (start % 2 === 1) start++
  const total = (from) => messages.slice(from).reduce((sum, m) => sum + m.content.length, 0)
  while (start < messages.length - 1 && total(start) > MAX_TOTAL_CHARS) start += 2
  return messages.slice(start).map(({ role, content }) => ({ role, content }))
}

/** Sends the conversation (ending with the user's question); resolves to { reply, onTopic }. */
export async function sendChat(messages) {
  const { data } = await http.post('/api/chat', { messages: conversationToSend(messages) }, { timeout: 90 * 1000 })
  return data
}

/** A message for a failed chat request. */
export function chatError(err) {
  const status = err.response?.status
  if (!err.response) return 'Could not reach the assistant. Check your connection and try again.'
  if (status === 401) return 'Your session has expired. Please sign in again.'
  if (status === 400 || status === 429 || status === 503) {
    return err.response.data?.detail ?? 'The assistant is not available right now. Please try again later.'
  }
  return 'Something went wrong. Please try again.'
}
