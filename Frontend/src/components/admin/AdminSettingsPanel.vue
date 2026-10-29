<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { getSiteSettings, saveSiteSettings } from '../../services/adminService'
import { errorMessage, fieldErrors } from '../../services/http'
import { applySiteSettings, site } from '../../config/siteSettings'
import { validatePhone } from '../../auth/validation'

const KEYS = ['contactPhone', 'contactEmail', 'contactAddress', 'facebookUrl', 'youtubeUrl', 'linkedinUrl', 'twitterUrl',
  'heroVideoUrl', 'announcementText']
const form = reactive({ ...Object.fromEntries(KEYS.map((k) => [k, ''])), announcementEnabled: false })
const errors = reactive(Object.fromEntries(KEYS.map((k) => [k, ''])))
const saved = ref(null)
const loading = ref(true)
const saving = ref(false)
const result = ref(null) // { ok, text }

const SOCIAL = [
  { key: 'facebookUrl', label: 'Facebook' },
  { key: 'youtubeUrl', label: 'YouTube' },
  { key: 'linkedinUrl', label: 'LinkedIn' },
  { key: 'twitterUrl', label: 'X (Twitter)' },
]

function fill(settings) {
  saved.value = settings
  for (const key of KEYS) errors[key] = ''
  if (settings.configured) {
    for (const key of KEYS) form[key] = settings[key] ?? ''
    form.announcementEnabled = settings.announcementEnabled
  } else {
    // Not saved yet: start from what the site shows today (the siteConfig.js defaults).
    Object.assign(form, {
      contactPhone: site.contact.phone, contactEmail: site.contact.email, contactAddress: site.contact.address,
      facebookUrl: site.social.facebook, youtubeUrl: site.social.youtube, linkedinUrl: site.social.linkedin,
      twitterUrl: site.social.twitter, heroVideoUrl: site.heroVideoUrl, announcementText: '', announcementEnabled: false,
    })
  }
}

const dirty = computed(() => {
  if (!saved.value) return false
  if (!saved.value.configured) return true
  return KEYS.some((k) => form[k].trim() !== (saved.value[k] ?? '')) || form.announcementEnabled !== saved.value.announcementEnabled
})

const LINK = /^https?:\/\/[^\s<>"']{3,300}$/
function validate() {
  errors.contactPhone = validatePhone(form.contactPhone)
  errors.contactEmail = form.contactEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.contactEmail.trim()) ? 'Enter a valid email address.' : ''
  for (const { key } of SOCIAL) errors[key] = form[key].trim() && !LINK.test(form[key].trim()) ? 'Enter a full link starting with https://' : ''
  errors.heroVideoUrl = form.heroVideoUrl.trim() && !/^https:\/\/(www\.|m\.)?(youtube\.com|youtu\.be)\/\S+$/.test(form.heroVideoUrl.trim())
    ? 'Use a YouTube link, like https://www.youtube.com/watch?v=…' : ''
  errors.announcementText = form.announcementEnabled && !form.announcementText.trim() ? 'Write the announcement, or switch it off.' : ''
  return KEYS.every((k) => !errors[k])
}

