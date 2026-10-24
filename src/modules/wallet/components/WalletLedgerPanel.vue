<script setup lang="ts">
import {channelType,channelLabel,topupChannelName} from '../collectionMethods'
import WalletTransactionEvidence from '../transactions/WalletTransactionEvidence.vue'
import WalletRefundAmounts from './WalletRefundAmounts.vue'
import WalletRefundComponents from './WalletRefundComponents.vue'
import type { RefundPreview } from '../marketingTypes'


import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {safeTransactionReturn} from '../transactions/query'
import { staffWalletPath } from '../navigation'
import { walletApi, WalletApiError } from '../api'
import { money } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Account, RefundDetail } from '../types'
const props = withDefaults(defineProps<{ store: string; account: Account | null; timezone: string; canRefund: boolean; mode?: string; refundId?: string }>(), { mode: 'refund-detail', refundId: '' })
const emit = defineEmits<{ changed: []; created: [id: string] }>()
const route = useRoute()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const detail = ref<RefundDetail | null>(null)
const refundPreview=ref<RefundPreview|null>(null),refundMode=ref('normal')
const sourceType = ref('bill'), sourceId = ref(''), amount = ref(''), reason = ref(''), receipt = ref(''), verified = ref(false)
if (['bill','top_up','collection_exception'].includes(String(route.query.sourceType))) sourceType.value=String(route.query.sourceType)
sourceId.value=String(route.query.sourceId||'')
async function refresh() {
 if (props.canRefund && props.mode==='refund-detail' && props.refundId) detail.value=await walletApi(props.store).get<RefundDetail>(`/refunds/${props.refundId}`)
}
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
  <section><div class="wallet-page-heading"><h2>{{ t(mode==='refund-create'?'wallet.refund':'wallet.refunds') }}</h2><button class="wallet-link-button" @click="run(refresh)">{{ t('wallet.refresh') }}</button></div>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>    <form v-if="canRefund && mode==='refund-create'" class="wallet-form" @submit.prevent="create"><p>{{ t('wallet.refundHint') }}</p>
      <label>{{ t('wallet.source') }}<select v-model="sourceType"><option value="bill">{{ t('wallet.bills') }}</option><option value="top_up">{{ t('wallet.topup') }}</option><option value="collection_exception">{{ t('wallet.collectionExceptions') }}</option></select></label>
      <label>{{ t('wallet.reference') }}<input v-model.trim="sourceId" required pattern="[0-9a-fA-F-]{36}"></label>
      <label>{{t('wallet.marketing.refundMode')}}<select v-model="refundMode"><option value="normal">{{t('wallet.marketing.normal')}}</option><option v-if="sourceType==='top_up'" value="remaining_lot_exit">{{t('wallet.marketing.remaining_lot_exit')}}</option></select></label><label v-if="refundMode==='normal'">{{ t('wallet.refundAmount') }}<input v-model="amount" type="number" min="0.01" step="0.01" inputmode="decimal" required></label>
      <label>{{ t('wallet.refundReason') }}<textarea v-model.trim="reason" required maxlength="200" /></label><WalletRefundComponents :store="store" :account="account" :source-type="sourceType" :source-id="sourceId" :amount="amount" :mode="refundMode" :reason="reason" @preview="value=>refundPreview=value" @changed="emit('changed')" /><button :disabled="busy||!refundPreview">{{ t('wallet.refund') }}</button>
    </form>
    <section v-if="detail" class="wallet-card"><WalletTransactionEvidence :store="store" source-type="refund" :id="detail.refund.id" /><h3>{{ t('wallet.refundParts') }} · {{ detail.refund.id }}</h3><p>{{ t(`wallet.states.${detail.refund.status}`) }}</p><WalletRefundAmounts :amounts="detail.refund" />
      <template v-if="detail.refund.status === 'pending'"><label>{{ t('wallet.receipt') }}<input v-model.trim="receipt" maxlength="160"></label><label class="wallet-check"><input v-model="verified" type="checkbox">{{ t('wallet.refundAttested') }}</label></template>
      <div v-for="part in detail.parts" :key="part.id" class="wallet-record"><strong>{{ channelLabel(t,part.channel,undefined,part.channelName) }} {{ money(part.amountCents) }}</strong><span v-if="part.completed">{{ t('wallet.states.completed') }} · {{ part.receiptReference }}</span><button v-else-if="detail.refund.status === 'pending'" :disabled="busy || !verified || !receipt" @click="confirm(part.id)">{{ t('wallet.confirmRefund') }}</button></div>
      <button v-if="detail.refund.status === 'pending'" :disabled="busy" @click="cancel">{{ t('wallet.cancelRefund') }}</button><RouterLink :to="safeTransactionReturn(route.query.returnTo,store)">{{ t('wallet.ui.back') }}</RouterLink>
    </section>
  </section>
</template>
