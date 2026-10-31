import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { todayIso, formatTime, formatFee } from '../src/appointments/format'

// The bell in the header and the admin account page load notifications; no server here.
vi.mock('../src/services/notificationService', async (importOriginal) => ({
  ...(await importOriginal()),
  listNotifications: vi.fn().mockResolvedValue({ items: [], unread: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
vi.mock('../src/services/authService', () => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('../src/services/surgeonService', () => ({
  listSurgeons: vi.fn(),
  getSurgeon: vi.fn(),
  getSlots: vi.fn(),
  getMyProfile: vi.fn(),
  createProfile: vi.fn(),
  updateProfile: vi.fn(),
  updateAvailability: vi.fn(),
  uploadPhoto: vi.fn(),
  removePhoto: vi.fn(),
  uploadDocument: vi.fn(),
  removeDocument: vi.fn(),
  documentUrl: vi.fn((id) => `/doc/${id}`),
  photoSrc: vi.fn((url) => url ?? ''),
}))
vi.mock('../src/services/appointmentService', () => ({
  bookAppointment: vi.fn(),
  listMyAppointments: vi.fn(),
  listSurgeonAppointments: vi.fn(),
  getAppointment: vi.fn(),
  confirmAppointment: vi.fn(),
  cancelAppointment: vi.fn(),
  rescheduleAppointment: vi.fn(),
  completeAppointment: vi.fn(),
}))

const PATIENT = { id: 'p1', fullName: 'Rahima Begum', email: 'rahima@example.com', role: 'PATIENT' }
const DOCTOR = { id: 'd1', fullName: 'Dr. Nasrin Akter', email: 'nasrin@example.com', role: 'DOCTOR' }

const day = (offset) => todayIso(new Date(Date.now() + offset * 86400000))
const TOMORROW = day(1)
const LATER = day(2)

const SURGEON = {
  id: 's1', fullName: 'Dr. Nasrin Akter', specialty: 'Breast Surgery', designation: 'Consultant Breast Surgeon',
  qualifications: 'MBBS, FCPS (Surgery)', experienceYears: 12, hospitalName: 'Dhaka Breast Centre',
  hospitalAddress: 'Dhanmondi, Dhaka', consultationFee: 1500, biography: 'Breast surgeon focused on early cancer care.',
  registrationNumber: 'BMDC-A-1234', availableDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
  timeSlots: ['09:00', '10:00'], acceptingAppointments: true, photoUrl: null,
}
const SLOTS = [
  { date: TOMORROW, dayOfWeek: 'X', slots: [{ time: '09:00', available: false }, { time: '10:00', available: true }] },
  { date: LATER, dayOfWeek: 'Y', slots: [{ time: '09:00', available: true }, { time: '10:00', available: true }] },
]

function appointment(over = {}) {
  return {
    id: 'a1', appointmentCode: 'BSA-261101-ABCDEF', date: TOMORROW, time: '10:00', status: 'PENDING', reason: 'Lump check',
    bookedAt: '2026-10-31T10:00:00Z', rescheduleCount: 0, previousDate: null, previousTime: null, cancelledBy: null, cancelReason: null,
    patient: { id: 'p1', fullName: 'Rahima Begum', email: 'rahima@example.com', phone: '+8801712345678' },
    surgeon: { id: 's1', fullName: 'Dr. Nasrin Akter', specialty: 'Breast Surgery', designation: 'Consultant Breast Surgeon', hospitalName: 'Dhaka Breast Centre', hospitalAddress: 'Dhanmondi', consultationFee: 1500, photoUrl: null },
    ...over,
  }
}

function httpError(status, data = {}) {
  return Object.assign(new Error(`HTTP ${status}`), { response: { status, data } })
}

async function mountAt(path, user = PATIENT, setup = () => {}) {
  vi.resetModules()
  const authService = await import('../src/services/authService')
  authService.fetchCurrentUser.mockResolvedValue(user)
  const surgeons = await import('../src/services/surgeonService')
  const appointments = await import('../src/services/appointmentService')
  surgeons.listSurgeons.mockResolvedValue([SURGEON])
  surgeons.getSurgeon.mockResolvedValue(structuredClone(SURGEON))
  surgeons.getSlots.mockResolvedValue(structuredClone(SLOTS))
  setup({ surgeons, appointments })
  const { routes } = await import('../src/router')
  const { installAuthGuard } = await import('../src/auth/guard')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push(path)
  const wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router], directives: { reveal: { mounted() {} } } } })
  await flushPromises()
  return { wrapper, router, surgeons, appointments }
}

describe('format helpers', () => {
  it('formats times, fees and dates for people', () => {
    expect(formatTime('09:30')).toBe('9:30 AM')
    expect(formatTime('00:15')).toBe('12:15 AM')
    expect(formatTime('17:00')).toBe('5:00 PM')
    expect(formatFee(1500)).toBe('৳1,500')
    expect(todayIso(new Date(2026, 0, 5))).toBe('2026-01-05')
  })
})

describe('Find a Breast Surgeon', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists the approved Breast Surgeons the server returns, with view and book buttons', async () => {
    const { wrapper } = await mountAt('/breast-surgeons', null)
    expect(wrapper.find('h1').text()).toBe('Find a Breast Surgeon')
    const card = wrapper.find('.surgeon-card')
    expect(card.text()).toContain('Dr. Nasrin Akter')
    expect(card.text()).toContain('Breast Surgery')
    expect(card.text()).toContain('12 years in breast surgery')
    expect(card.text()).toContain('৳1,500')
    expect(card.findAll('a').map((a) => a.attributes('href'))).toEqual(['/breast-surgeons/s1', '/breast-surgeons/s1/book'])
    expect(wrapper.findAll('select')[1].text()).toContain('Dhaka Breast Centre')
  })

  it('shows an empty state when no surgeon is approved yet', async () => {
    const { wrapper } = await mountAt('/breast-surgeons', null, ({ surgeons }) => surgeons.listSurgeons.mockResolvedValue([]))
    expect(wrapper.text()).toContain('No Breast Surgeons available yet')
  })

  it('filters on the server by experience and hospital', async () => {
    const { wrapper, surgeons } = await mountAt('/breast-surgeons', null)
    const [experience, hospital] = wrapper.findAll('select')
    await experience.setValue('10')
    await flushPromises()
    expect(surgeons.listSurgeons).toHaveBeenLastCalledWith({ q: '', minExperience: 10, hospital: '' })
    await hospital.setValue('Dhaka Breast Centre')
    await flushPromises()
    expect(surgeons.listSurgeons).toHaveBeenLastCalledWith({ q: '', minExperience: 10, hospital: 'Dhaka Breast Centre' })
  })
})

