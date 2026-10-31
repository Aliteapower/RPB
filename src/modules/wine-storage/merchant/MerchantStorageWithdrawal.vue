<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import type {Home,Intent} from '../types'
import {StorageApiError,type StorageApi} from '../api'
import StorageIntentPanel from '../StorageIntentPanel.vue'
import StorageQuantityStepper from '../StorageQuantityStepper.vue'
import WalletQrDisplay from '../../wallet/components/WalletQrDisplay.vue'
import {merchantSelection} from './scope'
import {displayName} from '../../shared-commerce/format'
import {dateTime,storageLocationText} from '../format'
import {useMerchantStorageText} from './text'
import {useStorageCodeRefresh} from '../customer/useStorageCodeRefresh'
const props=defineProps<{home:Home;customerId:string;api:StorageApi;disabled:boolean;canDeliver:boolean;activeIntent:Intent|null}>(),emit=defineEmits<{created:[Intent];command:[()=>Promise<unknown>,string?];error:[unknown];refresh:[];'busy-change':[boolean];cleared:[]}>(),{t,locale}=useMerchantStorageText()
const quantities=ref<Record<string,number>>({}),saving=ref(false),now=ref(Date.now()),observed=ref<Intent|null>(null)
const directMode=computed(()=>props.home.operationMode==='staff_registration')
const confirmation=ref<{itemId:string;quantity:number;name:string}|null>(null)
watch(directMode,()=>{confirmation.value=null})
function prepareDirect(id:string){
 if(props.disabled||saving.value||!props.canDeliver||!directMode.value)return
 const selected=selection(id)?.selections[0],item=props.home.items.find(i=>i.id===id)
 if(selected&&item)confirmation.value={...selected,name:displayName(item.snapshot,locale.value)}
}
function deliverDirect(){
 const selected=confirmation.value
 if(!selected||props.disabled||!props.canDeliver||!directMode.value)return
 const command={customerId:props.customerId,selections:[{itemId:selected.itemId,quantity:selected.quantity}]}
 emit('command',async()=>{await props.api.command('/direct-deliver',command);confirmation.value=null;emit('cleared')},t('delivered'))
}
let active=true
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{active=false;clearInterval(timer);emit('busy-change',false)})
watch(saving,value=>emit('busy-change',value),{flush:'sync'})
watch(()=>props.activeIntent?.id,()=>{observed.value=null})
const items=computed(()=>props.home.items.filter(i=>i.customerId===props.customerId&&i.status==='stored'&&i.remaining>0&&Date.parse(i.expiresAt)>now.value))
const intent=computed(()=>{
 if(!props.activeIntent||props.activeIntent.customerId!==props.customerId)return null
 const stored=props.home.intents.find(i=>i.id===props.activeIntent!.id)
 const latest=stored&&!['awaiting_authorization','authorized'].includes(stored.status)?stored:observed.value??stored??props.activeIntent
 return {...latest,code:['awaiting_authorization','authorized'].includes(latest.status)&&Date.parse(latest.expiresAt)>now.value?props.activeIntent.code:null}
})
const pendingIntent=computed(()=>intent.value&&['awaiting_authorization','authorized'].includes(intent.value.status)&&Date.parse(intent.value.expiresAt)>now.value?intent.value:null)
function codeFor(id:string){return pendingIntent.value?.selections.length===1&&pendingIntent.value.selections[0]?.itemId===id?pendingIntent.value:null}
function quantity(id:string){return quantities.value[id]??1}
function selection(id:string){return merchantSelection(props.customerId,props.home.items,{[id]:quantity(id)},now.value)}
async function create(id:string){
 if(props.disabled||saving.value||!props.canDeliver||pendingIntent.value)return
 const command=merchantSelection(props.customerId,props.home.items,{[id]:quantity(id)})
 if(!command){emit('error',new Error(t('invalidSelection')));return}
 saving.value=true
 try{const result=await props.api.command<Intent>('/intents',command);if(active)emit('created',result)}catch(e){if(active)emit('error',e)}finally{if(active)saving.value=false}
}
useStorageCodeRefresh(()=>!!pendingIntent.value&&!props.disabled&&!saving.value,async()=>{
 const id=props.activeIntent?.id,customer=props.customerId
 try{const data=await props.api.get<Home>('/home');if(!active||props.activeIntent?.id!==id||props.customerId!==customer)return
  const latest=data.intents.find(i=>i.id===id&&i.customerId===customer)
  if(latest){observed.value=latest;if(!['awaiting_authorization','authorized'].includes(latest.status))emit('refresh')}
 }catch(e){if(active&&e instanceof StorageApiError&&[401,403].includes(e.status))emit('error',e)}
})
</script>
<template><section class="storage-panel">
 <h2>{{t('selectBottles')}}</h2><p v-if="!items.length" class="storage-empty">{{t('noBottles')}}</p>
 <div class="storage-grid"><article v-for="item in items" :key="item.id" class="storage-panel storage-merchant-bottle">
  <div class="storage-bottle-heading"><h3>{{displayName(item.snapshot,locale)}}</h3><aside class="storage-bottle-code">
   <template v-if="codeFor(item.id)"><WalletQrDisplay v-if="codeFor(item.id)!.status==='awaiting_authorization'&&codeFor(item.id)!.code" :value="codeFor(item.id)!.code!" :expires-at="codeFor(item.id)!.expiresAt"/><strong>{{t(codeFor(item.id)!.status==='authorized'?'customerConfirmed':'quickConfirmHint')}}</strong><small>{{t('quickQuantity')}} {{codeFor(item.id)!.selections[0]!.quantity}} {{t('unit')}}</small></template>
   <template v-else><StorageQuantityStepper :model-value="quantity(item.id)" :max="item.opened?1:item.remaining" :disabled="disabled||saving||!!pendingIntent||!canDeliver" :label="t('collectQuantity')" @update:model-value="quantities[item.id]=$event"/><button v-if="directMode" :disabled="disabled||saving||!canDeliver||!selection(item.id)" @click="prepareDirect(item.id)">{{t('directDeliver')}}</button><template v-else><button :disabled="disabled||saving||!!pendingIntent||!canDeliver||!selection(item.id)" @click="create(item.id)">{{t('authorize')}}</button><small>{{t('quickConfirmHint')}}</small></template></template>
  </aside></div><p>{{t('remaining')}} {{item.remaining}} {{t('unit')}}</p><small>{{t('expiresAt')}} {{dateTime(item.expiresAt,item.snapshot.timezone)}}</small><p>{{t('location')}} {{storageLocationText(item)}} · {{t('label')}} {{item.label}}</p>
 </article></div>
 <section v-if="directMode&&confirmation" class="storage-panel"><h3>{{t('directDeliver')}}</h3><p>{{confirmation.name}} × {{confirmation.quantity}} {{t('unit')}}</p><p>{{t('directDeliveryHint')}}</p><button :disabled="disabled||saving" @click="confirmation=null">{{t('cancelDirect')}}</button><button class="primary" :disabled="disabled||saving||!canDeliver" @click="deliverDirect">{{t('confirmDirectDeliver')}}</button></section>
 <template v-if="intent"><p class="storage-notice">{{t(intent.status==='authorized'?'deliveryHint':'authorizationHint')}}</p><StorageIntentPanel :intent="{...intent,code:intent.selections.length===1&&items.some(item=>item.id===intent!.selections[0]!.itemId)?null:intent.code}" :items="home.items" :customer="false" :timezone="home.timezone" :disabled="disabled||saving||Date.parse(intent.expiresAt)<=now" :can-deliver="canDeliver" @deliver="emit('command',async()=>{await api.command(`/intents/${intent!.id}/deliver`,{});emit('cleared')},t('delivered'))" @cancel="emit('command',async()=>{await api.command(`/intents/${intent!.id}/cancel`,{});emit('cleared')})"/><button :disabled="disabled||saving" @click="observed=null;emit('refresh')">{{t('refreshAuthorization')}}</button></template>
</section></template>
