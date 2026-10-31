// Shared wording and formatting for the Breast Surgeon appointment pages.

export const SPECIALTY = 'Breast Surgery'

export const DAYS = [
  { id: 'MONDAY', short: 'Mon', label: 'Monday' },
  { id: 'TUESDAY', short: 'Tue', label: 'Tuesday' },
  { id: 'WEDNESDAY', short: 'Wed', label: 'Wednesday' },
  { id: 'THURSDAY', short: 'Thu', label: 'Thursday' },
  { id: 'FRIDAY', short: 'Fri', label: 'Friday' },
  { id: 'SATURDAY', short: 'Sat', label: 'Saturday' },
  { id: 'SUNDAY', short: 'Sun', label: 'Sunday' },
]

export const STATUS = {
  PENDING: { label: 'Pending', icon: 'refresh' },
  CONFIRMED: { label: 'Confirmed', icon: 'check-circle' },
  CANCELLED: { label: 'Cancelled', icon: 'close' },
  COMPLETED: { label: 'Completed', icon: 'heart' },
}

/** Patients may cancel or move an appointment while it is still open. */
export const isOpen = (appointment) => appointment.status === 'PENDING' || appointment.status === 'CONFIRMED'

// Dates from the API are plain yyyy-MM-dd days; read them at local midnight so they never shift a day.
const asDay = (date) => new Date(`${date}T00:00`)

export function formatDate(date, { weekday = true } = {}) {
  if (!date) return ''
  return asDay(date).toLocaleDateString('en-GB', {
    weekday: weekday ? 'short' : undefined, day: 'numeric', month: 'short', year: 'numeric',
  })
}

/** "09:30" → "9:30 AM". */
export function formatTime(time) {
  if (!time) return ''
  const [h, m] = time.split(':').map(Number)
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`
}

export function formatFee(fee) {
  return fee == null ? '' : `৳${Number(fee).toLocaleString('en-US')}`
}

export function formatDateTime(instant) {
  return instant ? new Date(instant).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : ''
}

export function dayLabels(days = []) {
  return DAYS.filter((d) => days.includes(d.id)).map((d) => d.short).join(', ')
}

export function initials(name = '') {
  return name
    .replace(/^dr\.?\s+/i, '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

/** Today's date in the browser as yyyy-MM-dd. */
export function todayIso(now = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
