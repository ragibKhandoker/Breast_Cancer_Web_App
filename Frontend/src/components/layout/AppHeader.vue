<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import BrandLogo from './BrandLogo.vue'

const links = [
  { to: '/', label: 'Home' },
  { to: '/prediction', label: 'Prediction' },
  { to: '/learn', label: 'Learn' },
  { to: '/resources', label: 'Resources' },
  { to: '/support', label: 'Support' },
  { to: '/about', label: 'About' },
]

const menuOpen = ref(false)
const route = useRoute()
watch(() => route.path, () => (menuOpen.value = false))
</script>

<template>
  <header class="header">
    <div class="container header-inner">
      <BrandLogo />

      <nav class="nav" :class="{ open: menuOpen }">
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-link">
          {{ link.label }}
        </RouterLink>
      </nav>

      <div class="actions">
        <button class="icon-btn" aria-label="Search"><AppIcon name="search" :size="20" /></button>
        <RouterLink to="/login" class="btn btn-primary login">Login</RouterLink>
        <button class="icon-btn menu-btn" :aria-label="menuOpen ? 'Close menu' : 'Open menu'" @click="menuOpen = !menuOpen">
          <AppIcon :name="menuOpen ? 'close' : 'menu'" />
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header { position: sticky; top: 0; z-index: 10; background: rgba(255, 244, 249, 0.92); backdrop-filter: blur(8px); }
.header-inner { display: flex; align-items: center; justify-content: space-between; gap: 1rem; height: 76px; }

.nav { display: flex; gap: 0.35rem; }
.nav-link {
  padding: 0.4rem 1rem; border-radius: 999px; border: 2px solid transparent;
  color: var(--purple-900); font-weight: 500; font-size: 0.95rem;
}
.nav-link:hover { color: var(--pink-600); }
.nav-link.router-link-exact-active { border-color: var(--pink-600); color: var(--pink-600); background: #fff; }

.actions { display: flex; align-items: center; gap: 0.75rem; }
.icon-btn { background: none; border: 0; color: var(--purple-900); cursor: pointer; padding: 6px; }
.login { padding: 0.55rem 1.5rem; font-size: 0.95rem; }
.menu-btn { display: none; }

@media (max-width: 900px) {
  .menu-btn { display: block; }
  .nav {
    display: none; position: absolute; top: 76px; left: 0; right: 0;
    flex-direction: column; padding: 1rem 20px; background: #fff; box-shadow: var(--shadow);
  }
  .nav.open { display: flex; }
}
@media (max-width: 480px) {
  .login { display: none; }
}
</style>
