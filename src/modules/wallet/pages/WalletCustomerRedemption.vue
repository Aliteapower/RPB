<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useCustomerWallet} from '../useCustomerWallet'
import {useWalletRecord} from '../useWalletRecord'
import {walletApi} from '../api'
import {customerWalletPath} from '../navigation'
import type {Redemption} from '../redemption'
const {t}=useI18n(),route=useRoute(),workspace=useCustomerWallet(),{store,home}=workspace
const path=computed(()=>`/points-redemptions/${encodeURIComponent(String(route.params.redemptionId))}`)
const {record,busy,error,run,refresh}=useWalletRecord<Redemption>(store,path,true)
async function confirm(){if(busy.value||record.value?.status!=='pending')return;await run(async()=>{try{record.value=await walletApi(store.value,true).command<Redemption>(`${path.value}/confirm`,{expectedVersion:record.value!.expectedVersion});await workspace.refresh()}catch(e){await refresh().catch(()=>{});throw e}})}
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.merchantQr.redemption')}}</h2><p>{{t('wallet.merchantQr.pointsHint')}}</p></section><p v-if="error" class="wallet-error" role="alert">{{error}}</p><section v-if="record" class="wallet-card"><h2>{{record.quantity}} {{t('wallet.merchantQr.points')}}</h2><p>{{record.description}}</p><p>{{home?.context.store.storeName}}</p><p>{{t('wallet.merchantQr.balance')}} {{record.balance}}</p><strong role="status">{{t('wallet.merchantQr.'+record.status)}}</strong><button v-if="record.status==='pending'" class="wallet-primary wallet-block" :disabled="busy||record.balance<record.quantity" @click="confirm">{{t('wallet.merchantQr.confirm')}}</button><RouterLink v-if="record.entryId" :to="customerWalletPath(store,'transactions/points/'+record.entryId)">{{t('wallet.transactions.title')}}</RouterLink><small>{{record.id}}</small></section></template>
<style scoped>
.wallet-card{display:grid;gap:12px}.wallet-card p,.wallet-card h2{margin:0}.wallet-card small{overflow-wrap:anywhere;color:#64748b}
</style>
