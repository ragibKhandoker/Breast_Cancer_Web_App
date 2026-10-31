// Per-tab sign-in. Each browser tab has its own session slot (kept in sessionStorage, which browsers keep per
// tab) and sends it with every API call; the server keeps one login cookie per slot. So one tab can be signed in
// as an administrator and another as a patient, and logging out in one tab never signs out another.
//
// A new tab (typed address, link opened in a new tab, "Duplicate tab") gets a fresh slot of its own and copies
// the login of the tab used most recently (POST /api/auth/fork), so it starts signed in as the same user but as a
// separate login. The slot is not a secret: the login itself stays in an HttpOnly cookie scripts cannot read.

const TAB_KEY = 'bc_auth_slot'
const LAST_KEY = 'bc_auth_last_slot'
const VALID = /^[a-z0-9]{8}$/
const CHANNEL = 'bc_auth_tabs'
const ASK_MS = 150

const tabId = Math.random().toString(36).slice(2)
let memorySlot = null // when storage is blocked
let forkFrom = null // the slot whose login this new tab should copy
let channel = null

function randomSlot() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  const bytes = new Uint8Array(8)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => chars[b % chars.length]).join('')
}

function read(storage, key) {
  try {
    const value = storage.getItem(key)
    return VALID.test(value ?? '') ? value : null
  } catch {
    return null
  }
}

function write(storage, key, value) {
  try {
    if (value) storage.setItem(key, value)
    else storage.removeItem(key)
  } catch {
    // Storage blocked: the slot lives in memory for this page.
  }
}

function setSlot(slot) {
  memorySlot = slot
  write(sessionStorage, TAB_KEY, slot)
  return slot
}

/** This tab's slot. A tab without one (a new tab) gets a fresh slot and will copy the latest tab's login. */
export function currentSlot() {
  const slot = read(sessionStorage, TAB_KEY) ?? memorySlot
  if (slot) return (memorySlot = slot)
  forkFrom = read(localStorage, LAST_KEY)
  return setSlot(randomSlot())
}

// Other tabs ask "is anyone using slot X?"; answer when it is ours, so a duplicated tab knows to take its own.
function listen() {
  if (channel || typeof BroadcastChannel === 'undefined' || import.meta.env?.MODE === 'test') return
  channel = new BroadcastChannel(CHANNEL)
  channel.onmessage = (event) => {
    const msg = event.data ?? {}
    if (msg.type === 'who-has' && msg.from !== tabId && msg.slot === currentSlot()) {
      channel.postMessage({ type: 'taken', slot: msg.slot, to: msg.from })
    }
  }
}

/** True when another open tab already uses `slot` (this tab was duplicated, so sessionStorage was copied). */
function slotTakenElsewhere(slot) {
  if (!channel) return Promise.resolve(false)
  return new Promise((resolve) => {
    const onReply = (event) => {
      if (event.data?.type === 'taken' && event.data.to === tabId && event.data.slot === slot) done(true)
    }
    const done = (taken) => {
      channel.removeEventListener('message', onReply)
      clearTimeout(timer)
      resolve(taken)
    }
    const timer = setTimeout(() => done(false), ASK_MS)
    channel.addEventListener('message', onReply)
    channel.postMessage({ type: 'who-has', slot, from: tabId })
  })
}

/**
 * Called once before this tab asks who is signed in. Returns the slot whose login to copy (a new or duplicated
 * tab), or null when the tab already has its own login (a reload).
 */
export async function prepareTab() {
  const hadSlot = !!(read(sessionStorage, TAB_KEY) ?? memorySlot)
  const slot = currentSlot()
  listen()
  if (!hadSlot) return forkFrom && forkFrom !== slot ? forkFrom : null
  if (await slotTakenElsewhere(slot)) {
    setSlot(randomSlot())
    return slot
  }
  return null
}

/** After signing in (or coming back to a signed-in tab): new tabs copy this tab's login. */
export function markSlotAsLatest() {
  write(localStorage, LAST_KEY, currentSlot())
}

/** After signing out: new tabs should not try to copy a signed-out tab. */
export function forgetSlotIfLatest() {
  if (read(localStorage, LAST_KEY) === currentSlot()) write(localStorage, LAST_KEY, null)
}

/** "Use another account in this tab": a fresh slot, so the other tabs keep their login. */
export function startNewSlot() {
  forkFrom = null
  return setSlot(randomSlot())
}

/** For plain links the browser opens itself (images, downloads), which cannot carry the header. */
export function withSlot(url) {
  return `${url}${url.includes('?') ? '&' : '?'}authSlot=${currentSlot()}`
}
