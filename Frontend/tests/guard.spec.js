import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { flushPromises } from '@vue/test-utils'

// The store and guard keep module state, so each test loads fresh copies with its own signed-in user.
vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

async function setup(user) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(user)
  const { routes } = await import('../src/router')
  const { installAuthGuard, handleSessionExpired } = await import('../src/auth/guard')
  const store = await import('../src/auth/authStore')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  return { router, store, handleSessionExpired, authService }
}

const patient = { id: 'p1', fullName: 'Pat', email: 'p@example.com', role: 'PATIENT' }
const doctor = { id: 'd1', fullName: 'Doc', email: 'd@example.com', role: 'DOCTOR' }
const admin = { id: 'a1', fullName: 'Ada', email: 'a@example.com', role: 'ADMIN' }

describe('route guard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('keeps a user on a temporary password on the Change password page', async () => {
    const { router } = await setup({ ...patient, mustChangePassword: true })
    for (const path of ['/dashboard', '/prediction', '/', '/breast-surgeons']) {
      await router.push(path)
      expect(router.currentRoute.value.path).toBe('/profile')
      expect(router.currentRoute.value.hash).toBe('#change-password')
    }
    const asDoctor = await setup({ ...doctor, mustChangePassword: true })
    await asDoctor.router.push('/doctor')
    expect(asDoctor.router.currentRoute.value.path).toBe('/account-settings')
    await asDoctor.router.push('/profile')
    expect(asDoctor.router.currentRoute.value.path).toBe('/account-settings')
  })

  it('lets the user go once the password is changed', async () => {
    const { router, store } = await setup({ ...patient, mustChangePassword: true })
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/profile')
    store.updateCurrentUser({ ...patient, mustChangePassword: false })
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('sends the user to change their password when the server asks for it', async () => {
    const { router, store } = await setup(patient)
    await router.push('/dashboard')
    const { handlePasswordChangeRequired } = await import('../src/auth/guard')
    handlePasswordChangeRequired(router)
    await flushPromises()
    expect(store.authState.user.mustChangePassword).toBe(true)
    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('lets visitors open public pages', async () => {
    const { router } = await setup(null)
    await router.push('/faq')
    expect(router.currentRoute.value.path).toBe('/faq')
  })

  it('sends visitors to login, remembering where they were going', async () => {
    const { router } = await setup(null)
    for (const path of ['/prediction', '/history', '/dashboard', '/doctor', '/admin', '/reports/x1']) {
      await router.push(path)
      expect(router.currentRoute.value.path).toBe('/login')
      expect(router.currentRoute.value.query.redirect).toBe(path)
    }
  })

  it('lets each role into its own pages only', async () => {
    const cases = [
      [patient, ['/dashboard', '/prediction', '/history', '/reports/x1'], ['/doctor', '/admin']],
      [doctor, ['/doctor', '/reports/x1'], ['/dashboard', '/prediction', '/history', '/admin']],
      [admin, ['/admin'], ['/dashboard', '/prediction', '/doctor', '/reports/x1']],
    ]
    for (const [user, allowed, denied] of cases) {
      const { router } = await setup(user)
      for (const path of allowed) {
        await router.push(path)
        expect(router.currentRoute.value.path, `${user.role} -> ${path}`).toBe(path)
      }
      for (const path of denied) {
        await router.push(path)
        expect(router.currentRoute.value.path, `${user.role} -> ${path}`).toBe('/forbidden')
      }
    }
  })

  it('keeps signed-in users off the login and register pages', async () => {
    const { router } = await setup(doctor)
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/doctor')
    await router.push('/register')
    expect(router.currentRoute.value.path).toBe('/doctor')
  })

  it('asks for the current user only once', async () => {
    const { router, authService } = await setup(patient)
    await router.push('/dashboard')
    await router.push('/history')
    expect(authService.fetchCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('returns to login when the session expires on a protected page', async () => {
    const { router, store, handleSessionExpired } = await setup(patient)
    await router.push('/history')
    const cleared = vi.fn()
    store.onSessionEnd(cleared)

    handleSessionExpired(router)
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))

    expect(router.currentRoute.value.query).toMatchObject({ redirect: '/history', expired: '1' })
    expect(store.authState.user).toBeNull()
    expect(cleared).toHaveBeenCalledOnce()
  })
})
