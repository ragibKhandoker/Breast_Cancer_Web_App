<script setup>
import { ref, useId } from 'vue'

defineProps({
  question: { type: String, required: true },
  answer: { type: String, required: true },
})

const open = ref(false)
const id = useId()
</script>

<template>
  <div class="faq-item" :class="{ open }">
    <h3 class="question">
      <button
        :id="`${id}-q`"
        type="button"
        class="toggle"
        :aria-expanded="open"
        :aria-controls="`${id}-a`"
        @click="open = !open"
      >
        <span>{{ question }}</span>
        <span class="chevron" aria-hidden="true"></span>
      </button>
    </h3>
    <div :id="`${id}-a`" class="answer-wrap" role="region" :aria-labelledby="`${id}-q`" :inert="!open">
      <div class="answer-inner">
        <p class="answer">{{ answer }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.faq-item {
  background: #fff; border: 1px solid var(--border); border-radius: 16px;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.faq-item.open { border-color: var(--pink-300); box-shadow: var(--shadow); }
.question { margin: 0; font-size: 1rem; }
.toggle {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem; width: 100%;
  padding: 1.1rem 1.35rem; background: none; border: 0; border-radius: 16px; cursor: pointer;
  font: 600 1rem 'Poppins', sans-serif; color: var(--purple-900); text-align: left;
}
.toggle:hover { color: var(--pink-600); }
.toggle:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 2px; }

.chevron {
  flex: none; width: 10px; height: 10px; margin-right: 4px;
  border-right: 2px solid currentColor; border-bottom: 2px solid currentColor;
  transform: rotate(45deg); transition: transform 0.25s ease;
}
.open .chevron { transform: rotate(-135deg); }

/* Grid-row trick animates the height from 0 to its natural size. */
.answer-wrap { display: grid; grid-template-rows: 0fr; transition: grid-template-rows 0.3s ease; }
.open .answer-wrap { grid-template-rows: 1fr; }
.answer-inner { overflow: hidden; }
.answer { margin: 0; padding: 0 1.35rem 1.2rem; color: var(--text); line-height: 1.7; }

@media (prefers-reduced-motion: reduce) {
  .answer-wrap, .chevron { transition: none; }
}
</style>
