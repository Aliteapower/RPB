<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { walletApi } from '../api'
import { useWalletTask } from '../useWalletTask'
import { rows } from '../marketingTypes'
import type { Topup } from '../types'
import { dateTime } from '../format'
import { staffWalletPath } from '../navigation'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
const {t}=useI18n(),{store,context,can}=useStaffWallet(),{busy,error,run}=useWalletTask(),records=ref<Topup[]>([]),status=ref('pending'),origin=ref(''),customer=ref(''),ready=ref(false),more=ref(false)
let generation=0
async function load(append=false){if(!can('wallet.collection.confirm')&&!can('wallet.topup.create'))return;const current=generation;await run(async()=>{const result=rows(await walletApi(store.value).get<Topup[]|{items:Topup[]}>(`/topups?offset=${append?records.value.length:0}&status=${encodeURIComponent(status.value)}&origin=${encodeURIComponent(origin.value)}&customer=${encodeURIComponent(customer.value)}`));if(current!==generation)return;records.value=append?[...records.value,...result]:result;more.value=result.length===100;ready.value=true})}
watch(store,()=>{generation++;records.value=[];ready.value=false;void load()},{immediate:true})
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.marketing.applications')}}</h2><button :disabled="busy" @click="load()">{{t('wallet.refresh')}}</button></section><p v-if="error" class="wallet-error" role="alert">{{error}}</p><template v-if="can('wallet.collection.confirm')||can('wallet.topup.create')"><form class="wallet-form" @submit.prevent="load()"><label>{{t('wallet.marketing.status')}}<select v-model="status"><option value="all">{{t('wallet.marketing.all')}}</option><option value="pending">{{t('wallet.marketing.pendingCollection')}}</option><option value="completed">{{t('wallet.marketing.credited')}}</option><option value="closed">{{t('wallet.marketing.cancelled')}}</option></select></label><label>{{t('wallet.marketing.origin')}}<select v-model="origin"><option value="">{{t('wallet.marketing.all')}}</option><option value="customer">{{t('wallet.marketing.customer')}}</option><option value="staff">{{t('wallet.marketing.staff')}}</option></select></label><label>{{t('wallet.customer')}}<input v-model.trim="customer" maxlength="100"></label><button :disabled="busy">{{t('wallet.marketing.load')}}</button></form><p v-if="busy&&!ready" role="status">{{t('wallet.loading')}}</p><p v-if="ready&&!records.length" class="wallet-empty">{{t('wallet.empty')}}</p><RouterLink v-for="r in records" :key="r.id" class="wallet-card wallet-record" :to="staffWalletPath(store,`topups/${r.id}`)"><WalletAmountSplit :principal="r.principalCents??r.amountCents" :bonus="r.bonusCents??0" :total="r.creditTotalCents??r.amountCents" /><span>{{t(`wallet.states.${r.status}`)}} · {{t(`wallet.marketing.${r.origin??'legacy_no_campaign'}`)}}</span><strong v-if="r.customerDeclaredPaidAt">{{t('wallet.marketing.declared')}}</strong><small>{{dateTime(r.createdAt,context?.store.timezone??'Asia/Singapore')}}</small></RouterLink><button v-if="more" :disabled="busy" @click="load(true)">{{t('wallet.marketing.next')}}</button></template><p v-else class="wallet-empty">{{t('wallet.noRights')}}</p></template>
