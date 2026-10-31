import { reactive, readonly } from 'vue'
import { listNotifications, markNotificationRead, markAllNotificationsRead } from '../services/notificationService'

/**
 * The signed-in user's latest notifications, shared by the bell in the header and the admin account page.
 * `refresh` reloads them (newest first); the bell calls it on a timer so new ones appear without a reload.
 */
const state = reactive({ items: [], unread: 0, loaded: false, error: '' })
export const notificationState = readonly(state)

let inFlight = null
export function refreshNotifications() {
  inFlight ??= listNotifications(20)
    .then((data) => {
      state.items = data.items ?? []
      state.unread = data.unread ?? 0
      state.loaded = true
      state.error = ''
    })
    .catch(() => {
      state.error = 'Could not load notifications.'
    })
    .finally(() => (inFlight = null))
  return inFlight
}

export async function markRead(item) {
  if (item.read) return
  const target = state.items.find((n) => n.id === item.id)
  if (target) target.read = true
  state.unread = Math.max(0, state.unread - 1)
  try {
    await markNotificationRead(item.id)
  } catch {
    // Shown as read here; the next refresh corrects it if the server did not save it.
  }
}

export async function markAllRead() {
  state.items.forEach((n) => (n.read = true))
  state.unread = 0
  try {
    await markAllNotificationsRead()
  } catch {
    // As above.
  }
}

/** On sign-out, so the next user never sees the previous one's notifications. */
export function clearNotifications() {
  Object.assign(state, { items: [], unread: 0, loaded: false, error: '' })
}
