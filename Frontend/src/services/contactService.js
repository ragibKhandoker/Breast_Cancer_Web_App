import { http } from './http'

/**
 * Sends the Contact form to POST /api/contact.
 * Resolves with the backend's confirmation ({ id, message, receivedAt }).
 * Rejects with a ContactError whose `fieldErrors` holds the backend's per-field messages on a 400.
 */
export async function sendContactMessage({ name, email, subject, message }) {
  try {
    const { data } = await http.post('/api/contact', { name, email, subject, message })
    if (!data || typeof data.message !== 'string') {
      throw new ContactError('The server sent an unexpected response. Please try again later.')
    }
    return data
  } catch (err) {
    if (err instanceof ContactError) throw err
    throw toContactError(err)
  }
}

export class ContactError extends Error {
  constructor(message, fieldErrors = {}) {
    super(message)
    this.fieldErrors = fieldErrors
  }
}

function toContactError(err) {
  const response = err.response
  if (!response) {
    return new ContactError('Could not reach the server. Check your internet connection and try again.')
  }
  if (response.status === 400) {
    return new ContactError(response.data?.detail ?? 'Please check the form and try again.', response.data?.errors ?? {})
  }
  // Server-side failures (for example the database being unreachable) are not something the visitor can fix.
  return new ContactError('Sorry, your message could not be sent right now. Please try again in a few minutes.')
}
