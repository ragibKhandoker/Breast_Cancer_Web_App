import { http } from './http'

// The signed-in user's notifications (the bell in the header), newest first.

/** { items: [{ id, type, title, message, link, read, createdAt }], unread } */
export async function listNotifications(limit = 20) {
  const { data } = await http.get('/api/notifications', { params: { limit } })
  return data
}

export async function markNotificationRead(id) {
  const { data } = await http.patch(`/api/notifications/${encodeURIComponent(id)}/read`)
  return data
}

export async function markAllNotificationsRead() {
  await http.post('/api/notifications/read-all')
}

/** Icon for each notification type (AppIcon names). */
const ICONS = {
  PROFILE_UPDATED: 'user', EMAIL_CHANGED: 'mail', PASSWORD_CHANGED: 'shield-check', PASSWORD_RESET: 'shield-check',
  ACCOUNT_VERIFIED: 'check-circle', ACCOUNT_REJECTED: 'alert-circle', NEW_PATIENT: 'users', NEW_DOCTOR: 'award',
  PASSWORD_HELP: 'shield-check', CONTACT_MESSAGE: 'mail', SURGEON_SUBMITTED: 'file-text', SURGEON_APPROVED: 'check-circle',
  SURGEON_REJECTED: 'alert-circle', REPORT_SHARED: 'microscope', APPOINTMENT_REQUESTED: 'calendar',
  APPOINTMENT_RESCHEDULED: 'calendar', APPOINTMENT_CONFIRMED: 'check-circle', APPOINTMENT_CANCELLED: 'close',
  APPOINTMENT_COMPLETED: 'heart',
}
export const iconFor = (type) => ICONS[type] ?? 'bell'

/** "just now", "5 min ago", "3 h ago", "yesterday", then a date. */
export function timeAgo(iso, now = Date.now()) {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (seconds < 45) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  if (days === 1) return 'yesterday'
  if (days < 7) return `${days} days ago`
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}
