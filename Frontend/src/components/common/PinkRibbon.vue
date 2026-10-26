<script setup>
defineProps({ size: { type: [Number, String], default: 40 } })
</script>

<template>
  <!-- Breast cancer awareness ribbon: one strand crossing over the other below the loop. -->
  <svg :width="size" :height="size * 1.4" viewBox="0 0 100 140" aria-hidden="true">
    <path
      d="M38 66 C22 40 26 14 50 12 C74 14 78 40 62 66 L22 132"
      fill="none"
      stroke="#c2185b"
      stroke-width="15"
      stroke-linejoin="round"
    />
    <path d="M38 66 L78 132" fill="none" stroke="#ec5c96" stroke-width="15" />
    <path d="M44 22 C36 30 36 44 42 54" fill="none" stroke="#f9a8c9" stroke-width="3" stroke-linecap="round" />
  </svg>
</template>
