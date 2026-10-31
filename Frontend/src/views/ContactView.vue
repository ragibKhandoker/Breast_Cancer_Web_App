<script setup>
import PageHeader from '../components/common/PageHeader.vue'
import AppIcon from '../components/common/AppIcon.vue'
import ContactForm from '../components/contact/ContactForm.vue'
import { site } from '../config/siteSettings'
import radiologistsPhoto from '../assets/images/contact-radiologists.webp'
</script>

<template>
  <div class="contact-page">
    <PageHeader
      eyebrow="Contact"
      title="Get in touch"
      subtitle="Questions about the project, feedback, or a problem with the site? Send us a message."
    />

    <section class="container section">
      <div v-reveal class="contact-card">
        <!-- Photo side: two radiologists reading a mammogram, with the contact details over it. -->
        <aside class="photo-side">
          <img :src="radiologistsPhoto" alt="Two radiologists reviewing a mammogram on a monitor" class="photo" />
          <div class="photo-overlay">
            <span class="pill"><span class="dot" aria-hidden="true"></span> We usually reply within 1–2 days</span>
            <div class="photo-copy">
              <h2>We're here to help</h2>
              <p>Real people read every message. Tell us what you need and we'll get back to you.</p>
            </div>
            <ul class="details">
              <li v-if="site.contact.email">
                <span class="icon"><AppIcon name="mail" :size="18" /></span>
                <div>
                  <span class="label">Email</span>
                  <a :href="`mailto:${site.contact.email}`">{{ site.contact.email }}</a>
                </div>
              </li>
              <li v-if="site.contact.phone">
                <span class="icon"><AppIcon name="phone" :size="18" /></span>
                <div>
                  <span class="label">Phone</span>
                  <a :href="`tel:${site.contact.phone.replace(/\s+/g, '')}`">{{ site.contact.phone }}</a>
                </div>
              </li>
              <li v-if="site.contact.address">
                <span class="icon"><AppIcon name="map-pin" :size="18" /></span>
                <div>
                  <span class="label">Address</span>
                  <span>{{ site.contact.address }}</span>
                </div>
              </li>
            </ul>
          </div>
        </aside>

        <!-- Form side -->
        <div class="form-side">
          <header class="form-head">
            <span class="head-icon"><AppIcon name="mail" :size="22" /></span>
            <div>
              <h2>Send a message</h2>
              <p class="muted">Fill in the form and we'll reply to your email.</p>
            </div>
          </header>
          <ContactForm />
          <p class="note">
            <AppIcon name="alert-circle" :size="18" />
            <span>This form is not for medical emergencies or diagnosis. If you have symptoms, please see a doctor.</span>
          </p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.contact-page { background: linear-gradient(180deg, transparent 0%, var(--pink-50) 40%, #f6f0ff 100%); }
.section { padding-top: 2rem; padding-bottom: 3.5rem; }

.contact-card {
  display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  background: #fff; border: 1px solid var(--border); border-radius: 28px; overflow: hidden;
  box-shadow: 0 24px 60px rgba(42, 22, 80, 0.14);
}

/* Photo side */
.photo-side { position: relative; min-height: 560px; }
.photo { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: 42% center; }
.photo-overlay {
  position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: space-between;
  gap: 1.5rem; padding: 2rem; color: #fff;
  background:
    linear-gradient(180deg, rgba(42, 22, 80, 0.15) 0%, rgba(42, 22, 80, 0) 30%, rgba(120, 10, 70, 0.7) 62%, rgba(42, 22, 80, 0.95) 100%);
}
.pill {
  align-self: flex-start; display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.4rem 0.9rem; border-radius: 999px;
  background: rgba(255, 255, 255, 0.16); border: 1px solid rgba(255, 255, 255, 0.35); backdrop-filter: blur(8px);
  font-size: 0.82rem; font-weight: 500;
}
.dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.3); }
.photo-copy { margin-top: auto; }
.photo-copy h2 { margin: 0 0 0.4rem; font-size: 1.9rem; line-height: 1.2; color: #fff; }
.photo-copy p { margin: 0; color: rgba(255, 255, 255, 0.85); line-height: 1.6; max-width: 34ch; }

.details { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.6rem; }
.details li {
  display: flex; align-items: center; gap: 0.8rem; padding: 0.7rem 0.9rem; border-radius: 16px;
  background: rgba(255, 255, 255, 0.12); border: 1px solid rgba(255, 255, 255, 0.22); backdrop-filter: blur(10px);
  overflow-wrap: anywhere; transition: background-color 0.2s ease, transform 0.2s ease;
}
.details li:hover { background: rgba(255, 255, 255, 0.2); transform: translateX(3px); }
.details .icon {
  flex: none; display: grid; place-items: center; width: 38px; height: 38px; border-radius: 12px;
  background: linear-gradient(135deg, var(--pink-500), #b5179e); color: #fff;
}
.details .label { display: block; font-size: 0.7rem; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: rgba(255, 255, 255, 0.7); }
.details a { color: #fff; font-weight: 500; }
.details a:hover { text-decoration: underline; }

/* Form side */
.form-side { padding: 2.5rem; }
.form-head { display: flex; gap: 0.9rem; align-items: center; margin-bottom: 1.5rem; padding-bottom: 1.25rem; border-bottom: 1px solid var(--border); }
.head-icon {
  flex: none; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 15px;
  background: linear-gradient(135deg, var(--pink-600), #b5179e); color: #fff; box-shadow: 0 8px 18px rgba(216, 27, 106, 0.28);
}
.form-head h2 { margin: 0; color: var(--purple-900); font-size: 1.4rem; }
.form-head p { margin: 0.15rem 0 0; font-size: 0.9rem; }
.form-side :deep(input), .form-side :deep(textarea) { background: #fffafc; }
.form-side :deep(input:focus), .form-side :deep(textarea:focus) { background: #fff; }
.form-side :deep(.submit) { background: linear-gradient(120deg, var(--pink-600), #b5179e); border-color: transparent; }

.note {
  display: flex; gap: 0.6rem; align-items: flex-start; margin: 1.5rem 0 0; padding: 0.9rem 1rem; border-radius: 14px;
  background: var(--pink-50); border: 1px solid var(--border); font-size: 0.88rem; color: var(--muted); line-height: 1.55;
}
.note :deep(svg) { flex: none; margin-top: 0.1rem; color: var(--pink-600); }

@media (max-width: 900px) {
  .contact-card { grid-template-columns: 1fr; }
  .photo-side { min-height: 460px; }
  .form-side { padding: 1.75rem; }
}
@media (max-width: 480px) {
  .photo-side { min-height: 420px; }
  .photo-overlay { padding: 1.25rem; }
  .photo-copy h2 { font-size: 1.5rem; }
  .form-side { padding: 1.25rem; }
}
</style>
