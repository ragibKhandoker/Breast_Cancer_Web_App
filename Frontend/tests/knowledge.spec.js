import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import KnowledgeBody from '../src/components/knowledge/KnowledgeBody.vue'
import KnowledgeSection from '../src/components/knowledge/KnowledgeSection.vue'
import KnowledgeHubView from '../src/views/KnowledgeHubView.vue'
import KnowledgeModuleView from '../src/views/KnowledgeModuleView.vue'
import { toEditable, toPayload, fieldLabel, blankContent } from '../src/components/knowledge/knowledgeEditing'
import { LOCAL_KEY } from '../src/composables/useLearningProgress'
import { routes } from '../src/router'
import * as api from '../src/services/knowledgeService'
import { authState } from '../src/auth/authStore'

vi.mock('../src/auth/authStore', async () => {
  const { reactive: r } = await import('vue')
  const fn = () => () => {}
  return {
    authState: r({ user: null, ready: true }), onSessionEnd: fn, initAuth: async () => null, hasRole: () => false,
    signIn: fn, signOut: fn, useAnotherAccount: fn, endSession: fn, updateCurrentUser: fn, markPasswordChangeRequired: fn,
  }
})
vi.mock('../src/services/knowledgeService', async (importOriginal) => ({
  ...(await importOriginal()),
  listModules: vi.fn(),
  getModule: vi.fn(),
  getGlossary: vi.fn(),
  getFaqs: vi.fn(),
  getProgress: vi.fn(),
  setCompleted: vi.fn(),
  setBookmarked: vi.fn(),
  setLast: vi.fn(),
  mergeProgress: vi.fn(),
}))

const summaries = [
  { slug: 'history', order: 1, title: 'History of Breast Cancer', category: 'Foundations', illustration: 'history', readingMinutes: 7,
    description: 'From ancient texts to AI.', sectionTitles: ['Timeline of key developments'] },
  { slug: 'mammography-birads', order: 9, title: 'Mammography and BI-RADS', category: 'Diagnosis', illustration: 'mammogram',
    readingMinutes: 7, description: 'How a mammogram works.', sectionTitles: ['BI-RADS categories'] },
  { slug: 'treatment', order: 10, title: 'Breast Cancer Treatment', category: 'Treatment', illustration: 'treatment', readingMinutes: 9,
    description: 'Local and systemic treatment.', sectionTitles: ['Local treatment'] },
]
const birads = {
  slug: 'mammography-birads', order: 9, title: 'Mammography and BI-RADS', category: 'Diagnosis', illustration: 'mammogram', readingMinutes: 7,
  description: 'How a mammogram works.', related: ['history', 'unknown'],
  sections: [
    { id: 'mammogram', title: 'What a mammogram is', body: 'A low-dose X-ray.\n- 2D\n- 3D', advanced: 'Density categories.' },
    { id: 'birads', title: 'BI-RADS categories', display: 'birads', items: [
      { label: '0', title: 'Incomplete', text: 'Needs more imaging.', note: 'More views.' },
      { label: '4', title: 'Suspicious', text: 'Cancer is possible.', note: 'A biopsy should be considered.' },
    ] },
  ],
  references: [{ title: 'Understanding Your Mammogram Report', publisher: 'American Cancer Society', url: 'https://www.cancer.org/x' }],
}

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/knowledge', component: KnowledgeHubView },
      { path: '/knowledge/:slug', component: KnowledgeModuleView },
      { path: '/login', component: { template: '<p>login</p>' } },
    ],
  })
}
async function mountAt(path, component) {
  const router = makeRouter()
  router.push(path)
  await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  authState.user = null
  api.listModules.mockResolvedValue(summaries)
  api.getGlossary.mockResolvedValue([{ term: 'HER2', definition: 'A protein that helps cells grow.' }, { term: 'Biopsy', definition: 'A tissue sample.' }])
  api.getFaqs.mockResolvedValue([{ question: 'Is every breast lump cancer?', answer: 'No.' }])
  api.getModule.mockResolvedValue(birads)
  window.scrollTo = vi.fn()
  Element.prototype.scrollIntoView = vi.fn()
})

describe('lesson text', () => {
  it('turns plain text into paragraphs, bullets and notes, never HTML', () => {
    const w = mount(KnowledgeBody, { props: { text: 'First line\nsame paragraph.\n\n- one\n- two\n> Important <b>note</b>' } })
    expect(w.findAll('p')[0].text()).toBe('First line same paragraph.')
    expect(w.findAll('li').map((l) => l.text())).toEqual(['one', 'two'])
    expect(w.find('.note').text()).toBe('Important <b>note</b>')
    expect(w.find('b').exists()).toBe(false)
  })

  it('switches BI-RADS categories and always shows the disclaimer', async () => {
    const w = mount(KnowledgeSection, { props: { section: birads.sections[1] } })
    expect(w.find('#birads-panel').text()).toContain('Incomplete')
    await w.findAll('[role="tab"]')[1].trigger('click')
    expect(w.find('#birads-panel').text()).toContain('A biopsy should be considered.')
    expect(w.text()).toContain('BI-RADS is an imaging assessment category, not a cancer stage.')
  })
})

