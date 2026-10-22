<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ledgerApi } from '../api'
import { businessDate, cents, money } from '../model'
import { csv, historyReport } from '../report'
import type { LedgerDay } from '../types'
import LedgerCharts from './LedgerCharts.vue'
import { useLedgerFormat } from '../format'
const format = useLedgerFormat()
const props = defineProps<{ storeId: string; timezone: string }>()
const emit = defineEmits<{ open: [date: string] }>()
const today = businessDate(props.timezone), start = ref(today.slice(0, 7) + '-01'), end = ref(today)
const days = ref<LedgerDay[]>([]), years = ref<number[]>([]), loading = ref(false), error = ref('')
const report = computed(() => historyReport(days.value)), selectedQuick = ref('month')
async function load() { loading.value = true; error.value = ''; try { days.value = await ledgerApi(props.storeId).history(start.value, end.value) } catch (e) { error.value = String(e); days.value = [] } finally { loading.value = false } }
function quick(kind: string) {
  selectedQuick.value = kind
  const date = new Date(today + 'T00:00:00Z'), y = date.getUTCFullYear(), m = date.getUTCMonth()
  if (kind === 'today') start.value = end.value = today
  if (kind === 'yesterday') { date.setUTCDate(date.getUTCDate() - 1); start.value = end.value = date.toISOString().slice(0, 10) }
  if (kind === 'month') { start.value = today.slice(0, 7) + '-01'; end.value = today }
  if (kind === 'last-month') { start.value = new Date(Date.UTC(y, m - 1, 1)).toISOString().slice(0, 10); end.value = new Date(Date.UTC(y, m, 0)).toISOString().slice(0, 10) }
  if (kind === 'last-year') { start.value = `${y - 1}-01-01`; end.value = `${y - 1}-12-31` }
  void load()
}
function exportCsv() { const url = URL.createObjectURL(new Blob([csv(days.value)], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = `daily-ledger-${start.value}-${end.value}.csv`; link.click(); URL.revokeObjectURL(url) }
onMounted(async () => { try { years.value = await ledgerApi(props.storeId).years() } catch (e) { error.value = String(e) } await load() })
</script>
<template>
  <section>
    <div class="page-heading"><div><div class="eyebrow">BUSINESS HISTORY</div><h1>历史账目<span class="heading-dot">.</span></h1><p>每一天的盘点，都有迹可循。</p></div></div>
    <div class="panel history-panel">
    <form @submit.prevent="selectedQuick = ''; load()"><div class="history-shortcuts" role="group" aria-label="日期快速查询"><span>快速查询</span><button v-for="(name, key) in { today: '本日', month: '本月', yesterday: '上日', 'last-month': '上月', 'last-year': '上年' }" :key="key" type="button" class="button secondary" :aria-pressed="selectedQuick === key" :disabled="loading" @click="quick(key)">{{ name }}</button><label class="history-year-label">年度<select aria-label="按年度查询" @change="selectedQuick = ''; start = `${($event.target as HTMLSelectElement).value}-01-01`; end = `${($event.target as HTMLSelectElement).value}-12-31`; load()"><option disabled selected>选择年度</option><option v-for="year in years" :key="year" :value="year">{{ year }}年</option></select></label></div>
    <div class="history-date-range"><label>开始日期<span class="history-date-control"><span class="history-date-display"><span>{{ format.date(start) }}</span><span>▦</span></span><input v-model="start" type="date" required /></span></label><span class="history-date-separator">至</span><label>结束日期<span class="history-date-control"><span class="history-date-display"><span>{{ format.date(end) }}</span><span>▦</span></span><input v-model="end" type="date" required /></span></label></div><button class="button primary" :disabled="loading">查询</button><button class="button secondary" type="button" :disabled="loading || !!error || !days.length" @click="exportCsv">导出 CSV</button></form>
    <p class="small muted">日报只取指定班次已提交的累计数，其他班次和草稿不计入；新口径营业额＝盘点现金＋已抽大钞＋非现金＋已付现金开支－底钱；现金总交款＝盘点现金＋已抽大钞－底钱。旧记录按原提交口径显示。</p>
    <p v-if="error" role="alert" class="ledger-error">{{ error }}</p><p v-if="loading" class="history-empty">正在读取…</p>
    <template v-else-if="!error">
      <LedgerCharts :days="days" :start="start" :end="end" />
      <div v-if="!days.length" class="history-empty"><h3>这段时间还没有账目</h3><p>保存第一笔盘点后，就可以在这里回看。</p></div>
      <template v-else>
      <div class="history-table-wrap"><table class="history-table"><thead><tr><th>营业日期 / 时段</th><th>盘点现金 / 口径</th><th>已抽大钞</th><th>现金收入</th><th>非现金收入 / 明细</th><th>已付现金开支</th><th>保留底钱</th><th>营业额</th><th>现金总交款</th><th>保存信息</th></tr></thead><tbody><tr v-for="{ day, record } in report.rows" :key="day.date"><td><button class="text-button" @click="emit('open', day.date)">{{ day.date }} ↗</button><small>{{ day.config.periods.find(p => p.id === day.last_period)?.name ?? '指定统计班次未提交' }}</small></td><template v-if="record"><td>${{ record.totals.cash }}<small>{{ record.accounting_version >= 5 ? '含底钱（未扣除）' : '旧口径：不含底钱' }}</small></td><td>${{ record.totals.withdrawn }}</td><td>${{ money(cents(record.totals.revenue) - cents(record.totals.noncash)) }}</td><td><strong>${{ record.totals.noncash }}</strong><dl class="history-payment-details"><div v-for="(field, id) in record.payments" :key="id"><dt>{{ day.config.payments.find(p => p.id === id)?.name ?? id }}</dt><dd>${{ field.value || '0.00' }}</dd></div></dl></td><td>${{ record.totals.expenses }}</td><td>${{ record.base_amount ?? day.config.base_amount }}</td><td>${{ record.totals.revenue }}</td><td class="emphasis">${{ record.totals.handover }}</td><td>{{ record.saved_by }}<small>{{ format.time(record.submitted_at ?? record.saved_at) }}</small></td></template><td v-else colspan="9" class="muted">尚未最终提交，不计入合计；点击日期继续填写</td></tr></tbody><tfoot><tr><th scope="row">查询结果合计<small>{{ report.rows.filter(row => row.record).length }} 天已提交</small></th><td>—</td><td>—</td><td>${{ money(report.totals.cashIncome) }}</td><td><strong>${{ money(report.totals.noncash) }}</strong><dl class="history-payment-details"><div v-for="channel in report.channels" :key="channel.id"><dt>{{ channel.name }}</dt><dd>${{ money(channel.amount) }}</dd></div></dl></td><td>${{ money(report.totals.expenses) }}</td><td>—</td><td>${{ money(report.totals.revenue) }}</td><td>${{ money(report.totals.handover) }}</td><td>仅已提交日报</td></tr></tfoot></table></div>
      <section class="history-range-summary" aria-label="查询期间收入支出合计"><h2>查询期间合计</h2><p>已提交 {{ report.rows.filter(row => row.record).length }} 天；每个营业日只统计指定班次一次。</p><div class="history-total-grid"><div v-for="(label, key) in { revenue: '营业额', cashIncome: '现金收入', noncash: '非现金收入', expenses: '已付现金开支', handover: '现金总交款' }" :key="key"><span>{{ label }}</span><strong>${{ money(report.totals[key]) }}</strong></div></div><dl class="history-payment-details"><div v-for="channel in report.channels" :key="channel.id"><dt>{{ channel.name }}</dt><dd>${{ money(channel.amount) }}</dd></div></dl><p>现金收入＝营业额－非现金收入，包含已付现金开支；现金总交款为扣除底钱后的应交现金。盘点现金、抽款和底钱为核对项目，不能再次相加计入营业额。旧记录保留原提交口径。</p></section>
      </template>
    </template>
    </div>
  </section>
</template>
