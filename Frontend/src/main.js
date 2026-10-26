import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import reveal from './directives/reveal'
import { setupSmoothScroll } from './plugins/smoothScroll'
import './assets/main.css'

createApp(App).use(router).directive('reveal', reveal).mount('#app')
setupSmoothScroll(router)
