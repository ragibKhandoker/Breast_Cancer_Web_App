<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import UploadPrivacyNotice from './UploadPrivacyNotice.vue'
import ScanOverlay from './ScanOverlay.vue'
import { analyzeImage, uploadFailure } from '../../services/predictionService'
import {
  ACCEPT,
  FORMAT_SUMMARY,
  MAX_UPLOAD_MB,
  MESSAGES,
  formatFileSize,
  newUploadId,
  validateMammogramFile,
} from '../../prediction/mammogramFile'

// Choose, check, preview and upload one mammogram (F05). The backend stores and analyses it in the same request;
// 'uploaded' is emitted only with the report the backend sent back.
const emit = defineEmits(['uploaded', 'reset'])

const input = ref(null)
const browseButton = ref(null)
const changeButton = ref(null)
const file = ref(null)
const format = ref(null)
const previewUrl = ref('')
const fileError = ref('')
const checking = ref(false)
const dragging = ref(false)
const consent = ref(false)
const consentError = ref('')
// idle → uploading → processing (bytes sent, model running) → success, or → error. After an error the file and
// consent stay, so Retry needs no new choice.
const status = ref('idle')
const progress = ref(null) // upload percentage, or null when the browser cannot tell
const failure = ref(null)
const announcement = ref('')

let uploadId = ''
let selection = 0 // bumps on every new choice, so a slower check of an earlier file is ignored
let dragDepth = 0
let controller = null

const busy = computed(() => status.value === 'uploading' || status.value === 'processing')
const locked = computed(() => busy.value || status.value === 'success')
const canUpload = computed(() => !!file.value && consent.value && !checking.value && !locked.value)
const hint = computed(() => {
  if (locked.value || checking.value) return ''
  if (!file.value) return MESSAGES.noFile
  if (!consent.value) return 'Tick the consent box above to enable the Upload Image button.'
  return ''
})
const fileErrorId = computed(() => (fileError.value ? 'upload-file-error' : null))

watch(consent, (given) => given && (consentError.value = ''))

function announce(message) {
  announcement.value = message
}

function clearFile() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  file.value = null
  format.value = null
}

function resetUploadState() {
  status.value = 'idle'
  progress.value = null
  failure.value = null
}

async function selectFile(candidate) {
  if (locked.value) return
  const token = ++selection
  clearFile()
  resetUploadState()
  fileError.value = ''
  checking.value = true
  const { format: found, error } = await validateMammogramFile(candidate)
  if (token !== selection) return
  checking.value = false
  if (error) {
    fileError.value = error
    return
  }
  file.value = candidate
  format.value = found
  previewUrl.value = URL.createObjectURL(candidate)
  uploadId = newUploadId()
  announce(`Selected ${candidate.name}, ${formatFileSize(candidate.size)}. Ready to upload.`)
  await nextTick()
  changeButton.value?.focus()
}

function handleFiles(list) {
  if (locked.value) return
  if (!list || list.length === 0) {
    fileError.value = MESSAGES.noFile
    return
  }
  if (list.length > 1) {
    fileError.value = MESSAGES.multiple
    return
  }
  selectFile(list[0])
}

function openPicker() {
  if (!locked.value) input.value?.click()
}

function onInputChange(event) {
  const { files } = event.target
  // Picking "Cancel" in the dialog leaves the current choice as it is.
  if (files.length) handleFiles(files)
  event.target.value = '' // so choosing the same file again still fires change
}

async function removeFile() {
  selection++
  checking.value = false
  clearFile()
  resetUploadState()
  fileError.value = ''
  announce('Image removed.')
  await nextTick()
  browseButton.value?.focus()
}

function onPreviewError() {
  clearFile()
  fileError.value = MESSAGES.previewFailed
}

// Drag and drop. Entering and leaving child elements fires its own events, so count depth instead of toggling.
function onDragEnter() {
  if (locked.value) return
  dragDepth++
  dragging.value = true
}
function onDragOver(event) {
  if (event.dataTransfer) event.dataTransfer.dropEffect = locked.value ? 'none' : 'copy'
}
function onDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragging.value = false
}
function onDrop(event) {
  dragDepth = 0
  dragging.value = false
  handleFiles(event.dataTransfer?.files)
}

