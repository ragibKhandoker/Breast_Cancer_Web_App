import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import reveal from './directives/reveal'
import { setupSmoothScroll } from './plugins/smoothScroll'
import { setUnauthorizedHandler } from './services/http'
import { handleSessionExpired } from './auth/guard'
import './assets/main.css'

// A 401 from the API while signed in means the login expired or was revoked: sign out and ask to log in again.
setUnauthorizedHandler(() => handleSessionExpired(router))

createApp(App).use(router).directive('reveal', reveal).mount('#app')
setupSmoothScroll(router)
