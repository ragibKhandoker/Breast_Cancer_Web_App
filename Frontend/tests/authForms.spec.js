import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
}))

// The dashboards a successful login lands on load data; answer with empty lists instead of the network.
vi.mock('../src/services/predictionService', () => ({
  listPredictions: vi.fn().mockResolvedValue([]),
  listSharedReports: vi.fn().mockResolvedValue([]),
  imageUrl: (id) => `/api/predictions/${id}/image`,
  errorMessage: (err) => err.message,
}))
vi.mock('../src/services/adminService', () => ({
  listUsers: vi.fn().mockResolvedValue([]),
}))

const noReveal = { mounted() {} }

async function mountAt(path) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(null)
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push(path)
  const App = { template: '<RouterView />' }
  const wrapper = mount(App, { global: { plugins: [router], directives: { reveal: noReveal } } })
  await flushPromises()
  return { wrapper, router, authService }
}

function httpError(status, data) {
  return Object.assign(new Error(`HTTP ${status}`), { response: { status, data } })
}

describe('Login page', () => {
  beforeEach(() => vi.clearAllMocks())

  it('validates before calling the server', async () => {
    const { wrapper, authService } = await mountAt('/login')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Email is required.')
    expect(wrapper.text()).toContain('Password is required.')
    expect(authService.login).not.toHaveBeenCalled()
  })

  it('toggles password visibility', async () => {
    const { wrapper } = await mountAt('/login')
    const input = wrapper.find('#login-password')
    expect(input.attributes('type')).toBe('password')
    await wrapper.find('button.toggle').trigger('click')
    expect(input.attributes('type')).toBe('text')
  })

  it('shows one message for invalid credentials and clears the password', async () => {
    const { wrapper, authService } = await mountAt('/login')
    authService.login.mockRejectedValue(httpError(401, { detail: 'Invalid email or password.' }))
    await wrapper.find('#login-email').setValue('pat@example.com')
    await wrapper.find('#login-password').setValue('Wrong1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe('Invalid email or password.')
    expect(wrapper.find('#login-password').element.value).toBe('')
  })

  it('redirects each role to its dashboard after login', async () => {
    for (const [role, home] of [['PATIENT', '/dashboard'], ['DOCTOR', '/doctor'], ['ADMIN', '/admin']]) {
      const { wrapper, router, authService } = await mountAt('/login')
      authService.login.mockResolvedValue({ id: 'u', fullName: 'U', email: 'u@example.com', role })
      await wrapper.find('#login-email').setValue('u@example.com')
      await wrapper.find('#login-password').setValue('Secret123')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(router.currentRoute.value.path).toBe(home)
      expect(authService.login).toHaveBeenCalledWith({ email: 'u@example.com', password: 'Secret123' })
      wrapper.unmount()
    }
  })

  it('returns to the page that required login', async () => {
    const { wrapper, router, authService } = await mountAt('/history')
    expect(router.currentRoute.value.fullPath).toBe('/login?redirect=/history')
    authService.login.mockResolvedValue({ id: 'u', fullName: 'U', email: 'u@example.com', role: 'PATIENT' })
    await wrapper.find('#login-email').setValue('u@example.com')
    await wrapper.find('#login-password').setValue('Secret123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/history')
  })
})

describe('Register page', () => {
  beforeEach(() => vi.clearAllMocks())

  async function fill(wrapper, { name = 'Ayesha Rahman', email = 'ayesha@example.com', password = 'Secret123', confirm = password } = {}) {
    await wrapper.find('#register-name').setValue(name)
    await wrapper.find('#register-email').setValue(email)
    await wrapper.find('#register-password').setValue(password)
    await wrapper.find('#register-confirm').setValue(confirm)
  }

  it('checks every field, including the confirmation', async () => {
    const { wrapper, authService } = await mountAt('/register')
    await fill(wrapper, { name: 'A', email: 'bad', password: 'weak', confirm: 'other' })
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Full name must be at least 2 characters.')
    expect(wrapper.text()).toContain('Enter a valid email address')
    expect(wrapper.text()).toContain('Use 8 to 72 characters')
    expect(wrapper.text()).toContain('Passwords do not match.')
    expect(authService.register).not.toHaveBeenCalled()
  })

  it('never sends a role or the confirmation', async () => {
    const { wrapper, router, authService } = await mountAt('/register')
    authService.register.mockResolvedValue({ id: 'n', fullName: 'Ayesha Rahman', email: 'ayesha@example.com', role: 'PATIENT' })
    await fill(wrapper, { email: '  Ayesha@example.com ' })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.register).toHaveBeenCalledWith({ fullName: 'Ayesha Rahman', email: 'Ayesha@example.com', password: 'Secret123' })
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.registered).toBe('1')
  })

  it('shows the server message under the email when it is already registered', async () => {
    const { wrapper, authService } = await mountAt('/register')
    const message = 'An account with this email already exists.'
    authService.register.mockRejectedValue(httpError(409, { detail: message, errors: { email: message } }))
    await fill(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('#register-email-error').text()).toBe(message)
    expect(wrapper.find('#register-email').attributes('aria-invalid')).toBe('true')
  })

  it('shows a connection message when the server is unreachable', async () => {
    const { wrapper, authService } = await mountAt('/register')
    authService.register.mockRejectedValue(new Error('Network Error'))
    await fill(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('Could not reach the server')
  })
})
