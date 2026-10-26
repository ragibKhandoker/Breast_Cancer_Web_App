<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import BrandLogo from './BrandLogo.vue'
import { APP_ROUTES } from '../../config/appRoutes'

const links = [
  { to: '/', label: 'Home' },
  { to: APP_ROUTES.analysis, label: 'Prediction' },
  { to: '/how-it-works', label: 'How It Works' },
  { to: '/about', label: 'About' },
  { to: '/faq', label: 'FAQ' },
  { to: '/contact', label: 'Contact' },
]

const menuOpen = ref(false)
const route = useRoute()
watch(() => route.path, () => (menuOpen.value = false))
</script>

<template>
  <header class="header">
    <div class="container header-inner">
      <BrandLogo />

      <nav id="main-nav" class="nav" :class="{ open: menuOpen }" aria-label="Main">
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-link">
          {{ link.label }}
        </RouterLink>
        <div class="menu-auth">
          <RouterLink :to="APP_ROUTES.login" class="btn btn-outline">Login</RouterLink>
          <RouterLink :to="APP_ROUTES.register" class="btn btn-primary">Register</RouterLink>
        </div>
      </nav>

      <div class="actions">
        <RouterLink :to="APP_ROUTES.login" class="btn btn-outline auth">Login</RouterLink>
        <RouterLink :to="APP_ROUTES.register" class="btn btn-primary auth">Register</RouterLink>
        <button
          class="icon-btn menu-btn"
          type="button"
          aria-controls="main-nav"
          :aria-expanded="menuOpen"
          :aria-label="menuOpen ? 'Close menu' : 'Open menu'"
          @click="menuOpen = !menuOpen"
        >
          <AppIcon :name="menuOpen ? 'close' : 'menu'" />
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header { position: sticky; top: 0; z-index: 10; background: rgba(255, 244, 249, 0.92); backdrop-filter: blur(8px); }
.header-inner { display: flex; align-items: center; justify-content: space-between; gap: 1rem; height: 76px; }

.nav { display: flex; gap: 0.2rem; }
.nav-link {
  padding: 0.4rem 0.85rem; border-radius: 999px; border: 2px solid transparent; white-space: nowrap;
  color: var(--purple-900); font-weight: 500; font-size: 0.93rem;
}
.nav-link:hover { color: var(--pink-600); }
.nav-link:focus-visible, .icon-btn:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.nav-link.router-link-exact-active { border-color: var(--pink-600); color: var(--pink-600); background: #fff; }
.menu-auth { display: none; }

.actions { display: flex; align-items: center; gap: 0.6rem; }
.icon-btn { background: none; border: 0; border-radius: 8px; color: var(--purple-900); cursor: pointer; padding: 6px; }
.auth { padding: 0.5rem 1.2rem; font-size: 0.92rem; }
.menu-btn { display: none; }

@media (max-width: 1100px) {
  .menu-btn { display: block; }
  .nav {
    display: none; position: absolute; top: 76px; left: 0; right: 0;
    flex-direction: column; gap: 0.25rem; padding: 1rem 20px 1.25rem; background: #fff; box-shadow: var(--shadow);
  }
  .nav.open { display: flex; }
  .nav-link { padding: 0.65rem 1rem; }
}
@media (max-width: 560px) {
  .auth { display: none; }
  .menu-auth { display: flex; gap: 0.75rem; margin-top: 0.5rem; }
  .menu-auth .btn { flex: 1; justify-content: center; }
}
</style>
