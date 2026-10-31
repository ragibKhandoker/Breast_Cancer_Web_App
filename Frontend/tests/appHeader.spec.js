import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

// The bell in the header loads notifications; no server here.
vi.mock('../src/services/notificationService', async (importOriginal) => ({
  ...(await importOriginal()),
  listNotifications: vi.fn().mockResolvedValue({ items: [], unread: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
// The store keeps module state, so each test loads a fresh copy with its own signed-in user.
vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

async function mountHeader(user) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(user)
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const { default: AppHeader } = await import('../src/components/layout/AppHeader.vue')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push('/')
  const wrapper = mount(AppHeader, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

const navLink = (wrapper, label) => wrapper.findAll('.nav-link').find((link) => link.text() === label)

describe('header navigation', () => {
  beforeEach(() => vi.clearAllMocks())

  const visitor = null
  const patient = { id: 'p1', fullName: 'Pat', email: 'p@example.com', role: 'PATIENT' }
  const doctor = { id: 'd1', fullName: 'Doc', email: 'd@example.com', role: 'DOCTOR' }
  const admin = { id: 'a1', fullName: 'Ada', email: 'a@example.com', role: 'ADMIN', owner: true }

  it.each([
    ['a visitor', visitor],
    ['a patient', patient],
    ['a doctor', doctor],
    ['an admin', admin],
  ])('shows Prediction and Find a Surgeon to %s', async (_, user) => {
    const wrapper = await mountHeader(user)
    expect(navLink(wrapper, 'Prediction')?.attributes('href')).toBe('/prediction')
    expect(navLink(wrapper, 'Find a Surgeon')?.attributes('href')).toBe('/breast-surgeons')
  })

  it('tells doctors and admins that Prediction is for patient accounts', async () => {
    expect(navLink(await mountHeader(patient), 'Prediction').attributes('title')).toBeUndefined()
    expect(navLink(await mountHeader(doctor), 'Prediction').attributes('title')).toBe('Prediction is for patient accounts')
  })
})
