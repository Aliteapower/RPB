<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import CountryPhoneField from '../common/CountryPhoneField.vue'

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

const { t } = useI18n()
</script>

<template>
  <CountryPhoneField
    :model-value="modelValue"
    :disabled="disabled"
    :label="t('staffControls.guest.phoneLabel')"
    model-format="local"
    @update:model-value="value => emit('update:modelValue', value)"
  />
</template>
