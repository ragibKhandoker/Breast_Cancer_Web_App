import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('../src/services/notificationService', async (importOriginal) => ({
  ...(await importOriginal()),
  listNotifications: vi.fn().mockResolvedValue({ items: [], unread: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  requestPasswordHelp: vi.fn(),
  checkPasswordHelpStatus: vi.fn(),
}))
// After logging in with a temporary password the app opens the profile page.
vi.mock('../src/services/profileService', () => ({
  getProfile: vi.fn().mockResolvedValue({ id: 'p1', fullName: 'Bilkis', email: 'bilkis@gmail.com', role: 'PATIENT', mustChangePassword: true, createdAt: '2026-01-01T00:00:00Z' }),
  updateProfile: vi.fn(),
  changeEmail: vi.fn(),
  changePassword: vi.fn(),
}))
vi.mock('../src/services/messageService', () => ({
  getMyMessages: vi.fn(),
  sendMyMessage: vi.fn(),
  markMyMessagesRead: vi.fn(),
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  sendToUser: vi.fn(),
  markConversationRead: vi.fn(),
}))

const CODE = 'ABCDE-FGHJK-MNPQR-STUVW'

async function mountAt(path, user = null, setup = () => {}) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(user)
  const messages = await import('../src/services/messageService')
  setup({ authService, messages })
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push(path)
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
  await flushPromises()
  return { wrapper, router, authService, messages }
}

describe('Forgot password: approved on the Login page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('sends the request without showing any code, and remembers it on this browser', async () => {
    const { wrapper, authService } = await mountAt('/forgot-password', null, ({ authService }) =>
      authService.requestPasswordHelp.mockResolvedValue({ message: 'ok', code: CODE, reference: 'PH-7KQ2M9' }))
    await wrapper.find('#forgot-email').setValue('bilkis@gmail.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.requestPasswordHelp).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Request sent')
    expect(wrapper.text()).not.toContain(CODE)
    expect(wrapper.text()).not.toContain('PH-7KQ2M9')
    expect(wrapper.text()).toContain('on this browser')
    expect(wrapper.find('a[href="/login"]').exists()).toBe(true)
    expect(JSON.parse(localStorage.getItem('bc_password_help'))).toMatchObject({ code: CODE, email: 'bilkis@gmail.com' })
  })

  it('shows on the Login page that the request is waiting', async () => {
    localStorage.setItem('bc_password_help', JSON.stringify({ code: CODE, email: 'bilkis@gmail.com' }))
    const { wrapper, authService } = await mountAt('/login', null, ({ authService }) =>
      authService.checkPasswordHelpStatus.mockResolvedValue({ reference: 'PH-7KQ2M9', state: 'WAITING' }))
    expect(authService.checkPasswordHelpStatus).toHaveBeenCalledWith(CODE)
    expect(wrapper.find('.reset-card.waiting').text()).toContain('Waiting for an administrator')
  })

  it('shows the approved temporary password once, fills the form, and forgets it after logging in', async () => {
    localStorage.setItem('bc_password_help', JSON.stringify({ code: CODE, email: 'bilkis@gmail.com' }))
    const { wrapper, authService } = await mountAt('/login', null, ({ authService }) => {
      authService.checkPasswordHelpStatus.mockResolvedValue({ reference: 'PH-7KQ2M9', state: 'PASSWORD_READY', temporaryPassword: 'Ab3defGh7kLm' })
      authService.login.mockResolvedValue({ id: 'p1', fullName: 'Bilkis', email: 'bilkis@gmail.com', role: 'PATIENT', mustChangePassword: true })
    })
    expect(wrapper.find('.reset-card.ready code').text()).toBe('Ab3defGh7kLm')
    expect(wrapper.find('#login-email').element.value).toBe('bilkis@gmail.com')
    expect(wrapper.find('#login-password').element.value).toBe('Ab3defGh7kLm')

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.login).toHaveBeenCalledWith({ email: 'bilkis@gmail.com', password: 'Ab3defGh7kLm' })
    expect(localStorage.getItem('bc_password_help')).toBeNull()
  })

  it('stops offering a temporary password the server says was already used', async () => {
    localStorage.setItem('bc_password_help', JSON.stringify({ code: CODE, email: 'bilkis@gmail.com' }))
    sessionStorage.setItem('bc_password_help_shown', 'Ab3defGh7kLm')
    const detail = 'This temporary password has already been used and works only once. Use "Forgot password?" to ask an administrator for a new one.'
    const { wrapper } = await mountAt('/login', null, ({ authService }) =>
      authService.login.mockRejectedValue(Object.assign(new Error('401'), { response: { status: 401, data: { detail, code: 'temporary_password_used' } } })))
    expect(wrapper.find('.reset-card.ready').exists()).toBe(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe(detail)
    expect(wrapper.find('.reset-card.ready').exists()).toBe(false)
    expect(wrapper.find('.reset-card.closed').text()).toContain('works only once')
    expect(wrapper.find('.reset-card.closed a[href="/forgot-password"]').exists()).toBe(true)
    expect(localStorage.getItem('bc_password_help')).toBeNull()
    expect(sessionStorage.getItem('bc_password_help_shown')).toBeNull()
  })

  it('keeps the shown password across a reload of the Login page', async () => {
    localStorage.setItem('bc_password_help', JSON.stringify({ code: CODE, email: 'bilkis@gmail.com' }))
    sessionStorage.setItem('bc_password_help_shown', 'Ab3defGh7kLm')
    const { wrapper, authService } = await mountAt('/login', null)
    expect(authService.checkPasswordHelpStatus).not.toHaveBeenCalled()
    expect(wrapper.find('.reset-card.ready code').text()).toBe('Ab3defGh7kLm')
  })

  it('says when an old request is closed and forgets it', async () => {
    localStorage.setItem('bc_password_help', JSON.stringify({ code: CODE, email: 'bilkis@gmail.com' }))
    const { wrapper } = await mountAt('/login', null, ({ authService }) =>
      authService.checkPasswordHelpStatus.mockResolvedValue({ reference: 'PH-7KQ2M9', state: 'CLOSED' }))
    expect(wrapper.find('.reset-card.closed').text()).toContain('closed')
    expect(localStorage.getItem('bc_password_help')).toBeNull()
  })

  it('sends old status-page links to Login', async () => {
    const { router } = await mountAt('/forgot-password/status')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})

describe('Messages with the administrators', () => {
  beforeEach(() => vi.clearAllMocks())

  const PATIENT = { id: 'p1', fullName: 'Bilkis Akter', email: 'bilkis@gmail.com', role: 'PATIENT' }
  const THREAD = [
    { id: 'm1', fromAdmin: false, senderName: 'Bilkis Akter', body: 'Did you reset my password?', createdAt: new Date(Date.now() - 600000).toISOString() },
    { id: 'm2', fromAdmin: true, senderName: 'BreastCare team', body: 'Yes, check the status page.', createdAt: new Date().toISOString() },
  ]

  it('is for patients and doctors only', async () => {
    const { router } = await mountAt('/messages', { id: 'a1', fullName: 'Admin', email: 'a@x.com', role: 'ADMIN' })
    expect(router.currentRoute.value.path).toBe('/forbidden')
  })

  it('shows the conversation, marks it read and sends a message', async () => {
    const { wrapper, messages } = await mountAt('/messages', PATIENT, ({ messages }) => {
      messages.getMyMessages.mockResolvedValue({ messages: structuredClone(THREAD), unread: 1 })
      messages.markMyMessagesRead.mockResolvedValue()
      messages.sendMyMessage.mockResolvedValue({ id: 'm3', fromAdmin: false, senderName: 'Bilkis Akter', body: 'Thank you!', createdAt: new Date().toISOString() })
    })
    const rows = wrapper.findAll('.bubble-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).toContain('mine')
    expect(rows[1].classes()).not.toContain('mine')
    expect(rows[1].text()).toContain('BreastCare team')
    expect(messages.markMyMessagesRead).toHaveBeenCalled()

    await wrapper.find('.composer textarea').setValue('Thank you!')
    await wrapper.find('.composer').trigger('submit')
    await flushPromises()
    expect(messages.sendMyMessage).toHaveBeenCalledWith('Thank you!')
    expect(wrapper.findAll('.bubble-row')).toHaveLength(3)
    expect(wrapper.find('.composer textarea').element.value).toBe('')
  })

  it('keeps the text when sending fails', async () => {
    const { wrapper } = await mountAt('/messages', PATIENT, ({ messages }) => {
      messages.getMyMessages.mockResolvedValue({ messages: [], unread: 0 })
      messages.sendMyMessage.mockRejectedValue(Object.assign(new Error('x'), { response: { status: 500, data: { detail: 'Server error' } } }))
    })
    await wrapper.find('.composer textarea').setValue('Hello')
    await wrapper.find('.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.composer textarea').element.value).toBe('Hello')
    expect(wrapper.text()).toContain('Server error')
  })
})