// A file dropped beside the drop area would make the browser open it and leave the page.
function blockStrayDrop(event) {
  if (event.dataTransfer?.types?.includes?.('Files')) event.preventDefault()
}

function onProgress(percent) {
  progress.value = percent
  if (percent === 100 && status.value === 'uploading') {
    status.value = 'processing'
    announce('Upload finished. Analysing the image, this can take a moment.')
  }
}

async function upload() {
  if (busy.value || checking.value) return
  if (!file.value) {
    fileError.value = MESSAGES.noFile
    return
  }
  if (!consent.value) {
    consentError.value = MESSAGES.noConsent
    return
  }
  failure.value = null
  status.value = 'uploading'
  progress.value = 0
  announce('Uploading your mammogram.')
  const request = (controller = new AbortController())
  try {
    const report = await analyzeImage(file.value, { uploadId, signal: request.signal, onProgress })
    status.value = 'success'
    announce('Upload complete. Your mammogram was saved and analysed.')
    emit('uploaded', report)
  } catch (err) {
    if (request.signal.aborted) return // the page was left mid-upload
    status.value = 'error'
    failure.value = uploadFailure(err)
  } finally {
    if (controller === request) controller = null
  }
}

async function startOver() {
  selection++
  clearFile()
  resetUploadState()
  fileError.value = ''
  consent.value = false
  consentError.value = ''
  emit('reset')
  await nextTick()
  browseButton.value?.focus()
}

onMounted(() => {
  window.addEventListener('dragover', blockStrayDrop)
  window.addEventListener('drop', blockStrayDrop)
})

