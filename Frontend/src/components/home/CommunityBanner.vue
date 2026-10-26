<script setup>
import AppIcon from '../common/AppIcon.vue'
import person1 from '../../assets/images/avatars/person-1.jpg'
import person2 from '../../assets/images/avatars/person-2.jpg'
import person3 from '../../assets/images/avatars/person-3.jpg'
import person4 from '../../assets/images/avatars/person-4.jpg'
import person5 from '../../assets/images/avatars/person-5.jpg'

// Faces cropped from the site's photos: doctors, a radiologist and patients.
const people = [person1, person2, person3, person4, person5]
</script>

<template>
  <section class="container">
    <div v-reveal class="banner">
      <div class="content">
        <h2>Together We Can Make a Difference</h2>
        <p class="lead">Spread awareness. Support patients. Save lives.</p>

        <div class="people">
          <div class="avatars">
            <img v-for="(src, i) in people" :key="i" :src="src" alt="" class="avatar" loading="lazy" />
          </div>
          <span class="caption">Doctors, patients and families supporting each other</span>
        </div>

        <RouterLink to="/support" class="btn btn-light">Get Involved <AppIcon name="arrow-right" :size="18" /></RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.banner {
  position: relative; overflow: hidden; min-height: 300px;
  display: flex; align-items: center;
  border-radius: 28px; box-shadow: 0 24px 60px rgba(42, 22, 80, 0.25);
  /* Photo on the right, a deep rose gradient on the left for the text. */
  background:
    linear-gradient(90deg, rgba(110, 14, 62, 0.96) 0%, rgba(160, 20, 88, 0.88) 38%, rgba(190, 24, 100, 0.35) 65%, rgba(190, 24, 100, 0) 85%),
    url('../../assets/images/prediction-bg.jpg') right 30% / cover no-repeat;
}
.content { padding: 2.75rem 3rem; max-width: 560px; color: #fff; }

h2 { margin: 0; font-size: clamp(1.7rem, 3.2vw, 2.4rem); line-height: 1.15; letter-spacing: -0.3px; }
.lead { margin: 0.75rem 0 1.5rem; font-size: 1.1rem; color: #fde3ef; }

.people { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.75rem; }
.avatars { display: flex; flex: none; }
.avatar {
  width: 46px; height: 46px; object-fit: cover; border-radius: 50%;
  border: 3px solid #fff; box-shadow: 0 4px 10px rgba(0, 0, 0, 0.25);
}
.avatar + .avatar { margin-left: -12px; }
.caption { font-size: 0.9rem; line-height: 1.4; color: #fde3ef; }

.btn-light { background: #fff; border-color: #fff; color: var(--pink-600); }
.btn-light:hover { box-shadow: 0 8px 20px rgba(0, 0, 0, 0.25); }

@media (max-width: 700px) {
  .banner {
    background:
      linear-gradient(180deg, rgba(110, 14, 62, 0.75) 0%, rgba(140, 18, 78, 0.95) 55%),
      url('../../assets/images/prediction-bg.jpg') center 20% / cover no-repeat;
  }
  .content { padding: 2rem 1.5rem; text-align: center; }
  .people { flex-direction: column; }
}
</style>
