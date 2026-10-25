<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import {useCustomerStorageText} from './customer/text'
import {displayName} from '../shared-commerce/format'
import type {StorageApi} from './api'
import type {Product,Item,Application} from './types'
import StoragePhotos from './StoragePhotos.vue'
import StorageProductPicker from './StorageProductPicker.vue'
const props=defineProps<{api:StorageApi;disabled:boolean;again?:{item:Item;intentId:string;quantity:number}|null}>(),emit=defineEmits<{saved:[Application];error:[unknown];'busy-change':[boolean]}>(),{t,locale}=useCustomerStorageText()
let nextLine=0
const blank=()=>({localKey:++nextLine,productId:null as string|null,nameZh:'',nameEn:'',quantity:1,opened:false,source:'customer',photos:[] as string[],note:''})
const lines=ref<ReturnType<typeof blank>[]>([]),accepted=ref(false),uploading=ref(0),saving=ref(false),manual=ref(false),manualName=ref(''),reportedBarcode=ref('')
watch(()=>props.again,value=>{if(value)lines.value=[{...blank(),productId:value.item.snapshot.productId,nameZh:value.item.snapshot.nameZh,nameEn:value.item.snapshot.nameEn,source:value.item.source,quantity:value.quantity}]},{immediate:true})
watch([saving,uploading],()=>emit('busy-change',saving.value||uploading.value>0),{flush:'sync'})
onUnmounted(()=>{emit('busy-change',false)})
function choose(product:Product){if(props.disabled||saving.value||lines.value.length>=50)return;lines.value.push({...blank(),productId:product.id,nameZh:product.nameZh,nameEn:product.nameEn})}
function addManual(){if(props.disabled||saving.value||!manualName.value.trim()||lines.value.length>=50)return;lines.value.push({...blank(),nameZh:locale.value.startsWith('en')?'':manualName.value.trim(),nameEn:locale.value.startsWith('en')?manualName.value.trim():'',note:reportedBarcode.value?'商品条码 / Product barcode: '+reportedBarcode.value:''});manualName.value='';reportedBarcode.value='';manual.value=false}
const valid=computed(()=>lines.value.length>0&&lines.value.length<=50&&accepted.value&&lines.value.every(l=>Number.isInteger(l.quantity)&&l.quantity>=1&&l.quantity<=Math.min(l.opened?1:10000,props.again?.quantity??10000)&&l.photos.length>0&&(l.productId||l.nameZh.trim()||l.nameEn.trim())))
async function save(){if(props.disabled||saving.value||uploading.value||!valid.value)return;saving.value=true;const api=props.api;try{const result=await api.command<Application>('/applications',{lines:lines.value.map(({localKey,...line})=>({...line,photos:line.photos.slice()})),rulesAccepted:true,withdrawalId:props.again?.intentId??null,withdrawalItemId:props.again?.item.id??null});emit('saved',result)}catch(e){emit('error',e)}finally{saving.value=false}}
</script>
<template><form class="storage-customer-deposit" @submit.prevent="save"><fieldset :disabled="disabled||saving||uploading>0">
 <StorageProductPicker v-if="!again" :api="api" :disabled="disabled||saving||lines.length>=50" @choose="choose" @manual="reportedBarcode=$event;manualName='';manual=true" @error="emit('error',$event)"><div v-if="manual" class="storage-panel"><p v-if="reportedBarcode">{{t('barcodeLabel')}}: {{reportedBarcode}}</p><label>{{t('wineName')}}<input v-model="manualName" maxlength="200"></label><button type="button" :disabled="disabled||!manualName.trim()" @click="addManual">{{t('addManual')}}</button><button type="button" @click="manual=false;reportedBarcode=''">{{t('cancel')}}</button></div></StorageProductPicker>
 <section v-if="lines.length" class="storage-panel"><h2>{{t('bottleDetails')}}</h2><p v-if="again" class="storage-notice">{{t('againHint')}}</p><p class="cards-muted">{{t('photoHint')}}</p>
  <article v-for="(line,index) in lines" :key="line.localKey" class="storage-panel"><div class="storage-inline"><h3>{{index+1}}. {{displayName(line,locale)}}</h3><button v-if="!again" type="button" :disabled="disabled||saving||uploading>0" @click="lines.splice(index,1)">{{t('remove')}}</button></div>
   <div class="storage-customer-fields"><label>{{t('quantity')}}<input v-model.number="line.quantity" type="number" min="1" :max="again?again.quantity:line.opened?1:10000" required></label><label>{{t('bottleState')}}<select v-model="line.opened" @change="line.opened&&(line.quantity=1)"><option :value="false">{{t('whole')}}</option><option :value="true">{{t('opened')}}</option></select></label><label>{{t('source')}}<select v-model="line.source"><option value="store">{{t('store')}}</option><option value="customer">{{t('customer')}}</option></select></label></div>
   <StoragePhotos v-model="line.photos" :api="api" :disabled="disabled||saving" @busy="uploading=Math.max(0,uploading+($event?1:-1))" @error="emit('error',$event)"/><label>{{t('note')}}<textarea v-model="line.note" maxlength="2000"/></label>
  </article>
  <label class="storage-choice"><input v-model="accepted" type="checkbox" required>{{t('accept')}}</label><button class="primary storage-customer-primary" type="submit" :disabled="disabled||saving||uploading>0||!valid">{{t('submitApplication')}}</button>
 </section>
</fieldset></form></template>
