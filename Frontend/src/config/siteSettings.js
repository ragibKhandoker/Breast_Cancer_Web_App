import { reactive } from 'vue'
import { http } from '../services/http'
import { CONTACT, SOCIAL_LINKS, HERO_VIDEO_URL } from './siteConfig'

/**
 * Website details shown on every page (footer contacts, social links, Home video, announcement). They start
 * as the defaults in siteConfig.js; once an administrator saves settings in the Admin Console, the saved values
 * replace them everywhere, including blanks (a blank social link hides its icon).
 */
export const site = reactive(fromDefaults())

function fromDefaults() {
  return {
    contact: { ...CONTACT },
    social: { ...SOCIAL_LINKS },
    heroVideoUrl: HERO_VIDEO_URL,
    announcement: { text: '', enabled: false },
  }
}

/** Applies a /api/site-settings response. */
export function applySiteSettings(settings) {
  if (!settings?.configured) {
    Object.assign(site, fromDefaults())
    return
  }
  Object.assign(site, {
    contact: { phone: settings.contactPhone, email: settings.contactEmail, address: settings.contactAddress },
    social: {
      facebook: settings.facebookUrl,
      youtube: settings.youtubeUrl,
      linkedin: settings.linkedinUrl,
      twitter: settings.twitterUrl,
    },
    heroVideoUrl: settings.heroVideoUrl,
    announcement: { text: settings.announcementText, enabled: settings.announcementEnabled && !!settings.announcementText },
  })
}

/** Loads the saved settings once at start-up. If the backend is unreachable, the defaults stay. */
export async function loadSiteSettings() {
  try {
    const { data } = await http.get('/api/site-settings')
    applySiteSettings(data)
  } catch {
    // Keep the defaults.
  }
}
