import { describe, it, expect } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import { routes } from '../src/router'
import { homeFor, safeRedirect } from '../src/auth/roles'

const router = createRouter({ history: createMemoryHistory(), routes })
const resolve = (to) => router.resolve(to)

describe('homeFor', () => {
  it('sends each role to its own dashboard', () => {
    expect(homeFor('PATIENT')).toBe('/dashboard')
    expect(homeFor('DOCTOR')).toBe('/doctor')
    expect(homeFor('ADMIN')).toBe('/admin')
    expect(homeFor(undefined)).toBe('/')
  })
})

describe('safeRedirect', () => {
  it('returns to the requested page when the role may open it', () => {
    expect(safeRedirect('/history', 'PATIENT', resolve)).toBe('/history')
    expect(safeRedirect('/reports/abc', 'DOCTOR', resolve)).toBe('/reports/abc')
    expect(safeRedirect('/faq', 'ADMIN', resolve)).toBe('/faq')
  })

  it('falls back to the dashboard for pages the role may not open', () => {
    expect(safeRedirect('/admin', 'PATIENT', resolve)).toBe('/dashboard')
    expect(safeRedirect('/prediction', 'DOCTOR', resolve)).toBe('/doctor')
    expect(safeRedirect('/login', 'PATIENT', resolve)).toBe('/dashboard')
    expect(safeRedirect('/no-such-page', 'ADMIN', resolve)).toBe('/admin')
  })

  it('never redirects off the site', () => {
    for (const target of ['https://evil.example', '//evil.example', '/\\evil.example', 'javascript:alert(1)', undefined, ['/x']]) {
      expect(safeRedirect(target, 'PATIENT', resolve)).toBe('/dashboard')
    }
  })
})
