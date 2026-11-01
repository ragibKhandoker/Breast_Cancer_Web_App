// Converts between a module's content and the editor's working copy. Tables are edited as text
// ("Column | Column" on one line, one row per line), so the working copy keeps them as two strings.

export const LAYOUTS = [
  { value: '', label: 'Text only' },
  { value: 'cards', label: 'Cards' },
  { value: 'timeline', label: 'Timeline' },
  { value: 'steps', label: 'Numbered steps' },
  { value: 'stages', label: 'Stages' },
  { value: 'birads', label: 'BI-RADS guide' },
]
export const DIAGRAMS = [
  { value: '', label: 'None' },
  { value: 'anatomy', label: 'Breast anatomy' },
  { value: 'cell-growth', label: 'Normal and cancer cells' },
  { value: 'receptors', label: 'ER, PR, HER2, Ki-67' },
  { value: 'grade', label: 'Tumour grade' },
  { value: 'tnm', label: 'TNM (interactive)' },
]
export const ILLUSTRATIONS = ['book', 'history', 'cells', 'classification', 'microscope', 'stages', 'dna', 'symptoms',
  'diagnosis', 'mammogram', 'treatment', 'surgery', 'survivorship']

export function blankSection() {
  return { id: '', title: '', body: '', advanced: '', display: '', items: [], diagram: '', imageFileId: '', imageCaption: '', tableColumns: '', tableRows: '' }
}

export function blankContent() {
  return {
    slug: '', order: 50, title: '', category: '', illustration: 'book', readingMinutes: 5, description: '',
    sections: [blankSection()], references: [{ title: '', publisher: '', url: '' }], related: [],
  }
}

export function blankItem() {
  return { label: '', title: '', text: '', note: '' }
}

/** A module's content as the editor's working copy. */
export function toEditable(content) {
  const c = structuredClone(content)
  return {
    ...c,
    sections: (c.sections ?? []).map((s) => ({
      id: s.id ?? '', title: s.title ?? '', body: s.body ?? '', advanced: s.advanced ?? '', display: s.display ?? '',
      items: (s.items ?? []).map((i) => ({ ...blankItem(), ...i })), diagram: s.diagram ?? '',
      imageFileId: s.imageFileId ?? '', imageCaption: s.imageCaption ?? '',
      tableColumns: s.table?.columns?.join(' | ') ?? '',
      tableRows: (s.table?.rows ?? []).map((r) => r.join(' | ')).join('\n'),
    })),
    references: (c.references ?? []).map((r) => ({ title: r.title ?? '', publisher: r.publisher ?? '', url: r.url ?? '' })),
    related: [...(c.related ?? [])],
  }
}

const cells = (line) => line.split('|').map((v) => v.trim())

/** The working copy as the API expects it. Empty optional parts are left out. */
export function toPayload(e) {
  return {
    slug: e.slug.trim(),
    order: Number(e.order) || null,
    title: e.title,
    category: e.category,
    illustration: e.illustration,
    readingMinutes: Number(e.readingMinutes) || null,
    description: e.description,
    sections: e.sections.map((s) => {
      const columns = s.tableColumns.trim() ? cells(s.tableColumns) : []
      const rows = s.tableRows.split('\n').filter((l) => l.trim()).map(cells)
      return {
        id: s.id || null,
        title: s.title,
        body: s.body || null,
        advanced: s.advanced || null,
        display: s.display || null,
        items: s.display ? s.items.filter((i) => i.title || i.text) : null,
        table: columns.length ? { columns, rows } : null,
        diagram: s.diagram || null,
        imageFileId: s.imageFileId || null,
        imageCaption: s.imageFileId ? s.imageCaption || null : null,
      }
    }),
    references: e.references.filter((r) => r.title || r.url),
    related: e.related,
  }
}

/** "sections[2].items[0].title" → "Section 3 · item 1 · title" */
export function fieldLabel(key) {
  return key
    .replace(/sections\[(\d+)\]/g, (_, n) => `Section ${Number(n) + 1}`)
    .replace(/items\[(\d+)\]/g, (_, n) => `item ${Number(n) + 1}`)
    .replace(/references\[(\d+)\]/g, (_, n) => `Reference ${Number(n) + 1}`)
    .replaceAll('.', ' · ')
}
