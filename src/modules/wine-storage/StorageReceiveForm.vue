<script setup lang="ts">
import {computed,ref,onMounted,watch,onUnmounted} from 'vue'
import {useMerchantStorageText} from './merchant/text'
import {StorageApiError,type StorageApi} from './api'
import type {Application,Home,ReceiptLine,Product,StorageLocation} from './types'
import StoragePhotos from './StoragePhotos.vue'
import StorageReceiptWinePicker from './merchant/StorageReceiptWinePicker.vue'
import StorageLocationSelect from './merchant/StorageLocationSelect.vue'
import StorageCustodianSelect from './merchant/StorageCustodianSelect.vue'
const props=defineProps<{api:StorageApi;application:Application;home:Home;disabled:boolean;direct?:boolean}>(),emit=defineEmits<{saved:[];error:[string];'busy-change':[boolean]}>(),{t}=useMerchantStorageText()
const lines=ref<ReceiptLine[]>(props.application.lines.map(l=>({lineId:l.id,productId:l.productId,categoryId:'',nameZh:l.nameZh,nameEn:l.nameEn,quantity:l.quantity,opened:l.opened,source:l.source,ruleId:'',photos:[],note:l.note,location:'',label:''}))),reason=ref(props.direct?t('registrationReason'):''),rulesConfirmed=ref(false),custodianId=ref(''),uploading=ref(0),saving=ref(false)
const matchesOriginal=computed(()=>lines.value.length===props.application.lines.length&&lines.value.every(line=>props.application.lines.some(original=>original.id===line.lineId&&original.productId===line.productId&&original.quantity===line.quantity&&original.opened===line.opened&&original.source===line.source)))
const selectedProducts=ref<Product[]>([]),searching=ref(false),searchFailed=ref(false),wineBusy=ref<Record<string,boolean>>({}),wineEditing=ref<Record<string,boolean>>({})
const lineKeys=new WeakMap<ReceiptLine,string>()
function lineKey(line:ReceiptLine){let key=lineKeys.get(line);if(!key){key=crypto.randomUUID();lineKeys.set(line,key)}return key}
const wineSearching=computed(()=>Object.values(wineBusy.value).some(Boolean))
let generation=0,locationGeneration=0
const locations=ref<StorageLocation[]>([]),locationsLoading=ref(true),locationsFailed=ref(false)
async function loadLocations(){const g=++locationGeneration;locationsLoading.value=true;locationsFailed.value=false;try{const values=await props.api.get<StorageLocation[]>('/locations');if(g!==locationGeneration)return;locations.value=values;for(const line of lines.value){if(line.storageLocationId===undefined)line.storageLocationId=values.find(p=>p.defaultLocation)?.id??null}}catch(e){if(g===locationGeneration){locationsFailed.value=true;emit('error',String(e))}}finally{if(g===locationGeneration)locationsLoading.value=false}}
const available=computed(()=>selectedProducts.value)
async function loadProducts(){const own=++generation;searching.value=true;searchFailed.value=false;try{
 const ids=[...new Set(lines.value.map(l=>l.productId).filter((id):id is string=>!!id))]
 const found=await Promise.all(ids.map(async id=>{try{return await props.api.get<Product>('/products/'+encodeURIComponent(id))}catch(e){if(e instanceof StorageApiError&&[404,409].includes(e.status))return null;throw e}}))
 if(own!==generation)return;selectedProducts.value=found.filter((p):p is Product=>!!p)
 for(const line of lines.value){const product=available.value.find(p=>p.id===line.productId);if(product)choose(line,product);else if(line.productId){line.ruleId='';line.categoryId=''}}
 }catch(e){if(own===generation){searchFailed.value=true;selectedProducts.value=[];emit('error',String(e))}}finally{if(own===generation)searching.value=false}}
