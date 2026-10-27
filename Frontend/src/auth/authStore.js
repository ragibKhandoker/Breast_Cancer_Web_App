import { reactive, readonly } from 'vue'
import * as authService from '../services/authService'

/**
 * Who is signed in, shared by the whole app. The login token itself is an HttpOnly cookie that scripts cannot
 * read, so this only holds the safe profile from /api/auth/me. The route guards use it for navigation, but the
 * backend checks every request on its own.
 */
const state = reactive({ user: null, ready: false })
let loading = null
const sessionEndListeners = new Set()

export const authState = readonly(state)

/** Loads the current user once (on first navigation). Safe to call repeatedly. */
export function initAuth() {
  if (state.ready) return Promise.resolve(state.user)
  loading ??= authService
    .fetchCurrentUser()
    .catch(() => null) // Backend unreachable: treat as signed out; API calls will show the error.
    .then((user) => {
      state.user = user
      state.ready = true
      loading = null
      return user
    })
  return loading
}

export async function signIn(credentials) {
  const user = await authService.login(credentials)
  state.user = user
  state.ready = true
  return user
}

/** Revokes the token on the server; local state is cleared even if that call fails. */
export async function signOut() {
  try {
    await authService.logout()
  } finally {
    endSession()
  }
}

/** Forgets the user and tells pages to drop any medical data they hold for them. */
export function endSession() {
  state.user = null
  state.ready = true
  sessionEndListeners.forEach((listener) => listener())
}

/** Registers cleanup to run when the user signs out or the session expires. Returns an unsubscribe function. */
export function onSessionEnd(listener) {
  sessionEndListeners.add(listener)
  return () => sessionEndListeners.delete(listener)
}

export function hasRole(...roles) {
  return !!state.user && roles.includes(state.user.role)
}
