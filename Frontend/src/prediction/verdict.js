// Benign or Malignant for a saved report: its label, or (for reports saved as "uncertain" under an older rule)
// whichever of the two percentages is larger.
export function isMalignant(report) {
  return report.label === 'uncertain' ? report.malignantProbability >= 0.5 : report.label === 'malignant'
}

// The AI risk category shown with a report. Results the model could not call clearly (lowConfidence, or the
// older "uncertain" label) are intermediate; the rest follow the verdict.
export const RISK_CATEGORIES = {
  low: { level: 'low', label: 'Low risk', hint: 'The AI found no signs it associates with cancer. Keep up regular screening.' },
  intermediate: {
    level: 'intermediate',
    label: 'Intermediate risk',
    hint: 'The AI could not decide clearly. Ask a doctor to review this scan.',
  },
  high: { level: 'high', label: 'High risk', hint: 'The AI found signs it associates with cancer. Please see a doctor soon.' },
}

export function riskCategory(report) {
  if (report.lowConfidence || report.label === 'uncertain') return RISK_CATEGORIES.intermediate
  return isMalignant(report) ? RISK_CATEGORIES.high : RISK_CATEGORIES.low
}