onBeforeUnmount(() => {
  window.removeEventListener('dragover', blockStrayDrop)
  window.removeEventListener('drop', blockStrayDrop)
  controller?.abort()
  selection++
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="upload">
    <div class="upload-grid">
      <!-- Step 1: the image -->
      <!-- Dropping works on the empty drop area and on the chosen image (to replace it). -->
      <div
        class="file-col"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
      >
        <div v-if="!file" class="drop-area" :class="{ dragging, 'has-error': fileError }" data-testid="drop-area">
          <span class="drop-icon"><AppIcon name="upload" :size="30" /></span>
          <p class="drop-title">{{ dragging ? 'Release to add your mammogram' : 'Drag and drop your mammogram here' }}</p>
          <p class="drop-or">or</p>
          <button
            ref="browseButton"
            type="button"
            class="btn btn-primary"
            :disabled="checking"
            :aria-describedby="['upload-rules', fileErrorId].filter(Boolean).join(' ')"
            @click="openPicker"
          >
            <span v-if="checking" class="spinner" aria-hidden="true"></span>
            {{ checking ? 'Checking file…' : 'Browse Files' }}
          </button>
          <p id="upload-rules" class="rules">{{ FORMAT_SUMMARY }} · up to {{ MAX_UPLOAD_MB }} MB · one image</p>
        </div>

        <div v-else class="selected card" :class="{ dragging }">
          <div class="preview-frame" :class="{ scanning: busy }">
            <!-- The overlay sits exactly on the image, not on the dark space around it. -->
            <div class="scan-target">
              <img :src="previewUrl" :alt="`Preview of the selected mammogram ${file.name}`" @error="onPreviewError" />
              <ScanOverlay :state="busy ? 'scanning' : status === 'success' ? 'done' : 'ready'" />
            </div>
          </div>
          <div class="file-meta">
            <p class="file-state">
              <AppIcon name="check-circle" :size="18" />
              {{ status === 'success' ? 'Uploaded' : 'Ready to upload' }}
            </p>
            <p class="file-name">{{ file.name }}</p>
            <p class="meta">{{ format.label }} · {{ formatFileSize(file.size) }}</p>
            <div v-if="!locked" class="file-actions">
              <button
                ref="changeButton"
                type="button"
                class="btn btn-outline btn-sm"
                :aria-describedby="fileErrorId ?? undefined"
                @click="openPicker"
              >
                <AppIcon name="refresh" :size="16" /> Change image
              </button>
              <button type="button" class="btn-text" @click="removeFile">
                <AppIcon name="trash" :size="16" /> Remove
              </button>
            </div>
          </div>
        </div>

        <p v-if="fileError" :id="fileErrorId" class="alert error" role="alert">
          <AppIcon name="alert-circle" :size="18" /> {{ fileError }}
        </p>
        <input
          ref="input"
          class="sr-only"
          type="file"
          :accept="ACCEPT"
          tabindex="-1"
          aria-hidden="true"
          data-testid="file-input"
          @change="onInputChange"
        />
      </div>

      <!-- Step 2: consent and upload -->
      <div class="action-col">
        <UploadPrivacyNotice id="upload-privacy" />

        <div class="consent" :class="{ 'has-error': consentError }">
          <input
            id="upload-consent"
            v-model="consent"
            type="checkbox"
            :disabled="locked"
            :aria-invalid="consentError ? 'true' : 'false'"
            :aria-describedby="consentError ? 'upload-privacy upload-consent-error' : 'upload-privacy'"
          />
          <label for="upload-consent">
            I have read the privacy notice and consent to the processing of my mammogram image for the stated purpose.
          </label>
        </div>
        <p v-if="consentError" id="upload-consent-error" class="field-error" role="alert">{{ consentError }}</p>

        <button
          v-if="status !== 'success'"
          type="button"
          class="btn btn-primary upload-btn"
          :disabled="!canUpload"
          :aria-describedby="hint ? 'upload-hint' : undefined"
          data-testid="upload-button"
          @click="upload"
        >
          <span v-if="busy" class="spinner" aria-hidden="true"></span>
          <AppIcon v-else name="upload" :size="18" />
          {{ status === 'processing' ? 'Analysing…' : busy ? 'Uploading…' : 'Upload Image' }}
        </button>
        <p v-if="hint" id="upload-hint" class="hint">{{ hint }}</p>

        <div v-if="busy" class="progress">
          <div class="progress-head">
            <span>{{ status === 'processing' ? 'Upload finished. Analysing the image…' : 'Uploading your mammogram…' }}</span>
            <span v-if="status === 'uploading' && progress !== null">{{ progress }}%</span>
          </div>
          <div
            class="progress-track"
            role="progressbar"
            :aria-label="status === 'processing' ? 'Analysing the image' : 'Upload progress'"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="status === 'uploading' && progress !== null ? progress : undefined"
          >
            <div
              class="progress-fill"
              :class="{ indeterminate: status === 'processing' || progress === null }"
              :style="status === 'uploading' && progress !== null ? { width: `${progress}%` } : null"
            ></div>
          </div>
        </div>

        <div v-if="status === 'error' && failure" class="alert error failure" role="alert">
          <p><AppIcon name="alert-circle" :size="18" /> {{ failure.message }}</p>
          <button v-if="failure.retryable" type="button" class="btn btn-outline btn-sm" @click="upload">
            <AppIcon name="refresh" :size="16" /> Retry upload
          </button>
          <p v-else class="failure-next">Choose a different image above, or fix the problem and upload again.</p>
        </div>

        <div v-if="status === 'success'" class="alert success">
          <p><strong>Upload complete.</strong> Your mammogram was saved to your account and analysed. The result is shown below.</p>
          <button type="button" class="btn btn-outline btn-sm" @click="startOver">Upload another mammogram</button>
        </div>
      </div>
    </div>

    <p class="sr-only" aria-live="polite" aria-atomic="true">{{ announcement }}</p>
  </div>
</template>

<style scoped>
.upload-grid { display: grid; gap: 1.5rem; margin-top: 1.5rem; }
@media (min-width: 900px) {
  .upload-grid { grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); align-items: start; }
}
.file-col, .action-col { display: grid; gap: 0.9rem; min-width: 0; }

/* Drop area */
.drop-area {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 0.35rem;
  min-height: 300px; padding: 2rem 1.25rem; text-align: center;
  border: 2px dashed var(--pink-300); border-radius: 18px;
  background: rgba(255, 255, 255, 0.88); color: var(--text);
  transition: border-color 0.15s, background-color 0.15s;
}
.drop-area.dragging { border-color: var(--pink-600); background: var(--pink-50); }
.selected.dragging { outline: 2px dashed var(--pink-600); outline-offset: 4px; }
.drop-area.has-error { border-color: var(--high); }
.drop-icon {
  display: grid; place-items: center; width: 64px; height: 64px; margin-bottom: 0.4rem;
  border-radius: 50%; background: var(--pink-100); color: var(--pink-600);
}
.drop-title { margin: 0; font-weight: 600; font-size: 1.05rem; color: var(--purple-900); }
.drop-or { margin: 0.15rem 0 0.35rem; color: var(--muted); font-size: 0.9rem; }
.rules { margin: 0.6rem 0 0; color: var(--muted); font-size: 0.85rem; }

/* Selected file */
.selected { display: grid; gap: 1rem; padding: 1rem; background: rgba(255, 255, 255, 0.92); }
@media (min-width: 560px) and (max-width: 899px) {
  .selected { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: center; }
}
.preview-frame {
  display: grid; place-items: center; min-height: 220px; max-height: 360px; overflow: hidden;
  border-radius: 12px; background: #1d1530;
}
.preview-frame img { display: block; max-width: 100%; max-height: 360px; object-fit: contain; }
.scan-target { position: relative; display: inline-block; max-width: 100%; line-height: 0; }
.preview-frame { transition: box-shadow 0.3s; }
.preview-frame.scanning { box-shadow: inset 0 0 0 2px rgba(255, 79, 154, 0.55), 0 0 24px rgba(216, 27, 106, 0.25); }
.file-meta { min-width: 0; }
.file-meta p { margin: 0; }
.file-state { display: flex; align-items: center; gap: 0.35rem; color: var(--low); font-weight: 600; font-size: 0.9rem; }
.file-name { margin-top: 0.35rem !important; font-weight: 600; color: var(--purple-900); overflow-wrap: anywhere; }
.file-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; margin-top: 0.85rem; }
.btn-text {
  display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.45rem 0.25rem;
  background: none; border: 0; border-radius: 6px; color: var(--high); font: 600 0.9rem 'Poppins', sans-serif; cursor: pointer;
}
.btn-text:hover { text-decoration: underline; }

