import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

let store
let authService

beforeEach(async () => {
  vi.resetModules()
  vi.clearAllMocks()
  authService = await import('../src/services/authService')
  store = await import('../src/auth/authStore')
})

const user = { id: 'u1', fullName: 'Pat', email: 'p@example.com', role: 'PATIENT' }

describe('auth store', () => {
  it('signs in and exposes the profile', async () => {
    authService.login.mockResolvedValue(user)
    await store.signIn({ email: 'p@example.com', password: 'Secret123' })
    expect(store.authState.user).toEqual(user)
    expect(store.hasRole('PATIENT')).toBe(true)
    expect(store.hasRole('ADMIN')).toBe(false)
  })

  it('treats an unreachable backend as signed out', async () => {
    authService.fetchCurrentUser.mockRejectedValue(new Error('Network Error'))
    expect(await store.initAuth()).toBeNull()
    expect(store.authState.ready).toBe(true)
  })

  it('clears the user and cached records on logout, even if the server call fails', async () => {
    authService.login.mockResolvedValue(user)
    authService.logout.mockRejectedValue(new Error('offline'))
    await store.signIn({ email: 'p@example.com', password: 'Secret123' })
    const clearRecords = vi.fn()
    store.onSessionEnd(clearRecords)

    await expect(store.signOut()).rejects.toThrow('offline')

    expect(authService.logout).toHaveBeenCalledOnce()
    expect(store.authState.user).toBeNull()
    expect(clearRecords).toHaveBeenCalledOnce()
  })

  it('does not expose a writable state', async () => {
    authService.login.mockResolvedValue(user)
    await store.signIn({ email: 'p@example.com', password: 'Secret123' })
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    store.authState.user = { ...user, role: 'ADMIN' }
    expect(store.authState.user.role).toBe('PATIENT')
    warn.mockRestore()
  })
})
