<script setup lang="ts">
import {computed,ref,onUnmounted} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {useWalletTask} from '../useWalletTask'
import {money} from '../format'
import {walletApi} from '../api'
import {customerWalletPath,staffWalletPath} from '../navigation'
import type {MerchantOrder} from '../merchantOrder'
import WalletAmountKeypad from '../components/WalletAmountKeypad.vue'
import WalletQrDisplay from '../components/WalletQrDisplay.vue'
const {t}=useI18n(),route=useRoute(),router=useRouter(),w=useStaffWallet(),{store,can}=w,{busy,error,run}=useWalletTask()
const points=computed(()=>route.query.kind==='points'||!can('wallet.charge.create'))
const amount=ref(''),quantity=ref(''),description=ref(''),order=ref<MerchantOrder|null>(null),code=ref<{code:string;expiresAt:string}|null>(null)
const valid=computed(()=>points.value?/^[1-9]\d{0,9}$/.test(quantity.value)&&Number(quantity.value)<=1e9&&!!description.value.trim():Number(amount.value)>0)
const pending=computed(()=>order.value?.status==='pending')
const url=computed(()=>code.value?`${window.location.origin}${customerWalletPath(store.value)}#order=${encodeURIComponent(code.value.code)}`:'')
function switchKind(kind:string){if(busy.value||order.value)return;amount.value='';quantity.value='';description.value='';void router.replace({query:{kind}})}
async function issue(){await run(async()=>{code.value=await walletApi(store.value).post<{code:string;expiresAt:string}>(`/merchant-orders/${order.value!.id}/code`)})}
async function create(){if(busy.value||!valid.value||order.value)return;await run(async()=>{order.value=await walletApi(store.value).command<MerchantOrder>('/merchant-orders',points.value?{kind:'points',quantity:Number(quantity.value),description:description.value.trim()}:{kind:'money',amount:amount.value,description:description.value.trim()})});if(order.value)await issue()}
async function cancel(){await run(async()=>{order.value=await walletApi(store.value).command<MerchantOrder>(`/merchant-orders/${order.value!.id}/cancel`);code.value=null})}
function next(){if(busy.value||pending.value)return;w.clear();amount.value='';quantity.value='';description.value='';order.value=null;code.value=null;error.value=''}
const timer=window.setInterval(()=>{if(!order.value||busy.value||document.visibilityState!=='visible')return;void run(async()=>{order.value=await walletApi(store.value).get<MerchantOrder>(`/merchant-orders/${order.value!.id}`);if(!pending.value)code.value=null})},3000)
onUnmounted(()=>window.clearInterval(timer))
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.merchantQr.title')}}</h2><p>{{t('wallet.merchantQr.hint')}}</p></section><p v-if="error" class="wallet-error" role="alert">{{error}}</p>
<nav class="wallet-cashier-tabs"><button v-if="can('wallet.charge.create')&&can('wallet.view')" :disabled="busy||!!order" :aria-pressed="!points" @click="switchKind('money')">{{t('wallet.merchantQr.money')}}</button><button v-if="can('wallet.points.view')&&can('wallet.points.redeem')" :disabled="busy||!!order" :aria-pressed="points" @click="switchKind('points')">{{t('wallet.merchantQr.redemption')}}</button></nav>
<section v-if="!order" class="wallet-card wallet-form"><template v-if="points"><label>{{t('wallet.merchantQr.points')}}<input v-model="quantity" inputmode="numeric" maxlength="10" :disabled="busy"></label><label>{{t('wallet.merchantQr.description')}}<textarea v-model="description" maxlength="1000" :disabled="busy" /></label></template><template v-else><WalletAmountKeypad v-model="amount" :store="store" :disabled="busy" /><label>{{t('wallet.personal.description')}}<textarea v-model="description" maxlength="200" :disabled="busy" /></label></template><p>{{t('wallet.merchantQr.confirmHint')}}</p><button class="wallet-primary wallet-block" :disabled="busy||!valid" @click="create">{{t('wallet.merchantQr.generate')}}</button></section>
<section v-else class="wallet-card wallet-form"><h2>{{order.kind==='money'?money(order.amountCents!):order.quantity+' '+t('wallet.merchantQr.points')}}</h2><p>{{order.description}}</p><strong role="status">{{t('wallet.merchantQr.'+order.status)}}</strong><WalletQrDisplay v-if="code&&pending" :value="url" :expires-at="code.expiresAt" /><a v-if="code&&pending" :href="url" target="_blank" rel="noreferrer">{{t('wallet.customerEntry')}}</a><p v-if="pending">{{t('wallet.merchantQr.confirmHint')}}</p><button v-if="pending" :disabled="busy" @click="issue">{{t('wallet.refresh')}}</button><button v-if="pending" :disabled="busy" @click="cancel">{{t('wallet.merchantQr.cancel')}}</button><RouterLink v-if="order.sourceId" :to="staffWalletPath(store,'transactions/'+order.sourceType+'/'+order.sourceId)">{{t('wallet.transactions.title')}}</RouterLink><button v-if="!pending" :disabled="busy" @click="next">{{t('wallet.merchantQr.next')}}</button></section></template>

<style scoped>.wallet-cashier-tabs{border-color:#cbd5e1}.wallet-cashier-tabs button{background:white;color:#475569;border-color:#cbd5e1}.wallet-cashier-tabs button[aria-pressed="true"]{background:#eaf4ff;color:#1264dd;border-color:#1677ff}</style>
