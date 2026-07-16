<script setup lang="ts">
import { computed } from 'vue'

import type { PhoneCountryCode } from '../../utils/countryPhone'
import {
  DEFAULT_PHONE_COUNTRY_CODE,
  isLegacyCountryPhone,
  phoneCountryConfig,
  sanitizeCountryLocalPhone,
  storedPhoneToCountryLocal,
  toCountryPhoneE164
} from '../../utils/countryPhone'

const props = withDefaults(
  defineProps<{
    modelValue: string | null | undefined
    countryCode?: PhoneCountryCode
    modelFormat?: 'local' | 'e164'
    label?: string
    name?: string
    disabled?: boolean
    required?: boolean
  }>(),
  {
    countryCode: DEFAULT_PHONE_COUNTRY_CODE,
    modelFormat: 'local',
    label: '',
    name: 'phoneLocal',
    disabled: false,
    required: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const fieldId = `country-phone-${Math.random().toString(36).slice(2)}`
const config = computed(() => phoneCountryConfig(props.countryCode))
const localValue = computed(() => (
  props.modelFormat === 'e164'
    ? storedPhoneToCountryLocal(props.modelValue, props.countryCode)
    : sanitizeCountryLocalPhone(String(props.modelValue || ''), props.countryCode)
))
const legacyValue = computed(() => (
  props.modelFormat === 'e164' && isLegacyCountryPhone(props.modelValue, props.countryCode)
    ? String(props.modelValue || '').trim()
    : ''
))

function updatePhone(event: Event): void {
  const input = event.target as HTMLInputElement
  const nextLocal = sanitizeCountryLocalPhone(input.value, props.countryCode)
  input.value = nextLocal
  emit('update:modelValue', props.modelFormat === 'e164' ? toCountryPhoneE164(nextLocal, props.countryCode) || '' : nextLocal)
}
</script>

<template>
  <section class="country-phone-field">
    <label v-if="label" class="country-phone-field__label" :for="fieldId">{{ label }}</label>

    <div class="country-phone-field__row">
      <span class="country-phone-field__prefix" aria-hidden="true">{{ config.callingCode }}</span>
      <input
        :id="fieldId"
        :value="localValue"
        autocomplete="tel-national"
        inputmode="numeric"
        :maxlength="config.nationalLength"
        :name="name"
        :pattern="`[0-9]{${config.nationalLength}}`"
        :placeholder="config.placeholder"
        type="tel"
        :disabled="disabled"
        :required="required"
        @input="updatePhone"
      />
    </div>

    <small v-if="legacyValue" class="country-phone-field__legacy">{{ legacyValue }}</small>
  </section>
</template>

<style scoped>
.country-phone-field {
  display: grid;
  gap: 7px;
}

.country-phone-field__label {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.country-phone-field__row {
  align-items: center;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 38px;
  overflow: hidden;
}

.country-phone-field__prefix {
  align-items: center;
  align-self: stretch;
  background: #f8fafc;
  border-right: 1px solid #cbd5e1;
  color: #0f172a;
  display: flex;
  font-weight: 900;
  justify-content: center;
  min-width: 54px;
  padding: 0 10px;
}

.country-phone-field input {
  background: #ffffff;
  border: 0;
  color: #0f172a;
  font: inherit;
  min-height: 36px;
  outline: none;
  padding: 7px 12px;
  width: 100%;
}

.country-phone-field__row:focus-within {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.country-phone-field input:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.country-phone-field__legacy {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  overflow-wrap: anywhere;
}
</style>
