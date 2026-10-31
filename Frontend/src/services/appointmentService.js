import { http } from './http'

// Breast Surgeon appointments (/api/appointments). Patients book, cancel and reschedule their own; surgeons
// confirm, reject (cancel) and complete theirs. Each call resolves with the appointment as the API returns it.

const BASE = '/api/appointments'
const one = (id) => `${BASE}/${encodeURIComponent(id)}`

export async function bookAppointment({ surgeonId, date, time, reason }) {
  const { data } = await http.post(BASE, { surgeonId, date, time, reason })
  return data
}

export async function listMyAppointments() {
  const { data } = await http.get(`${BASE}/my`)
  return data
}

export async function listSurgeonAppointments() {
  const { data } = await http.get(`${BASE}/doctor`)
  return data
}

export async function getAppointment(id) {
  const { data } = await http.get(one(id))
  return data
}

export async function confirmAppointment(id) {
  const { data } = await http.put(`${one(id)}/confirm`, {})
  return data
}

/** A patient cancelling, or a surgeon cancelling or rejecting a request. */
export async function cancelAppointment(id, reason = '') {
  const { data } = await http.put(`${one(id)}/cancel`, { reason })
  return data
}

export async function rescheduleAppointment(id, { date, time }) {
  const { data } = await http.put(`${one(id)}/reschedule`, { date, time })
  return data
}

export async function completeAppointment(id) {
  const { data } = await http.put(`${one(id)}/complete`, {})
  return data
}
