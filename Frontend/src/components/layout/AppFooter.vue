<script setup>
import AppIcon from '../common/AppIcon.vue'
import BrandLogo from './BrandLogo.vue'
import { CONTACT, SOCIAL_LINKS } from '../../config/siteConfig'
import { APP_ROUTES } from '../../config/appRoutes'

const quickLinks = [
  { to: '/', label: 'Home' },
  { to: APP_ROUTES.analysis, label: 'Start Analysis' },
  { to: '/how-it-works', label: 'How It Works' },
  { to: '/about', label: 'About' },
  { to: '/faq', label: 'FAQ' },
  { to: '/contact', label: 'Contact' },
]
const legalLinks = [
  { to: '/privacy', label: 'Privacy Policy' },
  { to: '/terms', label: 'Terms of Service' },
]
const contacts = [
  { icon: 'phone', label: 'Phone', value: CONTACT.phone, href: `tel:${CONTACT.phone.replace(/[^\d+]/g, '')}` },
  { icon: 'mail', label: 'Email', value: CONTACT.email, href: `mailto:${CONTACT.email}` },
  { icon: 'map-pin', label: 'Address', value: CONTACT.address },
]
const socials = Object.entries(SOCIAL_LINKS).filter(([, url]) => url)
const year = new Date().getFullYear()
</script>

<template>
  <footer class="footer">
    <div class="container columns">
      <div class="brand-col">
        <BrandLogo dark />
        <p class="about">
          AI decision support for mammography screening, built to help with early detection of breast cancer. Every
          result should be reviewed by a qualified physician.
        </p>
        <div v-if="socials.length" class="socials">
          <a v-for="[name, url] in socials" :key="name" :href="url" target="_blank" rel="noopener" :aria-label="name">
            <AppIcon :name="name" :size="18" />
          </a>
        </div>
      </div>

      <div>
        <h3 class="heading">Quick Links</h3>
        <ul class="links">
          <li v-for="link in quickLinks" :key="link.to"><RouterLink :to="link.to">{{ link.label }}</RouterLink></li>
        </ul>
      </div>

      <div>
        <h3 class="heading"><AppIcon name="languages" :size="16" /> Language</h3>
        <ul class="links">
          <li><span class="current">English</span></li>
          <li><span class="soon" title="Coming soon">বাংলা <small>soon</small></span></li>
        </ul>
        <h3 class="heading legal">Legal</h3>
        <ul class="links">
          <li v-for="link in legalLinks" :key="link.to"><RouterLink :to="link.to">{{ link.label }}</RouterLink></li>
        </ul>
      </div>

      <div>
        <h3 class="heading">Get in Touch</h3>
        <ul class="contacts">
          <li v-for="c in contacts" :key="c.label">
            <span class="contact-icon"><AppIcon :name="c.icon" :size="18" /></span>
            <span>
              <span class="contact-label">{{ c.label }}</span>
              <a v-if="c.href" :href="c.href" class="contact-value">{{ c.value }}</a>
              <span v-else class="contact-value">{{ c.value }}</span>
            </span>
          </li>
        </ul>
      </div>
    </div>

    <div class="container bottom">
      <span>All Rights Reserved © {{ year }} BreastCare</span>
      <span class="bottom-links">
        <RouterLink to="/privacy">Privacy Policy</RouterLink>
        <span aria-hidden="true">·</span>
        <RouterLink to="/terms">Terms of Service</RouterLink>
      </span>
    </div>
    <p class="container disclaimer">
      For research and education only. BreastCare is not a medical device and does not replace diagnosis by a doctor.
    </p>
  </footer>
</template>

<style scoped>
.footer {
  position: relative; margin-top: 3rem; color: #d9d1e8;
  background:
    radial-gradient(circle at 100% 0%, rgba(216, 27, 106, 0.18) 0, transparent 40%),
    radial-gradient(circle at 0% 100%, rgba(216, 27, 106, 0.12) 0, transparent 35%),
    #170d1c;
  border-top: 3px solid var(--pink-600);
}
.columns {
  display: grid; grid-template-columns: 1.4fr 1fr 1fr 1.3fr; gap: 2.5rem;
  padding-top: 3.5rem; padding-bottom: 2.5rem;
}

.about { margin: 1.25rem 0; line-height: 1.75; max-width: 360px; color: #cfc6e0; }
.socials { display: flex; gap: 0.6rem; }
.socials a {
  display: grid; place-items: center; width: 38px; height: 38px; border-radius: 10px;
  background: rgba(216, 27, 106, 0.15); color: var(--pink-300);
}
.socials a:hover { background: var(--pink-600); color: #fff; }

.heading {
  display: flex; align-items: center; gap: 0.4rem; margin: 0 0 1.4rem; padding-bottom: 0.7rem;
  position: relative; color: #fff; font-size: 1.05rem;
}
.heading::after { content: ''; position: absolute; left: 0; bottom: 0; width: 32px; height: 3px; border-radius: 2px; background: var(--pink-600); }
.legal { margin-top: 1.75rem; }

.links { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.85rem; }
.links a { color: #d9d1e8; transition: color 0.15s, padding-left 0.15s; }
.links a:hover { color: var(--pink-300); padding-left: 4px; }
.current { color: #fff; font-weight: 500; }
.soon { color: #9d93b3; }
.soon small { margin-left: 0.3rem; padding: 0 6px; border-radius: 4px; background: rgba(255, 255, 255, 0.08); font-size: 0.7rem; }

.contacts { list-style: none; margin: 0; padding: 0; display: grid; gap: 1.25rem; }
.contacts li { display: flex; align-items: center; gap: 0.9rem; }
.contact-icon {
  flex: none; display: grid; place-items: center; width: 46px; height: 46px; border-radius: 12px;
  background: rgba(216, 27, 106, 0.15); border: 1px solid rgba(216, 27, 106, 0.35); color: var(--pink-300);
}
.contact-label { display: block; font-size: 0.72rem; letter-spacing: 1px; text-transform: uppercase; color: #9d93b3; }
.contact-value { color: #fff; font-weight: 500; overflow-wrap: anywhere; }
a.contact-value:hover { color: var(--pink-300); }

.bottom {
  display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.75rem;
  padding-top: 1.4rem; padding-bottom: 1rem; border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 0.9rem; color: #9d93b3;
}
.bottom-links { display: flex; gap: 0.6rem; color: #d9d1e8; }
.bottom-links a:hover { color: var(--pink-300); }
.disclaimer { margin: 0 auto; padding-bottom: 1.5rem; font-size: 0.78rem; color: #7f7594; }

@media (max-width: 960px) { .columns { grid-template-columns: 1fr 1fr; } }
@media (max-width: 560px) { .columns { grid-template-columns: 1fr; gap: 2rem; } .bottom { justify-content: center; text-align: center; } }
</style>
