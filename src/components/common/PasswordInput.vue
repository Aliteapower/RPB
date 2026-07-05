<script setup lang="ts">
import { computed, ref } from 'vue'

interface PasswordModelModifiers {
  trim?: boolean
}

const props = withDefaults(defineProps<{
  modelValue?: string | null
  modelModifiers?: PasswordModelModifiers
  id?: string
  name?: string
  autocomplete?: string
  maxlength?: number | string
  pattern?: string
  placeholder?: string
  required?: boolean
  disabled?: boolean
  readonly?: boolean
}>(), {
  modelModifiers: () => ({})
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const passwordVisible = ref(false)
const inputType = computed(() => (passwordVisible.value ? 'text' : 'password'))
const toggleLabel = computed(() => (passwordVisible.value ? '隐藏密码' : '显示密码'))
const inputValue = computed(() => props.modelValue ?? '')

function updateValue(event: Event): void {
  const input = event.target as HTMLInputElement
  emit('update:modelValue', props.modelModifiers.trim ? input.value.trim() : input.value)
}

function togglePasswordVisibility(): void {
  if (props.disabled) {
    return
  }
  passwordVisible.value = !passwordVisible.value
}
</script>

<template>
  <div class="password-input" :class="{ 'password-input--disabled': disabled }">
    <input
      :id="id"
      class="password-input__control"
      :value="inputValue"
      :type="inputType"
      :name="name"
      :autocomplete="autocomplete"
      :maxlength="maxlength"
      :pattern="pattern"
      :placeholder="placeholder"
      :required="required"
      :disabled="disabled"
      :readonly="readonly"
      autocapitalize="none"
      spellcheck="false"
      @input="updateValue"
    />
    <button
      class="password-input__toggle"
      type="button"
      :aria-label="toggleLabel"
      :aria-pressed="passwordVisible"
      :disabled="disabled"
      @mousedown.prevent
      @click="togglePasswordVisibility"
    >
      <svg
        v-if="passwordVisible"
        class="password-eye-off-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M3 3l18 18" />
        <path d="M10.6 10.6A2 2 0 0 0 13.4 13.4" />
        <path d="M9.9 5.2A10.5 10.5 0 0 1 12 5c5.2 0 8.6 4.4 9.5 7-0.4 1.1-1.2 2.3-2.3 3.4" />
        <path d="M6.5 6.7C4.5 8 3.1 10 2.5 12c0.9 2.6 4.3 7 9.5 7 1.8 0 3.3-0.5 4.6-1.2" />
      </svg>
      <svg
        v-else
        class="password-eye-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M2.5 12c0.9-2.6 4.3-7 9.5-7s8.6 4.4 9.5 7c-0.9 2.6-4.3 7-9.5 7s-8.6-4.4-9.5-7z" />
        <circle cx="12" cy="12" r="2.7" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.password-input {
  width: 100%;
  min-height: 40px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px;
  align-items: stretch;
  overflow: hidden;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #ffffff;
}

.password-input:focus-within {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.password-input--disabled {
  background: #f8fafc;
  opacity: 0.75;
}

.password-input__control {
  min-width: 0;
  min-height: 38px;
  border: 0;
  padding: 8px 10px;
  color: #0f172a;
  background: transparent;
  font: inherit;
  outline: 0;
}

.password-input__control::placeholder {
  color: #94a3b8;
}

.password-input__toggle {
  width: 40px;
  min-height: 38px;
  display: grid;
  place-items: center;
  border: 0;
  border-left: 1px solid #e2e8f0;
  padding: 0;
  color: #475569;
  background: #ffffff;
  cursor: pointer;
}

.password-input__toggle:hover:not(:disabled),
.password-input__toggle:focus-visible {
  color: #0f766e;
  background: #f0fdfa;
}

.password-input__toggle:focus-visible {
  outline: 2px solid rgba(15, 118, 110, 0.45);
  outline-offset: -2px;
}

.password-input__toggle:disabled {
  cursor: default;
}

.password-eye-icon,
.password-eye-off-icon {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}
</style>
