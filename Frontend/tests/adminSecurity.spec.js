import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('../src/services/notificationService', async (importOriginal) => ({
  ...(await importOriginal()),
  listNotifications: vi.fn().mockResolvedValue({ items: [], unread: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
vi.mock('../src/services/authService', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../src/services/adminService', () => ({
  getOverview: vi.fn().mockResolvedValue({ support: null }),
  listMessages: vi.fn().mockResolvedValue([]),
  listSurgeonProfiles: vi.fn().mockResolvedValue([]),
  listAuditLogs: vi.fn(),
  getAuditStats: vi.fn(),
  getAuditEvent: vi.fn(),
  verifyAuditChain: vi.fn(),
  anchorAuditEvent: vi.fn(),
  getBlockchainProof: vi.fn(),
  getBackupStatus: vi.fn(),
  listBackups: vi.fn(),
  createBackup: vi.fn(),
  verifyBackup: vi.fn(),
  runRestoreTest: vi.fn(),
}))

const ADMIN = { id: 'a1', fullName: 'Nusrat Jahan', email: 'admin@example.com', role: 'ADMIN' }
const HASH = 'ab'.repeat(32)
const EVENT = {
  id: 'e2', sequence: 2, eventType: 'ADMIN_ROLE_CHANGED', actorId: 'a1', actorRole: 'ADMIN', targetType: 'USER', targetId: 'u7',
  timestamp: '2026-11-01T10:30:00Z', metadata: { previousRole: 'PATIENT', newRole: 'DOCTOR' }, previousHash: 'cd'.repeat(32),
  eventHash: HASH, hashVerified: true, blockchainStatus: 'PENDING', blockchainReference: null, blockchainNetwork: null,
  blockchainAnchoredAt: null, blockchainError: null,
}
const TAMPERED = { ...EVENT, id: 'e1', sequence: 1, eventType: 'USER_LOGIN', hashVerified: false, blockchainStatus: 'NOT_CONFIGURED' }
const STATS = { total: 12, byType: { SCAN_CREATED: 4, REPORT_DOWNLOADED: 3, REVIEW_UPDATED: 2, ADMIN_ROLE_CHANGED: 1 }, blockchain: { enabled: true, network: 'hardhat' } }

async function mountTab(tab, setup) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(ADMIN)
  const admin = await import('../src/services/adminService')
  setup(admin)
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push(`/admin?tab=${tab}`)
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
  await flushPromises()
  return { wrapper, admin }
}

describe('Audit logs tab', () => {
  const setup = (admin) => {
    admin.listAuditLogs.mockResolvedValue({ items: [EVENT, TAMPERED], total: 45, page: 0, size: 20 })
    admin.getAuditStats.mockResolvedValue(STATS)
  }

  it('shows the overview, the events and whether each hash checks out', async () => {
    const { wrapper } = await mountTab('audit', setup)
    expect(wrapper.find('h2').text()).toBe('Audit logs')
    const stats = wrapper.findAll('.stat').map((s) => s.text())
    expect(stats[0]).toContain('12')
    expect(stats[1]).toContain('4')
    expect(stats[4]).toContain('Role changes')
    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Admin role changed')
    expect(rows[0].find('.hash').classes()).toContain('ok')
    expect(rows[1].find('.hash').classes()).toContain('bad')
    expect(rows[0].find('.state').text()).toBe('Pending')
    expect(wrapper.text()).toContain('45 events · page 1 of 3')
    expect(wrapper.text()).toContain('on (hardhat)')
  })

  it('filters and pages through the server', async () => {
    const { wrapper, admin } = await mountTab('audit', setup)
    const selects = wrapper.findAll('.filters select')
    await selects[0].setValue('REPORT_DOWNLOADED')
    await wrapper.find('.filters input').setValue('u7')
    await selects[1].setValue('ANCHORED')
    await wrapper.find('.filters').trigger('submit')
    await flushPromises()
    expect(admin.listAuditLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({ eventType: 'REPORT_DOWNLOADED', actorId: 'u7', blockchainStatus: 'ANCHORED' }), 0, 20)

    const next = wrapper.findAll('.pager button')[1]
    await next.trigger('click')
    await flushPromises()
    expect(admin.listAuditLogs).toHaveBeenLastCalledWith(expect.anything(), 1, 20)
  })

  it('opens an event with its verification and anchors it', async () => {
    const { wrapper, admin } = await mountTab('audit', setup)
    admin.getAuditEvent.mockResolvedValue({
      event: EVENT,
      verification: { eventId: 'e2', sequence: 2, status: 'VERIFIED', chainStatus: 'LINKED', chainDetail: 'Linked to event 1.' },
    })
    await wrapper.findAll('tbody tr')[0].trigger('click')
    await flushPromises()
    const detail = wrapper.find('.detail')
    expect(detail.text()).toContain('Hash verified')
    expect(detail.text()).toContain('chain linked')
    expect(detail.text()).toContain('previousRole')
    expect(detail.text()).toContain(HASH)

    admin.anchorAuditEvent.mockResolvedValue({ ...EVENT, blockchainStatus: 'ANCHORED', blockchainReference: '0xabc', blockchainNetwork: 'hardhat' })
    await detail.find('.detail-actions .btn-primary').trigger('click')
    await flushPromises()
    expect(admin.anchorAuditEvent).toHaveBeenCalledWith('e2')
    expect(wrapper.find('.detail').text()).toContain('0xabc')
    expect(wrapper.find('.detail .detail-actions .btn-primary').exists()).toBe(false)

    admin.getBlockchainProof.mockResolvedValue({ found: true, matches: true, blockNumber: 42, network: 'hardhat' })
    await wrapper.find('.detail .detail-actions .btn-outline').trigger('click')
    await flushPromises()
    expect(wrapper.find('.detail').text()).toContain('On-chain proof matches')
    expect(wrapper.find('.detail').text()).toContain('Block 42')
  })

  it('checks the whole chain and shows where it breaks', async () => {
    const { wrapper, admin } = await mountTab('audit', setup)
    admin.verifyAuditChain.mockResolvedValue({ checked: 1, status: 'BROKEN', firstBadSequence: 2, problem: 'Event 2 was changed after it was written.' })
    await wrapper.find('.chain-bar button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.chain-bar .pill').text()).toBe('Event 2 was changed after it was written.')
    expect(wrapper.find('.chain-bar .pill').classes()).toContain('bad')
  })
})

describe('Backups tab', () => {
  const STATUS = {
    encryptionConfigured: true, retentionEnabled: true, retentionValid: true, retention: { daily: 7, weekly: 4, monthly: 3 },
    restoreEnabled: true, backupCount: 1, lastBackup: { file: 'backup_2026-11-01_02-00-00.bcbk', createdAt: '2026-11-01T02:00:00Z' },
    lastRestoreTest: { outcome: 'PASSED', at: '2026-11-01T03:00:00Z' },
  }
  const BACKUP = { file: 'backup_2026-11-01_02-00-00.bcbk', createdAt: '2026-11-01T02:00:00Z', sizeBytes: 2 * 1048576, sha256: 'ef'.repeat(32), documents: 120 }

  it('shows the status, lists backups and runs a restore test', async () => {
    const { wrapper, admin } = await mountTab('backups', (a) => {
      a.getBackupStatus.mockResolvedValue(STATUS)
      a.listBackups.mockResolvedValue([BACKUP])
    })
    expect(wrapper.text()).toContain('7 daily · 4 weekly · 3 monthly')
    expect(wrapper.text()).toContain('PASSED')
    expect(wrapper.find('tbody tr').text()).toContain('2.0 MB')

    admin.runRestoreTest.mockResolvedValue({ file: BACKUP.file, targetDatabase: 'breast_cancer_restore_test', passed: true,
      expected: { users: 5 }, restored: { users: 5 }, problems: [], auditEventsChecked: 9, auditEventsTampered: 0, testDatabaseKept: false })
    await wrapper.findAll('.row-actions button')[1].trigger('click')
    await flushPromises()
    expect(admin.runRestoreTest).toHaveBeenCalledWith(BACKUP.file)
    expect(wrapper.find('.report').text()).toContain('Restore test passed')
    expect(wrapper.find('.report').text()).toContain('5 / 5')
  })

  it('cannot back up without an encryption key and hides restore when it is off', async () => {
    const { wrapper } = await mountTab('backups', (a) => {
      a.getBackupStatus.mockResolvedValue({ ...STATUS, encryptionConfigured: false, restoreEnabled: false, retentionEnabled: false })
      a.listBackups.mockResolvedValue([BACKUP])
    })
    expect(wrapper.find('.actions-card button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Set BACKUP_ENCRYPTION_KEY')
    expect(wrapper.findAll('.row-actions button')).toHaveLength(1)
    expect(wrapper.text()).toContain('Restore tests are off on this server')
  })
})
