import { http } from './http'

// Administrator-only calls to /api/admin. The backend refuses them for any other role.

/** Site totals and system status: { users, predictions, messages, system, uploadsLast14Days }. */
export async function getOverview() {
  const { data } = await http.get('/api/admin/overview')
  return data
}

export async function listUsers() {
  const { data } = await http.get('/api/admin/users')
  return data
}

export async function createUser({ fullName, email, password, role }) {
  const { data } = await http.post('/api/admin/users', { fullName, email, password, role })
  return data
}

export async function changeRole(userId, role) {
  const { data } = await http.patch(`/api/admin/users/${encodeURIComponent(userId)}/role`, { role })
  return data
}

/** Disables (true) or re-enables (false) an account. */
export async function setUserDisabled(userId, disabled) {
  const { data } = await http.patch(`/api/admin/users/${encodeURIComponent(userId)}/status`, { disabled })
  return data
}

/** Contact form messages, newest first. `status` is 'NEW', 'READ' or omitted for all. */
export async function listMessages(status) {
  const { data } = await http.get('/api/admin/messages', { params: status ? { status } : {} })
  return data
}

export async function setMessageStatus(id, status) {
  const { data } = await http.patch(`/api/admin/messages/${encodeURIComponent(id)}`, { status })
  return data
}

export async function deleteMessage(id) {
  await http.delete(`/api/admin/messages/${encodeURIComponent(id)}`)
}

export async function getSiteSettings() {
  const { data } = await http.get('/api/admin/site-settings')
  return data
}

export async function saveSiteSettings(settings) {
  const { data } = await http.put('/api/admin/site-settings', settings)
  return data
}
