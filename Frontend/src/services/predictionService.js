import { http } from './http'

export { errorMessage } from './http'

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
