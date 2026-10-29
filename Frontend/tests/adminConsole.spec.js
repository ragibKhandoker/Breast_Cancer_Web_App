import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('../src/services/authService', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../src/services/adminService', () => ({
  getOverview: vi.fn(),
  listUsers: vi.fn(),
  createUser: vi.fn(),
  changeRole: vi.fn(),
  setUserDisabled: vi.fn(),
  listMessages: vi.fn(),
  setMessageStatus: vi.fn(),
  deleteMessage: vi.fn(),
  getSiteSettings: vi.fn(),
  saveSiteSettings: vi.fn(),
}))

const ADMIN = { id: 'a1', fullName: 'Nusrat Jahan', email: 'admin@example.com', role: 'ADMIN' }

const OVERVIEW = {
  users: { total: 12, patients: 9, doctors: 2, admins: 1, disabled: 1, newLast7Days: 3, newLast30Days: 7 },
  predictions: { total: 40, last7Days: 6, malignant: 10, benign: 30, demoMode: 40, shared: 5, withoutOwner: 2 },
  messages: { total: 4, unread: 2 },
  system: { databaseName: 'breast_cancer', databaseLatencyMs: 12, model: 'v1 (untrained, demo mode)', springBootVersion: '4.1.2', javaVersion: '21', startedAt: '2026-10-27T05:00:00Z', uptimeSeconds: 7260 },
  uploadsLast14Days: Array.from({ length: 14 }, (_, i) => ({ date: `2026-10-${String(14 + i).padStart(2, '0')}`, count: i === 13 ? 4 : i % 3 })),
}
const USERS = [
  { id: 'a1', fullName: 'Nusrat Jahan', email: 'admin@example.com', role: 'ADMIN', createdAt: '2026-10-01T00:00:00Z', disabled: false, lastLoginAt: new Date().toISOString() },
  { id: 'p1', fullName: 'Rahima Begum', email: 'rahima@example.com', role: 'PATIENT', createdAt: '2026-10-02T00:00:00Z', disabled: false, lastLoginAt: null },
  { id: 'd1', fullName: 'Dr. Karim', email: 'karim@clinic.org', role: 'DOCTOR', createdAt: '2026-10-03T00:00:00Z', disabled: true, lastLoginAt: null },
]
const MESSAGES = [
  { id: 'm1', name: 'Visitor', email: 'v@example.com', subject: 'Question about results', message: 'How accurate is it?', createdAt: '2026-10-27T05:00:00Z', status: 'NEW' },
  { id: 'm2', name: 'Other', email: 'o@example.com', subject: 'Thanks', message: 'Great site.', createdAt: '2026-10-26T05:00:00Z', status: 'READ' },
]

async function mountConsole(tab) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(ADMIN)
  const admin = await import('../src/services/adminService')
  admin.getOverview.mockResolvedValue(structuredClone(OVERVIEW))
  admin.listUsers.mockResolvedValue(structuredClone(USERS))
  admin.listMessages.mockImplementation(async (status) => structuredClone(MESSAGES).filter((m) => !status || m.status === status))
  admin.getSiteSettings.mockResolvedValue({ configured: false })
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push(tab ? `/admin?tab=${tab}` : '/admin')
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
  await flushPromises()
  return { wrapper, router, admin }
}

