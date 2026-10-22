<script setup lang="ts">
import type { LedgerConfig, LedgerRecord } from '../types'
import { useLedgerFormat } from '../format'
defineProps<{ record?: LedgerRecord; config: LedgerConfig; period: string }>()
const format = useLedgerFormat()
</script>
<template><section v-if="record" class="panel submitted-payments" aria-label="已提交非现金收入累计"><div class="submitted-payments-heading"><h2>非现金收入累计</h2><strong>${{ record.totals.noncash }}</strong></div><p class="submitted-payments-caption">{{ config.periods.find(p => p.id === period)?.name }} · 最近已提交 · {{ format.time(record.submitted_at ?? record.saved_at) }}</p><dl class="submitted-payment-lines"><div v-for="(field, id) in Object.fromEntries(Object.entries(record.payments).filter(([, field]) => Number(field.value)))" :key="id"><dt>{{ config.payments.find(p => p.id === id)?.name ?? id }}</dt><dd>${{ field.value }}</dd></div></dl><p class="submitted-payments-note">每班均为当日累计，不与其他班次相加。草稿不改变此汇总，重新提交后更新。</p></section></template>
