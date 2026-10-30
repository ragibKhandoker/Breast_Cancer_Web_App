<template>
  <!-- Sticks to the bottom of the screen for as long as the report is on the page; it cannot be closed. -->
  <aside class="disclaimer" role="note" aria-label="Medical disclaimer">
    <strong>Not a medical diagnosis.</strong>
    This AI result is for research and education only and can be wrong. Only a doctor can diagnose breast cancer. If
    you have symptoms or concerns, see a doctor, whatever this result says.
  </aside>
</template>

<style scoped>
.disclaimer {
  position: sticky; bottom: 0.75rem; z-index: 20; margin-top: 1.5rem; padding: 0.8rem 1.1rem;
  border: 1px solid #f5c98a; border-left: 5px solid var(--warn-text); border-radius: 12px;
  background: var(--warn-bg); color: var(--warn-text); font-size: 0.9rem; line-height: 1.5;
  box-shadow: 0 8px 24px rgba(42, 22, 80, 0.12);
}
</style>