describe('Admin console', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the admin profile and the unread badge on Messages', async () => {
    const { wrapper } = await mountConsole()
    expect(wrapper.find('.admin-card h1').text()).toBe('Nusrat Jahan')
    expect(wrapper.find('.admin-card .avatar').text()).toBe('NJ')
    expect(wrapper.find('#tab-messages .badge-count').text()).toBe('1')
  })

  it('overview shows totals, the chart with a table, and system status', async () => {
    const { wrapper } = await mountConsole()
    const tiles = wrapper.findAll('.tile').map((t) => t.text())
    expect(tiles[0]).toContain('12')
    expect(tiles[1]).toContain('40')
    expect(tiles[3]).toContain('2')
    expect(wrapper.findAll('.bar')).toHaveLength(14)
    expect(wrapper.findAll('table.sr-only tbody tr')).toHaveLength(14)
    expect(wrapper.text()).toContain('breast_cancer · 12 ms')
    expect(wrapper.text()).toContain('2h 1m')
    expect(wrapper.text()).toContain('75%') // benign share

    await wrapper.findAll('.bar-slot')[13].trigger('mouseenter')
    expect(wrapper.find('.tooltip').text()).toContain('4 analyses')
  })

  it('switches tabs through the URL', async () => {
    const { wrapper, router } = await mountConsole()
    await wrapper.find('#tab-users').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.tab).toBe('users')
    expect(wrapper.text()).toContain('Rahima Begum')
  })

  it('filters users and disables an account after confirming', async () => {
    const { wrapper, admin } = await mountConsole('users')
    expect(wrapper.findAll('tbody tr')).toHaveLength(3)
    await wrapper.find('input[type="search"]').setValue('karim')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    await wrapper.find('input[type="search"]').setValue('')
    await wrapper.find('select[aria-label="Filter by status"]').setValue('disabled')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    await wrapper.find('select[aria-label="Filter by status"]').setValue('')

    // The admin's own row has no Disable button.
    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].find('.link-btn').exists()).toBe(false)

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    admin.setUserDisabled.mockResolvedValue({ ...USERS[1], disabled: true })
    await rows[1].find('.link-btn').trigger('click')
    await flushPromises()
    expect(admin.setUserDisabled).toHaveBeenCalledWith('p1', true)
    expect(wrapper.findAll('tbody tr')[1].text()).toContain('Disabled')
    expect(wrapper.findAll('tbody tr')[1].text()).toContain('Account disabled')
  })

  it('opens a message, marking it read, and deletes one after confirming', async () => {
    const { wrapper, admin } = await mountConsole('messages')
    expect(wrapper.findAll('.list li')).toHaveLength(2)

    admin.setMessageStatus.mockResolvedValue({ ...MESSAGES[0], status: 'READ' })
    await wrapper.find('.list li .summary').trigger('click')
    await flushPromises()
    expect(admin.setMessageStatus).toHaveBeenCalledWith('m1', 'READ')
    expect(wrapper.find('.body .text').text()).toBe('How accurate is it?')
    expect(wrapper.find('.body a').attributes('href')).toBe('mailto:v@example.com?subject=Re%3A%20Question%20about%20results')

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    admin.deleteMessage.mockResolvedValue()
    await wrapper.find('.body .link-danger').trigger('click')
    await flushPromises()
    expect(admin.deleteMessage).toHaveBeenCalledWith('m1')
    expect(wrapper.findAll('.list li')).toHaveLength(1)
  })

  it('validates website settings before saving and applies them to the site', async () => {
    const { wrapper, admin } = await mountConsole('settings')
    const { site } = await import('../src/config/siteSettings')
    expect(wrapper.text()).toContain('Not customised yet')

    await wrapper.find('#set-phone').setValue('+8801712345678')
    await wrapper.find('#set-facebookUrl').setValue('javascript:alert(1)')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Enter a full link starting with https://')
    expect(admin.saveSiteSettings).not.toHaveBeenCalled()

    admin.saveSiteSettings.mockImplementation(async (payload) => ({ ...payload, configured: true, updatedAt: new Date().toISOString(), updatedBy: 'admin@example.com' }))
    await wrapper.find('#set-facebookUrl').setValue('https://facebook.com/breastcare')
    await wrapper.find('#set-announcement').setValue('Free screening week!')
    await wrapper.find('.switch input').setValue(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(admin.saveSiteSettings).toHaveBeenCalledWith(expect.objectContaining({
      contactPhone: '+8801712345678', facebookUrl: 'https://facebook.com/breastcare', announcementText: 'Free screening week!', announcementEnabled: true,
    }))
    expect(site.social.facebook).toBe('https://facebook.com/breastcare')
    expect(site.announcement).toEqual({ text: 'Free screening week!', enabled: true })
    expect(wrapper.text()).toContain('Last saved')
  })

  it('is closed to patients and doctors', async () => {
    for (const role of ['PATIENT', 'DOCTOR']) {
      vi.resetModules()
      const authService = await import('../src/services/authService')
      authService.fetchCurrentUser.mockResolvedValue({ ...ADMIN, role })
      const { routes } = await import('../src/router')
      const { installAuthGuard } = await import('../src/auth/guard')
      const router = createRouter({ history: createMemoryHistory(), routes })
      installAuthGuard(router)
      await router.push('/admin?tab=users')
      expect(router.currentRoute.value.path).toBe('/forbidden')
    }
  })
})

describe('site settings store', () => {
  it('keeps the defaults until settings are saved, then uses saved values, blanks included', async () => {
    vi.resetModules()
    const { site, applySiteSettings } = await import('../src/config/siteSettings')
    const defaults = await import('../src/config/siteConfig')
    expect(site.contact.email).toBe(defaults.CONTACT.email)

    applySiteSettings({ configured: true, contactPhone: '', contactEmail: 'help@x.org', contactAddress: 'Dhaka', facebookUrl: '', youtubeUrl: '', linkedinUrl: '', twitterUrl: '', heroVideoUrl: '', announcementText: '', announcementEnabled: true })
    expect(site.contact).toEqual({ phone: '', email: 'help@x.org', address: 'Dhaka' })
    expect(site.heroVideoUrl).toBe('')
    expect(site.announcement.enabled).toBe(false) // no text, nothing to show

    applySiteSettings({ configured: false })
    expect(site.contact.email).toBe(defaults.CONTACT.email)
  })
})
