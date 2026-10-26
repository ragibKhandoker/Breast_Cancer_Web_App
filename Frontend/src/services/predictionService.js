import axios from 'axios'

// Empty base URL uses the Vite proxy in development; set VITE_API_URL for a deployed backend.
const http = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '' })

export function errorMessage(err) {
  return err.response?.data?.detail ?? err.message ?? 'Something went wrong'
}

export const imageUrl = (id) => `${http.defaults.baseURL}/api/predictions/${id}/image`

export async function analyzeImage(file) {
  const form = new FormData()
  form.append('image', file)
  const { data } = await http.post('/api/predictions', form)
  return data
}

export async function listPredictions() {
  const { data } = await http.get('/api/predictions')
  return data
}

export async function deletePrediction(id) {
  await http.delete(`/api/predictions/${id}`)
}
