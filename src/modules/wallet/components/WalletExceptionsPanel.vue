<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Collection, CollectionConfirm } from '../types'
import WalletCollectionForm from './WalletCollectionForm.vue'
const props = defineProps<{ store: string; paynowConfigured: boolean }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const sourceType = ref('top_up'), sourceId = ref(''), reason = ref(''), amount = ref(''), rows = ref<Collection[]>([])
const cents = computed(() => /^\d+(\.\d{1,2})?$/.test(amount.value) ? Math.round(Number(amount.value) * 100) : 0)
async function load() { rows.value = await walletApi(props.store).get<Collection[]>('/collection-exceptions') }
async function record(confirmation: CollectionConfirm) { await run(async () => { await walletApi(props.store).command('/collection-exceptions', { sourceType: sourceType.value, sourceId: sourceId.value, reason: reason.value, confirmation }); amount.value = ''; await load() }) }
onMounted(() => { void run(load) })
</script>
<template><section class="wallet-card"><h2>{{ t('wallet.collectionExceptions') }}</h2><p>{{ t('wallet.exceptionHint') }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
  <div class="wallet-form"><label>{{ t('wallet.source') }}<select v-model="sourceType"><option value="top_up">{{ t('wallet.topup') }}</option><option value="bill">{{ t('wallet.bills') }}</option></select></label><label>{{ t('wallet.reference') }}<input v-model.trim="sourceId" maxlength="36"></label><label>{{ t('wallet.refundReason') }}<textarea v-model.trim="reason" maxlength="200" /></label><label>{{ t('wallet.amount') }}<input v-model="amount" type="number" min="0.01" step="0.01" inputmode="decimal"></label></div>
  <WalletCollectionForm v-if="cents > 0 && sourceId && reason" :amount-cents="cents" :channels="['cash', 'paynow', 'credit_card']" :busy="busy" :paynow-configured="true" @confirm="record" />
  <article v-for="row in rows" :key="row.id" class="wallet-record"><strong>{{ t(`wallet.channels.${row.channel}`) }} {{ money(row.amountCents) }}</strong><span>{{ row.exceptionReason }}</span><small>{{ row.id }} · {{ row.receiptReference }}</small><span>{{ t('wallet.refunds') }} {{ money(row.refundedCents) }}</span></article>
</section></template>
