import axios from 'axios'

// Shared API client. An empty base URL uses the Vite proxy in development and the same origin when Spring Boot
// serves the built site; set VITE_API_URL at build time for a backend on another host.
//
// Login state is an HttpOnly cookie the browser sends by itself; scripts never see the token. Requests that
// change data must also carry the CSRF token: axios copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header
// (its defaults match Spring Security), and the interceptor below fetches that cookie first if it is missing.
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '',
  timeout: 30000,
  withCredentials: true,
})

const SAFE_METHODS = ['get', 'head', 'options']
const CSRF_COOKIE = 'XSRF-TOKEN'

function hasCookie(name) {
  return document.cookie.split(';').some((part) => part.trim().startsWith(`${name}=`))
}

let csrfRequest = null
export function ensureCsrfToken() {
  csrfRequest ??= http.get('/api/auth/csrf').finally(() => (csrfRequest = null))
  return csrfRequest
}

http.interceptors.request.use(async (config) => {
  if (!SAFE_METHODS.includes((config.method ?? 'get').toLowerCase()) && !hasCookie(CSRF_COOKIE)) {
    await ensureCsrfToken()
  }
  return config
})

// Called when the server answers 401 to a signed-in request, which means the login expired or was revoked.
let onUnauthorized = () => {}
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

http.interceptors.response.use(undefined, async (err) => {
  const { config, response } = err
  // A stale CSRF token: get a fresh one and try once more.
  if (response?.status === 403 && response.data?.code === 'csrf_invalid' && config && !config._csrfRetried) {
    config._csrfRetried = true
    await ensureCsrfToken()
    return http.request(config)
  }
  // The auth endpoints report their own 401s (wrong password, not signed in yet).
  if (response?.status === 401 && !config?.url?.startsWith('/api/auth/')) {
    onUnauthorized()
  }
  return Promise.reject(err)
})

export function errorMessage(err) {
  if (!err.response) return 'Could not reach the server. Check your connection and try again.'
  return err.response.data?.detail ?? err.message ?? 'Something went wrong'
}

/** Per-field messages from a 400/409 problem response, or {}. */
export function fieldErrors(err) {
  return err.response?.data?.errors ?? {}
}
