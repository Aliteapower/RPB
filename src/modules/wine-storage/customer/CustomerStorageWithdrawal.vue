<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import type {Item,Intent} from '../types'
import type {StorageApi} from '../api'
import {displayName} from '../../shared-commerce/format'
import {dateTime} from '../format'
import {useCustomerStorageText} from './text'
import {collectibleItems,withdrawalSelection} from './selection'
const props=defineProps<{api:StorageApi;items:Item[];disabled:boolean}>(),emit=defineEmits<{created:[Intent];error:[unknown];deposit:[];'busy-change':[boolean];scan:[]}>(),{t,locale}=useCustomerStorageText()
const selected=ref<Record<string,number>>({}),review=ref(false),saving=ref(false),now=ref(Date.now())
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{clearInterval(timer);emit('busy-change',false)})
watch(saving,value=>emit('busy-change',value),{flush:'sync'})
const available=computed(()=>collectibleItems(props.items,now.value)),selection=computed(()=>withdrawalSelection(props.items,selected.value,now.value))
watch(available,items=>{const validIds=new Set(items.map(item=>item.id));for(const id of Object.keys(selected.value))if(!validIds.has(id)){delete selected.value[id];review.value=false}})
watch(selection,value=>{if(!value)review.value=false})
function choose(id:string,checked:boolean){if(checked)selected.value[id]=1;else delete selected.value[id]}
function preview(){now.value=Date.now();if(!props.disabled&&selection.value)review.value=true}
async function create(){if(props.disabled||saving.value)return;const lines=withdrawalSelection(props.items,selected.value);if(!lines){review.value=false;emit('error',new Error(t('selectionChanged')));return}saving.value=true;try{const result=await props.api.command<Intent>('/intents',{selections:lines});selected.value={};review.value=false;emit('created',result)}catch(e){emit('error',e)}finally{saving.value=false}}
</script>
<template>
 <section class="storage-panel storage-section">
  <template v-if="!available.length"><h2>{{t('emptyCollectible')}}</h2><p class="cards-muted">{{t('emptyCollectibleHint')}}</p><button @click="emit('deposit')">{{t('deposit')}}</button></template>
  <template v-else-if="!review"><h2>{{t('chooseBottles')}}</h2>
   <div class="storage-grid"><article v-for="item in available" :key="item.id" class="storage-panel storage-customer-bottle">
    <h3>{{displayName(item.snapshot,locale)}}</h3><p>{{t('remaining')}} {{item.remaining}} {{t('bottleUnit')}}</p><small>{{t('expiresAt')}} {{dateTime(item.expiresAt,item.snapshot.timezone)}}</small>
    <label class="storage-choice"><input type="checkbox" :checked="item.id in selected" :disabled="disabled||saving" @change="choose(item.id,($event.target as HTMLInputElement).checked)">{{t('select')}}</label>
    <label v-if="item.id in selected">{{t('collectQuantity')}}<input v-model.number="selected[item.id]" type="number" :disabled="disabled||saving||item.opened" min="1" :max="item.opened?1:item.remaining"></label>
   </article></div>
   <p v-if="!selection" class="cards-muted">{{t('selectOwn')}}</p><button class="primary storage-customer-primary" :disabled="disabled||saving||!selection" @click="preview">{{t('reviewSelection')}}</button>
  </template>
  <template v-else><h2>{{t('reviewTitle')}}</h2><ul class="storage-customer-review"><li v-for="line in selection" :key="line.itemId"><strong>{{displayName(items.find(i=>i.id===line.itemId)!.snapshot,locale)}}</strong><span>× {{line.quantity}} {{t('bottleUnit')}}</span></li></ul>
   <p class="storage-notice">{{t('handoverHint')}}</p><div class="storage-inline"><button :disabled="saving||disabled" @click="review=false">{{t('editSelection')}}</button><button class="primary" :disabled="saving||disabled||!selection" @click="create">{{t('generateCode')}}</button></div>
  </template>
 </section>
 <details class="storage-panel storage-customer-scan"><summary>{{t('scanMerchant')}}</summary><p>{{t('merchantHandoverHint')}}</p><button :disabled="disabled||saving" @click="emit('scan')">{{t('scan')}}</button></details>
</template>
