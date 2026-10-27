import { http } from './http'

// The signed-in user's own account (/api/profile). Each call resolves with the full profile:
// { id, fullName, email, role, createdAt, updatedAt, phone, dateOfBirth, gender, address, specialization, hospital }

export async function getProfile() {
  const { data } = await http.get('/api/profile')
  return data
}

export async function updateProfile({ fullName, phone, dateOfBirth, gender, address, specialization, hospital }) {
  const { data } = await http.put('/api/profile', { fullName, phone, dateOfBirth, gender, address, specialization, hospital })
  return data
}

export async function changeEmail({ email, currentPassword }) {
  const { data } = await http.put('/api/profile/email', { email, currentPassword })
  return data
}

/** Signs out other devices; this browser receives a new login cookie. */
export async function changePassword({ currentPassword, newPassword }) {
  const { data } = await http.put('/api/profile/password', { currentPassword, newPassword })
  return data
}
