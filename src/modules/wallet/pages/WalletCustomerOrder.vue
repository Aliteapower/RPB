<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useCustomerWallet} from '../useCustomerWallet'
import {useWalletRecord} from '../useWalletRecord'
import {walletApi} from '../api'
import {money} from '../format'
import {customerWalletPath} from '../navigation'
import type {MerchantOrder} from '../merchantOrder'
const {t}=useI18n(),route=useRoute(),workspace=useCustomerWallet(),{store,home}=workspace
const path=computed(()=>`/merchant-orders/${encodeURIComponent(String(route.params.orderId))}`)
const {record,busy,error,run,refresh}=useWalletRecord<MerchantOrder>(store,path,true)
const enough=computed(()=>record.value?.expectedVersion!=null&&(record.value.balance??0)>=(record.value.kind==='money'?record.value.amountCents??0:record.value.quantity??0))
async function confirm(){if(busy.value||record.value?.status!=='pending'||!enough.value)return;await run(async()=>{try{record.value=await walletApi(store.value,true).command<MerchantOrder>(`${path.value}/confirm`,{expectedVersion:record.value!.expectedVersion});await workspace.refresh()}catch(e){await refresh().catch(()=>{});throw e}})}
</script>
<template><section class="wallet-page-heading"><h2>{{t(record?.kind==='points'?'wallet.merchantQr.redemption':'wallet.merchantQr.money')}}</h2><p>{{t(record?.kind==='points'?'wallet.merchantQr.pointsHint':'wallet.merchantQr.paymentHint')}}</p></section><p v-if="error" class="wallet-error" role="alert">{{error}}</p><section v-if="record" class="wallet-card wallet-order-detail"><h2>{{record.kind==='money'?money(record.amountCents!):record.quantity+' '+t('wallet.merchantQr.points')}}</h2><p>{{record.description}}</p><p>{{home?.context.store.storeName}}</p><p>{{t(record.kind==='points'?'wallet.merchantQr.balance':'wallet.available')}} {{record.kind==='points'?record.balance:money(record.balance??0)}}</p><strong role="status">{{t('wallet.merchantQr.'+record.status)}}</strong><p v-if="record.status==='pending'&&!enough" class="wallet-warning">{{t('wallet.merchantQr.insufficient')}}</p><button v-if="record.status==='pending'" class="wallet-primary wallet-block" :disabled="busy||!enough" @click="confirm">{{t(record.kind==='points'?'wallet.merchantQr.confirm':'wallet.merchantQr.pay')}}</button><RouterLink v-if="record.sourceId" :to="customerWalletPath(store,'transactions/'+record.sourceType+'/'+record.sourceId)">{{t('wallet.transactions.title')}}</RouterLink><small>{{record.id}}</small></section></template>
<style scoped>.wallet-order-detail{display:grid;gap:12px}.wallet-order-detail p,.wallet-order-detail h2{margin:0}.wallet-order-detail small{overflow-wrap:anywhere;color:#64748b}</style>
