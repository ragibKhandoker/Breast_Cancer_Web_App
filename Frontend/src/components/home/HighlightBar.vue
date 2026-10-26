<script setup>
import AppIcon from '../common/AppIcon.vue'

const items = [
  { icon: 'shield-check', label: 'AI-Powered Prediction', to: '/prediction' },
  { icon: 'book', label: 'Educational Resources', to: '/resources' },
  { icon: 'heart', label: 'Patient Support', to: '/support' },
  { icon: 'users', label: 'Join Our Community', to: '/about' },
]
</script>

<template>
  <div class="container">
    <div class="highlights">
      <RouterLink v-for="(item, i) in items" :key="item.label" v-reveal="i * 100" :to="item.to" class="item">
        <span class="icon"><AppIcon :name="item.icon" :size="30" /></span>
        <span class="label">{{ item.label }}</span>
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.highlights {
  display: grid; grid-template-columns: repeat(4, 1fr);
  margin-top: -3.5rem; position: relative; z-index: 1;
  background: #fff; border: 1px solid var(--border); border-radius: 24px; box-shadow: var(--shadow);
}
.item { display: flex; align-items: center; gap: 1rem; padding: 1.4rem 1.5rem; }
.item + .item { border-left: 1px solid var(--border); }
.icon {
  flex: none; display: grid; place-items: center; width: 64px; height: 64px;
  border-radius: 50%; background: var(--pink-100); color: var(--pink-600);
}
.label { font-weight: 600; color: var(--purple-900); line-height: 1.3; }
.item:hover .label { color: var(--pink-600); }

@media (max-width: 900px) {
  .highlights { grid-template-columns: repeat(2, 1fr); }
  .item:nth-child(3) { border-left: 0; }
  .item:nth-child(n + 3) { border-top: 1px solid var(--border); }
}
@media (max-width: 520px) {
  .highlights { grid-template-columns: 1fr; }
  .item + .item { border-left: 0; border-top: 1px solid var(--border); }
}
</style>
