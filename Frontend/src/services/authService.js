import { http } from './http'

// Calls to /api/auth. Each resolves with the user's safe profile: { id, fullName, email, role, createdAt }.

/** `accountType` is 'PATIENT' or 'DOCTOR'; a new doctor account waits for an administrator's verification. */
export async function register({ fullName, email, password, accountType = 'PATIENT' }) {
  const { data } = await http.post('/api/auth/register', { fullName, email, password, accountType })
  return data
}

export async function login({ email, password }) {
  const { data } = await http.post('/api/auth/login', { email, password })
  return data
}

/** Revokes the login on the server and removes the cookie. */
export async function logout() {
  await http.post('/api/auth/logout')
}

/** The signed-in user, or null when not signed in. */
export async function fetchCurrentUser() {
  try {
    const { data } = await http.get('/api/auth/me')
    return data
  } catch (err) {
    if (err.response?.status === 401) return null
    throw err
  }
}

/** "Forgot password?": asks an administrator to reset the password. Same answer whether or not the email exists. */
export async function requestPasswordHelp({ email, message }) {
  const { data } = await http.post('/api/auth/password-help', { email, message })
  return data
}
