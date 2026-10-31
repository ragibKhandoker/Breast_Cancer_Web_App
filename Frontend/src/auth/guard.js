import { authState, initAuth, endSession, markPasswordChangeRequired } from './authStore'
import { homeFor, passwordPageFor } from './roles'

/**
 * Route rules, from each route's meta:
 *   roles: [...]   signed-in users with one of these roles only (others go to /forbidden)
 *   guestOnly      login and register pages; signed-in users go to their dashboard
 * A user on an administrator's temporary password is kept on the Change password page until they choose their
 * own (the backend refuses everything else too).
 * This is for navigation only: the backend enforces the same rules on every API call.
 */
export function installAuthGuard(router) {
  router.beforeEach(async (to) => {
    await initAuth()
    const user = authState.user

    if (user?.mustChangePassword) {
      const page = passwordPageFor(user.role)
      return to.path === page ? true : { path: page, hash: '#change-password', replace: true }
    }
    if (to.meta.guestOnly && user) return homeFor(user.role)
    if (!to.meta.roles) return true
    if (!user) return { path: '/login', query: { redirect: to.fullPath } }
    if (!to.meta.roles.includes(user.role)) return { path: '/forbidden', replace: true }
    return true
  })
}

/** For the API client: the server refused a call because the user must first change a temporary password. */
export function handlePasswordChangeRequired(router) {
  if (!authState.user) return
  markPasswordChangeRequired()
  router.replace({ path: passwordPageFor(authState.user.role), hash: '#change-password' })
}

/** For the API client: the login expired or was revoked while the user was on the site. */
export function handleSessionExpired(router) {
  if (!authState.user) return
  endSession()
  const current = router.currentRoute.value
  if (current.meta.roles) {
    router.replace({ path: '/login', query: { redirect: current.fullPath, expired: '1' } })
  }
}