describe('Breast Surgeon details', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows the full profile and the calendar, and carries a chosen slot to booking', async () => {
    const { wrapper, router } = await mountAt('/breast-surgeons/s1')
    expect(wrapper.find('h1').text()).toBe('Dr. Nasrin Akter')
    expect(wrapper.text()).toContain('Verified Breast Surgeon')
    expect(wrapper.text()).toContain('BMDC-A-1234')
    expect(wrapper.text()).toContain('Dhanmondi, Dhaka')
    // The booked slot cannot be chosen; a free one can.
    expect(wrapper.find('[data-time="09:00"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[data-time="10:00"]').trigger('click')
    await wrapper.find('.chosen .btn').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/breast-surgeons/s1/book')
    expect(router.currentRoute.value.query).toEqual({ date: TOMORROW, time: '10:00' })
  })

  it('tells doctors that only patients can book', async () => {
    const { wrapper } = await mountAt('/breast-surgeons/s1', DOCTOR)
    expect(wrapper.text()).toContain('Only patient accounts can book appointments.')
    expect(wrapper.find('.book-btn').exists()).toBe(false)
  })

  it('says "not found" for a profile that is not approved', async () => {
    const { wrapper } = await mountAt('/breast-surgeons/s9', null, ({ surgeons }) => surgeons.getSurgeon.mockRejectedValue(httpError(404, { detail: 'Breast Surgeon not found' })))
    expect(wrapper.text()).toContain('Breast Surgeon not found')
  })
})

