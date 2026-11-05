<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useAuthSessionStore} from '../../stores/authSession'
import {useStaffScanText} from './text'
import {staffScanCode} from '../shared-commerce/scanning/staffCode.mjs'
import {createStaffScanSession} from './session'
import {setScanHandoff} from './handoff'
import type {Customer,Account} from '../wallet/types'
import StaffScanPayment from './StaffScanPayment.vue'
import WalletQrScanner from '../shared-commerce/scanning/StaffQrScanner.vue'
import ScanDeviceSettings from '../shared-commerce/scanning/ScanDeviceSettings.vue'
import {useScanDevice} from '../shared-commerce/scanning/useScanDevice'
import WalletLocaleControl from '../wallet/components/WalletLocaleControl.vue'
import '../wallet/wallet.css'
const route=useRoute(),router=useRouter(),auth=useAuthSessionStore(),{t}=useStaffScanText(),store=computed(()=>String(route.params.storeId)),actor=computed(()=>auth.user?.accountId??'')
const {mode}=useScanDevice(()=>store.value)
const hardware=computed(()=>mode.value==='hardware')
const paidAmount=ref(''),paidBillId=ref('')
const recent=ref<{kind:string;quantity:number;status?:string}[]>([]),completedCodes=new Set<string>()
const can=(permission:string)=>auth.user?.permissions.includes(permission)===true
const storeCaption=computed(()=>auth.authorizedStores.find(s=>s.storeId===store.value)?.storeName??store.value)
const allowed=computed(()=>['wallet.view','wallet.cards.view','wallet.charge.create','wallet.topup.create','wallet.points.view','wine_storage.view'].some(can))
let session=createStaffScanSession(store.value,actor.value,()=>actor.value,can),generation=0
const busy=ref(false),error=ref(''),customer=ref<Customer|null>(null),account=ref<Account|null>(null),code=ref(''),result=ref<{kind:string;status?:string;quantity?:number;selections?:{quantity:number}[]}|null>(null),payment=ref(false),paymentBusy=ref(false),revision=ref(0),pending=ref(session.pending())
const blocked=computed(()=>busy.value||paymentBusy.value||pending.value.card||pending.value.storage)
const recoverable=computed(()=>pending.value.owner?.actor===actor.value&&!!pending.value.owner)
watch([store,actor,()=>auth.user?.tenantId],()=>{generation++;recent.value=[];completedCodes.clear();session=createStaffScanSession(store.value,actor.value,()=>actor.value,can);reset();pending.value=session.pending()},{flush:'sync'})
const receive=computed(()=>allowed.value&&!pending.value.card&&!pending.value.storage&&(hardware.value?(!payment.value&&(!result.value||result.value.status==='completed')):(!result.value&&(!code.value||busy.value))))
function reset(){customer.value=null;account.value=null;code.value='';result.value=null;payment.value=false;paymentBusy.value=false;busy.value=false;error.value='';paidAmount.value='';paidBillId.value='';revision.value++}
function paymentCompleted(value:{amount:string;billId:string}){reset();paidAmount.value=value.amount;paidBillId.value=value.billId}
async function run(work:()=>ReturnType<typeof session.scan>|ReturnType<typeof session.recover>){
 if(busy.value||paymentBusy.value)return
 busy.value=true;error.value='';const g=generation,credential=code.value
 try{const value=await work();if(g!==generation)return
  if(value.kind==='customer'){customer.value=value.customer;account.value=value.account}
  else{const detail=value.result as {status?:string;quantity?:number;selections?:{quantity:number}[]};result.value={kind:value.kind,status:detail.status,quantity:detail.quantity,selections:detail.selections};code.value='';if(hardware.value&&result.value.status==='completed'){recent.value=[{kind:value.kind,status:detail.status,quantity:detail.quantity??detail.selections?.reduce((n,s)=>n+s.quantity,0)??0},...recent.value].slice(0,10);if(credential){completedCodes.add(credential);if(completedCodes.size>200)completedCodes.delete(completedCodes.values().next().value!)}}}
 }catch(e){if(g===generation){code.value='';error.value=e instanceof Error?e.message:String(e)}}
 finally{if(g===generation){busy.value=false;pending.value=session.pending()}}
}
async function scan(value:string){
 if(!allowed.value||blocked.value||payment.value||(!hardware.value&&(result.value||code.value))||(result.value&&result.value.status!=='completed'))return
 const parsed=staffScanCode(value,window.location.origin,store.value)
 if(!parsed){error.value=t('invalid');return}
 if(hardware.value&&completedCodes.has(parsed.code)){error.value=t('alreadyScanned');return}
 if(hardware.value)reset()
 code.value=parsed.code;await run(()=>session.scan(parsed))
}
async function handoff(target:'recharge'|'cards'|'storage',task?:string){
 if(blocked.value)return
 setScanHandoff({store:store.value,actor:actor.value,target,code:code.value,task,customerId:customer.value?.id})
 await router.push(target==='storage'?`/stores/${store.value}/wine-storage`:target==='cards'?`/stores/${store.value}/wallet/cards`:`/stores/${store.value}/wallet/topup`)
}
onUnmounted(()=>{generation++;code.value='';customer.value=null;account.value=null})
</script>
<template><main class="wallet-shell staff-scan-page">
 <header class="wallet-header"><RouterLink :to="`/stores/${store}/wallet`">← {{t('back')}}</RouterLink><h1>{{t('title')}}</h1><WalletLocaleControl/></header>
 <details class="wallet-card"><summary>{{t('deviceSettings')}}</summary><ScanDeviceSettings :store="store" :disabled="blocked||!!code"/></details>
 <p class="wallet-muted">{{storeCaption}}</p><p>{{t('hint')}}</p>
 <p v-if="error" role="alert" class="wallet-error">{{error}}</p>
 <p v-if="paidAmount" class="wallet-success" role="status">{{t('paid')}} · SGD {{paidAmount}} <RouterLink :to="`/stores/${store}/wallet/bills/${paidBillId}`">{{t('receipt')}}</RouterLink></p>
 <section v-if="pending.card||pending.storage" class="wallet-card"><p role="status">{{t('pending')}}</p><button :disabled="busy||!recoverable" @click="run(()=>session.recover())">{{t('recover')}}</button><div class="staff-scan-actions"><RouterLink :to="`/stores/${store}/wallet/cards`">{{t('cardRecovery')}}</RouterLink><RouterLink :to="`/stores/${store}/wine-storage`">{{t('storageRecovery')}}</RouterLink></div></section>
 <p v-if="!allowed" class="wallet-empty">{{t('noRights')}}</p>
 <section v-else-if="result" class="wallet-card" role="status"><h2>{{t(result.status==='completed'?'done':'notCompleted')}}</h2><p>{{t(result.kind==='card'?'card':'storage')}}</p><p>{{t('quantity')}}: {{result.quantity??result.selections?.reduce((n,s)=>n+s.quantity,0)}}</p><button v-if="!hardware||result.status!=='completed'" :disabled="blocked" class="wallet-primary" @click="reset">{{t('next')}}</button></section>
 <template v-else-if="code&&!busy">
  <section class="wallet-card"><h2>{{t(customer?'customer':'personal')}}</h2><strong v-if="customer">{{customer.displayName||customer.customerCode}}</strong><p v-if="customer">{{customer.phone}}</p>
   <div class="staff-scan-actions">
    <button v-if="code.startsWith('WI.')&&can('wallet.charge.create')&&can('wallet.collection.confirm')" :disabled="blocked" @click="payment=true">{{t('charge')}}</button>
    <button v-if="/^W[IP]\./.test(code)&&(can('wallet.topup.create')||can('wallet.points.view'))" :disabled="blocked" @click="handoff('recharge')">{{t('recharge')}}</button>
    <button v-if="/^W[IP]\./.test(code)&&can('wallet.cards.view')" :disabled="blocked" @click="handoff('cards')">{{t('cards')}}</button>
    <button v-if="customer&&/^W[IP]\./.test(code)&&can('wine_storage.view')&&can('wine_storage.receive')" :disabled="blocked" @click="handoff('storage','deposit')">{{t('deposit')}}</button>
    <button v-if="customer&&/^W[IP]\./.test(code)&&can('wine_storage.view')&&can('wine_storage.deliver')" :disabled="blocked" @click="handoff('storage','withdraw')">{{t('withdraw')}}</button>
   </div><p v-if="!code.startsWith('WI.')">{{t('freshPersonal')}}</p>
  </section>
  <StaffScanPayment v-if="payment" :key="`${store}:${actor}`" :store="store" :code="code" :continuous="hardware" @busy-change="paymentBusy=$event" @completed="paymentCompleted"/>
  <button :disabled="blocked" @click="reset">{{t('next')}}</button>
 </template>
 <WalletQrScanner v-if="receive" :key="`${store}:${actor}:${revision}`" purpose="staff" auto-start :title="t('title')" :privacy-message="t('privacy')" :manual-label="t('manual')" :invalid-message="t('invalid')" :disabled="blocked" @code="scan"/>
 <section v-if="hardware&&recent.length" class="wallet-card"><h2>{{t('recent')}}</h2><p v-for="(item,i) in recent" :key="i" data-recent-scan>{{t(item.kind==='card'?'card':'storage')}} · {{t('quantity')}}: {{item.quantity}} · {{t('done')}}</p></section>
 <p v-if="busy" role="status">{{t('loading')}}</p>
</main></template>
<style scoped>.staff-scan-page h1{font-size:22px;margin:0}.staff-scan-page .wallet-header{flex-wrap:wrap;gap:12px}.staff-scan-actions{display:flex;flex-wrap:wrap;gap:10px;margin:16px 0}.staff-scan-actions>*{min-height:44px}.staff-scan-page{padding-bottom:32px}</style>
