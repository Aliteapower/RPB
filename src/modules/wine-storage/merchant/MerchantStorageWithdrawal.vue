<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import type {Home,Intent} from '../types'
import type {StorageApi} from '../api'
import StorageIntentPanel from '../StorageIntentPanel.vue'
import {merchantSelection} from './scope'
import {displayName} from '../../shared-commerce/format'
import {dateTime} from '../format'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;customerId:string;api:StorageApi;disabled:boolean;canDeliver:boolean;activeIntent:Intent|null}>(),emit=defineEmits<{created:[Intent];command:[()=>Promise<unknown>,string?];error:[unknown];refresh:[];'busy-change':[boolean];cleared:[]}>(),{t,locale}=useMerchantStorageText()
const selected=ref<Record<string,number>>({}),review=ref(false),saving=ref(false),now=ref(Date.now()),timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>{clearInterval(timer);emit('busy-change',false)});watch(saving,value=>emit('busy-change',value),{flush:'sync'})
const items=computed(()=>props.home.items.filter(i=>i.customerId===props.customerId&&i.status==='stored'&&i.remaining>0&&Date.parse(i.expiresAt)>now.value))
const payload=computed(()=>merchantSelection(props.customerId,props.home.items,selected.value,now.value))
watch(items,rows=>{const ids=new Set(rows.map(i=>i.id));for(const id of Object.keys(selected.value))if(!ids.has(id)){delete selected.value[id];review.value=false}});watch(payload,value=>{if(!value)review.value=false})
const intent=computed(()=>{if(!props.activeIntent||props.activeIntent.customerId!==props.customerId)return null;const current=props.home.intents.find(i=>i.id===props.activeIntent!.id)??props.activeIntent;return{...current,code:['awaiting_authorization','authorized'].includes(current.status)&&Date.parse(current.expiresAt)>now.value?props.activeIntent.code:null}})
async function create(){if(props.disabled||saving.value||!props.canDeliver)return;const command=merchantSelection(props.customerId,props.home.items,selected.value);if(!command){review.value=false;emit('error',new Error(t('invalidSelection')));return}saving.value=true;try{const result=await props.api.command<Intent>('/intents',command);selected.value={};review.value=false;emit('created',result)}catch(e){emit('error',e)}finally{saving.value=false}}
</script>
<template><section class="storage-panel">
 <template v-if="intent"><h2>{{t('reviewTitle')}}</h2><p class="storage-notice">{{t(intent.status==='authorized'?'deliveryHint':'authorizationHint')}}</p><StorageIntentPanel :intent="intent" :items="home.items" :customer="false" :timezone="home.timezone" :disabled="disabled||saving||Date.parse(intent.expiresAt)<=now" :can-deliver="canDeliver" @deliver="emit('command',async()=>{await api.command(`/intents/${intent!.id}/deliver`,{});emit('cleared')},t('delivered'))" @cancel="emit('command',async()=>{await api.command(`/intents/${intent!.id}/cancel`,{});emit('cleared')})"/><button :disabled="disabled||saving" @click="emit('refresh')">{{t('refreshAuthorization')}}</button></template>
 <template v-else><h2>{{t(review?'reviewTitle':'selectBottles')}}</h2><p v-if="!items.length" class="storage-empty">{{t('noBottles')}}</p>
  <div v-if="!review" class="storage-grid"><article v-for="item in items" :key="item.id" class="storage-panel"><h3>{{displayName(item.snapshot,locale)}}</h3><p>{{t('remaining')}} {{item.remaining}} {{t('unit')}}</p><small>{{t('expiresAt')}} {{dateTime(item.expiresAt,item.snapshot.timezone)}}</small><p>{{t('location')}} {{item.location}} · {{t('label')}} {{item.label}}</p><label class="storage-choice"><input type="checkbox" :disabled="disabled||!canDeliver" :checked="item.id in selected" @change="($event.target as HTMLInputElement).checked?selected[item.id]=1:delete selected[item.id]">{{t('select')}}</label><label v-if="item.id in selected">{{t('quantity')}}<input v-model.number="selected[item.id]" type="number" min="1" :max="item.opened?1:item.remaining" :disabled="disabled||item.opened"></label></article></div>
  <ul v-else class="storage-customer-review"><li v-for="line in payload?.selections" :key="line.itemId"><strong>{{displayName(home.items.find(i=>i.id===line.itemId)!.snapshot,locale)}}</strong> × {{line.quantity}} {{t('unit')}}</li></ul>
  <div v-if="items.length" class="storage-inline"><button v-if="review" :disabled="disabled||saving" @click="review=false">{{t('editSelection')}}</button><button class="primary" :disabled="disabled||saving||!canDeliver||!payload" @click="review?create():review=true">{{t(review?'authorize':'review')}}</button></div>
 </template>
</section></template>
