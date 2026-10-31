import { http } from './http'

// Messages between a patient or doctor and the administrators (/api/messages), and the administrators' inbox
// (/api/admin/conversations).

/** { messages: [{ id, fromAdmin, senderName, body, createdAt, readByUser }], unread } — oldest first. */
export async function getMyMessages() {
  const { data } = await http.get('/api/messages')
  return data
}

export async function sendMyMessage(body) {
  const { data } = await http.post('/api/messages', { body })
  return data
}

export async function markMyMessagesRead() {
  await http.post('/api/messages/read')
}

/** [{ userId, fullName, email, role, lastMessage, lastFromAdmin, lastAt, unread }], most recent first. */
export async function listConversations() {
  const { data } = await http.get('/api/admin/conversations')
  return data
}

export async function getConversation(userId) {
  const { data } = await http.get(`/api/admin/conversations/${encodeURIComponent(userId)}`)
  return data
}

export async function sendToUser(userId, body) {
  const { data } = await http.post(`/api/admin/conversations/${encodeURIComponent(userId)}`, { body })
  return data
}

export async function markConversationRead(userId) {
  await http.post(`/api/admin/conversations/${encodeURIComponent(userId)}/read`)
}
