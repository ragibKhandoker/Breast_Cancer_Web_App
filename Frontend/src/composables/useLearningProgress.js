import { reactive, readonly, watch } from 'vue'
import { authState, onSessionEnd } from '../auth/authStore'
import * as api from '../services/knowledgeService'

// Knowledge Hub progress shared by the hub and lesson pages. Signed-in users keep it in their account; visitors
// keep it in this browser, and that is added to their account the next time they sign in.
export const LOCAL_KEY = 'bc_knowledge_progress'

const state = reactive({ completed: [], bookmarks: [], lastSlug: null, loaded: false, error: '' })
let loadedFor = undefined

function readLocal() {
  try {
    const value = JSON.parse(localStorage.getItem(LOCAL_KEY) ?? '{}')
    return {
      completed: Array.isArray(value.completed) ? value.completed.filter((s) => typeof s === 'string') : [],
      bookmarks: Array.isArray(value.bookmarks) ? value.bookmarks.filter((s) => typeof s === 'string') : [],
      lastSlug: typeof value.lastSlug === 'string' ? value.lastSlug : null,
    }
  } catch {
    return { completed: [], bookmarks: [], lastSlug: null }
  }
}

function writeLocal() {
  localStorage.setItem(LOCAL_KEY, JSON.stringify({ completed: state.completed, bookmarks: state.bookmarks, lastSlug: state.lastSlug }))
}

function apply(p) {
  state.completed = [...(p.completed ?? [])]
  state.bookmarks = [...(p.bookmarks ?? [])]
  state.lastSlug = p.lastSlug ?? null
}

const signedIn = () => !!authState.user

/** Loads progress for whoever is using the page now. Safe to call often. */
export async function loadProgress() {
  const who = authState.user?.id ?? null
  if (loadedFor === who && state.loaded) return
  loadedFor = who
  state.error = ''
  if (!who) {
    apply(readLocal())
    state.loaded = true
    return
  }
  try {
    const local = readLocal()
    let p = await api.getProgress()
    if (local.completed.length || local.bookmarks.length) {
      p = await api.mergeProgress(local.completed, local.bookmarks)
      localStorage.removeItem(LOCAL_KEY)
    }
    apply(p)
  } catch {
    state.error = 'Could not load your progress.'
  }
  state.loaded = true
}

function toggled(list, slug, on) {
  return on ? [...new Set([...list, slug])] : list.filter((s) => s !== slug)
}

export async function markCompleted(slug, completed = true) {
  const before = state.completed
  state.completed = toggled(state.completed, slug, completed)
  if (!signedIn()) return writeLocal()
  try {
    apply(await api.setCompleted(slug, completed))
  } catch {
    state.completed = before
    state.error = 'Could not save your progress.'
  }
}

export async function toggleBookmark(slug) {
  const on = !state.bookmarks.includes(slug)
  const before = state.bookmarks
  state.bookmarks = toggled(state.bookmarks, slug, on)
  if (!signedIn()) return writeLocal()
  try {
    apply(await api.setBookmarked(slug, on))
  } catch {
    state.bookmarks = before
    state.error = 'Could not save your bookmark.'
  }
}

export async function rememberLast(slug) {
  state.lastSlug = slug
  if (!signedIn()) return writeLocal()
  try {
    await api.setLast(slug)
  } catch {
    // Only affects "Continue learning"; nothing to tell the reader.
  }
}

// Signing in or out switches whose progress is shown.
watch(() => authState.user?.id ?? null, () => {
  if (state.loaded) loadProgress()
})
onSessionEnd(() => {
  loadedFor = undefined
  apply(readLocal())
})

export function useLearningProgress() {
  return { progress: readonly(state), loadProgress, markCompleted, toggleBookmark, rememberLast }
}
