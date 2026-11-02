<template>
  <!-- Sticks to the bottom of the screen for as long as the report is on the page; it cannot be closed. -->
  <aside class="disclaimer" role="note" aria-label="Medical disclaimer">
    <strong>Not a medical diagnosis.</strong>
    This AI result is for research and education only and can be wrong. Only a doctor can diagnose breast cancer. If
    you have symptoms or concerns, see a doctor, whatever this result says.
  </aside>
</template>

<style scoped>
/* margin-right keeps the chat button in the corner from covering the text. */
.disclaimer {
  position: sticky; bottom: 0.75rem; z-index: 20; margin-top: 1.5rem; margin-right: 4.5rem; padding: 0.8rem 1.1rem;
  border: 1px solid #f5c98a; border-left: 5px solid var(--warn-text); border-radius: 12px;
  background: var(--warn-bg); color: var(--warn-text); font-size: 0.9rem; line-height: 1.5;
  box-shadow: 0 8px 24px rgba(42, 22, 80, 0.12);
}
/* Phones: same text, smaller, so it covers less of the report while it stays on screen. */
@media (max-width: 640px) {
  .disclaimer { margin-right: 3.9rem; padding: 0.5rem 0.7rem; border-left-width: 4px; font-size: 0.76rem; line-height: 1.35; }
}
</style>
