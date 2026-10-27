import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'

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
