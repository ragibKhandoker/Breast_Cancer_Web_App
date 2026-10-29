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
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import PatientDashboardView from '../views/PatientDashboardView.vue'
import DoctorDashboardView from '../views/DoctorDashboardView.vue'
import AdminDashboardView from '../views/AdminDashboardView.vue'
import ReportView from '../views/ReportView.vue'
import ProfileView from '../views/ProfileView.vue'
import { APP_ROUTES } from '../config/appRoutes'
import { ROLES } from '../auth/roles'
import { installAuthGuard } from '../auth/guard'

const { PATIENT, DOCTOR, ADMIN } = ROLES

// meta.roles limits a page to signed-in users with those roles; meta.guestOnly is for login/register.
// Backend SpaController serves index.html for any single-segment path and /reports/{id}, so each opens by URL.
export const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/about', name: 'about', component: AboutView, meta: { title: 'About' } },
  { path: '/how-it-works', name: 'how-it-works', component: HowItWorksView, meta: { title: 'How It Works' } },
  { path: '/faq', name: 'faq', component: FaqView, meta: { title: 'FAQ' } },
  { path: '/contact', name: 'contact', component: ContactView, meta: { title: 'Contact' } },
  { path: APP_ROUTES.login, name: 'login', component: LoginView, meta: { title: 'Login', guestOnly: true } },
  { path: APP_ROUTES.register, name: 'register', component: RegisterView, meta: { title: 'Register', guestOnly: true } },
  { path: APP_ROUTES.patientDashboard, name: 'patient-dashboard', component: PatientDashboardView, meta: { title: 'My Dashboard', roles: [PATIENT] } },
  { path: APP_ROUTES.doctorDashboard, name: 'doctor-dashboard', component: DoctorDashboardView, meta: { title: 'Doctor Dashboard', roles: [DOCTOR] } },
  { path: APP_ROUTES.adminDashboard, name: 'admin-dashboard', component: AdminDashboardView, meta: { title: 'Admin Dashboard', roles: [ADMIN] } },
  { path: APP_ROUTES.analysis, name: 'prediction', component: PredictionView, meta: { title: 'Upload Mammogram', roles: [PATIENT] } },
  { path: APP_ROUTES.history, name: 'history', component: HistoryView, meta: { title: 'Prediction History', roles: [PATIENT] } },
  { path: APP_ROUTES.profile, name: 'profile', component: ProfileView, meta: { title: 'My Profile', roles: [PATIENT, DOCTOR, ADMIN] } },
  { path: '/reports/:id', name: 'report', component: ReportView, meta: { title: 'Report', roles: [PATIENT, DOCTOR] } },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: ComingSoonView,
    meta: { title: 'Access Denied', description: 'Your account does not have access to that page.' },
  },
  { path: '/privacy', component: ComingSoonView, meta: { title: 'Privacy Policy' } },
  { path: '/terms', component: ComingSoonView, meta: { title: 'Terms of Service' } },
  // Older placeholder links now point to the pages that cover them.
  { path: '/learn', redirect: '/how-it-works' },
  { path: '/resources', redirect: '/faq' },
  { path: '/support', redirect: '/contact' },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { title: 'Page Not Found' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

installAuthGuard(router)

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} | BreastCare` : 'BreastCare — Detect Early, Live Healthier'
})

export default router