async function save() {
  result.value = null
  if (!validate()) return
  saving.value = true
  try {
    const payload = { ...Object.fromEntries(KEYS.map((k) => [k, form[k].trim()])), announcementEnabled: form.announcementEnabled }
    const settings = await saveSiteSettings(payload)
    fill(settings)
    applySiteSettings(settings) // the footer and banner on this page update right away
    result.value = { ok: true, text: 'Saved. Visitors see the changes on their next page load.' }
  } catch (err) {
    const serverErrors = fieldErrors(err)
    for (const key of KEYS) if (serverErrors[key]) errors[key] = serverErrors[key]
    result.value = { ok: false, text: Object.keys(serverErrors).length ? 'Please correct the highlighted fields.' : errorMessage(err) }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    fill(await getSiteSettings())
  } catch (err) {
    result.value = { ok: false, text: errorMessage(err) }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <p v-if="loading" class="muted">Loading website settings…</p>
  <form v-else-if="saved" class="settings" novalidate @submit.prevent="save">
    <p class="meta-line muted">
      <template v-if="saved.configured">Last saved {{ new Date(saved.updatedAt).toLocaleString() }} by {{ saved.updatedBy }}.</template>
      <template v-else>Not customised yet: the site shows its built-in defaults, filled in below.</template>
    </p>

    <div class="grid">
      <section class="card form-stack">
        <h3>Announcement banner</h3>
        <p class="muted small">A short notice shown at the top of every page, e.g. a free screening week.</p>
        <label class="switch">
          <input v-model="form.announcementEnabled" type="checkbox" />
          <span class="track" aria-hidden="true"></span>
          <span>{{ form.announcementEnabled ? 'Shown on the site' : 'Hidden' }}</span>
        </label>
        <div class="form-field">
          <label for="set-announcement">Text</label>
          <input id="set-announcement" v-model="form.announcementText" maxlength="200" :aria-invalid="!!errors.announcementText" />
          <p class="field-error" aria-live="polite">{{ errors.announcementText }}</p>
        </div>
        <div v-if="form.announcementEnabled && form.announcementText.trim()" class="preview" aria-hidden="true">
          <span class="dot"></span>{{ form.announcementText }}
        </div>
      </section>

      <section class="card form-stack">
        <h3>Contact details</h3>
        <p class="muted small">Shown in the footer and on the Contact page. Leave a field empty to hide it.</p>
        <div class="form-field">
          <label for="set-phone">Phone</label>
          <input id="set-phone" v-model="form.contactPhone" type="tel" maxlength="20" :aria-invalid="!!errors.contactPhone" />
          <p class="field-error" aria-live="polite">{{ errors.contactPhone }}</p>
        </div>
        <div class="form-field">
          <label for="set-email">Email</label>
          <input id="set-email" v-model="form.contactEmail" type="email" maxlength="254" :aria-invalid="!!errors.contactEmail" />
          <p class="field-error" aria-live="polite">{{ errors.contactEmail }}</p>
        </div>
        <div class="form-field">
          <label for="set-address">Address</label>
          <input id="set-address" v-model="form.contactAddress" maxlength="200" :aria-invalid="!!errors.contactAddress" />
          <p class="field-error" aria-live="polite">{{ errors.contactAddress }}</p>
        </div>
      </section>

      <section class="card form-stack">
        <h3>Social media</h3>
        <p class="muted small">Icons appear in the footer for the links you fill in.</p>
        <div v-for="item in SOCIAL" :key="item.key" class="form-field">
          <label :for="`set-${item.key}`">{{ item.label }}</label>
          <input :id="`set-${item.key}`" v-model="form[item.key]" type="url" maxlength="300" placeholder="https://"
            :aria-invalid="!!errors[item.key]" />
          <p class="field-error" aria-live="polite">{{ errors[item.key] }}</p>
        </div>
      </section>

      <section class="card form-stack">
        <h3>Home page video</h3>
        <p class="muted small">The YouTube video in the Home page hero. Leave empty to show the illustration instead.</p>
        <div class="form-field">
          <label for="set-video">YouTube link</label>
          <input id="set-video" v-model="form.heroVideoUrl" type="url" maxlength="250" placeholder="https://www.youtube.com/watch?v=…"
            :aria-invalid="!!errors.heroVideoUrl" />
          <p class="field-error" aria-live="polite">{{ errors.heroVideoUrl }}</p>
        </div>
      </section>
    </div>

    <div class="savebar">
      <div v-if="result" class="alert" :class="result.ok ? 'success' : 'error'" role="status">{{ result.text }}</div>
      <button type="button" class="btn btn-outline btn-sm" :disabled="!dirty || saving" @click="fill(saved)">Discard changes</button>
      <button type="submit" class="btn btn-primary btn-sm" :disabled="!dirty || saving" :aria-busy="saving">
        <span v-if="saving" class="spinner" aria-hidden="true"></span>
        {{ saving ? 'Saving…' : 'Save website settings' }}
      </button>
    </div>
  </form>
  <p v-else-if="result" class="alert error" role="alert">{{ result.text }}</p>
</template>

<style scoped>
h3 { margin: 0; font-size: 1.05rem; color: var(--purple-900); }
.small { font-size: 0.86rem; margin: 0 0 0.4rem; }
.meta-line { margin: 0 0 1rem; font-size: 0.88rem; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; align-items: start; }
.switch { display: inline-flex; align-items: center; gap: 0.6rem; margin-bottom: 0.6rem; font-weight: 600; color: var(--purple-900); cursor: pointer; }
.switch input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.track { position: relative; width: 42px; height: 24px; border-radius: 999px; background: var(--border); transition: background-color 0.2s; }
.track::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2); transition: transform 0.2s; }
.switch input:checked + .track { background: var(--pink-600); }
.switch input:checked + .track::after { transform: translateX(18px); }
.switch input:focus-visible + .track { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.preview {
  display: flex; align-items: center; justify-content: center; gap: 0.6rem; padding: 0.6rem 1rem; border-radius: 10px;
  background: linear-gradient(90deg, var(--purple-900), #6b3fb5 60%, var(--pink-600)); color: #fff; font-size: 0.88rem; font-weight: 500;
}
.preview .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--pink-300); }
.savebar {
  position: sticky; bottom: 0; z-index: 3; display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 0.75rem;
  margin-top: 1rem; padding: 0.9rem 1rem; border: 1px solid var(--border); border-radius: 16px;
  background: rgba(255, 255, 255, 0.95); backdrop-filter: blur(8px); box-shadow: 0 -6px 20px rgba(42, 22, 80, 0.06);
}
.savebar .alert { flex: 1; margin: 0; padding: 0.55rem 0.8rem; font-size: 0.88rem; }
@media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
@media (max-width: 520px) { .savebar .btn { flex: 1; justify-content: center; } }
</style>
