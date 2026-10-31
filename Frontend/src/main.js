import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import reveal from './directives/reveal'
import { setupSmoothScroll } from './plugins/smoothScroll'
import { http, setUnauthorizedHandler, setPasswordChangeRequiredHandler } from './services/http'
import { authState } from './auth/authStore'
import { refreshNotifications } from './notifications/notificationStore'
import { handleSessionExpired, handlePasswordChangeRequired } from './auth/guard'
import './assets/main.css'

// A 401 from the API while signed in means the login expired or was revoked: sign out and ask to log in again.
setUnauthorizedHandler(() => handleSessionExpired(router))
// A 403 password_change_required means the user is on a temporary password: take them to change it.
setPasswordChangeRequiredHandler(() => handlePasswordChangeRequired(router))

// After any change the user makes (saving a profile, booking, sharing…) check the bell straight away, since
// that change may have created a notification. A short delay groups several requests into one check.
let bellTimer = null
http.interceptors.response.use((response) => {
  const method = (response.config?.method ?? 'get').toLowerCase()
  if (method !== 'get' && authState.user && !response.config?.url?.startsWith('/api/notifications')) {
    clearTimeout(bellTimer)
    bellTimer = setTimeout(() => {
      if (authState.user && !authState.user.mustChangePassword) refreshNotifications()
    }, 400)
  }
  return response
})

createApp(App).use(router).directive('reveal', reveal).mount('#app')
setupSmoothScroll(router)
