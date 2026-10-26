import axios from 'axios'

// Shared API client. An empty base URL uses the Vite proxy in development and the same origin when Spring Boot
// serves the built site; set VITE_API_URL at build time for a backend on another host.
export const http = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '', timeout: 30000 })

export function errorMessage(err) {
  return err.response?.data?.detail ?? err.message ?? 'Something went wrong'
}
