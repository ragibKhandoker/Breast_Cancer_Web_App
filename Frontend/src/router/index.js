import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import PredictionView from '../views/PredictionView.vue'
import HistoryView from '../views/HistoryView.vue'
import ComingSoonView from '../views/ComingSoonView.vue'

// Keep these paths in sync with Backend SpaController, which serves index.html for them.
export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/prediction', name: 'prediction', component: PredictionView },
    { path: '/history', name: 'history', component: HistoryView },
    { path: '/learn', component: ComingSoonView, meta: { title: 'Learn About Breast Cancer' } },
    { path: '/resources', component: ComingSoonView, meta: { title: 'Resources' } },
    { path: '/support', component: ComingSoonView, meta: { title: 'Patient Support' } },
    { path: '/about', component: ComingSoonView, meta: { title: 'About Us' } },
    { path: '/login', component: ComingSoonView, meta: { title: 'Login' } },
    { path: '/privacy', component: ComingSoonView, meta: { title: 'Privacy Policy' } },
    { path: '/terms', component: ComingSoonView, meta: { title: 'Terms of Service' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
