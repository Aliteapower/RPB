<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import type {StorageApi} from '../api'
import type {Product,ReceiptLine,Category} from '../types'
import {useMerchantStorageText} from './text'
const props=defineProps<{api:StorageApi;line:ReceiptLine;categories:Category[];disabled:boolean;code?:string}>()
const emit=defineEmits<{choose:[Product];clear:[];manual:[];patch:[Partial<ReceiptLine>];error:[unknown];busy:[boolean];editing:[boolean]}>(),{t,locale}=useMerchantStorageText()
const query=ref(''),results=ref<Product[]>([]),loading=ref(false),failed=ref(false),mode=ref<'existing'|'manual'>(!props.line.productId&&(props.line.nameZh||props.line.nameEn)?'manual':'existing'),editing=ref(!props.line.productId)
let generation=0
function resetSearch(){generation++;loading.value=false;emit('busy',false);query.value='';results.value=[];failed.value=false}
async function search(){const own=++generation;loading.value=true;emit('busy',true);failed.value=false;results.value=[];try{const found=await props.api.get<Product[]>('/products?query='+encodeURIComponent(query.value.trim()));if(own===generation)results.value=found}catch(e){if(own===generation){failed.value=true;emit('error',e)}}finally{if(own===generation){loading.value=false;emit('busy',false)}}}
function setMode(value:'existing'|'manual'){if(value===mode.value)return;const name=query.value.trim();resetSearch();mode.value=value;editing.value=true;emit('manual');if(value==='existing')void search();else if(name)emit('patch',locale.value.startsWith('en')?{nameEn:name}:{nameZh:name})}
function select(product:Product){resetSearch();emit('choose',product);editing.value=false}
function clear(){resetSearch();mode.value='existing';editing.value=true;emit('clear');void search()}
function reselect(){editing.value=true;mode.value='existing';void search()}
watch([editing,()=>props.line.productId],()=>emit('editing',!!props.line.productId&&editing.value),{immediate:true})
watch(()=>props.line.productId,id=>{if(id){editing.value=false;mode.value='existing'}})
watch(()=>props.api.base,()=>{resetSearch();if(editing.value&&mode.value==='existing')void search()},{immediate:true})
onUnmounted(()=>{generation++;emit('busy',false);emit('editing',false)})
</script>
<template><section class="storage-receipt-wine-picker">
 <div v-if="line.productId&&!editing" class="storage-selected-wine"><div><small>{{t('selectedWine')}}</small><strong>{{locale.startsWith('en')?line.nameEn||line.nameZh:line.nameZh||line.nameEn}}</strong><small v-if="code">{{code}}</small></div><div class="storage-inline"><button type="button" :disabled="disabled" @click="reselect">{{t('reselectWine')}}</button><button type="button" :disabled="disabled" @click="clear">{{t('clearWine')}}</button></div></div>
 <template v-else>
  <div class="storage-wine-modes" :aria-label="t('chooseWineStep')"><button type="button" :aria-pressed="mode==='existing'" :class="{primary:mode==='existing'}" :disabled="disabled" @click="setMode('existing')">{{t('existingWineTab')}}</button><button type="button" :aria-pressed="mode==='manual'" :class="{primary:mode==='manual'}" :disabled="disabled" @click="setMode('manual')">＋ {{t('newWineTab')}}</button><button type="button" :disabled="disabled" @click="clear">{{t('clearWine')}}</button><button v-if="line.productId" type="button" :disabled="disabled" @click="editing=false">{{t('keepWine')}}</button></div>
  <template v-if="mode==='existing'"><div class="storage-receive-search-row"><label>{{t('wineSearch')}}<input v-model="query" maxlength="120" :placeholder="t('wineSearchHint')" :disabled="disabled" @keydown.enter.prevent="search"></label><button type="button" :disabled="disabled||loading" @click="search">{{t(loading?'loading':'searchWine')}}</button><button v-if="query" type="button" :disabled="disabled" @click="resetSearch();search()">{{t('clearSearch')}}</button></div>
   <p v-if="loading" role="status">{{t('loadingWine')}}</p><p v-else-if="failed" role="alert">{{t('wineSearchFailed')}}</p><template v-else><p class="cards-muted">{{t(results.length?'pickWineResult':'noWineResults')}}</p><div class="storage-wine-result-list"><button v-for="product in results" :key="product.id" type="button" :disabled="disabled" @click="select(product)"><span><strong>{{locale.startsWith('en')?product.nameEn||product.nameZh:product.nameZh||product.nameEn}}</strong><small>{{product.code}}</small></span><span>{{t('selectWineResult')}}</span></button></div><button v-if="!results.length" type="button" :disabled="disabled" @click="setMode('manual')">＋ {{t('newWineTab')}}</button></template>
  </template>
  <template v-else><p class="cards-muted">{{t('manualWineNames')}}</p><div class="storage-receive-grid"><label>{{t('nameZh')}}<input :value="line.nameZh" maxlength="200" :disabled="disabled" @input="emit('patch',{nameZh:($event.target as HTMLInputElement).value})"></label><label>{{t('nameEn')}}<input :value="line.nameEn" maxlength="200" :disabled="disabled" @input="emit('patch',{nameEn:($event.target as HTMLInputElement).value})"></label></div><label>{{t('category')}}<select :value="line.categoryId" required :disabled="disabled" @change="emit('patch',{categoryId:($event.target as HTMLSelectElement).value})"><option value="">{{t('chooseCategory')}}</option><option v-for="category in categories" :key="category.id" :value="category.id">{{category.nameZh||category.nameEn}}</option></select></label></template>
 </template>
</section></template>