describe('Booking an appointment', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sends visitors to log in first, and keeps doctors out', async () => {
    const guest = await mountAt('/breast-surgeons/s1/book', null)
    expect(guest.router.currentRoute.value.path).toBe('/login')
    expect(guest.router.currentRoute.value.query.redirect).toBe('/breast-surgeons/s1/book')
    const doctor = await mountAt('/breast-surgeons/s1/book', DOCTOR)
    expect(doctor.router.currentRoute.value.path).toBe('/forbidden')
  })

  it('books the chosen slot after the review step and opens the confirmation', async () => {
    const { wrapper, router, appointments } = await mountAt('/breast-surgeons/s1/book', PATIENT, ({ appointments }) => {
      appointments.bookAppointment.mockResolvedValue(appointment({ date: LATER, time: '09:00' }))
      appointments.getAppointment.mockResolvedValue(appointment({ date: LATER, time: '09:00' }))
    })
    const continueButton = wrapper.find('.nav .btn-primary')
    expect(continueButton.attributes('disabled')).toBeDefined()
    await wrapper.find(`[data-date="${LATER}"]`).trigger('click')
    await wrapper.find('[data-time="09:00"]').trigger('click')
    await wrapper.find('#booking-reason').setValue('  Follow-up after mammogram ')
    await continueButton.trigger('click')

    const summary = wrapper.find('.summary').text()
    expect(summary).toContain('Dr. Nasrin Akter')
    expect(summary).toContain('Breast Surgery')
    expect(summary).toContain('9:00 AM')
    expect(summary).toContain('Follow-up after mammogram')

    await wrapper.findAll('.nav .btn-primary').at(-1).trigger('click')
    await flushPromises()
    expect(appointments.bookAppointment).toHaveBeenCalledWith({ surgeonId: 's1', date: LATER, time: '09:00', reason: 'Follow-up after mammogram' })
    expect(router.currentRoute.value.path).toBe('/appointments/a1/confirmation')
    expect(wrapper.text()).toContain('BSA-261101-ABCDEF')
    expect(wrapper.text()).toContain('Appointment requested')
  })

  it('goes back to the calendar when someone else took the slot first', async () => {
    const { wrapper, surgeons, appointments } = await mountAt(`/breast-surgeons/s1/book?date=${TOMORROW}&time=10:00`, PATIENT, ({ appointments }) => {
      appointments.bookAppointment.mockRejectedValue(httpError(409, { detail: 'Sorry, this time slot has just been booked by someone else. Please choose another.' }))
    })
    // The slot from the profile page is still free, so it is kept.
    await wrapper.find('.nav .btn-primary').trigger('click')
    await wrapper.findAll('.nav .btn-primary').at(-1).trigger('click')
    await flushPromises()
    expect(appointments.bookAppointment).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.alert.error').text()).toContain('just been booked')
    expect(wrapper.find('.summary').exists()).toBe(false)
    expect(surgeons.getSlots).toHaveBeenCalledTimes(2)
  })
})

