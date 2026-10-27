import { describe, it, expect } from 'vitest'
import { validateConfirm, validateEmail, validateFullName, validatePassword } from '../src/auth/validation'

describe('registration rules (same as the backend)', () => {
  it('checks the full name', () => {
    expect(validateFullName('  ')).toMatch(/required/)
    expect(validateFullName('A')).toMatch(/at least 2/)
    expect(validateFullName('x'.repeat(101))).toMatch(/100/)
    expect(validateFullName(' Ayesha Rahman ')).toBe('')
  })

  it('checks the email', () => {
    expect(validateEmail('')).toMatch(/required/)
    expect(validateEmail('name@example')).toMatch(/valid email/)
    expect(validateEmail('no at.com')).toMatch(/valid email/)
    expect(validateEmail(' name@example.com ')).toBe('')
  })

  it('requires a strong password within BCrypt limits', () => {
    expect(validatePassword('')).toMatch(/required/)
    expect(validatePassword('Short1')).toMatch(/8 to 72/)
    expect(validatePassword('alllowercase1')).toMatch(/upper-case/)
    expect(validatePassword('ALLUPPERCASE1')).toMatch(/lower-case/)
    expect(validatePassword('NoNumbersHere')).toMatch(/number/)
    expect(validatePassword('Aa1' + 'é'.repeat(40))).toBe('Password is too long.')
    expect(validatePassword('Secret123')).toBe('')
  })

  it('requires the confirmation to match', () => {
    expect(validateConfirm('Secret123', '')).toMatch(/confirm/)
    expect(validateConfirm('Secret123', 'Secret124')).toBe('Passwords do not match.')
    expect(validateConfirm('Secret123', 'Secret123')).toBe('')
  })
})
