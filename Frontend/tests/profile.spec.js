import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { validateDateOfBirth, validatePhone, localIsoDate } from '../src/auth/validation'

vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('../src/services/profileService', () => ({
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  changeEmail: vi.fn(),
  changePassword: vi.fn(),
}))

const base = {
  id: 'u1', fullName: 'Ayesha Rahman', email: 'ayesha@example.com', role: 'PATIENT',
  createdAt: '2026-01-15T10:00:00Z', updatedAt: '2026-01-15T10:00:00Z',
  phone: null, dateOfBirth: null, gender: null, address: null, specialization: null, hospital: null,
}

function httpError(status, data) {
  return Object.assign(new Error(`HTTP ${status}`), { response: { status, data } })
}

async function mountProfile(profile = base) {
  vi.resetModules()
  const profileService = await import('../src/services/profileService')
  profileService.getProfile.mockResolvedValue(profile)
  return mountProfileWithoutReset(profile)
}

/** Mounts /profile using whatever the profileService mock currently returns. */
async function mountProfileWithoutReset(profile = base) {
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue({ id: profile.id, fullName: profile.fullName, email: profile.email, role: profile.role })
  const profileService = await import('../src/services/profileService')
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const store = await import('../src/auth/authStore')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push('/profile')
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
  await flushPromises()
  return { wrapper, router, store, profileService }
}

