<script setup>
defineProps({ magnifier: { type: Boolean, default: false } })
</script>

<template>
  <!-- A monitor showing a stylised mammogram, optionally with a magnifying glass. -->
  <svg viewBox="0 0 260 220" role="img" aria-label="Mammogram on a monitor">
    <defs>
      <radialGradient id="tissue" cx="35%" cy="50%" r="70%">
        <stop offset="0%" stop-color="#f4f2f8" />
        <stop offset="45%" stop-color="#a9a4b8" />
        <stop offset="100%" stop-color="#2f2945" />
      </radialGradient>
    </defs>
    <rect x="105" y="168" width="50" height="26" fill="#3a3157" />
    <rect x="80" y="192" width="100" height="10" rx="5" fill="#2e2647" />
    <rect x="10" y="10" width="240" height="162" rx="14" fill="#2e2647" />
    <rect x="22" y="22" width="216" height="138" rx="6" fill="#141022" />
    <path d="M70 34 C150 40 196 78 196 92 C196 108 150 144 70 150 Z" fill="url(#tissue)" />
    <g fill="none" stroke="#ffffff" stroke-opacity="0.35" stroke-width="1.4">
      <path d="M74 60 C110 62 140 78 170 90" />
      <path d="M74 92 C110 86 150 92 186 94" />
      <path d="M74 124 C110 122 140 110 170 98" />
      <path d="M90 46 C110 70 112 110 92 138" />
      <path d="M120 54 C132 76 134 108 122 130" />
    </g>
    <rect x="30" y="30" width="26" height="4" rx="2" fill="#4b4466" />
    <rect x="30" y="38" width="16" height="4" rx="2" fill="#4b4466" />
    <g v-if="magnifier">
      <line x1="196" y1="132" x2="232" y2="170" stroke="#d81b6a" stroke-width="12" stroke-linecap="round" />
      <circle cx="170" cy="106" r="34" fill="#ffffff" fill-opacity="0.15" stroke="#e84a8a" stroke-width="9" />
    </g>
  </svg>
</template>
