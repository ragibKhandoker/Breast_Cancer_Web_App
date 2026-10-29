import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('../src/services/predictionService', async (importOriginal) => ({
  ...(await importOriginal()),
  getModelInfo: vi.fn(),
}))

const { getModelInfo } = await import('../src/services/predictionService')
const { default: ModelPerformance } = await import('../src/components/prediction/ModelPerformance.vue')
const { default: ResultCard } = await import('../src/components/prediction/ResultCard.vue')

const view = (images, recall, specificity, auc) => ({
  images, malignant: 10, benign: images - 10, accuracy: 0.7, precision: 0.6, recall, specificity, f1: 0.62, roc_auc: auc,
  confusion_matrix: { true_positive: 7, false_positive: 5, true_negative: 15, false_negative: 3 },
})
const trainedInfo = {
  available: true, version: 'resnet50-cbis-test', trained: true, threshold: 0.42, uncertainMargin: 0.1,
  imageHeight: 512, imageWidth: 512, benignMax: 0.05, malignantMin: 0.93,
  metrics: {
    selective: {
      tests: {
        cbis: { images: 595, answered: 0.153, correct_when_answered: 0.923, benign_calls: 51, benign_correct: 48,
          malignant_calls: 40, malignant_correct: 36, uncertain: 504, positives_called_benign: 3 },
        vindr: { images: 2218, answered: 0.025, correct_when_answered: 0.927, benign_calls: 46, benign_correct: 44,
          malignant_calls: 9, malignant_correct: 7, uncertain: 2163, positives_called_benign: 2 },
      },
    },
    evaluated_at: '2026-10-28T12:00:00+00:00',
    tests: [
      {
        key: 'cbis', name: 'CBIS-DDSM official test split (USA, film)', labels: 'Biopsy-proven malignant vs benign findings',
        patients: 317,
        overall: { ...view(30, 0.7, 0.75, 0.781), ci95: { recall: [0.6, 0.8], specificity: [0.65, 0.85], accuracy: [0.6, 0.8], roc_auc: [0.72, 0.84] } },
        by_view: { CC: view(14, 0.71, 0.7, 0.79), MLO: view(16, 0.69, 0.8, 0.77) },
      },
      {
        key: 'vindr', name: 'VinDr-Mammo official test split (Vietnam, digital)', labels: 'Radiologist BI-RADS 4-5 vs 1-3',
        note: 'BI-RADS is not biopsy.', patients: 900,
        overall: view(40, 0.56, 0.76, 0.712),
        by_view: { CC: view(20, 0.5, 0.8, 0.7), MLO: view(20, 0.6, 0.7, 0.72) },
      },
    ],
  },
}

describe('ModelPerformance', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the measured test metrics, overall and for CC and MLO', async () => {
    getModelInfo.mockResolvedValue(trainedInfo)
    const wrapper = mount(ModelPerformance, { props: { highlightView: 'MLO' } })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('30 mammograms from 317 patients')
    expect(text).toContain('Sensitivity (recall)')
    expect(text).toContain('70.0%')
    expect(text).toContain('Specificity')
    expect(text).toContain('75.0%')
    expect(text).toContain('Precision')
    expect(text).toContain('0.781')
    expect(text).toContain('95% CI 60.0% – 80.0%')
    expect(text).toContain('Flagged 7 of 10 positive images; missed 3.')
    expect(text).toContain('CBIS-DDSM official test split')
    expect(text).toContain('VinDr-Mammo official test split')
    expect(text).toContain('40 mammograms from 900 patients')
    expect(text).toContain('0.712')
    expect(text).toContain('photos, drawings and other scans are refused')
    expect(wrapper.findAll('article.test')).toHaveLength(2)
    expect(wrapper.findAll('article.test tbody tr')).toHaveLength(4)
    expect(wrapper.findAll('tr.highlight')).toHaveLength(2)
    expect(wrapper.get('tr.highlight').text()).toContain('MLO (your image)')
    expect(text).toContain('decision threshold 42.0%')
    expect(text).toMatch(/Malignant when that is the larger\s+percentage, otherwise Benign/)
    expect(text).not.toContain('When does the AI give an answer?')
  })

  it('shows no accuracy at all for an untrained model', async () => {
    getModelInfo.mockResolvedValue({ ...trainedInfo, trained: false, metrics: null })
    const wrapper = mount(ModelPerformance)
    await flushPromises()
    expect(wrapper.text()).toContain('demo mode')
    expect(wrapper.text()).not.toMatch(/\d+\.\d%/)
  })

  it('says so when no model is installed or the request fails', async () => {
    getModelInfo.mockResolvedValueOnce({ ...trainedInfo, available: false, trained: false, metrics: null })
    let wrapper = mount(ModelPerformance)
    await flushPromises()
    expect(wrapper.text()).toContain('No model is installed')

    getModelInfo.mockRejectedValueOnce(Object.assign(new Error('x'), { code: 'ERR_NETWORK' }))
    wrapper = mount(ModelPerformance)
    await flushPromises()
    expect(wrapper.text()).toContain('Could not load the model')
  })
})

describe('ResultCard', () => {
  const base = { id: 'r1', label: 'benign', malignantProbability: 0.03, modelVersion: 'v', demoMode: false, createdAt: '2026-10-28T10:00:00Z' }

  it('says Benign or Malignant, whichever percentage is larger, and shows both', () => {
    const benign = mount(ResultCard, { props: { prediction: { ...base, view: 'CC' } } })
    expect(benign.text()).toContain('View: Craniocaudal (CC)')
    expect(benign.get('.verdict').text()).toBe('Benign')
    expect(benign.text()).toContain('97%')
    expect(benign.text()).toContain('3%')

    const malignant = mount(ResultCard, { props: { prediction: { ...base, label: 'malignant', malignantProbability: 0.62 } } })
    expect(malignant.get('.verdict').text()).toBe('Malignant')
    expect(malignant.text()).toContain('38%')
    expect(malignant.text()).toContain('62%')

    for (const w of [benign, malignant]) {
      expect(w.text()).not.toMatch(/uncertain|suspicious/i)
      expect(w.text()).toContain('not a diagnosis')
    }
  })

  it('shows older "uncertain" reports by the larger percentage, and flags demo results', () => {
    const old = mount(ResultCard, { props: { prediction: { ...base, label: 'uncertain', malignantProbability: 0.7 } } })
    expect(old.get('.verdict').text()).toBe('Malignant')
    const demo = mount(ResultCard, { props: { prediction: { ...base, demoMode: true } } })
    expect(demo.text()).toContain('Demo mode')
    expect(demo.text()).not.toContain('View:')
  })
})
