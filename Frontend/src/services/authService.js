import { http } from './http'

// Calls to /api/auth. Each resolves with the user's safe profile: { id, fullName, email, role, createdAt }.

export async function register({ fullName, email, password }) {
  const { data } = await http.post('/api/auth/register', { fullName, email, password })
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
