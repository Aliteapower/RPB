<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Reconciliation } from '../types'
const props = defineProps<{ store: string }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Singapore' })
const start = ref(today), end = ref(today), report = ref<Reconciliation | null>(null)
const issuer = ref(false)
const metrics = ['topupCashCents', 'topupPaynowCents', 'topupCardCents', 'creditedTopupCents', 'walletSpentCents', 'externalBillCents', 'refundsCents', 'pendingTopupCents', 'pendingRefundCents', 'outstandingBillCents', 'programBalanceCents', 'programReservedCents', 'exceptionReceivedCents'] as const
function exportCsv() {
  if(!report.value) return
  const cell = (v: unknown) => '"' + String(v ?? '').replace(/^[=+@\-]/, "'$&").replace(/"/g, '""') + '"'
  const lines = [['metric', 'amount_sgd'], ...metrics.map(key => [t(`wallet.${key}`), (report.value![key] / 100).toFixed(2)]), [], ['receipt_id', 'channel', 'amount_sgd', 'external_reference', 'account', 'exception'], ...report.value.collections.map(c => [c.id, c.channel, (c.amountCents / 100).toFixed(2), c.receiptReference, c.collectionAccount, c.exception])]
  const url = URL.createObjectURL(new Blob(['\ufeff' + lines.map(row => row.map(cell).join(',')).join('\r\n')], {type: 'text/csv;charset=utf-8'}))
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = `wallet-${props.store}-${start.value}-${end.value}.csv`; anchor.click(); URL.revokeObjectURL(url)
}
async function load() { await run(async () => { report.value = await walletApi(props.store).get<Reconciliation>(`/reconciliation?start=${start.value}&end=${end.value}&group=${issuer.value ? 'issuer' : 'store'}`) }) }
</script>
<template><section><h2>{{ t('wallet.report') }}</h2><p>{{ t('wallet.reportHint') }}</p><form class="wallet-actions" @submit.prevent="load"><label>{{ t('wallet.start') }}<input v-model="start" type="date" required></label><label>{{ t('wallet.end') }}<input v-model="end" type="date" :min="start" required></label><label class="wallet-check"><input v-model="issuer" type="checkbox">{{ t('wallet.issuerReport') }}</label><button :disabled="busy">{{ t('wallet.refresh') }}</button></form><button v-if="report" @click="exportCsv">{{ t('wallet.export') }}</button><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="report" :class="{'wallet-error': report.ledgerMismatchAccounts > 0}">{{ t('wallet.ledgerMismatchAccounts') }}：{{ report.ledgerMismatchAccounts }}</p><dl v-if="report" class="wallet-metrics"><template v-for="metric in metrics" :key="metric"><dt>{{ t(`wallet.${metric}`) }}</dt><dd>{{ money(report[metric]) }}</dd></template></dl><article v-for="c in report?.collections" :key="c.id" class="wallet-record"><strong>{{ t(`wallet.channels.${c.channel}`) }} {{ money(c.amountCents) }}</strong><small>{{ c.receiptReference }} · {{ c.collectionAccount }} · {{ c.id }}</small></article></section></template>
