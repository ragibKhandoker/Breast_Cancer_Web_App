import { http } from './http'

// Breast Cancer Knowledge Hub. Reading is public; progress needs a sign-in; the admin calls need ADMIN and
// submissions an approved Breast Surgeon (the backend checks each).

export const listModules = () => http.get('/api/knowledge/modules').then((r) => r.data)
export const getModule = (slug) => http.get(`/api/knowledge/modules/${encodeURIComponent(slug)}`).then((r) => r.data)
export const getGlossary = () => http.get('/api/knowledge/glossary').then((r) => r.data)
export const getFaqs = () => http.get('/api/knowledge/faqs').then((r) => r.data)
export const imageUrl = (fileId) => `${http.defaults.baseURL ?? ''}/api/knowledge/images/${encodeURIComponent(fileId)}`

// ----- Signed-in progress -----
export const getProgress = () => http.get('/api/learning/progress').then((r) => r.data)
export const setCompleted = (slug, completed) =>
  http.put(`/api/learning/progress/${encodeURIComponent(slug)}`, { completed }).then((r) => r.data)
export const setBookmarked = (slug, bookmarked) =>
  http.put(`/api/learning/bookmarks/${encodeURIComponent(slug)}`, { bookmarked }).then((r) => r.data)
export const setLast = (slug) => http.put(`/api/learning/last/${encodeURIComponent(slug)}`, {}).then((r) => r.data)
export const mergeProgress = (completed, bookmarks) =>
  http.post('/api/learning/progress/merge', { completed, bookmarks }).then((r) => r.data)

// ----- Administrators -----
export const adminListModules = () => http.get('/api/admin/knowledge/modules').then((r) => r.data)
export const adminCreateModule = (content) => http.post('/api/admin/knowledge/modules', content).then((r) => r.data)
export const adminUpdateModule = (slug, content) =>
  http.put(`/api/admin/knowledge/modules/${encodeURIComponent(slug)}`, content).then((r) => r.data)
export const adminSetPublished = (slug, published) =>
  http.put(`/api/admin/knowledge/modules/${encodeURIComponent(slug)}/published`, { published }).then((r) => r.data)
export function adminUploadImage(file) {
  const form = new FormData()
  form.append('image', file)
  return http.post('/api/admin/knowledge/images', form).then((r) => r.data)
}
export const adminListSubmissions = (status) =>
  http.get('/api/admin/knowledge/submissions', { params: status ? { status } : {} }).then((r) => r.data)
export const adminReviewSubmission = (id, status, note) =>
  http.put(`/api/admin/knowledge/submissions/${encodeURIComponent(id)}/review`, { status, note }).then((r) => r.data)

// ----- Breast Surgeons -----
export const mySubmissions = () => http.get('/api/knowledge-submissions').then((r) => r.data)
export const submitContent = (targetSlug, content) =>
  http.post('/api/knowledge-submissions', { targetSlug: targetSlug || null, content }).then((r) => r.data)

/** The editable fields of a module, as the editor and the API use them. */
export function contentOf(module) {
  const { slug, order, title, category, illustration, readingMinutes, description, sections, references, related } = module
  return structuredClone({ slug, order, title, category, illustration, readingMinutes, description, sections, references, related })
}
