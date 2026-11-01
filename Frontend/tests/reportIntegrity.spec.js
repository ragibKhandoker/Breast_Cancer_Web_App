import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('../src/services/predictionService', () => ({
  getReportIntegrity: vi.fn(),
  verifyReportIntegrity: vi.fn(),
}))

const { getReportIntegrity, verifyReportIntegrity } = await import('../src/services/predictionService')
const { default: ReportIntegrity } = await import('../src/components/report/ReportIntegrity.vue')

const PENDING = { status: 'PENDING', message: 'Blockchain verification is currently pending.', recordedAt: '2026-11-01T10:00:00Z',
  transactionReference: null, checkedAt: '2026-11-01T10:05:00Z' }
const VERIFIED = { ...PENDING, status: 'VERIFIED', message: 'This report matches its original recorded integrity hash.',
  transactionReference: '0x' + 'ab'.repeat(32), network: 'hardhat-local', anchoredAt: '2026-11-01T10:01:00Z' }
const FAILED = { ...PENDING, status: 'FAILED', message: 'The report content does not match the originally recorded integrity hash.' }

describe('ReportIntegrity', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the stored status and verifies on request', async () => {
    getReportIntegrity.mockResolvedValue(PENDING)
    const wrapper = mount(ReportIntegrity, { props: { scanId: 's1' } })
    await flushPromises()
    expect(getReportIntegrity).toHaveBeenCalledWith('s1')
    expect(wrapper.find('.badge').text()).toBe('Pending')
    expect(wrapper.find('.message').text()).toBe('Blockchain verification is currently pending.')

    verifyReportIntegrity.mockResolvedValue(VERIFIED)
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(verifyReportIntegrity).toHaveBeenCalledWith('s1')
    expect(wrapper.find('.badge').text()).toBe('Verified')
    expect(wrapper.find('.badge').classes()).toContain('ok')
    expect(wrapper.text()).toContain('This report matches its original recorded integrity hash.')
    expect(wrapper.text()).toContain('hardhat-local')
    expect(wrapper.find('.mono').attributes('title')).toBe(VERIFIED.transactionReference)
  })

  it('shows a failed check clearly', async () => {
    getReportIntegrity.mockResolvedValue(FAILED)
    const wrapper = mount(ReportIntegrity, { props: { scanId: 's1' } })
    await flushPromises()
    expect(wrapper.find('.badge').text()).toBe('Verification failed')
    expect(wrapper.find('.badge').classes()).toContain('bad')
    expect(wrapper.text()).toContain('does not match the originally recorded integrity hash')
  })

  it('offers no check before a report exists, and reloads after a download', async () => {
    getReportIntegrity.mockResolvedValue({ status: 'NO_REPORT', message: 'No PDF report has been made yet.' })
    const wrapper = mount(ReportIntegrity, { props: { scanId: 's1', refreshKey: 0 } })
    await flushPromises()
    expect(wrapper.find('button').exists()).toBe(false)
    getReportIntegrity.mockResolvedValue(PENDING)
    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()
    expect(getReportIntegrity).toHaveBeenCalledTimes(2)
    expect(wrapper.find('button').exists()).toBe(true)
  })
})
