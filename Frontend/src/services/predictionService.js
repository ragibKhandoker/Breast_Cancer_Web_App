import { http } from './http'
import { withSlot } from '../auth/tabSession'

export { errorMessage } from './http'

export const imageUrl = (id) => withSlot(`${http.defaults.baseURL}/api/predictions/${id}/image`)

// Uploading 50 MB on a slow line and then running the model can take longer than the usual 30 s.
const UPLOAD_TIMEOUT_MS = 5 * 60 * 1000

/**
 * Uploads one mammogram; the backend stores it and analyses it in the same request, and answers with the saved
 * report. onProgress gets the upload percentage, or null when the browser cannot tell. Sending the same uploadId
 * again (a retry) returns the record already created instead of a second one.
 */
export async function analyzeImage(file, { uploadId, onProgress, signal } = {}) {
  const form = new FormData()
  form.append('image', file)
  if (uploadId) form.append('uploadId', uploadId)
  const { data } = await http.post('/api/predictions', form, {
    timeout: UPLOAD_TIMEOUT_MS,
    signal,
    onUploadProgress: onProgress && ((event) => onProgress(event.total ? Math.round((event.loaded / event.total) * 100) : null)),
  })
  return data
}

/**
 * A message for a failed upload, and whether trying the same file again can help. Retrying is safe because the
 * upload carries an uploadId. Server-side details (database hints and so on) are not shown to patients.
 */
export function uploadFailure(err) {
  const status = err.response?.status
  if (!err.response) {
    const timedOut = err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT'
    return {
      message: timedOut
        ? 'The upload took too long and was stopped. Check your connection and try again.'
        : 'Could not reach the server. Check your internet connection and try again.',
      retryable: true,
    }
  }
  if (status === 401) return { message: 'Your session has expired. Please sign in again to upload.', retryable: false }
  if (status === 403) return { message: 'Your account is not allowed to upload mammograms. Only patient accounts can.', retryable: false }
  if (status === 413) return { message: 'The selected file exceeds the maximum allowed size.', retryable: false }
  if (status === 400) {
    return { message: err.response.data?.detail ?? 'The server could not accept this file. Please select another image.', retryable: false }
  }
  if (status === 503) {
    return { message: 'The analysis service is temporarily unavailable. Please try again in a few minutes.', retryable: true }
  }
  return { message: 'Something went wrong on the server while uploading. Please try again.', retryable: true }
}

/** The running model and its results on held-out test patients (see ML-Service/evaluate.py). */
export async function getModelInfo() {
  const { data } = await http.get('/api/model')
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

/**
 * The report's heatmap ({ rows, cols, values, modelVersion }), or null when the server's model cannot make one
 * (204). Older reports get theirs made on this first request, so it can take a few seconds.
 */
export async function getHeatmap(id) {
  const { status, data } = await http.get(`/api/predictions/${encodeURIComponent(id)}/heatmap`)
  return status === 204 || !data ? null : data
}

/**
 * Downloads the scan's PDF report (GET /api/scans/{id}/report) and saves it as a file. The first download makes
 * the report on the server, so it can take a few seconds. Errors keep the axios shape (err.response.status).
 */
export async function downloadReport(id) {
  const { data } = await http.get(`/api/scans/${encodeURIComponent(id)}/report`, {
    responseType: 'blob',
    timeout: 2 * 60 * 1000,
  })
  const url = URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = `breastcare-report-${id}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Give the browser a moment to start the download before the file's address is released.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** A message for a failed report download; the error body is a Blob, so it is not read. */
export function reportDownloadError(err) {
  const status = err.response?.status
  if (!err.response) return 'Could not reach the server. Check your connection and try again.'
  if (status === 404) return 'This report does not exist or you do not have access to it.'
  if (status === 403) return 'Your account is not allowed to download this report.'
  return 'The report could not be created. Please try again.'
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
