// Page paths that buttons across the site link to. Change a path here and every Start Analysis / Login /
// Register / Dashboard button follows.
export const APP_ROUTES = {
  analysis: '/prediction', // mammogram analysis page (patients)
  history: '/history', // past analyses (patients)
  login: '/login',
  register: '/register',
  patientDashboard: '/dashboard',
  doctorDashboard: '/doctor',
  adminDashboard: '/admin',
  profile: '/profile', // the signed-in user's own profile: the doctor profile for doctors, account details for others
  accountSettings: '/account-settings', // a doctor's account details: name, email and password
  forgotPassword: '/forgot-password',
  messages: '/messages', // a patient's or doctor's conversation with the administrators
  adminAccount: '/admin-account', // the site owner's own admin profile
  surgeons: '/breast-surgeons', // Find a Breast Surgeon (public)
  myAppointments: '/my-appointments', // a patient's Breast Surgeon appointments
  surgeonDashboard: '/surgeon-dashboard', // an approved Breast Surgeon's appointments and availability
  surgeonProfile: '/profile', // a doctor's own doctor (Breast Surgeon) profile is their profile page
}
