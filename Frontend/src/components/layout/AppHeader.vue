<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../common/AppIcon.vue'
import BrandLogo from './BrandLogo.vue'
import NotificationBell from './NotificationBell.vue'
import UserMenu from './UserMenu.vue'
import { APP_ROUTES } from '../../config/appRoutes'
import { authState, signOut, useAnotherAccount } from '../../auth/authStore'
import { homeFor, ROLES } from '../../auth/roles'

const user = computed(() => authState.user)

// Shown to every visitor. Browsing Breast Surgeons is public; uploading a mammogram is for patient accounts,
// so for doctors and administrators the link says so (the route guard sends them to Access Denied).
const links = computed(() => [
  { to: '/', label: 'Home' },
  {
    to: APP_ROUTES.analysis,
    label: 'Prediction',
    title: user.value && user.value.role !== ROLES.PATIENT ? 'Prediction is for patient accounts' : undefined,
  },
  { to: APP_ROUTES.surgeons, label: 'Find a Surgeon' },
  { to: '/how-it-works', label: 'How It Works' },
  { to: '/about', label: 'About' },
  { to: '/faq', label: 'FAQ' },
  { to: '/contact', label: 'Contact' },
])

const menuOpen = ref(false)
const route = useRoute()
const router = useRouter()
watch(() => route.path, () => (menuOpen.value = false))

const loggingOut = ref(false)
// Signs this tab in as someone else; the other tabs keep their own login.
function anotherAccount() {
  useAnotherAccount()
  router.push(APP_ROUTES.login)
}

async function logout() {
  loggingOut.value = true
  try {
    await signOut()
  } catch {
    // The local session is cleared regardless; the cookie expires on its own.
  } finally {
    loggingOut.value = false
    router.replace('/')
  }
}
</script>

<template>
  <header class="header">
    <div class="container header-inner">
      <BrandLogo />

      <nav id="main-nav" class="nav" :class="{ open: menuOpen }" aria-label="Main">
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" :title="link.title" class="nav-link">
          {{ link.label }}
        </RouterLink>
        <div class="menu-auth">
          <template v-if="user">
            <RouterLink :to="homeFor(user.role)" class="btn btn-primary">Dashboard</RouterLink>
            <RouterLink :to="user.owner ? APP_ROUTES.adminAccount : APP_ROUTES.profile" class="btn btn-outline">Profile</RouterLink>
            <button type="button" class="btn btn-outline" :disabled="loggingOut" @click="logout">Logout</button>
            <button type="button" class="btn btn-outline" @click="anotherAccount">Use another account</button>
          </template>
          <template v-else>
            <RouterLink :to="APP_ROUTES.login" class="btn btn-outline">Login</RouterLink>
            <RouterLink :to="APP_ROUTES.register" class="btn btn-primary">Register</RouterLink>
          </template>
        </div>
      </nav>

      <div class="actions">
        <RouterLink
          v-if="user"
          :to="user.role === ROLES.ADMIN ? { path: APP_ROUTES.adminDashboard, query: { tab: 'inbox' } } : APP_ROUTES.messages"
          class="msg-btn"
          :title="user.role === ROLES.ADMIN ? 'Inbox' : 'Messages'"
          :aria-label="user.role === ROLES.ADMIN ? 'Inbox' : 'Messages'"
        >
          <AppIcon name="message" :size="20" />
        </RouterLink>
        <NotificationBell v-if="user" />
        <template v-if="user">
          <RouterLink :to="homeFor(user.role)" class="btn btn-primary auth" :title="`Signed in as ${user.email}`">
            Dashboard
          </RouterLink>
          <!-- Profile, "Use another account" and Logout live in the avatar's menu, keeping the header on one line. -->
          <UserMenu class="auth-menu" :logging-out="loggingOut" @logout="logout" @another-account="anotherAccount" />
        </template>
        <template v-else>
          <RouterLink :to="APP_ROUTES.login" class="btn btn-outline auth">Login</RouterLink>
          <RouterLink :to="APP_ROUTES.register" class="btn btn-primary auth">Register</RouterLink>
        </template>
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
/* A little wider than page content: logo, seven links and the account buttons need about 1180px. */
.header-inner { display: flex; align-items: center; justify-content: space-between; gap: 1rem; height: 76px; max-width: 1280px; }
.header-inner > :first-child { flex: none; }

.nav { display: flex; gap: 0.1rem; min-width: 0; }
.nav-link {
  padding: 0.4rem 0.7rem; border-radius: 999px; border: 2px solid transparent; white-space: nowrap;
  color: var(--purple-900); font-weight: 500; font-size: 0.93rem;
}
.nav-link:hover { color: var(--pink-600); }
.nav-link:focus-visible, .icon-btn:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }
.nav-link.router-link-exact-active { border-color: var(--pink-600); color: var(--pink-600); background: #fff; }
.menu-auth { display: none; }

.actions { flex: none; display: flex; align-items: center; gap: 0.5rem; }
.icon-btn { background: none; border: 0; border-radius: 8px; color: var(--purple-900); cursor: pointer; padding: 6px; }
.auth { padding: 0.5rem 1.2rem; font-size: 0.92rem; }
.menu-btn { display: none; }
.msg-btn {
  display: grid; place-items: center; width: 42px; height: 42px; border-radius: 50%; border: 1.5px solid var(--border);
  background: #fff; color: var(--purple-900); transition: border-color 0.15s, color 0.15s;
}
.msg-btn:hover, .msg-btn.router-link-active { border-color: var(--pink-300); color: var(--pink-600); }
.msg-btn:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }

/* Narrower desktops: tighter links so everything stays on one line. */
@media (max-width: 1320px) and (min-width: 1201px) {
  .header-inner { gap: 0.75rem; }
  .nav-link { padding: 0.4rem 0.55rem; font-size: 0.9rem; }
  .auth { padding: 0.5rem 1rem; }
}
/* Below that the links move into the menu button. */
@media (max-width: 1200px) {
  .menu-btn { display: block; }
  .nav {
    display: none; position: absolute; top: 76px; left: 0; right: 0;
    flex-direction: column; gap: 0.25rem; padding: 1rem 20px 1.25rem; background: #fff; box-shadow: var(--shadow);
  }
  .nav.open { display: flex; }
  .nav-link { padding: 0.65rem 1rem; }
}
@media (max-width: 560px) {
  .auth, .auth-menu { display: none; }
  .menu-auth { display: flex; gap: 0.75rem; margin-top: 0.5rem; }
  .menu-auth .btn { flex: 1; justify-content: center; }
}
</style>
