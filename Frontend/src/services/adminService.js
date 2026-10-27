import { http } from './http'

// Administrator-only calls to /api/admin. The backend refuses them for any other role.

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
