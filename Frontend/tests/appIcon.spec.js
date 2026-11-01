import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import AppIcon from '../src/components/common/AppIcon.vue'

// Every icon name written in the pages (name="...") and the ones chosen in code must draw an icon.
function vueFiles(dir) {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f)
    return statSync(p).isDirectory() ? vueFiles(p) : p.endsWith('.vue') ? [p] : []
  })
}
const used = new Set()
for (const file of vueFiles(join(__dirname, '../src'))) {
  for (const m of readFileSync(file, 'utf8').matchAll(/<AppIcon[^>]*?\sname="([a-z-]+)"/g)) used.add(m[1])
}
const fromCode = ['close', 'menu', 'message', 'microscope', 'refresh', 'bookmark', 'facebook', 'youtube', 'linkedin', 'twitter']

describe('AppIcon', () => {
  it.each([...used, ...fromCode])('draws "%s"', (name) => {
    const svg = mount(AppIcon, { props: { name, size: 20 } }).find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.attributes('aria-hidden')).toBe('true')
    expect(svg.attributes('width')).toBe('20')
  })

  it('fills an icon when asked (a saved bookmark)', () => {
    expect(mount(AppIcon, { props: { name: 'bookmark', filled: true } }).find('svg').attributes('fill')).toBe('currentColor')
    expect(mount(AppIcon, { props: { name: 'bookmark' } }).find('svg').attributes('fill')).toBe('none')
  })

  it('draws nothing for an unknown name', () => {
    expect(mount(AppIcon, { props: { name: 'no-such-icon' } }).find('svg').exists()).toBe(false)
  })
})
