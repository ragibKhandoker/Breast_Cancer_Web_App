import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

// The bell in the header and the admin account page load notifications; no server here.
vi.mock('../src/services/notificationService', async (importOriginal) => ({
  ...(await importOriginal()),
  listNotifications: vi.fn().mockResolvedValue({ items: [], unread: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  requestPasswordHelp: vi.fn(),
}))
vi.mock('../src/services/profileService', () => ({
  getProfile: vi.fn().mockReturnValue(new Promise(() => {})),
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
  listMessages: vi.fn().mockResolvedValue([]),
  getOverview: vi.fn().mockReturnValue(new Promise(() => {})),
  getAdminAccount: vi.fn().mockReturnValue(new Promise(() => {})),
  listSurgeonProfiles: vi.fn().mockResolvedValue([]),
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

  it('registers a patient by default and never sends a role or the confirmation', async () => {
    const { wrapper, router, authService } = await mountAt('/register')
    authService.register.mockResolvedValue({ id: 'n', fullName: 'Ayesha Rahman', email: 'ayesha@example.com', role: 'PATIENT' })
    expect(wrapper.find('.account-type.on').text()).toContain('Register as a Patient')
    await fill(wrapper, { email: '  Ayesha@example.com ' })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.register).toHaveBeenCalledWith({
      fullName: 'Ayesha Rahman', email: 'Ayesha@example.com', password: 'Secret123', accountType: 'PATIENT',
    })
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.registered).toBe('1')
  })

  it('registers a doctor when Register as a Doctor is chosen', async () => {
    const { wrapper, router, authService } = await mountAt('/register')
    authService.register.mockResolvedValue({ id: 'd', fullName: 'Ayesha Rahman', email: 'ayesha@example.com', role: 'DOCTOR' })
    await wrapper.find('input[name="account-type"][value="DOCTOR"]').setValue(true)
    expect(wrapper.find('.account-type.on').text()).toContain('Register as a Doctor')
    expect(wrapper.text()).toContain('Doctor accounts are checked by an administrator')
    expect(wrapper.find('button[type="submit"]').text()).toContain('Create Doctor Account')
    await fill(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.register).toHaveBeenCalledWith(expect.objectContaining({ accountType: 'DOCTOR' }))
    expect(router.currentRoute.value.query.registered).toBe('doctor')
  })

  it('opens with Doctor chosen from a Join as a doctor link', async () => {
    const { wrapper } = await mountAt('/register?as=doctor')
    expect(wrapper.find('.account-type.on').text()).toContain('Register as a Doctor')
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

describe('Forgot password', () => {
  beforeEach(() => vi.clearAllMocks())

  it('is linked from the Login page', async () => {
    const { wrapper } = await mountAt('/login')
    expect(wrapper.find('a.forgot').attributes('href')).toBe('/forgot-password')
  })

  it('validates, sends the request and shows the same answer for any email', async () => {
    const { wrapper, authService } = await mountAt('/forgot-password')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Email is required.')
    expect(authService.requestPasswordHelp).not.toHaveBeenCalled()

    authService.requestPasswordHelp.mockResolvedValue({ message: 'ok' })
    await wrapper.find('#forgot-email').setValue(' rahima@example.com ')
    await wrapper.find('#forgot-message').setValue('Call me on 01712345678')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(authService.requestPasswordHelp).toHaveBeenCalledWith({ email: 'rahima@example.com', message: 'Call me on 01712345678' })
    expect(wrapper.text()).toContain('Request sent')
    expect(wrapper.text()).toContain('If rahima@example.com has an account')
  })

  it('sends a user with a temporary password to choose a new one after login', async () => {
    const { wrapper, router, authService } = await mountAt('/login')
    authService.login.mockResolvedValue({ id: 'p1', fullName: 'Rahima', email: 'r@example.com', role: 'PATIENT', mustChangePassword: true })
    await wrapper.find('#login-email').setValue('r@example.com')
    await wrapper.find('#login-password').setValue('Temp2345abcd')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/profile')
    expect(router.currentRoute.value.hash).toBe('#change-password')
  })

  it('shows "account created" as a notification outside the form, which can be closed', async () => {
    document.body.querySelectorAll('.toast').forEach((el) => el.remove())
    const { wrapper } = await mountAt('/login?registered=1')
    const toast = document.body.querySelector('.toast')
    expect(toast.textContent).toContain('Account created')
    expect(toast.textContent).toContain('Your account has been created. Please log in.')
    expect(wrapper.find('form').text()).not.toContain('Your account has been created')
    expect(toast.closest('form')).toBeNull()

    toast.querySelector('.toast-close').click()
    await flushPromises()
    expect(document.body.querySelector('.toast')).toBeNull()
    wrapper.unmount()
  })

  it('sends a doctor with a temporary password to Account Settings, where the password form is', async () => {
    const { wrapper, router, authService } = await mountAt('/login')
    authService.login.mockResolvedValue({ id: 'd1', fullName: 'Dr. Nasrin', email: 'n@example.com', role: 'DOCTOR', mustChangePassword: true })
    await wrapper.find('#login-email').setValue('n@example.com')
    await wrapper.find('#login-password').setValue('Temp2345abcd')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/account-settings')
    expect(router.currentRoute.value.hash).toBe('#change-password')
  })

  it('explains an expired temporary password', async () => {
    const { wrapper, authService } = await mountAt('/login')
    const detail = 'Your temporary password has expired. Use "Forgot password?" to ask an administrator for a new one.'
    authService.login.mockRejectedValue(httpError(401, { detail, code: 'temporary_password_expired' }))
    await wrapper.find('#login-email').setValue('r@example.com')
    await wrapper.find('#login-password').setValue('Temp2345abcd')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe(detail)
  })

  it('explains that a temporary password works only once', async () => {
    const { wrapper, authService } = await mountAt('/login')
    const detail = 'This temporary password has already been used and works only once. Use "Forgot password?" to ask an administrator for a new one.'
    authService.login.mockRejectedValue(httpError(401, { detail, code: 'temporary_password_used' }))
    await wrapper.find('#login-email').setValue('r@example.com')
    await wrapper.find('#login-password').setValue('Temp2345abcd')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe(detail)
  })
})

describe('Admin login option', () => {
  beforeEach(() => vi.clearAllMocks())

  async function logInAs(user, { tick = true } = {}) {
    const ctx = await mountAt('/login')
    ctx.authService.login.mockResolvedValue(user)
    if (tick) await ctx.wrapper.find('.admin-toggle input').setValue(true)
    await ctx.wrapper.find('#login-email').setValue(user.email)
    await ctx.wrapper.find('#login-password').setValue('Secret123')
    await ctx.wrapper.find('form').trigger('submit')
    await flushPromises()
    return ctx
  }

  it('switches the page to the admin login and keeps it in the URL', async () => {
    const { wrapper, router } = await mountAt('/login')
    await wrapper.find('.admin-toggle input').setValue(true)
    await flushPromises()
    expect(wrapper.find('h1').text()).toBe('Admin login')
    expect(wrapper.find('button[type="submit"]').text()).toBe('Log in to admin')
    expect(router.currentRoute.value.query.as).toBe('admin')
    expect(wrapper.text()).not.toContain('Create an account')
  })

  it('opens it ticked from ?as=admin', async () => {
    const { wrapper } = await mountAt('/login?as=admin')
    expect(wrapper.find('.admin-toggle input').element.checked).toBe(true)
  })

  it('sends the site owner to their admin account and other admins to the portal', async () => {
    const owner = await logInAs({ id: 'o1', fullName: 'Owner', email: 'ragibahsan999@admin.com', role: 'ADMIN', owner: true })
    expect(owner.router.currentRoute.value.path).toBe('/admin-account')
    const other = await logInAs({ id: 'a2', fullName: 'Second', email: 'second@admin.com', role: 'ADMIN', owner: false })
    expect(other.router.currentRoute.value.path).toBe('/admin')
  })

  it('refuses a patient account and signs it out again', async () => {
    const { wrapper, router, authService } = await logInAs({ id: 'p1', fullName: 'Rahima', email: 'r@example.com', role: 'PATIENT' })
    expect(authService.logout).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/login')
    expect(wrapper.text()).toContain('This is not an administrator account.')
  })
})
