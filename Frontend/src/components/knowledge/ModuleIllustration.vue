<script setup>
import { computed } from 'vue'

// Topic pictures for the Knowledge Hub cards and lesson headers: real photographs from Unsplash (see
// assets/knowledge/CREDITS.md). A name without a photo (such as "book") falls back to a small drawn SVG.
const PHOTOS = Object.fromEntries(
  Object.entries(import.meta.glob('../../assets/knowledge/*.jpg', { eager: true, import: 'default' }))
    .map(([path, url]) => [path.split('/').pop().replace('.jpg', ''), url]),
)

const props = defineProps({ name: { type: String, default: 'book' }, alt: { type: String, default: '' } })
const photo = computed(() => PHOTOS[props.name])
</script>

<template>
  <img v-if="photo" class="photo" :src="photo" :alt="alt" decoding="async" />
  <svg v-else class="illustration" viewBox="0 0 120 90" role="img" :aria-label="`${name} illustration`">
    <rect x="0" y="0" width="120" height="90" rx="16" fill="#fff0f6" />
    <circle cx="98" cy="16" r="22" fill="#fde3ef" />

    <g v-if="name === 'history'">
      <rect x="28" y="20" width="56" height="52" rx="4" fill="#fff" stroke="#e84a8a" stroke-width="2.5" />
      <rect x="22" y="16" width="12" height="60" rx="6" fill="#f7a8c8" />
      <rect x="78" y="16" width="12" height="60" rx="6" fill="#f7a8c8" />
      <path d="M40 34h28M40 43h32M40 52h22M40 61h28" stroke="#6b3fb5" stroke-width="2.5" stroke-linecap="round" />
    </g>

    <g v-else-if="name === 'cells'">
      <circle cx="40" cy="44" r="18" fill="#fff" stroke="#e84a8a" stroke-width="2.5" />
      <circle cx="40" cy="44" r="6" fill="#f7a8c8" />
      <circle cx="78" cy="40" r="14" fill="#fff" stroke="#6b3fb5" stroke-width="2.5" />
      <circle cx="78" cy="40" r="5" fill="#a99be0" />
      <circle cx="74" cy="66" r="10" fill="#fff" stroke="#6b3fb5" stroke-width="2.5" stroke-dasharray="4 3" />
      <circle cx="74" cy="66" r="3.5" fill="#d81b6a" />
    </g>

    <g v-else-if="name === 'classification'">
      <rect x="46" y="12" width="28" height="16" rx="5" fill="#e84a8a" />
      <path d="M60 28v10M26 38h68M26 38v8M60 38v8M94 38v8" stroke="#6b3fb5" stroke-width="2.5" fill="none" />
      <rect x="14" y="46" width="24" height="14" rx="4" fill="#fff" stroke="#e84a8a" stroke-width="2" />
      <rect x="48" y="46" width="24" height="14" rx="4" fill="#fff" stroke="#e84a8a" stroke-width="2" />
      <rect x="82" y="46" width="24" height="14" rx="4" fill="#fff" stroke="#e84a8a" stroke-width="2" />
      <path d="M20 68h12M54 68h12M88 68h12" stroke="#a99be0" stroke-width="3" stroke-linecap="round" />
    </g>

    <g v-else-if="name === 'microscope'">
      <rect x="30" y="72" width="56" height="7" rx="3" fill="#6b3fb5" />
      <path d="M48 72c0-12 8-20 22-20" stroke="#6b3fb5" stroke-width="4" fill="none" stroke-linecap="round" />
      <rect x="52" y="14" width="12" height="34" rx="4" transform="rotate(-25 58 31)" fill="#e84a8a" />
      <rect x="44" y="56" width="30" height="5" rx="2" fill="#f7a8c8" />
      <circle cx="88" cy="44" r="9" fill="#fff" stroke="#e84a8a" stroke-width="2" />
      <circle cx="86" cy="42" r="2.5" fill="#d81b6a" />
    </g>

    <g v-else-if="name === 'stages'">
      <rect x="16" y="60" width="14" height="14" rx="3" fill="#fde3ef" stroke="#e84a8a" stroke-width="1.5" />
      <rect x="35" y="50" width="14" height="24" rx="3" fill="#fbcfe0" />
      <rect x="54" y="40" width="14" height="34" rx="3" fill="#f7a8c8" />
      <rect x="73" y="30" width="14" height="44" rx="3" fill="#e84a8a" />
      <rect x="92" y="18" width="14" height="56" rx="3" fill="#6b3fb5" />
      <path d="M14 78h94" stroke="#6b3fb5" stroke-width="2" />
    </g>

    <g v-else-if="name === 'dna'">
      <path d="M42 12c36 16 0 50 36 66M78 12c-36 16 0 50-36 66" stroke="#e84a8a" stroke-width="3" fill="none" />
      <path d="M48 22h24M52 32h16M50 58h20M46 68h28" stroke="#6b3fb5" stroke-width="2.5" stroke-linecap="round" />
      <circle cx="60" cy="45" r="4" fill="#6b3fb5" />
    </g>

    <g v-else-if="name === 'symptoms'">
      <path d="M22 30c6 30 34 40 44 22" stroke="#e84a8a" stroke-width="3" fill="#fff" />
      <circle cx="50" cy="52" r="3" fill="#d81b6a" />
      <circle cx="80" cy="44" r="15" fill="#fff" stroke="#6b3fb5" stroke-width="3" />
      <path d="M91 55l12 12" stroke="#6b3fb5" stroke-width="5" stroke-linecap="round" />
      <circle cx="80" cy="44" r="4" fill="#f7a8c8" />
    </g>

    <g v-else-if="name === 'diagnosis'">
      <rect x="32" y="14" width="46" height="62" rx="6" fill="#fff" stroke="#6b3fb5" stroke-width="2.5" />
      <rect x="45" y="10" width="20" height="9" rx="3" fill="#e84a8a" />
      <path d="M40 34l4 4 7-8M40 50l4 4 7-8M40 66l4 4 7-8" stroke="#e84a8a" stroke-width="2.5" fill="none" stroke-linecap="round" />
      <path d="M56 35h14M56 51h14M56 67h10" stroke="#a99be0" stroke-width="2.5" stroke-linecap="round" />
      <circle cx="92" cy="58" r="10" fill="#fde3ef" stroke="#e84a8a" stroke-width="2" />
    </g>

    <g v-else-if="name === 'mammogram'">
      <rect x="20" y="14" width="80" height="54" rx="6" fill="#2a1650" />
      <path d="M34 22c-4 20 6 38 30 40" stroke="#f3e8ff" stroke-width="2.5" fill="none" />
      <path d="M38 34c8 2 14 8 18 16M40 44c6 2 10 6 12 10" stroke="#a99be0" stroke-width="1.5" fill="none" />
      <circle cx="52" cy="42" r="4" fill="#f7a8c8" />
      <path d="M72 28h20M72 38h14M72 48h18" stroke="#f7a8c8" stroke-width="2.5" stroke-linecap="round" />
      <rect x="50" y="68" width="20" height="8" fill="#6b3fb5" />
    </g>

    <g v-else-if="name === 'treatment'">
      <rect x="22" y="30" width="44" height="22" rx="11" transform="rotate(-30 44 41)" fill="#fff" stroke="#e84a8a" stroke-width="2.5" />
      <path d="M34 50l20-12" stroke="#e84a8a" stroke-width="2.5" />
      <path d="M84 24c-8 0-8 12 0 22 8-10 8-22 0-22z" fill="#e84a8a" />
      <path d="M84 46l-10 22M84 46l10 22" stroke="#e84a8a" stroke-width="5" stroke-linecap="round" />
      <circle cx="40" cy="70" r="6" fill="#a99be0" />
    </g>

    <g v-else-if="name === 'surgery'">
      <rect x="22" y="38" width="62" height="14" rx="7" transform="rotate(-20 53 45)" fill="#f7a8c8" />
      <rect x="40" y="38" width="26" height="14" fill="#fff" transform="rotate(-20 53 45)" />
      <circle cx="49" cy="43" r="1.6" fill="#e84a8a" /><circle cx="56" cy="40" r="1.6" fill="#e84a8a" /><circle cx="52" cy="48" r="1.6" fill="#e84a8a" />
      <path d="M86 20c-8-8-18 4-8 12l8 8 8-8c10-8 0-20-8-12z" fill="#e84a8a" />
      <path d="M24 72h72" stroke="#6b3fb5" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="6 5" />
    </g>

    <g v-else-if="name === 'survivorship'">
      <path d="M60 18c-10 0-10 16 0 28 10-12 10-28 0-28z" fill="#e84a8a" />
      <path d="M60 46l-14 28M60 46l14 28" stroke="#e84a8a" stroke-width="6" stroke-linecap="round" />
      <path d="M22 66c8-14 18-14 24-6M98 66c-8-14-18-14-24-6" stroke="#6b3fb5" stroke-width="3" fill="none" stroke-linecap="round" />
      <circle cx="26" cy="30" r="4" fill="#a99be0" /><circle cx="94" cy="34" r="3" fill="#f7a8c8" />
    </g>

    <g v-else>
      <path d="M60 24c-10-6-24-6-34-2v48c10-4 24-4 34 2 10-6 24-6 34-2V22c-10-4-24-4-34 2z" fill="#fff" stroke="#e84a8a" stroke-width="2.5" />
      <path d="M60 24v48" stroke="#6b3fb5" stroke-width="2" />
      <path d="M34 36h16M34 46h16M70 36h16M70 46h16" stroke="#a99be0" stroke-width="2.5" stroke-linecap="round" />
    </g>
  </svg>
</template>

<style scoped>
.illustration { width: 100%; height: auto; }
.photo { display: block; width: 100%; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 16px; background: #fff0f6; }
</style>
