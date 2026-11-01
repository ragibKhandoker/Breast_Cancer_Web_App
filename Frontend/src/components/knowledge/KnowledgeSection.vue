<script setup>
import { ref, computed } from 'vue'
import KnowledgeBody from './KnowledgeBody.vue'
import KnowledgeDiagram from './KnowledgeDiagram.vue'
import { imageUrl } from '../../services/knowledgeService'

// One lesson section: its text, then (when set) a diagram, an uploaded picture, a table and items drawn as a
// timeline, cards, numbered steps, stages or the interactive BI-RADS guide. "Learn more" holds the advanced part.
const props = defineProps({ section: { type: Object, required: true } })

const items = computed(() => props.section.items ?? [])
const birads = ref(0)
const chosen = computed(() => items.value[birads.value])
const BIRADS_TONES = ['#6b3fb5', '#2e7d32', '#2e7d32', '#b26a00', '#d84315', '#c62828', '#8e1c4f']
</script>

<template>
  <section :id="section.id" class="kb-section">
    <h2>{{ section.title }}</h2>

    <KnowledgeBody v-if="section.body" :text="section.body" />

    <KnowledgeDiagram v-if="section.diagram" :name="section.diagram" />

    <figure v-if="section.imageFileId" class="upload">
      <img :src="imageUrl(section.imageFileId)" :alt="section.imageCaption || section.title" loading="lazy" />
      <figcaption v-if="section.imageCaption">{{ section.imageCaption }}</figcaption>
    </figure>

    <div v-if="section.table" class="table-wrap">
      <table>
        <thead><tr><th v-for="(c, i) in section.table.columns" :key="i" scope="col">{{ c }}</th></tr></thead>
        <tbody>
          <tr v-for="(row, r) in section.table.rows" :key="r">
            <component :is="c === 0 ? 'th' : 'td'" v-for="(cell, c) in row" :key="c" :scope="c === 0 ? 'row' : undefined">{{ cell }}</component>
          </tr>
        </tbody>
      </table>
    </div>

    <ol v-if="section.display === 'timeline'" class="timeline">
      <li v-for="(item, i) in items" :key="i">
        <span class="dot" aria-hidden="true"></span>
        <span class="when">{{ item.label }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.text }}</p>
        <p v-if="item.note" class="why"><strong>Why it mattered:</strong> {{ item.note }}</p>
      </li>
    </ol>

    <div v-else-if="section.display === 'cards'" class="cards">
      <article v-for="(item, i) in items" :key="i" class="item-card">
        <span v-if="item.label" class="chip">{{ item.label }}</span>
        <h3 v-if="item.title">{{ item.title }}</h3>
        <p v-if="item.text">{{ item.text }}</p>
        <p v-if="item.note" class="item-note">{{ item.note }}</p>
      </article>
    </div>

    <ol v-else-if="section.display === 'steps'" class="steps">
      <li v-for="(item, i) in items" :key="i">
        <span class="num" aria-hidden="true">{{ i + 1 }}</span>
        <div>
          <h3>{{ item.title }}</h3>
          <p v-if="item.text">{{ item.text }}</p>
          <p v-if="item.note" class="item-note">{{ item.note }}</p>
        </div>
      </li>
    </ol>

    <ol v-else-if="section.display === 'stages'" class="stages">
      <li v-for="(item, i) in items" :key="i" :style="{ '--tone': `hsl(${330 - i * 18} 70% ${92 - i * 9}%)` }">
        <span class="stage-label">{{ item.label }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.text }}</p>
      </li>
    </ol>

    <div v-else-if="section.display === 'birads'" class="birads">
      <div class="birads-buttons" role="tablist" aria-label="BI-RADS categories">
        <button v-for="(item, i) in items" :id="`birads-tab-${i}`" :key="i" type="button" role="tab"
          :aria-selected="birads === i" aria-controls="birads-panel" :class="{ active: birads === i }"
          :style="{ '--tone': BIRADS_TONES[i] ?? '#6b3fb5' }" @click="birads = i">
          <strong>{{ item.label }}</strong><span>{{ item.title }}</span>
        </button>
      </div>
      <div v-if="chosen" id="birads-panel" class="birads-panel" role="tabpanel" :aria-labelledby="`birads-tab-${birads}`"
        :style="{ '--tone': BIRADS_TONES[birads] ?? '#6b3fb5' }">
        <h3>BI-RADS {{ chosen.label }}: {{ chosen.title }}</h3>
        <p><strong>What it means:</strong> {{ chosen.text }}</p>
        <p v-if="chosen.note"><strong>What may happen next:</strong> {{ chosen.note }}</p>
      </div>
      <p class="birads-disclaimer" role="note">BI-RADS is an imaging assessment category, not a cancer stage. Only a biopsy can confirm cancer.</p>
    </div>

    <details v-if="section.advanced" class="advanced">
      <summary>Learn more (advanced)</summary>
      <KnowledgeBody :text="section.advanced" />
    </details>
  </section>
