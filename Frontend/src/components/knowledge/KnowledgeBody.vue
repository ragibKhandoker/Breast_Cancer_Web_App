<script setup>
import { computed } from 'vue'

// Lesson text with three plain-text conventions: a blank line starts a paragraph, "- " starts a bullet and
// "> " starts a highlighted note. Everything is shown as text, so stored content can never inject HTML.
const props = defineProps({ text: { type: String, default: '' } })

const blocks = computed(() => {
  const out = []
  for (const chunk of (props.text ?? '').split(/\n\s*\n/)) {
    let list = null
    let para = []
    const flush = () => {
      if (para.length) out.push({ type: 'p', text: para.join(' ') })
      para = []
    }
    for (const raw of chunk.split('\n')) {
      const line = raw.trim()
      if (!line) continue
      if (line.startsWith('- ')) {
        flush()
        if (!list) out.push((list = { type: 'ul', items: [] }))
        list.items.push(line.slice(2))
      } else if (line.startsWith('> ')) {
        flush()
        list = null
        out.push({ type: 'note', text: line.slice(2) })
      } else {
        list = null
        para.push(line)
      }
    }
    flush()
  }
  return out
})
</script>

<template>
  <div class="kb-body">
    <template v-for="(block, i) in blocks" :key="i">
      <p v-if="block.type === 'p'">{{ block.text }}</p>
      <ul v-else-if="block.type === 'ul'">
        <li v-for="(item, j) in block.items" :key="j">{{ item }}</li>
      </ul>
      <p v-else class="note" role="note">{{ block.text }}</p>
    </template>
  </div>
</template>

<style scoped>
.kb-body { display: grid; gap: 0.75rem; color: #3d3557; line-height: 1.75; font-size: 1rem; }
.kb-body p, .kb-body ul { margin: 0; }
.kb-body ul { padding-left: 1.25rem; display: grid; gap: 0.35rem; }
.kb-body li::marker { color: #d81b6a; }
.note {
  padding: 0.85rem 1rem; border-left: 4px solid #d81b6a; border-radius: 10px;
  background: #fff0f6; color: #2a1650; font-weight: 500;
}
</style>
