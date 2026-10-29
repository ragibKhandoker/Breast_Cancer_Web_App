// Benign or Malignant for a saved report: its label, or (for reports saved as "uncertain" under an older rule)
// whichever of the two percentages is larger.
export function isMalignant(report) {
  return report.label === 'uncertain' ? report.malignantProbability >= 0.5 : report.label === 'malignant'
}
