// "Forgot password?" on this browser. When the visitor sends a request, the server gives back a secret code;
// it is kept here, out of sight, so the Login page can quietly check whether an administrator has approved the
// request and then show the temporary password. Only this browser has the code, which is why the password is
// shown only where the request was made.
const KEY = 'bc_password_help'
const SHOWN_KEY = 'bc_password_help_shown'

export function rememberHelpCode({ code, email }) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code, email, savedAt: new Date().toISOString() }))
  } catch {
    // Storage blocked (private mode): the request still reaches the administrator.
  }
}

/** { code, email, savedAt } or null. */
export function rememberedHelpCode() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    return saved?.code ? saved : null
  } catch {
    return null
  }
}

/**
 * The server shows a temporary password only once; keep it for this tab until the user has logged in with it,
 * so a page reload does not lose it.
 */
export function keepShownPassword(password) {
  try {
    sessionStorage.setItem(SHOWN_KEY, password)
  } catch {
    // Kept on screen only.
  }
}

export function shownPassword() {
  try {
    return sessionStorage.getItem(SHOWN_KEY) || ''
  } catch {
    return ''
  }
}

/** Once the user has logged in (or the request is over), forget everything about it. */
export function forgetHelpCode() {
  try {
    localStorage.removeItem(KEY)
    sessionStorage.removeItem(SHOWN_KEY)
  } catch {
    // Nothing to do.
  }
}
