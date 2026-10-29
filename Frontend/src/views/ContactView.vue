<script setup>
import PageHeader from '../components/common/PageHeader.vue'
import AppIcon from '../components/common/AppIcon.vue'
import ContactForm from '../components/contact/ContactForm.vue'
import { site } from '../config/siteSettings'
</script>

<template>
  <PageHeader
    eyebrow="Contact"
    title="Get in touch"
    subtitle="Questions about the project, feedback, or a problem with the site? Send us a message."
  />

  <section class="container section layout">
    <div class="form-card">
      <h2>Send a message</h2>
      <ContactForm />
    </div>

    <aside class="info">
      <h2>Contact details</h2>
      <ul>
        <li v-if="site.contact.email">
          <span class="icon"><AppIcon name="mail" :size="18" /></span>
          <a :href="`mailto:${site.contact.email}`">{{ site.contact.email }}</a>
        </li>
        <li v-if="site.contact.phone">
          <span class="icon"><AppIcon name="phone" :size="18" /></span>
          <span>{{ site.contact.phone }}</span>
        </li>
        <li v-if="site.contact.address">
          <span class="icon"><AppIcon name="map-pin" :size="18" /></span>
          <span>{{ site.contact.address }}</span>
        </li>
      </ul>
      <p class="note">
        This form is not for medical emergencies or diagnosis. If you have symptoms, please see a doctor.
      </p>
    </aside>
  </section>
</template>

<style scoped>
.section { padding-top: 2rem; padding-bottom: 1rem; }
.layout { display: grid; grid-template-columns: 1.6fr 1fr; gap: 1.5rem; align-items: start; }
h2 { margin: 0 0 1.25rem; color: var(--purple-900); font-size: 1.3rem; }

.form-card, .info { padding: 2rem; background: #fff; border: 1px solid var(--border); border-radius: 22px; box-shadow: var(--shadow); }
.info ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 1rem; }
.info li { display: flex; align-items: center; gap: 0.8rem; overflow-wrap: anywhere; }
.info a { color: var(--pink-600); font-weight: 500; }
.icon { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 12px; background: var(--pink-100); color: var(--pink-600); }
.note { margin: 1.5rem 0 0; padding-top: 1.25rem; border-top: 1px solid var(--border); font-size: 0.9rem; color: var(--muted); line-height: 1.6; }

@media (max-width: 860px) {
  .layout { grid-template-columns: 1fr; }
  .form-card, .info { padding: 1.5rem; }
}
</style>
