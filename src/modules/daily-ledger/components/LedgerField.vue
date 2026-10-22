<script setup lang="ts">
import { useLedgerI18n } from '../i18n'
import type { Field } from '../types'
import { useLedgerFormat } from '../format'
const { text } = useLedgerI18n()
const format = useLedgerFormat()
const props = defineProps<{ label: string; model: Field; integer?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
function changed() { props.model.entered_at = new Date().toISOString(); emit('changed') }
</script>
<template>
  <div class="payment-item">
    <label>{{ label }}<span class="money-input"><span>$</span><input v-model="model.value" :aria-label="label" :disabled="disabled" :inputmode="integer ? 'numeric' : 'decimal'" type="number" min="0" :step="integer ? '1' : '0.01'" placeholder="0.00" @input="changed" /></span></label>
    <input v-model="model.note" class="note-input" :aria-label="text('{name}备注', { name: label })" :disabled="disabled" maxlength="300" :placeholder="text('备注（选填）')" @input="changed" />
    <span class="field-time">{{ model.entered_at ? text("输入 ") + format.clock(model.entered_at) : text("待填写") }}</span>
  </div>
</template>

