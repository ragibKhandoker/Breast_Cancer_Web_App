import { describe, it, expect, vi, beforeAll } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { colourAt, heatmapPixels, isValidHeatmap, strongest, MIN_INTENSITY } from '../src/prediction/heatmap'
import { riskCategory, RISK_CATEGORIES } from '../src/prediction/verdict'
import ScanViewer from '../src/components/report/ScanViewer.vue'
import ReportStatus from '../src/components/report/ReportStatus.vue'

vi.mock('../src/services/authService', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../src/services/predictionService', async (importOriginal) => ({
  ...(await importOriginal()),
  getPrediction: vi.fn(),
  getHeatmap: vi.fn(),
  listShares: vi.fn(),
  listDoctors: vi.fn(),
}))

// jsdom has no canvas; the overlay is checked through heatmapPixels instead.
beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = () => null
})

const report = {
  id: 'r1', ownerId: 'u1', originalFilename: 'left-cc.png', label: 'benign', malignantProbability: 0.12,
  benignProbability: 0.88, modelVersion: 'v1', demoMode: false, lowConfidence: false, createdAt: '2026-10-28T10:00:00Z',
}
const grid = (values, rows = 2, cols = 2) => ({ rows, cols, values, modelVersion: 'resnet-v1' })

describe('heatmap pixels', () => {
  it('checks the shape the backend promises', () => {
    expect(isValidHeatmap(grid([1, 2, 3, 4]))).toBe(true)
    expect(isValidHeatmap(grid([1, 2, 3]))).toBe(false)
    expect(isValidHeatmap(grid([1, 2, 3, NaN]))).toBe(false)
    expect(isValidHeatmap(null)).toBe(false)
  })

  it('colours from blue (weak) to red (strong)', () => {
    expect(colourAt(0)).toEqual([0, 0, 255])
    expect(colourAt(0.75)).toEqual([255, 255, 0])
    expect(colourAt(1)).toEqual([255, 0, 0])
    expect(colourAt(2)).toEqual([255, 0, 0])
  })

  it('draws nothing when no region pushed the result towards malignant', () => {
    const map = grid([-1, -2, -0.5, -3])
    expect(strongest(map)).toBe(0)
    expect(heatmapPixels(map, 8, 8).every((v) => v === 0)).toBe(true)
  })

  it('colours the strongest region red and leaves weak regions transparent', () => {
    const map = grid([0, 0, 0, 2]) // bottom-right cell only
    const pixels = heatmapPixels(map, 8, 8)
    const at = (x, y) => Array.from(pixels.slice((y * 8 + x) * 4, (y * 8 + x) * 4 + 4))
    const [r, g, b, a] = at(7, 7)
    expect([r, g, b]).toEqual([255, 0, 0])
    expect(a).toBeGreaterThan(200)
    expect(at(0, 0)[3]).toBe(0)
    expect(MIN_INTENSITY).toBeGreaterThan(0)
  })
})

describe('risk category', () => {
  it('follows the verdict, and is intermediate when the AI was not confident', () => {
    expect(riskCategory(report)).toBe(RISK_CATEGORIES.low)
    expect(riskCategory({ ...report, label: 'malignant', malignantProbability: 0.9 })).toBe(RISK_CATEGORIES.high)
    expect(riskCategory({ ...report, lowConfidence: true })).toBe(RISK_CATEGORIES.intermediate)
    expect(riskCategory({ ...report, label: 'uncertain', malignantProbability: 0.7 })).toBe(RISK_CATEGORIES.intermediate)
  })
})

describe('ReportStatus', () => {
  const statusText = (props) => mount(ReportStatus, { props: { report, ...props } }).text()

  it('tells the owner whether a doctor has been asked to review', () => {
    expect(statusText({ isOwner: true })).toContain('Not shared with a doctor')
    const shared = statusText({ isOwner: true, shares: [{ id: 'd1', fullName: 'Dr. Nusrat Jahan' }] })
    expect(shared).toContain('Awaiting doctor review')
    expect(shared).toContain('Dr. Nusrat Jahan')
    expect(statusText({ isOwner: false })).toContain('Shared with you for review')
  })

  it('shows the scan status, including demo and low-confidence results', () => {
    expect(statusText({ isOwner: true })).toContain('Analysis complete')
    expect(statusText({ report: { ...report, lowConfidence: true } })).toContain('low confidence')
    expect(statusText({ report: { ...report, demoMode: true } })).toContain('demo model')
  })
})

