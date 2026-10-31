import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import {
  MAX_UPLOAD_BYTES,
  MESSAGES,
  formatFileSize,
  newUploadId,
  validateMammogramFile,
} from '../src/prediction/mammogramFile'

vi.mock('../src/services/predictionService', async (importOriginal) => ({
  ...(await importOriginal()),
  analyzeImage: vi.fn(),
}))

const { analyzeImage, uploadFailure } = await import('../src/services/predictionService')
const { default: MammogramUpload } = await import('../src/components/prediction/MammogramUpload.vue')

const PNG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13]
const JPEG = [0xff, 0xd8, 0xff, 0xe0, 0, 16]

const pngFile = (name = 'scan.png', type = 'image/png') => new File([new Uint8Array(PNG)], name, { type })
const jpegFile = (name = 'scan.jpg') => new File([new Uint8Array(JPEG)], name, { type: 'image/jpeg' })
const report = { id: 'r1', label: 'benign', malignantProbability: 0.12, modelVersion: 'v1', demoMode: false, createdAt: '2026-10-28T10:00:00Z' }

function httpError(status, data = {}) {
  return Object.assign(new Error(`HTTP ${status}`), { response: { status, data } })
}
const networkError = () => Object.assign(new Error('Network Error'), { code: 'ERR_NETWORK' })

function withSize(file, size) {
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateMammogramFile', () => {
  it('accepts PNG and JPEG files by name, type and content', async () => {
    expect((await validateMammogramFile(pngFile())).format.label).toBe('PNG')
    expect((await validateMammogramFile(jpegFile())).format.label).toBe('JPEG')
    expect((await validateMammogramFile(jpegFile('SCAN.JPEG'))).format.label).toBe('JPEG')
    // Some systems report no type at all; the content still decides.
    expect((await validateMammogramFile(pngFile('scan.png', ''))).format.label).toBe('PNG')
  })

  it('asks for a file when none is chosen', async () => {
    expect(await validateMammogramFile(null)).toEqual({ error: MESSAGES.noFile })
  })

  it('rejects unsupported formats, including DICOM and renamed files', async () => {
    const text = new File(['hello'], 'notes.txt', { type: 'text/plain' })
    const dicom = new File([new Uint8Array(200)], 'scan.dcm', { type: 'application/dicom' })
    const renamed = new File(['<html></html>'], 'scan.png', { type: 'image/png' })
    const wrongType = pngFile('scan.png', 'image/gif')
    const pngNamedJpg = new File([new Uint8Array(PNG)], 'scan.jpg', { type: '' })
    for (const file of [text, dicom, renamed, wrongType, pngNamedJpg]) {
      expect(await validateMammogramFile(file)).toEqual({ error: MESSAGES.unsupported })
    }
  })

  it('rejects empty files and files over the size limit', async () => {
    expect(await validateMammogramFile(new File([], 'scan.png', { type: 'image/png' }))).toEqual({ error: MESSAGES.empty })
    expect(await validateMammogramFile(withSize(pngFile(), MAX_UPLOAD_BYTES + 1))).toEqual({ error: MESSAGES.tooLarge })
    expect((await validateMammogramFile(withSize(pngFile(), MAX_UPLOAD_BYTES))).format).toBeTruthy()
  })
})

describe('upload helpers', () => {
  it('formats file sizes for people', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2 KB')
    expect(formatFileSize(2.5 * 1024 * 1024)).toBe('2.5 MB')
  })

  it('makes a new UUID for every upload', () => {
    const id = newUploadId()
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(newUploadId()).not.toBe(id)
  })

  it('explains failures without server internals, and says which ones a retry can fix', () => {
    expect(uploadFailure(networkError())).toMatchObject({ retryable: true, message: expect.stringMatching(/reach the server/) })
    expect(uploadFailure(Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }))).toMatchObject({ retryable: true, message: expect.stringMatching(/too long/) })
    expect(uploadFailure(httpError(401))).toMatchObject({ retryable: false, message: expect.stringMatching(/session has expired/) })
    expect(uploadFailure(httpError(413))).toMatchObject({ retryable: false, message: expect.stringMatching(/maximum allowed size/) })
    expect(uploadFailure(httpError(400, { detail: 'Upload a PNG or JPG mammogram' }))).toEqual({ retryable: false, message: 'Upload a PNG or JPG mammogram' })
    const db = uploadFailure(httpError(503, { detail: 'Cannot reach MongoDB. Check MONGODB_URI in Backend/.env' }))
    expect(db.retryable).toBe(true)
    expect(db.message).not.toMatch(/MongoDB|MONGODB_URI/)
    expect(uploadFailure(httpError(500))).toMatchObject({ retryable: true })
  })
})

