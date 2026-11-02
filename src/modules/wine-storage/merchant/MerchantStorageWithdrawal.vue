<script setup lang="ts">
import {computed,ref,onUnmounted} from 'vue'
import type {Home,Intent,Selection} from '../types'
import type {StorageApi} from '../api'
import StorageIntentPanel from '../StorageIntentPanel.vue'
import StoragePickupCart from '../StoragePickupCart.vue'
import {merchantSelection} from './scope'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;customerId:string;api:StorageApi;disabled:boolean;canDeliver:boolean;activeIntent:Intent|null}>(),emit=defineEmits<{command:[()=>Promise<unknown>,string?];error:[unknown];refresh:[];'busy-change':[boolean];cleared:[]}>(),{t,locale}=useMerchantStorageText()
const now=ref(Date.now()),submitting=ref(false),resetKey=ref(0),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{clearInterval(timer);emit('busy-change',false)})
const items=computed(()=>props.home.items.filter(i=>i.customerId===props.customerId))
const intent=computed(()=>{if(!props.activeIntent||props.activeIntent.customerId!==props.customerId)return null;return {...(props.home.intents.find(i=>i.id===props.activeIntent!.id)??props.activeIntent),code:null}})
const pendingIntent=computed(()=>intent.value&&['awaiting_authorization','authorized'].includes(intent.value.status)&&Date.parse(intent.value.expiresAt)>now.value?intent.value:null)
function submit(lines:Selection[]){
 if(props.disabled||!props.canDeliver||submitting.value||pendingIntent.value)return
 const command=merchantSelection(props.customerId,props.home.items,Object.fromEntries(lines.map(l=>[l.itemId,l.quantity])),now.value)
 if(!command){emit('error',new Error(x('存酒数量或状态已变化，请调整取酒车。','Stock or eligibility changed. Adjust the cart.')));return}
 emit('command',async()=>{if(submitting.value)return;submitting.value=true;try{await props.api.command('/direct-deliver',command);resetKey.value++;emit('cleared')}finally{submitting.value=false}},t('delivered'))
}
</script>
<template><section class="storage-panel">
 <StoragePickupCart :items="items" :events="home.events" :api="api" :scope="api.base+'|'+customerId" :reset-key="resetKey" :disabled="disabled||!canDeliver||submitting||!!pendingIntent" :submit-label="x('提交取酒','Submit pickup')" :submit-hint="x('提交即确认整车实物已交付，并一次扣减存酒。','Submitting confirms physical handover of the entire cart and deducts stock.')" @submit="submit"/>
 <template v-if="intent"><p v-if="pendingIntent" class="storage-notice">{{t('deliveryHint')}}</p><StorageIntentPanel :intent="intent" :items="home.items" :customer="false" :timezone="home.timezone" :disabled="disabled||Date.parse(intent.expiresAt)<=now" :can-deliver="canDeliver" @deliver="emit('command',async()=>{await api.command(`/intents/${intent!.id}/deliver`,{});emit('cleared')},t('delivered'))" @cancel="emit('command',async()=>{await api.command(`/intents/${intent!.id}/cancel`,{});emit('cleared')})"/></template>
</section></template>
