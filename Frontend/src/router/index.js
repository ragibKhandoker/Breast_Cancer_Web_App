import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import PredictionView from '../views/PredictionView.vue'
import HistoryView from '../views/HistoryView.vue'
import AboutView from '../views/AboutView.vue'
import HowItWorksView from '../views/HowItWorksView.vue'
import FaqView from '../views/FaqView.vue'
import ContactView from '../views/ContactView.vue'
import ComingSoonView from '../views/ComingSoonView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import { APP_ROUTES } from '../config/appRoutes'

const AUTH_PENDING = 'Login and registration are being built as a separate feature and will be available here soon.'

// Backend SpaController serves index.html for any single-segment path, so each of these opens directly by URL.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/about', name: 'about', component: AboutView, meta: { title: 'About' } },
    { path: '/how-it-works', name: 'how-it-works', component: HowItWorksView, meta: { title: 'How It Works' } },
    { path: '/faq', name: 'faq', component: FaqView, meta: { title: 'FAQ' } },
    { path: '/contact', name: 'contact', component: ContactView, meta: { title: 'Contact' } },
    { path: APP_ROUTES.analysis, name: 'prediction', component: PredictionView, meta: { title: 'Mammography Prediction' } },
    { path: APP_ROUTES.history, name: 'history', component: HistoryView, meta: { title: 'Prediction History' } },
    { path: APP_ROUTES.login, component: ComingSoonView, meta: { title: 'Login', description: AUTH_PENDING } },
    { path: APP_ROUTES.register, component: ComingSoonView, meta: { title: 'Register', description: AUTH_PENDING } },
    { path: '/privacy', component: ComingSoonView, meta: { title: 'Privacy Policy' } },
    { path: '/terms', component: ComingSoonView, meta: { title: 'Terms of Service' } },
    // Older placeholder links now point to the pages that cover them.
    { path: '/learn', redirect: '/how-it-works' },
    { path: '/resources', redirect: '/faq' },
    { path: '/support', redirect: '/contact' },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { title: 'Page Not Found' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} | BreastCare` : 'BreastCare — Detect Early, Live Healthier'
})

export default router