describe('My Breast Surgeon Appointments', () => {
  beforeEach(() => vi.clearAllMocks())

  const LIST = [
    appointment(),
    appointment({ id: 'a2', appointmentCode: 'BSA-261101-CONFIR', status: 'CONFIRMED', date: LATER, time: '09:00' }),
    appointment({ id: 'a3', appointmentCode: 'BSA-261001-DONEXX', status: 'COMPLETED', date: '2026-01-10' }),
    appointment({ id: 'a4', appointmentCode: 'BSA-261001-CANCEL', status: 'CANCELLED', cancelledBy: 'SURGEON', cancelReason: 'Away that day' }),
  ]

  it('shows only the patient’s own list, with status filters and actions on open appointments only', async () => {
    const { wrapper } = await mountAt('/my-appointments', PATIENT, ({ appointments }) => appointments.listMyAppointments.mockResolvedValue(structuredClone(LIST)))
    expect(wrapper.find('h1').text()).toBe('My Breast Surgeon Appointments')
    expect(wrapper.findAll('.item')).toHaveLength(4)
    expect(wrapper.text()).toContain('Cancelled by the surgeon: “Away that day”')
    // Completed and cancelled appointments cannot be changed.
    const items = wrapper.findAll('.item')
    expect(items[0].find('.actions').exists()).toBe(true)
    expect(items[2].find('.actions').exists()).toBe(false)
    expect(items[3].find('.actions').exists()).toBe(false)

    await wrapper.find('[data-filter="CONFIRMED"]').trigger('click')
    expect(wrapper.findAll('.item')).toHaveLength(1)
    expect(wrapper.find('.item').text()).toContain('BSA-261101-CONFIR')
  })

  it('cancels after confirmation and refreshes the list', async () => {
    const { wrapper, appointments } = await mountAt('/my-appointments', PATIENT, ({ appointments }) => {
      appointments.listMyAppointments.mockResolvedValue(structuredClone(LIST))
      appointments.cancelAppointment.mockResolvedValue(appointment({ status: 'CANCELLED' }))
    })
    await wrapper.find('.cancel-btn').trigger('click')
    expect(wrapper.find('[role="alertdialog"]').text()).toContain('BSA-261101-ABCDEF')
    // "Keep it" closes without cancelling.
    await wrapper.find('[role="alertdialog"] .btn-outline').trigger('click')
    expect(appointments.cancelAppointment).not.toHaveBeenCalled()

    await wrapper.find('.cancel-btn').trigger('click')
    await wrapper.find('#cancel-reason').setValue('Feeling better')
    await wrapper.find('.btn-danger').trigger('click')
    await flushPromises()
    expect(appointments.cancelAppointment).toHaveBeenCalledWith('a1', 'Feeling better')
    expect(appointments.listMyAppointments).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alertdialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('has been cancelled')
  })

  it('reschedules to a free slot shown in the calendar', async () => {
    const { wrapper, appointments } = await mountAt('/my-appointments', PATIENT, ({ appointments }) => {
      appointments.listMyAppointments.mockResolvedValue([appointment()])
      appointments.rescheduleAppointment.mockResolvedValue(appointment({ date: LATER, time: '09:00', previousDate: TOMORROW, previousTime: '10:00' }))
    })
    await wrapper.find('.actions .btn-outline').trigger('click')
    await flushPromises()
    const modal = wrapper.find('[role="dialog"]')
    expect(modal.text()).toContain('Reschedule appointment')
    // The current slot is marked, and the request button waits for a choice.
    expect(modal.find('[data-time="10:00"]').text()).toContain('current')
    expect(modal.find('footer .btn-primary').attributes('disabled')).toBeDefined()
    await modal.find(`[data-date="${LATER}"]`).trigger('click')
    await modal.find('[data-time="09:00"]').trigger('click')
    await modal.find('footer .btn-primary').trigger('click')
    await flushPromises()
    expect(appointments.rescheduleAppointment).toHaveBeenCalledWith('a1', { date: LATER, time: '09:00' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Pending until the surgeon confirms')
  })
})

describe('Set up a Breast Surgeon profile from Find a Breast Surgeon', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers doctors their profile page, visitors registration, and patients nothing', async () => {
    const asDoctor = await mountAt('/breast-surgeons', DOCTOR)
    expect(asDoctor.wrapper.find('.join-banner a').attributes('href')).toBe('/profile')
    const asVisitor = await mountAt('/breast-surgeons', null)
    expect(asVisitor.wrapper.find('.join-banner a').attributes('href')).toBe('/register?as=doctor')
    const asPatient = await mountAt('/breast-surgeons', PATIENT)
    expect(asPatient.wrapper.find('.join-banner').exists()).toBe(false)
  })
})

