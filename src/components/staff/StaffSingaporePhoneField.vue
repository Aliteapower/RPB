<script setup lang="ts">
import { sanitizeSingaporeLocalPhone } from './staffGuestContact'

const props = withDefaults(
  defineProps<{
    modelValue: string
    disabled?: boolean
  }>(),
  {
    disabled: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const fieldId = `staff-singapore-phone-${Math.random().toString(36).slice(2)}`

function updatePhone(event: Event): void {
  emit('update:modelValue', sanitizeSingaporeLocalPhone((event.target as HTMLInputElement).value))
}
</script>

<template>
  <section class="staff-singapore-phone-field">
    <label class="staff-singapore-phone-field__label" :for="fieldId">手机号</label>

    <div class="staff-singapore-phone-field__row">
      <span class="staff-singapore-phone-field__prefix" aria-hidden="true">+65</span>
      <input
        :id="fieldId"
        :value="modelValue"
        autocomplete="tel-national"
        inputmode="numeric"
        maxlength="8"
        name="phoneLocal"
        pattern="[0-9]*"
        placeholder="91234567"
        type="tel"
        :disabled="disabled"
        @input="updatePhone"
      />
    </div>
  </section>
</template>

<style scoped>
.staff-singapore-phone-field {
  display: grid;
  gap: 6px;
}

.staff-singapore-phone-field__label {
  color: #0f172a;
  font-size: 0.82rem;
  font-weight: 900;
}

.staff-singapore-phone-field__row {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 36px;
  overflow: hidden;
}

.staff-singapore-phone-field__prefix {
  align-items: center;
  align-self: stretch;
  background: #f8fafc;
  border-right: 1px solid #d8e0eb;
  color: #0f172a;
  display: flex;
  font-size: 0.92rem;
  font-weight: 900;
  justify-content: center;
  min-width: 54px;
  padding: 0 10px;
}

.staff-singapore-phone-field input {
  background: #ffffff;
  border: 0;
  color: #0f172a;
  min-height: 36px;
  outline: none;
  padding: 7px 12px;
  width: 100%;
}

.staff-singapore-phone-field__row:focus-within {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.staff-singapore-phone-field input:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}
</style>
