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

describe('Forgot password: request code and status page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('gives the visitor a request code and reference, and remembers it on this browser', async () => {
    const { wrapper, authService } = await mountAt('/forgot-password', null, ({ authService }) =>
      authService.requestPasswordHelp.mockResolvedValue({ message: 'ok', code: CODE, reference: 'PH-7KQ2M9' }))
    await wrapper.find('#forgot-email').setValue('bilkis@gmail.com')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.requestPasswordHelp).toHaveBeenCalled()
    expect(wrapper.find('.code').text()).toBe(CODE)
    expect(wrapper.text()).toContain('PH-7KQ2M9')
    expect(wrapper.find('a[href="/forgot-password/status"]').exists()).toBe(true)
    expect(JSON.parse(localStorage.getItem('bc_password_help')).code).toBe(CODE)
  })

  it('opens the remembered code, shows the reply, and keeps the one-time password on screen', async () => {
    localStorage.setItem('bc_password_help', JSON.stringify({ code: CODE, reference: 'PH-7KQ2M9' }))
    const { wrapper, authService } = await mountAt('/forgot-password/status', null, ({ authService }) =>
      authService.checkPasswordHelpStatus.mockResolvedValue({ reference: 'PH-7KQ2M9', state: 'REPLIED', reply: 'We will call you.', repliedAt: new Date().toISOString() }))
    expect(authService.checkPasswordHelpStatus).toHaveBeenCalledWith(CODE)
    expect(wrapper.text()).toContain('The administrator replied')
    expect(wrapper.find('.reply').text()).toContain('We will call you.')
    expect(wrapper.find('.password').exists()).toBe(false)

    authService.checkPasswordHelpStatus.mockResolvedValueOnce({
      reference: 'PH-7KQ2M9', state: 'PASSWORD_READY', reply: 'Confirmed by phone.', repliedAt: new Date().toISOString(),
      temporaryPassword: 'Ab3defGh7kLm', passwordExpiresAt: new Date(Date.now() + 86400000).toISOString(),
    })
    await wrapper.find('.code-form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.password code').text()).toBe('Ab3defGh7kLm')
    expect(wrapper.text()).toContain('shown only once')
    expect(wrapper.find('.password a[href="/login"]').exists()).toBe(true)

    // A later check says it was shown, but the page keeps it until the visitor leaves.
    authService.checkPasswordHelpStatus.mockResolvedValueOnce({ reference: 'PH-7KQ2M9', state: 'PASSWORD_SHOWN' })
    await wrapper.find('.code-form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('.password code').text()).toBe('Ab3defGh7kLm')
  })

  it('asks for a code when none is entered', async () => {
    const { wrapper, authService } = await mountAt('/forgot-password/status')
    await wrapper.find('.code-form').trigger('submit')
    expect(authService.checkPasswordHelpStatus).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Enter the request code you were given.')
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