describe('Breast Surgeon dashboard', () => {
  beforeEach(() => vi.clearAllMocks())

  const PROFILE = { ...SURGEON, status: 'APPROVED', documents: [{ fileId: 'f1', filename: 'reg.pdf', size: 2048 }], email: 'c@example.com', phone: '+880171' }

  it('is for doctors only', async () => {
    const { router } = await mountAt('/surgeon-dashboard', PATIENT)
    expect(router.currentRoute.value.path).toBe('/forbidden')
    const back = await mountAt('/my-appointments', DOCTOR)
    expect(back.router.currentRoute.value.path).toBe('/forbidden')
  })

  it('invites a doctor without a profile to create one', async () => {
    const { wrapper } = await mountAt('/surgeon-dashboard', DOCTOR, ({ surgeons }) => surgeons.getMyProfile.mockRejectedValue(httpError(404)))
    expect(wrapper.text()).toContain('Become a listed Breast Surgeon')
    expect(wrapper.find('a.btn-primary').attributes('href')).toBe('/profile')
  })

  it('counts appointments by status and lets the surgeon accept and reject requests', async () => {
    const list = [
      appointment(),
      appointment({ id: 'a2', patient: { ...appointment().patient, fullName: 'Karima Khatun' } }),
      appointment({ id: 'a3', status: 'CONFIRMED', date: LATER }),
      appointment({ id: 'a4', status: 'COMPLETED', date: '2026-01-10' }),
    ]
    const { wrapper, appointments } = await mountAt('/surgeon-dashboard', DOCTOR, ({ surgeons, appointments }) => {
      surgeons.getMyProfile.mockResolvedValue(structuredClone(PROFILE))
      appointments.listSurgeonAppointments.mockResolvedValue(structuredClone(list))
      appointments.confirmAppointment.mockResolvedValue(appointment({ status: 'CONFIRMED' }))
      appointments.cancelAppointment.mockResolvedValue(appointment({ id: 'a2', status: 'CANCELLED', cancelledBy: 'SURGEON' }))
    })
    const stat = (key) => wrapper.find(`[data-stat="${key}"] strong`).text()
    expect([stat('total'), stat('PENDING'), stat('CONFIRMED'), stat('COMPLETED'), stat('CANCELLED')]).toEqual(['4', '2', '1', '1', '0'])
    expect(wrapper.find('.upcoming').text()).toContain('Rahima Begum')

    // Requests are shown first.
    expect(wrapper.findAll('.row')).toHaveLength(2)
    await wrapper.find('.row .toggle').trigger('click')
    expect(wrapper.find('.details').text()).toContain('Lump check')
    expect(wrapper.find('.details').text()).toContain('+8801712345678')

    await wrapper.find('.row .row-actions .btn-primary').trigger('click')
    await flushPromises()
    expect(appointments.confirmAppointment).toHaveBeenCalledWith('a1')
    expect(stat('PENDING')).toBe('1')
    expect(stat('CONFIRMED')).toBe('2')

    await wrapper.find('.row .reject').trigger('click')
    expect(wrapper.find('[role="alertdialog"] h2').text()).toBe('Reject this request?')
    await wrapper.find('.btn-danger').trigger('click')
    await flushPromises()
    expect(appointments.cancelAppointment).toHaveBeenCalledWith('a2', '')
    expect(stat('CANCELLED')).toBe('1')
  })

  it('saves weekly availability and the bookings switch', async () => {
    const { wrapper, surgeons } = await mountAt('/surgeon-dashboard?tab=availability', DOCTOR, ({ surgeons, appointments }) => {
      surgeons.getMyProfile.mockResolvedValue(structuredClone(PROFILE))
      appointments.listSurgeonAppointments.mockResolvedValue([])
      surgeons.updateAvailability.mockResolvedValue({ ...PROFILE, availableDays: ['MONDAY'], acceptingAppointments: false })
    })
    // Untick every day except Monday.
    for (const box of wrapper.findAll('.day-chip input')) {
      if (box.element.value !== 'MONDAY') await box.trigger('change')
    }
    await wrapper.find('.accept-toggle input').setValue(false)
    await wrapper.find('.save-row .btn-primary').trigger('click')
    await flushPromises()
    expect(surgeons.updateAvailability).toHaveBeenCalledWith({ availableDays: ['MONDAY'], timeSlots: ['09:00', '10:00'], acceptingAppointments: false })
    expect(wrapper.text()).toContain('Availability saved')
  })
})

