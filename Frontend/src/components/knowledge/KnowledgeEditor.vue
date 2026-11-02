<script setup>
import { ref } from 'vue'
import AppIcon from '../common/AppIcon.vue'
import { LAYOUTS, DIAGRAMS, ILLUSTRATIONS, blankSection, blankItem, fieldLabel } from './knowledgeEditing'
import { adminUploadImage, imageUrl } from '../../services/knowledgeService'
import { errorMessage } from '../../services/http'

// Form for a Knowledge Hub module's content, used by administrators (with diagram uploads) and by Breast
// Surgeons proposing content. It edits the working copy from knowledgeEditing.js in place.
const content = defineModel({ type: Object, required: true })
defineProps({
  modules: { type: Array, default: () => [] },
  slugLocked: { type: Boolean, default: false },
  allowImages: { type: Boolean, default: false },
  errors: { type: Object, default: () => ({}) },
})

const open = ref(0)
const uploading = ref(-1)
const uploadError = ref('')

function move(list, i, step) {
  const j = i + step
  if (j < 0 || j >= list.length) return
  ;[list[i], list[j]] = [list[j], list[i]]
}
function addSection() {
  content.value.sections.push(blankSection())
  open.value = content.value.sections.length - 1
}
function toggleRelated(slug) {
  const list = content.value.related
  const at = list.indexOf(slug)
  if (at >= 0) list.splice(at, 1)
  else list.push(slug)
}
async function upload(section, i, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = i
  uploadError.value = ''
  try {
    section.imageFileId = (await adminUploadImage(file)).fileId
  } catch (err) {
    uploadError.value = errorMessage(err)
  } finally {
    uploading.value = -1
  }
}
</script>