describe('ScanViewer', () => {
  const props = { src: '/api/predictions/r1/image', alt: 'Mammogram left-cc.png' }
  const heatmapButton = (wrapper) => wrapper.findAll('.segmented button')[1]

  it('enables the heatmap only once it is ready, and switches between overlays', async () => {
    const wrapper = mount(ScanViewer, { props: { ...props, heatmapState: 'loading' } })
    expect(heatmapButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Preparing the heatmap')

    await wrapper.setProps({ heatmapState: 'ready', heatmap: grid([0, 1, 0.5, 2]) })
    await heatmapButton(wrapper).trigger('click')
    expect(heatmapButton(wrapper).attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('canvas').attributes('style')).not.toContain('display: none')
    expect(wrapper.text()).toContain('Heatmap opacity')
    expect(wrapper.text()).toContain('resnet-v1')

    await wrapper.findAll('.segmented button')[0].trigger('click')
    expect(wrapper.find('canvas').attributes('style')).toContain('display: none')
  })

  it('explains when there is no heatmap', () => {
    const wrapper = mount(ScanViewer, { props: { ...props, heatmapState: 'unavailable' } })
    expect(heatmapButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('not available for this report')
  })

  it('zooms with the buttons and keys, and resets', async () => {
    const wrapper = mount(ScanViewer, { props: { ...props, heatmapState: 'unavailable' } })
    const level = () => wrapper.find('.zoom-level').text()
    expect(level()).toBe('100%')
    await wrapper.find('[aria-label="Zoom in"]').trigger('click')
    expect(level()).toBe('150%')
    await wrapper.find('.viewport').trigger('keydown', { key: '+' })
    expect(level()).toBe('225%')
    expect(wrapper.find('.stage').attributes('style')).toContain('scale(2.25)')
    await wrapper.find('.viewport').trigger('keydown', { key: '-' })
    expect(level()).toBe('150%')
    await wrapper.find('.reset').trigger('click')
    expect(level()).toBe('100%')
    expect(wrapper.find('[aria-label="Zoom out"]').attributes('disabled')).toBeDefined()
  })
})

describe('report page', () => {
  async function openReport({ heatmap, user = { id: 'u1', fullName: 'Ayesha Rahman', role: 'PATIENT' } }) {
    vi.resetModules()
    const authService = await import('../src/services/authService')
    authService.fetchCurrentUser.mockResolvedValue({ email: 'a@example.com', ...user })
    const predictionService = await import('../src/services/predictionService')
    predictionService.getPrediction.mockResolvedValue(report)
    predictionService.getHeatmap.mockImplementation(heatmap)
    predictionService.listShares.mockResolvedValue([])
    predictionService.listDoctors.mockResolvedValue([{ id: 'd1', fullName: 'Dr. Nusrat Jahan' }])
    const { routes } = await import('../src/router')
    const { installAuthGuard } = await import('../src/auth/guard')
    const router = createRouter({ history: createMemoryHistory(), routes })
    installAuthGuard(router)
    await router.push('/reports/r1')
    const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
    await flushPromises()
    return wrapper
  }

  it('shows the score, risk category, scan, heatmap, statuses and the disclaimer', async () => {
    const wrapper = await openReport({ heatmap: async () => grid([0, 1, 0.5, 2]) })
    const text = wrapper.text()
    expect(text).toContain('88%') // benign score
    expect(text).toContain('AI risk category: Low risk')
    expect(wrapper.find('.viewport img').attributes('src')).toContain('/api/predictions/r1/image')
    expect(wrapper.findAll('.segmented button')[1].attributes('disabled')).toBeUndefined()
    expect(text).toContain('Analysis complete')
    expect(text).toContain('Not shared with a doctor')
    expect(wrapper.find('[role="note"][aria-label="Medical disclaimer"]').text()).toContain('Not a medical diagnosis')
  })

  it('keeps working without a heatmap and keeps the disclaimer when the report cannot be opened', async () => {
    let wrapper = await openReport({ heatmap: async () => null })
    expect(wrapper.text()).toContain('not available for this report')

    wrapper = await openReport({ heatmap: async () => { throw new Error('offline') } })
    expect(wrapper.text()).toContain('could not be loaded')

    const predictionService = await import('../src/services/predictionService')
    predictionService.getPrediction.mockRejectedValue(Object.assign(new Error('HTTP 404'), { response: { status: 404 } }))
    const router = wrapper.vm.$router
    await router.push('/reports/missing')
    await flushPromises()
    expect(wrapper.text()).toContain('does not exist or you do not have access')
    expect(wrapper.find('[aria-label="Medical disclaimer"]').exists()).toBe(true)
  })
})
