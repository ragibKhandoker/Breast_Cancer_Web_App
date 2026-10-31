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

/** One account with its private profile details, verification and number of analyses. */
export async function getUserDetail(userId) {
  const { data } = await http.get(`/api/admin/users/${encodeURIComponent(userId)}`)
  return data
}

/** `status`: 'VERIFIED', 'REJECTED' (needs a note) or 'PENDING'. Resolves with the updated detail. */
export async function setVerification(userId, status, note = '') {
  const { data } = await http.patch(`/api/admin/users/${encodeURIComponent(userId)}/verification`, { status, note })
  return data
}

/** Gives the account a temporary password: { user, temporaryPassword }. Shown once; pass it on to the user. */
export async function resetPassword(userId) {
  const { data } = await http.post(`/api/admin/users/${encodeURIComponent(userId)}/password-reset`, {})
  return data
}

/** "Forgot password?" requests, newest first. `status` is 'OPEN', 'RESOLVED', 'DISMISSED' or omitted for all. */
export async function listPasswordRequests(status) {
  const { data } = await http.get('/api/admin/password-requests', { params: status ? { status } : {} })
  return data
}

export async function setPasswordRequestStatus(id, status) {
  const { data } = await http.patch(`/api/admin/password-requests/${encodeURIComponent(id)}`, { status })
  return data
}

/** Breast Surgeon profiles for review, newest submission first. `status`: 'PENDING', 'APPROVED', 'REJECTED' or omitted. */
export async function listSurgeonProfiles(status) {
  const { data } = await http.get('/api/admin/breast-surgeons', { params: status ? { status } : {} })
  return data
}

/** `status`: 'APPROVED', 'REJECTED' (needs a note) or 'PENDING'. Resolves with the updated profile. */
export async function reviewSurgeon(profileId, status, note = '') {
  const { data } = await http.patch(`/api/admin/breast-surgeons/${encodeURIComponent(profileId)}/review`, { status, note })
  return data
}

export function surgeonDocumentUrl(profileId, fileId) {
  return `${http.defaults.baseURL}/api/admin/breast-surgeons/${encodeURIComponent(profileId)}/documents/${encodeURIComponent(fileId)}`
}

/** The site owner's own account and activity. Every other administrator gets 403. */
export async function getAdminAccount() {
  const { data } = await http.get('/api/admin/account')
  return data
}
