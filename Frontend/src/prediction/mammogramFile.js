// Upload rules for mammograms (F05). They mirror the backend so most mistakes are caught before uploading, but
// the backend checks every file again on its own: PredictionService accepts PNG and JPEG, judged by the file's
// bytes, and application.properties caps uploads at 50 MB. Change both sides together.
//
// DICOM is not accepted: the backend model reads images with Java ImageIO, which cannot decode DICOM.
export const MAX_UPLOAD_MB = 50
export const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024

export const FORMATS = {
  png: { label: 'PNG', mime: 'image/png', extensions: ['png'], signature: [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] },
  jpeg: { label: 'JPEG', mime: 'image/jpeg', extensions: ['jpg', 'jpeg'], signature: [0xff, 0xd8, 0xff] },
}

/** For the file input's accept attribute, which only filters the picker; validateMammogramFile decides. */
export const ACCEPT = '.png,.jpg,.jpeg,image/png,image/jpeg'
export const FORMAT_SUMMARY = 'PNG or JPEG (.png, .jpg, .jpeg)'

/** The two standard mammogram views; the model was trained and tested on both. */
export const VIEWS = [
  { value: 'CC', label: 'Craniocaudal (CC)', description: 'Taken from above: the breast is pressed top to bottom.' },
  { value: 'MLO', label: 'Mediolateral oblique (MLO)', description: 'Taken from the side at an angle, including the armpit area.' },
]

export const MESSAGES = {
  noFile: 'Please select a mammogram image first.',
  unsupported: `Unsupported file format. Please select a supported file: ${FORMAT_SUMMARY}.`,
  tooLarge: `The selected file exceeds the maximum allowed size of ${MAX_UPLOAD_MB} MB.`,
  empty: 'The selected file is empty. Please select another file.',
  previewFailed: 'The file could not be previewed. Please select another file.',
  unreadable: 'The file could not be read. Please select another file.',
  multiple: 'Please select one image at a time.',
  noConsent: 'Please confirm that you have read the privacy notice and consent before uploading.',
}

export function fileExtension(name = '') {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/** "834 KB", "2.4 MB". */
export function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatForExtension(extension) {
  return Object.values(FORMATS).find((format) => format.extensions.includes(extension)) ?? null
}

async function readHeader(file, length) {
  const slice = file.slice(0, length)
  if (typeof slice.arrayBuffer === 'function') return new Uint8Array(await slice.arrayBuffer())
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new Uint8Array(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(slice)
  })
}

/**
 * Checks a chosen file: name, browser-reported type, size, and the first bytes of its content (so a renamed
 * document is caught). Resolves to { format } when it can be uploaded, or { error } with a message to show.
 * Only the first few bytes are read, and nothing is kept or logged.
 */
export async function validateMammogramFile(file) {
  if (!file) return { error: MESSAGES.noFile }
  const format = formatForExtension(fileExtension(file.name))
  // Browsers leave the type empty when they do not know it; the content check below still applies then.
  if (!format || (file.type && file.type !== format.mime)) return { error: MESSAGES.unsupported }
  if (file.size === 0) return { error: MESSAGES.empty }
  if (file.size > MAX_UPLOAD_BYTES) return { error: MESSAGES.tooLarge }

  let header
  try {
    header = await readHeader(file, format.signature.length)
  } catch {
    return { error: MESSAGES.unreadable }
  }
  const matches = format.signature.every((byte, i) => header[i] === byte)
  return matches ? { format } : { error: MESSAGES.unsupported }
}

/** A fresh id for one upload attempt; retries reuse it so the backend saves the image only once. */
export function newUploadId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  // randomUUID needs a secure context (HTTPS or localhost); build a v4 UUID by hand elsewhere.
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
