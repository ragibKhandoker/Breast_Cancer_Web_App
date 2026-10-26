// Answers reflect the current implementation; update them when features change (accounts, DICOM, a trained model).
export const FAQS = [
  {
    question: 'What does BreastCare do?',
    answer:
      'BreastCare lets you upload a mammogram image and returns an AI-estimated probability that it shows a malignant finding. It also explains how the analysis works and stores your past results so you can review them.',
  },
  {
    question: 'Is the result a medical diagnosis?',
    answer:
      'No. BreastCare is a research and education project, not a medical device. The result is an estimate from a machine-learning model and can be wrong. Only a qualified doctor can diagnose breast cancer, so always discuss your mammogram with a radiologist.',
  },
  {
    question: 'How accurate is the model?',
    answer:
      'We do not publish accuracy figures yet. The model is still being trained on mammogram data. Until a trained model is installed, the site runs in demo mode: the numbers it shows are not meaningful, and every result displays a "Demo mode" warning.',
  },
  {
    question: 'Which image files can I upload?',
    answer:
      'PNG and JPG images up to 50 MB. DICOM (.dcm) files are not supported yet; export the mammogram as PNG or JPG first.',
  },
  {
    question: 'How long does an analysis take?',
    answer:
      'Usually a few seconds. The time depends on the image size and on how busy the server is.',
  },
  {
    question: 'Where are my images stored, and who can see them?',
    answer:
      'Each uploaded image and its result are stored in our MongoDB Atlas database. There are no user accounts yet, so the History page shows every analysis made on this site. Please do not upload images that contain names or other personal details, and delete your images from History when you no longer need them.',
  },
  {
    question: 'Do I need an account?',
    answer:
      'Not at the moment. Login and registration are being built as a separate feature. Once they are ready, your history will be private to your account.',
  },
  {
    question: 'How can I contact the team?',
    answer: 'Use the Contact page to send us a message. We read every message that comes in through the form.',
  },
]
