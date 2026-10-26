<script setup>
import { computed } from 'vue'

const props = defineProps({
  url: { type: String, required: true },
  title: { type: String, default: 'YouTube video' },
})

/** Pulls the 11-character video ID out of watch, youtu.be, embed and Shorts links. */
const videoId = computed(() => {
  const match = props.url.match(/(?:youtu\.be\/|[?&]v=|\/embed\/|\/shorts\/|\/live\/)([\w-]{11})/)
  return match ? match[1] : ''
})

// The no-cookie domain avoids YouTube tracking cookies until the visitor presses play.
const src = computed(() => `https://www.youtube-nocookie.com/embed/${videoId.value}?rel=0&modestbranding=1`)
</script>

<template>
  <div class="video">
    <iframe
      v-if="videoId"
      :src="src"
      :title="title"
      loading="lazy"
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      referrerpolicy="strict-origin-when-cross-origin"
      allowfullscreen
    ></iframe>
    <p v-else class="invalid">This is not a valid YouTube link: {{ url }}</p>
  </div>
</template>

<style scoped>
.video { position: relative; aspect-ratio: 16 / 9; border-radius: 18px; overflow: hidden; background: #141022; }
iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
.invalid { display: grid; place-items: center; height: 100%; margin: 0; padding: 1rem; color: #f7a8c8; text-align: center; font-size: 0.9rem; }
</style>