/* Consent and upload */
.consent {
  display: flex; align-items: flex-start; gap: 0.7rem; padding: 0.9rem 1rem;
  border: 1.5px solid var(--border); border-radius: 12px; background: rgba(255, 255, 255, 0.92);
}
.consent.has-error { border-color: var(--high); }
.consent input { flex: none; width: 1.2rem; height: 1.2rem; margin: 0.15rem 0 0; accent-color: var(--pink-600); cursor: pointer; }
.consent label { font-size: 0.92rem; line-height: 1.5; color: var(--purple-900); cursor: pointer; }
.consent input:disabled, .consent input:disabled + label { cursor: not-allowed; }
.field-error { min-height: 0; margin-top: -0.4rem; }
.upload-btn { justify-content: center; width: 100%; }
.hint { margin: -0.4rem 0 0; color: var(--purple-900); font-size: 0.85rem; text-align: center; }

.alert { display: grid; gap: 0.6rem; margin: 0; }
.alert p { display: flex; align-items: flex-start; gap: 0.45rem; margin: 0; }
.alert p svg { flex: none; margin-top: 0.15rem; }
.alert .btn { justify-self: start; }
.failure-next { font-weight: 400; font-size: 0.9rem; }

/* Progress */
.progress { padding: 0.9rem 1rem; border-radius: 12px; background: rgba(255, 255, 255, 0.92); border: 1px solid var(--border); }
.progress-head { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: 0.5rem; font-size: 0.9rem; font-weight: 500; color: var(--purple-900); }
.progress-track { height: 10px; border-radius: 5px; background: var(--pink-100); overflow: hidden; }
.progress-fill { height: 100%; width: 0; border-radius: 5px; background: var(--pink-600); transition: width 0.2s ease; }
.progress-fill.indeterminate { width: 35%; animation: slide 1.4s ease-in-out infinite; }
@keyframes slide { from { transform: translateX(-100%); } to { transform: translateX(300%); } }
@media (prefers-reduced-motion: reduce) {
  .progress-fill.indeterminate { width: 100%; animation: none; opacity: 0.6; }
}

/* Keyboard focus, clearly visible on the light cards and the photo behind them */
.btn:focus-visible, .btn-text:focus-visible, .consent input:focus-visible {
  outline: 3px solid var(--purple-900); outline-offset: 3px;
}

.sr-only {
  position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
  overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;
}
</style>
