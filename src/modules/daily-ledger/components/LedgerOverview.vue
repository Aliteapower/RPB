<script setup lang="ts">
import { useLedgerI18n } from '../i18n'
import type { LedgerRecord } from '../types'
import LedgerIcon from './LedgerIcon.vue'
const { text } = useLedgerI18n()
defineProps<{ submitted?: LedgerRecord; base: string }>()
</script>
<template><div class="overview-grid">
  <div class="overview-card revenue-card"><div class="overview-label">{{ text('当日营业额') }} <span class="metric-icon">↗</span></div><strong>{{ submitted ? '$' + submitted.totals.revenue : '—' }}</strong><span class="metric-detail">{{ submitted ? text("指定班次已提交") : text("等待指定班次最终提交") }}</span></div>
  <div class="overview-card"><div class="overview-label">{{ text('当日累计开支') }} <span class="metric-icon amber">↘</span></div><strong>{{ submitted ? '$' + submitted.totals.expenses : '—' }}</strong><span class="metric-detail">{{ text('采购、杂费等开支') }}</span></div>
  <div class="overview-card handover-card"><div class="overview-label">{{ text('当日现金总交款') }} <LedgerIcon name="wallet" /></div><strong>{{ submitted ? '$' + submitted.totals.handover : '—' }}</strong><span class="metric-detail">{{ text('保留底钱 $') }}{{ base }} · {{ submitted ? text("指定班次已提交") : text("尚未提交统计") }}</span></div>
</div></template>