<template>
  <div class="editor">
    <ul v-if="Object.keys(errors).length" class="errors" role="alert">
      <li v-for="(msg, key) in errors" :key="key"><strong>{{ fieldLabel(key) }}:</strong> {{ msg }}</li>
    </ul>

    <fieldset class="group">
      <legend>Module</legend>
      <div class="row">
        <label class="f grow">Title <input v-model="content.title" maxlength="120" required /></label>
        <label class="f">Address (slug)
          <input v-model="content.slug" :disabled="slugLocked" maxlength="60" placeholder="e.g. self-check" />
        </label>
      </div>
      <div class="row">
        <label class="f grow">Category
          <input v-model="content.category" list="kh-categories" maxlength="40" placeholder="Foundations" />
          <datalist id="kh-categories">
            <option v-for="c in [...new Set(modules.map((m) => m.category))]" :key="c" :value="c" />
          </datalist>
        </label>
        <label class="f">Illustration
          <select v-model="content.illustration"><option v-for="i in ILLUSTRATIONS" :key="i" :value="i">{{ i }}</option></select>
        </label>
        <label class="f small-f">Minutes <input v-model.number="content.readingMinutes" type="number" min="1" max="120" /></label>
        <label class="f small-f">Order <input v-model.number="content.order" type="number" min="1" max="999" /></label>
      </div>
      <label class="f">Short description <textarea v-model="content.description" rows="2" maxlength="400"></textarea></label>
    </fieldset>

    <fieldset class="group">
      <legend>YouTube video (optional)</legend>
      <p class="hint">From a trusted channel (hospital, cancer charity, health service). Shown at the top of the lesson.</p>
      <label class="f">Video link <input v-model="content.videoUrl" placeholder="https://www.youtube.com/watch?v=…" maxlength="200" /></label>
      <div class="row">
        <label class="f grow">Video title <input v-model="content.videoTitle" maxlength="200" /></label>
        <label class="f grow">Channel <input v-model="content.videoChannel" maxlength="120" placeholder="e.g. Mayo Clinic" /></label>
      </div>
    </fieldset>

    <fieldset class="group">
      <legend>Sections</legend>
      <p class="hint">In text: an empty line starts a paragraph, “- ” starts a bullet, “&gt; ” starts a highlighted note.</p>
      <div v-for="(s, i) in content.sections" :key="i" class="section-box" :class="{ open: open === i }">
        <div class="section-bar">
          <button type="button" class="section-title" :aria-expanded="open === i" @click="open = open === i ? -1 : i">
            {{ i + 1 }}. {{ s.title || 'Untitled section' }}
          </button>
          <div class="mini-actions">
            <button type="button" aria-label="Move up" :disabled="i === 0" @click="move(content.sections, i, -1)">↑</button>
            <button type="button" aria-label="Move down" :disabled="i === content.sections.length - 1" @click="move(content.sections, i, 1)">↓</button>
            <button type="button" aria-label="Remove section" :disabled="content.sections.length === 1" @click="content.sections.splice(i, 1)">
              <AppIcon name="trash" :size="14" />
            </button>
          </div>
        </div>
        <div v-if="open === i" class="section-fields">
          <label class="f">Section title <input v-model="s.title" maxlength="120" /></label>
          <label class="f">Text <textarea v-model="s.body" rows="6"></textarea></label>
          <label class="f">Advanced details (shown under “Learn more”) <textarea v-model="s.advanced" rows="3"></textarea></label>
          <div class="row">
            <label class="f">Layout for items
              <select v-model="s.display"><option v-for="l in LAYOUTS" :key="l.value" :value="l.value">{{ l.label }}</option></select>
            </label>
            <label class="f">Diagram
              <select v-model="s.diagram"><option v-for="d in DIAGRAMS" :key="d.value" :value="d.value">{{ d.label }}</option></select>
            </label>
          </div>

          <div v-if="s.display" class="items">
            <div v-for="(item, j) in s.items" :key="j" class="item-row">
              <input v-model="item.label" placeholder="Label (e.g. year, stage)" maxlength="80" />
              <input v-model="item.title" placeholder="Title" maxlength="160" />
              <textarea v-model="item.text" rows="2" placeholder="Text"></textarea>
              <textarea v-model="item.note" rows="2" placeholder="Note (why it matters / next step)"></textarea>
              <div class="mini-actions">
                <button type="button" aria-label="Move item up" :disabled="j === 0" @click="move(s.items, j, -1)">↑</button>
                <button type="button" aria-label="Remove item" @click="s.items.splice(j, 1)"><AppIcon name="trash" :size="14" /></button>
              </div>
            </div>
            <button type="button" class="btn btn-outline btn-sm" @click="s.items.push(blankItem())">Add item</button>
          </div>

          <details class="table-edit" :open="!!s.tableColumns">
            <summary>Table (optional)</summary>
            <label class="f">Columns, separated by | <input v-model="s.tableColumns" placeholder="Subtype | Receptors | Meaning" /></label>
            <label class="f">Rows: one per line, cells separated by | <textarea v-model="s.tableRows" rows="4"></textarea></label>
          </details>

          <div v-if="allowImages" class="image-edit">
            <img v-if="s.imageFileId" :src="imageUrl(s.imageFileId)" alt="Uploaded diagram" />
            <div class="image-actions">
              <label class="btn btn-outline btn-sm upload-btn">
                <AppIcon name="upload" :size="14" /> {{ uploading === i ? 'Uploading…' : s.imageFileId ? 'Replace diagram' : 'Upload diagram' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploading >= 0" @change="upload(s, i, $event)" />
              </label>
              <button v-if="s.imageFileId" type="button" class="btn btn-outline btn-sm" @click="s.imageFileId = ''">Remove</button>
            </div>
            <label v-if="s.imageFileId" class="f">Caption <input v-model="s.imageCaption" maxlength="200" /></label>
            <p v-if="uploadError && uploading < 0" class="error small">{{ uploadError }}</p>
          </div>
        </div>
      </div>
      <button type="button" class="btn btn-outline btn-sm" @click="addSection">Add section</button>
    </fieldset>

    <fieldset class="group">
      <legend>References</legend>
      <p class="hint">Trusted sources only (WHO, NCI, ACS, ACR, Breast Cancer Now, RadiologyInfo). Links must start with https://</p>
      <div v-for="(r, i) in content.references" :key="i" class="ref-row">
        <input v-model="r.title" placeholder="Title" maxlength="250" />
        <input v-model="r.publisher" placeholder="Publisher" maxlength="120" />
        <input v-model="r.url" placeholder="https://" maxlength="500" />
        <button type="button" class="icon-btn" aria-label="Remove reference" @click="content.references.splice(i, 1)"><AppIcon name="trash" :size="14" /></button>
      </div>
      <button type="button" class="btn btn-outline btn-sm" @click="content.references.push({ title: '', publisher: '', url: '' })">Add reference</button>
    </fieldset>

    <fieldset v-if="modules.length" class="group">
      <legend>Related lessons</legend>
      <div class="related">
        <label v-for="m in modules.filter((m) => m.slug !== content.slug)" :key="m.slug" class="check">
          <input type="checkbox" :checked="content.related.includes(m.slug)" @change="toggleRelated(m.slug)" /> {{ m.title }}
        </label>
      </div>
    </fieldset>
  </div>
</template>

<style scoped>
.editor { display: grid; gap: 1rem; }
.group { margin: 0; padding: 1rem; border: 1px solid var(--border); border-radius: 14px; display: grid; gap: 0.7rem; min-width: 0; }
legend { padding: 0 0.4rem; font-weight: 700; color: var(--purple-900); }
.row { display: flex; flex-wrap: wrap; gap: 0.7rem; }
.f { display: grid; gap: 0.3rem; font-size: 0.85rem; font-weight: 600; color: var(--purple-900); min-width: 150px; }
.grow { flex: 1 1 240px; }
.small-f { min-width: 90px; width: 90px; }
input, select, textarea {
  width: 100%; padding: 0.55rem 0.7rem; border: 1.5px solid #f3d4e3; border-radius: 10px; background: #fff; color: #2a1650;
  font: 400 0.92rem 'Poppins', sans-serif;
}
input:focus, select:focus, textarea:focus { outline: 0; border-color: #e84a8a; }
input:disabled { background: #f3f1f6; color: #6f6787; }
textarea { resize: vertical; line-height: 1.5; }
.hint { margin: 0; font-size: 0.82rem; color: var(--muted); }
.errors { margin: 0; padding: 0.8rem 1rem 0.8rem 2rem; border-radius: 12px; background: #fdecea; color: #8e1c1c; border: 1px solid #f5b5b0; font-size: 0.88rem; }
.section-box { border: 1px solid var(--border); border-radius: 12px; overflow: hidden; }
.section-box.open { border-color: #e84a8a; }
.section-bar { display: flex; align-items: center; gap: 0.5rem; padding: 0.3rem 0.5rem; }
.section-title { flex: 1; text-align: left; padding: 0.5rem; background: none; border: 0; cursor: pointer; font: 600 0.92rem 'Poppins', sans-serif; color: var(--purple-900); }
.mini-actions { display: flex; gap: 0.25rem; }
.mini-actions button, .icon-btn {
  display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; border: 1px solid #f3d4e3; background: #fff; color: #6b3fb5; cursor: pointer;
}
.mini-actions button:disabled { opacity: 0.4; cursor: not-allowed; }
.section-fields { display: grid; gap: 0.7rem; padding: 0.5rem 0.9rem 1rem; }
.items { display: grid; gap: 0.6rem; }
.item-row { display: grid; grid-template-columns: 1fr 1.4fr; gap: 0.4rem; padding: 0.6rem; border: 1px dashed var(--border); border-radius: 10px; }
.item-row .mini-actions { grid-column: 1 / -1; justify-content: flex-end; }
.table-edit summary { cursor: pointer; font-weight: 600; font-size: 0.88rem; color: var(--purple-900); }
.table-edit[open] { display: grid; gap: 0.5rem; }
.image-edit { display: grid; gap: 0.5rem; }
.image-edit img { max-height: 180px; width: auto; border-radius: 10px; border: 1px solid #f3d4e3; background: #fff; }
.image-actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.upload-btn { position: relative; overflow: hidden; display: inline-flex; align-items: center; gap: 0.35rem; cursor: pointer; }
.upload-btn input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.ref-row { display: grid; grid-template-columns: 1.4fr 1fr 1.6fr auto; gap: 0.4rem; align-items: center; }
.related { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.35rem; }
.check { display: flex; align-items: center; gap: 0.45rem; font-size: 0.88rem; color: var(--text); cursor: pointer; }
.check input { width: auto; }
.small { font-size: 0.85rem; margin: 0; }
.group > .btn { justify-self: start; }
@media (max-width: 700px) {
  .item-row, .ref-row { grid-template-columns: minmax(0, 1fr); }
}
</style>
