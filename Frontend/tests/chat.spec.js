import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { conversationToSend, chatError, MAX_MESSAGES, MAX_TOTAL_CHARS } from '../src/services/chatService'

vi.mock('../src/services/authService', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../src/services/chatService', async (importOriginal) => ({ ...(await importOriginal()), sendChat: vi.fn() }))

const { sendChat } = await import('../src/services/chatService')
const authService = await import('../src/services/authService')
const store = await import('../src/auth/authStore')
const { default: ChatWidget } = await import('../src/components/chat/ChatWidget.vue')

const turns = (n, size = 10) =>
  Array.from({ length: n }, (_, i) => ({ role: i % 2 === 0 ? 'user' : 'assistant', content: `${i}`.padEnd(size, '.') }))

describe('conversationToSend', () => {
  it('sends everything while it fits', () => {
    expect(conversationToSend(turns(3))).toEqual(turns(3))
  })

  it('drops the oldest pairs to stay within the limits, always starting with the user', () => {
    const sent = conversationToSend(turns(31))
    expect(sent.length).toBeLessThan(MAX_MESSAGES)
    expect(sent[0].role).toBe('user')
    expect(sent.at(-1).content).toBe(turns(31).at(-1).content)

    const long = conversationToSend(turns(7, 6000))
    expect(long.reduce((sum, m) => sum + m.content.length, 0)).toBeLessThanOrEqual(MAX_TOTAL_CHARS)
    expect(long[0].role).toBe('user')
  })

  it('sends only role and content', () => {
    expect(conversationToSend([{ role: 'user', content: 'Hi', offTopic: true }])).toEqual([{ role: 'user', content: 'Hi' }])
  })
})

describe('chatError', () => {
  it('shows the server message for limits and outages, and a plain one otherwise', () => {
    const err = (status, detail) => Object.assign(new Error(), { response: { status, data: { detail } } })
    expect(chatError(err(429, 'Please wait a few minutes'))).toBe('Please wait a few minutes')
    expect(chatError(err(503, 'The chatbot is not set up on this server yet.'))).toContain('not set up')
    expect(chatError(err(500, 'stack trace'))).toBe('Something went wrong. Please try again.')
    expect(chatError(new Error('Network Error'))).toContain('Could not reach')
  })
})

describe('ChatWidget', () => {
  const mountWidget = () => mount(ChatWidget, { global: { stubs: { RouterLink: RouterLinkStub } } })

  beforeEach(() => {
    sendChat.mockReset()
    store.endSession()
  })

  async function signIn() {
    authService.login.mockResolvedValue({ id: 'u1', fullName: 'Ayesha', email: 'a@example.com', role: 'PATIENT' })
    await store.signIn({ email: 'a@example.com', password: 'x' })
  }

  it('asks signed-out visitors to sign in', async () => {
    const wrapper = mountWidget()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.find('.launcher').trigger('click')
    expect(wrapper.find('.launcher').attributes('aria-expanded')).toBe('true')
    expect(wrapper.text()).toContain('Please sign in')
    expect(wrapper.find('textarea').exists()).toBe(false)
  })

  it('sends the question with the conversation and shows the reply', async () => {
    await signIn()
    const wrapper = mountWidget()
    await wrapper.find('.launcher').trigger('click')
    sendChat.mockResolvedValueOnce({ reply: 'A new lump or skin change.', onTopic: true })
    await wrapper.find('textarea').setValue('What are early signs?')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(sendChat).toHaveBeenCalledWith([{ role: 'user', content: 'What are early signs?' }])
    const bubbles = wrapper.findAll('.bubble')
    expect(bubbles.map((b) => b.text())).toEqual(['What are early signs?', 'A new lump or skin change.'])
    expect(wrapper.find('textarea').element.value).toBe('')

    // A suggestion sends straight away; an off-topic reply is marked.
    sendChat.mockResolvedValueOnce({ reply: 'Sorry, I can only help with breast health.', onTopic: false })
    await wrapper.find('.bubble.assistant').trigger('click') // not a button: nothing happens
    await wrapper.find('textarea').setValue('Tell me a joke')
    await wrapper.find('textarea').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(sendChat.mock.calls[1][0]).toHaveLength(3)
    expect(wrapper.find('.bubble.off-topic').text()).toContain('only help with breast health')

    await wrapper.find('.text-btn').trigger('click')
    expect(wrapper.findAll('.bubble')).toHaveLength(0)
    expect(wrapper.findAll('.suggestions button')).toHaveLength(4)
  })

  it('keeps the question for a retry when sending fails', async () => {
    await signIn()
    const wrapper = mountWidget()
    await wrapper.find('.launcher').trigger('click')
    sendChat.mockRejectedValueOnce(Object.assign(new Error(), { response: { status: 429, data: { detail: 'Please wait a few minutes.' } } }))
    await wrapper.findAll('.suggestions button')[0].trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe('Please wait a few minutes.')
    expect(wrapper.findAll('.bubble')).toHaveLength(0)
    expect(wrapper.find('textarea').element.value).toBe('What are the early signs of breast cancer?')
  })

  it('forgets the conversation when the user signs out', async () => {
    await signIn()
    const wrapper = mountWidget()
    await wrapper.find('.launcher').trigger('click')
    sendChat.mockResolvedValueOnce({ reply: 'Every 1 to 2 years.', onTopic: true })
    await wrapper.findAll('.suggestions button')[2].trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.bubble')).toHaveLength(2)

    store.endSession()
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await signIn()
    await wrapper.find('.launcher').trigger('click')
    expect(wrapper.findAll('.bubble')).toHaveLength(0)
  })
})
