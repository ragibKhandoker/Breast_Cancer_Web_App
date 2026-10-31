import { describe, it, expect, vi, beforeEach } from 'vitest'

// Each test loads a fresh module (it keeps the slot in memory too) with clean storage.
async function fresh() {
  vi.resetModules()
  return import('../src/auth/tabSession')
}

describe('per-tab sign-in slots', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  it('gives a tab one slot and keeps it', async () => {
    const tab = await fresh()
    const slot = tab.currentSlot()
    expect(slot).toMatch(/^[a-z0-9]{8}$/)
    expect(tab.currentSlot()).toBe(slot)
    expect(sessionStorage.getItem('bc_auth_slot')).toBe(slot)
  })

  it('gives a new tab its own slot and asks it to copy the latest tab’s login', async () => {
    const first = await fresh()
    const firstSlot = first.currentSlot()
    first.markSlotAsLatest()
    sessionStorage.clear() // a new tab has its own, empty sessionStorage
    const second = await fresh()
    expect(await second.prepareTab()).toBe(firstSlot)
    expect(second.currentSlot()).not.toBe(firstSlot)
  })

  it('does not copy anything on a reload or when no tab is signed in', async () => {
    const tab = await fresh()
    expect(await tab.prepareTab()).toBeNull() // nothing signed in anywhere
    const reloaded = await fresh() // same sessionStorage: a reload
    expect(await reloaded.prepareTab()).toBeNull()
  })

  it('gives "use another account" a fresh slot without touching the others', async () => {
    const tab = await fresh()
    const before = tab.currentSlot()
    tab.markSlotAsLatest()
    const after = tab.startNewSlot()
    expect(after).not.toBe(before)
    expect(tab.currentSlot()).toBe(after)
    expect(localStorage.getItem('bc_auth_last_slot')).toBe(before)
  })

  it('stops new tabs copying a slot that signed out', async () => {
    const tab = await fresh()
    tab.currentSlot()
    tab.markSlotAsLatest()
    tab.forgetSlotIfLatest()
    expect(localStorage.getItem('bc_auth_last_slot')).toBeNull()
  })

  it('names the slot on plain links', async () => {
    const tab = await fresh()
    const slot = tab.currentSlot()
    expect(tab.withSlot('/api/predictions/x/image')).toBe(`/api/predictions/x/image?authSlot=${slot}`)
    expect(tab.withSlot('/a?b=1')).toBe(`/a?b=1&authSlot=${slot}`)
  })

  it('sends the slot with every API call', async () => {
    const tab = await fresh()
    const { http } = await import('../src/services/http')
    const slot = tab.currentSlot()
    let sent
    http.defaults.adapter = async (config) => {
      sent = config.headers['X-Auth-Slot']
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }
    await http.get('/api/auth/me')
    expect(sent).toBe(slot)
  })

  it('a new tab starts signed in through its own copy of the login', async () => {
    localStorage.setItem('bc_auth_last_slot', 'admintab')
    vi.resetModules()
    vi.doMock('../src/services/authService', () => ({
      fetchCurrentUser: vi.fn().mockResolvedValue(null),
      forkLogin: vi.fn().mockResolvedValue({ id: 'a1', role: 'ADMIN', fullName: 'Admin', email: 'a@x.com' }),
      login: vi.fn(),
      logout: vi.fn(),
    }))
    const store = await import('../src/auth/authStore')
    const authService = await import('../src/services/authService')
    const tab = await import('../src/auth/tabSession')
    const user = await store.initAuth()
    expect(authService.forkLogin).toHaveBeenCalledWith('admintab')
    expect(user.role).toBe('ADMIN')
    expect(tab.currentSlot()).not.toBe('admintab')
    vi.doUnmock('../src/services/authService')
  })

  it('a new tab with nothing to copy starts signed out', async () => {
    localStorage.setItem('bc_auth_last_slot', 'gonetab1')
    vi.resetModules()
    vi.doMock('../src/services/authService', () => ({
      fetchCurrentUser: vi.fn().mockResolvedValue(null),
      forkLogin: vi.fn().mockRejectedValue(new Error('401')),
      login: vi.fn(),
      logout: vi.fn(),
    }))
    const store = await import('../src/auth/authStore')
    expect(await store.initAuth()).toBeNull()
    vi.doUnmock('../src/services/authService')
  })

  it('signs this tab out locally for "use another account"', async () => {
    vi.resetModules()
    vi.doMock('../src/services/authService', () => ({ fetchCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }))
    const tab = await import('../src/auth/tabSession')
    const store = await import('../src/auth/authStore')
    const authService = await import('../src/services/authService')
    authService.login.mockResolvedValue({ id: 'a1', role: 'ADMIN', fullName: 'Admin', email: 'a@x.com' })
    await store.signIn({ email: 'a@x.com', password: 'x' })
    const adminSlot = tab.currentSlot()
    expect(localStorage.getItem('bc_auth_last_slot')).toBe(adminSlot)

    store.useAnotherAccount()
    expect(store.authState.user).toBeNull()
    expect(tab.currentSlot()).not.toBe(adminSlot)
    expect(authService.logout).not.toHaveBeenCalled() // the admin stays signed in in their own tab
    vi.doUnmock('../src/services/authService')
  })
})
