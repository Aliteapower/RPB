<script setup lang="ts">
import {computed,ref,onUnmounted} from 'vue'
import type {Home,Intent} from '../types'
import type {StorageApi} from '../api'
import StorageIntentPanel from '../StorageIntentPanel.vue'
import StorageQuantityStepper from '../StorageQuantityStepper.vue'
import StorageItemPhotos from '../StorageItemPhotos.vue'
import {merchantSelection} from './scope'
import {displayName} from '../../shared-commerce/format'
import {storageAvailable,storageExpiryText,storageLocationText} from '../format'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;customerId:string;api:StorageApi;disabled:boolean;canDeliver:boolean;activeIntent:Intent|null}>(),emit=defineEmits<{command:[()=>Promise<unknown>,string?];error:[unknown];refresh:[];'busy-change':[boolean];cleared:[]}>(),{t,locale}=useMerchantStorageText()
const quantities=ref<Record<string,number>>({}),now=ref(Date.now()),confirmation=ref<{itemId:string;quantity:number;name:string}|null>(null)
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{clearInterval(timer);emit('busy-change',false)})
const items=computed(()=>props.home.items.filter(i=>i.customerId===props.customerId&&i.status==='stored'&&i.remaining>0&&storageAvailable(i.expiresAt,now.value)))
const intent=computed(()=>{
 if(!props.activeIntent||props.activeIntent.customerId!==props.customerId)return null
 const latest=props.home.intents.find(i=>i.id===props.activeIntent!.id)??props.activeIntent
 return {...latest,code:null}
})
const pendingIntent=computed(()=>intent.value&&['awaiting_authorization','authorized'].includes(intent.value.status)&&Date.parse(intent.value.expiresAt)>now.value?intent.value:null)
function quantity(id:string){return quantities.value[id]??1}
function selection(id:string){return merchantSelection(props.customerId,props.home.items,{[id]:quantity(id)},now.value)}
function prepareDirect(id:string){
 if(props.disabled||!props.canDeliver||pendingIntent.value)return
 const selected=selection(id)?.selections[0],item=props.home.items.find(i=>i.id===id)
 if(selected&&item)confirmation.value={...selected,name:displayName(item.snapshot,locale.value)}
}
function deliverDirect(){
 const selected=confirmation.value
 if(!selected||props.disabled||!props.canDeliver||pendingIntent.value)return
 const command={customerId:props.customerId,selections:[{itemId:selected.itemId,quantity:selected.quantity}]}
 emit('command',async()=>{await props.api.command('/direct-deliver',command);confirmation.value=null;emit('cleared')},t('delivered'))
}
</script>
<template><section class="storage-panel">
 <h2>{{t('selectBottles')}}</h2><p v-if="!items.length" class="storage-empty">{{t('noBottles')}}</p>
 <div class="storage-grid"><article v-for="item in items" :key="item.id" class="storage-panel storage-merchant-bottle">
  <div class="storage-bottle-heading"><h3>{{displayName(item.snapshot,locale)}}</h3><aside class="storage-bottle-code"><StorageQuantityStepper :model-value="quantity(item.id)" :max="item.opened?1:item.remaining" :disabled="disabled||!!pendingIntent||!canDeliver" :label="t('collectQuantity')" @update:model-value="quantities[item.id]=$event"/><button :disabled="disabled||!!pendingIntent||!canDeliver||!selection(item.id)" @click="prepareDirect(item.id)">{{t('directDeliver')}}</button></aside></div>
  <p>{{t('remaining')}} {{item.remaining}} {{t('unit')}}</p><small>{{t('expiresAt')}} {{storageExpiryText(item.expiresAt,item.snapshot.timezone,t('unlimited'))}}</small><p>{{t('location')}} {{storageLocationText(item)}} · {{t('label')}} {{item.label}}</p><StorageItemPhotos :item="item" :api="api" :events="home.events"/>
 </article></div>
 <section v-if="confirmation" class="storage-panel"><h3>{{t('directDeliver')}}</h3><p>{{confirmation.name}} × {{confirmation.quantity}} {{t('unit')}}</p><p>{{t('directDeliveryHint')}}</p><button :disabled="disabled" @click="confirmation=null">{{t('cancelDirect')}}</button><button class="primary" :disabled="disabled||!canDeliver||!!pendingIntent" @click="deliverDirect">{{t('confirmDirectDeliver')}}</button></section>
 <template v-if="intent"><p v-if="pendingIntent" class="storage-notice">{{t('deliveryHint')}}</p><StorageIntentPanel :intent="intent" :items="home.items" :customer="false" :timezone="home.timezone" :disabled="disabled||Date.parse(intent.expiresAt)<=now" :can-deliver="canDeliver" @deliver="emit('command',async()=>{await api.command(`/intents/${intent!.id}/deliver`,{});emit('cleared')},t('delivered'))" @cancel="emit('command',async()=>{await api.command(`/intents/${intent!.id}/cancel`,{});emit('cleared')})"/></template>
</section></template>
