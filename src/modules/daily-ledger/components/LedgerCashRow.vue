<script setup lang="ts">
import { useLedgerI18n } from '../i18n'
import { computed } from 'vue'
import type { Field } from '../types'
import { cents, money } from '../model'
import { useLedgerFormat } from '../format'
const { text } = useLedgerI18n()
const props = defineProps<{ denomination?: string; model: Field }>()
const emit = defineEmits<{ changed: [] }>()
const format = useLedgerFormat()
const label = computed(() => props.denomination ? `$${props.denomination} ${text('数量')}` : text("其他零钱金额"))
const amount = computed(() => money(props.denomination ? cents(props.denomination) * Number(props.model.value || 0) : cents(props.model.value)))
function changed() { props.model.entered_at = new Date().toISOString(); emit('changed') }
</script>
<template><div class="cash-row"><div class="denom"><span class="note-icon" :class="{ 'coin-icon': !denomination || Number(denomination) < 1 }">{{ denomination ? '$' : '¢' }}</span><strong>{{ denomination ? '$' + denomination : text("其他零钱") }}</strong></div><div class="quantity"><input v-model="model.value" :aria-label="label" type="number" :inputmode="denomination ? 'numeric' : 'decimal'" min="0" :step="denomination ? '1' : '0.01'" placeholder="0" @input="changed" /><span class="field-time">{{ model.entered_at ? text("输入 ") + format.clock(model.entered_at) : text("待填写") }}</span></div><div class="line-total">${{ amount }}</div><input v-model="model.note" class="note-input" :aria-label="text('{name}备注', { name: denomination ? '$' + denomination : text('其他零钱') })" :placeholder="text('添加备注')" maxlength="300" @input="changed" /></div></template>

