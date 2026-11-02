<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import type {Item,Intent,Event,Selection} from '../types'
import type {StorageApi} from '../api'
import StoragePickupCart from '../StoragePickupCart.vue'
import WalletQrDisplay from '../../wallet/components/WalletQrDisplay.vue'
import {withdrawalSelection} from './selection'
import {displayName} from '../../shared-commerce/format'
import {useCustomerStorageText} from './text'
const props=defineProps<{api:StorageApi;items:Item[];events?:Event[];disabled:boolean;activeIntent?:Intent|null}>(),emit=defineEmits<{created:[Intent];'quick-created':[Intent];cancel:[];error:[unknown];deposit:[];'busy-change':[boolean]}>(),{t,locale}=useCustomerStorageText()
const saving=ref(false),now=ref(Date.now()),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
let active=true
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{active=false;clearInterval(timer);emit('busy-change',false)})
watch(saving,value=>emit('busy-change',value),{flush:'sync'})
const liveCode=computed(()=>props.activeIntent?.origin==='customer'&&props.activeIntent.status==='authorized'&&!!props.activeIntent.code&&Date.parse(props.activeIntent.expiresAt)>now.value?props.activeIntent:null)
async function submit(selected:Selection[]){
 if(props.disabled||saving.value||liveCode.value)return
 const lines=withdrawalSelection(props.items,Object.fromEntries(selected.map(l=>[l.itemId,l.quantity])))
 if(!lines){emit('error',new Error(t('selectionChanged')));return}
 saving.value=true
 try{const result=await props.api.command<Intent>('/intents',{selections:lines});if(active)emit('quick-created',result)}catch(e){if(active)emit('error',e)}finally{if(active)saving.value=false}
}
</script>
<template><section class="storage-panel storage-section">
 <section v-if="liveCode" class="storage-panel"><h2>{{x('整车取酒码','Batch pickup code')}}</h2><p v-for="line in liveCode.selections" :key="line.itemId">{{items.find(i=>i.id===line.itemId)?displayName(items.find(i=>i.id===line.itemId)!.snapshot,locale):'—'}} × {{line.quantity}} {{x('瓶','bottles')}}</p><WalletQrDisplay :value="liveCode.code!" :expires-at="liveCode.expiresAt"/><p>{{x('请交由店员核对并扫码，核销后才扣减存酒。','Staff checks and scans this code before stock is deducted.')}}</p><button type="button" :disabled="disabled||saving" @click="emit('cancel')">{{t('cancel')}}</button></section>
 <StoragePickupCart v-else :items="items" :events="events" :api="api" :scope="api.base" :disabled="disabled||saving" :submit-label="x('提交取酒，生成取酒码','Submit and get pickup code')" :submit-hint="x('一次生成整车取酒码，由店员核销后扣减存酒。','One code for the entire cart. Stock is deducted after staff handover.')" @submit="submit"/>
</section></template>