</template>

<style scoped>
.kb-section { display: grid; gap: 1rem; scroll-margin-top: 90px; }
h2 { margin: 0; color: #2a1650; font-size: 1.4rem; }
h3 { margin: 0; color: #2a1650; font-size: 1.02rem; }
p { margin: 0; color: #3d3557; line-height: 1.65; }

.upload { margin: 0; }
.upload img { width: 100%; max-height: 420px; object-fit: contain; border-radius: 14px; border: 1px solid #f3d4e3; background: #fff; }
.upload figcaption { margin-top: 0.4rem; font-size: 0.85rem; color: #6f6787; text-align: center; }

.table-wrap { overflow-x: auto; border: 1px solid #f3d4e3; border-radius: 14px; background: #fff; }
table { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
th, td { padding: 0.7rem 0.85rem; text-align: left; vertical-align: top; border-bottom: 1px solid #f8e3ed; color: #3d3557; }
thead th { background: #fff0f6; color: #2a1650; font-weight: 600; }
tbody th { color: #2a1650; font-weight: 600; white-space: nowrap; }
tbody tr:last-child > * { border-bottom: 0; }

.timeline { list-style: none; margin: 0; padding: 0 0 0 1.6rem; border-left: 3px solid #f7a8c8; display: grid; gap: 1.1rem; }
.timeline li { position: relative; display: grid; gap: 0.3rem; padding: 1rem 1.1rem; background: #fff; border: 1px solid #f3d4e3; border-radius: 14px; box-shadow: 0 6px 18px rgba(216, 27, 106, 0.06); }
.dot { position: absolute; left: calc(-1.6rem - 9px); top: 1.2rem; width: 15px; height: 15px; border-radius: 50%; background: #d81b6a; border: 3px solid #fff; box-shadow: 0 0 0 2px #f7a8c8; }
.when { font-size: 0.82rem; font-weight: 700; color: #d81b6a; letter-spacing: 0.02em; }
.why { font-size: 0.92rem; color: #4b3f6b; background: #faf6ff; border-radius: 10px; padding: 0.55rem 0.75rem; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.85rem; }
.item-card { display: grid; align-content: start; gap: 0.45rem; padding: 1rem; background: #fff; border: 1px solid #f3d4e3; border-top: 4px solid #e84a8a; border-radius: 14px; }
.chip { justify-self: start; padding: 0.15rem 0.6rem; border-radius: 999px; background: #fde3ef; color: #a3134f; font-size: 0.75rem; font-weight: 600; }
.item-note { font-size: 0.88rem; color: #5a4f78; border-top: 1px dashed #f3d4e3; padding-top: 0.45rem; }

.steps { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.75rem; }
.steps li { display: grid; grid-template-columns: auto 1fr; gap: 0.85rem; align-items: start; padding: 0.95rem 1rem; background: #fff; border: 1px solid #f3d4e3; border-radius: 14px; }
.steps li > div { display: grid; gap: 0.3rem; }
.num { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; background: #d81b6a; color: #fff; font-weight: 700; }

.stages { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.6rem; }
.stages li { display: grid; align-content: start; gap: 0.35rem; padding: 0.9rem; border-radius: 14px; background: var(--tone); border: 1px solid #f3d4e3; }
.stage-label { font-weight: 700; color: #2a1650; font-size: 1.05rem; }

.birads { display: grid; gap: 0.85rem; }
.birads-buttons { display: grid; grid-template-columns: repeat(auto-fit, minmax(92px, 1fr)); gap: 0.5rem; }
.birads-buttons button {
  display: grid; gap: 0.15rem; padding: 0.65rem 0.5rem; border-radius: 12px; border: 2px solid #f3d4e3;
  background: #fff; cursor: pointer; font-family: inherit; color: #2a1650; text-align: center;
}
.birads-buttons button strong { font-size: 1.3rem; color: var(--tone); }
.birads-buttons button span { font-size: 0.78rem; line-height: 1.3; }
.birads-buttons button.active { border-color: var(--tone); background: #fff7fb; box-shadow: 0 6px 16px rgba(42, 22, 80, 0.12); }
.birads-buttons button:focus-visible { outline: 3px solid #f7a8c8; outline-offset: 2px; }
.birads-panel { display: grid; gap: 0.5rem; padding: 1.1rem 1.2rem; border-radius: 14px; background: #fff; border: 1px solid #f3d4e3; border-left: 6px solid var(--tone); }
.birads-disclaimer { padding: 0.75rem 1rem; border-radius: 12px; background: #fff4e5; color: #6b3a00; font-weight: 500; font-size: 0.92rem; }

.advanced { border: 1px solid #e3dbf5; border-radius: 14px; background: #faf8ff; padding: 0.2rem 1rem; }
.advanced summary { cursor: pointer; padding: 0.7rem 0; font-weight: 600; color: #6b3fb5; }
.advanced[open] { padding-bottom: 1rem; }
</style>
