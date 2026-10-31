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
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import MessagesView from '../views/MessagesView.vue'
import PatientDashboardView from '../views/PatientDashboardView.vue'
import DoctorDashboardView from '../views/DoctorDashboardView.vue'
import AdminDashboardView from '../views/AdminDashboardView.vue'
import AdminAccountView from '../views/AdminAccountView.vue'
import ReportView from '../views/ReportView.vue'
import ProfileView from '../views/ProfileView.vue'
import MyProfilePage from '../views/MyProfilePage.vue'
import BreastSurgeonListingPage from '../views/BreastSurgeonListingPage.vue'
import BreastSurgeonDetailsPage from '../views/BreastSurgeonDetailsPage.vue'
import AppointmentBookingPage from '../views/AppointmentBookingPage.vue'
import AppointmentConfirmationPage from '../views/AppointmentConfirmationPage.vue'
import MyAppointmentsPage from '../views/MyAppointmentsPage.vue'
import BreastSurgeonDashboard from '../views/BreastSurgeonDashboard.vue'
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
  { path: APP_ROUTES.forgotPassword, name: 'forgot-password', component: ForgotPasswordView, meta: { title: 'Forgot Password', guestOnly: true } },
  // Older link: an approved reset now shows on the Login page.
  { path: '/forgot-password/status', redirect: APP_ROUTES.login },
  { path: APP_ROUTES.messages, name: 'messages', component: MessagesView, meta: { title: 'Messages', roles: [PATIENT, DOCTOR] } },
  { path: APP_ROUTES.patientDashboard, name: 'patient-dashboard', component: PatientDashboardView, meta: { title: 'My Dashboard', roles: [PATIENT] } },
  { path: APP_ROUTES.doctorDashboard, name: 'doctor-dashboard', component: DoctorDashboardView, meta: { title: 'Doctor Dashboard', roles: [DOCTOR] } },
  { path: APP_ROUTES.adminDashboard, name: 'admin-dashboard', component: AdminDashboardView, meta: { title: 'Admin Dashboard', roles: [ADMIN] } },
  // Admins only; the page itself (and the backend) also refuse every admin except the site owner.
  { path: APP_ROUTES.adminAccount, name: 'admin-account', component: AdminAccountView, meta: { title: 'Admin Account', roles: [ADMIN] } },
  { path: APP_ROUTES.analysis, name: 'prediction', component: PredictionView, meta: { title: 'Upload Mammogram', roles: [PATIENT] } },
  { path: APP_ROUTES.history, name: 'history', component: HistoryView, meta: { title: 'Prediction History', roles: [PATIENT] } },
  // Doctors get their doctor profile here; patients and administrators their account profile.
  { path: APP_ROUTES.profile, name: 'profile', component: MyProfilePage, meta: { title: 'My Profile', roles: [PATIENT, DOCTOR, ADMIN] } },
  { path: APP_ROUTES.accountSettings, name: 'account-settings', component: ProfileView, meta: { title: 'Account Settings', roles: [DOCTOR] } },
  // Breast Surgeon appointments. Browsing is public; booking is for patients; the surgeon pages for doctors.
  { path: APP_ROUTES.surgeons, name: 'breast-surgeons', component: BreastSurgeonListingPage, meta: { title: 'Find a Breast Surgeon' } },
  { path: '/breast-surgeons/:id', name: 'breast-surgeon', component: BreastSurgeonDetailsPage, meta: { title: 'Breast Surgeon' } },
  { path: '/breast-surgeons/:id/book', name: 'book-appointment', component: AppointmentBookingPage, meta: { title: 'Book an Appointment', roles: [PATIENT] } },
  { path: '/appointments/:id/confirmation', name: 'appointment-confirmation', component: AppointmentConfirmationPage, meta: { title: 'Appointment Requested', roles: [PATIENT] } },
  { path: APP_ROUTES.myAppointments, name: 'my-appointments', component: MyAppointmentsPage, meta: { title: 'My Breast Surgeon Appointments', roles: [PATIENT] } },
  { path: APP_ROUTES.surgeonDashboard, name: 'surgeon-dashboard', component: BreastSurgeonDashboard, meta: { title: 'Breast Surgeon Dashboard', roles: [DOCTOR] } },
  { path: '/surgeon-profile', redirect: APP_ROUTES.profile }, // older links
  { path: '/reports/:id', name: 'report', component: ReportView, meta: { title: 'Report', roles: [PATIENT, DOCTOR] } },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: ComingSoonView,
    meta: { title: 'Access Denied', description: 'Your account does not have access to that page. Prediction and booking a Breast Surgeon are for patient accounts.' },
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