describe('MammogramUpload', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })
  afterEach(() => wrapper?.unmount())

  const uploadButton = () => wrapper.get('[data-testid="upload-button"]')

  // Reading a file's first bytes takes a few event-loop turns in jsdom; wait until the check is done.
  async function settled() {
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.text()).not.toContain('Checking file'))
  }

  async function choose(...files) {
    const input = wrapper.get('[data-testid="file-input"]')
    Object.defineProperty(input.element, 'files', { value: files, configurable: true })
    await input.trigger('change')
    await settled()
  }

  async function consent() {
    await wrapper.get('#upload-consent').setValue(true)
  }

  function mountUpload() {
    wrapper = mount(MammogramUpload, { attachTo: document.body })
    return wrapper
  }

  it('starts with the drop area, the rules, and a disabled upload button', () => {
    mountUpload()
    expect(wrapper.text()).toContain('Drag and drop your mammogram here')
    expect(wrapper.text()).toContain('Browse Files')
    expect(wrapper.text()).toMatch(/PNG or JPEG.*50 MB/)
    expect(wrapper.text()).toContain('Privacy notice')
    expect(uploadButton().attributes('disabled')).toBeDefined()
    expect(wrapper.get('#upload-hint').text()).toBe(MESSAGES.noFile)
  })

  it('previews a chosen file and enables upload only after consent', async () => {
    mountUpload()
    await choose(pngFile('left-cc.png'))
    expect(wrapper.get('img').attributes('src')).toBe('blob:preview')
    expect(wrapper.text()).toContain('left-cc.png')
    expect(wrapper.text()).toContain('Ready to upload')
    expect(wrapper.text()).toMatch(/PNG · 12 B/)
    expect(uploadButton().attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('Craniocaudal')
    expect(wrapper.find('input[name="mammogram-view"]').exists()).toBe(false)
    expect(wrapper.get('#upload-hint').text()).toMatch(/consent/)

    await wrapper.get('#upload-consent').setValue(true)
    expect(uploadButton().attributes('disabled')).toBeUndefined()
    expect(wrapper.find('#upload-hint').exists()).toBe(false)
  })

  it('shows a clear message for unsupported or oversized files and does not preview them', async () => {
    mountUpload()
    await choose(new File(['hello'], 'notes.txt', { type: 'text/plain' }))
    expect(wrapper.get('[role="alert"]').text()).toContain('Unsupported file format')
    expect(wrapper.find('img').exists()).toBe(false)

    await choose(withSize(pngFile(), MAX_UPLOAD_BYTES + 1))
    expect(wrapper.get('[role="alert"]').text()).toContain('exceeds the maximum allowed size')
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('accepts a dropped file and refuses several at once', async () => {
    mountUpload()
    const area = wrapper.get('[data-testid="drop-area"]')
    await area.trigger('drop', { dataTransfer: { files: [pngFile(), jpegFile()] } })
    await flushPromises()
    expect(wrapper.text()).toContain(MESSAGES.multiple)

    await area.trigger('drop', { dataTransfer: { files: [jpegFile('dropped.jpg')] } })
    await settled()
    expect(wrapper.text()).toContain('dropped.jpg')
    expect(wrapper.text()).not.toContain(MESSAGES.multiple)

    // Dropping on the chosen image replaces it.
    await wrapper.get('.selected').trigger('drop', { dataTransfer: { files: [pngFile('replacement.png')] } })
    await settled()
    expect(wrapper.text()).toContain('replacement.png')
    expect(wrapper.text()).not.toContain('dropped.jpg')
  })

  it('removes the file and frees its preview', async () => {
    mountUpload()
    await choose(pngFile())
    await wrapper.findAll('button').find((b) => b.text().includes('Remove')).trigger('click')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('Browse Files')
  })

  it('asks for another file when the image cannot be previewed', async () => {
    mountUpload()
    await choose(pngFile())
    await wrapper.get('img').trigger('error')
    expect(wrapper.get('[role="alert"]').text()).toContain(MESSAGES.previewFailed)
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('shows real upload progress, then the analysing step, and blocks a second submit', async () => {
    let finish
    analyzeImage.mockImplementation((_file, { onProgress }) => {
      onProgress(40)
      return new Promise((resolve) => (finish = () => resolve(report)))
    })
    mountUpload()
    await choose(pngFile())
    await consent()
    await uploadButton().trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('40%')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('40')
    expect(uploadButton().attributes('disabled')).toBeDefined()
    await uploadButton().trigger('click')
    expect(analyzeImage).toHaveBeenCalledTimes(1)
    expect(wrapper.find('#upload-consent').attributes('disabled')).toBeDefined()

    analyzeImage.mock.calls[0][1].onProgress(100)
    await flushPromises()
    expect(wrapper.text()).toContain('Analysing the image')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBeUndefined()
    expect(wrapper.emitted('uploaded')).toBeUndefined()

    finish()
    await flushPromises()
    expect(wrapper.emitted('uploaded')).toEqual([[report]])
    expect(wrapper.text()).toContain('Upload complete')
  })

  it('draws a scanning overlay on the preview while the image is analysed', async () => {
    let finish
    analyzeImage.mockImplementation(() => new Promise((resolve) => (finish = () => resolve(report))))
    mountUpload()
    await choose(pngFile())
    expect(wrapper.find('.scan').classes()).toContain('ready')
    expect(wrapper.find('.scan .tag').text()).toBe('Ready to scan')
    expect(wrapper.find('.scan').attributes('aria-hidden')).toBe('true')

    await consent()
    await uploadButton().trigger('click')
    await flushPromises()
    expect(wrapper.find('.scan').classes()).toContain('scanning')
    expect(wrapper.find('.preview-frame').classes()).toContain('scanning')
    expect(wrapper.find('.scan .beam').exists()).toBe(true)
    expect(wrapper.find('.scan .label').text()).toContain('AI scanning')

    finish()
    await flushPromises()
    expect(wrapper.find('.scan').classes()).toContain('done')
    expect(wrapper.find('.scan .beam').exists()).toBe(false)
    expect(wrapper.find('.scan .tag').text()).toBe('Scan complete')
  })

  it('keeps the file after a network failure and retries with the same upload id', async () => {
    analyzeImage.mockRejectedValueOnce(networkError()).mockResolvedValueOnce(report)
    mountUpload()
    const file = pngFile()
    await choose(file)
    await consent()
    await uploadButton().trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Could not reach the server')
    expect(wrapper.text()).not.toContain('Upload complete')
    expect(wrapper.emitted('uploaded')).toBeUndefined()
    expect(wrapper.get('img').exists()).toBe(true)

    await wrapper.findAll('button').find((b) => b.text().includes('Retry upload')).trigger('click')
    await flushPromises()
    const [first, second] = analyzeImage.mock.calls
    expect(second[0]).toBe(file)
    expect(second[1].uploadId).toBe(first[1].uploadId)
    expect(wrapper.emitted('uploaded')).toEqual([[report]])
  })

  it('does not offer a retry when the server rejects the file', async () => {
    analyzeImage.mockRejectedValueOnce(httpError(400, { detail: 'Could not read the image. Upload a PNG or JPG file.' }))
    mountUpload()
    await choose(pngFile())
    await consent()
    await uploadButton().trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Could not read the image')
    expect(wrapper.findAll('button').some((b) => b.text().includes('Retry upload'))).toBe(false)
  })

  it('does not send a view', async () => {
    analyzeImage.mockResolvedValueOnce(report)
    mountUpload()
    await choose(pngFile())
    await consent()
    await uploadButton().trigger('click')
    await flushPromises()
    expect(analyzeImage.mock.calls[0][1].view).toBeUndefined()
  })

  it('uses a new upload id for a new file and resets consent when starting over', async () => {
    analyzeImage.mockResolvedValue(report)
    mountUpload()
    await choose(pngFile())
    await consent()
    await uploadButton().trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text().includes('Upload another')).trigger('click')
    expect(wrapper.emitted('reset')).toHaveLength(1)
    expect(wrapper.get('#upload-consent').element.checked).toBe(false)

    await choose(jpegFile())
    await consent()
    await uploadButton().trigger('click')
    await flushPromises()
    expect(analyzeImage.mock.calls[1][1].uploadId).not.toBe(analyzeImage.mock.calls[0][1].uploadId)
  })
})
