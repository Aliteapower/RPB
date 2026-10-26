<script setup lang="ts">
import {ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useMerchantStorage} from './merchant/useMerchantStorage'
import StorageScanResult from './merchant/StorageScanResult.vue'
import StorageCustomerPicker from './merchant/StorageCustomerPicker.vue'
import MerchantStorageInbox from './merchant/MerchantStorageInbox.vue'
import MerchantStorageCustodians from './merchant/MerchantStorageCustodians.vue'
import MerchantStorageDeposit from './merchant/MerchantStorageDeposit.vue'
import MerchantStorageWithdrawal from './merchant/MerchantStorageWithdrawal.vue'
import MerchantStorageRecords from './merchant/MerchantStorageRecords.vue'
import MerchantStorageSettings from './merchant/MerchantStorageSettings.vue'
import WalletQrScanner from '../wallet/components/WalletQrScanner.vue'
import '../wallet/wallet.css'
import './storage.css'
defineOptions({inheritAttrs:false})
const {store,api,home,customer,results,query,task,busy,loading,searching,searched,pending,error,notice,activeIntent,scanResult,scoped,can,choose,search,open,load,run,scan,recover,fail,t}=useMerchantStorage()
const router=useRouter(),operationBusy=ref(false),reviewing=ref(false),showScan=ref(false),storeInput=ref(store.value)
watch(store,()=>{operationBusy.value=false;reviewing.value=false;showScan.value=false;storeInput.value=store.value})
function identify(code:string){showScan.value=false;void run(()=>scan(code))}
</script>
<template><main class="cards-page storage-page storage-merchant-page" data-storage-role="merchant">
 <header class="storage-page-header"><div class="storage-heading"><RouterLink :to="`/stores/${store}/wallet`">← {{t('back')}}</RouterLink><div><span class="cards-badge">{{t('merchantArea')}}</span><h1>{{t('manage')}}</h1></div></div><button :disabled="loading||busy||operationBusy" @click="load">{{t('refresh')}}</button></header>
 <div class="storage-inline"><p class="storage-customer-store">{{home?.storeName}}</p><form v-if="home&&home.stores.length>1" class="storage-inline" @submit.prevent="router.push(`/stores/${encodeURIComponent(storeInput)}/wine-storage`)"><select v-model="storeInput" :disabled="busy||operationBusy" :aria-label="t('storeId')"><option v-for="option in home.stores" :key="option.id" :value="option.id">{{option.name}}</option></select><button :disabled="busy||operationBusy">{{t('switchStore')}}</button></form></div><p v-if="loading&&!home" role="status">{{t('loading')}}</p><p v-if="error" class="storage-error" role="alert">{{error}}</p><p v-if="notice" class="storage-notice" role="status">{{notice}}</p>
 <section v-if="pending" class="storage-panel"><p>{{t('pendingResult')}}</p><button :disabled="busy||operationBusy" @click="recover">{{t('recover')}}</button></section>
 <template v-if="home">
  <nav class="storage-customer-tasks" :aria-label="t('sections')"><button v-for="view in (home?.newBusinessEnabled?['deposit','withdraw','records']:['withdraw','records'])" :key="view" :aria-pressed="task===view" :class="{primary:task===view}" :disabled="busy||operationBusy" @click="open(view)">{{t(view)}}</button></nav><p class="storage-customer-hint">{{t(task==='withdraw'?'withdrawHint':task==='records'?'recordsHint':'depositHint')}}</p>
  <button v-if="task==='withdraw'&&can('deliver')" :disabled="busy||operationBusy||pending" @click="showScan=!showScan">{{t('scanCode')}}</button><WalletQrScanner v-if="task==='withdraw'&&showScan" purpose="storage" :title="t('scanCode')" :privacy-message="t('scanPrivacy')" :manual-label="t('pasteCode')" :invalid-message="t('invalidCode')" :disabled="busy||pending" @code="identify"/>
<MerchantStorageInbox v-if="task==='deposit'&&can('receive')" :key="store" :api="api" :home="home" :disabled="busy||pending||operationBusy" @changed="load();notice=$event" @error="fail" @busy-change="operationBusy=$event" @reviewing-change="reviewing=$event"/>
  <details v-if="home?.newBusinessEnabled&&task==='deposit'&&!reviewing" class="storage-panel" :open="!!customer"><summary>{{t('directRegister')}}</summary><StorageCustomerPicker v-model:query="query" :customer="customer" :results="results" :busy="busy||operationBusy" :searching="searching" :searched="searched" @search="search" @choose="choose"/><MerchantStorageDeposit v-if="scoped&&customer" :key="`${store}:${customer.id}`" :home="scoped" :api="api" :store="store" :customer-id="customer.id" :disabled="busy||pending||operationBusy" :can-receive="can('receive')" @saved="load();notice=t('received')" @error="fail" @busy-change="operationBusy=$event"/></details>
  <StorageScanResult v-if="task==='withdraw'&&scanResult" :intent="scanResult" :home="home" :disabled="busy||pending" @next="scanResult=null;showScan=true"/>
  <StorageCustomerPicker v-if="task!=='deposit'&&!reviewing&&!scanResult" v-model:query="query" :customer="customer" :results="results" :busy="busy||operationBusy" :searching="searching" :searched="searched" @search="search" @choose="choose"/>
  <template v-if="task!=='deposit'&&scoped&&customer&&!reviewing&&!scanResult">
   <MerchantStorageWithdrawal v-if="task==='withdraw'" :key="`${store}:${customer.id}`" :home="scoped" :customer-id="customer.id" :api="api" :disabled="busy||pending" :can-deliver="can('deliver')" :active-intent="activeIntent" @created="activeIntent=$event;load()" @command="run" @refresh="load" @error="fail" @cleared="activeIntent=null" @busy-change="operationBusy=$event"/>
   <MerchantStorageRecords v-if="task==='records'" :key="`${store}:${customer.id}`" :home="scoped" :api="api" :disabled="busy||pending" :can-correct="can('correct')" :can-photo="can('receive')" @command="run" @error="fail" @changed="load"/>
  </template><p v-else-if="task!=='deposit'&&!reviewing&&!scanResult" class="storage-panel storage-empty">{{t('noCustomer')}}</p>
  <MerchantStorageCustodians v-if="home.newBusinessEnabled&&can('custodians.manage')" :key="store" :api="api" :disabled="busy||pending||operationBusy" @error="fail" @busy-change="operationBusy=$event"/>
  <MerchantStorageSettings :home="home" :api="api" :disabled="busy||pending||operationBusy" :can-manage="home.newBusinessEnabled&&can('rules.manage')" @command="run"/>
 </template>
</main></template>
