<script setup lang="ts">
import WalletRefundAmounts from './WalletRefundAmounts.vue'
import WalletRefundComponents from './WalletRefundComponents.vue'
import type { RefundPreview } from '../marketingTypes'
import WalletEntryAllocations from '../components/WalletEntryAllocations.vue'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { staffWalletPath } from '../navigation'
import { walletApi, WalletApiError } from '../api'
import { money, dateTime } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Account, Entry, Refund, RefundDetail } from '../types'
const props = withDefaults(defineProps<{ store: string; account: Account | null; timezone: string; canRefund: boolean; mode?: string; refundId?: string }>(), { mode: 'entries', refundId: '' })
const emit = defineEmits<{ changed: []; created: [id: string] }>()
const route = useRoute()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const entries = ref<Entry[]>([]), refunds = ref<Refund[]>([]), detail = ref<RefundDetail | null>(null)
const refundPreview=ref<RefundPreview|null>(null),refundMode=ref('normal')
const sourceType = ref('bill'), sourceId = ref(''), amount = ref(''), reason = ref(''), receipt = ref(''), verified = ref(false)
if (['bill','top_up','collection_exception'].includes(String(route.query.sourceType))) sourceType.value=String(route.query.sourceType)
sourceId.value=String(route.query.sourceId||'')
const more = ref(false)
async function refresh() {
  const id=props.account?.id
  const resultEntries = props.mode==='entries' && id ? await walletApi(props.store).get<Entry[]>(`/accounts/${id}/entries`) : []
  if(props.account?.id!==id)return
  entries.value=resultEntries
  more.value=entries.value.length===100
  if (props.canRefund && props.mode==='refunds') refunds.value = await walletApi(props.store).get<Refund[]>('/refunds')
  if (props.canRefund && props.mode==='refund-detail' && props.refundId) detail.value=await walletApi(props.store).get<RefundDetail>(`/refunds/${props.refundId}`)
}
async function loadMore() { const id=props.account?.id;if(!id)return;await run(async()=>{const next=await walletApi(props.store).get<Entry[]>(`/accounts/${id}/entries?offset=${entries.value.length}`);if(props.account?.id!==id)return;entries.value.push(...next.filter(e=>!entries.value.some(old=>old.id===e.id)));more.value=next.length===100}) }
async function create() {
  if(!refundPreview.value)return
  await run(async () => {
    try {
      const created=await walletApi(props.store).command<RefundDetail>('/refunds',{sourceType:sourceType.value,sourceId:sourceId.value,...(refundMode.value==='remaining_lot_exit'?{mode:refundMode.value}:{amount:amount.value}),reason:reason.value,...(refundPreview.value?{expectedAccountVersion:refundPreview.value.accountVersion}:{})})
      emit('changed');emit('created',created.refund.id)
    } catch(e) {
      if(e instanceof WalletApiError&&e.code==='WALLET_ALLOCATION_STALE')refundPreview.value=null
      throw e
    }
  })
}
async function confirm(partId: string) {
  const id = detail.value?.refund.id; if (!id || !verified.value) return
  await run(async () => { detail.value = await walletApi(props.store).command<RefundDetail>(`/refunds/${id}/confirm`, { partId, receiptReference: receipt.value }); verified.value = false; receipt.value = ''; await refresh(); emit('changed') })
}
async function cancel() { const id = detail.value?.refund.id; if (!id) return; await run(async () => { detail.value = await walletApi(props.store).command<RefundDetail>(`/refunds/${id}/cancel`); await refresh(); emit('changed') }) }
watch(sourceType,()=>{refundMode.value='normal'})
watch(() => props.account?.id, () => { detail.value=null;sourceId.value='';verified.value=false;void run(refresh) })
onMounted(() => { void run(refresh) })
</script>
<template>
  <section><div class="wallet-page-heading"><h2>{{ t(mode==='entries'?'wallet.ui.entries':mode==='refund-create'?'wallet.refund':'wallet.refunds') }}</h2><button class="wallet-link-button" @click="run(refresh)">{{ t('wallet.refresh') }}</button></div>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="mode==='entries' && !entries.length">{{ t('wallet.empty') }}</p>
    <article v-for="e in entries" :key="e.id" class="wallet-record"><strong>{{ t(`wallet.states.${e.kind}`) }} {{ money(e.amountCents) }}</strong><span>{{ t('wallet.balance') }} {{ money(e.afterCents) }}</span><WalletAmountSplit :principal="e.principalCents??e.amountCents" :bonus="e.bonusCents??0" /><WalletEntryAllocations :entry="e" /><small>{{ dateTime(e.createdAt, timezone) }} · {{ e.sourceId }}</small><RouterLink v-if="e.kind==='bill_debit'" :to="staffWalletPath(store,`bills/${e.sourceId}`)">{{ t('wallet.ui.viewBill') }}</RouterLink></article>
    <button v-if="more" :disabled="busy" @click="loadMore">{{ t('wallet.loadMore') }}</button>
    <form v-if="canRefund && mode==='refund-create'" class="wallet-form" @submit.prevent="create"><p>{{ t('wallet.refundHint') }}</p>
      <label>{{ t('wallet.source') }}<select v-model="sourceType"><option value="bill">{{ t('wallet.bills') }}</option><option value="top_up">{{ t('wallet.topup') }}</option><option value="collection_exception">{{ t('wallet.collectionExceptions') }}</option></select></label>
      <label>{{ t('wallet.reference') }}<input v-model.trim="sourceId" required pattern="[0-9a-fA-F-]{36}"></label>
      <label>{{t('wallet.marketing.refundMode')}}<select v-model="refundMode"><option value="normal">{{t('wallet.marketing.normal')}}</option><option v-if="sourceType==='top_up'" value="remaining_lot_exit">{{t('wallet.marketing.remaining_lot_exit')}}</option></select></label><label v-if="refundMode==='normal'">{{ t('wallet.refundAmount') }}<input v-model="amount" type="number" min="0.01" step="0.01" inputmode="decimal" required></label>
      <label>{{ t('wallet.refundReason') }}<textarea v-model.trim="reason" required maxlength="200" /></label><WalletRefundComponents :store="store" :account="account" :source-type="sourceType" :source-id="sourceId" :amount="amount" :mode="refundMode" :reason="reason" @preview="value=>refundPreview=value" @changed="emit('changed')" /><button :disabled="busy||!refundPreview">{{ t('wallet.refund') }}</button>
    </form>
    <template v-if="canRefund && mode==='refunds'"><RouterLink class="wallet-secondary-link" :to="staffWalletPath(store,'refund-create')">{{ t('wallet.refund') }}</RouterLink><p v-if="!refunds.length" class="wallet-empty">{{ t('wallet.empty') }}</p><article v-for="r in refunds" :key="r.id" class="wallet-record"><strong>{{ money(r.amountCents) }} · {{ t(`wallet.states.${r.status}`) }}</strong><small>{{ r.reason }}</small><RouterLink :to="staffWalletPath(store,`refunds/${r.id}`)">{{ t('wallet.refundParts') }}</RouterLink></article></template>
    <section v-if="detail" class="wallet-card"><h3>{{ t('wallet.refundParts') }} · {{ detail.refund.id }}</h3><p>{{ t(`wallet.states.${detail.refund.status}`) }}</p><WalletRefundAmounts :amounts="detail.refund" />
      <template v-if="detail.refund.status === 'pending'"><label>{{ t('wallet.receipt') }}<input v-model.trim="receipt" maxlength="160"></label><label class="wallet-check"><input v-model="verified" type="checkbox">{{ t('wallet.refundAttested') }}</label></template>
      <div v-for="part in detail.parts" :key="part.id" class="wallet-record"><strong>{{ t(`wallet.channels.${part.channel}`) }} {{ money(part.amountCents) }}</strong><span v-if="part.completed">{{ t('wallet.states.completed') }} · {{ part.receiptReference }}</span><button v-else-if="detail.refund.status === 'pending'" :disabled="busy || !verified || !receipt" @click="confirm(part.id)">{{ t('wallet.confirmRefund') }}</button></div>
      <button v-if="detail.refund.status === 'pending'" :disabled="busy" @click="cancel">{{ t('wallet.cancelRefund') }}</button><RouterLink :to="staffWalletPath(store,'refunds')">{{ t('wallet.ui.back') }}</RouterLink>
    </section>
  </section>
</template>
