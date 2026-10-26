<script setup>
import { reactive, ref, computed, nextTick } from 'vue'
import { sendContactMessage } from '../../services/contactService'

// Same limits as the backend's ContactRequest, so both sides agree on what is valid.
const LIMITS = { name: [2, 100], subject: [3, 150], message: [10, 2000] }
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const fields = reactive({ name: '', email: '', subject: '', message: '' })
const errors = reactive({ name: '', email: '', subject: '', message: '' })
const touched = reactive({ name: false, email: false, subject: false, message: false })

const status = ref('idle') // idle | sending | success | error
const feedback = ref('')
const formEl = ref(null)

const sending = computed(() => status.value === 'sending')
const messageLength = computed(() => fields.message.trim().length)

function validateField(name) {
  const value = fields[name].trim()
  const label = { name: 'Name', email: 'Email', subject: 'Subject', message: 'Message' }[name]
  let error = ''
  if (!value) {
    error = `${label} is required.`
  } else if (name === 'email') {
    if (value.length > 254 || !EMAIL_PATTERN.test(value)) error = 'Enter a valid email address, like name@example.com.'
  } else {
    const [min, max] = LIMITS[name]
    if (value.length < min) error = `${label} must be at least ${min} characters.`
    else if (value.length > max) error = `${label} must be ${max} characters or fewer.`
  }
  errors[name] = error
  return !error
}

function onBlur(name) {
  touched[name] = true
  validateField(name)
}

function onInput(name) {
  if (touched[name]) validateField(name)
  if (status.value === 'success') status.value = 'idle'
}

async function focusFirstError() {
  await nextTick()
  formEl.value?.querySelector('[aria-invalid="true"]')?.focus()
}

async function submit() {
  if (sending.value) return
  Object.keys(touched).forEach((key) => (touched[key] = true))
  const valid = Object.keys(fields).map(validateField).every(Boolean)
  if (!valid) {
    status.value = 'idle'
    return focusFirstError()
  }

  status.value = 'sending'
  feedback.value = ''
  try {
    const payload = Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, value.trim()]))
    const response = await sendContactMessage(payload)
    status.value = 'success'
    feedback.value = response.message
    Object.keys(fields).forEach((key) => {
      fields[key] = ''
      touched[key] = false
    })
  } catch (err) {
    // Keep what the visitor typed; show the backend's per-field messages where it sent them.
    status.value = 'error'
    feedback.value = err.message
    for (const [key, message] of Object.entries(err.fieldErrors ?? {})) {
      if (key in errors) errors[key] = message
    }
    if (Object.keys(err.fieldErrors ?? {}).length) focusFirstError()
  }
}
</script>

<template>
  <form ref="formEl" class="contact-form" novalidate @submit.prevent="submit">
    <div class="row">
      <div class="field">
        <label for="contact-name">Name</label>
        <input
          id="contact-name"
          v-model="fields.name"
          type="text"
          autocomplete="name"
          maxlength="100"
          required
          :aria-invalid="!!errors.name"
          aria-describedby="contact-name-error"
          @blur="onBlur('name')"
          @input="onInput('name')"
        />
        <p id="contact-name-error" class="field-error" aria-live="polite">{{ errors.name }}</p>
      </div>

      <div class="field">
        <label for="contact-email">Email</label>
        <input
          id="contact-email"
          v-model="fields.email"
          type="email"
          autocomplete="email"
          inputmode="email"
          maxlength="254"
          required
          :aria-invalid="!!errors.email"
          aria-describedby="contact-email-error"
          @blur="onBlur('email')"
          @input="onInput('email')"
        />
        <p id="contact-email-error" class="field-error" aria-live="polite">{{ errors.email }}</p>
      </div>
    </div>

    <div class="field">
      <label for="contact-subject">Subject</label>
      <input
        id="contact-subject"
        v-model="fields.subject"
        type="text"
        maxlength="150"
        required
        :aria-invalid="!!errors.subject"
        aria-describedby="contact-subject-error"
        @blur="onBlur('subject')"
        @input="onInput('subject')"
      />
      <p id="contact-subject-error" class="field-error" aria-live="polite">{{ errors.subject }}</p>
    </div>

    <div class="field">
      <label for="contact-message">Message</label>
      <textarea
        id="contact-message"
        v-model="fields.message"
        rows="6"
        maxlength="2000"
        required
        :aria-invalid="!!errors.message"
        aria-describedby="contact-message-error contact-message-count"
        @blur="onBlur('message')"
        @input="onInput('message')"
      ></textarea>
      <div class="field-footer">
        <p id="contact-message-error" class="field-error" aria-live="polite">{{ errors.message }}</p>
        <span id="contact-message-count" class="count" :class="{ short: messageLength > 0 && messageLength < 10 }">
          {{ messageLength }} / 2000
        </span>
      </div>
    </div>

    <div v-if="status === 'success'" class="alert success" role="status">{{ feedback }}</div>
    <div v-else-if="status === 'error'" class="alert error" role="alert">{{ feedback }}</div>

    <button type="submit" class="btn btn-primary submit" :disabled="sending" :aria-busy="sending">
      <span v-if="sending" class="spinner" aria-hidden="true"></span>
      {{ sending ? 'Sending…' : 'Send Message' }}
    </button>
  </form>
</template>

<style scoped>
.contact-form { display: grid; gap: 0.35rem; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.field { display: grid; gap: 0.4rem; }
label { font-weight: 600; color: var(--purple-900); font-size: 0.95rem; }

input, textarea {
  width: 100%; padding: 0.8rem 1rem; border: 1.5px solid var(--border); border-radius: 12px;
  background: #fff; color: var(--text); font: 400 1rem 'Poppins', sans-serif;
  transition: border-color 0.15s, box-shadow 0.15s;
}
textarea { resize: vertical; min-height: 140px; }
input:hover, textarea:hover { border-color: var(--pink-300); }
input:focus, textarea:focus { outline: none; border-color: var(--pink-600); box-shadow: 0 0 0 4px rgba(216, 27, 106, 0.15); }
input[aria-invalid='true'], textarea[aria-invalid='true'] { border-color: var(--high); }
input[aria-invalid='true']:focus, textarea[aria-invalid='true']:focus { box-shadow: 0 0 0 4px rgba(198, 40, 40, 0.15); }

.field-error { min-height: 1.25rem; margin: 0; color: var(--high); font-size: 0.85rem; }
.field-footer { display: flex; justify-content: space-between; gap: 1rem; }
.count { flex: none; font-size: 0.8rem; color: var(--muted); }
.count.short { color: var(--high); }

.alert { margin: 0.5rem 0; padding: 0.9rem 1rem; border-radius: 12px; font-weight: 500; line-height: 1.5; }
.alert.success { background: #e8f5e9; color: #1b5e20; border: 1px solid #a5d6a7; }
.alert.error { background: #fdecea; color: #8e1c1c; border: 1px solid #f5b5b0; }

.submit { justify-self: start; margin-top: 0.5rem; }
.submit:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 3px; }
.spinner {
  width: 16px; height: 16px; border: 2px solid rgba(255, 255, 255, 0.5); border-top-color: #fff;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 640px) {
  .row { grid-template-columns: 1fr; gap: 0.35rem; }
  .submit { justify-self: stretch; justify-content: center; }
}
</style>