onMounted(()=>{void loadProducts();void loadLocations()})
function choose(line:ReceiptLine,product:Product){if(line.productId!==product.id)line.photos=[];selectedProducts.value=[...selectedProducts.value.filter(p=>p.id!==product.id),product];Object.assign(line,{productId:product.id,categoryId:product.categoryId,nameZh:product.nameZh,nameEn:product.nameEn,ruleId:props.home.rules.find(r=>r.categoryId===product.categoryId)?.id??''});rulesConfirmed.value=false}
function clearWine(line:ReceiptLine){Object.assign(line,{productId:null,categoryId:'',nameZh:'',nameEn:'',ruleId:'',quantity:1,opened:false,source:'customer',photos:[],note:'',location:'',label:'',storageLocationId:locations.value.find(p=>p.defaultLocation)?.id??null});rulesConfirmed.value=false}
function patchWine(line:ReceiptLine,patch:Partial<ReceiptLine>){Object.assign(line,patch);if('categoryId'in patch)line.ruleId=props.home.rules.find(r=>r.categoryId===line.categoryId)?.id??'';rulesConfirmed.value=false}
watch([saving,uploading,searching,locationsLoading,wineSearching],()=>emit('busy-change',saving.value||uploading.value>0||searching.value||locationsLoading.value||wineSearching.value),{flush:'sync'})
onUnmounted(()=>{generation++;locationGeneration++;emit('busy-change',false)})
function addLine(line:ReceiptLine){const added={...line,lineId:props.direct?crypto.randomUUID():line.lineId,quantity:1,opened:props.direct?false:true,photos:[],label:''};if(props.direct)clearWine(added);lines.value.push(added)}
function validLine(line:ReceiptLine){return !searching.value&&!wineSearching.value&&!wineEditing.value[lineKey(line)]&&(!line.productId||available.value.some(p=>p.id===line.productId))&&!!line.ruleId&&Number.isInteger(line.quantity)&&line.quantity>=1&&line.quantity<=(line.opened?1:10000)&&!!(line.productId||(line.categoryId&&(line.nameZh.trim()||line.nameEn.trim())))}
const chosenLines=computed(()=>lines.value.filter(line=>line.productId||((line.nameZh.trim()||line.nameEn.trim())&&line.categoryId)))
const remainingSteps=computed(()=>{
 const missing:string[]=[]
 if(searching.value||wineSearching.value)missing.push(t('loadingWine'))
 if(locationsLoading.value)missing.push(t('loadingLocations'))
 if(locationsFailed.value)missing.push(t('retryLocations'))
 for(const [i,line] of lines.value.entries()){
  const prefix=t('wineItem')+' '+(i+1)+'：'
  if(wineEditing.value[lineKey(line)]||!line.productId&&!((line.nameZh.trim()||line.nameEn.trim())&&line.categoryId)||line.productId&&!available.value.some(p=>p.id===line.productId))missing.push(prefix+t('completeWine'))
  if(!line.ruleId)missing.push(prefix+t('chooseRule'))
  if(!Number.isInteger(line.quantity)||line.quantity<1||line.quantity>(line.opened?1:10000))missing.push(prefix+t('validQuantity'))
 }
 if(!reason.value.trim())missing.push(t('completeReason'))
 if(props.direct&&!rulesConfirmed.value)missing.push(t('confirmReceiptConsent'))
 return missing
})
async function submit(){if(props.disabled||saving.value||uploading.value||locationsLoading.value||locationsFailed.value||!reason.value.trim()||(props.direct&&!rulesConfirmed.value)||!lines.value.length||lines.value.some(l=>!validLine(l)))return;saving.value=true;try{await props.api.command(props.direct?'/registrations':`/applications/${props.application.id}/receive`,{lines:lines.value.map(line=>({...line,storageLocationId:line.storageLocationId||null})),reason:reason.value,custodianId:custodianId.value||null,...(props.direct?{customerId:props.application.customerId,rulesConfirmed:rulesConfirmed.value}:{})});emit('saved')}catch(e){emit('error',String(e))}finally{saving.value=false}}
</script>
<template>
<form class="storage-panel storage-receive-form" @submit.prevent="submit"><fieldset :disabled="disabled||saving||uploading>0||searching||locationsLoading">
 <header class="storage-receive-header"><h2>{{t(direct?'directRegister':'receive')}}</h2><p>{{t('receiveSimpleHint')}}</p></header>
 <article v-for="(l,index) in lines" :key="lineKey(l)" class="storage-receive-line">
  <div class="storage-inline"><h3>{{t('wineItem')}} {{index+1}}<template v-if="l.nameZh||l.nameEn"> · {{l.nameZh||l.nameEn}}</template></h3><button v-if="direct?lines.length>1:lines.filter(x=>x.lineId===l.lineId).length>1" type="button" @click="lines.splice(index,1)">{{t('remove')}}</button></div>
  <h3><span class="storage-step">1</span>{{t('chooseWineStep')}}</h3>
  <StorageReceiptWinePicker :api="api" :line="l" :categories="home.categories" :disabled="disabled||saving||uploading>0" :code="available.find(p=>p.id===l.productId)?.code" @choose="choose(l,$event)" @clear="clearWine(l)" @manual="clearWine(l)" @patch="patchWine(l,$event)" @error="emit('error',String($event))" @busy="wineBusy[lineKey(l)]=$event" @editing="wineEditing[lineKey(l)]=$event"/>
  <p v-if="l.productId&&!searching&&!searchFailed&&!available.some(p=>p.id===l.productId)" class="storage-notice" role="status">{{t('productUnavailable')}}</p>
  <template v-if="l.productId||((l.nameZh.trim()||l.nameEn.trim())&&l.categoryId)">
  <h3><span class="storage-step">2</span>{{t('storageDetailsStep')}}</h3>
  <div class="storage-receive-grid"><label>{{t('actualQuantity')}}<small v-if="!direct">{{t('originalQuantity')}} {{application.lines.find(original=>original.id===l.lineId)?.quantity}}</small><input v-model.number="l.quantity" type="number" required min="1" :max="l.opened?1:10000"></label><label>{{t('bottleState')}}<select v-model="l.opened" @change="l.opened&&(l.quantity=1)"><option :value="false">{{t('whole')}}</option><option :value="true">{{t('opened')}}</option></select></label><label>{{t('source')}}<select v-model="l.source"><option value="store">{{t('store')}}</option><option value="customer">{{t('customer')}}</option></select></label></div>
  <label>{{t('rules')}}<select v-model="l.ruleId" required><option value="">{{t('chooseRule')}}</option><option v-for="r in home.rules.filter(r=>!l.categoryId||r.categoryId===l.categoryId)" :key="r.id" :value="r.id">{{r.name}} · {{r.months===0?t('unlimited'):`${r.months} ${t('months')}`}}</option></select></label><p v-if="!home.rules.some(r=>!l.categoryId||r.categoryId===l.categoryId)" class="storage-notice">{{t('missingRuleHint')}}</p>
  <StoragePhotos optional v-model="l.photos" :api="api" :customer-id="application.customerId" :disabled="disabled||saving" @busy="uploading=Math.max(0,uploading+($event?1:-1))" @error="emit('error',$event)"/>
  <details class="storage-receive-extras"><summary>{{t('extraStorageDetails')}}</summary><p class="cards-muted">{{t('optionalDetailsHint')}}</p><StorageLocationSelect :model-value="l.storageLocationId" :locations="locations" :disabled="disabled||saving||locationsLoading" @update:model-value="l.storageLocationId=$event"/><button type="button" :disabled="disabled||saving||locationsLoading" @click="loadLocations">{{t('refreshLocations')}}</button><label>{{t('slotOptional')}}<input v-model="l.location" maxlength="200" :placeholder="t('slotHint')"></label><label>{{t('labelOptional')}}<input v-model="l.label" maxlength="200"></label><label>{{t('noteOptional')}}<textarea v-model="l.note" maxlength="2000"/></label></details>
  </template>
 </article>
 <button class="storage-add-wine" type="button" :disabled="disabled||saving||lines.length>=(direct?50:100)" @click="lines[0]&&addLine(lines[lines.length-1]!)">＋ {{t(direct?'addBottle':'add')}}</button>
 <section class="storage-receive-confirm"><h3><span class="storage-step">3</span>{{t('confirmStorageStep')}}</h3><p class="storage-receive-total">{{t('thisReceipt')}} {{chosenLines.length}} {{t('wineKinds')}} · {{chosenLines.reduce((sum,l)=>sum+(Number.isInteger(l.quantity)&&l.quantity>0?l.quantity:0),0)}} {{t('unit')}}</p><StorageCustodianSelect v-model="custodianId" :api="api" :disabled="disabled||saving||uploading>0" @error="emit('error',$event)"/>
 <button v-if="!direct" type="button" :disabled="disabled||saving||uploading>0||!matchesOriginal" @click="reason=t('matchesApplication')">{{t('matchesApplication')}}</button>
 <label>{{t('reason')}}<textarea v-model="reason" required maxlength="2000"/></label>
 <label v-if="direct" class="storage-receive-consent"><input v-model="rulesConfirmed" type="checkbox" required>{{t('registrationConsent')}}</label>
 <p class="cards-muted">{{t('expiryHint')}}</p>
 <p v-if="remainingSteps.length" class="storage-form-guidance" role="status">{{t('stillNeeded')}}{{remainingSteps.join('；')}}</p><p v-else-if="uploading" class="storage-form-guidance" role="status">{{t('uploadingPhotos')}}</p>
 <button class="primary storage-receive-submit" type="submit" :disabled="disabled||saving||uploading>0||locationsLoading||locationsFailed||!lines.length||remainingSteps.length>0">{{t(saving?'saving':direct?'registerSubmit':'receive')}}</button></section>
</fieldset></form></template>
