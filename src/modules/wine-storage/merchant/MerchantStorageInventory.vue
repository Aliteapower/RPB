<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import './inventory.css'
import type {StorageApi} from '../api'
import type {InventoryPage} from '../types'
import {dateTime} from '../format'
import {useMerchantStorageText} from './text'
const props=defineProps<{api:StorageApi;timezone:string;refreshKey:number}>(),{t,locale}=useMerchantStorageText()
const query=ref(''),status=ref('all'),page=ref(0),data=ref<InventoryPage|null>(null),loading=ref(false),error=ref('')
const totalCards:[keyof InventoryPage['summary'],string][]=[['customerCount','inventoryCustomers'],['bottleCount','inventoryBottles'],['openedBottleCount','inventoryOpened'],['expiredBottleCount','inventoryExpiredBottles']]
let generation=0,active=true
async function load(){
 const request=++generation,client=props.api,params=new URLSearchParams({query:query.value.trim(),status:status.value,page:String(page.value),size:'50'})
 loading.value=true;error.value='';data.value=null
 try{const result=await client.get<InventoryPage>('/inventory?'+params);if(active&&request===generation)data.value=result}
 catch(e){if(active&&request===generation)error.value=e instanceof Error?e.message:String(e)}
 finally{if(active&&request===generation)loading.value=false}
}
function search(){page.value=0;void load()}
function next(offset:number){page.value+=offset;void load()}
watch(()=>[props.api,props.refreshKey],()=>{page.value=0;void load()},{immediate:true})
onUnmounted(()=>{active=false;generation++})
</script>
<template><section class="storage-inventory" :aria-label="t('inventory')" :aria-busy="loading">
 <form class="storage-panel storage-inventory-filters" @submit.prevent="search">
  <label>{{t('inventorySearch')}}<input v-model="query" maxlength="120" :placeholder="t('inventorySearchHint')"></label>
  <label>{{t('inventoryStatus')}}<select v-model="status"><option value="all">{{t('inventoryAll')}}</option><option value="stored">{{t('inventoryStored')}}</option><option value="expired">{{t('inventoryExpired')}}</option></select></label>
  <button class="primary" :disabled="loading">{{t('inventoryQuery')}}</button>
 </form>
 <p v-if="loading" role="status">{{t('loading')}}</p>
 <div v-else-if="error" class="storage-panel" role="alert"><p class="storage-error">{{error}}</p><button @click="load">{{t('inventoryRetry')}}</button></div>
 <template v-else-if="data">
  <div class="storage-inventory-totals" aria-live="polite"><div v-for="entry in totalCards" :key="entry[0]" class="storage-panel"><strong>{{data.summary[entry[0]]}}</strong><span>{{t(entry[1])}}</span></div></div>
  <p class="cards-muted">{{t('inventoryTotalsHint')}} · {{data.summary.itemCount}} {{t('inventoryRows')}}</p>
  <p v-if="!data.items.length" class="storage-panel storage-empty">{{t(data.summary.itemCount?'inventoryEmptyPage':'inventoryEmpty')}}</p>
  <div v-else class="storage-inventory-list">
   <article v-for="item in data.items" :key="item.id" class="storage-panel storage-inventory-item">
    <header><div><h2>{{(locale.startsWith('en')?item.nameEn||item.nameZh:item.nameZh||item.nameEn)||t('inventoryUnnamed')}}</h2><span class="cards-badge" :class="{'storage-error':item.status==='expired'}">{{t(item.status==='expired'?'inventoryExpired':'inventoryStored')}}</span><span v-if="item.opened" class="cards-badge">{{t('inventoryOpenedBottle')}}</span></div><div class="storage-inventory-quantity"><strong>{{item.remaining}}</strong><span>{{t('unit')}}</span></div></header>
    <dl><div><dt>{{t('inventoryCustomer')}}</dt><dd>{{item.customerName||t('inventoryUnknownCustomer')}}<small v-if="item.customerPhone">{{item.customerPhone}}</small></dd></div><div><dt>{{t('inventoryLocation')}}</dt><dd>{{[item.locationName,item.slot].filter(Boolean).join(' · ')||'—'}}</dd></div><div><dt>{{t('inventoryReceived')}}</dt><dd>{{dateTime(item.receivedAt,timezone)}}</dd></div><div><dt>{{t('inventoryExpires')}}</dt><dd :class="{'storage-error':item.status==='expired'}">{{dateTime(item.expiresAt,timezone)}}</dd></div><div><dt>{{t('inventoryCustodian')}}</dt><dd>{{item.custodianName||'—'}}</dd></div><div><dt>{{t('inventoryLabel')}}</dt><dd>{{item.label||'—'}}</dd></div></dl>
   </article>
  </div>
  <nav v-if="page>0||data.hasMore" class="storage-inline" :aria-label="t('inventoryPages')"><button :disabled="loading||page===0" @click="next(-1)">{{t('inventoryPrevious')}}</button><span>{{t('inventoryPage')}} {{page+1}}</span><button :disabled="loading||!data.hasMore" @click="next(1)">{{t('inventoryNext')}}</button></nav>
 </template>
</section></template>
