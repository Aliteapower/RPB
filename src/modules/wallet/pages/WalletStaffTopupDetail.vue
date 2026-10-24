<script setup lang="ts">
import {channelType,channelLabel,topupChannelName} from '../collectionMethods'
import WalletProofHistory from '../proof/WalletProofHistory.vue'
import WalletNextCustomer from '../components/WalletNextCustomer.vue'
import {staffWalletPath} from '../navigation'
import {safeTransactionReturn} from '../transactions/query'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money, dateTime } from '../format'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletRecord } from '../useWalletRecord'
import type { Topup, CollectionConfirm } from '../types'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
import WalletCollectionForm from '../components/WalletCollectionForm.vue'
import WalletQrDisplay from '../components/WalletQrDisplay.vue'
import WalletCustomerSummary from '../components/WalletCustomerSummary.vue'
const {t}=useI18n(),route=useRoute(),workspace=useStaffWallet(),{store,context,can}=workspace
const path=computed(()=>`/topups/${encodeURIComponent(String(route.params.topupId))}`)
const historyReturn=computed(()=>safeTransactionReturn(route.query.returnTo??staffWalletPath(store.value,'transactions')+'?category=topup',store.value))
const {record:topup,ready,busy,error,run,refresh}=useWalletRecord<Topup>(store,path)
const qr=ref('');watch(path,()=>{qr.value=''})
async function confirm(value:CollectionConfirm){await run(async()=>{await walletApi(store.value).command(`${path.value}/confirm`,value as unknown as Record<string,unknown>);qr.value='';await refresh();await workspace.refresh()})}
async function showQr(){await run(async()=>{qr.value=(await walletApi(store.value).get<{payload:string}>(`${path.value}/paynow`)).payload})}
async function cancel(){await run(async()=>{await walletApi(store.value).command(`${path.value}/cancel`);qr.value='';await refresh()})}
</script>
<template><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="!ready && busy" role="status" class="wallet-empty">{{ t('wallet.loading') }}</p>
  <template v-if="topup && context"><section class="wallet-payment-summary"><span class="wallet-status" :data-state="topup.status==='credited'?'paid':topup.status==='cancelled'?'closed':'pending'">{{ t(`wallet.states.${topup.status}`) }}</span><h2>{{ money(topup.amountCents) }}</h2><p>{{ t('wallet.topup') }} · {{ channelLabel(t,topup.channel,undefined,topupChannelName(topup)) }}</p></section>
    <WalletAmountSplit :principal="topup.principalCents??topup.amountCents" :bonus="topup.bonusCents??0" :total="topup.creditTotalCents??topup.amountCents" /><p>{{t('wallet.marketing.origin')}}: {{t(`wallet.marketing.${topup.origin??'legacy_no_campaign'}`)}}</p><p v-if="topup.customerDeclaredPaidAt" class="wallet-warning">{{t('wallet.marketing.declared')}} · {{dateTime(topup.customerDeclaredPaidAt,context.store.timezone)}}</p>
    <WalletCustomerSummary :store="store" :account-id="topup.accountId" />
    <p v-if="topup.status==='pending_collection'" class="wallet-warning">{{ t('wallet.ui.topupNotCredited') }}</p>
    <button v-if="topup.status==='pending_collection' && channelType(topup.channel)==='paynow' && context.transactionsEnabled" class="wallet-block" :disabled="busy" @click="showQr">{{ t('wallet.showPaynow') }}</button>
    <section v-if="qr && topup.status==='pending_collection'" class="wallet-card"><WalletQrDisplay :value="qr" /><p>{{ t('wallet.paynowHint') }}</p></section>
    <WalletCollectionForm :store="store" source-type="topup" :source-id="topup.id" v-if="can('wallet.collection.confirm') && topup.status==='pending_collection'" :key="topup.id" :amount-cents="topup.amountCents" :channel-labels="{[topup.channel]:topupChannelName(topup)||''}" :fixed-channel="topup.channel" :channels="[topup.channel]" :paynow-configured="context.paynowConfigured" :busy="busy" topup @confirm="confirm" />
    <button v-if="can('wallet.topup.create') && topup.status==='pending_collection'" class="wallet-link-button" :disabled="busy" @click="cancel">{{ t('wallet.cancel') }}</button>
    <WalletProofHistory :store="store" source-type="topup" :source-id="topup.id" :key="topup.status" /><WalletNextCustomer v-if="topup.status==='credited'||topup.status==='cancelled'" :disabled="busy" /><RouterLink class="wallet-secondary-link" :to="{path:staffWalletPath(store,`transactions/topup/${topup.id}`),query:{returnTo:historyReturn}}">{{t('wallet.operation.detail')}}</RouterLink><details class="wallet-reference"><summary>{{ t('wallet.ui.transactionInfo') }}</summary><p>{{ t('wallet.reference') }} {{ topup.id }}</p><p>{{ dateTime(topup.createdAt,context.store.timezone) }}</p></details>
  </template>
</template>