describe('profile validation', () => {
  it('accepts empty or well-formed phone numbers', () => {
    expect(validatePhone('')).toBe('')
    expect(validatePhone('+880 1712-345678')).toBe('')
    expect(validatePhone('call me')).toMatch(/valid phone/)
    expect(validatePhone('12')).toMatch(/valid phone/)
  })

  it('requires a date of birth in the past', () => {
    const today = new Date(2026, 9, 27)
    expect(validateDateOfBirth('', today)).toBe('')
    expect(validateDateOfBirth('1990-05-17', today)).toBe('')
    expect(validateDateOfBirth('2026-10-27', today)).toMatch(/past/)
    expect(validateDateOfBirth('1899-12-31', today)).toMatch(/past/)
    expect(validateDateOfBirth('17/05/1990', today)).toMatch(/YYYY-MM-DD/)
  })

  it('formats today in local time', () => {
    expect(localIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })
})

describe('Profile page', () => {
  beforeEach(() => vi.clearAllMocks())

  it('is open to every signed-in role', async () => {
    for (const role of ['PATIENT', 'DOCTOR', 'ADMIN']) {
      const { router } = await mountProfile({ ...base, role })
      expect(router.currentRoute.value.path).toBe('/profile')
    }
  })

  it('explains an outdated backend and can retry', async () => {
    vi.resetModules()
    const profileService = await import('../src/services/profileService')
    profileService.getProfile.mockRejectedValueOnce(httpError(404, { detail: 'No static resource api/profile.' }))
    const { wrapper } = await mountProfileWithoutReset()
    expect(wrapper.find('[role="alert"]').text()).toContain('Restart the backend')

    profileService.getProfile.mockResolvedValue(base)
    await wrapper.find('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('h1').text()).toBe('Ayesha Rahman')
  })

  it('shows the summary and only shows doctor fields to doctors', async () => {
    const { wrapper } = await mountProfile()
    expect(wrapper.find('h1').text()).toBe('Ayesha Rahman')
    expect(wrapper.find('.avatar').text()).toBe('AR')
    expect(wrapper.text()).toContain('Patient')
    expect(wrapper.text()).toContain('0%')
    expect(wrapper.find('#profile-specialization').exists()).toBe(false)

    const doctor = await mountProfile({ ...base, role: 'DOCTOR', phone: '+8801712345678', specialization: 'Radiology' })
    expect(doctor.wrapper.find('#profile-specialization').element.value).toBe('Radiology')
    expect(doctor.wrapper.text()).toContain('33%')
  })

  it('saves changes, then updates the name shown in the header', async () => {
    const { wrapper, store, profileService } = await mountProfile()
    const saveButton = () => wrapper.find('form button[type="submit"]')
    expect(saveButton().attributes('disabled')).toBeDefined() // nothing changed yet

    profileService.updateProfile.mockImplementation(async (payload) => ({ ...base, ...payload, phone: payload.phone || null }))
    await wrapper.find('#profile-name').setValue('  Ayesha R. Khan ')
    await wrapper.find('#profile-phone').setValue('+8801712345678')
    await wrapper.find('#profile-gender').setValue('FEMALE')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(profileService.updateProfile).toHaveBeenCalledWith(expect.objectContaining({
      fullName: 'Ayesha R. Khan', phone: '+8801712345678', gender: 'FEMALE', dateOfBirth: '', address: '',
    }))
    expect(wrapper.text()).toContain('Your profile has been saved.')
    expect(store.authState.user.fullName).toBe('Ayesha R. Khan')
    expect(saveButton().attributes('disabled')).toBeDefined()
  })

  it('checks fields before saving and shows server errors under them', async () => {
    const { wrapper, profileService } = await mountProfile()
    await wrapper.find('#profile-phone').setValue('call me')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.find('#profile-phone-error').text()).toMatch(/valid phone/)
    expect(profileService.updateProfile).not.toHaveBeenCalled()

    profileService.updateProfile.mockRejectedValue(httpError(400, { detail: 'Please correct the highlighted fields.', errors: { dateOfBirth: 'Enter a real date' } }))
    await wrapper.find('#profile-phone').setValue('+8801712345678')
    await wrapper.find('#profile-dob').setValue('1990-02-01')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('#profile-dob-error').text()).toBe('Enter a real date')
  })

  it('discards unsaved changes', async () => {
    const { wrapper } = await mountProfile()
    await wrapper.find('#profile-name').setValue('Someone Else')
    await wrapper.findAll('form')[0].find('button[type="button"].btn-outline').trigger('click')
    expect(wrapper.find('#profile-name').element.value).toBe('Ayesha Rahman')
  })

  it('changes the email with the current password', async () => {
    const { wrapper, store, profileService } = await mountProfile()
    const emailForm = wrapper.findAll('form')[1]

    await wrapper.find('#profile-new-email').setValue('AYESHA@example.com')
    await wrapper.find('#profile-email-password').setValue('Secret123')
    await emailForm.trigger('submit')
    expect(wrapper.text()).toContain('This is already your email.')
    expect(profileService.changeEmail).not.toHaveBeenCalled()

    profileService.changeEmail.mockRejectedValue(httpError(400, { errors: { currentPassword: 'Your current password is incorrect' } }))
    await wrapper.find('#profile-new-email').setValue('new@example.com')
    await wrapper.find('#profile-email-password').setValue('Wrong1234')
    await emailForm.trigger('submit')
    await flushPromises()
    expect(wrapper.find('#profile-email-password-error').text()).toBe('Your current password is incorrect')

    profileService.changeEmail.mockResolvedValue({ ...base, email: 'new@example.com' })
    await wrapper.find('#profile-email-password').setValue('Secret123')
    await emailForm.trigger('submit')
    await flushPromises()
    expect(profileService.changeEmail).toHaveBeenLastCalledWith({ email: 'new@example.com', currentPassword: 'Secret123' })
    expect(store.authState.user.email).toBe('new@example.com')
    expect(wrapper.text()).toContain('Your email is now new@example.com')
  })

  it('changes the password and clears the form', async () => {
    const { wrapper, profileService } = await mountProfile()
    const passwordForm = wrapper.findAll('form')[2]

    await wrapper.find('#profile-current-password').setValue('Secret123')
    await wrapper.find('#profile-new-password').setValue('BrandNew456')
    await wrapper.find('#profile-confirm-password').setValue('Different789')
    await passwordForm.trigger('submit')
    expect(wrapper.find('#profile-confirm-password-error').text()).toBe('Passwords do not match.')

    profileService.changePassword.mockResolvedValue(base)
    await wrapper.find('#profile-confirm-password').setValue('BrandNew456')
    await passwordForm.trigger('submit')
    await flushPromises()
    expect(profileService.changePassword).toHaveBeenCalledWith({ currentPassword: 'Secret123', newPassword: 'BrandNew456' })
    expect(wrapper.text()).toContain('signed out on your other devices')
    expect(wrapper.find('#profile-new-password').element.value).toBe('')
  })
})