describe('Knowledge Hub page', () => {
  it('shows the title, subtitle and a card per module with an Explore More link', async () => {
    const w = await mountAt('/knowledge', KnowledgeHubView)
    expect(w.find('h1').text()).toBe('Breast Cancer Knowledge Hub')
    expect(w.find('.subtitle').text()).toContain('from understanding its history and classifications to diagnosis, treatment, and recovery.')
    const cards = w.findAll('.module-card')
    expect(cards).toHaveLength(3)
    expect(cards[0].text()).toContain('7 min read')
    expect(cards[0].find('a.btn').text()).toBe('Explore More')
    expect(cards[0].find('a.btn').attributes('href')).toBe('/knowledge/history')
    expect(w.text()).toContain('Is every breast lump cancer?')
  })

  it('searches modules and glossary, and filters by category', async () => {
    const w = await mountAt('/knowledge', KnowledgeHubView)
    await w.find('.search input').setValue('bi-rads')
    expect(w.findAll('.module-card').map((c) => c.find('h3').text())).toEqual(['Mammography and BI-RADS'])
    await w.find('.search input').setValue('her2')
    expect(w.find('.quick-terms').text()).toContain('A protein that helps cells grow.')
    await w.find('.search input').setValue('')
    await w.findAll('.categories button').find((b) => b.text() === 'Treatment').trigger('click')
    expect(w.findAll('.module-card')).toHaveLength(1)
  })

  it('keeps a visitor’s bookmarks in this browser', async () => {
    const w = await mountAt('/knowledge', KnowledgeHubView)
    await w.findAll('.bookmark')[1].trigger('click')
    expect(JSON.parse(localStorage.getItem(LOCAL_KEY)).bookmarks).toEqual(['mammography-birads'])
    expect(api.setBookmarked).not.toHaveBeenCalled()
    expect(w.find('.bookmarks').text()).toContain('Mammography and BI-RADS')
  })
})

describe('lesson page', () => {
  it('shows contents, sections, references, related lessons and previous/next links', async () => {
    const w = await mountAt('/knowledge/mammography-birads', KnowledgeModuleView)
    expect(w.find('h1').text()).toBe('Mammography and BI-RADS')
    expect(w.findAll('.toc a').map((a) => a.text())).toEqual(['What a mammogram is', 'BI-RADS categories', 'References'])
    expect(w.find('.advanced summary').text()).toBe('Learn more (advanced)')
    const ref = w.find('.refs a')
    expect(ref.attributes('href')).toBe('https://www.cancer.org/x')
    expect(ref.attributes('rel')).toContain('noopener')
    expect(w.findAll('.related-card').map((a) => a.text())).toEqual([expect.stringContaining('History of Breast Cancer')])
    const pager = w.findAll('.pager-link')
    expect(pager[0].attributes('href')).toBe('/knowledge/history')
    expect(pager[1].attributes('href')).toBe('/knowledge/treatment')
    // Recorded for "Continue learning" (in this browser for a visitor).
    expect(JSON.parse(localStorage.getItem(LOCAL_KEY)).lastSlug).toBe('mammography-birads')
  })

  it('saves progress to the account when signed in, adding what was done before signing in', async () => {
    localStorage.setItem(LOCAL_KEY, JSON.stringify({ completed: ['history'], bookmarks: [] }))
    authState.user = { id: 'u1', role: 'PATIENT' }
    api.getProgress.mockResolvedValue({ completed: [], bookmarks: [], lastSlug: null })
    api.mergeProgress.mockResolvedValue({ completed: ['history'], bookmarks: [], lastSlug: null })
    api.setCompleted.mockResolvedValue({ completed: ['history', 'mammography-birads'], bookmarks: [], lastSlug: 'mammography-birads' })
    api.setLast.mockResolvedValue({})

    const w = await mountAt('/knowledge/mammography-birads', KnowledgeModuleView)
    expect(api.mergeProgress).toHaveBeenCalledWith(['history'], [])
    expect(localStorage.getItem(LOCAL_KEY)).toBeNull()
    expect(api.setLast).toHaveBeenCalledWith('mammography-birads')

    await w.find('.finish button').trigger('click')
    await flushPromises()
    expect(api.setCompleted).toHaveBeenCalledWith('mammography-birads', true)
    expect(w.find('.done-text').text()).toContain('Lesson completed')
  })

  it('says so when a lesson does not exist', async () => {
    api.getModule.mockRejectedValue({ response: { status: 404 } })
    const w = await mountAt('/knowledge/nope', KnowledgeModuleView)
    expect(w.find('h1').text()).toBe('Lesson not found')
  })
})

describe('content editing', () => {
  it('round-trips tables and drops empty optional parts', () => {
    const content = {
      ...blankContent(), slug: 'x', title: 'X', category: 'C', description: 'Long enough text.',
      sections: [{ id: 'a', title: 'A', body: 'Text', table: { columns: ['One', 'Two'], rows: [['1', '2'], ['3', '4']] } }],
      references: [{ title: 'R', publisher: '', url: 'https://www.who.int' }, { title: '', publisher: '', url: '' }],
    }
    const payload = toPayload(toEditable(content))
    expect(payload.sections[0].table).toEqual({ columns: ['One', 'Two'], rows: [['1', '2'], ['3', '4']] })
    expect(payload.sections[0].items).toBeNull()
    expect(payload.sections[0].imageFileId).toBeNull()
    expect(payload.references).toHaveLength(1)
  })

  it('names fields from server errors in plain words', () => {
    expect(fieldLabel('sections[2].items[0].title')).toBe('Section 3 · item 1 · title')
    expect(fieldLabel('references[0].url')).toBe('Reference 1 · url')
  })
})

describe('navigation', () => {
  it('has public Knowledge Hub routes and sends /learn there', () => {
    const hub = routes.find((r) => r.path === '/knowledge')
    expect(hub.meta.roles).toBeUndefined()
    expect(routes.find((r) => r.path === '/knowledge/:slug').meta.roles).toBeUndefined()
    expect(routes.find((r) => r.path === '/learn').redirect).toBe('/knowledge')
  })
})

