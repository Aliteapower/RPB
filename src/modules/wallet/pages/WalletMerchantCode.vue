<script setup lang="ts">
import {computed,ref,onUnmounted} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {money,remaining} from '../format'
import {walletApi,createWalletBill} from '../api'
import {billStage,customerWalletPath,staffWalletPath} from '../navigation'
import type {Bill,PaymentCode} from '../types'
import type {Redemption} from '../redemption'
import WalletPhoneCustomerPicker from '../components/WalletPhoneCustomerPicker.vue'
import WalletAmountKeypad from '../components/WalletAmountKeypad.vue'
import WalletQrDisplay from '../components/WalletQrDisplay.vue'
const {t}=useI18n(),route=useRoute(),router=useRouter(),w=useStaffWallet(),{store,customer,phoneConfirmedCustomerId,account,can,busy,error,run}=w
const points=computed(()=>route.query.kind==='points'||!can('wallet.charge.create'))
const pickerRevision=ref(0)
const amount=ref(''),quantity=ref(''),description=ref(''),bill=ref<Bill|null>(null),redemption=ref<Redemption|null>(null),code=ref<PaymentCode|null>(null)
const active=computed(()=>!!bill.value||!!redemption.value)
const valid=computed(()=>points.value?/^[1-9]\d{0,9}$/.test(quantity.value)&&Number(quantity.value)<=1e9&&!!description.value.trim():Number(amount.value)>0)
const url=computed(()=>code.value?`${window.location.origin}${customerWalletPath(store.value)}#${points.value?'redeem':'bill'}=${encodeURIComponent(code.value.code)}`:'')
function switchKind(kind:string){if(busy.value||active.value)return;void router.replace({query:{entry:'phone',kind}})}
async function confirmed(id:string){phoneConfirmedCustomerId.value=id}
async function issue(){await run(async()=>{code.value=await walletApi(store.value).post<PaymentCode>(points.value?`/points-redemptions/${redemption.value!.id}/code`:`/bills/${bill.value!.id}/code`)})}
async function create(){if(busy.value||!valid.value||!customer.value||phoneConfirmedCustomerId.value!==customer.value.id)return
 if(!points.value){await w.choose(customer.value,true);if(!account.value){error.value=t('wallet.ui.noAccount');return}}
 await run(async()=>{if(points.value){redemption.value=await walletApi(store.value).command<Redemption>('/points-redemptions',{customerId:customer.value!.id,quantity:Number(quantity.value),description:description.value.trim()})}else{bill.value=await createWalletBill(store.value,account.value!.id,amount.value,'')}})
 if(active.value)await issue()
}
async function cancel(){await run(async()=>{if(points.value){redemption.value=await walletApi(store.value).command<Redemption>(`/points-redemptions/${redemption.value!.id}/cancel`)}else{bill.value=await walletApi(store.value).command<Bill>(`/bills/${bill.value!.id}/cancel`)}code.value=null})}
function next(){if(busy.value)return;w.clear();pickerRevision.value++;amount.value='';quantity.value='';description.value='';bill.value=null;redemption.value=null;code.value=null;error.value=''}
const pending=computed(()=>points.value?redemption.value?.status==='pending':!!bill.value&&!bill.value.closed&&!bill.value.walletApplied)
const timer=window.setInterval(()=>{if(!active.value||busy.value)return;void run(async()=>{if(points.value){redemption.value=await walletApi(store.value).get<Redemption>(`/points-redemptions/${redemption.value!.id}`)}else{bill.value=await walletApi(store.value).get<Bill>(`/bills/${bill.value!.id}`)}if(!pending.value)code.value=null})},3000)
onUnmounted(()=>window.clearInterval(timer))
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.merchantQr.title')}}</h2><p>{{t('wallet.merchantQr.hint')}}</p></section><p v-if="error" class="wallet-error" role="alert">{{error}}</p>
<nav class="wallet-cashier-tabs"><button v-if="can('wallet.charge.create')&&can('wallet.view')" :disabled="busy||active" :aria-pressed="!points" @click="switchKind('money')">{{t('wallet.merchantQr.money')}}</button><button v-if="can('wallet.points.view')&&can('wallet.points.redeem')" :disabled="busy||active" :aria-pressed="points" @click="switchKind('points')">{{t('wallet.merchantQr.redemption')}}</button></nav>
<WalletPhoneCustomerPicker :key="pickerRevision" hint-key="wallet.merchantQr.phoneHint" confirm-hint-key="wallet.merchantQr.phoneConfirmHint" :disabled="active" :confirmed-id="phoneConfirmedCustomerId" @confirmed="confirmed" />
<section v-if="customer&&phoneConfirmedCustomerId===customer.id&&!active" class="wallet-card wallet-form"><template v-if="points"><label>{{t('wallet.merchantQr.points')}}<input v-model="quantity" inputmode="numeric" maxlength="10" :disabled="busy"></label><label>{{t('wallet.merchantQr.description')}}<textarea v-model="description" maxlength="1000" :disabled="busy" /></label></template><WalletAmountKeypad v-else v-model="amount" :store="store" :disabled="busy" /><p>{{t('wallet.merchantQr.confirmHint')}}</p><button class="wallet-primary wallet-block" :disabled="busy||!valid" @click="create">{{t('wallet.merchantQr.generate')}}</button></section>
<section v-if="active" class="wallet-card"><template v-if="bill"><h2>{{money(bill.amountCents)}}</h2><strong role="status">{{t('wallet.ui.status.'+billStage(bill))}}</strong><p v-if="bill.walletApplied&&remaining(bill)>0&&!bill.closed">{{t('wallet.ui.supplementHint',{amount:money(remaining(bill))})}}</p></template><p v-if="redemption">{{redemption.quantity}} {{t('wallet.merchantQr.points')}} · {{redemption.description}} · {{t('wallet.merchantQr.'+redemption.status)}}</p><WalletQrDisplay v-if="code&&pending" :value="url" :expires-at="code.expiresAt" /><a v-if="code&&pending" :href="url" target="_blank" rel="noreferrer">{{t('wallet.customerEntry')}}</a><p v-if="pending">{{t('wallet.merchantQr.confirmHint')}}</p><button v-if="pending" :disabled="busy" @click="issue">{{t('wallet.refresh')}}</button><button v-if="pending" :disabled="busy" @click="cancel">{{t('wallet.merchantQr.cancel')}}</button><RouterLink v-if="bill" :to="staffWalletPath(store,'bills/'+bill.id)">{{t('wallet.ui.viewBill')}}</RouterLink><RouterLink v-if="redemption?.entryId" :to="staffWalletPath(store,'transactions/points/'+redemption.entryId)">{{t('wallet.transactions.title')}}</RouterLink><button v-if="!pending&&(!bill||bill.closed||remaining(bill)===0)" :disabled="busy" @click="next">{{t('wallet.ui.changeCustomer')}}</button></section></template>
