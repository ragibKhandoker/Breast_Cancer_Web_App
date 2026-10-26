// The analysis workflow as it is actually implemented (PredictionView -> POST /api/predictions -> OnnxPredictor
// -> MongoDB). Used by the How It Works page and the Home page preview.
export const HOW_IT_WORKS_STEPS = [
  {
    icon: 'microscope',
    title: 'Open the analysis page',
    text: 'Go to Prediction from the menu or press Start Analysis. No account is needed at the moment.',
  },
  {
    icon: 'file-text',
    title: 'Upload a mammogram',
    text: 'Drag a PNG or JPG mammogram onto the upload area, or click to choose one. Files can be up to 50 MB.',
  },
  {
    icon: 'shield-check',
    title: 'The model analyses it',
    text: 'Our server resizes the image to 512×512 pixels and runs an EfficientNet-B0 neural network on it with ONNX Runtime.',
  },
  {
    icon: 'heart',
    title: 'Read the result',
    text: 'You see an estimated probability that the image shows a malignant finding, with a colour bar and which way the model leans.',
  },
  {
    icon: 'book',
    title: 'Find it again in History',
    text: 'The image and its result are saved in our MongoDB Atlas database. Open History to review or delete them.',
  },
  {
    icon: 'users',
    title: 'Talk to a doctor',
    text: 'The result is a research estimate, not a diagnosis. Always discuss your mammogram with a qualified radiologist.',
  },
]
