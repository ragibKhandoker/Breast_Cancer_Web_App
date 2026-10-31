import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('../src/services/notificationService', async (importOriginal) => {
  const real = await importOriginal()
  return { ...real, listNotifications: vi.fn(), markNotificationRead: vi.fn(), markAllNotificationsRead: vi.fn() }
})

const PATIENT = { id: 'p1', fullName: 'Rahima Begum', email: 'r@example.com', role: 'PATIENT' }
const minutesAgo = (m) => new Date(Date.now() - m * 60000).toISOString()
const ITEMS = [
  { id: 'n2', type: 'APPOINTMENT_CONFIRMED', title: 'Appointment confirmed', message: 'Dr. Nasrin confirmed it.', link: '/my-appointments', read: false, createdAt: minutesAgo(5) },
  { id: 'n1', type: 'PROFILE_UPDATED', title: 'Profile updated', message: 'Your profile details were saved.', link: '/profile', read: true, createdAt: minutesAgo(60 * 26) },
]

async function mountHeader(user = PATIENT, items = ITEMS, unread = 1) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(user)
  const service = await import('../src/services/notificationService')
  service.listNotifications.mockResolvedValue({ items: structuredClone(items), unread })
  service.markNotificationRead.mockResolvedValue({})
  service.markAllNotificationsRead.mockResolvedValue()
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const { default: AppHeader } = await import('../src/components/layout/AppHeader.vue')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push('/')
  const wrapper = mount(AppHeader, { attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router, service }
}

describe('notification bell', () => {
  beforeEach(() => vi.clearAllMocks())

  it('formats times as people say them', async () => {
    const { timeAgo } = await import('../src/services/notificationService')
    const now = Date.parse('2026-10-31T12:00:00Z')
    expect(timeAgo('2026-10-31T11:59:40Z', now)).toBe('just now')
    expect(timeAgo('2026-10-31T11:55:00Z', now)).toBe('5 min ago')
    expect(timeAgo('2026-10-31T09:00:00Z', now)).toBe('3 h ago')
    expect(timeAgo('2026-10-30T10:00:00Z', now)).toBe('yesterday')
  })

  it('is only shown to signed-in users', async () => {
    const { wrapper } = await mountHeader(null)
    expect(wrapper.find('.bell').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the unread count and the latest notifications, newest first', async () => {
    const { wrapper } = await mountHeader()
    expect(wrapper.find('.bell .count').text()).toBe('1')
    expect(wrapper.find('.bell').attributes('aria-label')).toBe('Notifications, 1 unread')

    await wrapper.find('.bell').trigger('click')
    const items = wrapper.findAll('.item')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('Appointment confirmed')
    expect(items[0].text()).toContain('5 min ago')
    expect(items[0].classes()).toContain('unread')
    expect(items[1].text()).toContain('Profile updated')
    expect(items[1].text()).toContain('yesterday')
    wrapper.unmount()
  })

  it('opens a notification, marks it read and goes to its page', async () => {
    const { wrapper, router, service } = await mountHeader()
    await wrapper.find('.bell').trigger('click')
    // What the server answers once it is read (the page change makes the bell check again).
    service.listNotifications.mockResolvedValue({ items: ITEMS.map((n) => ({ ...n, read: true })), unread: 0 })
    await wrapper.find('.item').trigger('click')
    await flushPromises()
    expect(service.markNotificationRead).toHaveBeenCalledWith('n2')
    expect(router.currentRoute.value.path).toBe('/my-appointments')
    expect(wrapper.find('.bell .count').exists()).toBe(false)
    wrapper.unmount()
  })

  it('marks everything read', async () => {
    const { wrapper, service } = await mountHeader()
    await wrapper.find('.bell').trigger('click')
    await wrapper.find('.mark-all').trigger('click')
    await flushPromises()
    expect(service.markAllNotificationsRead).toHaveBeenCalled()
    expect(wrapper.findAll('.item.unread')).toHaveLength(0)
    expect(wrapper.text()).toContain('You are all caught up')
    wrapper.unmount()
  })

  it('says so when there are none', async () => {
    const { wrapper } = await mountHeader(PATIENT, [], 0)
    await wrapper.find('.bell').trigger('click')
    expect(wrapper.text()).toContain('No notifications yet')
    wrapper.unmount()
  })

  it('does not ask the server while the user must change a temporary password', async () => {
    const { wrapper, service } = await mountHeader({ ...PATIENT, mustChangePassword: true })
    expect(service.listNotifications).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
