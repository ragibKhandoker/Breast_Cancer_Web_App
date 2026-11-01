import { http } from './http'

// Doctor-patient care. A patient becomes a doctor's patient once the doctor confirms their appointment; the
// backend checks that relation on every call.

// ----- Patients -----
export const myDoctors = () => http.get('/api/care/my-doctors').then((r) => r.data)
export const myReviewRequests = () => http.get('/api/care/review-requests').then((r) => r.data)
export const requestReview = (predictionId, doctorId, message) =>
  http.post('/api/care/review-requests', { predictionId, doctorId, message }).then((r) => r.data)

// ----- Doctors -----
export const myPatients = () => http.get('/api/care/my-patients').then((r) => r.data)
export const patientScans = (patientId) => http.get(`/api/care/my-patients/${encodeURIComponent(patientId)}/scans`).then((r) => r.data)
export const reviewInbox = () => http.get('/api/care/review-inbox').then((r) => r.data)
export const writeReview = (requestId, note) =>
  http.put(`/api/care/review-inbox/${encodeURIComponent(requestId)}`, { note }).then((r) => r.data)

/** Runs the mammogram analysis for one of the doctor's patients; resolves to the new report. */
export async function runDetectionFor(patientId, file, { uploadId, onProgress } = {}) {
  const form = new FormData()
  form.append('image', file)
  if (uploadId) form.append('uploadId', uploadId)
  const { data } = await http.post(`/api/care/my-patients/${encodeURIComponent(patientId)}/scans`, form, {
    timeout: 5 * 60 * 1000,
    onUploadProgress: (e) => onProgress?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0),
  })
  return data
}

// ----- Both -----
export const reportReviews = (predictionId) =>
  http.get(`/api/care/reports/${encodeURIComponent(predictionId)}/reviews`).then((r) => r.data)

export const REVIEW_STATUS = { REQUESTED: 'Waiting for the doctor', REVIEWED: 'Reviewed', WITHDRAWN: 'Withdrawn' }
