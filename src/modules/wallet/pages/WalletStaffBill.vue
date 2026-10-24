<script setup lang="ts">
import WalletNextCustomer from '../components/WalletNextCustomer.vue'
import WalletTransactionEvidence from '../transactions/WalletTransactionEvidence.vue'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money, remaining, dateTime } from '../format'
import { billStage, customerWalletPath, staffWalletPath } from '../navigation'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletRecord } from '../useWalletRecord'
import type { Bill, CollectionConfirm, PaymentCode } from '../types'
import WalletQrDisplay from '../components/WalletQrDisplay.vue'
import WalletCollectionForm from '../components/WalletCollectionForm.vue'
import WalletCustomerSummary from '../components/WalletCustomerSummary.vue'
import WalletIcon from '../components/WalletIcon.vue'
import WalletPaymentResult from '../components/WalletPaymentResult.vue'
const { t } = useI18n(), route = useRoute(), workspace = useStaffWallet(), { store, context, can } = workspace
const path = computed(() => `/bills/${encodeURIComponent(String(route.params.billId))}`)
const { record: bill, ready, busy, error, run, refresh } = useWalletRecord<Bill>(store,path)
const code = ref<PaymentCode | null>(null), paynow = ref(''), collecting = ref(false), scanning = ref(false)
const canCollect = computed(() => can('wallet.collection.confirm') && !!bill.value && !bill.value.closed && remaining(bill.value)>0 && (context.value?.transactionsEnabled || bill.value.walletPaidCents+bill.value.externalPaidCents>0))
const customerUrl = computed(() => code.value ? `${window.location.origin}${customerWalletPath(store.value)}#bill=${encodeURIComponent(code.value.code)}` : '')
watch(path, () => { code.value=null; paynow.value=''; collecting.value=false;scanning.value=false })
watch(()=>bill.value?.walletApplied,applied=>{if(applied)scanning.value=false})
async function showCode() { scanning.value=false; await run(async () => { code.value=await walletApi(store.value).post<PaymentCode>(`${path.value}/code`); paynow.value=''; collecting.value=false }) }
async function showPaynow() { scanning.value=false; await run(async () => { paynow.value=(await walletApi(store.value).get<{payload:string}>(`${path.value}/paynow`)).payload; code.value=null; collecting.value=false }) }
async function collect(value: CollectionConfirm) { await run(async () => { await walletApi(store.value).command(`${path.value}/collections`,value as unknown as Record<string,unknown>); collecting.value=false; code.value=null;paynow.value='';await refresh();await workspace.refresh() }) }
async function cancel() { await run(async () => { await walletApi(store.value).command(`${path.value}/cancel`); code.value=null;collecting.value=false;await refresh() }) }
</script>
<template><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="!ready && busy" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p>
  <template v-if="bill && context"><WalletPaymentResult :key="bill.id" :bill="bill" /><section class="wallet-payment-summary"><span class="wallet-status" :data-state="billStage(bill)">{{ t(`wallet.ui.status.${billStage(bill)}`) }}</span><h2>{{ money(bill.amountCents) }}</h2><p>{{ context.store.storeName }}</p><small>{{ t('wallet.issuer') }} · {{ context.store.operatingEntityName }}</small></section>
    <WalletCustomerSummary :store="store" :account-id="bill.accountId" />
    <WalletAmountSplit :principal="bill.principalPaidCents??bill.walletPaidCents" :bonus="bill.bonusPaidCents??0" /><dl class="wallet-detail-fields"><dt>{{ t('wallet.walletPaid') }}</dt><dd>{{ money(bill.walletPaidCents) }}</dd><dt>{{ t('wallet.otherPaid') }}</dt><dd>{{ money(bill.externalPaidCents) }}</dd><dt>{{ t('wallet.stillDue') }}</dt><dd :class="{'wallet-due':!bill.closed && remaining(bill)>0}">{{ bill.closed ? '—' : money(remaining(bill)) }}</dd></dl>
    <p v-if="bill.refundStatus!=='none'" class="wallet-warning">{{ t(`wallet.states.${bill.refundStatus}`) }}</p>
    <div v-if="!bill.closed && remaining(bill)>0" class="wallet-detail-actions">
      <button v-if="can('wallet.charge.create') && context.transactionsEnabled && !bill.walletApplied" class="wallet-primary" :disabled="busy" @click="showCode"><WalletIcon name="qr" />{{ t('wallet.showBillCode') }}</button>
      <button v-if="canCollect" class="wallet-primary" :disabled="busy" @click="collecting=!collecting;code=null;paynow='';scanning=false">{{ t(collecting?'wallet.ui.hideCollection':'wallet.confirmSupplement') }}</button>
      <button v-if="canCollect && context.paynowConfigured" :disabled="busy" @click="showPaynow">{{ t('wallet.showPaynow') }}</button>
      <button v-if="can('wallet.charge.create') && bill.walletPaidCents+bill.externalPaidCents===0" class="wallet-link-button" :disabled="busy" @click="cancel">{{ t('wallet.cancel') }}</button>
    </div>
    <p v-if="!bill.closed && !bill.walletApplied && remaining(bill)>0" class="wallet-warning">{{t('wallet.customerConfirm')}}</p>
    <section v-if="code && !bill.walletApplied && !bill.closed" class="wallet-card"><WalletQrDisplay :value="customerUrl" :expires-at="code.expiresAt" /><p class="wallet-center">{{ t('wallet.customerConfirm') }}</p><a :href="customerUrl" target="_blank" rel="noreferrer">{{ t('wallet.customerEntry') }}</a></section>
    <section v-if="paynow && !bill.closed && remaining(bill)>0" class="wallet-card"><WalletQrDisplay :value="paynow" /><p>{{ t('wallet.paynowHint') }}</p></section>
    <WalletCollectionForm :store="store" source-type="bill" :source-id="bill.id" :channel-labels="context?.program?.channelLabels" v-if="collecting && canCollect" :key="bill.id" :amount-cents="remaining(bill)" :channels="context.program?.channels ?? []" :paynow-configured="context.paynowConfigured" :busy="busy" @confirm="collect" />
    <WalletNextCustomer v-if="bill.closed||remaining(bill)===0" :disabled="busy" collect /><WalletTransactionEvidence :store="store" source-type="bill" :id="bill.id" /><details class="wallet-reference"><summary>{{ t('wallet.ui.transactionInfo') }}</summary><p>{{ t('wallet.reference') }} {{ bill.id }}</p><p>{{ dateTime(bill.createdAt,context.store.timezone) }}</p></details>
    <RouterLink v-if="can('wallet.refund.manage') && bill.walletPaidCents+bill.externalPaidCents>0" :to="{path:staffWalletPath(store,'refund-create'),query:{sourceType:'bill',sourceId:bill.id}}" class="wallet-secondary-link">{{ t('wallet.refund') }}</RouterLink>
  </template>
</template>
