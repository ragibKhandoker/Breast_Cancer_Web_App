import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BreastSurgeonProfileForm from '../src/components/appointments/BreastSurgeonProfileForm.vue'

const mountForm = (props = {}) => mount(BreastSurgeonProfileForm, { props, global: { stubs: { RouterLink: true } } })
const languageChip = (wrapper, name) => wrapper.findAll('.chip').find((c) => c.text().includes(name))

describe('Breast Surgeon profile form defaults', () => {
  it('fills a new profile with a ৳1,000 fee and Bangla, which the doctor can change', async () => {
    const wrapper = mountForm()
    expect(wrapper.find('#bs-fee').element.value).toBe('1000')
    expect(languageChip(wrapper, 'Bangla').attributes('aria-pressed')).toBe('true')

    await wrapper.find('#bs-fee').setValue('1500')
    expect(wrapper.find('#bs-fee').element.value).toBe('1500')
    await languageChip(wrapper, 'Bangla').trigger('click')
    expect(languageChip(wrapper, 'Bangla').attributes('aria-pressed')).toBe('false')
  })

  it('keeps what a saved profile already has', () => {
    const wrapper = mountForm({ initial: { id: 'p1', fullName: 'Dr Nusrat Jahan', consultationFee: 800, languages: ['English'] } })
    expect(wrapper.find('#bs-fee').element.value).toBe('800')
    expect(languageChip(wrapper, 'Bangla').attributes('aria-pressed')).toBe('false')
    expect(languageChip(wrapper, 'English').attributes('aria-pressed')).toBe('true')
  })
})
