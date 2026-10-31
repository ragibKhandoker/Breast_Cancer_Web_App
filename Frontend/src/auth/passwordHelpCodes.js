// Remembers this browser's latest "Forgot password?" request code, so the status page can open it without the
// visitor typing it. Only on this device; anyone else needs the code itself.
const KEY = 'bc_password_help'

export function rememberHelpCode({ code, reference }) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code, reference, savedAt: new Date().toISOString() }))
  } catch {
    // Storage blocked (private mode): the visitor still sees the code to write down.
  }
}

/** { code, reference, savedAt } or null. */
export function rememberedHelpCode() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    return saved?.code ? saved : null
  } catch {
    return null
  }
}

export function forgetHelpCode() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Nothing to do.
  }
}
