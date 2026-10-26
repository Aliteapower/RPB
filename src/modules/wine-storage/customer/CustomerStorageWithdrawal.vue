<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import type {Item,Intent} from '../types'
import type {StorageApi} from '../api'
import {displayName} from '../../shared-commerce/format'
import {dateTime} from '../format'
import {useCustomerStorageText} from './text'
import {collectibleItems,withdrawalSelection} from './selection'
import StorageQuantityStepper from '../StorageQuantityStepper.vue'
import WalletQrDisplay from '../../wallet/components/WalletQrDisplay.vue'
const props=defineProps<{api:StorageApi;items:Item[];disabled:boolean;activeIntent?:Intent|null}>(),emit=defineEmits<{created:[Intent];'quick-created':[Intent];cancel:[];error:[unknown];deposit:[];'busy-change':[boolean];scan:[]}>(),{t,locale}=useCustomerStorageText()
const saving=ref(false),now=ref(Date.now())
const quickQuantities=ref<Record<string,number>>({})
function quickQuantity(id:string){return quickQuantities.value[id]??1}
function quickSelection(id:string){return withdrawalSelection(props.items,{[id]:quickQuantity(id)},now.value)}
let active=true
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{active=false;clearInterval(timer);emit('busy-change',false)})
watch(saving,value=>emit('busy-change',value),{flush:'sync'})
const available=computed(()=>collectibleItems(props.items,now.value))
const liveCode=computed(()=>props.activeIntent?.origin==='customer'&&props.activeIntent.status==='authorized'&&!!props.activeIntent.code&&Date.parse(props.activeIntent.expiresAt)>now.value?props.activeIntent:null)
function codeFor(id:string){return liveCode.value?.selections.length===1&&liveCode.value.selections[0]?.itemId===id?liveCode.value:null}
async function quickCreate(id:string){
 if(props.disabled||saving.value||liveCode.value)return
 const lines=withdrawalSelection(props.items,{[id]:quickQuantity(id)})
 if(!lines){emit('error',new Error(t('selectionChanged')));return}
 saving.value=true
 try{const result=await props.api.command<Intent>('/intents',{selections:lines});if(active)emit('quick-created',result)}catch(e){if(active)emit('error',e)}finally{if(active)saving.value=false}
}
</script>
<template>
 <section class="storage-panel storage-section">
  <template v-if="!available.length"><h2>{{t('emptyCollectible')}}</h2><p class="cards-muted">{{t('emptyCollectibleHint')}}</p><button @click="emit('deposit')">{{t('deposit')}}</button></template>
  <template v-else><h2>{{t('chooseBottles')}}</h2>
   <div class="storage-grid"><article v-for="item in available" :key="item.id" class="storage-panel storage-customer-bottle">
    <div class="storage-bottle-heading"><h3>{{displayName(item.snapshot,locale)}}</h3><aside class="storage-bottle-code">
     <template v-if="codeFor(item.id)"><WalletQrDisplay :value="codeFor(item.id)!.code!" :expires-at="codeFor(item.id)!.expiresAt"/><strong>{{t('quickScanHint')}}</strong><small>{{t('quickQuantity')}} {{codeFor(item.id)!.selections[0]!.quantity}} {{t('bottleUnit')}}</small><button :disabled="disabled||saving" @click="emit('cancel')">{{t('cancel')}}</button></template>
     <template v-else><StorageQuantityStepper :model-value="quickQuantity(item.id)" :max="item.opened?1:item.remaining" :disabled="disabled||saving||!!liveCode" :label="t('collectQuantity')" @update:model-value="quickQuantities[item.id]=$event"/><button :disabled="disabled||saving||!!liveCode||!quickSelection(item.id)" @click="quickCreate(item.id)">{{t('quickCode')}}</button><small>{{t('quickScanHint')}}</small></template>
    </aside></div><p>{{t('remaining')}} {{item.remaining}} {{t('bottleUnit')}}</p><small>{{t('expiresAt')}} {{dateTime(item.expiresAt,item.snapshot.timezone)}}</small>
   </article></div>
  </template>
 </section>
 <details class="storage-panel storage-customer-scan"><summary>{{t('scanMerchant')}}</summary><p>{{t('merchantHandoverHint')}}</p><button :disabled="disabled||saving" @click="emit('scan')">{{t('scan')}}</button></details>
</template>
