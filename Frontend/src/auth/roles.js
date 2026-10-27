// Roles as the backend names them, and where each one lands after signing in.
export const ROLES = { PATIENT: 'PATIENT', DOCTOR: 'DOCTOR', ADMIN: 'ADMIN' }

export const ROLE_LABELS = { PATIENT: 'Patient', DOCTOR: 'Doctor', ADMIN: 'Administrator' }

const HOME_BY_ROLE = { PATIENT: '/dashboard', DOCTOR: '/doctor', ADMIN: '/admin' }

export function homeFor(role) {
  return HOME_BY_ROLE[role] ?? '/'
}

/**
 * Where to go after login: the page the user was sent away from, if it is a path on this site that their role
 * may open, otherwise their dashboard. Rejects absolute and protocol-relative URLs (open-redirect protection).
 * `resolve` is router.resolve.
 */
export function safeRedirect(target, role, resolve) {
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) {
    return homeFor(role)
  }
  const route = resolve(target)
  if (route.name === 'not-found' || route.meta.guestOnly) return homeFor(role)
  if (route.meta.roles && !route.meta.roles.includes(role)) return homeFor(role)
  return route.fullPath
}