describe('Breast Surgeon profile form', () => {
  beforeEach(() => vi.clearAllMocks())

  async function openCreate(setup = () => {}) {
    return mountAt('/surgeon-profile', DOCTOR, (ctx) => {
      ctx.surgeons.getMyProfile.mockRejectedValue(httpError(404))
      setup(ctx)
    })
  }

  it('fixes the specialty to Breast Surgery and checks the fields before sending', async () => {
    const { wrapper, surgeons } = await openCreate()
    expect(wrapper.find('h1').text()).toBe('Doctor Profile')
    expect(wrapper.find('.crumbs').text()).toContain('Add Profile')
    expect(wrapper.find('#bs-specialty').text()).toContain('Breast Surgery')
    expect(wrapper.find('select#bs-specialty').exists()).toBe(false)
    // Name and email come from the account, split into first and last name.
    expect(wrapper.find('#bs-first-name').element.value).toBe('Dr. Nasrin')
    expect(wrapper.find('#bs-last-name').element.value).toBe('Akter')
    expect(wrapper.find('#bs-email').element.value).toBe('nasrin@example.com')
    await wrapper.find('form').trigger('submit')
    expect(surgeons.createProfile).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Phone number is required')
    expect(wrapper.text()).toContain('Medical registration number is required')
    expect(wrapper.text()).toContain('Upload at least one verification document')
    // Basic Information has errors, so it is the step left open.
    expect(wrapper.find('.step-tab.active').text()).toContain('Basic Information')
    expect(wrapper.find('[data-step="extra"]').isVisible()).toBe(false)
  })

  it('moves to Extra Information only when Basic Information is complete', async () => {
    const { wrapper } = await openCreate()
    const next = () => wrapper.find('.next-btn').trigger('click')
    await next()
    expect(wrapper.find('.step-tab.active').text()).toContain('Basic Information')
    expect(wrapper.text()).toContain('Phone number is required')
    // Extra-step fields are not complained about yet.
    expect(wrapper.text()).not.toContain('Designation is required')

    await wrapper.find('#bs-phone').setValue('+8801712345678')
    await wrapper.find('#bs-fee').setValue('1500')
    await wrapper.find('#bs-registration').setValue('BMDC-A-1234')
    await wrapper.find('#bs-bio').setValue('Breast surgeon focused on early cancer care and reconstruction.')
    await wrapper.find('#bs-dob').setValue(`${new Date().getFullYear() - 5}-01-01`)
    await next()
    expect(wrapper.text()).toContain('Enter a date of birth for an age between 21 and 100')
    await wrapper.find('#bs-dob').setValue('1980-05-17')
    await next()
    expect(wrapper.find('.step-tab.active').text()).toContain('Extra Information')
    expect(wrapper.find('[data-step="extra"]').isVisible()).toBe(true)
  })

  it('creates the profile, then uploads the documents', async () => {
    const saved = { ...SURGEON, status: 'PENDING', documents: [], email: 'nasrin@example.com', phone: '+8801712345678' }
    const { wrapper, surgeons } = await openCreate(({ surgeons }) => {
      surgeons.createProfile.mockResolvedValue(saved)
      surgeons.uploadDocument.mockResolvedValue({ ...saved, documents: [{ fileId: 'f1', filename: 'reg.pdf', size: 1000 }] })
    })
    const set = (id, v) => wrapper.find(id).setValue(v)
    await set('#bs-phone', '+8801712345678')
    await set('#bs-gender', 'FEMALE')
    await set('#bs-dob', '1980-05-17')
    // Pick two common languages and type one more.
    for (const chip of wrapper.findAll('.chip')) if (['Bangla', 'English'].includes(chip.text())) await chip.trigger('click')
    await set('#bs-language-input', 'Chakma')
    await wrapper.find('#bs-language-input').trigger('keydown', { key: 'Enter' })
    await set('#bs-designation', 'Consultant Breast Surgeon')
    await set('#bs-registration', 'BMDC-A-1234')
    await set('#bs-qualifications', 'MBBS, FCPS (Surgery)')
    await set('#bs-experience', '12')
    await set('#bs-fee', '1500')
    await set('#bs-bio', 'Breast surgeon focused on early cancer care and reconstruction.')
    await set('#bs-hospital', 'Dhaka Breast Centre')
    await set('#bs-address', 'Dhanmondi, Dhaka')
    const file = new File(['%PDF-1.4'], 'reg.pdf', { type: 'application/pdf' })
    const input = wrapper.find('.dropzone input')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(surgeons.createProfile).toHaveBeenCalledTimes(1)
    const body = surgeons.createProfile.mock.calls[0][0]
    expect(body).toMatchObject({
      specialty: 'Breast Surgery', fullName: 'Dr. Nasrin Akter', experienceYears: 12, consultationFee: 1500, registrationNumber: 'BMDC-A-1234',
      gender: 'FEMALE', dateOfBirth: '1980-05-17', languages: ['Bangla', 'English', 'Chakma'],
    })
    expect(body.availableDays.length).toBeGreaterThan(0)
    expect(surgeons.uploadDocument).toHaveBeenCalledWith(file)
    expect(wrapper.text()).toContain('Waiting for verification')
    expect(wrapper.text()).toContain('reg.pdf')
  })

  it('edits a saved profile and removes its picture', async () => {
    const saved = { ...SURGEON, status: 'APPROVED', documents: [{ fileId: 'f1', filename: 'reg.pdf', size: 2048 }], email: 'c@example.com',
      phone: '+8801712345678', photoUrl: '/api/doctors/breast-surgeons/s1/photo?v=1', gender: 'FEMALE', dateOfBirth: '1980-05-17', languages: ['Bangla'] }
    const { wrapper, surgeons } = await mountAt('/surgeon-profile', DOCTOR, ({ surgeons }) => {
      surgeons.getMyProfile.mockResolvedValue(structuredClone(saved))
      surgeons.updateProfile.mockResolvedValue({ ...saved, consultationFee: 2000 })
      surgeons.removePhoto.mockResolvedValue({ ...saved, consultationFee: 2000, photoUrl: null })
    })
    expect(wrapper.text()).toContain('Female')
    expect(wrapper.text()).toContain('Bangla')
    await wrapper.findAll('button').find((b) => b.text().includes('Edit profile')).trigger('click')
    expect(wrapper.find('#bs-id').element.value).toBe('#BS-S1')
    expect(wrapper.find('#bs-gender').element.value).toBe('FEMALE')
    expect(wrapper.find('.chip.on').text()).toContain('Bangla')

    await wrapper.find('#bs-fee').setValue('2000')
    await wrapper.findAll('button').find((b) => b.text() === 'Remove').trigger('click')
    expect(wrapper.text()).toContain('The picture will be removed when you save')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(surgeons.updateProfile).toHaveBeenCalledWith(expect.objectContaining({ consultationFee: 2000, languages: ['Bangla'], fullName: 'Dr. Nasrin Akter' }))
    expect(surgeons.removePhoto).toHaveBeenCalledTimes(1)
    expect(surgeons.uploadPhoto).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Your profile has been saved.')
  })

  it('shows the administrator’s note on a rejected profile', async () => {
    const { wrapper } = await mountAt('/surgeon-profile', DOCTOR, ({ surgeons }) =>
      surgeons.getMyProfile.mockResolvedValue({ ...SURGEON, status: 'REJECTED', reviewNote: 'Upload a clearer certificate', documents: [], email: 'e@x.com', phone: '1234567' }),
    )
    expect(wrapper.text()).toContain('Changes needed')
    expect(wrapper.text()).toContain('Upload a clearer certificate')
  })
})
