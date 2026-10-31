import { http } from './http'
import { withSlot } from '../auth/tabSession'

// Breast Surgeons (/api/doctors/breast-surgeons). Browsing approved surgeons needs no login; the /profile calls
// are the signed-in doctor's own Breast Surgeon profile.

const BASE = '/api/doctors/breast-surgeons'

/** Approved Breast Surgeons. Filters (all optional): { q, minExperience, hospital }. */
export async function listSurgeons(filters = {}) {
  const params = Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v != null))
  const { data } = await http.get(BASE, { params })
  return data
}

export async function getSurgeon(id) {
  const { data } = await http.get(`${BASE}/${encodeURIComponent(id)}`)
  return data
}

/** [{ date: 'yyyy-MM-dd', dayOfWeek, slots: [{ time: 'HH:mm', available }] }] for the next `days` days. */
export async function getSlots(id, days = 28) {
  const { data } = await http.get(`${BASE}/${encodeURIComponent(id)}/slots`, { params: { days } })
  return data
}

/** The signed-in doctor's own profile; rejects with 404 when they have not created one yet. */
export async function getMyProfile() {
  const { data } = await http.get(`${BASE}/profile`)
  return data
}

export async function createProfile(profile) {
  const { data } = await http.post(`${BASE}/profile`, profile)
  return data
}

export async function updateProfile(profile) {
  const { data } = await http.put(`${BASE}/profile`, profile)
  return data
}

/** { availableDays, timeSlots, acceptingAppointments } */
export async function updateAvailability(availability) {
  const { data } = await http.put(`${BASE}/profile/availability`, availability)
  return data
}

export async function uploadPhoto(file) {
  const form = new FormData()
  form.append('photo', file)
  const { data } = await http.post(`${BASE}/profile/photo`, form)
  return data
}

export async function removePhoto() {
  const { data } = await http.delete(`${BASE}/profile/photo`)
  return data
}

export async function uploadDocument(file) {
  const form = new FormData()
  form.append('document', file)
  const { data } = await http.post(`${BASE}/profile/documents`, form)
  return data
}

export async function removeDocument(fileId) {
  const { data } = await http.delete(`${BASE}/profile/documents/${encodeURIComponent(fileId)}`)
  return data
}

/** Download link for one of the signed-in surgeon's own documents (the login cookie goes with it). */
export function documentUrl(fileId) {
  return withSlot(`${http.defaults.baseURL}${BASE}/profile/documents/${encodeURIComponent(fileId)}`)
}

/** A photoUrl from the API, made absolute when the API is on another host (VITE_API_URL). */
export function photoSrc(photoUrl) {
  return photoUrl ? `${http.defaults.baseURL}${photoUrl}` : ''
}
