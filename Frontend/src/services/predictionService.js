import { http } from './http'

export { errorMessage } from './http'

export const imageUrl = (id) => `${http.defaults.baseURL}/api/predictions/${id}/image`

export async function analyzeImage(file) {
  const form = new FormData()
  form.append('image', file)
  const { data } = await http.post('/api/predictions', form)
  return data
}

/** The signed-in patient's own analyses, newest first. */
export async function listPredictions() {
  const { data } = await http.get('/api/predictions')
  return data
}

/** One report the signed-in user may open (their own, or shared with them as a doctor). */
export async function getPrediction(id) {
  const { data } = await http.get(`/api/predictions/${encodeURIComponent(id)}`)
  return data
}

export async function deletePrediction(id) {
  await http.delete(`/api/predictions/${encodeURIComponent(id)}`)
}

/** Doctor: reports patients shared, as [{ report, patient: { id, fullName } }]. */
export async function listSharedReports() {
  const { data } = await http.get('/api/predictions/shared')
  return data
}

/** Patient: doctors who may see this report, as [{ id, fullName }]. */
export async function listShares(id) {
  const { data } = await http.get(`/api/predictions/${encodeURIComponent(id)}/shares`)
  return data
}

export async function shareWithDoctor(id, doctorId) {
  const { data } = await http.post(`/api/predictions/${encodeURIComponent(id)}/shares`, { doctorId })
  return data
}

export async function unshareWithDoctor(id, doctorId) {
  await http.delete(`/api/predictions/${encodeURIComponent(id)}/shares/${encodeURIComponent(doctorId)}`)
}

/** Patient: doctors to choose from when sharing, as [{ id, fullName }]. */
export async function listDoctors() {
  const { data } = await http.get('/api/doctors')
  return data
}
