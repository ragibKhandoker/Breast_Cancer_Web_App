import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ReportReviews from '../src/components/care/ReportReviews.vue'
import DoctorPatients from '../src/components/care/DoctorPatients.vue'
import ReviewInbox from '../src/components/care/ReviewInbox.vue'
import * as care from '../src/services/careService'

vi.mock('../src/services/careService', async (importOriginal) => ({
  ...(await importOriginal()),
  myDoctors: vi.fn(),
  requestReview: vi.fn(),
  reportReviews: vi.fn(),
  writeReview: vi.fn(),
  myPatients: vi.fn(),
  patientScans: vi.fn(),
  runDetectionFor: vi.fn(),
  reviewInbox: vi.fn(),
}))

const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0])

function router() {
  const r = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<p />' } }] })
  r.push('/doctor')
  return r
}
async function mountWith(component, props = {}) {
  const r = router()
  await r.isReady()
  const w = mount(component, { props, global: { plugins: [r] } })
  await flushPromises()
  return { w, r }
}

beforeEach(() => vi.clearAllMocks())

describe('review on a report (patient)', () => {
  it('asks one of their doctors and tells the page the report is now shared', async () => {
    care.reportReviews.mockResolvedValue([])
    care.myDoctors.mockResolvedValue([{ id: 'd1', fullName: 'Dr. Nasrin', hospitalName: 'Dhaka Breast Centre' }])
    care.requestReview.mockResolvedValue({})
    const { w } = await mountWith(ReportReviews, { reportId: 'r1', isOwner: true })

    await w.find('select').setValue('d1')
    await w.find('textarea').setValue('Is this normal?')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(care.requestReview).toHaveBeenCalledWith('r1', 'd1', 'Is this normal?')
    expect(w.emitted('shared')).toHaveLength(1)
  })

  it('shows the doctor\'s answer, and explains how to get a doctor when there is none', async () => {
    care.reportReviews.mockResolvedValue([
      { id: 'q1', doctorId: 'd1', doctorName: 'Dr. Nasrin', status: 'REVIEWED', doctorNote: 'Looks reassuring.' },
    ])
    care.myDoctors.mockResolvedValue([])
    const { w } = await mountWith(ReportReviews, { reportId: 'r1', isOwner: true })
    expect(w.find('.note').text()).toBe('Looks reassuring.')
    expect(w.find('.pill').text()).toBe('Reviewed')
    expect(w.text()).toContain('after a Breast Surgeon confirms your appointment')
    expect(w.find('form').exists()).toBe(false)
  })
})

describe('review on a report (doctor)', () => {
  it('writes the review for the patient who asked', async () => {
    care.reportReviews.mockResolvedValue([{ id: 'q1', patientName: 'Alice', message: 'Please check', status: 'REQUESTED' }])
    care.writeReview.mockResolvedValue({})
    const { w } = await mountWith(ReportReviews, { reportId: 'r1', isDoctor: true })
    expect(w.text()).toContain('Alice asked for your review')
    await w.find('textarea').setValue('Benign-looking; keep yearly screening.')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(care.writeReview).toHaveBeenCalledWith('q1', 'Benign-looking; keep yearly screening.')
    expect(w.text()).toContain('Review saved')
  })

  it('shows nothing to a doctor nobody asked', async () => {
    care.reportReviews.mockResolvedValue([])
    const { w } = await mountWith(ReportReviews, { reportId: 'r1', isDoctor: true })
    expect(w.find('.reviews').exists()).toBe(false)
  })
})

describe('doctor dashboard', () => {
  it('runs a detection for a patient and opens the new report', async () => {
    care.myPatients.mockResolvedValue([{ id: 'p1', fullName: 'Alice', lastVisitDate: '2026-11-02', scans: 0, openRequests: 1 }])
    care.runDetectionFor.mockResolvedValue({ id: 'new-report' })
    const { w, r } = await mountWith(DoctorPatients)
    expect(w.find('.badge').text()).toBe('1 to review')

    const input = w.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [new File([PNG], 'mammo.png', { type: 'image/png' })] })
    await input.trigger('change')
    // The file check reads the first bytes with FileReader, which finishes on a later tick.
    await vi.waitFor(() => expect(r.currentRoute.value.path).toBe('/reports/new-report'))
    expect(care.runDetectionFor).toHaveBeenCalledWith('p1', expect.any(File), expect.objectContaining({ uploadId: expect.any(String) }))
  })

  it('refuses a file that is not a mammogram image before uploading', async () => {
    care.myPatients.mockResolvedValue([{ id: 'p1', fullName: 'Alice', lastVisitDate: '2026-11-02', scans: 0, openRequests: 0 }])
    const { w } = await mountWith(DoctorPatients)
    const input = w.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['hello'], 'notes.txt', { type: 'text/plain' })] })
    await input.trigger('change')
    await vi.waitFor(() => expect(w.find('.error').exists()).toBe(true))
    expect(care.runDetectionFor).not.toHaveBeenCalled()
  })

  it('lists review requests, open ones first', async () => {
    care.reviewInbox.mockResolvedValue([
      { id: 'q1', predictionId: 'r1', patientName: 'Alice', status: 'REQUESTED', createdAt: '2026-11-01T10:00:00Z', message: 'Please check' },
      { id: 'q2', predictionId: 'r2', patientName: 'Bob', status: 'REVIEWED', createdAt: '2026-10-01T10:00:00Z' },
    ])
    const { w } = await mountWith(ReviewInbox)
    expect(w.find('.count').text()).toBe('1')
    const links = w.findAll('a.go')
    expect(links[0].text()).toBe('Review')
    expect(links[0].attributes('href')).toBe('/reports/r1')
    expect(links[1].text()).toBe('Open')
  })
})
