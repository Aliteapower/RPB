<script setup lang="ts">
import WalletEntryAllocations from '../components/WalletEntryAllocations.vue'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
import { onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { walletApi } from '../api'
import { money,dateTime } from '../format'
import { customerWalletPath } from '../navigation'
import { useWalletTask } from '../useWalletTask'
import type { Entry } from '../types'
const{t}=useI18n(),{store,home,account}=useCustomerWallet(),{busy,error,run}=useWalletTask()
const entries=ref<Entry[]>([]),more=ref(false),loaded=ref(false);let generation=0
async function load(append=false){const id=account.value?.id,current=generation;if(!id){loaded.value=true;return}const result=await walletApi(store.value,true).get<Entry[]>(`/accounts/${id}/entries?offset=${append?entries.value.length:0}`);if(current!==generation)return;entries.value=append?[...entries.value,...result.filter(e=>!entries.value.some(old=>old.id===e.id))]:result;more.value=result.length===100;loaded.value=true}
watch(()=>account.value?.id,()=>{generation++;entries.value=[];more.value=false;loaded.value=false;void run(()=>load())},{immediate:true})
onUnmounted(()=>{generation++})
</script>
<template><div class="wallet-page-heading"><h2>{{ t('wallet.ui.entries') }}</h2><button class="wallet-link-button" :disabled="busy" @click="run(()=>load())">{{ t('wallet.refresh') }}</button></div><p class="wallet-muted">{{ t('wallet.ui.entriesHint') }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="busy && !loaded" role="status">{{ t('wallet.loading') }}</p><p v-if="loaded && !entries.length" class="wallet-empty">{{ t('wallet.empty') }}</p>
<section class="wallet-list"><article v-for="e in entries" :key="e.id" class="wallet-entry-row"><div><strong>{{ t(`wallet.states.${e.kind}`) }}</strong><small>{{ dateTime(e.createdAt,home?.context.store.timezone) }}</small></div><div class="wallet-list-amount"><strong :class="{'wallet-credit':['topup_credit','bill_refund'].includes(e.kind)}">{{ ['topup_credit','bill_refund'].includes(e.kind)?'+':'−' }}{{ money(e.amountCents) }}</strong><small>{{ t('wallet.balance') }} {{ money(e.afterCents) }}</small></div><WalletAmountSplit :principal="e.principalCents??e.amountCents" :bonus="e.bonusCents??0" /><WalletEntryAllocations :entry="e" /><RouterLink v-if="e.kind==='bill_debit'" class="wallet-entry-link" :to="customerWalletPath(store,`bills/${e.sourceId}`)">{{ t('wallet.ui.viewBill') }}</RouterLink></article></section><button v-if="more" class="wallet-block" :disabled="busy" @click="run(()=>load(true))">{{ t('wallet.loadMore') }}</button></template>
