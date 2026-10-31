import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

// The bell in the header and the admin account page load notifications; no server here.
vi.mock('../src/services/notificationService', async (importOriginal) => ({
  ...(await importOriginal()),
  listNotifications: vi.fn().mockResolvedValue({ items: [], unread: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
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
  getUserDetail: vi.fn(),
  setVerification: vi.fn(),
  resetPassword: vi.fn(),
  listPasswordRequests: vi.fn(),
  setPasswordRequestStatus: vi.fn(),
  sendPasswordToRequest: vi.fn(),
  getAdminAccount: vi.fn(),
  listSurgeonProfiles: vi.fn().mockResolvedValue([]),
  reviewSurgeon: vi.fn(),
  surgeonDocumentUrl: vi.fn(() => '#'),
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

describe('Admin portal: verification and password help', () => {
  beforeEach(() => vi.clearAllMocks())

  const PATIENTS = [
    { id: 'p1', fullName: 'Rahima Begum', email: 'rahima@example.com', role: 'PATIENT', createdAt: '2026-10-02T00:00:00Z', disabled: false, lastLoginAt: null, verificationStatus: 'PENDING' },
    { id: 'p2', fullName: 'Salma Akter', email: 'salma@example.com', role: 'PATIENT', createdAt: '2026-10-03T00:00:00Z', disabled: false, lastLoginAt: null, verificationStatus: 'VERIFIED' },
    { id: 'd1', fullName: 'Dr. Karim', email: 'karim@clinic.org', role: 'DOCTOR', createdAt: '2026-10-03T00:00:00Z', disabled: false, lastLoginAt: null, verificationStatus: null },
  ]
  const DETAIL = {
    ...PATIENTS[0], updatedAt: '2026-10-02T00:00:00Z', mustChangePassword: false, phone: '+8801712345678', dateOfBirth: '1990-05-01',
    gender: 'FEMALE', address: '', specialization: null, hospital: null, verificationNote: null, verifiedBy: null, verifiedAt: null, predictionCount: 3,
  }

  const REQUEST = { id: 'r1', userId: 'p1', email: 'rahima@example.com', fullName: 'Rahima Begum', message: 'Locked out', status: 'OPEN', createdAt: new Date().toISOString(), handledAt: null, handledBy: null }

  async function mountPortal(tab, users = PATIENTS, requests = [REQUEST]) {
    vi.resetModules()
    const authService = await import('../src/services/authService')
    authService.fetchCurrentUser.mockResolvedValue(ADMIN)
    const admin = await import('../src/services/adminService')
    admin.getOverview.mockResolvedValue({ ...structuredClone(OVERVIEW), support: { pendingVerification: 1, verified: 1, rejected: 0, openPasswordRequests: 2 } })
    admin.listUsers.mockResolvedValue(structuredClone(users))
    admin.listMessages.mockResolvedValue([])
    admin.listPasswordRequests.mockResolvedValue(structuredClone(requests))
    const { routes } = await import('../src/router')
    const { installAuthGuard } = await import('../src/auth/guard')
    const router = createRouter({ history: createMemoryHistory(), routes })
    installAuthGuard(router)
    await router.push(`/admin?tab=${tab}`)
    const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
    await flushPromises()
    return { wrapper, admin }
  }

  it('shows waiting counts in the sidebar and on the overview', async () => {
    const { wrapper } = await mountPortal('overview')
    expect(wrapper.find('#tab-verification .badge-count').text()).toBe('1')
    expect(wrapper.find('#tab-passwords .badge-count').text()).toBe('2')
    expect(wrapper.text()).toContain('Needs your attention')
  })

  it('lists pending patients, shows details, needs a note to reject, and verifies', async () => {
    const { wrapper, admin } = await mountPortal('verification')
    // Pending patients by default; doctors have their own queue.
    expect(wrapper.findAll('.person')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('Dr. Karim')

    admin.getUserDetail.mockResolvedValue(structuredClone(DETAIL))
    await wrapper.find('.person').trigger('click')
    await flushPromises()
    expect(admin.getUserDetail).toHaveBeenCalledWith('p1')
    expect(wrapper.text()).toContain('+8801712345678')
    expect(wrapper.text()).toContain('Missing: address')

    await wrapper.find('.reject').trigger('click')
    expect(wrapper.text()).toContain('Tell the patient what needs to be corrected.')
    expect(admin.setVerification).not.toHaveBeenCalled()

    admin.setVerification.mockResolvedValue({ ...DETAIL, verificationStatus: 'VERIFIED', verifiedBy: 'admin@example.com', verifiedAt: new Date().toISOString() })
    await wrapper.find('.approve').trigger('click')
    await flushPromises()
    expect(admin.setVerification).toHaveBeenCalledWith('p1', 'VERIFIED', '')
    expect(wrapper.text()).toContain('Patient verified.')
    expect(wrapper.find('.chip.on').text()).toContain('0') // nobody left pending
  })

  it('verifies a self-registered doctor from the Doctors queue', async () => {
    const pendingDoctor = { id: 'd2', fullName: 'Dr. Nasrin Akter', email: 'nasrin@example.com', role: 'DOCTOR', createdAt: '2026-10-30T00:00:00Z', disabled: false, lastLoginAt: null, verificationStatus: 'PENDING' }
    const { wrapper, admin } = await mountPortal('verification', [...PATIENTS, pendingDoctor])
    expect(wrapper.find('.queue-tab.on').text()).toContain('Patients')
    const doctorsTab = wrapper.findAll('.queue-tab').find((t) => t.text().startsWith('Doctors'))
    expect(doctorsTab.find('.n').text()).toBe('1') // one doctor waiting

    await doctorsTab.trigger('click')
    // Pending doctors only; the admin-made Dr. Karim is already verified.
    expect(wrapper.findAll('.person')).toHaveLength(1)
    expect(wrapper.find('.person').text()).toContain('Dr. Nasrin Akter')

    admin.getUserDetail.mockResolvedValue({ ...pendingDoctor, updatedAt: pendingDoctor.createdAt, mustChangePassword: false, phone: '+8801712345678',
      dateOfBirth: null, gender: null, address: null, specialization: 'Breast Surgery', hospital: 'Dhaka Breast Centre',
      verificationNote: null, verifiedBy: null, verifiedAt: null, predictionCount: 0 })
    await wrapper.find('.person').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Dhaka Breast Centre')
    expect(wrapper.find('label[for="verify-note"]').text()).toContain('Note to the doctor')

    await wrapper.find('.reject').trigger('click')
    expect(wrapper.text()).toContain('Tell the doctor what needs to be corrected.')
    admin.setVerification.mockResolvedValue({ ...pendingDoctor, verificationStatus: 'VERIFIED' })
    await wrapper.find('.approve').trigger('click')
    await flushPromises()
    expect(admin.setVerification).toHaveBeenCalledWith('d2', 'VERIFIED', '')
    expect(wrapper.text()).toContain('Doctor verified. Patients can now share reports with them.')
  })

  it('resets a password from a request and shows the temporary password once', async () => {
    const { wrapper, admin } = await mountPortal('passwords')
    expect(wrapper.text()).toContain('Locked out')
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    admin.resetPassword.mockResolvedValue({ user: { ...PATIENTS[0], mustChangePassword: true }, temporaryPassword: 'Ab3defGh7kLm' })
    await wrapper.findAll('.request button').find((b) => b.text() === 'Reset password').trigger('click')
    await flushPromises()
    expect(admin.resetPassword).toHaveBeenCalledWith('p1')
    expect(wrapper.find('.temp code').text()).toBe('Ab3defGh7kLm')
    expect(wrapper.find('.request .pill').text()).toBe('Resolved')
  })

  it('approves a request with one button, after a confirmation, without showing the password', async () => {
    const listed = [{ ...REQUEST, reference: 'PH-7KQ2M9', hasStatusPage: true }]
    const { wrapper: w, admin: a } = await mountPortal('passwords', PATIENTS, listed)
    const approve = () => w.findAll('.request button').find((b) => b.text().includes('Approve & send temporary password'))
    expect(approve().exists()).toBe(true)

    const ask = vi.spyOn(window, 'confirm').mockReturnValueOnce(false)
    await approve().trigger('click')
    expect(ask).toHaveBeenCalled()
    expect(a.sendPasswordToRequest).not.toHaveBeenCalled()

    ask.mockReturnValueOnce(true)
    a.sendPasswordToRequest.mockResolvedValue({ ...listed[0], status: 'RESOLVED', passwordSent: true, passwordShownAt: null })
    await approve().trigger('click')
    await flushPromises()
    expect(a.sendPasswordToRequest).toHaveBeenCalledWith('r1', { message: '', identityConfirmed: true })
    expect(w.text()).toContain('appears when the user opens the Login page')
    expect(w.find('.temp').exists()).toBe(false)
  })
})

describe('Admin account (site owner only)', () => {
  beforeEach(() => vi.clearAllMocks())

  const OWNER = { id: 'o1', fullName: 'Khandoker Md. Ragib Ahsan', email: 'ragibahsan999@admin.com', role: 'ADMIN', owner: true }
  const ACCOUNT = {
    profile: { ...OWNER, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z', phone: null, dateOfBirth: null, gender: null, address: null, specialization: null, hospital: null, verificationStatus: null, mustChangePassword: false },
    lastLoginAt: '2026-10-30T10:00:00Z',
    passwordChangedAt: null,
    activity: { patientsVerified: 7, patientsRejected: 2, passwordRequestsResolved: 3, passwordRequestsDismissed: 1 },
    team: { administrators: 2, doctors: 4, patients: 30, disabled: 1 },
  }

  async function mountAs(user, path) {
    vi.resetModules()
    const authService = await import('../src/services/authService')
    authService.fetchCurrentUser.mockResolvedValue(user)
    const admin = await import('../src/services/adminService')
    admin.getAdminAccount.mockResolvedValue(structuredClone(ACCOUNT))
    admin.getOverview.mockResolvedValue(structuredClone(OVERVIEW))
    admin.listMessages.mockResolvedValue([])
    admin.listUsers.mockResolvedValue([{ ...USERS[0], id: 'o1', fullName: OWNER.fullName, email: OWNER.email, owner: true }, ...structuredClone(USERS).slice(1)])
    const { routes } = await import('../src/router')
    const { installAuthGuard } = await import('../src/auth/guard')
    const router = createRouter({ history: createMemoryHistory(), routes })
    installAuthGuard(router)
    await router.push(path)
    const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
    await flushPromises()
    return { wrapper, router, admin }
  }

  it('shows the owner their private account page with their activity', async () => {
    const { wrapper, admin } = await mountAs(OWNER, '/admin-account')
    expect(admin.getAdminAccount).toHaveBeenCalled()
    expect(wrapper.find('h1').text()).toBe('Khandoker Md. Ragib Ahsan')
    expect(wrapper.text()).toContain('Site owner')
    expect(wrapper.findAll('.stat').map((t) => t.text())[0]).toContain('7')
    expect(wrapper.find('#acct-email').attributes('readonly')).toBeDefined()
  })

  it('refuses every other administrator without asking the server', async () => {
    const { wrapper, admin } = await mountAs(ADMIN, '/admin-account')
    expect(admin.getAdminAccount).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Owner only')
  })

  it('shows the owner as read-only in the users list for other administrators', async () => {
    const { wrapper } = await mountAs({ ...ADMIN, id: 'a2' }, '/admin?tab=users')
    const ownerRow = wrapper.findAll('tbody tr')[0]
    expect(ownerRow.text()).toContain('owner')
    expect(ownerRow.find('select').exists()).toBe(false)
    expect(ownerRow.find('.link-btn').exists()).toBe(false)
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
