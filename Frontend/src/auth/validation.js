// Same rules as the backend's RegisterRequest / ValidationRules, so both sides agree on what is valid.
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
export const PASSWORD_RULES = [
  { test: (v) => v.length >= 8 && v.length <= 72, label: '8 to 72 characters' },
  { test: (v) => /[A-Z]/.test(v), label: 'An upper-case letter' },
  { test: (v) => /[a-z]/.test(v), label: 'A lower-case letter' },
  { test: (v) => /\d/.test(v), label: 'A number' },
]

export function validateFullName(value) {
  const name = value.trim()
  if (!name) return 'Full name is required.'
  if (name.length < 2) return 'Full name must be at least 2 characters.'
  if (name.length > 100) return 'Full name must be 100 characters or fewer.'
  return ''
}

export function validateEmail(value) {
  const email = value.trim()
  if (!email) return 'Email is required.'
  if (email.length > 254 || !EMAIL_PATTERN.test(email)) return 'Enter a valid email address, like name@example.com.'
  return ''
}

export function validatePassword(value) {
  if (!value) return 'Password is required.'
  if (PASSWORD_RULES.some((rule) => !rule.test(value))) {
    return 'Use 8 to 72 characters with an upper-case letter, a lower-case letter and a number.'
  }
  // BCrypt's limit is 72 bytes; accented letters and emoji take more than one byte each.
  if (new TextEncoder().encode(value).length > 72) return 'Password is too long.'
  return ''
}

export function validateConfirm(password, confirm) {
  if (!confirm) return 'Please confirm your password.'
  return password === confirm ? '' : 'Passwords do not match.'
}
