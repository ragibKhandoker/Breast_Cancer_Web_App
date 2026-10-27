<script setup>
import { ref } from 'vue'

// Password input with a Show/Hide toggle. v-model binds the value; `error` shows under the field.
const model = defineModel({ type: String, default: '' })
defineProps({
  id: { type: String, required: true },
  label: { type: String, default: 'Password' },
  autocomplete: { type: String, default: 'current-password' },
  error: { type: String, default: '' },
  describedby: { type: String, default: '' },
})
defineEmits(['blur'])

const visible = ref(false)
</script>

<template>
  <div class="form-field">
    <label :for="id">{{ label }}</label>
    <div class="password-wrap">
      <input
        :id="id"
        v-model="model"
        :type="visible ? 'text' : 'password'"
        :autocomplete="autocomplete"
        maxlength="200"
        required
        spellcheck="false"
        autocapitalize="off"
        :aria-invalid="!!error"
        :aria-describedby="[`${id}-error`, describedby].filter(Boolean).join(' ')"
        @blur="$emit('blur')"
      />
      <button
        type="button"
        class="toggle"
        :aria-pressed="visible"
        :aria-controls="id"
        :aria-label="visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`"
        @click="visible = !visible"
      >
        {{ visible ? 'Hide' : 'Show' }}
      </button>
    </div>
    <p :id="`${id}-error`" class="field-error" aria-live="polite">{{ error }}</p>
  </div>
</template>

<style scoped>
.password-wrap { position: relative; }
.password-wrap input { padding-right: 4.5rem; }
.toggle {
  position: absolute; top: 50%; right: 0.5rem; transform: translateY(-50%);
  padding: 0.35rem 0.7rem; border: 0; border-radius: 8px; background: var(--pink-50);
  color: var(--pink-600); font: 600 0.85rem 'Poppins', sans-serif; cursor: pointer;
}
.toggle:hover { background: var(--pink-100); }
.toggle:focus-visible { outline: 3px solid var(--pink-300); outline-offset: 1px; }
</style>
