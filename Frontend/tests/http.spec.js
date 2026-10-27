import { describe, it, expect, vi, beforeEach } from 'vitest'
import { http, setUnauthorizedHandler } from '../src/services/http'

// Replaces the network: each request is recorded and answered by `respond`.
let calls
let respond
function fakeAdapter(config) {
  calls.push({ method: config.method, url: config.url })
  const { status = 200, data = {} } = respond(config) ?? {}
  if (status >= 400) {
    const error = new Error(`HTTP ${status}`)
    error.config = config
    error.response = { status, data, config, headers: {} }
    return Promise.reject(error)
  }
  return Promise.resolve({ status, data, config, headers: {}, statusText: 'OK' })
}

function clearCookies() {
  for (const part of document.cookie.split(';')) {
    const name = part.split('=')[0].trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

beforeEach(() => {
  calls = []
  respond = () => ({})
  http.defaults.adapter = fakeAdapter
  clearCookies()
  setUnauthorizedHandler(() => {})
})

describe('API client', () => {
  it('fetches a CSRF token before the first state-changing request', async () => {
    respond = (config) => {
      if (config.url === '/api/auth/csrf') document.cookie = 'XSRF-TOKEN=abc123; path=/'
      return {}
    }
    await http.post('/api/contact', {})
    // axios itself then copies the cookie into the X-XSRF-TOKEN header (in its real adapter, not this fake one).
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual(['get /api/auth/csrf', 'post /api/contact'])

    await http.post('/api/contact', {})
    expect(calls).toHaveLength(3) // token already present: no second fetch
  })

  it('does not fetch a CSRF token for reads', async () => {
    await http.get('/api/predictions')
    expect(calls.map((c) => c.url)).toEqual(['/api/predictions'])
  })

  it('retries once with a fresh token when the CSRF token is rejected', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/'
    let attempts = 0
    respond = (config) => {
      if (config.url === '/api/auth/csrf') return {}
      attempts++
      return attempts === 1 ? { status: 403, data: { code: 'csrf_invalid' } } : { status: 204 }
    }
    await http.post('/api/auth/logout')
    expect(attempts).toBe(2)
  })

  it('does not retry other 403 errors', async () => {
    document.cookie = 'XSRF-TOKEN=ok; path=/'
    respond = () => ({ status: 403, data: { code: 'forbidden' } })
    await expect(http.post('/api/admin/users', {})).rejects.toMatchObject({ response: { status: 403 } })
    expect(calls).toHaveLength(1)
  })

  it('reports an expired session on 401, but not for the login call itself', async () => {
    const expired = vi.fn()
    setUnauthorizedHandler(expired)
    document.cookie = 'XSRF-TOKEN=ok; path=/'
    respond = () => ({ status: 401, data: { detail: 'Please log in to continue.' } })

    await expect(http.get('/api/predictions')).rejects.toBeTruthy()
    expect(expired).toHaveBeenCalledOnce()

    await expect(http.post('/api/auth/login', {})).rejects.toBeTruthy()
    await expect(http.get('/api/auth/me')).rejects.toBeTruthy()
    expect(expired).toHaveBeenCalledOnce()
  })
})
